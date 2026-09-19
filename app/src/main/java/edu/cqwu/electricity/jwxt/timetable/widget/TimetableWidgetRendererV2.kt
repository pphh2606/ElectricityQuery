package edu.cqwu.electricity.jwxt.timetable.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.MainActivity
import edu.cqwu.electricity.home.data.HomeAppIds
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWeekLesson
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWidgetPayload
import edu.cqwu.electricity.shortcut.util.ShortcutHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「今日课表」小组件的 RemoteViews 渲染层。
 *
 * 原则（与 [WidgetStateV2] 的判定分开）：
 * 1. 渲染层只读不算：入参已是可直接展示的数据，不做网络、不做业务换算；
 * 2. 每次渲染先重置**内容区**两个容器的可见性——RemoteViews 是增量指令，不重置会出现上一个状态的残影；
 *    顶部信息行不参与显隐切换（布局里它恒可见），但每次都要重写文字；
 * 3. 课程列表交给集合小组件（`ListView` + [TimetableWidgetServiceV2]），
 *    这是 RemoteViews 里唯一能响应手指滑动的形式，顶部信息行留在列表外面所以固定不动；
 * 4. 文案全部走字符串资源（项目 `checkHardcodedStrings` 任务会拦截硬编码中文）。
 */
object TimetableWidgetRendererV2 {

    /** 没有缓存时「更新时间」的占位：一眼能看出"还没取过数"，而不是某个真实时刻 */
    private const val NO_UPDATE_TIME = "00:00"

    fun render(
        context: Context,
        state: WidgetStateV2,
        payload: TimetableWidgetPayload?,
        lessons: List<TimetableWeekLesson>,
    ): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_timetable_v2)
        resetWidgetStateV2(remoteViews)

        // 点整块进 App：复用桌面快捷方式那套 extra，由 MainActivity → AppLaunchEffects → HomeAppLauncher
        // 解析成 Routes.JWXT_TIMETABLE——这里给的是首页「我的课表」格子的 appId，
        // 所以桌面一点就直接落到课表页（不是教务首页），导航侧无需任何改动
        remoteViews.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))

        // 顶部信息行**任何状态下都写**：条数跟着"当前要显示的课"，时间跟着"那份缓存 JSON"——
        // 两者来源不同，无内容时分别退化成 0 与 00:00，于是「没取到数」和「取了但今天没课」一眼能分开
        remoteViews.setTextViewText(
            R.id.tv_footer,
            context.getString(
                R.string.widget_timetable_v2_footer,
                lessons.size,
                updatedTimeLabel(payload?.savedAt ?: 0L),
            ),
        )

        when (state) {
            WidgetStateV2.NORMAL -> renderLessons(context, remoteViews)
            WidgetStateV2.NO_LESSON -> showStatus(remoteViews, context.getString(R.string.jwxt_lesson_empty))
            WidgetStateV2.NO_CACHE -> showStatus(remoteViews, context.getString(R.string.widget_state_no_cache))
            WidgetStateV2.NO_ACCOUNT -> showStatus(remoteViews, context.getString(R.string.widget_state_no_account))
            // 跨周（或换了账号）：提示这是哪一周的课表，让用户知道该进 App 同步了
            WidgetStateV2.STALE -> showStatus(
                remoteViews,
                context.getString(
                    R.string.widget_state_stale,
                    payload?.weekStartDate?.takeLast(5).orEmpty(),
                ),
            )
        }
        return remoteViews
    }

    /** 缓存写入时刻 → `HH:mm`；没有缓存（或旧缓存没有这个字段）时用 [NO_UPDATE_TIME] 占位 */
    private fun updatedTimeLabel(savedAt: Long): String =
        if (savedAt <= 0L) {
            NO_UPDATE_TIME
        } else {
            // minSdk 21 用不了 java.time，与 TimetableToday 同一套做法
            SimpleDateFormat("HH:mm", Locale.US).format(Date(savedAt))
        }

    private fun resetWidgetStateV2(remoteViews: RemoteViews) {
        remoteViews.setViewVisibility(R.id.inner_card, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.container_status, View.GONE)
    }

    /**
     * 有课：列表绑定集合组件（内容由 Factory 提供，可上下滑动）。
     * 列表项的点击靠 template + Factory 里的 fillInIntent 合并，见 [TimetableWidgetServiceV2]。
     *
     * 顶部信息行不在这里写——它在所有状态下都要显示，由 [render] 统一处理。
     */
    // setRemoteAdapter(viewId, Intent) 自 Android 12 起被标记废弃：新 API（RemoteCollectionItems）
    // 不需要 Service，但要求 API 31+。为了不牺牲 minSdk 21 的老设备，这里保留传统集合 API。
    @Suppress("DEPRECATION")
    private fun renderLessons(context: Context, remoteViews: RemoteViews) {
        remoteViews.setRemoteAdapter(
            R.id.list_courses,
            Intent(context, TimetableWidgetServiceV2::class.java),
        )
        remoteViews.setPendingIntentTemplate(R.id.list_courses, openAppIntent(context))
    }

    /** 提示态（空态 / 待更新 / 未登录）：内容区只显示一句话 */
    private fun showStatus(remoteViews: RemoteViews, message: String) {
        remoteViews.setViewVisibility(R.id.inner_card, View.GONE)
        remoteViews.setViewVisibility(R.id.container_status, View.VISIBLE)
        remoteViews.setTextViewText(R.id.tv_status, message)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(ShortcutHelper.EXTRA_SHORTCUT_APP_ID, HomeAppIds.TIMETABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
