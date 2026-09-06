package edu.cqwu.electricity.common.net

import android.webkit.CookieManager
import edu.cqwu.electricity.logging.AppLog
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * WebVPN 应用层拦截器 —— URL 改写 + 登录后重试。
 *
 * OkHttp 约束：URL 改 host/port 只能在应用层做；**只有应用层拦截器可多次 proceed**。
 * 职责：
 * 1. 开关开启且需转换时，把校内 URL 改写为 clientvpn 代理 URL（Origin/Referer 同步改写，
 *    无 CookieJar 客户端回填代理 Cookie）；开关关闭时原样放行；
 * 2. 网络层 [WebVpnInterceptor] 只 proceed 一次，检测到需登录会触发登录并抛
 *    [WebVpnRetryException]；本层捕获后**带新 cookie 重试一次**，重试仍失败则转抛
 *    [SessionExpiredException]（真实登录失败，交给业务层）；
 * 3. [SessionExpiredException]（含登录失败、会话过期）在此按 [swallowSessionExpired]
 *    统一收敛为 502（图片等异步场景），避免异常外泄到异步线程。
 */
class WebVpnUrlTransformer(
    private val cookieJar: CookieJar?,
    private val swallowSessionExpired: Boolean = false,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!WebVpnInterceptor.shouldTransform(original.url.toString())) {
            return chain.proceed(original)
        }
        return try {
            // 第一次 + （登录后）重试一次：应用层可多次 proceed
            try {
                chain.proceed(rewrite(original) ?: original)
            } catch (e: WebVpnRetryException) {
                chain.proceed(rewrite(original) ?: original)
            }
        } catch (e: WebVpnRetryException) {
            // 重试后仍需要登录：登录未生效，转真实会话过期
            throw SessionExpiredException(
                "WebVPN 自动登录后仍需要登录",
                SessionExpiryReason.LOGIN_REJECTED,
            )
        } catch (e: SessionExpiredException) {
            if (swallowSessionExpired) {
                sessionExpiredResponse(chain.request())
            } else {
                throw e
            }
        }
    }

    private fun sessionExpiredResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(502)
            .message("WebVPN session expired")
            .body("WebVPN session expired".toResponseBody("text/plain; charset=utf-8".toMediaType()))
            .build()
    }

    /** 原始请求 → 代理请求；URL 无法转换时返回 null（调用方按原请求放行） */
    private fun rewrite(original: Request): Request? {
        val transformedUrl = runCatching {
            WebVpnEncoder.transform(original.url.toString())
        }.getOrNull() ?: return null

        AppLog.url(TAG, "WebVPN 转换: ${original.url} -> $transformedUrl")
        val builder = original.newBuilder().url(transformedUrl)
        rewriteSameHostHeader(builder, original, "Origin")
        rewriteSameHostHeader(builder, original, "Referer")

        // 仅无 CookieJar 客户端手动注入代理 Cookie（带 jar 时 OkHttp 会在请求前自动从 jar
        // 携带，手动注入会造成 Cookie 头重复）。jar 为空时仍需首请求就带代理 Cookie。
        if (cookieJar == null) {
            reinjectProxyCookie(builder)
        }
        return builder.build()
    }

    /** 无 CookieJar 时从系统 CookieManager 回填 clientvpn 代理 Cookie */
    private fun reinjectProxyCookie(builder: Request.Builder) {
        val proxyCookies = CookieManager.getInstance().getCookie(WebVpnEncoder.PROXY_BASE)
        if (proxyCookies.isNullOrBlank()) {
            builder.removeHeader("Cookie")
        } else {
            builder.header("Cookie", proxyCookies)
        }
    }

    /** Origin/Referer 若指向原始同主机则改写为代理基址 */
    private fun rewriteSameHostHeader(builder: Request.Builder, original: Request, name: String) {
        val value = original.header(name) ?: return
        val headerUrl = runCatching { value.toHttpUrlOrNull() }.getOrNull() ?: return
        if (headerUrl.host == original.url.host) {
            builder.header(name, WebVpnEncoder.PROXY_BASE)
        }
    }

    private companion object {
        const val TAG = "WebVpnUrlTransformer"
    }
}
