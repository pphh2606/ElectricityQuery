package edu.cqwu.electricity.login.data

/**
 * 统一的 HTML 表单解析工具。
 *
 * 合并了原 CasAuthApi 和 QrLoginApi 中重复的 extractInputValue/extractRegex 逻辑，
 * 以及 SessionChecker 中的 CAS 登录页检测逻辑。
 *
 * 所有需要从 HTML 中提取信息的模块统一使用此工具。
 */
object HtmlFormParser {

    /**
     * 从 HTML 中提取指定正则表达式的第一个匹配组
     */
    fun extractRegex(html: String, pattern: String): String? {
        val regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    /**
     * 从 HTML 中提取 <input name="name"> 的 value 属性。
     *
     * 支持 name 在 value 前后两种顺序：
     * - <input name="xxx" value="yyy">
     * - <input value="yyy" name="xxx">
     */
    fun extractInputValue(html: String, name: String): String? {
        val pattern1 = Regex(
            """<input[^>]*\sname\s*=\s*["']$name["'][^>]*\svalue\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        val match1 = pattern1.find(html)
        if (match1 != null) return match1.groupValues[1]

        val pattern2 = Regex(
            """<input[^>]*\svalue\s*=\s*["']([^"']*)["'][^>]*\sname\s*=\s*["']$name["']""",
            RegexOption.IGNORE_CASE
        )
        val match2 = pattern2.find(html)
        if (match2 != null) return match2.groupValues[1]

        return null
    }

    // ═══════════════════════════════════════════
    //  CAS 登录页检测
    // ═══════════════════════════════════════════

    /**
     * CAS 登录页 HTML 特征信号列表。
     *
     * 注意：不使用 "authserver/login" 作为信号——pay 域 casLogin/ 未认证时返回的
     * JS 导航页（跳转 authserver 登录页）其 URL 就包含该子串，会被误判为登录页。
     * 真正的 CAS 登录页必含登录表单（casLoginForm / pwdDefaultEncryptSalt / CASTGC）。
     */
    private val CAS_LOGIN_SIGNALS = listOf(
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

    /**
     * 从 HTML 中提取 JS 重定向地址，兼容 location.href / window.location 等常见写法。
     */
    fun extractJsRedirect(html: String): String? {
        val match = JS_REDIRECT_REGEX.find(html) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    // ═══════════════════════════════════════════
    //  CAS 用户信息提取
    // ═══════════════════════════════════════════

    /** 预编译正则，避免每次调用临时构造 */
    private val JS_REDIRECT_REGEX = Regex(
        """(?:window\.location(?:\.href)?|location\.href)\s*=\s*["']([^"']+)["']|""" +
            """(?:window\.)?location\.(?:replace|assign)\s*\(\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val USERNAME_REGEX = Regex("""data-name="id">([^<]+)</div>""")
    private val REAL_NAME_REGEX = Regex("""data-name="name">([^<]+)</div>""")

    /**
     * 从 CAS index.do 页面 HTML 中提取用户ID（数字学号）。
     *
     * 有效 Cookie → 返回 200 + 个人设置页 HTML（含 data-name="id"，即 CAS 用户ID/数字学号）
     * 无效 Cookie → 被重定向回 CAS 登录页 HTML
     *
     * 注意：该字段是数字学号，与登录时使用的用户名（可能是登录别名）无关。
     */
    fun extractUsername(html: String): String? {
        return USERNAME_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * 从 CAS index.do 页面 HTML 中提取实名。
     */
    fun extractRealName(html: String): String? {
        return REAL_NAME_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()
    }
}
