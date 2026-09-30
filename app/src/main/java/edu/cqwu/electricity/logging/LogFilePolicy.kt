package edu.cqwu.electricity.logging

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 日志文件命名与保留策略。
 *
 * 这里只放纯逻辑（不碰线程、不碰 Context），方便单元测试直接覆盖
 * 「当天文件名」「过期文件筛选」「按时间排序」这些容易写错的判断。
 */
internal object LogFilePolicy {

    /** 文件名前缀，形如 `app-20260927.log` */
    const val FILE_PREFIX = "app-"
    const val FILE_SUFFIX = ".log"

    private const val DAY_PATTERN = "yyyyMMdd"

    /** 兼容将来可能加上的滚动后缀（`app-20260927.1.log`） */
    private val FILE_NAME_REGEX = Regex("""^app-(\d{8})(?:\.\d+)?\.log$""")

    fun dayStamp(timestamp: Long): String =
        SimpleDateFormat(DAY_PATTERN, Locale.US).format(Date(timestamp))

    fun fileName(day: String): String = "$FILE_PREFIX$day$FILE_SUFFIX"

    fun isLogFile(file: File): Boolean = file.isFile && FILE_NAME_REGEX.matches(file.name)

    /** 从文件名解析出当天 00:00 的时间戳；解析不出来返回 null。 */
    fun timestampOfFileName(name: String): Long? {
        val day = FILE_NAME_REGEX.find(name)?.groupValues?.get(1) ?: return null
        return try {
            SimpleDateFormat(DAY_PATTERN, Locale.US).apply { isLenient = false }.parse(day)?.time
        } catch (_: Exception) {
            null
        }
    }

    /** 按时间升序排列（文件名里的日期即时间序，字典序等价）。 */
    fun sortedAscending(files: List<File>): List<File> =
        files.filter { isLogFile(it) }.sortedBy { it.name }

    /**
     * 超出保留期、需要删除的文件。
     *
     * 优先用文件名里的日期判断；文件名不符合规范时回退到文件修改时间，
     * 避免因为系统时钟偏差误删刚刚写下的日志。
     */
    fun expiredFiles(files: List<File>, now: Long, retentionDays: Long): List<File> {
        val cutoff = now - TimeUnit.DAYS.toMillis(retentionDays)
        return files.filter { file ->
            if (!isLogFile(file)) return@filter false
            val dayStart = timestampOfFileName(file.name) ?: file.lastModified()
            dayStart < cutoff
        }
    }
}
