package edu.cqwu.electricity.login.data

import android.webkit.WebStorage
import edu.cqwu.electricity.logging.AppLog

/**
 * 登录态清理器：统一清除系统 CookieManager 与 WebView DOM 存储（localStorage/sessionStorage/IndexedDB/WebSQL）。
 *
 * 被 [AccountSessionStore]（切换/删除/回未登录）与设置页"清除存储空间"共用，保证清理行为一致。
 */
object SessionCleaner {

    private const val TAG = "SessionCleaner"

    /** 清除系统 CookieManager 中的所有 cookie（同步等待完成） */
    fun clearSystemCookies() {
        try {
            CookieStore.removeAllCookies()
        } catch (e: Exception) {
            AppLog.w(TAG, "清除系统 cookie 失败", e)
        }
    }

    /** 清除 WebView DOM 存储（网页 localStorage 等），防止旧账号网页状态残留 */
    fun clearWebStorages() {
        try {
            WebStorage.getInstance().deleteAllData()
            AppLog.d(TAG, "已清除 WebView DOM 存储")
        } catch (e: Exception) {
            AppLog.w(TAG, "清除 WebView DOM 存储失败", e)
        }
    }

    /** 同时清除系统 cookie 与 WebView DOM 存储 */
    fun clearAll() {
        clearSystemCookies()
        clearWebStorages()
    }
}
