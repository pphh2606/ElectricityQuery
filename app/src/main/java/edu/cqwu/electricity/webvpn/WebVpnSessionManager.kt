package edu.cqwu.electricity.webvpn

import edu.cqwu.electricity.logging.AppLog
import android.webkit.CookieManager
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.CasLoginException
import edu.cqwu.electricity.login.data.CasLoginFlow
import edu.cqwu.electricity.login.data.CookieParser
import edu.cqwu.electricity.login.data.CookieStoreOkHttpJar
import edu.cqwu.electricity.login.data.RedirectChainFollower
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.login.data.SessionExpiryReason
import edu.cqwu.electricity.payment.data.HttpClientFactory
import okhttp3.CookieJar
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * CAS auto login for the WebVPN proxy.
 */
object WebVpnSessionManager {

    private class AuthGate {
        val latch = CountDownLatch(1)

        @Volatile
        var result: Any? = null

        @Volatile
        var failure: Throwable? = null
    }

    private val inflight = ConcurrentHashMap<String, AuthGate>()

    internal fun <T> runSingleFlight(key: String, block: () -> T): T {
        val gate = AuthGate()
        val existing = inflight.putIfAbsent(key, gate)
        if (existing != null) {
            try {
                existing.latch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException("等待 WebVPN 登录时被中断", e)
            }
            existing.failure?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            return existing.result as T
        }

        try {
            val value = block()
            gate.result = value
            return value
        } catch (t: Throwable) {
            gate.failure = t
            throw t
        } finally {
            gate.latch.countDown()
            inflight.remove(key, gate)
        }
    }

    private const val TAG = "WebVpnSessionManager"

    fun authenticate(
        protectedUrl: String,
        cookieJar: CookieJar? = CookieStoreOkHttpJar,
    ) {
        if (!WebVpnSettings.enabled) return

        val account = resolveSavedAccount()
            ?: throw SessionExpiredException(
                "未找到已保存的账号密码，无法自动登录 WebVPN，请先保存密码",
                SessionExpiryReason.NO_SAVED_ACCOUNT,
            )
        val lockKey = "${account.first}@${System.identityHashCode(cookieJar ?: CookieStoreOkHttpJar)}"
        runSingleFlight(lockKey) {
            doAuthenticate(protectedUrl, cookieJar, account)
        }
    }

    private fun doAuthenticate(
        protectedUrl: String,
        cookieJar: CookieJar?,
        account: Pair<String, String>,
    ) {
        val client = HttpClientFactory.create(
            cookieJar = cookieJar ?: CookieStoreOkHttpJar,
            followRedirects = false,
            includeWebVpn = false,
        )
        val startUrl = if (WebVpnEncoder.isWebVpnUrl(protectedUrl)) {
            protectedUrl
        } else {
            WebVpnEncoder.transform(protectedUrl)
        }

        val loginPage = RedirectChainFollower.followToCasLoginPage(
            client = client,
            startUrl = startUrl,
            tag = TAG,
        ) ?: return
        val (loginUrl, loginHtml) = loginPage

        val username = account.first
        val password = account.second

        val outcome = try {
            CasLoginFlow.login(
                client = client,
                loginPageUrl = loginUrl,
                username = username,
                password = password,
                extraHeaders = mapOf(
                    "Origin" to WebVpnEncoder.PROXY_BASE,
                    "Referer" to loginUrl,
                    "X-Requested-With" to "edu.cqwu.electricity",
                ),
                enlinkVpn = true,
                existingHtml = loginHtml,
            )
        } catch (e: CasLoginException.CaptchaRequired) {
            throw SessionExpiredException(
                "CAS 需要验证码，无法自动登录，请手动完成 WebVPN 登录",
                SessionExpiryReason.CAPTCHA_REQUIRED,
            )
        } catch (e: CasLoginException.MissingField) {
            throw SessionExpiredException(
                "无法获取 WebVPN CAS 登录参数 ${e.field}",
                SessionExpiryReason.PROTOCOL_MISMATCH,
            )
        } catch (e: CasLoginException.LoginRejected) {
            throw SessionExpiredException(
                "WebVPN CAS 登录失败：账号或密码错误",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        }

        val location = outcome.location
            ?: throw SessionExpiredException(
                "WebVPN CAS 登录未返回重定向",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        val ticketUrl = RedirectChainFollower.resolve(loginUrl, location)
        AppLog.url(TAG, "CAS 登录成功，跟踪 ticket 回调: $ticketUrl")
        val finalPage = try {
            RedirectChainFollower.followToCasLoginPage(
                client = client,
                startUrl = ticketUrl,
                tag = TAG,
                referer = loginUrl,
            )
        } catch (e: IOException) {
            throw SessionExpiredException(
                "WebVPN CAS ticket 校验失败：${e.message}",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        }
        if (finalPage != null) {
            throw SessionExpiredException(
                "WebVPN CAS ticket 校验后仍返回登录页",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        }

        AppLog.d(TAG, "WebVPN CAS 自动登录完成")
        persistWebVpnCookies(cookieJar)
    }

    private fun persistWebVpnCookies(cookieJar: CookieJar?) {
        if (cookieJar != null && cookieJar !== CookieStoreOkHttpJar) return

        val cookieString = CookieManager.getInstance().getCookie(WebVpnEncoder.PROXY_BASE)
            ?: return
        val parsed = CookieParser.parse(cookieString)
        if (parsed.isEmpty()) return
        // 将 clientvpn cookie 合并进当前激活账号的持久化登录状态，切换账号后仍可恢复
        AccountSessionStore.mergeSystemCookiesForActiveUser(WebVpnEncoder.PROXY_BASE)
        AppLog.d(TAG, "WebVPN 自动登录完成，已同步 clientvpn Cookie: ${parsed.keys.sorted()}")
    }

    private fun resolveSavedAccount(): Pair<String, String>? {
        val active = AccountSessionStore.getActiveAccount()
        if (active != null) {
            val password = active.password
            if (!password.isNullOrBlank()) return active.username to password
        }
        return AccountSessionStore.getAllAccounts()
            .firstOrNull { !it.password.isNullOrBlank() }
            ?.let { it.username to it.password!! }
    }
}
