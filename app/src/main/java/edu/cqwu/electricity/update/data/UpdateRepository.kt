package edu.cqwu.electricity.update.data
import edu.cqwu.electricity.logging.AppLog

import com.google.gson.Gson
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.common.net.HttpClientFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Checks for app updates from the public assets repository.
 *
 * Metadata is fetched from all mirrors concurrently so a slow or failed
 * mirror does not delay the whole check. The campus WebVPN interceptor is
 * intentionally excluded so update requests never go through the school proxy.
 */
class UpdateRepository(
    private val timeoutMs: Long = DEFAULT_UPDATE_TIMEOUT_MS,
    private val client: OkHttpClient = updateHttpClient(timeoutMs),
    private val gson: Gson = Gson(),
) {

    suspend fun check(channel: UpdateChannel): UpdateInfo? = withContext(Dispatchers.IO) {
        val localVersionCode = BuildConfig.VERSION_CODE.toLong()
        val deferreds = endpointUrls(channel).map { url ->
            async { url to fetchInfo(url) }
        }
        val pending = deferreds.toMutableSet()
        var found: UpdateInfo? = null
        var fallback: UpdateInfo? = null
        withTimeoutOrNull(timeoutMs) {
            while (found == null && pending.isNotEmpty()) {
                select<Unit> {
                    pending.forEach { deferred ->
                        deferred.onAwait { (_, info) ->
                            pending.remove(deferred)
                            if (info != null && !info.app.link.isNullOrBlank()) {
                                if (info.app.versionCode > localVersionCode) {
                                    found = info
                                } else if (fallback == null) {
                                    fallback = info
                                }
                            }
                            Unit
                        }
                    }
                }
            }
        }
        deferreds.forEach { it.cancel() }
        found ?: fallback
    }

    internal fun endpointUrls(channel: UpdateChannel): List<String> =
        UpdateMirrorSources.metadataUrls("${channel.fileName}.json")

    private suspend fun fetchInfo(url: String): UpdateInfo? =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeIfActive(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                val info = if (it.isSuccessful && continuation.isActive) {
                                    try {
                                        gson.fromJson(it.body.string(), UpdateInfo::class.java)
                                    } catch (_: Exception) {
                                        AppLog.w("UpdateRepository", "更新信息 JSON 解析失败")
                                        null
                                    }
                                } else {
                                    null
                                }
                                continuation.resumeIfActive(info)
                            }
                        } catch (e: Exception) {
                            AppLog.w("UpdateRepository", "更新请求处理失败: ${e.message}")
                            continuation.resumeIfActive(null)
                        }
                    }
                },
            )
        }

}

private const val DEFAULT_UPDATE_TIMEOUT_MS = 3000L

internal fun updateHttpClient(timeoutMs: Long): OkHttpClient =
    HttpClientFactory.create(
        includeWebVpn = false,
    ).newBuilder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

private fun CancellableContinuation<UpdateInfo?>.resumeIfActive(value: UpdateInfo?) {
    if (isActive) resume(value)
}
