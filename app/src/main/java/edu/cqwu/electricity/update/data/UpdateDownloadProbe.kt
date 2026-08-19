package edu.cqwu.electricity.update.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateDownloadProbeResult(
    val ok: Boolean,
    val latencyMs: Long?,
)

object UpdateDownloadProbe {
    private const val PROBE_READ_BYTES = 16 * 1024
    private const val TIMEOUT_MS = 2500L

    private val client: OkHttpClient by lazy { updateHttpClient(TIMEOUT_MS) }

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
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${PROBE_READ_BYTES - 1}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    UpdateDownloadProbeResult(
                        ok = false,
                        latencyMs = null,
                    )
                } else {
                    val read = response.body.byteStream().use { input ->
                        input.read(ByteArray(PROBE_READ_BYTES))
                    }
                    UpdateDownloadProbeResult(
                        ok = read > 0,
                        latencyMs = (System.nanoTime() - startNs) / 1_000_000,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UpdateDownloadProbeResult(
                ok = false,
                latencyMs = null,
            )
        }
    }
}
