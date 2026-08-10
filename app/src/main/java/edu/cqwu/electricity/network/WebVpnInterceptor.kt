package edu.cqwu.electricity.network

import android.util.Log
import android.webkit.CookieManager
import edu.cqwu.electricity.app.ElectricityApp
import edu.cqwu.electricity.login.data.HtmlFormParser
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.login.data.SessionExpiryReason
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 标记手动 CAS 登录流程的请求，WebVPN 拦截器只做 URL 转换，不再触发自动登录。
 */
internal object ManualCasFlowTag

/**
 * WebVPN 请求转换与自动登录拦截器。
 *
 * 每个 OkHttpClient 使用自己的实例，保证 WebVPN Cookie 与客户端 CookieJar 隔离。
 * 同一实例同时作为应用层和网络层拦截器：
 * - 应用层负责 URL 转换、Origin/Referer 重写和代理 Cookie 注入。
 * - 网络层负责逐跳检测 401/403、302 自循环和 CAS 登录页，并在必要时登录后重试一次。
 */
class WebVpnInterceptor(
    private val cookieJar: CookieJar?,
    private val sessionAuthenticator: (String) -> Unit = { protectedUrl ->
        WebVpnSessionManager.authenticate(ElectricityApp.instance, protectedUrl, cookieJar)
    },
) : Interceptor {

    private object AppLayerTag

    private val autoLoginAttempted = ThreadLocal<Boolean>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!WebVpnSettings.enabled) return chain.proceed(original)

        return when {
            shouldTransform(original.url.toString()) -> {
                transformRequest(chain, original)
            }
            WebVpnEncoder.isWebVpnUrl(original.url.toString()) -> {
                if (original.tag(ManualCasFlowTag::class.java) != null) {
                    chain.proceed(original)
                } else if (original.tag(AppLayerTag::class.java) != null) {
                    handleProxyResponse(original) { chain.proceed(it) }
                } else {
                    executeWithLoginRetry(
                        original.newBuilder().tag(AppLayerTag::class.java, AppLayerTag).build(),
                    ) {
                        chain.proceed(it)
                    }
                }
            }
            else -> chain.proceed(original)
        }
    }

    private fun transformRequest(chain: Interceptor.Chain, original: Request): Response {
        val transformedUrl = runCatching {
            WebVpnEncoder.transform(original.url.toString())
        }.getOrNull() ?: return chain.proceed(original)

        Log.d(TAG, "WebVPN 转换: ${original.url.toString().take(200)} -> $transformedUrl")
        val response = executeWithLoginRetry(buildTransformedRequest(original, transformedUrl)) {
            chain.proceed(it)
        }
        if (cookieJar == null) {
            saveProxyCookies(response)
        }
        return response
    }

    internal fun handleProxyResponse(
        request: Request,
        proceed: (Request) -> Response,
    ): Response {
        val response = proceed(request)
        if (cookieJar == null) {
            saveProxyCookies(response)
        }
        if (needsWebVpnLogin(response, request)) {
            response.close()
            loginAndRetry(request)
        }
        return response
    }

    internal fun executeWithLoginRetry(
        transformedRequest: Request,
        proceed: (Request) -> Response,
    ): Response {
        return try {
            proceed(transformedRequest)
        } catch (e: WebVpnRetryException) {
            retryAfterLogin(transformedRequest, proceed)
        }
    }

    private fun retryAfterLogin(
        transformedRequest: Request,
        proceed: (Request) -> Response,
    ): Response {
        if (autoLoginAttempted.get() == true) {
            throw SessionExpiredException(
                "WebVPN 自动登录后仍需要登录",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        }

        autoLoginAttempted.set(true)
        try {
            return try {
                proceed(buildRetryRequest(transformedRequest))
            } catch (e: WebVpnRetryException) {
                throw SessionExpiredException(
                    "WebVPN 自动登录后仍需要登录",
                    SessionExpiryReason.LOGIN_REJECTED,
                )
            }
        } finally {
            autoLoginAttempted.remove()
        }
    }

    internal fun loginAndRetry(request: Request): Nothing {
        if (autoLoginAttempted.get() == true) {
            throw SessionExpiredException(
                "WebVPN 自动登录后仍需要登录",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        }

        val protectedUrl = runCatching {
            WebVpnEncoder.decode(request.url.toString())
        }.getOrNull() ?: request.url.toString()
        Log.d(TAG, "WebVPN 检测到需要登录，开始自动登录: $protectedUrl")
        sessionAuthenticator(protectedUrl)
        throw WebVpnRetryException()
    }

    private fun buildRetryRequest(original: Request): Request {
        val freshCookies = loadProxyCookies(original.url)
        return if (freshCookies.isNullOrBlank()) {
            original.newBuilder()
                .removeHeader("Cookie")
                .tag(AppLayerTag::class.java, AppLayerTag)
                .build()
        } else {
            original.newBuilder()
                .header("Cookie", freshCookies)
                .tag(AppLayerTag::class.java, AppLayerTag)
                .build()
        }
    }

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

    private fun buildTransformedRequest(original: Request, transformedUrl: String): Request {
        val builder = original.newBuilder()
            .url(transformedUrl)
            .tag(AppLayerTag::class.java, AppLayerTag)
        if (original.tag(ManualCasFlowTag::class.java) != null) {
            builder.tag(ManualCasFlowTag::class.java, ManualCasFlowTag)
        }

        rewriteOriginHeader(builder, original, "Origin")
        rewriteOriginHeader(builder, original, "Referer")

        val transformedHttpUrl = transformedUrl.toHttpUrlOrNull()
        if (transformedHttpUrl != null) {
            val proxyCookies = loadProxyCookies(transformedHttpUrl)
            if (proxyCookies.isNullOrBlank()) {
                builder.removeHeader("Cookie")
            } else {
                builder.header("Cookie", proxyCookies)
            }
        }

        return builder.build()
    }

    internal fun loadProxyCookies(url: HttpUrl): String? {
        if (cookieJar != null) {
            val cookies = cookieJar.loadForRequest(url)
            if (cookies.isEmpty()) return null
            return cookies.joinToString("; ") { "${it.name}=${it.value}" }
        }
        return CookieManager.getInstance().getCookie(WebVpnEncoder.PROXY_BASE)
    }

    private fun rewriteOriginHeader(
        builder: Request.Builder,
        original: Request,
        name: String,
    ) {
        val value = original.header(name) ?: return
        val headerUrl = runCatching { value.toHttpUrlOrNull() }.getOrNull() ?: return
        if (headerUrl.host == original.url.host) {
            builder.header(name, WebVpnEncoder.PROXY_BASE)
        }
    }

    private fun saveProxyCookies(response: Response) {
        val cookieManager = CookieManager.getInstance()
        response.headers("Set-Cookie").forEach { cookie ->
            cookieManager.setCookie(WebVpnEncoder.PROXY_BASE, cookie)
        }
        cookieManager.flush()
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
         * 纯判定逻辑，便于单元测试。
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
 * 网络层检测到 WebVPN 需要登录后抛给应用层，由应用层刷新 Cookie 并重试一次。
 */
internal class WebVpnRetryException : Exception()
