package edu.cqwu.electricity.logging

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.io.Reader

/**
 * 本地日志文件读取器：反馈页的预览与分享都通过它读取 [FileLogWriter] 写下的
 * `app-yyyyMMdd.log`，所以进程重启后仍能拿到上一次运行的日志。
 *
 * - **尾部读取**：字节级反向扫描定位行首再解码，不会把 UTF-8 字符截成乱码。
 * - **预算封顶**：[readTail] / [readAllText] 都有字符上限，避免把几天日志全读进内存。
 * - **丢弃半行**：正在写入的文件末尾可能只有半行，一律丢弃。
 */
object LogReader {

    private const val DIR_NAME = "logs"
    private const val SCAN_CHUNK = 8192
    private const val NEW_LINE = '\n'.code

    /** 单次尾部读取的字符上限（中文占 3 字节，这个值对内存是保守的） */
    private const val DEFAULT_MAX_CHARS = 8L * 1024 * 1024

    /** 全量读取的字符上限，防止分享时把内存打爆 */
    private const val MAX_ALL_CHARS = 64L * 1024 * 1024

    private const val DEFAULT_TAIL_LINES = 500

    /** 日志目录：`filesDir/logs` */
    fun dirOf(context: Context): File = File(context.applicationContext.filesDir, DIR_NAME)

    /** 读取全部保留期内的日志（供「分享完整日志」使用）。 */
    fun readAllText(dir: File, maxChars: Long = MAX_ALL_CHARS): String {
        val sb = StringBuilder()
        for (file in logFiles(dir)) {
            if (sb.length >= maxChars) break
            try {
                file.bufferedReader(Charsets.UTF_8).use { readInto(sb, it, maxChars) }
            } catch (_: Exception) {
                // 单个文件读失败（损坏、权限）不影响其余文件
            }
        }
        return dropIncompleteLastLine(sb.toString())
    }

    /** 读取最近 [maxLines] 行日志。 */
    fun readTail(
        dir: File,
        maxLines: Int = DEFAULT_TAIL_LINES,
        maxChars: Long = DEFAULT_MAX_CHARS,
    ): String {
        if (maxLines <= 0) return ""
        var remainingLines = maxLines
        var remainingChars = maxChars
        val segments = ArrayList<String>() // 新 → 旧

        for (file in logFiles(dir).asReversed()) {
            if (remainingLines <= 0 || remainingChars <= 0) break
            val text = tailSegment(file, remainingLines, remainingChars)
            if (text.isEmpty()) continue
            // trimEnd：text 以换行结尾，而 lines() 会多切出一个空串
            val lines = text.trimEnd('\n').lines().takeLast(remainingLines)
            remainingLines -= lines.size
            remainingChars -= text.length
            segments.add(lines.joinToString("\n"))
        }

        // 按时间顺序拼回：旧文件在前，新文件在后
        val joined = segments.asReversed().joinToString("\n")
        return if (joined.isEmpty() || joined.endsWith("\n")) joined else "$joined\n"
    }

    private fun logFiles(dir: File): List<File> =
        LogFilePolicy.sortedAscending(dir.listFiles()?.toList().orEmpty())

    /** 读取单个文件的尾部；起点是行首，末尾半行丢弃。 */
    private fun tailSegment(file: File, maxLines: Int, maxChars: Long): String {
        val start = findTailStart(file, maxLines, maxChars)
        val text = StringBuilder()
        try {
            FileInputStream(file).use { fis ->
                fis.channel.position(start)
                InputStreamReader(fis, Charsets.UTF_8).use { readInto(text, it, maxChars) }
            }
        } catch (_: Exception) {
            return ""
        }
        return dropIncompleteLastLine(text.toString())
    }

    /** 读满 [maxChars] 或读到结尾为止（字符数近似字节预算，偏保守）。 */
    private fun readInto(sb: StringBuilder, reader: Reader, maxChars: Long) {
        val buffer = CharArray(SCAN_CHUNK)
        while (true) {
            val allowed = minOf(buffer.size.toLong(), maxChars - sb.length).toInt()
            if (allowed <= 0) break
            val n = reader.read(buffer, 0, allowed)
            if (n <= 0) break
            sb.append(buffer, 0, n)
        }
    }

    /**
     * 从文件末尾反向扫描，找到「倒数第 maxLines 行」的起始字节偏移。
     *
     * 只按 `\n`（0x0A）切分是安全的：UTF-8 的续字节都在 0x80-0xBF，不会出现 0x0A。
     */
    private fun findTailStart(file: File, neededLines: Int, maxChars: Long): Long {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                var pos = raf.length()
                var found = 0
                var scanned = 0L
                val chunk = ByteArray(SCAN_CHUNK)
                while (pos > 0 && found <= neededLines && scanned < maxChars) {
                    val size = minOf(chunk.size.toLong(), pos).toInt()
                    pos -= size
                    raf.seek(pos)
                    raf.readFully(chunk, 0, size)
                    scanned += size
                    for (i in size - 1 downTo 0) {
                        if (chunk[i].toInt() == NEW_LINE) {
                            found++
                            if (found > neededLines) return pos + i + 1
                        }
                    }
                }
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /** 丢掉末尾没有换行符的半行（正在写入的文件必然出现）。 */
    private fun dropIncompleteLastLine(text: String): String {
        if (text.isEmpty() || text.endsWith('\n')) return text
        val idx = text.lastIndexOf('\n')
        return if (idx < 0) "" else text.substring(0, idx + 1)
    }
}
