package edu.cqwu.electricity.jwxt.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import edu.cqwu.electricity.R
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWeekLesson
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWidgetPayload
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWidgetRepositoryV2
import edu.cqwu.electricity.jwxt.timetable.domain.TimetableToday
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 小组件刷新入口（统一出口）。
 *
 * 只做「读缓存 → 判定状态 → 渲染」：桌面上的内容来自 [TimetableWidgetRepositoryV2] 落盘的缓存，
 * 不联网，因此无论从 App 内还是系统广播进来都能立刻出画面。
 *
 * 「今天要上哪些课」也在这里算——缓存里存的是**整周**的课，跨天之后要按本地日期重新筛，
 * 这正是"缓存整周"换来的好处：跨天不必联网。
 */
object TimetableWidgetUpdaterV2 {

    private const val TAG = "TimetableWidgetUpdaterV2"

    /** 读缓存涉及文件与账号存储 IO，统一丢到 IO 线程，调用方（含广播回调）无需关心线程 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 用缓存渲染一次。
     *
     * [context] 由调用方传入（Application 启动时与小组件广播回调里都有），不做隐式全局 Context，
     * 因此也没有「尚未初始化」这条分支。
     */
    fun renderFromCache(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, TimetableWidgetProviderV2::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) {
                    // 系统没返回组件实例（例如覆盖安装后 widget 记录还没恢复）：这里只能等下一次广播
                    AppLog.w(TAG, "系统未返回小组件实例，跳过刷新")
                    return@launch
                }

                val now = Date()
                val today = TimetableToday.dayOfWeek(now)
                val payload = TimetableWidgetRepositoryV2.cached()
                val state = resolveState(payload, today, now)
                val lessons = payload
                    ?.takeIf { state == WidgetStateV2.NORMAL }
                    ?.let { TimetableWidgetRepositoryV2.lessonsOf(it, today) }
                    .orEmpty()

                AppLog.d(
                    TAG,
                    "刷新小组件：ids=${ids.toList()}, state=$state, 今天=$today, " +
                        "缓存周=${payload?.weekStartDate}, 今日课程=${lessons.size}",
                )
                manager.updateAppWidget(
                    component,
                    TimetableWidgetRendererV2.render(appContext, state, payload, lessons),
                )
                // 集合列表（ListView）的数据要单独通知刷新，否则只会更新外壳、列表内容不变。
                // 该 API 自 Android 12 起被标记废弃（新 API RemoteCollectionItems 需要 API 31+），
                // 为兼容 minSdk 21 保留传统集合 API。
                @Suppress("DEPRECATION")
                ids.forEach { manager.notifyAppWidgetViewDataChanged(it, R.id.list_courses) }
                AppLog.d(TAG, "小组件已提交更新")
            } catch (e: Exception) {
                AppLog.e(TAG, "小组件刷新失败", e)
            }
        }
    }

    /** 判定当前该显示哪种状态；顺序即优先级 */
    private fun resolveState(
        payload: TimetableWidgetPayload?,
        todayDayOfWeek: Int,
        now: Date,
    ): WidgetStateV2 {
        val account = SessionCoordinatorV2.currentAccount()
        if (account == null || !account.hasLoginState) return WidgetStateV2.NO_ACCOUNT
        if (payload == null) return WidgetStateV2.NO_CACHE
        // 换了账号，或已经跨周（缓存属于上一周）：缓存内容已经不属于「现在这个人这周」
        val current = TimetableWidgetRepositoryV2.cacheMatches(
            payload = payload,
            accountId = account.id,
            weekStartDate = TimetableToday.weekStartDateString(now),
        )
        if (!current) return WidgetStateV2.STALE

        return if (TimetableWidgetRepositoryV2.lessonsOf(payload, todayDayOfWeek).isEmpty()) {
            WidgetStateV2.NO_LESSON
        } else {
            WidgetStateV2.NORMAL
        }
    }
}

/** 小组件要呈现的状态；由 [TimetableWidgetUpdaterV2] 判定后交给渲染层 */
enum class WidgetStateV2 {
    /** 没有可用账号（未登录） */
    NO_ACCOUNT,

    /** 有账号但还没有缓存（用户没在 App 里加载过课表） */
    NO_CACHE,

    /** 换了账号，或缓存属于上一周（跨周了） */
    STALE,

    /** 今天确实没课 */
    NO_LESSON,

    /** 正常展示今日课程 */
    NORMAL,
}
