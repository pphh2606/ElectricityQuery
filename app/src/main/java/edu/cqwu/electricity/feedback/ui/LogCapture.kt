package edu.cqwu.electricity.feedback.ui

import android.content.Context
import android.os.Build
import android.os.Process
import edu.cqwu.electricity.feedback.util.CrashHandler
import edu.cqwu.electricity.R
import java.util.concurrent.TimeUnit

/**
 * 通过系统 logcat 命令收集当前 App 进程的日志。
 * 零依赖设计，无需修改现有 android.util.Log 调用。
 *
 * 此方案与 ACRA（Android 崩溃报告库）使用的日志收集策略相同。
 *
 * 改进说明（v2）：
 * - 三优先级日志收集策略（持久化文件 > 当前 logcat > crash 缓冲区）
 * - 保留 [ProcessBuilder] 避免 [Runtime.exec] 潜在死锁问题
 * - 保留 3 秒超时，防止定制 ROM 的 logcat 命令卡死
 * - 保留 API 24+ `--pid` 精确过滤；旧版本全量抓取后手动匹配 PID
 * - [waitForSafe] 抽取为公共方法，消除三个方法间的重复超时轮询代码
 */
object LogCapture {

    /** logcat 命令超时时间（毫秒） */
    private const val LOG_TIMEOUT_MS = 3000L

    /**
     * 获取聚合后的日志文本。
     *
     * 三优先级收集策略：
     * 1. **持久化崩溃文件**（最可靠）
     * 2. **当前进程 logcat**（捕获非崩溃类异常/ANR 线索）
     * 3. **logcat -b crash 缓冲区**（作为补充，不依赖）
     *
     * @param lineCount 最大返回行数，默认 500
     * @return 日志文本，失败时返回错误描述
     */
    fun getRecentLogs(context: Context, lineCount: Int = 500): String {
        val parts = mutableListOf<String>()

        // ── 优先级 1：持久化崩溃文件 ──
        val crashReports = CrashHandler.getCrashReports(maxFiles = 10)
        if (crashReports.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_crash))
            parts.add("")
            parts.add(crashReports)
        }

        // ── 优先级 2：当前进程 logcat ──
        val currentLogs = getCurrentProcessLogs(lineCount)
        if (currentLogs.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_process))
            parts.add("")
            parts.add(currentLogs)
        }

        // ── 优先级 3：crash 缓冲区（补充） ──
        val crashBufferLogs = getCrashBufferLogs(lineCount)
        if (crashBufferLogs.isNotBlank()) {
            parts.add(context.getString(R.string.feedback_log_section_crash_buffer))
            parts.add("")
            parts.add(crashBufferLogs)
        }

        return if (parts.isEmpty()) {
            context.getString(R.string.feedback_log_no_logs)
        } else {
            parts.joinToString("\n")
        }
    }

    /** 执行 logcat 命令并返回输出文本 */
    private fun executeLogCommand(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitForSafe()) {
                process.destroy()
                return ""
            }
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 获取当前进程的 logcat 日志。
     * 使用 `--pid` 精确过滤当前进程，API < 24 手动匹配 PID。
     */
    private fun getCurrentProcessLogs(lineCount: Int): String {
        val pid = Process.myPid()

        // API 24+ 直接用 --pid 过滤；旧版本全量抓取后手动过滤
        val command = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            arrayOf("logcat", "-d", "--pid=$pid", "-t", lineCount.toString(), "-v", "threadtime")
        } else {
            // 旧设备多抓 3 倍量，过滤后可能只剩目标行数
            arrayOf("logcat", "-d", "-t", (lineCount * 3).toString(), "-v", "threadtime")
        }

        val output = executeLogCommand(command)

        // API < 24 手动过滤当前进程（logcat threadtime 格式中 PID 前后都是空格）
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            output.lineSequence()
                .filter { it.contains(" $pid ") }
                .take(lineCount)
                .joinToString("\n")
        } else {
            output
        }
    }

    /**
     * 尝试读取 logcat crash 缓冲区作为补充。
     *
     * 注意：部分国产 ROM（MIUI、ColorOS 等）会修改 logcat 缓冲区配置，
     * crash 缓冲区可能为空或被重定向到别处，因此此方法仅作补充。
     */
    private fun getCrashBufferLogs(lineCount: Int): String {
        return executeLogCommand(
            arrayOf("logcat", "-d", "-b", "crash", "-t", lineCount.toString(), "-v", "threadtime")
        )
    }

    /**
     * 安全等待进程退出，支持 API < 26 轮询方案。
     *
     * - API 26+：使用 [java.lang.Process.waitFor] 原生超时支持
     * - API < 26：以 100ms 间隔轮询 [java.lang.Process.exitValue]，超时返回 false
     *
     * @return true 进程已退出，false 超时
     */
    private fun java.lang.Process.waitForSafe(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                waitFor(LOG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            }
        }
        // API < 26：轮询
        val deadline = System.currentTimeMillis() + LOG_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                // 进程仍在运行，继续等待
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }
}
