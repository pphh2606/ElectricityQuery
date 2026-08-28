package edu.cqwu.electricity.login.data

import android.net.Uri
import android.webkit.CookieManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bridge over android.webkit.CookieManager for shared cookies.
 */
object CookieStore {

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
     * 使用回调 + CountDownLatch 同步等待删除完成（最多 3 秒，超时按已尽力处理继续），
     * 确保后续写入新 cookie 时旧 cookie 已被完全清除。
     */
    fun removeAllCookies() {
        checkInitialized()
        val latch = CountDownLatch(1)
        try {
            CookieManager.getInstance().removeAllCookies { latch.countDown() }
        } catch (e: Exception) {
            latch.countDown()
        }
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
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
            url
        }
    }
}
