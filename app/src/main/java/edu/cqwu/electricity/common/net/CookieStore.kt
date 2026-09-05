package edu.cqwu.electricity.common.net

import edu.cqwu.electricity.logging.AppLog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bridge over android.webkit.CookieManager for shared cookies.
 */
object CookieStore {

    private const val TAG = "CookieStore"

    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        CookieManager.getInstance().setAcceptCookie(true)
        isInitialized = true
    }

    fun getCookie(url: String): String? {
        checkInitialized()
        return CookieManager.getInstance().getCookie(url)
    }

    fun setCookie(url: String, cookieValue: String) {
        checkInitialized()
        CookieManager.getInstance().setCookie(url, cookieValue)
        CookieManager.getInstance().flush()
    }

    /**
     * 清除系统 CookieManager 中的所有 cookie。
     *
     * WebKit 的 removeAllCookies(callback) 要求运行在**带 Looper 的线程**（后台协程会抛
     * IllegalStateException），因此统一投递到主线程执行；本方法同步等待删除与 flush 完成
     * （最多 5 秒），确保后续写入新 cookie 时旧 cookie 已被完全清除。
     * 已在主线程调用时直接执行，避免自锁。
     */
    fun removeAllCookies() {
        checkInitialized()
        val latch = CountDownLatch(1)

        val doClear = {
            try {
                CookieManager.getInstance().removeAllCookies {
                    try {
                        CookieManager.getInstance().flush()
                    } catch (e: Exception) {
                        AppLog.w(TAG, "removeAllCookies flush 异常", e)
                    }
                    latch.countDown()
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "removeAllCookies 调用异常", e)
                try {
                    CookieManager.getInstance().flush()
                } catch (_: Exception) {
                }
                latch.countDown()
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            doClear()
        } else {
            Handler(Looper.getMainLooper()).post { doClear() }
        }

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                AppLog.w(TAG, "removeAllCookies 等待超时（5s）")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            AppLog.w(TAG, "removeAllCookies 等待被中断", e)
        }
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("CookieStore 未初始化，请先调用 CookieStore.init(context)")
        }
    }
}

/**
 * Per-user cookie store（内存临时容器）。
 *
 * 仅用于登录过程（账号密码登录 / 扫码登录）的隔离收集，以及会话验证时临时装载账号 cookie，
 * 持久化统一由 [AccountSessionStore] 负责。
 */
class UserCookieStore {

    private val cookieMap = mutableMapOf<String, MutableMap<String, String>>()

    fun getCookie(url: String): String? {
        val domainCookies = cookieMap[normalizeUrl(url)] ?: return null
        if (domainCookies.isEmpty()) return null
        return domainCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun getCookieValue(url: String, name: String): String? {
        return cookieMap[normalizeUrl(url)]?.get(name)
    }

    fun setCookie(url: String, cookieValue: String) {
        val normalized = normalizeUrl(url)
        val domainCookies = cookieMap.getOrPut(normalized) { mutableMapOf() }

        val eqIndex = cookieValue.indexOf("=")
        if (eqIndex < 0) return
        val name = cookieValue.substring(0, eqIndex).trim()
        val value = cookieValue.substring(eqIndex + 1).trim()
            .split(";")[0].trim()

        domainCookies[name] = value
    }

    fun getAllCookies(): Map<String, Map<String, String>> {
        return cookieMap.toMap()
    }

    /** 批量装载持久化 cookie（domain → name→value） */
    fun loadFrom(cookies: Map<String, Map<String, String>>) {
        for ((domain, kv) in cookies) {
            for ((name, value) in kv) {
                setCookie(domain, "$name=$value")
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            AppLog.w("UserCookieStore", "URL 解析失败，原样返回: $url")
            url
        }
    }
}
