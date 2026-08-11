package edu.cqwu.electricity.login.data

import android.util.Log
import edu.cqwu.electricity.feedback.util.LogRedactor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Shared redirect-chain walking used by service login and WebVPN auto login.
 */
object RedirectChainFollower {

    const val MAX_REDIRECTS = 10

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
        var currentUrl = startUrl
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            tag?.let { Log.d(it, "redirect ${redirectCount + 1}: ${LogRedactor.url(currentUrl)}") }
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
                        currentUrl = resolve(currentUrl, location)
                        redirectCount++
                    }
                    in 200..299 -> {
                        val body = it.body.string()
                        if (HtmlFormParser.isCasLoginPage(body)) {
                            return currentUrl to body
                        }
                        val jsRedirect = HtmlFormParser.extractJsRedirect(body)
                        if (jsRedirect != null) {
                            currentUrl = resolve(currentUrl, jsRedirect)
                            redirectCount++
                        } else {
                            return null
                        }
                    }
                    else -> {
                        if (tolerateHttpError) {
                            return null
                        }
                        throw IOException("protected URL request failed: HTTP ${it.code}")
                    }
                }
            }
        }

        throw IOException("redirect count exceeded limit: $startUrl")
    }
}
