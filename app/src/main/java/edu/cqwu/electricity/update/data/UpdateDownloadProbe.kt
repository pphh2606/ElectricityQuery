package edu.cqwu.electricity.update.data

import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateDownloadProbeResult(
    val ok: Boolean,
    val latencyMs: Long?,
    val speedBytesPerSec: Long?,
    val error: String?,
)

object UpdateDownloadProbe {
    private const val SAMPLE_BYTES = 256 * 1024L
    private const val TIMEOUT_MS = 2500L

    private val client: OkHttpClient by lazy {
        HttpClientFactory.create(includeWebVpn = false).newBuilder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun probe(links: List<UpdateDownloadLink>): Map<String, UpdateDownloadProbeResult> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                links.map { link ->
                    async { link.url to probeOne(link.url) }
                }.awaitAll().toMap()
            }
        }

    private fun probeOne(url: String): UpdateDownloadProbeResult {
        val startNs = System.nanoTime()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${SAMPLE_BYTES - 1}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return UpdateDownloadProbeResult(
                        ok = false,
                        latencyMs = null,
                        speedBytesPerSec = null,
                        error = "HTTP ${response.code}",
                    )
                }

                var sampled = 0L
                val buffer = ByteArray(16 * 1024)
                response.body.byteStream().use { input ->
                    while (sampled < SAMPLE_BYTES) {
                        val toRead = minOf(buffer.size.toLong(), SAMPLE_BYTES - sampled).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) break
                        sampled += read
                    }
                }

                val costMs = (System.nanoTime() - startNs) / 1_000_000
                val safeMs = if (costMs <= 0L) 1L else costMs
                val speed = if (sampled > 0L) (sampled * 1000L) / safeMs else null
                return UpdateDownloadProbeResult(
                    ok = sampled > 0L,
                    latencyMs = costMs,
                    speedBytesPerSec = speed,
                    error = if (sampled > 0L) null else "EMPTY_BODY",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return UpdateDownloadProbeResult(
                ok = false,
                latencyMs = null,
                speedBytesPerSec = null,
                error = e.javaClass.simpleName,
            )
        }
    }
}
