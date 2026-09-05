package edu.cqwu.electricity.login.domain

import edu.cqwu.electricity.login.data.ServiceLoginManager

/**
 * 业务二级站自动登录协调器（domain 门面）：业务 API 统一经此"确保某 URL 已具备服务会话"。
 *
 * - [ensureService]：业务二级站的服务会话（CAS ticket 交换，复用 [ServiceLoginManager]；
 *   登录态失效/授权失败抛 [edu.cqwu.electricity.common.net.SessionExpiredException]）
 *
 * WebVPN 通道的自动登录由 [edu.cqwu.electricity.webvpn.WebVpnSessionManager] 直接负责
 * （组合根注入网络层），不在此协调器重复转一层。
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
}
