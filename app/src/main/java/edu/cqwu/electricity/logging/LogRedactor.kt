package edu.cqwu.electricity.logging

/**
 * 日志脱敏工具：避免把 Cookie、Token、ticket、密码等敏感值写入 Logcat。
 */
object LogRedactor {

    private const val SENSITIVE_KEYS =
        "CASTGC|token|ticket|password|pwdEncrypt2|value|realName|trueName|username|userName|userId|studentId|studentNo|学号|实名"

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