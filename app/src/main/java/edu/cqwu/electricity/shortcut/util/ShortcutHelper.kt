package edu.cqwu.electricity.shortcut.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import edu.cqwu.electricity.app.MainActivity
import edu.cqwu.electricity.R

/**
 * 桌面快捷方式工具类
 *
 * 负责创建 Pinned Shortcut（固定快捷方式）和解析快捷方式启动时的 Intent。
 * 使用 AndroidX [ShortcutManagerCompat]，兼容 API 21+。
 */
object ShortcutHelper {

    private const val TAG = "ShortcutHelper"

    /** Intent Extra Key：首页应用的 appId */
    const val EXTRA_SHORTCUT_APP_ID = "shortcut_app_id"

    /** Intent Extra Key：快捷方式显示名称 */
    const val EXTRA_SHORTCUT_APP_NAME = "shortcut_app_name"

    /** Intent Extra Key：应用的 openUrl */
    const val EXTRA_SHORTCUT_OPEN_URL = "shortcut_open_url"

    /** Intent Extra Key：应用图标 URL */
    const val EXTRA_SHORTCUT_ICON_URL = "shortcut_icon_url"

    /**
     * 从 Intent 中提取快捷方式携带的应用信息。
     * @return 如果是快捷方式启动则返回 [ShortcutAppInfo]，否则返回 null
     */
    fun extractShortcutAppInfo(intent: Intent): ShortcutAppInfo? {
        val appId = intent.getStringExtra(EXTRA_SHORTCUT_APP_ID) ?: return null
        val appName = intent.getStringExtra(EXTRA_SHORTCUT_APP_NAME) ?: ""
        val openUrl = intent.getStringExtra(EXTRA_SHORTCUT_OPEN_URL) ?: ""
        val iconUrl = intent.getStringExtra(EXTRA_SHORTCUT_ICON_URL) ?: ""
        return ShortcutAppInfo(appId = appId, appName = appName, openUrl = openUrl, iconUrl = iconUrl)
    }

    /**
     * 检查当前设备是否支持固定快捷方式。
     */
    fun isSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }

    /**
     * 快捷方式创建结果
     */
    sealed class CreateResult {
        /** 创建成功 */
        data object Success : CreateResult()
        /** 系统不支持快捷方式创建 */
        data object NotSupported : CreateResult()
        /** 创建失败（权限不足或系统拦截） */
        data class Failed(val exception: Exception?) : CreateResult()
    }

    /**
     * 创建桌面固定快捷方式。
     *
     * @param context 上下文
     * @param appInfo 快捷方式携带的应用信息
     * @param label 在桌面显示的名称
     * @return [CreateResult] 创建结果
     */
    suspend fun createPinnedShortcut(
        context: Context,
        appInfo: ShortcutAppInfo,
        label: String
    ): CreateResult {
        // 前置检查：桌面是否支持固定快捷方式
        if (!isSupported(context)) {
            Log.w(TAG, "当前桌面不支持固定快捷方式")
            return CreateResult.NotSupported
        }

        return try {
            val shortcutId = "shortcut_${appInfo.appId}"

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(EXTRA_SHORTCUT_APP_ID, appInfo.appId)
                putExtra(EXTRA_SHORTCUT_APP_NAME, appInfo.appName)
                putExtra(EXTRA_SHORTCUT_OPEN_URL, appInfo.openUrl)
                putExtra(EXTRA_SHORTCUT_ICON_URL, appInfo.iconUrl)
                // 融入现有任务栈，复用已有 MainActivity 实例
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            // 尝试从 iconUrl 异步下载图标，失败则回退到应用图标
            val iconCompat = loadIconFromUrl(context, appInfo.iconUrl)
                ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)

            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(label.take(10))
                .setLongLabel(appInfo.appName.ifEmpty { label })
                .setIcon(iconCompat)
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            Log.d(TAG, "快捷方式创建请求已提交: id=$shortcutId, label=$label")
            CreateResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "创建快捷方式失败", e)
            CreateResult.Failed(e)
        }
    }

    /**
     * 从 URL 下载图标并转为 [IconCompat]。
     * 复用 App 级别的 Coil [ImageLoader] 单例，避免重复创建。
     * 总是创建独立的 Bitmap 副本，防止 Coil 缓存回收后图标异常。
     */
    private suspend fun loadIconFromUrl(context: Context, iconUrl: String): IconCompat? {
        if (iconUrl.isBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(iconUrl)
                .size(192)
                .allowHardware(false)
                .build()
            val imageLoader = coil.Coil.imageLoader(context)
            val result = imageLoader.execute(request)
            val drawable = result.drawable ?: return null
            val sourceBitmap = (drawable as? BitmapDrawable)?.bitmap
            val bitmap = if (sourceBitmap != null) {
                // 创建独立副本，防止 Coil 缓存回收后图标异常
                sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                // 非 BitmapDrawable，手动绘制为 Bitmap
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            IconCompat.createWithBitmap(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "从 URL 加载图标失败: $iconUrl", e)
            null
        }
    }

    /**
     * 快捷方式携带的应用信息
     */
    data class ShortcutAppInfo(
        val appId: String,
        val appName: String,
        val openUrl: String,
        val iconUrl: String = ""
    )
}
