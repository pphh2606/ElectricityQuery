package edu.cqwu.electricity.data.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 桥接 OkHttp 与 android.webkit.CookieManager 的 CookieJar 实现。
 *
 * 所有 Cookie 统一保存在 CookieManager（浏览器缓存）中，
 * OkHttp 通过此 CookieJar 直接读写 CookieManager，无需额外同步。
 *
 * 与 SharedHttpClient 配合使用：
 *   SharedHttpClient.init(context)
 *   // client 已自动使用此 CookieJar
 */
object CookieStoreOkHttpJar : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = CookieManager.getInstance()
        // 去除 query 参数，确保 CookieManager 正确识别 domain/path
        val baseUrl = url.newBuilder().query(null).build().toString()
        for (cookie in cookies) {
            val cookieString = buildSetCookieString(cookie)
            cm.setCookie(baseUrl, cookieString)
        }
        cm.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cm = CookieManager.getInstance()
        val cookieString = cm.getCookie(url.toString()) ?: return emptyList()

        // 1. 按 Cookie 名称去重：同名保留最后一个值（最新设置的）
        //    使用 LinkedHashMap 保持原始顺序
        val cookiesMap = linkedMapOf<String, String>()
        cookieString
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { pair ->
                val eqIndex = pair.indexOf("=")
                val name = pair.substring(0, eqIndex).trim()
                val value = pair.substring(eqIndex + 1).trim()
                cookiesMap[name] = value  // 同名覆盖，保留最后的值
            }

        // 2. 为去重后的 Cookie 生成 OkHttp Cookie 对象
        return cookiesMap.mapNotNull { (name, value) ->
            try {
                // 跳过空值 Cookie（服务端用于清除的无效条目）
                if (value.isEmpty()) return@mapNotNull null

                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .apply {
                        if (url.scheme == "https") secure()
                    }
                    .build()
            } catch (e: Exception) {
                android.util.Log.w("CookieStoreOkHttpJar", "解析 Cookie 失败: $name=$value", e)
                null
            }
        }
    }

    /**
     * 将 OkHttp Cookie 对象转换为 Set-Cookie 格式字符串
     */
    private fun buildSetCookieString(cookie: Cookie): String {
        val sb = StringBuilder()
        sb.append("${cookie.name}=${cookie.value}")
        sb.append("; Domain=${cookie.domain.trimStart('.')}")
        sb.append("; Path=${cookie.path}")
        if (cookie.secure) sb.append("; Secure")
        if (cookie.httpOnly) sb.append("; HttpOnly")
        if (cookie.expiresAt < Long.MAX_VALUE) {
            val expires = SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                Locale.US
            ).apply { timeZone = TimeZone.getTimeZone("GMT") }
            sb.append("; Expires=${expires.format(Date(cookie.expiresAt))}")
        }
        return sb.toString()
    }
}

/**
 * 多实例 CookieJar，绑定到特定用户的 UserCookieStore。
 *
 * 用于多用户场景：每个用户拥有独立的 OkHttpClient，
 * 使用此 CookieJar 读写用户独立的内存 Cookie 存储，
 * 与系统 CookieManager 完全隔离，互不干扰。
 *
 * 使用方式：
 *   val userStore = AccountManager.getCookiesForUser("2024xxxxx")
 *   val client = OkHttpClient.Builder()
 *       .cookieJar(UserAwareCookieJar(userStore))
 *       .build()
 */
class UserAwareCookieJar(
    private val cookieStore: UserCookieStore
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // 去除 query 参数，避免 query 干扰 Cookie 存储
        val baseUrl = url.newBuilder().query(null).build().toString()
        for (cookie in cookies) {
            val cookieString = buildSetCookieString(cookie)
            cookieStore.setCookie(baseUrl, cookieString)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString = cookieStore.getCookie(url.toString()) ?: return emptyList()

        // 1. 按 Cookie 名称去重：同名保留最后一个值（最新设置的）
        val cookiesMap = linkedMapOf<String, String>()
        cookieString
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { pair ->
                val eqIndex = pair.indexOf("=")
                val name = pair.substring(0, eqIndex).trim()
                val value = pair.substring(eqIndex + 1).trim()
                cookiesMap[name] = value
            }

        // 2. 为去重后的 Cookie 生成 OkHttp Cookie 对象
        return cookiesMap.mapNotNull { (name, value) ->
            try {
                // 跳过空值 Cookie
                if (value.isEmpty()) return@mapNotNull null

                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .apply {
                        if (url.scheme == "https") secure()
                    }
                    .build()
            } catch (e: Exception) {
                android.util.Log.w("UserAwareCookieJar", "解析 Cookie 失败: $name=$value", e)
                null
            }
        }
    }

    private fun buildSetCookieString(cookie: Cookie): String {
        val sb = StringBuilder()
        sb.append("${cookie.name}=${cookie.value}")
        sb.append("; Domain=${cookie.domain.trimStart('.')}")
        sb.append("; Path=${cookie.path}")
        if (cookie.secure) sb.append("; Secure")
        if (cookie.httpOnly) sb.append("; HttpOnly")
        if (cookie.expiresAt < Long.MAX_VALUE) {
            val expires = SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                Locale.US
            ).apply { timeZone = TimeZone.getTimeZone("GMT") }
            sb.append("; Expires=${expires.format(Date(cookie.expiresAt))}")
        }
        return sb.toString()
    }
}
