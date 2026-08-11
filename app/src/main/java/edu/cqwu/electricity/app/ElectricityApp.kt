package edu.cqwu.electricity.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import edu.cqwu.electricity.login.data.CookieStore
import edu.cqwu.electricity.network.WebVpnSettings
import edu.cqwu.electricity.payment.data.HttpClientFactory
import edu.cqwu.electricity.feedback.util.CrashHandler
import edu.cqwu.electricity.settings.data.SettingsPreferences

/**
 * 自定义 Application，配置 Coil ImageLoader
 *
 * - 磁盘缓存：100MB，目录为 cacheDir/image_cache
 * - 内存缓存：30% 可用堆内存（默认 25%）
 * - 启用 crossfade 淡入动画
 */
class ElectricityApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        WebVpnSettings.enabled = SettingsPreferences(this).isWebVpnEnabled()
        // 崩溃捕获必须在最前面注册，确保第三方 SDK 初始化前就已就绪
        CrashHandler.init(this)
        instance = this
        CookieStore.init()
        // 预初始化 AccountStore 单例（EncryptedSharedPreferences 初始化耗时 ~100ms）
        // 避免在 UI 组合线程中首次调用时阻塞滑动动画
        edu.cqwu.electricity.login.data.AccountStore.getInstance(this)
    }

    companion object {
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
