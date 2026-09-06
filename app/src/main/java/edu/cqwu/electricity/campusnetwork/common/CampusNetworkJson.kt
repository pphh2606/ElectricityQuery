package edu.cqwu.electricity.campusnetwork.common

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.common.net.CookieStoreOkHttpJar
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 校园网测速站（speedtest.cqwu.edu.cn/api/speedlyst）的客户端与统一 JSON 传输。
 *
 * 站点特点：无 Cookie / 无鉴权、"源 IP 即身份"，仅在校园网内且完成上网认证时可达。
 *
 * 客户端跟随全局 WebVPN 开关（[edu.cqwu.electricity.common.net.WebVpnSettings]，
 * 实验性功能）：开关关闭时拦截器直接放行，与直连一致；开启时全部请求（含 probe）
 * 经 WebVPN 转换与自动登录。错误直接上抛暴露，不做额外保护。
 *
 * 错误语义（对 speedtest 与 campusnetworkinfo 两个功能一致）：
 * - HTTP 非 2xx / 业务 code!=0 → [CampusNetworkException](SERVER)，message 原样展示；
 * - 连接超时 / 拒绝 → CAMPUS_OFFLINE，域名无法解析 → NO_NETWORK，解析失败 → PARSE（均经
 *   [toCampusNetworkException] 归类）；`CancellationException` 原样上抛，绝不静默吞掉；
 * - 每次请求成功/失败均经 AppLog 记录 method+path 与错误，方便排查。
 *
 * data 统一为 `{code, message, data}` 信封；`data` 目标类型均为具体业务类（非顶层泛型），
 * 因此按 Class 反序列化即可，无需 TypeToken。
 */
internal object CampusNetworkClients {

    /** 测速站 API 根地址（speedtest 与 campusnetworkinfo 共用，杜绝两处写死漂移） */
    const val BASE_URL = "https://speedtest.cqwu.edu.cn/api/speedlyst"

    /** 校园网络统一客户端：与其它 WebVPN 客户端一致挂 CookieStoreOkHttpJar，跟随全局实验性开关 */
    val direct: OkHttpClient by lazy {
        HttpClientFactory.create(
            cookieJar = CookieStoreOkHttpJar,
            includeWebVpn = true,
        )
    }
}

/** 统一 JSON 传输（GET/POST 返回 `Result<T>`；DELETE 为"释放"语义，不解析响应体）。 */
internal class CampusNetworkJson(
    private val client: OkHttpClient = CampusNetworkClients.direct,
) {
    private val gson = Gson()

    private data class Envelope(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("message") val message: String? = null,
        /** 先以 JsonElement 兜住，校验 code 后再按目标类型反序列化 */
        @SerializedName("data") val data: JsonElement? = null,
    )

    /** GET：解析 data 为目标类型 */
    suspend fun <T> get(tag: String, path: String, dataType: Class<T>): Result<T> =
        requestJson(tag, "GET", path, body = null, dataType = dataType)

    /** POST：body 为 null 时发送空 JSON 体（与官网创建会话一致） */
    suspend fun <T> post(tag: String, path: String, body: Any?, dataType: Class<T>): Result<T> =
        requestJson(tag, "POST", path, body, dataType)

    /**
     * DELETE：不解析响应体，成功即 Unit。
     * 语义为"兜底释放会话"：失败仅记录日志、仍返回成功，不阻断调用方清理流程。
     */
    suspend fun delete(tag: String, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CampusNetworkClients.BASE_URL + path)
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.e(tag, "DELETE $path HTTP 失败: ${response.code}")
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(tag, "DELETE $path 请求失败: ${e.message}", e)
            Result.success(Unit) // 释放失败不阻断调用方流程，仅记录
        }
    }

    private suspend fun <T> requestJson(
        tag: String,
        method: String,
        path: String,
        body: Any?,
        dataType: Class<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CampusNetworkClients.BASE_URL + path)
                .apply {
                    if (method == "POST") {
                        val payload = if (body != null) {
                            gson.toJson(body)
                        } else {
                            ""
                        }
                        post(payload.toRequestBody(JSON_TYPE.toMediaType()))
                    }
                }
                .build()
            val response = client.newCall(request).execute()
            val text = response.use { it.body.string() }

            if (!response.isSuccessful) {
                AppLog.e(tag, "$method $path HTTP 失败: ${response.code}")
                return@withContext Result.failure(
                    CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = "HTTP ${response.code}")
                )
            }

            val envelope = gson.fromJson(text, Envelope::class.java)
                ?: throw IllegalStateException("$method $path 响应为空")
            if (envelope.code != 0) {
                AppLog.e(tag, "$method $path 业务失败: code=${envelope.code}, message=${envelope.message}")
                return@withContext Result.failure(
                    CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = envelope.message)
                )
            }
            val data = envelope.data
                ?: throw IllegalStateException("$method $path data 为空")
            val parsed = gson.fromJson(data, dataType)
                ?: throw IllegalStateException("$method $path data 反序列化失败")
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(tag, "$method $path 请求失败: ${e.message}", e)
            Result.failure(e.toCampusNetworkException())
        }
    }

    private companion object {
        const val JSON_TYPE = "application/json; charset=utf-8"
    }
}
