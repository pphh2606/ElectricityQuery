package edu.cqwu.electricity.update.data

import com.google.gson.Gson
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.payment.data.HttpClientFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import java.util.concurrent.TimeUnit

/**
 * Checks for app updates from the public assets repository.
 *
 * Metadata is fetched from all mirrors concurrently so a slow or failed
 * mirror does not delay the whole check. The campus WebVPN interceptor is
 * intentionally excluded so update requests never go through the school proxy.
 */
class UpdateRepository(
    private val timeoutMs: Long = DEFAULT_UPDATE_TIMEOUT_MS,
    private val client: OkHttpClient = defaultUpdateClient(timeoutMs),
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
        try {
            withTimeout(timeoutMs) {
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
        } catch (_: TimeoutCancellationException) {
        }
        deferreds.forEach { it.cancel() }
        found ?: fallback
    }

    fun needsUpdate(remote: UpdateInfo, localVersionCode: Int = BuildConfig.VERSION_CODE): Boolean =
        remote.app.versionCode > localVersionCode

    internal fun endpointUrls(channel: UpdateChannel): List<String> =
        UpdateMirrorSources.metadataUrls("${channel.fileName}.json")

    private suspend fun fetchInfo(url: String): UpdateInfo? =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                if (!continuation.isActive) return@use
                                if (!it.isSuccessful) {
                                    if (continuation.isActive) continuation.resume(null)
                                    return@use
                                }

                                val body = it.body.string()
                                val info = gson.fromJson(body, UpdateInfo::class.java)
                                if (continuation.isActive) continuation.resume(info)
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                },
            )
        }

}

private const val DEFAULT_UPDATE_TIMEOUT_MS = 3000L

private fun defaultUpdateClient(timeoutMs: Long): OkHttpClient =
    HttpClientFactory.create(
        includeWebVpn = false,
    ).newBuilder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()
