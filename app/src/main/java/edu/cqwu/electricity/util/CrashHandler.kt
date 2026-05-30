package edu.cqwu.electricity.util

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 崩溃捕获处理器。
 *
 * 通过 [Thread.setDefaultUncaughtExceptionHandler] 注册，
 * 在应用发生未捕获异常时，**同步**将崩溃堆栈写入文件后再链式调用系统默认处理器。
 *
 * ## 设计要点
 *
 * 1. **同步写入**：使用 [File.writeText] 直接落盘，禁止异步/协程。
 *    因为系统默认的 [KillApplicationHandler] 调用后进程会被杀死，
 *    异步写入可能导致文件没写完。
 *
 * 2. **锁粒度优化**：字符串拼接在锁外完成，锁仅保护文件 I/O 的最小临界区。
 *
 * 3. **线程安全**：通过 [synchronized] 锁保护文件写入，
 *    防止多线程同时崩溃（如主线程和后台线程同时崩溃）导致数据竞争。
 *
 * 4. **文件清理**：启动时自动删除 7 天前的旧崩溃文件，避免无限累积。
 *
 * 5. **写入内容**：时间戳、设备信息、App 版本号、进程名/线程名、完整堆栈。
 */
class CrashHandler private constructor(
    private val appContext: Context,
) : Thread.UncaughtExceptionHandler {

    private val originalHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    /** 崩溃文件存储目录 */
    private val dir = File(appContext.filesDir, CRASH_DIR)

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_DIR = "crash_logs"
        private const val RETENTION_DAYS = 7L
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss_SSS"
        private const val LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        /** 读取单个崩溃文件的最大字节数（200KB），防止 OOM */
        private const val MAX_BYTES_PER_FILE = 200_000

        @Volatile
        private var instance: CrashHandler? = null

        @Volatile
        private var initialized = false

        /**
         * 初始化崩溃捕获。应在 [Application.onCreate] 的**最前面**调用，
         * 确保任何第三方 SDK 初始化前就已经注册完毕。
         */
        fun init(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                initialized = true

                val handler = CrashHandler(context.applicationContext)
                instance = handler
                Thread.setDefaultUncaughtExceptionHandler(handler)

                // 启动时清理过期文件
                handler.cleanupOldCrashFiles()
            }
        }

        /**
         * 获取 [CrashHandler] 实例，供 [LogCapture] 读取崩溃文件使用。
         * 未初始化时返回 null。
         */
        fun getInstance(): CrashHandler? = instance

        /**
         * 是否有崩溃记录文件。
         */
        fun hasCrashReports(): Boolean = instance?.hasCrashReports() == true

        /**
         * 获取所有持久化的崩溃报告文本。
         */
        fun getCrashReports(maxFiles: Int = 10): String =
            instance?.getCrashReports(maxFiles) ?: ""

        /**
         * 获取崩溃记录数量。
         */
        fun crashReportCount(): Int = instance?.crashReportCount() ?: 0
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        saveCrashSync(thread, throwable)

        // 链式调用系统默认处理器（会杀死进程、显示崩溃对话框）
        // 如果 originalHandler 为 null，兜底手动杀进程避免变成僵尸 App
        if (originalHandler != null) {
            originalHandler.uncaughtException(thread, throwable)
        } else {
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }

    /**
     * 同步写入崩溃日志文件。
     *
     * 字符串拼接在锁外完成，锁仅保护 [file.writeText] 文件 I/O 最小临界区。
     */
    private fun saveCrashSync(thread: Thread, throwable: Throwable) {
        val file = createCrashFile() ?: return
        val content = buildCrashContent(thread, throwable)

        synchronized(this) {
            try {
                file.writeText(content)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "写入崩溃文件失败", e)
            }
        }
    }

    /**
     * 构建崩溃报告文本内容（无锁操作）。
     */
    private fun buildCrashContent(thread: Thread, throwable: Throwable): String {
        return buildString {
            appendLine("==========================================")
            appendLine("         应用崩溃报告")
            appendLine("==========================================")
            appendLine()

            // ── 时间信息 ──
            appendLine("--- 基本信息 ---")
            appendLine("崩溃时间: ${formatNow(LOG_DATE_FORMAT)}")
            appendLine()

            // ── 应用信息 ──
            appendLine("--- 应用信息 ---")
            appendLine("包名: ${appContext.packageName}")
            try {
                val pkgInfo = appContext.packageManager.getPackageInfo(
                    appContext.packageName, 0
                )
                appendLine("版本名: ${pkgInfo.versionName ?: "未知"}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appendLine("版本号: ${pkgInfo.longVersionCode}")
                } else {
                    @Suppress("DEPRECATION")
                    appendLine("版本号: ${pkgInfo.versionCode}")
                }
            } catch (_: Exception) {
                appendLine("版本信息: 获取失败")
            }
            appendLine()

            // ── 设备信息 ──
            appendLine("--- 设备信息 ---")
            appendLine("设备型号: ${Build.MODEL}")
            appendLine("制造商: ${Build.MANUFACTURER}")
            appendLine("品牌: ${Build.BRAND}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS?.joinToString(", ") ?: "未知"}")
            appendLine()

            // ── 进程 / 线程信息 ──
            appendLine("--- 进程/线程信息 ---")
            appendLine("进程 PID: ${Process.myPid()}")
            appendLine("线程名: ${thread.name}")
            @Suppress("DEPRECATION")
            appendLine("线程 ID: ${thread.id}")
            appendLine("是否主线程: ${thread === appContext.mainLooper.thread}")
            appendLine("线程优先级: ${thread.priority}")
            appendLine("线程组: ${thread.threadGroup?.name ?: "未知"}")
            appendLine()

            // ── 崩溃堆栈 ──
            appendLine("--- 崩溃堆栈 ---")
            appendLine(stackTraceToString(throwable))

            // 同时打印 cause 链，确保不遗漏
            var cause = throwable.cause
            var level = 1
            while (cause != null && level <= 10) {
                appendLine()
                appendLine("Caused by (level $level):")
                appendLine(stackTraceToString(cause))
                cause = cause.cause
                level++
            }

            appendLine()
            appendLine("==========================================")
            appendLine("报告结束")
            appendLine("==========================================")
        }
    }

    /**
     * 将 [Throwable] 的堆栈转为字符串。
     */
    private fun stackTraceToString(throwable: Throwable): String {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    /**
     * 创建崩溃日志文件，返回 [File] 对象。
     * 文件路径：`filesDir/crash_logs/crash_yyyyMMdd_HHmmss_SSS.txt`
     *
     * 文件名包含毫秒级时间戳，防止同一秒内多次崩溃（如 OOM 连锁反应）互相覆盖。
     */
    private fun createCrashFile(): File? {
        return try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val fileName = "crash_${formatNow(DATE_FORMAT)}.txt"
            File(dir, fileName)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "创建崩溃文件失败", e)
            null
        }
    }

    /**
     * 清理 7 天前的旧崩溃文件。
     */
    private fun cleanupOldCrashFiles() {
        try {
            if (!dir.exists()) return

            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            val files = dir.listFiles() ?: return

            var deletedCount = 0
            for (file in files) {
                if (file.isFile && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            if (deletedCount > 0) {
                android.util.Log.d(TAG, "已清理 $deletedCount 个过期崩溃文件")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "清理过期崩溃文件失败", e)
        }
    }

    /**
     * 获取所有持久化的崩溃报告文本。
     * 按文件修改时间倒序排列（最新的在前）。
     *
     * @param maxFiles 最多返回的文件数
     */
    fun getCrashReports(maxFiles: Int = 10): String {
        return try {
            if (!dir.exists() || !dir.isDirectory) return ""

            val files = dir.listFiles { file -> file.isFile && file.name.startsWith("crash_") }
                ?: return ""

            if (files.isEmpty()) return ""

            val sortedFiles = files
                .sortedByDescending { it.lastModified() }
                .take(maxFiles)

            buildString {
                sortedFiles.forEachIndexed { index, file ->
                    val fileTime = SimpleDateFormat(LOG_DATE_FORMAT, Locale.getDefault())
                        .run { format(Date(file.lastModified())) }
                    appendLine("╔══════════════════════════════════════════════")
                    appendLine("║ 崩溃记录 #${index + 1}  ($fileTime)")
                    appendLine("╚══════════════════════════════════════════════")
                    appendLine()
                    try {
                        // 限制单文件读取大小，防止 OOM
                        val bytes = file.readBytes().take(MAX_BYTES_PER_FILE).toByteArray()
                        val content = bytes.decodeToString()
                        append(content)
                    } catch (e: Exception) {
                        appendLine("[读取崩溃文件失败: ${e.message}]")
                    }
                    appendLine()
                    appendLine()
                }
            }
        } catch (e: Exception) {
            "[读取崩溃记录失败: ${e.message}]"
        }
    }

    /**
     * 是否有崩溃记录文件。
     */
    fun hasCrashReports(): Boolean {
        return try {
            if (!dir.exists() || !dir.isDirectory) return false
            dir.listFiles { file -> file.isFile && file.name.startsWith("crash_") }
                ?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取崩溃记录数量。
     */
    fun crashReportCount(): Int {
        return try {
            if (!dir.exists() || !dir.isDirectory) return 0
            dir.listFiles { file -> file.isFile && file.name.startsWith("crash_") }
                ?.size ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun formatNow(pattern: String): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
}
