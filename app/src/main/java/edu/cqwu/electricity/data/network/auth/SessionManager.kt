package edu.cqwu.electricity.data.network.auth

import android.util.Log
import edu.cqwu.electricity.data.network.common.UserAgentInterceptor
import edu.cqwu.electricity.data.network.common.UserAwareCookieJar
import edu.cqwu.electricity.data.network.common.UserCookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 会话管理器（Step 1：认证验证）。
 *
 * 负责验证 CAS 登录态是否有效，不涉及服务级授权。
 * 服务级 CAS ticket 交换已迁移到 [edu.cqwu.electricity.data.network.sso.ServiceLoginManager]。
 *
 * 提供以下能力：
 * 1. **Cookie 验证**：[validateCookie] — 验证 CASTGC 是否有效，提取学号/实名
 * 2. **会话检测**：[isCasLoginPage] / [checkSessionOrThrow] — 检测响应是否为 CAS 登录页
 */
object SessionManager {

    private const val INDEX_URL =
        "https://authserver.cqwu.edu.cn/authserver/index.do?locale=zh_CN"

    /** 复用的 OkHttpClient 基础配置，cookieJar 通过 newBuilder() 动态替换 */
    private val baseClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor)
            .build()
    }

    // ═══════════════════════════════════════════
    //  Cookie 验证
    // ═══════════════════════════════════════════

    /**
     * 验证指定用户的 Cookie 是否有效。
     *
     * 使用 GET /authserver/index.do 验证 CASTGC 是否有效，
     * 同时从返回的个人设置页 HTML 中提取学号和实名信息。
     *
     * @param userStore 该用户的 [UserCookieStore]
     * @param syncFromSystem 是否在 UserCookieStore 为空时从系统 CookieManager 兜底导入。
     * @return [SessionValidationResult.Valid]、[SessionValidationResult.Invalid]、[SessionValidationResult.NetworkError]
     */
    suspend fun validateCookie(
        userStore: UserCookieStore,
        syncFromSystem: Boolean = true,
    ): SessionValidationResult = withContext(Dispatchers.IO) {
        try {
            if (syncFromSystem) {
                val existingCookie = userStore.getCookie("https://authserver.cqwu.edu.cn")
                if (existingCookie.isNullOrBlank()) {
                    Log.d("SessionManager", "UserCookieStore 为空，从系统 CookieManager 兜底导入")
                    userStore.syncFromCookieManager()
                }
            }

            val client = baseClient.newBuilder()
                .cookieJar(UserAwareCookieJar(userStore))
                .build()

            val response = client.newCall(
                Request.Builder()
                    .url(INDEX_URL)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()

            if (HtmlFormParser.isCasLoginPage(html)) {
                Log.d("SessionManager", "Cookie 无效：响应为 CAS 登录页")
                return@withContext SessionValidationResult.Invalid
            }

            val username = HtmlFormParser.extractUsername(html)
                ?: run {
                    Log.w("SessionManager", "无法从 index.do 提取学号，HTML长度=${html.length}")
                    return@withContext SessionValidationResult.Invalid
                }

            val realName = HtmlFormParser.extractRealName(html) ?: ""

            Log.d("SessionManager", "Cookie 有效！学号=$username, 实名=$realName")

            SessionValidationResult.Valid(CasUserInfo(username = username, realName = realName))
        } catch (e: SocketTimeoutException) {
            Log.w("SessionManager", "验证 Cookie 网络超时", e)
            SessionValidationResult.NetworkError("网络超时，请检查网络连接")
        } catch (e: UnknownHostException) {
            Log.w("SessionManager", "验证 Cookie DNS 解析失败", e)
            SessionValidationResult.NetworkError("无法连接服务器，请检查网络")
        } catch (e: Exception) {
            Log.w("SessionManager", "验证 Cookie 时发生异常", e)
            SessionValidationResult.NetworkError("网络异常: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════
    //  会话检测（委托 HtmlFormParser）
    // ═══════════════════════════════════════════

    /**
     * 判断响应 HTML 是否为 CAS 登录页。
     */
    fun isCasLoginPage(html: String): Boolean = HtmlFormParser.isCasLoginPage(html)

    /**
     * 检查 HTML 是否为 CAS 登录页，是则抛出 [SessionExpiredException]。
     */
    fun checkSessionOrThrow(html: String) = HtmlFormParser.checkAndThrow(html)
}
