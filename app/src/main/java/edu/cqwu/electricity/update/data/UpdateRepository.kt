package edu.cqwu.electricity.update.data

import com.google.gson.Gson
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Checks for app updates from the public assets repository.
 *
 * Metadata is fetched through jsDelivr CDNs first because GitHub endpoints are
 * unreliable for users on mainland China. The campus WebVPN interceptor is
 * intentionally excluded so update requests never go through the school proxy.
 */
class UpdateRepository(
    private val client: OkHttpClient = defaultUpdateClient(),
    private val gson: Gson = Gson(),
) {

    suspend fun check(channel: UpdateChannel): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in endpointUrls(channel)) {
            val info = try {
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
            } catch (_: Exception) {
                null
            }
            if (info?.app?.link?.isNotBlank() == true) {
                return@withContext info
            }
        }
        null
    }

    fun needsUpdate(remote: UpdateInfo, localVersionCode: Int = BuildConfig.VERSION_CODE): Boolean =
        remote.app.versionCode > localVersionCode

    internal fun endpointUrls(channel: UpdateChannel): List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/$ASSETS_OWNER/$ASSETS_REPO@$ASSETS_BRANCH/${channel.fileName}.json",
        "https://fastly.jsdelivr.net/gh/$ASSETS_OWNER/$ASSETS_REPO@$ASSETS_BRANCH/${channel.fileName}.json",
        "https://gcore.jsdelivr.net/gh/$ASSETS_OWNER/$ASSETS_REPO@$ASSETS_BRANCH/${channel.fileName}.json",
        "https://raw.githubusercontent.com/$ASSETS_OWNER/$ASSETS_REPO/$ASSETS_BRANCH/${channel.fileName}.json",
    )

    private companion object {
        const val ASSETS_OWNER = "pphh2606"
        const val ASSETS_REPO = "ElectricityQuery-assets"
        const val ASSETS_BRANCH = "main"
    }
}

private fun defaultUpdateClient(): OkHttpClient =
    HttpClientFactory.create(
        includeWebVpn = false,
        connectTimeout = 8,
        readTimeout = 8,
        writeTimeout = 8,
    )
