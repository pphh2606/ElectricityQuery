package edu.cqwu.electricity.common.net

import edu.cqwu.electricity.logging.AppLog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder

/**
 * Shared redirect-chain walking used by service login and WebVPN auto login.
 *
 * 所有"带 CAS 会话跟随重定向链直到拿到服务凭证"的流程共用此处的骨架：
 * - [followToCasLoginPage]：cookie 凭证域（ePay/ehall 等），跟随到 CAS 登录页停下（登录态失效）或到达最终页
 * - [followToLocationToken]：pay 域（JWT 凭证），跟随到 302 Location 含 token= 停下并提取 token
 */
object RedirectChainFollower {

    const val MAX_REDIRECTS = 10

    /** 302 Location 中 token= 参数的匹配（pay dlyscas 凭证） */
    private val TOKEN_REGEX = Regex("""token=([^&]+)""")

    fun resolve(baseUrl: String, location: String): String {
        val baseHttpUrl = baseUrl.toHttpUrl()
        return baseHttpUrl.resolve(location)?.toString()
            ?: throw IOException("cannot resolve redirect URL: $location (base: $baseUrl)")
    }

    /**
     * Follows HTTP/JS redirects until a CAS login page or a final page is reached.
     *
     * @return (login page url, html) when a CAS login page is found, otherwise null.
     */
    fun followToCasLoginPage(
        client: OkHttpClient,
        startUrl: String,
        tolerateHttpError: Boolean = false,
        tag: String? = null,
        referer: String? = null,
    ): Pair<String, String>? {
        return followRedirects(
            client = client,
            startUrl = startUrl,
            tag = tag,
            referer = referer,
            tolerateHttpError = tolerateHttpError,
            onRedirect = { _, _ -> null },
            onPage = { url, body ->
                if (HtmlFormParser.isCasLoginPage(body)) url to body else null
            },
            onNoMoreRedirect = { _, _ -> null },
        )
    }

    /**
     * Follows HTTP/JS redirects until a 302 Location containing `token=` is found,
     * then extracts and returns the token (URL-decoded).
     *
     * 对应 pay.cqwu.edu.cn 登录链路：
     * GET /casLogin/ → (JS/302) → dlyscas 302 → Location 含 token=xxx
     *
     * @throws SessionExpiredException 重定向链被重定向到 CAS 登录页（CASTGC 无效）
     * @throws IOException 链到达死胡同（无 JS 跳转且非 token 重定向）或超限
     */
    fun followToLocationToken(
        client: OkHttpClient,
        startUrl: String,
        tag: String? = null,
        referer: String? = null,
    ): String {
        return followRedirects(
            client = client,
            startUrl = startUrl,
            tag = tag,
            referer = referer,
            tolerateHttpError = false,
            onRedirect = { _, location -> extractToken(location) },
            onPage = { _, body ->
                if (HtmlFormParser.isCasLoginPage(body)) {
                    throw SessionExpiredException("会话已过期，请重新登录")
                }
                null
            },
            onNoMoreRedirect = { url, _ ->
                throw IOException("未找到 token 重定向: $url")
            },
        )
    }

    // ═══════════════════════════════════════════
    //  公共循环骨架
    // ═══════════════════════════════════════════

    /**
     * 通用重定向链循环：
     * - 302：先给 [onRedirect] 一次终止机会（如提取 token），否则跟随
     * - 200：先给 [onPage] 一次终止机会（如检测 CAS 登录页），否则跟随 JS 跳转
     * - 200 无 JS 跳转：交给 [onNoMoreRedirect] 决定结果
     */
    private fun <T> followRedirects(
        client: OkHttpClient,
        startUrl: String,
        tag: String?,
        referer: String?,
        tolerateHttpError: Boolean,
        onRedirect: (currentUrl: String, location: String) -> T?,
        onPage: (currentUrl: String, html: String) -> T?,
        onNoMoreRedirect: (currentUrl: String, html: String) -> T,
    ): T {
        var currentUrl = startUrl
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            tag?.let { AppLog.url(it, "redirect ${redirectCount + 1}: $currentUrl") }
            val response = client.newCall(
                Request.Builder()
                    .url(currentUrl)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .apply {
                        if (referer != null) addHeader("Referer", referer)
                    }
                    .get()
                    .build(),
            ).execute()

            response.use {
                when (it.code) {
                    in 300..399 -> {
                        val location = it.header("Location")
                            ?: throw IOException("redirect missing Location: $currentUrl")
                        onRedirect(currentUrl, location)?.let { hit -> return hit }
                        currentUrl = resolve(currentUrl, location)
                        redirectCount++
                    }
                    in 200..299 -> {
                        val body = it.body.string()
                        onPage(currentUrl, body)?.let { hit -> return hit }
                        val jsRedirect = HtmlFormParser.extractJsRedirect(body)
                        if (jsRedirect != null) {
                            currentUrl = resolve(currentUrl, jsRedirect)
                            redirectCount++
                        } else {
                            return onNoMoreRedirect(currentUrl, body)
                        }
                    }
                    else -> {
                        if (tolerateHttpError) {
                            return onNoMoreRedirect(currentUrl, "")
                        }
                        throw IOException("redirect chain request failed: HTTP ${it.code} ($currentUrl)")
                    }
                }
            }
        }

        throw IOException("redirect count exceeded limit: $startUrl")
    }

    /** 从 302 Location 中提取 token= 参数（URL 解码），不存在返回 null */
    private fun extractToken(location: String): String? {
        val token = TOKEN_REGEX.find(location)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { URLDecoder.decode(token, "UTF-8") }.getOrDefault(token)
    }
}