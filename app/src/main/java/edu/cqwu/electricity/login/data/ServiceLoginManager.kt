package edu.cqwu.electricity.login.data

import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.payment.data.HttpClientFactory

/**
 * Ensures a third-party service session using the saved CAS CASTGC cookie.
 */
object ServiceLoginManager {

    private const val TAG = "ServiceLoginManager"

    fun ensureLogin(
        protectedUrl: String,
        serviceDomain: String? = null,
        expectedCookie: String? = null,
    ) {
        AppLog.d(TAG, ">>> ensure service login: $protectedUrl")

        val client = HttpClientFactory.create(
            cookieJar = CookieStoreOkHttpJar,
            followRedirects = false,
        )
        val loginPage = RedirectChainFollower.followToCasLoginPage(
            client = client,
            startUrl = protectedUrl,
            tolerateHttpError = true,
            tag = TAG,
        )
        if (loginPage != null) {
            AppLog.w(TAG, "CAS login page detected, CASTGC is invalid")
            throw SessionExpiredException("会话已过期，请重新登录")
        }

        if (serviceDomain != null && expectedCookie != null) {
            val cookies = CookieStore.getCookie(serviceDomain)
            val hasCookie = cookies?.contains("$expectedCookie=") == true
            AppLog.d(TAG, "Cookie check: $expectedCookie=$hasCookie")

            if (!hasCookie) {
                AppLog.w(TAG, "service authorization failed: $serviceDomain missing $expectedCookie")
                throw SessionExpiredException("服务授权失败，请重新登录")
            }

            // 将服务域登录 cookie 合并进当前激活账号的持久化登录状态，切换账号后仍可恢复
            AccountSessionStore.mergeSystemCookiesForActiveUser(serviceDomain)
        }
    }
}
