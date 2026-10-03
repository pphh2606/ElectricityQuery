package edu.cqwu.electricity.accountmanagerv2

import com.google.gson.Gson
import edu.cqwu.electricity.login.data.AesEncrypt
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.net.PageSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/**
 * 修改密码页面加载结果：加密盐 + 验证码可用标记。
 */
data class PasswordChangePageInfo(
    /** 页面下发的密码加密盐（pwdDefaultEncryptSalt），未解析到视为异常 */
    val salt: String,
)

/**
 * 修改密码提交结果。
 */
data class PasswordChangeSubmitResult(
    val isSuccess: Boolean,
    val message: String,
)

/**
 * CAS 修改密码 API。
 *
 * 对应抓包接口：
 * - GET  mobilePasswordChange.do → HTML 内含 `pwdDefaultEncryptSalt = "..."` 加密盐
 * - GET  captcha.html           → 图形验证码（image/jpeg），刷新加 ?ts= 时间戳
 * - POST mobilePasswordChange.do → {"success":true/false,"errorMsg":"..."} 提交修改
 *
 * 密码加密复用 [AesEncrypt.encryptPassword]（AES-CBC，key/IV 取盐值随机前缀）。
 * 三步共用一个 [PageSession]：盐与验证码都由服务端存在该会话里，换会话必然解密失败。
 * 响应为 CAS 登录页时抛 [SessionExpiredException]。
 */
class PasswordChangeApi {

    private val gson = Gson()

    /** 本次页面会话：取盐、取验证码、提交共用一个，避免服务端为每一步重建 session */
    private val session = PageSession()

    companion object {
        private const val TAG = "PasswordChangeApi"
        private const val CHANGE_URL = "https://authserver.cqwu.edu.cn/authserver/mobilePasswordChange.do"
        private const val CAPTCHA_URL = "https://authserver.cqwu.edu.cn/authserver/captcha.html"
        private val SALT_REGEX = Regex("""var pwdDefaultEncryptSalt\s*=\s*"(.+?)"""")
    }

    /** 加载修改密码页面，解析加密盐（页面请求本身携带登录 cookie，验证会话有效性） */
    suspend fun loadPage(cookies: Map<String, Map<String, String>>): Result<PasswordChangePageInfo> =
        withContext(Dispatchers.IO) {
            session.restart()
            try {
                val html = session.of(cookies).client.newCall(
                    Request.Builder()
                        .url(CHANGE_URL)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body.string()
                }
                HtmlFormParser.checkAndThrow(html)
                val salt = SALT_REGEX.find(html)?.groupValues?.getOrNull(1)
                    ?: throw IOException("无法从页面解析加密盐")
                Result.success(PasswordChangePageInfo(salt = salt))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "加载修改密码页面失败", e)
                Result.failure(e)
            }
        }

    /** 下载图形验证码（与取盐、提交同一个会话：服务端把验证码也存在该 session 里） */
    suspend fun loadCaptcha(cookies: Map<String, Map<String, String>>): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val bytes = session.of(cookies).client.newCall(
                    Request.Builder()
                        .url("$CAPTCHA_URL?ts=${System.currentTimeMillis()}")
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body.bytes()
                }
                Result.success(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "加载验证码失败", e)
                Result.failure(e)
            }
        }

    /** 提交修改密码（密码均先 AES 加密，与服务端网页行为一致） */
    suspend fun submit(
        cookies: Map<String, Map<String, String>>,
        salt: String,
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        captcha: String,
    ): Result<PasswordChangeSubmitResult> = withContext(Dispatchers.IO) {
        try {
            val body = session.of(cookies).client.newCall(
                Request.Builder()
                    .url(CHANGE_URL)
                    .post(
                        FormBody.Builder()
                            .add("oldPassword", AesEncrypt.encryptPassword(oldPassword, salt))
                            .add("newPassword", AesEncrypt.encryptPassword(newPassword, salt))
                            .add("confirmPassword", AesEncrypt.encryptPassword(confirmPassword, salt))
                            .add("captchaResponse", captcha)
                            .build()
                    )
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body.string()
            }
            HtmlFormParser.checkAndThrow(body)
            val result = gson.fromJson(body, SubmitResponse::class.java)
            Result.success(
                PasswordChangeSubmitResult(
                    isSuccess = result.success,
                    message = result.errorMsg,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "提交修改密码失败", e)
            Result.failure(e)
        }
    }

    /** 用账号 cookie 构建隔离客户端（同 UserNameEditApi 模式） */
    private data class SubmitResponse(val success: Boolean = false, val errorMsg: String = "")
}
