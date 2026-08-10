package edu.cqwu.electricity.login.data

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
 * WebVPN 自动登录失败原因，供调用方区分“需要用户手动处理”和“协议/页面结构异常”。
 */
enum class SessionExpiryReason {
    NO_SAVED_ACCOUNT,
    CAPTCHA_REQUIRED,
    LOGIN_REJECTED,
    PROTOCOL_MISMATCH,
}

/**
 * Session 过期异常。
 * 当 API 请求响应被重定向到 CAS 登录页时抛出此异常，UI 层捕获后提示用户重新登录。
 */
class SessionExpiredException(
    message: String,
    val reason: SessionExpiryReason? = null,
) : Exception(message)
