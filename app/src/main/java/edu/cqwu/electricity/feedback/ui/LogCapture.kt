package edu.cqwu.electricity.feedback.ui

import android.content.Context
import edu.cqwu.electricity.R
import edu.cqwu.electricity.feedback.util.CrashHandler
import edu.cqwu.electricity.logging.AppLogBuffer

/**
 * Collects feedback logs from the in-app buffer and persisted crash files.
 */
object LogCapture {

    /**
     * @param lineCount Maximum number of in-app buffer lines to include.
     */
    fun getRecentLogs(context: Context, lineCount: Int = 500): String {
        val parts = mutableListOf<String>()

        val crashReports = CrashHandler.getCrashReports(maxFiles = 10)
        if (crashReports.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_crash))
            parts.add("")
            parts.add(crashReports)
        }

        val currentLogs = AppLogBuffer.dump(maxLines = lineCount)
        if (currentLogs.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_process))
            parts.add("")
            parts.add(currentLogs)
        }

        return if (parts.isEmpty()) {
            context.getString(R.string.feedback_log_no_logs)
        } else {
            parts.joinToString("\n")
        }
    }
}
