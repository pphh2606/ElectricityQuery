package edu.cqwu.electricity.login.model

import edu.cqwu.electricity.login.data.SavedAccount

/**
 * 会话阶段 V2（纯模型，无 Android 依赖）。
 *
 * 描述"当前激活账号的会话"处于什么状态，供数据层发布、UI/领域层订阅；
 * 只携带轻量元信息，不携带 Cookie/密码等敏感原文。
 *
 * - [LoggedOut]：无激活账号（或已被登出/清空）
 * - [Active]：有激活账号条目；[hasCookies] 表示该条目当前是否带有登录态 Cookie
 */
sealed interface SessionStateV2 {
    data object LoggedOut : SessionStateV2

    data class Active(
        val accountId: String,
        val username: String,
        val hasCookies: Boolean,
    ) : SessionStateV2
}

/**
 * 由账号条目推导会话状态；账号为 null 视为 [SessionStateV2.LoggedOut]。
 */
fun sessionStateOf(account: SavedAccount?): SessionStateV2 =
    account?.let { SessionStateV2.Active(it.id, it.username, it.hasLoginState) }
        ?: SessionStateV2.LoggedOut
