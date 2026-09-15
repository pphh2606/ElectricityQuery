package edu.cqwu.electricity.jwxt.schedule.data

import android.content.Context
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.jwxt.data.JwxtApi
import edu.cqwu.electricity.jwxt.data.JwxtConstants
import edu.cqwu.electricity.jwxt.data.JwxtTodayLesson
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 今日课表仓库：教务首页「今日课程」与桌面小组件共用的唯一取数入口。
 *
 * 职责：网络取数 → 清洗成 [JwxtLessonUi] → 落缓存。
 * 原来这段逻辑挂在 `JwxtHomeViewModel` 里（UI 层），小组件无法复用，故搬到这里；
 * 将来教务的「课表」tab 也复用同一份数据。
 *
 * 与项目其它单例（CookieStore / AccountSessionStore）一致：由 Application 注入 Context。
 */
object TodayLessonRepository {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    private val api = JwxtApi()

    /** 缓存位置：Context 由 [init] 注入（Application.onCreate 先于任何 Activity / 广播回调执行） */
    private lateinit var cache: TodayLessonCache

    /**
     * 「缓存已更新」信号（CONFLATED：只保留最新一次）。
     *
     * 桌面小组件靠它做到「App 里课表一变，桌面立刻跟着变」；订阅方是 Application，
     * 这样刷新小组件的调用不必写在 UI 层（ViewModel / Compose）里。
     */
    private val _dataUpdated = Channel<Unit>(Channel.CONFLATED)
    val dataUpdated: Flow<Unit> = _dataUpdated.receiveAsFlow()

    /** 初始化，在 Application.onCreate 中调用 */
    fun init(context: Context) {
        cache = TodayLessonCache.from(context)
    }

    /**
     * 拉取今日课程并写入缓存。
     *
     * 失败（含会话过期）原样返回 `Result.failure`，由调用方决定如何呈现——不做自动重试，
     * 也不清空既有缓存（旧缓存仍可给小组件兜底展示）。
     */
    suspend fun fetchAndCache(): Result<List<JwxtLessonUi>> {
        val result = api.fetchTodayLessons().map { toUiLessons(it) }
        result.getOrNull()?.let { lessons ->
            cache.write(
                TodayLessonPayload(
                    savedDate = todayDateString(),
                    accountId = SessionCoordinatorV2.currentAccount()?.id.orEmpty(),
                    lessons = lessons,
                )
            )
            // 通知订阅者（Application）刷新桌面小组件
            _dataUpdated.trySend(Unit)
        }
        return result
    }

    /** 读缓存（不做日期/账号过期判断，判定交给调用方） */
    fun cached(): TodayLessonPayload? = cache.read()

    /** 设备当天日期 `yyyy-MM-dd`；`java.time` 需要 API 26，本项目 minSdk 21，故用 SimpleDateFormat */
    fun todayDateString(): String = SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())

    /**
     * 今日课程映射（原 `JwxtHomeViewModel.buildLessons()`，逻辑不变）。
     *
     * 接口返回顺序是乱的（实测 5-6 → 1-2 → 3-4 → 7-8 节），按开始节次升序排。
     * 课程内容只能来自 `cellDetail`（顶层 `courseName`/`classroom` 等实测全为 null），
     * 逐行去掉 HTML 标签后展示；清洗后为空的行丢弃。
     */
    private fun toUiLessons(lessons: List<JwxtTodayLesson>): List<JwxtLessonUi> =
        lessons.sortedBy { it.startSession }.map { lesson ->
            JwxtLessonUi(
                sectionRange = "${lesson.startSession}-${lesson.endSession}",
                timeRange = "${lesson.startTime}-${lesson.endTime}",
                transferTag = lesson.classTransferTypeCode.take(2)
                    .takeIf { it == JwxtConstants.TRANSFER_TYPE_ADJUST || it == JwxtConstants.TRANSFER_TYPE_MAKEUP }
                    .orEmpty(),
                lines = lesson.cellDetail.orEmpty().mapNotNull { line ->
                    val text = HtmlFormParser.stripHtml(line.text)
                    if (text.isEmpty()) null else JwxtLessonLine(text = text, highlight = line.color != null)
                },
            )
        }
}
