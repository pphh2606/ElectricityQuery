package edu.cqwu.electricity.logging

/**
 * Log levels supported by [AppLog].
 */
enum class LogLevel(val value: String) {
    VERBOSE("verbose"),
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    OFF("off");

    companion object {
        fun fromValue(value: String, default: LogLevel): LogLevel {
            return entries.firstOrNull { it.value == value } ?: default
        }
    }
}
