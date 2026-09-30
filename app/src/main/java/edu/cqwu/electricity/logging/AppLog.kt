package edu.cqwu.electricity.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central logging entry point.
 *
 * All messages are sanitized before reaching logcat or [FileLogWriter], and
 * the configured minimum level controls output on both debug and release builds.
 *
 * 注意：`LogLevel.OFF` 时本方法在过滤阶段就返回，因此 logcat 与本地日志文件
 * 会同时静默——这是「日志等级：关闭」的既定语义，不要在过滤之前插入写文件逻辑。
 */
object AppLog {
    @Volatile
    private var minLevel: LogLevel = LogLevel.DEBUG

    /**
     * 时间戳格式化器按线程复用。
     * 每行 new 一个 [SimpleDateFormat] 在「logcat + 落盘」双写场景下分配压力明显。
     */
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    fun d(tag: String, message: String?) = log(LogLevel.DEBUG, tag, message, null)
    fun d(tag: String, message: String?, throwable: Throwable?) =
        log(LogLevel.DEBUG, tag, message, throwable)

    fun w(tag: String, message: String?) = log(LogLevel.WARN, tag, message, null)
    fun w(tag: String, message: String?, throwable: Throwable?) =
        log(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String?) = log(LogLevel.ERROR, tag, message, null)
    fun e(tag: String, message: String?, throwable: Throwable?) =
        log(LogLevel.ERROR, tag, message, throwable)

    fun url(tag: String, message: String?) = url(LogLevel.DEBUG, tag, message)
    fun url(level: LogLevel, tag: String, message: String?) =
        log(level, tag, message, null) { LogRedactor.url(it) }

    fun body(tag: String, message: String?) = body(LogLevel.DEBUG, tag, message)
    fun body(level: LogLevel, tag: String, message: String?) =
        log(level, tag, message, null) { LogRedactor.body(it) }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String?,
        throwable: Throwable?,
        preprocess: ((String?) -> String)? = null,
    ) {
        if (level.ordinal < minLevel.ordinal) return

        val prepared = preprocess?.invoke(message) ?: message
        val safeMessage = LogRedactor.sanitize(prepared ?: "null")
        val safeStack = throwable?.let {
            LogRedactor.sanitize(Log.getStackTraceString(it))
        }
        val logText = if (safeStack == null) safeMessage else "$safeMessage\n$safeStack"

        // 先落盘再进 logcat：崩溃路径 flush 的队列顺序与代码顺序一致
        FileLogWriter.append(formatLine(level, tag, logText), level)
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, logText)
            LogLevel.DEBUG -> Log.d(tag, logText)
            LogLevel.INFO -> Log.i(tag, logText)
            LogLevel.WARN -> Log.w(tag, logText)
            LogLevel.ERROR -> Log.e(tag, logText)
            LogLevel.OFF -> Unit
        }
    }

    private fun formatLine(level: LogLevel, tag: String, text: String): String {
        val time = timeFormat.get()!!.format(Date())
        val levelChar = when (level) {
            LogLevel.VERBOSE -> "V"
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
            LogLevel.OFF -> "X"
        }
        return "$time $levelChar/$tag: $text"
    }
}
