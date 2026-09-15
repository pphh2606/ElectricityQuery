package edu.cqwu.electricity.jwxt.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import edu.cqwu.electricity.R
import edu.cqwu.electricity.jwxt.schedule.data.TodayLessonPayload
import edu.cqwu.electricity.jwxt.schedule.data.TodayLessonRepository
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件刷新入口（统一出口）。
 *
 * 阶段 1 只做「读缓存 → 渲染」：桌面上的内容来自 [TodayLessonRepository] 落盘的缓存，
 * 不联网，因此无论从 App 内还是系统广播进来都能立刻出画面。
 * 阶段 2 再在这里补「联网刷新」，届时调用方不用改。
 */
object TodayLessonWidgetUpdater {

    private const val TAG = "TodayLessonWidgetUpdater"

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
                val component = ComponentName(appContext, TodayLessonWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) {
                    // 系统没返回组件实例（例如覆盖安装后 widget 记录还没恢复）：这里只能等下一次广播
                    AppLog.w(TAG, "系统未返回小组件实例，跳过刷新")
                    return@launch
                }

                val payload = TodayLessonRepository.cached()
                val state = resolveState(payload)
                AppLog.d(TAG, "刷新小组件：ids=${ids.toList()}, state=$state, savedDate=${payload?.savedDate}")
                manager.updateAppWidget(component, TodayLessonWidgetRenderer.render(appContext, state, payload))
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
    private fun resolveState(payload: TodayLessonPayload?): WidgetState {
        val account = SessionCoordinatorV2.currentAccount()
        if (account == null || !account.hasLoginState) return WidgetState.NO_ACCOUNT
        if (payload == null) return WidgetState.NO_CACHE
        // 跨天或换了账号：缓存内容已经不属于「现在这个人今天」的课表
        if (payload.savedDate != TodayLessonRepository.todayDateString() || payload.accountId != account.id) {
            return WidgetState.STALE
        }
        return if (payload.lessons.isEmpty()) WidgetState.NO_LESSON else WidgetState.NORMAL
    }
}
