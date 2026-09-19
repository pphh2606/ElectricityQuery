package edu.cqwu.electricity.jwxt.core.model

/**
 * 教务的一个服务入口（首页九宫格 / 更多服务列表的一项）。
 *
 * 首页 `listLabelService` 与「更多服务」`listLabelServiceSet` 都用它，所以放在共享层。
 */
data class JwxtService(
    val serviceKey: String = "",
    val serviceName: String = "",
    /** 相对路径，如 `icon/service/cjcx@2x.png`；用 `JwxtConstants.iconUrl` 补全 */
    val serviceIcon: String = "",
    /** hash 路由，如 `#/cjcx_v5`；用 `JwxtConstants.pageUrl` 补全 */
    val url: String = "",
    val appId: String = "",
)
