package edu.cqwu.electricity

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import edu.cqwu.electricity.data.network.common.CookieStore
import edu.cqwu.electricity.data.network.pay.HttpClientFactory
import edu.cqwu.electricity.util.CrashHandler

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
        // 崩溃捕获必须在最前面注册，确保第三方 SDK 初始化前就已就绪
        CrashHandler.init(this)
        instance = this
        CookieStore.init()
        // 预初始化 AccountStore 单例（EncryptedSharedPreferences 初始化耗时 ~100ms）
        // 避免在 UI 组合线程中首次调用时阻塞滑动动画
        edu.cqwu.electricity.data.local.AccountStore.getInstance(this)
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
                // 使用共享的 OkHttpClient，自动携带 ehall 认证 Cookie
                // 解决 Coil 加载 ehall 图片时无 Cookie 导致返回 HTML 而非图片的问题
                HttpClientFactory.shared
            }
            .build()
    }
}
