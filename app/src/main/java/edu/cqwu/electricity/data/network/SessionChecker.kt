package edu.cqwu.electricity.data.network

/**
 * CAS 登录状态检测工具。
 *
 * 通过检查 HTTP 响应 HTML 是否包含 CAS 登录页的特征字符串，
 * 判断用户 Session 是否已过期（被重定向到 CAS 登录页）。
 *
 * 所有 API 模块应统一使用此工具，而非各自实现 [isCasLoginPage] 方法。
 */
object SessionChecker {

    /** CAS 登录页 HTML 特征信号列表 */
    private val CAS_LOGIN_SIGNALS = listOf(
        "authserver/login",
        "id=\"casLoginForm\"",
        "pwdDefaultEncryptSalt",
        "CASTGC",
    )

    /**
     * 判断响应 HTML 是否为 CAS 登录页。
     *
     * @param html HTTP 响应的 HTML 字符串
     * @return true 表示该页面是 CAS 登录页（Session 过期）
     */
    fun isCasLoginPage(html: String): Boolean {
        return CAS_LOGIN_SIGNALS.any { html.contains(it, ignoreCase = true) }
    }

    /**
     * 检查 HTML 是否为 CAS 登录页，是则抛出 [SessionExpiredException]。
     *
     * @param html HTTP 响应的 HTML 字符串
     * @throws SessionExpiredException 如果检测到 CAS 登录页
     */
    fun checkAndThrow(html: String) {
        if (isCasLoginPage(html)) {
            throw SessionExpiredException("Session 已过期，请重新登录")
        }
    }
}
