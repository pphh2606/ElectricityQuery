package edu.cqwu.electricity.common.net

import edu.cqwu.electricity.logging.AppLog
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 通用的 OkHttp CookieJar 桥接实现，保存和读取逻辑通过回调绑定到具体存储。
 */
internal class CookieJarBridge(
    private val readCookies: (String) -> String?,
    private val writeCookie: (String, String) -> Unit,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val baseUrl = url.newBuilder().query(null).build().toString()
        for (cookie in cookies) {
            // 写库前检查非 ASCII：OkHttp Cookie 值只允许可见 ASCII，先在这里定位谁种下的非法 cookie
            logNonAsciiIfPresent("save", baseUrl, cookie.name, cookie.value)
            writeCookie(baseUrl, buildSetCookieString(cookie))
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString = readCookies(url.toString()) ?: return emptyList()

        val cookiesMap = CookieParser.parse(cookieString)

        return cookiesMap.mapNotNull { (name, value) ->
            try {
                if (value.isEmpty()) return@mapNotNull null
                // 出库拼头前检查：非 ASCII 值会导致 OkHttp BridgeInterceptor 组装 Cookie 头时抛
                // IllegalArgumentException(Unexpected char ... in Cookie value)，这里先定位元凶
                logNonAsciiIfPresent("load", url.toString(), name, value)

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
                AppLog.w("CookieJarBridge", "解析 Cookie 失败: $name=****", e)
                null
            }
        }
    }

    /**
     * 排查用日志：cookie 名/值只允许可见 ASCII（0x20..0x7E），一旦出现其它字符，
     * OkHttp 在把 Cookie 写入请求头时会抛 IllegalArgumentException（复现日志中的
     * "Unexpected char ... in Cookie value"）。
     * 注意：不打印 cookie 值内容（可能含 token/隐私），只输出域名、cookie 名、长度与
     * 每个非 ASCII 字符的码点（U+xxxx）与下标，便于定位元凶且不泄密。
     */
    private fun logNonAsciiIfPresent(phase: String, url: String, name: String, value: String) {
        val offenders = value.mapIndexedNotNull { index, c ->
            val code = c.code
            if (code < 0x20 || code > 0x7E) "U+${code.toString(16).uppercase()}@$index" else null
        }
        if (offenders.isNotEmpty()) {
            AppLog.w(
                "CookieJarBridge",
                "非 ASCII Cookie 值 [phase=$phase] url=$url cookie=$name 长度=${value.length} offenders=[${offenders.joinToString(", ")}]",
            )
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
                Locale.US,
            ).apply { timeZone = TimeZone.getTimeZone("GMT") }
            sb.append("; Expires=${expires.format(Date(cookie.expiresAt))}")
        }
        return sb.toString()
    }
}

/**
 * 桥接 OkHttp 与 android.webkit.CookieManager 的 CookieJar 实现。
 *
 * 所有 Cookie 统一保存在 CookieManager（浏览器缓存）中，
 * OkHttp 通过此 CookieJar 直接读写 CookieManager，无需额外同步。
 */
object CookieStoreOkHttpJar : CookieJar by CookieJarBridge(
    readCookies = { CookieManager.getInstance().getCookie(it) },
    writeCookie = { url, cookie ->
        CookieManager.getInstance().setCookie(url, cookie)
        CookieManager.getInstance().flush()
    },
)

/**
 * 多实例 CookieJar，绑定到特定用户的 UserCookieStore。
 *
 * 用于多用户场景：每个用户拥有独立的 OkHttpClient，
 * 使用此 CookieJar 读写用户独立的内存 Cookie 存储，
 * 与系统 CookieManager 完全隔离，互不干扰。
 */
class UserAwareCookieJar(
    private val cookieStore: UserCookieStore,
) : CookieJar by CookieJarBridge(
    readCookies = { cookieStore.getCookie(it) },
    writeCookie = { url, cookie -> cookieStore.setCookie(url, cookie) },
)
