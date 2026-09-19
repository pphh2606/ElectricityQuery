package edu.cqwu.electricity.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.common.net.CookieStore
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.WebVpnSettings
import edu.cqwu.electricity.login.domain.CasAuthFlow
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.feedback.util.CrashHandler
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWidgetRepositoryV2
import edu.cqwu.electricity.jwxt.timetable.widget.TimetableWidgetUpdaterV2
import edu.cqwu.electricity.jwxt.todaylesson.data.TodayLessonRepository
import edu.cqwu.electricity.jwxt.todaylesson.widget.TodayLessonWidgetUpdater
import edu.cqwu.electricity.common.settings.SettingsKeys
import edu.cqwu.electricity.common.settings.SettingsPreferences
import edu.cqwu.electricity.common.settings.UserAgentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 自定义 Application，配置 Coil ImageLoader
 *
 * - 磁盘缓存：100MB，目录为 cacheDir/image_cache
 * - 内存缓存：30% 可用堆内存（默认 25%）
 * - 启用 crossfade 淡入动画
 */
class ElectricityApp : Application(), ImageLoaderFactory {

    /** 与应用同生命周期的作用域：只用于订阅「课表缓存已更新」信号（进程结束即结束，无需取消） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val settingsPrefs = SettingsPreferences(this)
        WebVpnSettings.enabled = settingsPrefs.get(SettingsKeys.WEBVPN_ENABLED)
        AppLog.setMinLevel(settingsPrefs.get(SettingsKeys.LOG_LEVEL))
        // 崩溃捕获必须在最前面注册，确保第三方 SDK 初始化前就已就绪
        CrashHandler.init(this)
        instance = this
        CookieStore.init()
        // 预初始化登录会话仓库（EncryptedSharedPreferences 初始化耗时 ~100ms），
        // 避免在 UI 组合线程中首次调用时阻塞滑动动画
        AccountSessionStore.init(this)
        // 恢复上次激活账号的登录态到系统 CookieManager（经会话协调器）
        SessionCoordinatorV2.restoreActive()
        // 今日课表仓库需要提前注入 Context（小组件被桌面进程拉起时会先走 Application.onCreate）
        TodayLessonRepository.init(this)
        // 「今日课表」小组件的数据来自课表接口，同样要提前注入 Context（理由同上）
        TimetableWidgetRepositoryV2.init(this)
        // 进程一起来就先刷一次桌面小组件：覆盖安装后系统不保证补发 APPWIDGET_UPDATE，
        // 这一步让「打开 App 的任意页面」都能把组件从加载占位里救回来
        TodayLessonWidgetUpdater.renderFromCache(this)
        // 课表缓存一更新就刷桌面（数据变化即刷新，等价于参考实现里 Koin + Flow 的那条链路）
        TodayLessonRepository.dataUpdated
            .onEach { TodayLessonWidgetUpdater.renderFromCache(this@ElectricityApp) }
            .launchIn(appScope)
        // 「今日课表」（新）同理：课表缓存一更新就刷桌面（缓存由课表页写，也可由上面的启动补取写）
        TimetableWidgetRepositoryV2.dataUpdated
            .onEach { TimetableWidgetUpdaterV2.renderFromCache(this@ElectricityApp) }
            .launchIn(appScope)
        // 课表接口那份缓存不可用时后台补取一次：小组件在桌面上跑、不能联网判断"现在是第几周"，
        // 所以跨周之后这是它唯一的自救机会。判断与取数都在协程里（读缓存也是文件 IO）；
        // 失败只记日志——旧缓存仍能给桌面兜底显示。
        appScope.launch {
            if (TimetableWidgetRepositoryV2.needsRefresh()) {
                TimetableWidgetRepositoryV2.fetchAndCache()
                    .onFailure { AppLog.w(TAG, "启动补取课表失败", it) }
            }
        }
        // 浏览器标识提供者需要 Context 读设置：显式初始化（避免它反向依赖本 Application 单例）
        UserAgentProvider.init(this)
        // 网络运行时依赖注入（组合根）：WebVPN 自动登录回调 + 当前 UA。
        // 必须先于任何 client 的首次创建（HttpClientFactory 各 client 为 lazy，首次访问在 Activity 期）。
        HttpClientFactory.initRuntime(
            webVpnLogin = { protectedUrl -> CasAuthFlow.ensureClientVpnActive(protectedUrl) },
            userAgent = { UserAgentProvider.getActiveUserAgent() },
        )
    }

    companion object {
        private const val TAG = "ElectricityApp"

        lateinit var instance: ElectricityApp
            private set
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .okHttpClient {
                // WebVPN 图片专用 client：会话过期时不把 SessionExpiredException 抛到 OkHttp 异步线程
                HttpClientFactory.webVpnImageClient
            }
            .build()
    }
}
