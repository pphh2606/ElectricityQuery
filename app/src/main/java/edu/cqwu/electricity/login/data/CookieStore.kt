package edu.cqwu.electricity.login.data

import android.net.Uri
import android.webkit.CookieManager

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

    fun removeAllCookies() {
        checkInitialized()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("CookieStore 未初始化，请先调用 CookieStore.init(context)")
        }
    }
}

/**
 * Per-user in-memory cookie store for multi-account isolation.
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

    fun removeAllCookies() {
        cookieMap.clear()
    }

    fun getAllCookies(): Map<String, Map<String, String>> {
        return cookieMap.toMap()
    }

    fun syncToCookieManager() {
        val cm = CookieManager.getInstance()
        for ((url, domainCookies) in cookieMap) {
            for ((name, value) in domainCookies) {
                cm.setCookie(url, "$name=$value")
            }
        }
        cm.flush()
    }

    fun syncFromCookieManager() {
        val cm = CookieManager.getInstance()
        val knownDomains = listOf(
            "https://authserver.cqwu.edu.cn",
            "https://clientvpn.cqwu.edu.cn",
            "https://electricitypay.cqwu.edu.cn",
            "https://pay.cqwu.edu.cn",
            "http://218.194.176.214:8382",
        )
        for (url in knownDomains) {
            val cookies = cm.getCookie(url) ?: continue
            CookieParser.parse(cookies).forEach { (name, value) ->
                setCookie(url, "$name=$value")
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
