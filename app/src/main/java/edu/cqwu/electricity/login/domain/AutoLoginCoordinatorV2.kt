package edu.cqwu.electricity.login.domain

import edu.cqwu.electricity.common.net.CookieStoreOkHttpJar
import edu.cqwu.electricity.login.data.ServiceLoginManager
import edu.cqwu.electricity.webvpn.WebVpnSessionManager
import okhttp3.CookieJar

/**
 * 自动登录协调器 V2（domain 门面）：业务 API / 网络拦截器统一经此"确保某 URL 已具备会话"。
 *
 * - [ensureService]：业务二级站的服务会话（CAS ticket 交换，复用 [ServiceLoginManager]；
 *   登录态失效/授权失败抛 [edu.cqwu.electricity.common.net.SessionExpiredException]）
 * - [ensureWebVpn]：WebVPN 通道自动登录（复用 [WebVpnSessionManager]，其内部自带单飞去重，
 *   失败不影响已有会话）
 *
 * 未来如需增加"先验证会话再认证/按服务缓存"等策略，只需在本协调器内扩展，业务代码不变。
 */
object AutoLoginCoordinatorV2 {

    fun ensureService(
        protectedUrl: String,
        serviceDomain: String? = null,
        expectedCookie: String? = null,
    ) {
        ServiceLoginManager.ensureLogin(protectedUrl, serviceDomain, expectedCookie)
    }

    fun ensureWebVpn(
        protectedUrl: String,
        cookieJar: CookieJar? = CookieStoreOkHttpJar,
    ) {
        WebVpnSessionManager.authenticate(protectedUrl, cookieJar)
    }
}
