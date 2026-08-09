package edu.cqwu.electricity.network

import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import java.io.IOException

/**
 * HTTP 诊断日志工具，用于排查 OkHttp 重定向链和请求异常。
 */
object HttpDiagnostics {

    private const val MAX_HOPS = 30
    private const val TAG = "HttpDiagnostics"

    /**
     * 只记录缴费服务大厅相关的请求，避免正常业务请求刷屏。
     */
    val eventListener: EventListener = object : EventListener() {
        override fun responseHeadersEnd(call: Call, response: Response) {
            val url = response.request.url.toString()
            if (!shouldLog(url)) return

            val location = response.header("Location")
            val cookieNames = response.request.header("Cookie")
                ?.split(";")
                ?.map { it.trim().substringBefore("=") }
                ?.filter { it.isNotBlank() }
                ?.groupingBy { it }
                ?.eachCount()
            val setCookieNames = response.headers("Set-Cookie")
                .map { it.substringBefore("=").trim() }

            val locationPart = if (location.isNullOrBlank()) "" else " -> ${location.take(200)}"
            Log.d(
                TAG,
                "HTTP hop: [${response.code}] ${url.take(200)}$locationPart " +
                    "cookies=$cookieNames setCookie=$setCookieNames",
            )
        }

        override fun callFailed(call: Call, ioe: IOException) {
            val url = call.request().url.toString()
            if (shouldLog(url)) {
                Log.e(TAG, "HTTP call failed: url=${url.take(200)}", ioe)
            }
        }
    }

    private fun shouldLog(url: String): Boolean {
        return url.startsWith("https://pay.cqwu.edu.cn") ||
            url.startsWith("https://clientvpn.cqwu.edu.cn")
    }

    /**
     * 打印从初始 URL 到最终响应的完整重定向链。
     */
    fun logRedirectChain(tag: String, startUrl: String, response: Response) {
        val hops = mutableListOf<String>()
        var current: Response? = response
        var count = 0
        while (current != null && count < MAX_HOPS) {
            val location = current.header("Location")
            val url = current.request.url.toString().take(200)
            hops.add(
                if (location.isNullOrBlank()) {
                    "[${current.code}] $url"
                } else {
                    "[${current.code}] $url -> ${location.take(200)}"
                }
            )
            current = current.priorResponse
            count++
        }

        if (hops.size > 1) {
            Log.d(tag, "HTTP 重定向链: start=${startUrl.take(200)}")
            hops.reversed().forEachIndexed { index, hop ->
                Log.d(tag, "  hop[$index] $hop")
            }
        } else {
            Log.d(tag, "HTTP 响应: start=${startUrl.take(200)} -> [${response.code}] ${response.request.url.toString().take(200)}")
        }
    }

    /**
     * 打印请求异常，便于定位 OkHttp "Too many follow-up requests" 等错误。
     */
    fun logFailure(tag: String, url: String, throwable: Throwable) {
        Log.e(
            tag,
            "HTTP 请求失败: url=${url.take(200)}, error=${throwable::class.simpleName}: ${throwable.message}",
            throwable,
        )
    }
}
