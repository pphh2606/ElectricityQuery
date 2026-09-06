package edu.cqwu.electricity.login.data

import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.net.SessionValidationResult
import edu.cqwu.electricity.common.net.UserAwareCookieJar
import edu.cqwu.electricity.common.net.UserCookieStore
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.net.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 会话管理器（Step 1：认证验证）。
 *
 * 负责验证 CAS 登录态是否有效，不涉及服务级授权。
 * 服务级 CAS ticket 交换已迁移到 [edu.cqwu.electricity.login.domain.CasAuthFlow]。
 *
 * 提供以下能力：
 * 1. **Cookie 验证**：[validateCookie] — 验证 CASTGC 是否有效，提取用户ID（数字学号）/实名
 * 2. **学号获取**：[fetchUserInfo] — 带账号 cookie 请求 index.do 提取用户ID（数字学号）/实名
 * 3. **会话检测**：响应为 CAS 登录页时抛 [edu.cqwu.electricity.common.net.SessionExpiredException]（[edu.cqwu.electricity.common.net.HtmlFormParser.checkAndThrow]）
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
     * 验证指定账号的登录状态（cookie）是否有效。
     *
     * 使用 GET /authserver/index.do 验证 CASTGC 是否有效，
     * 同时从返回的个人设置页 HTML 中提取用户ID（数字学号）和实名信息，
     * 并顺手回填该账号的数字学号（[AccountSessionStore.updateStudentId]，启动验证场景零额外请求）。
     *
     * @param cookies 该账号持久化的 cookie 集合（domain → name→value），为空直接判定无效。
     * @return [edu.cqwu.electricity.common.net.SessionValidationResult.Valid]、[edu.cqwu.electricity.common.net.SessionValidationResult.Invalid]、[edu.cqwu.electricity.common.net.SessionValidationResult.NetworkError]
     */
    suspend fun validateCookie(
        cookies: Map<String, Map<String, String>>,
    ): SessionValidationResult {
        val result = fetchUserInfo(cookies)
        return if (result.isSuccess) {
            val (username, realName) = result.getOrThrow()
            AppLog.d("SessionManager", "Cookie 有效！用户ID=${username}, 实名=${realName}")
            // 启动验证回填老账号学号（index.do 请求已发生，无额外网络开销）
            val active = AccountSessionStore.getActiveAccount()
            if (active != null) AccountSessionStore.updateStudentId(active.id, username)
            SessionValidationResult.Valid
        } else {
            when (val e = result.exceptionOrNull()) {
                is SessionExpiredException -> {
                    AppLog.d("SessionManager", "Cookie 无效：会话失效或响应为 CAS 登录页")
                    SessionValidationResult.Invalid
                }
                is SocketTimeoutException -> {
                    AppLog.w("SessionManager", "验证 Cookie 网络超时", e)
                    SessionValidationResult.NetworkError("网络超时，请检查网络连接")
                }
                is UnknownHostException -> {
                    AppLog.w("SessionManager", "验证 Cookie DNS 解析失败", e)
                    SessionValidationResult.NetworkError("无法连接服务器，请检查网络")
                }
                else -> {
                    AppLog.w("SessionManager", "验证 Cookie 时发生异常", e)
                    SessionValidationResult.NetworkError("网络异常: ${e?.message}")
                }
            }
        }
    }

    /**
     * 带账号 cookie 请求 index.do 提取用户ID（数字学号）和实名。
     *
     * 供登录成功后获取学号、启动验证回填等场景复用（index.do 请求与解析的单点实现）。
     *
     * @param cookies 该账号持久化的 cookie 集合（domain → name → value）
     * @return 成功返回 `(数字学号, 实名)`；cookie 为空/会话失效抛 [SessionExpiredException]，网络异常原样返回 failure
     */
    suspend fun fetchUserInfo(
        cookies: Map<String, Map<String, String>>,
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            if (cookies.isEmpty()) {
                return@withContext Result.failure(SessionExpiredException("cookies 为空"))
            }

            // 将账号 cookie 装载到临时 UserCookieStore，用 UserAwareCookieJar 隔离验证
            val userStore = UserCookieStore().also { it.loadFrom(cookies) }
            val client = baseClient.newBuilder()
                .cookieJar(UserAwareCookieJar(userStore))
                .build()

            val html = client.newCall(
                Request.Builder()
                    .url(INDEX_URL)
                    .get()
                    .build()
            ).execute().use { it.body.string() }

            // 响应为 CAS 登录页（会话失效）→ 抛 SessionExpiredException
            HtmlFormParser.checkAndThrow(html)

            val username = HtmlFormParser.extractUsername(html)?.trim()
                ?: return@withContext Result.failure(
                    SessionExpiredException("无法从 index.do 提取用户ID，HTML长度=${html.length}")
                )
            val realName = HtmlFormParser.extractRealName(html)?.trim() ?: ""
            Result.success(username to realName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
