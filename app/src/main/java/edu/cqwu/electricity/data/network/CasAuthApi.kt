package edu.cqwu.electricity.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * CAS 统一认证登录 API
 * 对应 Python 参考登录流程中的 login() 函数
 *
 * 专用于 CAS 层认证，仅负责向 authserver.cqwu.edu.cn 发送登录请求获取 CASTGC Cookie。
 * epay 层的 JSESSIONID 获取和 ticket 交换由 QrCodeApi 通过 followRedirects 自动完成。
 *
 * 流程：
 * 1. GET LOGIN_URL → 直接获取 CAS 登录页
 * 2. 解析登录页 HTML 提取 salt、lt、execution、dllt
 * 3. AES-CBC 加密密码
 * 4. POST 表单到 LOGIN_URL
 * 5. 从 Cookie 中提取 CASTGC
 *
 * 所有登录均使用用户隔离的 Cookie 存储（UserCookieStore + UserAwareCookieJar），
 * 与系统 CookieManager 完全隔离，避免多用户串号。
 */

/**
 * 自定义 DNS 解析器：优先使用 IPv4 地址。
 *
 * 日志数据显示 `authserver.cqwu.edu.cn` 的 IPv6 地址（2001:250:2407::12）端口 80 不可用，
 * 导致 OkHttp 先尝试 IPv6 连接等待 15 秒超时后，才回退到 IPv4（120ms 成功）。
 *
 * 此解析器将 IPv4 地址排在 IPv6 前面，使 OkHttp 优先尝试 IPv4，
 * 彻底避免 15 秒的 IPv6 连接超时。
 */
object PreferIPv4Dns : Dns {
    private val fallbackDns = Dns.SYSTEM

    override fun lookup(hostname: String): List<InetAddress> {
        val allAddresses = fallbackDns.lookup(hostname)
        val ipv4 = mutableListOf<InetAddress>()
        val ipv6 = mutableListOf<InetAddress>()
        for (addr in allAddresses) {
            if (addr is java.net.Inet4Address) {
                ipv4.add(addr)
            } else if (addr is java.net.Inet6Address) {
                ipv6.add(addr)
            }
        }
        // IPv4 在前，IPv6 在后
        return ipv4 + ipv6
    }
}

/**
 * 共享的 OkHttpClient 单例，
 * 使 CasAuthApi（登录）和 QrCodeApi（二维码获取）共享同一 Cookie Session。
 *
 * 需在 Application.onCreate() 中调用 SharedHttpClient.init(context) 初始化。
 */
object SharedHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cookieJar(CookieStoreOkHttpJar)
            .dns(PreferIPv4Dns)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor)
            .build()
    }

    fun init() {
        CookieStore.init()
    }

    /**
     * 为指定用户创建独立的 OkHttpClient。
     *
     * 使用 UserAwareCookieJar 绑定到该用户的独立 UserCookieStore，
     * 与系统 CookieManager 完全隔离。
     * 用于多用户场景，每个用户的登录会话互不干扰。
     */
    fun createClientForUser(username: String): OkHttpClient {
        val userStore = AccountManager.getCookiesForUser(username)
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cookieJar(UserAwareCookieJar(userStore))
            .dns(PreferIPv4Dns)
            .addInterceptor(UserAgentInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

class CasAuthApi {

    companion object {
        /** CAS 统一认证登录页 */
        const val LOGIN_URL = "https://authserver.cqwu.edu.cn/authserver/login"
    }


    /**
     * 为指定用户执行 CAS 登录（使用该用户独立的 Cookie 存储）。
     * 使用 createClientForUser(username) 创建的独立 OkHttpClient，
     * Cookie 通过 UserAwareCookieJar 操作 UserCookieStore，与系统 CookieManager 隔离。
     */
    suspend fun loginForUser(username: String, password: String): Result<LoginResult> {
        val userClient = SharedHttpClient.createClientForUser(username)
        val userStore = AccountManager.getCookiesForUser(username)
        return performLogin(username, password, userClient, tag = "(user)") { url ->
            userStore.getCookie(url) ?: ""
        }
    }

    /**
     * CAS 登录核心实现。
     *
     * @param username    学号
     * @param password    密码
     * @param client      用于 HTTP 请求的 OkHttpClient
     * @param tag         日志标签后缀
     * @param cookieProvider 从指定 URL 获取 Cookie 字符串的函数
     * @return Result<LoginResult>
     */
    private suspend fun performLogin(
        username: String,
        password: String,
        client: OkHttpClient,
        tag: String,
        cookieProvider: (String) -> String
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()

            // === 诊断日志：记录开始登录时的 Cookie 状态 ===
            val preLoginCookie = cookieProvider(LOGIN_URL)
            android.util.Log.d("CasAuthApi", "登录前Cookie状态$tag: $preLoginCookie")

            // 步骤 1：直接 GET CAS 登录页
            android.util.Log.d("CasAuthApi", "步骤1$tag: GET $LOGIN_URL")
            val loginPageResp = client.newCall(
                Request.Builder().url(LOGIN_URL).get().build()
            ).execute()

            // === 诊断日志：记录 GET 响应详情 ===
            val loginPageFinalUrl = loginPageResp.request.url.toString()
            val loginPageCode = loginPageResp.code
            android.util.Log.d("CasAuthApi", "步骤1响应$tag: code=$loginPageCode, finalUrl=$loginPageFinalUrl")

            val loginPageHtml = loginPageResp.body.string()
            val t1 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤1耗时$tag: ${t1 - t0}ms，登录页HTML长度: ${loginPageHtml.length}")

            // === 诊断日志：HTML 头部快照（检查是否包含关键字段）===
            val htmlHeadPreview = loginPageHtml.take(300)
            android.util.Log.d("CasAuthApi", "步骤1 HTML头部预览$tag: $htmlHeadPreview")

            // 步骤 2：解析登录页参数
            val salt = extractRegex(loginPageHtml, """var pwdDefaultEncryptSalt = "(.+?)"""")
                ?: throw RuntimeException("无法获取加密 salt")
            val lt = extractInputValue(loginPageHtml, "lt")
                ?: throw RuntimeException("无法获取 lt")
            val execution = extractInputValue(loginPageHtml, "execution")
                ?: throw RuntimeException("无法获取 execution")
            val dllt = extractInputValue(loginPageHtml, "dllt") ?: ""

            val t2 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤2耗时$tag: ${t2 - t1}ms, salt=$salt, lt=$lt, execution=$execution, dllt=$dllt")

            // 步骤 3：AES-CBC 加密密码
            val encryptedPassword = AesEncrypt.encryptPassword(password, salt)
            val t3 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤3耗时$tag: ${t3 - t2}ms")

            // 步骤 4：POST 登录表单
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

            // === 诊断日志：POST 前记录 ===
            android.util.Log.d("CasAuthApi", "步骤4$tag: POST $LOGIN_URL (username=$username, lt前4位=${lt.take(4)})")

            val loginResp = client.newCall(
                Request.Builder()
                    .url(LOGIN_URL)
                    .post(formBody)
                    .build()
            ).execute()

            val t4 = System.currentTimeMillis()

            // === 诊断日志：POST 响应详情 ===
            val postFinalUrl = loginResp.request.url.toString()
            val postResponseBody = loginResp.body.string()
            val postBodyLen = postResponseBody.length
            android.util.Log.d("CasAuthApi", "步骤4耗时$tag: ${t4 - t3}ms, 登录响应 code=${loginResp.code}, finalUrl=$postFinalUrl, bodyLen=$postBodyLen")

            // === 诊断日志：POST 后的 Cookie 状态 ===
            val postLoginCookie = cookieProvider(LOGIN_URL)
            android.util.Log.d("CasAuthApi", "登录后Cookie状态$tag: $postLoginCookie")

            // 如果响应体可读，检查是否包含错误提示
            if (postResponseBody.length < 2000) {
                android.util.Log.d("CasAuthApi", "POST响应体内容$tag: ${postResponseBody.take(500)}")
            }

            // 步骤 5：从 Cookie（全局 CookieManager 或 UserCookieStore）中提取 CASTGC
            val cookieString = cookieProvider(LOGIN_URL)
            val castgc = cookieString.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("CASTGC=") }
                ?.substringAfter("CASTGC=")

            if (castgc == null) {
                // === 诊断日志：未获取到 CASTGC，列出所有 Cookie ===
                val allCookies = cookieString.split(";").map { it.trim() }.filter { it.isNotBlank() }
                android.util.Log.e("CasAuthApi", "未获取到 CASTGC$tag, 共有${allCookies.size}个Cookie: ${allCookies.joinToString(", ")}")
                throw RuntimeException("登录失败：未能获取到 CASTGC Cookie，请检查账号或密码")
            }

            val t5 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤5耗时$tag: ${t5 - t4}ms, CASTGC=$castgc")
            android.util.Log.d("CasAuthApi", "登录总耗时$tag: ${t5 - t0}ms")

            Result.success(LoginResult(
                username = username,
                cookieString = cookieString
            ))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            // === 诊断日志：超时时的 Cookie 状态 ===
            val timeoutCookie = try { cookieProvider(LOGIN_URL) } catch (ex: Exception) { "(获取失败: ${ex.message})" }
            android.util.Log.e("CasAuthApi", "=== Socket超时诊断 === 登录失败$tag, 超时时刻Cookie状态: $timeoutCookie")
            android.util.Log.e("CasAuthApi", "=== Socket超时诊断 === 异常信息: ${e.message}")
            android.util.Log.e("CasAuthApi", "登录失败$tag", e)
            Result.failure(e)
        } catch (e: Exception) {
            // === 诊断日志：异常时的 Cookie 状态 ===
            val exceptionCookie = try { cookieProvider(LOGIN_URL) } catch (ex: Exception) { "(获取失败: ${ex.message})" }
            android.util.Log.e("CasAuthApi", "=== 异常诊断 === 登录失败$tag, 异常类型=${e::class.simpleName}, 异常时刻Cookie状态: $exceptionCookie")
            android.util.Log.e("CasAuthApi", "登录失败$tag", e)
            Result.failure(e)
        }
    }

    /**
     * 从 HTML 中提取指定正则表达式的第一个匹配组
     */
    private fun extractRegex(html: String, pattern: String): String? {
        val regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    /**
     * 从 HTML 中提取 <input name="name"> 的 value 属性
     */
    private fun extractInputValue(html: String, name: String): String? {
        val pattern1 = Regex("""<input[^>]*\sname\s*=\s*["']$name["'][^>]*\svalue\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(html)
        if (match1 != null) return match1.groupValues[1]

        val pattern2 = Regex("""<input[^>]*\svalue\s*=\s*["']([^"']*)["'][^>]*\sname\s*=\s*["']$name["']""", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(html)
        if (match2 != null) return match2.groupValues[1]

        return null
    }
}

/**
 * CAS 登录结果
 *
 * @param username 登录用户名（学号）
 * @param cookieString 完整 Cookie 字符串（包含 CASTGC 等），用于展示和调试
 */
data class LoginResult(
    val username: String,
    val cookieString: String
)
