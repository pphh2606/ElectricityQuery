package edu.cqwu.electricity.jwxt.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import edu.cqwu.electricity.logging.AppLog

/**
 * 「今日课表」桌面小组件接收器。
 *
 * 阶段 1：系统刷新广播（最快 30 分钟一次）与 App 内数据刷新都会调到 [TodayLessonWidgetUpdater.renderFromCache]，
 * 后者只读本地缓存，因此这里同步调用即可，不需要 `goAsync()`、也没有后台任务要调度
 * （阶段 2 补联网刷新时再改成 `goAsync()` + 协程，并在此处调度 WorkManager 之类）。
 *
 * 这里保留了广播生命周期日志：小组件跑在后台，日志是唯一能判断
 * 「系统到底有没有把广播发给我们」「组件实例在系统里是否还在」的手段。
 */
class TodayLessonWidgetProvider : AppWidgetProvider() {

    /** 记录所有广播（含系统各种 widget 相关 action），用于排查「组件停在下发不来的广播上」 */
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.d(TAG, "onReceive: ${intent.action}")
        // 覆盖安装后系统不一定会补发 APPWIDGET_UPDATE（ColorOS 实测会漏，组件会一直停在
        // initialLayout 的加载占位上），所以收到「本包被替换」时自己补一次刷新
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TodayLessonWidgetUpdater.renderFromCache(context)
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        AppLog.d(TAG, "onUpdate: ids=${appWidgetIds.toList()}")
        TodayLessonWidgetUpdater.renderFromCache(context)
    }

    private companion object {
        const val TAG = "TodayLessonWidgetProvider"
    }
}
