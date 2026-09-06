package edu.cqwu.electricity.logging

/**
 * 日志脱敏工具：避免把 Cookie、Token、ticket、密码等敏感值写入 Logcat。
 */
object LogRedactor {

    /**
     * 敏感键清单。
     * `\\bname` 带词边界：仅匹配独立的 `name=` / `name:` 字段（前导为空白/逗号/引号等
     * 非单词字符），不会误伤 className/schoolName/userName 等以 name 结尾的复合键
     * （它们前导是字母，`\b` 不成立；其中确实敏感的已单独列于此清单）。
     */
    private const val SENSITIVE_KEYS =
        "CASTGC|token|ticket|password|pwdEncrypt2|value|realName|trueName|username|userName|userId|studentId|studentNo|学号|实名|\\bname"

    private val SENSITIVE_VALUE = Regex(
        """(?i)($SENSITIVE_KEYS)\s*[=:]\s*("[^"]*"|[^&,\s"';}]+)"""
    )

    private val JSON_SENSITIVE_VALUE = Regex(
        """(?i)(")($SENSITIVE_KEYS)(")\s*:\s*("[^"]*"|[^&,\s"';}]+)"""
    )

    private const val MAX_LOG_CHARS = 200

    fun url(value: String?): String = redact(value)

    fun body(value: String?): String = redact(value)

    fun sanitize(value: String?): String {
        return value?.let {
            val redacted = SENSITIVE_VALUE.replace(it, "$1=****")
            JSON_SENSITIVE_VALUE.replace(redacted) { match ->
                val key = match.groupValues[2]
                "\"$key\":\"****\""
            }
        } ?: "null"
    }

    private fun redact(value: String?): String {
        return sanitize(value).take(MAX_LOG_CHARS)
    }
}