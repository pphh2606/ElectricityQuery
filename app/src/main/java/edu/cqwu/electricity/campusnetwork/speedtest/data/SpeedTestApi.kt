package edu.cqwu.electricity.campusnetwork.speedtest.data
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkErrorKind
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkException
import edu.cqwu.electricity.campusnetwork.common.toCampusNetworkException

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestSettings
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 测速会话与探测流量接口封装。
 *
 * - 会话链（POST /session → GET → claim → complete → DELETE）返回 JSON；
 * - probe 三类探测请求由 [edu.cqwu.electricity.campusnetwork.engine.SpeedTestEngine] 使用本类构造的 Call 执行。
 *
 * 与接入者信息接口一致：无 Cookie、IP 即身份；独立无 Cookie / 无 WebVPN 客户端。
 * 错误统一归类为 [CampusNetworkException]（复用接入者信息的错误分类），
 * 原始异常经 AppLog 全量记录，不静默吞掉。
 */
class SpeedTestApi {

    companion object {
        private const val BASE_URL = "https://speedtest.cqwu.edu.cn/api/speedlyst"
        private const val TAG = "SpeedTestApi"
        private const val JSON_TYPE = "application/json; charset=utf-8"
    }

    private val gson = Gson()

    private val client by lazy {
        HttpClientFactory.create(
            cookieJar = null,
            includeWebVpn = false,
        )
    }

    private fun sessionPath() = "$BASE_URL/session"
    private fun sessionPath(id: String) = "$BASE_URL/session/$id"
    private fun probePath(kind: String) = "$BASE_URL/probe/$kind"

    // ══════════════════════════════════════════════
    //  会话链 JSON 接口
    // ══════════════════════════════════════════════

    /** 创建测速会话（无请求体） */
    suspend fun createSession(): Result<SpeedTestSessionData> =
        sessionJson("POST", sessionPath())

    /** 查询会话 */
    suspend fun querySession(sessionId: String): Result<SpeedTestSessionData> =
        sessionJson("GET", sessionPath(sessionId))

    /** 抢占会话 */
    suspend fun claimSession(sessionId: String): Result<SpeedTestSessionData> =
        sessionJson("POST", sessionPath(sessionId) + "/claim")

    /** 全局会话状态（空闲/排队位次/活跃数） */
    suspend fun sessionStatus(): Result<SpeedTestSessionData> =
        sessionJson("GET", sessionPath() + "/status")

    /**
     * 上报测速结果并释放会话。实测报文四个数值均为**字符串**、保留 2 位小数。
     */
    suspend fun completeSession(
        sessionId: String,
        download: String,
        upload: String,
        ping: String,
        jitter: String,
        log: String = "",
    ): Result<SpeedTestCompleteData> = withContext(Dispatchers.IO) {
        try {
            val bodyText = buildString {
                append("{\"download\":\"").append(download)
                append("\",\"upload\":\"").append(upload)
                append("\",\"ping\":\"").append(ping)
                append("\",\"jitter\":\"").append(jitter)
                append("\",\"log\":\"").append(log.replace("\"", "\\\""))
                append("\"}")
            }
            val request = Request.Builder()
                .url(sessionPath(sessionId) + "/complete")
                .post(bodyText.toRequestBody(JSON_TYPE.toMediaType()))
                .build()
            val (success, code, text) = executeAndRead(request)
            if (!success) {
                AppLog.e(TAG, "complete HTTP 失败: $code")
                return@withContext Result.failure(
                    CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = "HTTP $code")
                )
            }
            val parsed = gson.fromJson(text, SpeedTestCompleteResponse::class.java)
                ?: throw IllegalStateException("complete 响应为空")
            if (parsed.code != 0) {
                AppLog.e(TAG, "complete 业务失败: code=${parsed.code}, message=${parsed.message}")
                return@withContext Result.failure(
                    CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = parsed.message)
                )
            }
            val data = parsed.data ?: throw IllegalStateException("complete data 为空")
            Result.success(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "complete 请求失败: ${e.message}", e)
            Result.failure(e.toCampusNetworkException())
        }
    }

    /** 取消/释放会话（停止测速、离开页面时兜底调用） */
    suspend fun releaseSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(sessionPath(sessionId)).delete().build()
            val (success, code) = executeAndRead(request)
            if (!success) {
                AppLog.e(TAG, "释放会话 HTTP 失败: $code")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "释放会话失败: ${e.message}", e)
            Result.success(Unit) // 释放失败不阻断流程，仅记录
        }
    }

    private suspend fun sessionJson(method: String, path: String): Result<SpeedTestSessionData> =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(path)
                when (method) {
                    "POST" -> builder.post("".toRequestBody(JSON_TYPE.toMediaType()))
                    "DELETE" -> builder.delete()
                    else -> builder.get()
                }
                val (success, code, text) = executeAndRead(builder.build())
                if (!success) {
                    AppLog.e(TAG, "$method $path HTTP 失败: $code")
                    return@withContext Result.failure(
                        CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = "HTTP $code")
                    )
                }
                val parsed = gson.fromJson(text, SpeedTestResponse::class.java)
                    ?: throw IllegalStateException("响应为空")
                if (parsed.code != 0) {
                    AppLog.e(TAG, "$method $path 业务失败: code=${parsed.code}, message=${parsed.message}")
                    return@withContext Result.failure(
                        CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = parsed.message)
                    )
                }
                val data = parsed.data ?: throw IllegalStateException("data 为空")
                Result.success(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "$method $path 请求失败: ${e.message}", e)
                Result.failure(e.toCampusNetworkException())
            }
        }

    // ══════════════════════════════════════════════
    //  probe 探测请求构造（执行与取消由 SpeedTestEngine 管理）
    // ══════════════════════════════════════════════

    /** 下载探测：GET garbage?r=<随机>&ckSize=<chunk> */
    fun newDownloadCall(r: String): Call {
        val url = probePath("garbage") +
            "?r=$r&ckSize=${SpeedTestSettings.GARBAGE_CK_SIZE}"
        return client.newCall(Request.Builder().url(url).get().build())
    }

    /** 延迟探测：GET empty?r=<随机> */
    fun newPingCall(r: String): Call {
        val url = probePath("empty") + "?r=$r"
        return client.newCall(Request.Builder().url(url).get().build())
    }

    /** 上传探测：POST empty?r=<随机>，body 为随机载荷，写出过程逐块回调计数 */
    fun newUploadCall(r: String, payload: ByteArray, onWritten: (Int) -> Unit): Call {
        val url = probePath("empty") + "?r=$r"
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = payload.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                var offset = 0
                while (offset < payload.size) {
                    val n = minOf(64 * 1024, payload.size - offset)
                    sink.write(payload, offset, n)
                    offset += n
                    onWritten(n)
                }
            }
        }
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Encoding", "identity") // 与官网请求一致
            .build()
        return client.newCall(request)
    }

    // ══════════════════════════════════════════════
    //  内部工具
    // ══════════════════════════════════════════════

    /** 同步执行请求并读取/关闭响应体，返回 (是否成功, HTTP code, body 文本) */
    private fun executeAndRead(request: Request): Triple<Boolean, Int, String> {
        val response: Response = client.newCall(request).execute()
        return try {
            Triple(response.isSuccessful, response.code, response.body.string())
        } finally {
            response.close()
        }
    }
}