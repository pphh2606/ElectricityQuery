package edu.cqwu.electricity.logging

import android.content.Context
import android.os.Process
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 本地日志文件写入器：把 [AppLog] 的每条日志追加到 `filesDir/logs/app-yyyyMMdd.log`。
 *
 * ## 设计要点
 *
 * 1. **单写入者**：文件 I/O 只发生在 [worker] 线程，[append] 只做一次非阻塞入队，
 *    主线程（Compose 层、WebView 回调）调用 [AppLog] 不会被磁盘卡住。
 * 2. **分级落盘**：`WARN`/`ERROR` 立即 flush，其余等级每 [FLUSH_INTERVAL_MS] 或攒够
 *    [FLUSH_EVERY_LINES] 行再 flush。
 * 3. **按天分文件**：跨天自动切换；启动时清掉 [RETENTION_DAYS] 天之前的文件。
 * 4. **降级不递归**：写入异常只标记 [degraded] 并停写文件，**绝不在这里调用 [AppLog]**，
 *    否则会形成「写失败 → 记日志 → 再写失败」的死循环。
 */
object FileLogWriter {

    private const val DIR_NAME = "logs"
    private const val THREAD_NAME = "app-log-writer"
    private const val RETENTION_DAYS = 7L
    private const val QUEUE_CAPACITY = 2000

    /** 攒够这么多行落盘 */
    private const val FLUSH_EVERY_LINES = 32

    /** 空闲多久落盘一次；同时也是 worker 的 poll 超时 */
    private const val FLUSH_INTERVAL_MS = 1000L

    /** 崩溃路径等待落盘的硬上限：宁可丢几行，也不能拖长崩溃处理触发系统看门狗 */
    private const val FLUSH_TIMEOUT_MS = 300L

    private val lock = Any()

    @Volatile
    private var degraded = false

    @Volatile
    private var running = false

    @Volatile
    private var droppedLines = 0

    private var dir: File? = null
    private var queue: ArrayBlockingQueue<LogEntry>? = null
    private var worker: Thread? = null

    // 以下字段仅由 worker 线程读写
    private var writer: BufferedWriter? = null
    private var currentDay: String? = null

    /** 生产环境入口：目录固定为 `filesDir/logs`。 */
    fun init(context: Context) = init(File(context.applicationContext.filesDir, DIR_NAME))

    /** 目录可注入，便于单元测试。重复调用只有第一次生效。 */
    fun init(logDir: File) {
        synchronized(lock) {
            if (queue != null) return
            if (!logDir.exists() && !logDir.mkdirs()) {
                degraded = true
                return
            }
            dir = logDir
            queue = ArrayBlockingQueue(QUEUE_CAPACITY)
            // 启动清理：顺带把上次运行留下的过期文件删掉
            LogFilePolicy.expiredFiles(
                logDir.listFiles()?.toList().orEmpty(),
                System.currentTimeMillis(),
                RETENTION_DAYS,
            ).forEach { it.delete() }

            running = true
            worker = Thread({ runWorker() }, THREAD_NAME).apply {
                isDaemon = true
                start()
            }
            // 进程分隔标记：跨天共用一个文件时也能看出「本次运行」从哪里开始
            offer("===== 进程启动 pid=${Process.myPid()} =====", urgent = false)
        }
    }

    /** 追加一行日志（非阻塞）。 */
    fun append(line: String, level: LogLevel) {
        offer(line, urgent = level.ordinal >= LogLevel.WARN.ordinal)
    }

    /** 等待队列中的日志落盘；只给崩溃处理用，超时即放弃。 */
    fun flushBlocking(timeoutMs: Long = FLUSH_TIMEOUT_MS) = sendControl(reset = false, timeoutMs = timeoutMs)

    /**
     * 关闭当前输出流，让下次写入重建文件。
     *
     * 存储清理页删掉日志目录后必须调用：Linux 下未关闭的 fd 会继续写已被删除的 inode，
     * 表现为「目录里看不到文件、空间却没释放」。调用方应在 IO 线程执行。
     */
    fun reset() = sendControl(reset = true, timeoutMs = FLUSH_TIMEOUT_MS)

