package edu.cqwu.electricity.feedback.util

/**
 * 日志脱敏工具：避免把 Cookie、Token、ticket、密码等敏感值写入 Logcat。
 */
object LogRedactor {

    private val SENSITIVE_VALUE = Regex(
        """(?i)(CASTGC|token|ticket|password|pwdEncrypt2|value|realName|trueName|username|userName)\s*[=:]\s*("[^"]*"|[^&,\s"';}]+)"""
    )

    private const val MAX_LOG_CHARS = 200

    fun mask(value: String?): String {
        return when {
            value == null -> "null"
            value.isBlank() -> ""
            else -> "****"
        }
    }

    fun url(value: String?): String = redact(value)

    fun body(value: String?): String = redact(value)

    private fun redact(value: String?): String {
        return value?.let {
            SENSITIVE_VALUE.replace(it, "$1=****").take(MAX_LOG_CHARS)
        } ?: "null"
    }
}
