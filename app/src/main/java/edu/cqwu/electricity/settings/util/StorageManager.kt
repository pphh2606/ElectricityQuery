package edu.cqwu.electricity.settings.util

import android.content.Context
import android.webkit.CookieManager
import edu.cqwu.electricity.login.data.SessionCleaner
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import java.io.File
import java.util.Locale

/**
 * 存储空间管理工具类。
 *
 * 封装 7 类存储的大小计算和清除逻辑，供 [StorageClearScreen] 调用。
 * 所有方法均在调用线程执行，调用方应自行切换到 IO 调度器。
 */
class StorageManager(private val context: Context) {

    // ═══════════════════════════════════════════════════
    //  大小计算
    // ═══════════════════════════════════════════════════

    /** 图片缓存：cacheDir/image_cache */
    fun getCacheImageSize(): Long =
        getDirSize(File(context.cacheDir, "image_cache"))

    /** 崩溃日志：filesDir/crash_logs */
    fun getCrashLogSize(): Long =
        getDirSize(File(context.filesDir, "crash_logs"))

    /** 临时日志文件：cacheDir/logs */
    fun getTempLogSize(): Long =
        getDirSize(File(context.cacheDir, "logs"))

    /**
     * WebView 真实数据目录：dataDir/app_webview。
     *
     * Android 的 WebView 将 localStorage / IndexedDB / Cookies 数据库 / 网页资源 HTTP 缓存 /
     * GPUCache / Service Worker 等全部存放在此目录（而不是 cacheDir），
     * 系统设置「应用详情」中显示为「数据」。
     */
    private fun getWebViewDataDir(): File =
        File(context.applicationInfo.dataDir, "app_webview")

    /** WebView 浏览数据：系统 WebView 缓存目录 + dataDir/app_webview */
    fun getWebViewDataSize(): Long {
        val webViewDir = File(context.cacheDir, "WebView")
        val webviewDir = File(context.cacheDir, "webview")
        val chromiumDir = File(context.cacheDir, "org.chromium.android_webview")
        return getDirSize(webViewDir) + getDirSize(webviewDir) +
            getDirSize(chromiumDir) + getDirSize(getWebViewDataDir())
    }

    /**
     * Cookie / 会话数据：通过 CookieManager 存储。
     *
     * 由于 CookieManager 不提供直接的文件大小 API，
     * 此处通过统计所有已知域名的 Cookie 字符串字节数来估算。
     * 注意：removeAllCookies() 会清除所有网站的 Cookie，不仅仅是本应用域名。
     */
    fun getCookieDataSize(): Long {
        return try {
            val cm = CookieManager.getInstance() ?: return 0
            val allCookies = StringBuilder()
            for (domain in KNOWN_DOMAINS) {
                val cookies = cm.getCookie(domain)
                if (cookies != null) {
                    allCookies.append(cookies)
                }
            }
            if (allCookies.isEmpty()) 0L else allCookies.toString().toByteArray().size.toLong()
        } catch (_: Exception) {
            0
        }
    }

    /** 应用涉及的已知域名列表（与 CookieStore 保持一致） */
    private val KNOWN_DOMAINS = listOf(
        "https://authserver.cqwu.edu.cn",
        "https://electricitypay.cqwu.edu.cn",
        "https://pay.cqwu.edu.cn",
        "http://218.194.176.214:8382"
    )

    /** 应用设置：settings_preferences SharedPreferences 文件 */
    fun getSettingsSize(): Long {
        val spDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val spFile = File(spDir, "settings_preferences.xml")
        return if (spFile.exists()) spFile.length() else 0
    }

    /** 账号数据：account_session_store_encrypted EncryptedSharedPreferences 文件 */
    fun getAccountDataSize(): Long {
        val spDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val spFile = File(spDir, "account_session_store_encrypted.xml")
        return if (spFile.exists()) spFile.length() else 0
    }

    // ═══════════════════════════════════════════════════
    //  清除操作
    // ═══════════════════════════════════════════════════

    /** 清除图片缓存 */
    fun clearCacheImages() {
        deleteDir(File(context.cacheDir, "image_cache"))
    }

    /** 清除崩溃日志 */
    fun clearCrashLogs() {
        deleteDir(File(context.filesDir, "crash_logs"))
    }

    /** 清除临时日志文件 */
    fun clearTempLogs() {
        deleteDir(File(context.cacheDir, "logs"))
    }

    /** 清除 WebView 浏览数据（缓存目录 + dataDir/app_webview 数据目录 + DOM 存储） */
    fun clearWebViewData() {
        deleteDir(File(context.cacheDir, "WebView"))
        deleteDir(File(context.cacheDir, "webview"))
        deleteDir(File(context.cacheDir, "org.chromium.android_webview"))
        // WebView 真实数据目录（localStorage/IndexedDB/HTTP 缓存等），删除后 WebView 会自动重建
        deleteDir(getWebViewDataDir())
        SessionCleaner.clearWebStorages()
    }

    /**
     * 清除「登录信息和cookie」：清除所有浏览器登录数据（系统 cookie + 所有账号持久化的登录状态），
     * 保留账号管理弹窗中的账号和密码。
     */
    fun clearCookieData() {
        SessionCoordinatorV2.clearLoginStates()
    }

    /** 清除应用设置（恢复默认） */
    fun clearSettings() {
        val prefs = context.getSharedPreferences("settings_preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /** 清除账号数据（所有账号的学号/密码/登录状态 + 当前登录态） */
    fun clearAccountData() {
        SessionCoordinatorV2.clearAll()
    }

    // ═══════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════

    /**
     * 递归计算目录总大小。
     * 如果目录不存在或为空，返回 0。
     * 内部捕获异常，单个文件读取失败不影响整体计算。
     */
    private fun getDirSize(dir: File): Long {
        return try {
            if (!dir.exists()) return 0
            if (dir.isFile) return dir.length()
            var size = 0L
            val files = dir.listFiles() ?: return 0
            for (file in files) {
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
            size
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 递归删除目录内的所有内容，但保留目录本身。
     * 内部捕获异常，单个文件删除失败不影响其他文件的清理。
     */
    private fun deleteDir(dir: File) {
        if (!dir.exists()) return
        try {
            if (dir.isFile) {
                dir.delete()
                return
            }
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    deleteDir(child)
                    child.delete()  // 删除子目录本身
                } else {
                    child.delete()
                }
            }
        } catch (_: Exception) {
            // 单个文件删除失败，继续处理其他文件
        }
    }

    companion object {
        /**
         * 格式化文件大小为人类可读字符串。
         *
         * - < 1 KB → "x B"
         * - < 1 MB → "x.x KB"
         * - < 1 GB → "x.x MB"
         * - ≥ 1 GB → "x.xx GB"
         */
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
