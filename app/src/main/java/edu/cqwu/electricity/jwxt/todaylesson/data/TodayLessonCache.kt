package edu.cqwu.electricity.jwxt.todaylesson.data

import android.content.Context
import com.google.gson.Gson
import edu.cqwu.electricity.logging.AppLog
import java.io.File

/**
 * 今日课表缓存载荷。
 *
 * [savedDate] 是**保存时的设备本地日期**（`yyyy-MM-dd`）：小组件渲染前拿它与当天比对，
 * 跨天后立即退化成「待更新」态，绝不显示昨天的课。
 * [accountId] 是保存时激活的账号条目 id：切换账号后同样视为过期（避免把别人的课表显示在自己桌面上）。
 */
data class TodayLessonPayload(
    val version: Int = TodayLessonCache.VERSION,
    val savedDate: String = "",
    val accountId: String = "",
    val lessons: List<JwxtLessonUi> = emptyList(),
)

/**
 * 今日课表缓存（单个 JSON 文件）。
 *
 * 构造参数是 [File]（而不是 Context），这样 JVM 单测可以注入临时文件，
 * 与本项目 `KeyValueStoreV2` 「窄接口 + 可注入」的思路一致。
 *
 * 读失败（文件不存在 / JSON 损坏 / 版本不符）一律按「无缓存」处理并记一行日志，不抛异常：
 * 小组件刷新跑在广播回调里，任何异常都会变成用户可见的崩溃。
 */
class TodayLessonCache(private val file: File) {

    /** 读缓存；无缓存或不可用时返回 null */
    fun read(): TodayLessonPayload? {
        if (!file.exists()) return null
        return try {
            val payload = gson.fromJson(file.readText(), TodayLessonPayload::class.java)
            when {
                payload == null -> null
                payload.version != VERSION -> {
                    AppLog.w(TAG, "缓存版本不符（${payload.version} != $VERSION），按无缓存处理")
                    null
                }
                else -> payload
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "缓存解析失败，按无缓存处理", e)
            null
        }
    }

    /**
     * 写缓存：先写临时文件再改名，避免写到一半被系统杀掉留下半个 JSON。
     * 失败只记日志（下一次刷新会重写），不影响其它流程。
     */
    fun write(payload: TodayLessonPayload) {
        try {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(gson.toJson(payload))
            if (temp.renameTo(file)) return
            // 少数文件系统不支持「改名覆盖已存在文件」，退化为先删再改名
            file.delete()
            if (!temp.renameTo(file)) {
                AppLog.w(TAG, "缓存改名失败，本次不更新：${file.absolutePath}")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "缓存写入失败", e)
        }
    }

    companion object {
        /**
         * 缓存格式版本：字段有增删时必须 +1，版本不符的旧缓存按「无缓存」处理（下次进 App 重写）。
         * v2：`JwxtLessonUi` 增加课程名 / 教师 / 教室，供桌面小组件按三行展示。
         * v3：`JwxtLessonLine` 由「整行文本 + 是否高亮」改为「片段列表」，用于行内主题色高亮。
         */
        const val VERSION = 3

        private const val TAG = "TodayLessonCache"
        private const val FILE_NAME = "today_lessons.json"

        private val gson = Gson()

        /** 生产环境的缓存位置：不参与云备份，也不受「清除缓存」影响 */
        fun from(context: Context): TodayLessonCache =
            TodayLessonCache(File(context.noBackupFilesDir, FILE_NAME))
    }
}
