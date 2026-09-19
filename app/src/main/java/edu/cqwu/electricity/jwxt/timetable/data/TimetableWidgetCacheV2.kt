package edu.cqwu.electricity.jwxt.timetable.data

import android.content.Context
import com.google.gson.Gson
import edu.cqwu.electricity.logging.AppLog
import java.io.File

/**
 * 「今日课表」小组件的缓存（单个 JSON 文件，独立于教务「今日课程」那份缓存）。
 *
 * 两份缓存分开存，是为了让新旧两个小组件能同时正常工作、便于并行对比；
 * 等旧的小组件套删除后，这份就是唯一的一份。
 *
 * 构造参数是 [File]（而不是 Context），这样 JVM 单测可以注入临时文件，
 * 与本项目 `KeyValueStoreV2` 「窄接口 + 可注入」的思路一致。
 *
 * 读失败（文件不存在 / JSON 损坏 / 版本不符）一律按「无缓存」处理并记一行日志，不抛异常：
 * 小组件刷新跑在广播回调里，任何异常都会变成用户可见的崩溃。
 */
class TimetableWidgetCacheV2(private val file: File) {

    /** 读缓存；无缓存或不可用时返回 null */
    fun read(): TimetableWidgetPayload? {
        if (!file.exists()) return null
        return try {
            val payload = gson.fromJson(file.readText(), TimetableWidgetPayload::class.java)
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
    fun write(payload: TimetableWidgetPayload) {
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
         * 缓存格式版本：字段有增删时必须 +1，版本不符的旧缓存按「无缓存」处理（下次刷新重写）。
         * v2：`TimetableWidgetPayload` 增加 `savedAt`（写缓存时刻，桌面顶部显示「HH:mm 更新」）。
         */
        const val VERSION = 2

        private const val TAG = "TimetableWidgetCacheV2"
        private const val FILE_NAME = "timetable_widget.json"

        private val gson = Gson()

        /** 生产环境的缓存位置：不参与云备份，也不受「清除缓存」影响 */
        fun from(context: Context): TimetableWidgetCacheV2 =
            TimetableWidgetCacheV2(File(context.noBackupFilesDir, FILE_NAME))
    }
}
