package edu.cqwu.electricity.login.data

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

/**
 * CAS 登录页参数。
 */
data class CasLoginPage(
    val salt: String,
    val lt: String,
    val execution: String,
    val dllt: String,
)

/**
 * CAS 登录 POST 结果。
 */
data class CasLoginOutcome(
    val responseCode: Int,
    val location: String?,
)

/**
 * CAS 登录异常，供直连和 WebVPN 调用方映射到各自业务异常。
 */
sealed class CasLoginException(message: String) : Exception(message) {
    class MissingField(val field: String) : CasLoginException(
        when (field) {
            "salt" -> "无法获取加密 salt"
            else -> "无法获取 $field"
        },
    )

    class CaptchaRequired : CasLoginException("CAS 需要验证码，无法自动登录，请手动完成登录")
    class LoginRejected : CasLoginException("登录失败：账号或密码错误")
}

/**
 * CAS 账号密码登录核心。
 *
 * 直连登录和 WebVPN 自动登录复用同一套流程：取登录页、解析表单参数、
 * needCaptcha 检查、AES 加密、提交登录表单。
 */
object CasLoginFlow {

    fun parseLoginPage(html: String): CasLoginPage {
        val salt = HtmlFormParser.extractRegex(html, """var pwdDefaultEncryptSalt = "(.+?)"""")
            ?: throw CasLoginException.MissingField("salt")
        val lt = HtmlFormParser.extractInputValue(html, "lt")
            ?: throw CasLoginException.MissingField("lt")
        val execution = HtmlFormParser.extractInputValue(html, "execution")
            ?: throw CasLoginException.MissingField("execution")
        val dllt = HtmlFormParser.extractInputValue(html, "dllt") ?: ""
        return CasLoginPage(salt, lt, execution, dllt)
    }

    fun fetchLoginPage(
        client: OkHttpClient,
        loginPageUrl: String,
        extraHeaders: Map<String, String> = emptyMap(),
        existingHtml: String? = null,
    ): CasLoginPage {
        if (existingHtml != null) return parseLoginPage(existingHtml)

        val request = Request.Builder()
            .url(loginPageUrl)
            .get()
            .apply {
                extraHeaders.forEach { (name, value) -> header(name, value) }
            }
            .build()

        val html = client.newCall(request).execute().use { it.body.string() }
        return parseLoginPage(html)
    }

    fun buildNeedCaptchaUrl(
        loginPageUrl: String,
        username: String,
        salt: String,
        v: String,
        enlinkVpn: Boolean,
    ): String {
        val base = loginPageUrl.toHttpUrl()
        val dir = base.pathSegments.dropLast(1).joinToString("/", prefix = "/")
        val builder = base.newBuilder()
            .encodedPath("$dir/needCaptcha.html")
            .removeAllQueryParameters("service")
            .addQueryParameter("username", username)
            .addQueryParameter("pwdEncrypt2", salt)
            .addQueryParameter("v", v)
        if (enlinkVpn) {
            builder.addQueryParameter("enlink-vpn", "")
        }
        return builder.build().toString()
    }

    fun isCaptchaRequired(
        client: OkHttpClient,
        loginPageUrl: String,
        username: String,
        salt: String,
        enlinkVpn: Boolean,
    ): Boolean {
        val v = ThreadLocalRandom.current()
            .nextLong(10_000_000_000_000_000L, 99_999_999_999_999_999L)
            .toString()
        val captchaUrl = buildNeedCaptchaUrl(loginPageUrl, username, salt, v, enlinkVpn)
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

    fun buildLoginForm(
        username: String,
        encryptedPassword: String,
        page: CasLoginPage,
    ): FormBody {
        return FormBody.Builder()
            .add("username", username)
            .add("password", encryptedPassword)
            .add("captchaResponse", "")
            .add("rememberMe", "on")
            .add("lt", page.lt)
            .add("dllt", page.dllt)
            .add("execution", page.execution)
            .add("_eventId", "submit")
            .add("rmShown", "1")
            .build()
    }

    fun submitLogin(
        client: OkHttpClient,
        loginPageUrl: String,
        form: FormBody,
        extraHeaders: Map<String, String> = emptyMap(),
    ): CasLoginOutcome {
        val request = Request.Builder()
            .url(loginPageUrl)
            .post(form)
            .apply {
                extraHeaders.forEach { (name, value) -> header(name, value) }
            }
            .build()

        val response = client.newCall(request).execute()
        return response.use {
            when (it.code) {
                in 300..399 -> {
                    val location = it.header("Location")
                        ?: throw IOException("CAS 登录返回重定向但缺少 Location: ${it.code}")
                    CasLoginOutcome(it.code, location)
                }
                in 200..299 -> {
                    val body = it.body.string()
                    if (HtmlFormParser.isCasLoginPage(body)) {
                        throw CasLoginException.LoginRejected()
                    }
                    CasLoginOutcome(it.code, null)
                }
                else -> {
                    throw IOException("CAS 登录失败: HTTP ${it.code}")
                }
            }
        }
    }

    fun login(
        client: OkHttpClient,
        loginPageUrl: String,
        username: String,
        password: String,
        extraHeaders: Map<String, String> = emptyMap(),
        enlinkVpn: Boolean = false,
        existingHtml: String? = null,
    ): CasLoginOutcome {
        val page = fetchLoginPage(client, loginPageUrl, extraHeaders, existingHtml)
        if (isCaptchaRequired(client, loginPageUrl, username, page.salt, enlinkVpn)) {
            throw CasLoginException.CaptchaRequired()
        }
        val encryptedPassword = AesEncrypt.encryptPassword(password, page.salt)
        val form = buildLoginForm(username, encryptedPassword, page)
        return submitLogin(client, loginPageUrl, form, extraHeaders)
    }
}
