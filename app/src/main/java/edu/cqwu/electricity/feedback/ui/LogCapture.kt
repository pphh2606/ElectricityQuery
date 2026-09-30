package edu.cqwu.electricity.feedback.ui

import android.content.Context
import edu.cqwu.electricity.R
import edu.cqwu.electricity.feedback.util.CrashHandler
import edu.cqwu.electricity.logging.LogReader

/**
 * Collects feedback logs from the persisted log files and crash reports.
 *
 * 日志正文来自 [LogReader] 读取的 `filesDir/logs` 下的本地文件，
 * 因此进程重启后依然能拿到上一次运行的记录。
 */
object LogCapture {

    /** 预览用：最近 [lineCount] 行 + 崩溃记录。 */
    fun getRecentLogs(context: Context, lineCount: Int = 500): String =
        compose(
            context = context,
            logs = LogReader.readTail(LogReader.dirOf(context), maxLines = lineCount),
        )

    /** 分享用：保留期内（7 天）的全部日志 + 崩溃记录。 */
    fun getAllLogs(context: Context): String =
        compose(
            context = context,
            logs = LogReader.readAllText(LogReader.dirOf(context)),
        )

    private fun compose(context: Context, logs: String): String {
        val parts = mutableListOf<String>()

        val crashReports = CrashHandler.getCrashReports(maxFiles = 10)
        if (crashReports.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_crash))
            parts.add("")
            parts.add(crashReports)
        }

        if (logs.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_process))
            parts.add("")
            parts.add(logs)
        }

        return if (parts.isEmpty()) {
            context.getString(R.string.feedback_log_no_logs)
        } else {
            parts.joinToString("\n")
        }
    }
}
