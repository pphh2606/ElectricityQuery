package edu.cqwu.electricity.data.network.auth

/**
 * CAS 会话验证结果：学号和实名
 *
 * @param username 学号，如 "2024XXXX0000"
 * @param realName 实名，如 "示例用户"
 */
data class CasUserInfo(
    val username: String,
    val realName: String,
)

/**
 * Cookie 验证结果密封类。
 * 区分「有效」「Cookie 过期」「网络错误」三种情况。
 */
sealed class SessionValidationResult {
    data class Valid(val info: CasUserInfo) : SessionValidationResult()
    data object Invalid : SessionValidationResult()
    data class NetworkError(val message: String) : SessionValidationResult()
}

/**
 * Session 过期异常。
 * 当 API 请求响应被重定向到 CAS 登录页时抛出此异常。
 * UI 层捕获后应提示用户重新登录。
 */
class SessionExpiredException(message: String) : Exception(message)
