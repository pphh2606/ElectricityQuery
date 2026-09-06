package edu.cqwu.electricity.common.net

import android.webkit.CookieManager
import edu.cqwu.electricity.logging.AppLog
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * WebVPN 网络层拦截器 —— 检测"需要登录"，**绝不二次 proceed**。
 *
 * OkHttp 约束：网络层拦截器只能调用一次 [chain.proceed]（URL 改写已在应用层完成，
 * host 已固定为 clientvpn）。因此本层职责只限：
 * 1. 对已是 clientvpn 代理 URL 的请求执行**一次** proceed；
 * 2. 无 CookieJar 客户端在此持久化代理 Set-Cookie；
 * 3. 响应命中"需要登录"（401/403、302 自循环/authserver、HTML 登录页）时，触发一次
 *    [sessionAuthenticator]（会话层单飞去重），随后抛 [WebVpnRetryException]，
 *    由应用层 [WebVpnUrlTransformer] 带新 cookie 重试一次。
 *
 * 登录失败抛出的 [SessionExpiredException] 原样上抛（swallow 语义由应用层统一处理）。
 */
class WebVpnInterceptor(
    private val cookieJar: CookieJar?,
    private val sessionAuthenticator: (String) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!WebVpnSettings.enabled || !WebVpnEncoder.isWebVpnUrl(original.url.toString())) {
            return chain.proceed(original)
        }

        // 恰好一次 proceed（网络层禁止重试）
        val response = chain.proceed(original)
        if (cookieJar == null) {
            saveProxyCookies(response) // 无 jar：手动持久化代理 cookie
        }
        if (!needsWebVpnLogin(response, original)) return response

        // 需要登录：关闭当前响应、触发一次认证（会话层单飞）、通知应用层重试
        AppLog.w(TAG, "WebVPN 响应需要登录，触发自动登录: HTTP ${response.code}")
        response.close()
        sessionAuthenticator(decodeIfWebVpn(original.url.toString()))
        throw WebVpnRetryException()
    }

    private fun saveProxyCookies(response: Response) {
        val cookieManager = CookieManager.getInstance()
        response.headers("Set-Cookie").forEach { cookie ->
            cookieManager.setCookie(WebVpnEncoder.PROXY_BASE, cookie)
        }
        cookieManager.flush()
    }

    /** 响应是否表示"需要登录"：401/403、302 自循环或指向 authserver、HTML 为 CAS 登录页 */
    internal fun needsWebVpnLogin(response: Response, request: Request): Boolean {
        if (response.code == 401 || response.code == 403) return true

        if (response.code in 300..399) {
            val location = response.header("Location") ?: return false
            val resolved = runCatching { request.url.resolve(location) }.getOrNull() ?: return false
            if (isSameUrl(resolved, request.url)) return true
            if (isAuthServerLogin(resolved)) return true
            return false
        }

        if (response.isSuccessful && isHtmlResponse(response)) {
            val body = response.peekBody(MAX_LOGIN_PAGE_BYTES.toLong()).string()
            return HtmlFormParser.isCasLoginPage(body)
        }
        return false
    }

    private fun isHtmlResponse(response: Response): Boolean {
        val contentType = response.header("Content-Type")?.lowercase() ?: return false
        return contentType.startsWith("text/html") || contentType.startsWith("application/xhtml+xml")
    }

    private fun isAuthServerLogin(url: HttpUrl): Boolean {
        if (url.host == AUTH_SERVER_HOST) return true
        val originalHost = runCatching {
            WebVpnEncoder.decode(url.toString()).toHttpUrlOrNull()?.host
        }.getOrNull()
        return originalHost == AUTH_SERVER_HOST || url.encodedPath.contains("/authserver/")
    }

    private fun isSameUrl(a: HttpUrl, b: HttpUrl): Boolean {
        return a.scheme == b.scheme &&
            a.host == b.host &&
            a.port == b.port &&
            a.encodedPath == b.encodedPath &&
            a.encodedQuery == b.encodedQuery
    }

    private fun decodeIfWebVpn(urlString: String): String {
        return runCatching { WebVpnEncoder.decode(urlString) }.getOrDefault(urlString)
    }

    companion object {
        private const val TAG = "WebVpnInterceptor"
        private const val MAX_LOGIN_PAGE_BYTES = 512 * 1024
        private const val AUTH_SERVER_HOST = "authserver.cqwu.edu.cn"

        private val passthroughHosts = setOf(
            "clientvpn.cqwu.edu.cn",
            "mail.cqwu.edu.cn",
            "cqwu.fysso.chaoxing.com",
        )

        /**
         * 纯判定逻辑：WebVPN 开启、http(s)、非放行域名、且尚未是代理 URL 时执行转换。
         */
        @JvmStatic
        fun shouldTransform(urlString: String): Boolean {
            if (!WebVpnSettings.enabled) return false
            val url = runCatching { urlString.toHttpUrlOrNull() }.getOrNull() ?: return false
            if (url.scheme != "http" && url.scheme != "https") return false
            if (url.host in passthroughHosts) return false
            return !WebVpnEncoder.isWebVpnUrl(urlString)
        }
    }
}

/**
 * 网络层检测到 WebVPN 需要登录后抛给应用层，由应用层带新 cookie 重试一次。
 * 仅作控制流信号，不携带数据。
 */
internal class WebVpnRetryException : Exception()
