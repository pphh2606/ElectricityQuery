package edu.cqwu.electricity.update.data

import com.google.gson.Gson
import edu.cqwu.electricity.BuildConfig
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

/**
 * Checks for app updates from the public assets repository.
 *
 * Metadata is fetched from all mirrors concurrently so a slow or failed
 * mirror does not delay the whole check. The campus WebVPN interceptor is
 * intentionally excluded so update requests never go through the school proxy.
 */
class UpdateRepository(
    timeoutMs: Long = DEFAULT_UPDATE_TIMEOUT_MS,
    private val client: OkHttpClient = defaultUpdateClient(timeoutMs),
    private val gson: Gson = Gson(),
) {

    suspend fun check(channel: UpdateChannel): UpdateInfo? = withContext(Dispatchers.IO) {
        coroutineScope {
            endpointUrls(channel).map { url ->
                async { fetchInfo(url) }
            }.awaitAll()
        }.let(::selectLatest)
    }

    fun needsUpdate(remote: UpdateInfo, localVersionCode: Int = BuildConfig.VERSION_CODE): Boolean =
        remote.app.versionCode > localVersionCode

    internal fun endpointUrls(channel: UpdateChannel): List<String> = listOf(
        "https://raw.githubusercontent.com/$ASSETS_OWNER/$ASSETS_REPO/$ASSETS_BRANCH/${channel.fileName}.json",
        "https://cdn.jsdelivr.net/gh/$ASSETS_OWNER/$ASSETS_REPO@$ASSETS_BRANCH/${channel.fileName}.json",
        "https://gh-proxy.org/https://github.com/$ASSETS_OWNER/$ASSETS_REPO/blob/$ASSETS_BRANCH/${channel.fileName}.json",
        "https://fastgit.cc/https://github.com/$ASSETS_OWNER/$ASSETS_REPO/blob/$ASSETS_BRANCH/${channel.fileName}.json",
    )

    internal fun selectLatest(candidates: List<UpdateInfo?>): UpdateInfo? {
        var latest: UpdateInfo? = null
        for (candidate in candidates) {
            val info = candidate ?: continue
            if (info.app.link.isNullOrBlank()) continue
            if (latest == null || info.app.versionCode > latest.app.versionCode) {
                latest = info
            }
        }
        return latest
    }

    private suspend fun fetchInfo(url: String): UpdateInfo? = try {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body.string().let { body ->
                    gson.fromJson(body, UpdateInfo::class.java)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ASSETS_OWNER = "pphh2606"
        const val ASSETS_REPO = "ElectricityQuery-assets"
        const val ASSETS_BRANCH = "main"
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
