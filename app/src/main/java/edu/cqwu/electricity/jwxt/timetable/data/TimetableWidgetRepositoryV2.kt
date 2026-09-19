package edu.cqwu.electricity.jwxt.timetable.data

import android.content.Context
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse
import edu.cqwu.electricity.jwxt.timetable.domain.KB_CLASSROOM
import edu.cqwu.electricity.jwxt.timetable.domain.KB_TEACHER
import edu.cqwu.electricity.jwxt.timetable.domain.TimetableToday
import edu.cqwu.electricity.jwxt.timetable.domain.anchorTextOf
import edu.cqwu.electricity.jwxt.timetable.domain.timeRangeOf
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 「今日课表」小组件的数据源：**课表接口**（v410 `getMyScheduleDetail`），
 * 而不是教务的「今日课程」（`listStudentTodayLesson`）。
 *
 * 与 `TodayLessonRepository` 是两套互不相干的数据，各自一份缓存：
 * 那套取「服务端算好的今天」，这套取「某一周的整周课表」再在本地筛今天——
 * 因为课表接口没有"今天"这个概念（详见 [TimetableToday]）。
 *
 * 取数三步与课表页首次进入完全一致：学期列表 → 该学期的当前周 → 该周的整周课表。
 * 但**更常见的路径是 [cacheFrom]**：课表页本来就取过这些数据，直接喂过来即可，
 * 不必让小组件再打一次网络。
 *
 * 与项目其它单例（CookieStore / AccountSessionStore / TodayLessonRepository）一致：
 * 由 Application 注入 Context。
 */
object TimetableWidgetRepositoryV2 {

    private val api = TimetableApi()

    /** 缓存位置：Context 由 [init] 注入（Application.onCreate 先于任何 Activity / 广播回调执行） */
    private lateinit var cache: TimetableWidgetCacheV2

    /**
     * 「缓存已更新」信号（CONFLATED：只保留最新一次）。
     *
     * 桌面小组件靠它做到「课表一变，桌面立刻跟着变」；订阅方是 Application，
     * 这样刷新小组件的调用不必写在 UI 层（ViewModel / Compose）里。
     */
    private val _dataUpdated = Channel<Unit>(Channel.CONFLATED)
    val dataUpdated: Flow<Unit> = _dataUpdated.receiveAsFlow()

    /** 初始化，在 Application.onCreate 中调用 */
    fun init(context: Context) {
        cache = TimetableWidgetCacheV2.from(context)
    }

    /** 读缓存（不做账号/跨周判断，判定交给小组件渲染层） */
    fun cached(): TimetableWidgetPayload? = cache.read()

    /**
     * 把课表页**已经取到**的数据写进缓存（不再多打一次网络）。
     *
     * 只应在「我的课表 + 当前教学周」时调用——小组件要的是"今天"，
     * 用户在课表页翻到第 5 周或看别人的课表时写进去的是错的。
     *
     * [now] 可注入以便单测；生产环境用默认值。
     */
    suspend fun cacheFrom(
        termCode: String,
        weekSerial: Int,
        courses: List<JwxtTimetableCourse>,
        now: Date = Date(),
    ) {
        val payload = TimetableWidgetPayload(
            accountId = SessionCoordinatorV2.currentAccount()?.id.orEmpty(),
            termCode = termCode,
            weekSerial = weekSerial,
            weekStartDate = TimetableToday.weekStartDateString(now),
            savedAt = now.time,
            lessons = toWeekLessons(courses),
        )
        // 写文件切到 IO：调用方是「课表页加载完成」，跑在主线程
        withContext(Dispatchers.IO) { cache.write(payload) }
        _dataUpdated.trySend(Unit)
    }

