package edu.cqwu.electricity.login.domain

import android.webkit.CookieManager
import edu.cqwu.electricity.common.net.CookieStoreOkHttpJar
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.RedirectChainFollower
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.net.SessionExpiryReason
import edu.cqwu.electricity.common.net.WebVpnEncoder
import edu.cqwu.electricity.common.net.WebVpnSettings
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.CasLoginException
import edu.cqwu.electricity.login.data.CasLoginFlow
import edu.cqwu.electricity.logging.AppLog
import java.io.IOException

/**
 * 统一 CAS 会话认证 —— 融合 WebVPN 代理域登录与直连服务域 ticket 交换两套逻辑。
 *
 * 原有两套（WebVpnSessionManager / ServiceLoginManager）各自实现"发现 CAS 登录页 → 认证 →
 * 校验 → 持久化"，仅凭据域不同。此处收敛为一个入口：
 * - [ensureClientVpnActive]：代理域（clientvpn）**完整密码登录**（enlink CAS），由网络拦截器在
 *   检测到 302 需要登录时触发；
 * - [ensureServiceActive]：服务域（ehall/campusphere 等）**CAS ticket 交换**（基于已存在的
 *   CASTGC，不再输密码），由业务 API 经 [AutoLoginCoordinatorV2] 触发。
 *
 * 两者共用 [SessionRegistry] 的"每域 in-flight 单飞"：同一凭据域并发触发只认证一次，
 * 修复双线程重复登录竞态；不做会话活性缓存，由 [AuthSession] 按实况判定。
 *
 * 所有登录动作保持**同步阻塞**（OkHttp execute），兼容被拦截器在请求线程调用。
 */
object CasAuthFlow {

    private const val TAG = "CasAuthFlow"

    /** 代理域会话的注册键（实际 cookie 域为 [WebVpnEncoder.PROXY_BASE]） */
    private const val CLIENT_VPN_SESSION = "clientvpn"

    /** 最近一次触发代理登录的目标 URL（代理域为单键会话，供闭包读取最新值而非注册时快照） */
    @Volatile
    private var clientVpnTarget: String? = null

    /**
     * 代理域完整登录入口（由 WebVpnInterceptor 在需要登录时同步调用）。
     *
     * @param protectedUrl 触发登录的原始目标 URL（WebVpnEncoder.decode 之后），仅用于取登录页
     */
    fun ensureClientVpnActive(protectedUrl: String) {
        clientVpnTarget = protectedUrl
        SessionRegistry.getOrCreate(CLIENT_VPN_SESSION) {
            doClientVpnLogin(clientVpnTarget ?: protectedUrl)
        }.ensureActive()
    }

    /**
     * 服务域 ticket 交换入口（经 [AutoLoginCoordinatorV2] 由业务 API 调用）。
     *
     * 注意：**不做 cookie 存在性短路** —— 服务端会话可能已失效而客户端 cookie 仍残留，
     * 只有真实走一遍重定向链才能确认；因此始终执行认证（受会话层 single-flight 保护，
     * 并发触发只交换一次，修复双线程重复登录）。
     *
     * @param protectedUrl 服务首页 URL（如 ehall appshow / campusphere index.html）
     * @param serviceDomain 服务 cookie 域（scheme://host）；为 null 时仅做会话校验
     * @param expectedCookie 该服务要求的最小凭证 cookie 名（如 MOD_AUTH_CAS）；为 null 同上
     */
    fun ensureServiceActive(
        protectedUrl: String,
        serviceDomain: String?,
        expectedCookie: String?,
    ) {
        SessionRegistry.getOrCreate(serviceDomain ?: protectedUrl) {
            doServiceTicketExchange(protectedUrl, serviceDomain, expectedCookie)
        }.ensureActive()
    }

    // ─────────────────────────────────────────────
    //  代理域完整登录（原 WebVpnSessionManager.doAuthenticate）
    // ─────────────────────────────────────────────

    private fun doClientVpnLogin(protectedUrl: String) {
        if (!WebVpnSettings.enabled) return

        val account = resolveSavedAccount()
            ?: throw SessionExpiredException(
                "未找到已保存的账号密码，无法自动登录 WebVPN，请先保存密码",
                SessionExpiryReason.NO_SAVED_ACCOUNT,
            )
        val client = HttpClientFactory.create(
            cookieJar = CookieStoreOkHttpJar,
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

        val outcome = try {
            CasLoginFlow.login(
                client = client,
                loginPageUrl = loginUrl,
                username = account.first,
                password = account.second,
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
        // 登录成功后把 clientvpn cookie 合并进当前激活账号的持久化登录状态
        AccountSessionStore.mergeSystemCookiesForActiveUser(WebVpnEncoder.PROXY_BASE)
    }

    // ─────────────────────────────────────────────
    //  服务域 ticket 交换（原 ServiceLoginManager.ensureLogin）
    // ─────────────────────────────────────────────

    private fun doServiceTicketExchange(
        protectedUrl: String,
        serviceDomain: String?,
        expectedCookie: String?,
    ) {
        AppLog.d(TAG, "服务域 ticket 交换: $protectedUrl")

        val client = HttpClientFactory.casFlowClient
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

        if (serviceDomain == null || expectedCookie == null) return // 纯校验模式：链未回登录页即通过

        val cookies = CookieManager.getInstance().getCookie(serviceDomain)
        val hasCookie = cookies?.contains("$expectedCookie=") == true
        AppLog.d(TAG, "Cookie check: $expectedCookie=$hasCookie")

        if (!hasCookie) {
            AppLog.w(TAG, "service authorization failed: $serviceDomain missing $expectedCookie")
            throw SessionExpiredException("服务授权失败，请重新登录")
        }

        // 将服务域登录 cookie 合并进当前激活账号的持久化登录状态
        AccountSessionStore.mergeSystemCookiesForActiveUser(serviceDomain)
    }

    // ─────────────────────────────────────────────
    //  账号解析
    // ─────────────────────────────────────────────

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
