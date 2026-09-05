package edu.cqwu.electricity.accountmanagerv2

import com.google.gson.Gson
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.net.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/**
 * 修改用户名页面当前值（登录别名 + 昵称）。
 */
data class UserNameEditInfo(
    val alias: String,
    val nickName: String,
)

/**
 * 保存结果。
 */
data class UserNameSubmitResult(
    val isSuccess: Boolean,
    val message: String,
)

/**
 * CAS 修改用户名（登录别名 + 昵称）API。
 *
 * 对应抓包接口：
 * - GET  mobileUserAttrEdit.do → 当前别名（span id="alias"）与昵称（input name="nickName"）
 * - POST checkAlias.do        → {"jsonValidateReturn":true/false} 别名校验
 * - POST mobileUserAttrEdit.do → {"returnValue":"修改成功","isSuccess":true} 保存
 *
 * 无状态设计：每次调用用账号 cookie 构建隔离的 UserCookieStore + OkHttpClient
 * （同 SessionManager.validateCookie 模式），响应为 CAS 登录页时抛 [SessionExpiredException]。
 */
class UserNameEditApi {

    private val gson = Gson()

    companion object {
        private const val TAG = "UserNameEditApi"
        private const val EDIT_URL = "https://authserver.cqwu.edu.cn/authserver/mobileUserAttrEdit.do"
        private const val CHECK_ALIAS_URL = "https://authserver.cqwu.edu.cn/authserver/checkAlias.do"
        private const val ALIAS_REGEX = """<span[^>]*\bid\s*=\s*["']alias["'][^>]*>(.*?)</span>"""
    }

    /** 加载当前登录别名与昵称 */
    suspend fun loadCurrent(cookies: Map<String, Map<String, String>>): Result<UserNameEditInfo> =
        withContext(Dispatchers.IO) {
            try {
                val html = HttpClientFactory.createIsolated(cookies).newCall(
                    Request.Builder()
                        .url(EDIT_URL)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body.string()
                }
                HtmlFormParser.checkAndThrow(html)
                Result.success(
                    UserNameEditInfo(
                        alias = HtmlFormParser.extractRegex(html, ALIAS_REGEX)?.trim().orEmpty(),
                        nickName = HtmlFormParser.extractInputValue(html, "nickName")?.trim().orEmpty(),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "加载当前用户名失败", e)
                Result.failure(e)
            }
        }

    /** 校验别名是否可用（未被占用且符合要求） */
    suspend fun checkAlias(cookies: Map<String, Map<String, String>>, value: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val body = HttpClientFactory.createIsolated(cookies).newCall(
                    Request.Builder()
                        .url(CHECK_ALIAS_URL)
                        .post(FormBody.Builder().add("validateValue", value).build())
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body.string()
                }
                HtmlFormParser.checkAndThrow(body)
                val result = gson.fromJson(body, CheckAliasResponse::class.java)
                Result.success(result.jsonValidateReturn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "别名校验失败: $value", e)
                Result.failure(e)
            }
        }

    /** 提交修改（别名 + 昵称） */
    suspend fun submit(
        cookies: Map<String, Map<String, String>>,
        alias: String,
        nickName: String,
    ): Result<UserNameSubmitResult> = withContext(Dispatchers.IO) {
        try {
            val body = HttpClientFactory.createIsolated(cookies).newCall(
                Request.Builder()
                    .url(EDIT_URL)
                    .post(
                        FormBody.Builder()
                            .add("alias", alias)
                            .add("nickName", nickName)
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
                UserNameSubmitResult(
                    isSuccess = result.isSuccess,
                    message = result.returnValue,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "保存用户名失败", e)
            Result.failure(e)
        }
    }

    private data class CheckAliasResponse(val jsonValidateReturn: Boolean = false)
    private data class SubmitResponse(val isSuccess: Boolean = false, val returnValue: String = "")
}
