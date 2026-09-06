package edu.cqwu.electricity.login.domain

/**
 * 业务二级站自动登录协调器（domain 门面）：业务 API 统一经此"确保某 URL 已具备服务会话"。
 *
 * - [ensureService]：服务域 CAS ticket 交换，实际认证收敛到 [CasAuthFlow]
 *   （统一会话层，含每域 in-flight 单飞与持久化）；业务 API 只声明"需要该服务可用"，
 *   不关心是直连交换还是经 WebVPN 通道。
 *
 * WebVPN 代理域完整登录由网络拦截器直接触发 [CasAuthFlow.ensureClientVpnActive]，
 * 不在此协调器重复转一层。
 */
object AutoLoginCoordinatorV2 {

    /**
     * 确保 [protectedUrl] 所在服务域已完成会话建立（ticket 交换）。
     *
     * @param protectedUrl 服务首页 URL（用于触发交换链）
     * @param serviceDomain 服务 cookie 域（scheme://host）；为 null 时仅做会话校验
     * @param expectedCookie 该服务要求的最小凭证 cookie 名；为 null 时仅做会话校验
     * @throws edu.cqwu.electricity.common.net.SessionExpiredException 会话/授权失效时抛出
     */
    fun ensureService(
        protectedUrl: String,
        serviceDomain: String? = null,
        expectedCookie: String? = null,
    ) {
        CasAuthFlow.ensureServiceActive(protectedUrl, serviceDomain, expectedCookie)
    }
}
