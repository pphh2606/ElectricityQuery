package edu.cqwu.electricity.login.data

import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * 退出登录 API。
 *
 * 对应抓包接口：
 * - GET authserver.cqwu.edu.cn/authserver/logout → 302 重定向到 /authserver/login，
 *   Set-Cookie 清除 CASTGC / CASPRIVACY / iPlanetDirectoryPro（服务端会话注销）。
 *
 * 成功判定：仅响应码为 3xx 视为成功（302 为正常登出信号）；其他任何响应码
 * （2xx / 4xx / 5xx）一律抛异常。
 *
 * 无状态设计：每次调用用账号 cookie 构建隔离的 UserCookieStore + OkHttpClient
 * （同 UserNameEditApi 模式），不跟随重定向——必须拿到服务端清除 cookie 的首个 3xx 响应。
 */
object LogoutApi {

    private const val TAG = "LogoutApi"
    private const val LOGOUT_URL = "https://authserver.cqwu.edu.cn/authserver/logout"

    /**
     * 调用服务端退出登录接口，使该账号在服务端的会话失效。
     *
     * @param username 要退出登录的登录用户名（学号或登录别名），仅用于日志
     * @param cookies 该账号当前持久化的登录状态（cookie 集合），供退出登录请求携带
     * @return 仅服务端响应 3xx 时成功；否则返回 [Result.failure]（含非 3xx 响应与网络异常）
     */
    suspend fun logout(username: String, cookies: Map<String, Map<String, String>>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val code = HttpClientFactory.createIsolated(cookies, followRedirects = false).newCall(
                    Request.Builder()
                        .url(LOGOUT_URL)
                        .addHeader("X-Requested-With", "edu.cqwu.electricity")
                        .get()
                        .build()
                ).execute().use { resp ->
                    resp.code
                }
                if (code !in 300..399) {
                    throw IOException("退出登录响应码异常: HTTP $code")
                }
                AppLog.d(TAG, "服务端登出成功: $username (HTTP $code)")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "服务端登出失败: $username", e)
                Result.failure(e)
            }
        }
}
