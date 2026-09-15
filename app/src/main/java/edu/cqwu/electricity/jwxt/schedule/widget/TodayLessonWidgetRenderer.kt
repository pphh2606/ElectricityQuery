package edu.cqwu.electricity.jwxt.schedule.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.MainActivity
import edu.cqwu.electricity.home.data.HomeAppIds
import edu.cqwu.electricity.jwxt.data.JwxtConstants
import edu.cqwu.electricity.jwxt.schedule.data.JwxtLessonUi
import edu.cqwu.electricity.jwxt.schedule.data.TodayLessonPayload
import edu.cqwu.electricity.shortcut.util.ShortcutHelper

/** 小组件要呈现的状态；由 [TodayLessonWidgetUpdater] 判定后交给渲染层 */
enum class WidgetState {
    /** 没有可用账号（未登录） */
    NO_ACCOUNT,

    /** 有账号但还没有缓存（用户没在 App 里加载过课表） */
    NO_CACHE,

    /** 缓存不是今天的，或缓存属于另一个账号 */
    STALE,

    /** 今天确实没课 */
    NO_LESSON,

    /** 正常展示今日课程 */
    NORMAL,
}

/**
 * 今日课表小组件的 RemoteViews 渲染层。
 *
 * 原则：
 * 1. 渲染层只读不算：入参已是可直接展示的数据，不做网络、不做业务换算；
 * 2. 每次渲染先重置两个容器的可见性——RemoteViews 是增量指令，不重置会出现上一个状态的残影；
 * 3. 课程列表交给集合小组件（`ListView` + [TodayLessonWidgetService]），
 *    这是 RemoteViews 里唯一能响应手指滑动的形式，标题行留在列表外面所以固定不动；
 * 4. 文案全部走字符串资源（项目 `checkHardcodedStrings` 任务会拦截硬编码中文）。
 */
object TodayLessonWidgetRenderer {

    fun render(context: Context, state: WidgetState, payload: TodayLessonPayload?): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_today_lesson)
        resetWidgetState(remoteViews)

        // 点整块进 App：复用桌面快捷方式那套 extra，由 MainActivity → AppLaunchEffects → HomeAppLauncher
        // 解析成 Routes.JWXT_HOME（导航侧无需任何改动）
        remoteViews.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))

        when (state) {
            WidgetState.NORMAL -> renderCourses(context, remoteViews, payload?.lessons.orEmpty())
            WidgetState.NO_LESSON -> showStatus(remoteViews, context.getString(R.string.jwxt_lesson_empty))
            WidgetState.NO_CACHE -> showStatus(remoteViews, context.getString(R.string.widget_state_no_cache))
            WidgetState.NO_ACCOUNT -> showStatus(remoteViews, context.getString(R.string.widget_state_no_account))
            WidgetState.STALE -> showStatus(
                remoteViews,
                context.getString(R.string.widget_state_stale, payload?.savedDate?.takeLast(5).orEmpty()),
            )
        }
        return remoteViews
    }

    private fun resetWidgetState(remoteViews: RemoteViews) {
        remoteViews.setViewVisibility(R.id.inner_card, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.container_status, View.GONE)
    }

    /**
     * 有课：列表绑定集合组件（内容由 Factory 提供，可上下滑动），标题行显示总节数。
     * 列表项的点击靠 template + Factory 里的 fillInIntent 合并，见 [TodayLessonWidgetService]。
     */
    // setRemoteAdapter(viewId, Intent) 自 Android 12 起被标记废弃：新 API（RemoteCollectionItems）
    // 不需要 Service，但要求 API 31+。为了不牺牲 minSdk 21 的老设备，这里保留传统集合 API。
    @Suppress("DEPRECATION")
    private fun renderCourses(context: Context, remoteViews: RemoteViews, lessons: List<JwxtLessonUi>) {
        remoteViews.setRemoteAdapter(
            R.id.list_courses,
            Intent(context, TodayLessonWidgetService::class.java),
        )
        remoteViews.setPendingIntentTemplate(R.id.list_courses, openAppIntent(context))
        remoteViews.setTextViewText(
            R.id.tv_footer,
            context.getString(R.string.widget_lesson_total, lessons.size),
        )
    }

    /** 提示态（空态 / 待更新 / 未登录）：整块只显示一句话 */
    private fun showStatus(remoteViews: RemoteViews, message: String) {
        remoteViews.setViewVisibility(R.id.inner_card, View.GONE)
        remoteViews.setViewVisibility(R.id.container_status, View.VISIBLE)
        remoteViews.setTextViewText(R.id.tv_status, message)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(ShortcutHelper.EXTRA_SHORTCUT_APP_ID, HomeAppIds.UNDERGRAD_JW)
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

/** 调/补标签文案；正常课（无标签）返回 null。渲染层与列表工厂共用 */
internal fun transferTagLabel(context: Context, code: String): String? = when (code) {
    JwxtConstants.TRANSFER_TYPE_ADJUST -> context.getString(R.string.jwxt_lesson_tag_adjust)
    JwxtConstants.TRANSFER_TYPE_MAKEUP -> context.getString(R.string.jwxt_lesson_tag_makeup)
    else -> null
}
