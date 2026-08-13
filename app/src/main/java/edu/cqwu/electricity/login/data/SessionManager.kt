package edu.cqwu.electricity.login.data

import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 会话管理器（Step 1：认证验证）。
 *
 * 负责验证 CAS 登录态是否有效，不涉及服务级授权。
 * 服务级 CAS ticket 交换已迁移到 [edu.cqwu.electricity.login.data.ServiceLoginManager]。
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
        HttpClientFactory.create(
            connectTimeout = 5,
            readTimeout = 15,
            writeTimeout = 15,
            includeWebVpn = false,
        )
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
                    AppLog.d("SessionManager", "UserCookieStore 为空，从系统 CookieManager 兜底导入")
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
                AppLog.d("SessionManager", "Cookie 无效：响应为 CAS 登录页")
                return@withContext SessionValidationResult.Invalid
            }

            val username = HtmlFormParser.extractUsername(html)
                ?: run {
                    AppLog.w("SessionManager", "无法从 index.do 提取学号，HTML长度=${html.length}")
                    return@withContext SessionValidationResult.Invalid
                }

            val realName = HtmlFormParser.extractRealName(html) ?: ""

            AppLog.d(
                "SessionManager",
                "Cookie 有效！学号=${username}, 实名=${realName}",
            )

            SessionValidationResult.Valid
        } catch (e: SocketTimeoutException) {
            AppLog.w("SessionManager", "验证 Cookie 网络超时", e)
            SessionValidationResult.NetworkError("网络超时，请检查网络连接")
        } catch (e: UnknownHostException) {
            AppLog.w("SessionManager", "验证 Cookie DNS 解析失败", e)
            SessionValidationResult.NetworkError("无法连接服务器，请检查网络")
        } catch (e: Exception) {
            AppLog.w("SessionManager", "验证 Cookie 时发生异常", e)
            SessionValidationResult.NetworkError("网络异常: ${e.message}")
        }
    }

}