    /**
     * 缓存是否已经不可用（没缓存 / 换了账号 / 已跨周），需要重新取一次。
     *
     * 小组件在桌面上跑、**不能联网问"现在是第几周"**，所以跨周之后它没有自救能力——
     * 这个判断就是 App 启动时补取一次的唯一依据。
     */
    suspend fun needsRefresh(now: Date = Date()): Boolean = withContext(Dispatchers.IO) {
        !cacheMatches(
            payload = cache.read(),
            accountId = SessionCoordinatorV2.currentAccount()?.id.orEmpty(),
            weekStartDate = TimetableToday.weekStartDateString(now),
        )
    }

    /**
     * 缓存与「当前账号 + 本周」是否匹配。
     *
     * 抽成纯函数是为了能单测：这几个条件是小组件「待更新」态的全部判据，
     * 写错了的表现是「桌面上显示着上周的课」——用户很可能不会察觉。
     */
    internal fun cacheMatches(
        payload: TimetableWidgetPayload?,
        accountId: String,
        weekStartDate: String,
    ): Boolean = payload != null &&
        payload.accountId == accountId &&
        payload.weekStartDate == weekStartDate

    /**
     * 完整取数后写缓存：学期 → 当前周 → 整周课表。App 启动时用它兜底
     * （用户没进过课表页、或缓存已跨周/跨天到无法用时）。
     *
     * 失败（含会话过期）原样返回 `Result.failure`，由调用方决定如何呈现——不做自动重试，
     * 也**不清空既有缓存**（旧缓存还能给小组件兜底显示）。
     */
    suspend fun fetchAndCache(now: Date = Date()): Result<Unit> {
        val terms = api.fetchScheduleTerms().getOrElse { return Result.failure(it) }
        val term = terms.firstOrNull { it.currentFlag } ?: terms.firstOrNull()
            ?: return Result.failure(IllegalStateException("学期列表为空"))

        val weeks = api.fetchTermWeeks(term.termCode).getOrElse { return Result.failure(it) }
        val week = weeks.firstOrNull { it.curWeek } ?: weeks.firstOrNull()
            ?: return Result.failure(IllegalStateException("周次列表为空"))

        val courses = api.fetchScheduleDetail(term.termCode, week.serialNumber)
            .getOrElse { return Result.failure(it) }

        cacheFrom(term.termCode, week.serialNumber, courses, now)
        return Result.success(Unit)
    }

    /**
     * 清洗：整周课程 → 缓存用的条目。
     *
     * 丢掉 `dayOfWeek` 越界的课（定位不到任何一列，留着也显示不出来），
     * 并按「先按天、再按开始节次」排序，这样渲染时筛出某一天就是正确顺序。
     *
     * 可见性为 internal：让单测能用真实抓包数据直接验证清洗结果。
     */
    internal fun toWeekLessons(courses: List<JwxtTimetableCourse>): List<TimetableWeekLesson> =
        courses
            .filter { it.dayOfWeek in 1..JwxtConstants.DAYS_IN_WEEK }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.beginSection }))
            .map { course ->
                TimetableWeekLesson(
                    dayOfWeek = course.dayOfWeek,
                    beginSection = course.beginSection,
                    endSection = course.endSection,
                    timeRange = timeRangeOf(course),
                    courseName = course.courseName,
                    teacher = anchorTextOf(course, KB_TEACHER),
                    // 正文里没有教室锚时退回接口的 placeName（实测两种情况都会出现）
                    classroom = anchorTextOf(course, KB_CLASSROOM).ifBlank { course.placeName },
                )
            }

    /**
     * 从缓存里筛出某一天的课（缓存已按天与节次排好序，这里只需过滤）。
     *
     * [dayOfWeek] 用 [TimetableToday.dayOfWeek]。这一天没课就返回空列表——
     * 空列表与"缓存不可用"是两回事，后者由渲染层在调用本函数之前判定。
     */
    internal fun lessonsOf(payload: TimetableWidgetPayload, dayOfWeek: Int): List<TimetableWeekLesson> =
        payload.lessons.filter { it.dayOfWeek == dayOfWeek }
}
