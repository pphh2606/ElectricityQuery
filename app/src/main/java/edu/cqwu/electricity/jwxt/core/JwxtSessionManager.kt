package edu.cqwu.electricity.jwxt.core

import edu.cqwu.electricity.common.net.CookieParser
import edu.cqwu.electricity.common.net.CookieStore
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.RedirectChainFollower
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 教务（jwfw.cqwu.edu.cn/jwmobile）JWT 凭证管理器 —— 与 [edu.cqwu.electricity.payment.data.PaySessionManager]
 * 同构：两者都是"业务 JWT 通过 302 的 Location 参数下发"。
 *
 * 抓包实证的完整链路（`fortest/教务系统登录.har.json`）：
 * ```
 * GET /jwmobile/auth/index
 *   → 302 authserver.cqwu.edu.cn/authserver/login?service=…%2Fjwmobile%2Fauth%2Findex
 *   → （App 已登录，CookieJar 里的 CASTGC 让 CAS 直接发 ticket，无需再输密码）
 *   → 302 /jwmobile/auth/index?ticket=ST-…（服务端在此校验 ticket 并种下 JSESSIONID）
 *   → 302 /jwmobile/auth/index
 *   → 302 /jwmobile/index#/?token=<JWT>      ← token 在这一跳的 Location 里
 * ```
 * 因此 [refreshToken] **只有一条路径**：交给 [RedirectChainFollower.followToLocationToken] 走完整条链。
 * 不依赖 WebView，不做分支判断；CAS 会话失效时该方法会命中登录页并抛
 * [edu.cqwu.electricity.common.net.SessionExpiredException]。
 *
 * token 存进 CookieManager（与网页端行为一致），业务请求再以同名请求头携带。
 */
object JwxtSessionManager {

    private const val TAG = "JwxtSessionManager"

    /** token 在 CookieManager 里的名字（网页端写入的同名 cookie） */
    const val TOKEN_COOKIE_NAME = "Authorization"

    private val tokenMutex = Mutex()

    /** `casFlowClient` 不自动跟随重定向（必须自己读 302 的 Location），且挂共享 CookieJar（自动带 CASTGC） */
    private val client get() = HttpClientFactory.casFlowClient

    /**
     * 幂等获取有效 token：本地已有就直接用，没有就走一次 [refreshToken]。
     *
     * 并发调用只刷新一次（[tokenMutex] 内双检）。
     *
     * @throws edu.cqwu.electricity.common.net.SessionExpiredException CAS 会话失效，需要用户重新登录
     */
    suspend fun ensureToken(): String {
        readToken()?.let { return it }
        return tokenMutex.withLock {
            readToken()?.let { return@withLock it }
            val token = withContext(Dispatchers.IO) { refreshToken() }
            storeToken(token)
            AppLog.d(TAG, "教务 token 刷新成功，长度=${token.length}")
            token
        }
    }

    /** 清除本地 token（登出、切换账号、或业务接口返回 401 时调用） */
    fun clearToken() {
        runCatching {
            CookieStore.setCookie(JwxtConstants.INDEX_URL, "$TOKEN_COOKIE_NAME=; Max-Age=0")
        }
    }

    /** 跟随 302 链取 token；链落在 CAS 登录页时会抛 SessionExpiredException */
    private fun refreshToken(): String = RedirectChainFollower.followToLocationToken(
        client = client,
        startUrl = JwxtConstants.AUTH_INDEX_URL,
        tag = TAG,
    )

    private fun readToken(): String? =
        CookieParser.getValue(CookieStore.getCookie(JwxtConstants.INDEX_URL), TOKEN_COOKIE_NAME)

    private fun storeToken(token: String) {
        CookieStore.setCookie(JwxtConstants.INDEX_URL, "$TOKEN_COOKIE_NAME=$token")
    }
}