    /** 投递一条控制指令并等待 worker 处理完。 */
    private fun sendControl(reset: Boolean, timeoutMs: Long) {
        if (degraded) return
        val q = queue ?: return
        val latch = CountDownLatch(1)
        val entry = LogEntry(line = null, urgent = true, flushLatch = latch, reset = reset)
        if (!q.offer(entry)) {
            // 队列满：丢掉最旧的一条再试，仍失败就放弃
            q.poll()
            if (!q.offer(entry)) return
        }
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun offer(line: String, urgent: Boolean) {
        if (degraded) return
        val q = queue ?: return
        val entry = LogEntry(line = line, urgent = urgent)
        if (q.offer(entry)) return
        // 队列已满：丢最旧的，保证新日志优先写入
        q.poll()
        if (!q.offer(entry)) droppedLines++
    }

    private fun runWorker() {
        val q = queue ?: return
        var pendingLines = 0
        var lastFlushAt = System.nanoTime()

        while (running) {
            // poll 超时返回 null 即「空闲」，此时落盘——这就是定时 flush
            val entry = try {
                q.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                null
            }

            if (entry == null) {
                flushWriter()
                pendingLines = 0
                lastFlushAt = System.nanoTime()
                continue
            }

            val urgent = handle(entry)
            pendingLines++
            val elapsedMs = (System.nanoTime() - lastFlushAt) / 1_000_000
            if (urgent || pendingLines >= FLUSH_EVERY_LINES || elapsedMs >= FLUSH_INTERVAL_MS) {
                flushWriter()
                pendingLines = 0
                lastFlushAt = System.nanoTime()
            }
        }

        // 退出前把队列里剩下的写完（仅供测试停机使用）
        while (true) handle(q.poll() ?: break)
        flushWriter()
        closeWriter()
    }

    /** @return 是否要求立即落盘 */
    private fun handle(entry: LogEntry): Boolean {
        if (entry.reset) {
            closeWriter()
            dir?.mkdirs()
            entry.flushLatch?.countDown()
            return false
        }
        entry.line?.let { writeLine(it) }
        val latch = entry.flushLatch ?: return entry.urgent
        flushWriter()
        latch.countDown()
        return true
    }

    private fun writeLine(line: String) {
        if (degraded) return
        val day = LogFilePolicy.dayStamp(System.currentTimeMillis())
        if (writer == null || day != currentDay) {
            if (!openWriter(day)) return
        }
        try {
            writer?.write(line)
            writer?.write("\n")
        } catch (_: Exception) {
            degrade()
        }
    }

    private fun openWriter(day: String): Boolean {
        closeWriter()
        val d = dir ?: return false
        return try {
            val file = File(d.apply { mkdirs() }, LogFilePolicy.fileName(day))
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
            currentDay = day
            true
        } catch (_: Exception) {
            degrade()
            false
        }
    }

    private fun flushWriter() {
        val w = writer ?: return
        try {
            val dropped = droppedLines
            if (dropped > 0) {
                droppedLines = 0
                w.write("[dropped $dropped lines]\n")
            }
            w.flush()
        } catch (_: Exception) {
            degrade()
        }
    }

    private fun closeWriter() {
        try {
            writer?.close() // close 自带 flush
        } catch (_: Exception) {
        }
        writer = null
        currentDay = null
    }

    /** 进入降级状态：此后只走 logcat。这里不能调用 [AppLog]，否则递归。 */
    private fun degrade() {
        degraded = true
        closeWriter()
    }

    /** 仅供单元测试：停机并清空单例状态，让下一个测试可以重新 [init]。 */
    internal fun stopForTest() {
        running = false
        worker?.interrupt()
        try {
            worker?.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        synchronized(lock) {
            closeWriter()
            queue = null
            dir = null
            worker = null
            degraded = false
            droppedLines = 0
        }
    }

    private class LogEntry(
        val line: String?,
        val urgent: Boolean,
        val flushLatch: CountDownLatch? = null,
        val reset: Boolean = false,
    )
}
