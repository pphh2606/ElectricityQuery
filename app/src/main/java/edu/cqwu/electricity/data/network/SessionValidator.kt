package edu.cqwu.electricity.data.network

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
 * Cookie 会话验证器。
 *
 * 使用 GET /authserver/index.do 验证 CASTGC 是否有效，
 * 同时从返回的个人设置页 HTML 中提取学号和实名信息。
 *
 * 原理：
 * - 有效 Cookie → 返回 200 + 个人设置页 HTML（含 data-name="id" 学号、data-name="name" 姓名）
 * - 无效 Cookie → 被重定向回 CAS 登录页 HTML（含 casLoginForm / pwdDefaultEncryptSalt 特征）
 *
 * 参考抓包数据：
 * ```html
 * <div class="index-nav-name" data-name="name">示例用户</div>
 * <div class="index-nav-id" data-name="id">2024XXXX0000</div>
 * ```
 */
object SessionValidator {

    private const val INDEX_URL =
        "https://authserver.cqwu.edu.cn/authserver/index.do?locale=zh_CN"

    /**
     * 验证指定用户的 Cookie 是否有效。
     *
     * @param userStore 该用户的 [UserCookieStore]
     * @param syncFromSystem 是否在 UserCookieStore 为空时从系统 [CookieManager] 兜底导入。
     *                       启动验证时应为 true（系统 CookieManager 中有上次登录的 Cookie）；
     *                       账号切换时应为 false（系统 CookieManager 中是当前用户的 Cookie，不是目标用户的）。
     * @return [SessionValidationResult.Valid]（Cookie 有效）、
     *         [SessionValidationResult.Invalid]（Cookie 过期）、
     *         [SessionValidationResult.NetworkError]（网络异常）
     */
    /**
     * 验证指定用户的 Cookie 是否有效。
     *
     * 委托给 [SessionManager.validateCookie]，保留此方法作为向后兼容入口。
     */
    suspend fun validate(
        userStore: UserCookieStore,
        syncFromSystem: Boolean = true,
    ): SessionValidationResult {
        return SessionManager.validateCookie(userStore, syncFromSystem)
    }
}

/**
 * CAS 登录状态检测工具（向后兼容包装器）。
 *
 * 实际逻辑已迁移到 [HtmlFormParser]，此类保留以避免大规模修改调用方。
 * 所有方法直接委托给 [HtmlFormParser]。
 */
object SessionChecker {
    fun isCasLoginPage(html: String): Boolean = HtmlFormParser.isCasLoginPage(html)
    fun checkAndThrow(html: String) = HtmlFormParser.checkAndThrow(html)
}

/**
 * Session 过期异常。
 * 当 API 请求响应被重定向到 CAS 登录页时抛出此异常。
 * UI 层捕获后应提示用户重新登录。
 */
class SessionExpiredException(message: String) : Exception(message)
