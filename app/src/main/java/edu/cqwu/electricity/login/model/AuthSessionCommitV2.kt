package edu.cqwu.electricity.login.model

/**
 * 登录成功后要提交的"账号会话"输入 V2（纯模型，无 Android 依赖）。
 *
 * 统一账密 / 扫码 / 快捷登录等路径登录成功后的提交数据：
 * - [username]：登录用户名（学号或登录别名）
 * - [password]：明文密码；仅在账密登录且勾选"记住密码"时携带
 * - [rememberPassword]：是否持久化记住密码
 * - [cookies]：本次认证得到的完整登录态 Cookie（domain → name → value）
 * - [studentId]：数字学号（账密路径登录后联网获取；扫码路径直接来自页面）
 */
data class AuthSessionCommitV2(
    val username: String,
    val password: String? = null,
    val rememberPassword: Boolean = false,
    val cookies: Map<String, Map<String, String>> = emptyMap(),
    val studentId: String? = null,
)
