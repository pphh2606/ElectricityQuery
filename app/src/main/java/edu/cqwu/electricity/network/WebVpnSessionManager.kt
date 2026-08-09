package edu.cqwu.electricity.network

import android.content.Context
import android.util.Log
import edu.cqwu.electricity.app.ElectricityApp
import edu.cqwu.electricity.login.data.AccountManager
import edu.cqwu.electricity.login.data.AccountStore
import edu.cqwu.electricity.login.data.AesEncrypt
import edu.cqwu.electricity.login.data.CookieStoreOkHttpJar
import edu.cqwu.electricity.login.data.HtmlFormParser
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import okhttp3.FormBody
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

/**
 * WebVPN 场景下的 CAS 自动登录。
 *
 * 复用 [AesEncrypt]、[HtmlFormParser] 和已保存账号密码，完成：
 * 1. 从受保护 URL 跟随重定向到 authserver 登录页
 * 2. 判断是否需要验证码
 * 3. 提交 CAS 登录表单
 * 4. 跟随 ticket 回调，直到服务端建立会话
 */
object WebVpnSessionManager {

    private const val TAG = "WebVpnSessionManager"
    private const val MAX_REDIRECTS = 10

    private val jsRedirectRegex = Regex(
        """(?:window\.location(?:\.href)?|location\.href)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 确保 WebVPN 下指定受保护 URL 的 CAS 会话已建立。
     *
     * 已登录时直接返回；未登录时自动用保存的账号密码完成登录。
     */
    fun authenticate(
        context: Context,
        protectedUrl: String,
        cookieJar: CookieJar? = CookieStoreOkHttpJar,
    ) {
        if (!WebVpnSettings.enabled) return

        synchronized(this) {
            val client = createAuthClient(cookieJar)
            val startUrl = if (WebVpnEncoder.isWebVpnUrl(protectedUrl)) {
                protectedUrl
            } else {
                WebVpnEncoder.transform(protectedUrl)
            }

            val loginPage = followToCasLoginPage(client, startUrl) ?: return
            val (loginUrl, loginHtml) = loginPage

            val account = resolveSavedAccount(context)
                ?: throw SessionExpiredException("未找到已保存的账号密码，无法自动登录 WebVPN，请先保存密码")
            val username = account.first
            val password = account.second

            val salt = HtmlFormParser.extractRegex(
                loginHtml,
                """var pwdDefaultEncryptSalt = "(.+?)"""",
            ) ?: throw SessionExpiredException("无法获取 WebVPN CAS 加密 salt")

            val lt = HtmlFormParser.extractInputValue(loginHtml, "lt")
                ?: throw SessionExpiredException("无法获取 WebVPN CAS 登录参数 lt")
            val execution = HtmlFormParser.extractInputValue(loginHtml, "execution")
                ?: throw SessionExpiredException("无法获取 WebVPN CAS 登录参数 execution")
            val dllt = HtmlFormParser.extractInputValue(loginHtml, "dllt") ?: ""

            if (isCaptchaRequired(client, loginUrl, username, salt)) {
                throw SessionExpiredException("CAS 需要验证码，无法自动登录，请手动完成 WebVPN 登录")
            }

            val encryptedPassword = AesEncrypt.encryptPassword(password, salt)
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", encryptedPassword)
                .add("captchaResponse", "")
                .add("rememberMe", "on")
                .add("lt", lt)
                .add("dllt", dllt)
                .add("execution", execution)
                .add("_eventId", "submit")
                .add("rmShown", "1")
                .build()

            val loginRequest = Request.Builder()
                .url(loginUrl)
                .post(formBody)
                .addHeader("Origin", WebVpnEncoder.PROXY_BASE)
                .addHeader("Referer", loginUrl)
                .addHeader("X-Requested-With", "edu.cqwu.electricity")
                .build()

            Log.d(TAG, "提交 WebVPN CAS 登录: username=$username")
            client.newCall(loginRequest).execute().use { response ->
                when {
                    response.code in 300..399 -> {
                        val location = response.header("Location")
                            ?: throw IOException("CAS 登录返回重定向但缺少 Location: ${response.code}")
                        val ticketUrl = resolveRedirectUrl(loginUrl, location)
                        Log.d(TAG, "CAS 登录成功，跟随 ticket 回调: ${ticketUrl.take(120)}")
                        val finalPage = followToCasLoginPage(client, ticketUrl)
                        if (finalPage != null) {
                            throw SessionExpiredException("WebVPN CAS ticket 校验后仍返回登录页")
                        }
                    }
                    response.code in 200..299 -> {
                        val body = response.body.string()
                        if (HtmlFormParser.isCasLoginPage(body)) {
                            throw SessionExpiredException("WebVPN CAS 登录失败：账号或密码错误")
                        }
                    }
                    else -> {
                        throw IOException("WebVPN CAS 登录失败: HTTP ${response.code}")
                    }
                }
            }

            Log.d(TAG, "WebVPN CAS 自动登录完成")
        }
    }

    private fun createAuthClient(cookieJar: CookieJar?): OkHttpClient {
        return HttpClientFactory.createNoRedirectWithoutWebVpn(cookieJar ?: CookieStoreOkHttpJar)
    }

    private fun resolveSavedAccount(context: Context): Pair<String, String>? {
        val store = AccountStore.getInstance(context)
        val activeUser = AccountManager.getActiveUser()
        if (activeUser != null) {
            val password = store.getPassword(activeUser)
            if (!password.isNullOrBlank()) return activeUser to password
        }
        return store.getAllAccounts()
            .firstOrNull { !it.password.isNullOrBlank() }
            ?.let { it.username to it.password!! }
    }

    /**
     * 返回 CAS 登录页 (url, html)；如果已登录则返回 null。
     */
    private fun followToCasLoginPage(client: OkHttpClient, startUrl: String): Pair<String, String>? {
        var currentUrl = startUrl
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val response = client.newCall(
                Request.Builder()
                    .url(currentUrl)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .get()
                    .build(),
            ).execute()

            response.use {
                when {
                    it.code in 300..399 -> {
                        val location = it.header("Location")
                            ?: throw IOException("重定向缺少 Location: $currentUrl")
                        currentUrl = resolveRedirectUrl(currentUrl, location)
                        redirectCount++
                    }
                    it.code in 200..299 -> {
                        val body = it.body.string()
                        if (HtmlFormParser.isCasLoginPage(body)) {
                            return currentUrl to body
                        }
                        val jsRedirect = jsRedirectRegex.find(body)?.groupValues?.getOrNull(1)
                        if (jsRedirect != null) {
                            currentUrl = resolveRedirectUrl(currentUrl, jsRedirect)
                            redirectCount++
                        } else {
                            return null
                        }
                    }
                    else -> {
                        throw IOException("受保护 URL 请求失败: HTTP ${it.code}")
                    }
                }
            }
        }

        throw IOException("WebVPN 重定向次数超过上限: $startUrl")
    }

    private fun isCaptchaRequired(
        client: OkHttpClient,
        loginUrl: String,
        username: String,
        salt: String,
    ): Boolean {
        val v = ThreadLocalRandom.current()
            .nextLong(10_000_000_000_000_000L, 99_999_999_999_999_999L)
            .toString()
        val captchaUrl = buildNeedCaptchaUrl(loginUrl, username, salt, v)
        val response = client.newCall(
            Request.Builder()
                .url(captchaUrl)
                .addHeader("Accept", "*/*")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .get()
                .build(),
        ).execute()
        response.use {
            if (!it.isSuccessful) {
                throw IOException("needCaptcha 请求失败: HTTP ${it.code}")
            }
            val body = it.body.string().trim()
            return !body.equals("false", ignoreCase = true)
        }
    }

    internal fun buildNeedCaptchaUrl(
        loginUrl: String,
        username: String,
        salt: String,
        v: String,
    ): String {
        val base = loginUrl.toHttpUrl()
        val dir = base.pathSegments.dropLast(1).joinToString("/", prefix = "/")
        return base.newBuilder()
            .encodedPath("$dir/needCaptcha.html")
            .removeAllQueryParameters("service")
            .addQueryParameter("username", username)
            .addQueryParameter("pwdEncrypt2", salt)
            .addQueryParameter("v", v)
            .addQueryParameter("enlink-vpn", "")
            .build()
            .toString()
    }

    internal fun resolveRedirectUrl(baseUrl: String, location: String): String {
        val baseHttpUrl = baseUrl.toHttpUrl()
        return baseHttpUrl.resolve(location)?.toString()
            ?: throw IOException("无法解析重定向 URL: $location (base: $baseUrl)")
    }
}
