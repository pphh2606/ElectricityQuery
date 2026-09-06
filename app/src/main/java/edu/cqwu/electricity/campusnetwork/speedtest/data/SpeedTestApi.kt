package edu.cqwu.electricity.campusnetwork.speedtest.data

import edu.cqwu.electricity.campusnetwork.common.CampusNetworkClients
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkJson
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestSettings
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * 测速会话与探测流量接口封装。
 *
 * - 会话链 JSON（POST /session → GET → claim → complete → DELETE）与 rank/stats 全部
 *   委托 common 的 [CampusNetworkJson]（统一信封解析 / 错误归类 / 日志），本类只声明路径与类型；
 * - probe 三类探测请求（流式大流量，非 JSON）由 [edu.cqwu.electricity.campusnetwork.engine.SpeedTestEngine]
 *   使用本类构造的 Call 执行，客户端复用 [CampusNetworkClients.direct]（无 Cookie / 无 WebVPN）。
 */
class SpeedTestApi internal constructor(
    private val json: CampusNetworkJson = CampusNetworkJson(),
) {

    private companion object {
        const val TAG = "SpeedTestApi"
    }

    // ══════════════════════════════════════════════
    //  会话链 JSON 接口
    // ══════════════════════════════════════════════

    /** 创建测速会话（无请求体） */
    suspend fun createSession(): Result<SpeedTestSessionData> =
        json.post(TAG, "/session", body = null, dataType = SpeedTestSessionData::class.java)

    /** 查询会话 */
    suspend fun querySession(sessionId: String): Result<SpeedTestSessionData> =
        json.get(TAG, "/session/$sessionId", SpeedTestSessionData::class.java)

    /** 抢占会话 */
    suspend fun claimSession(sessionId: String): Result<SpeedTestSessionData> =
        json.post(TAG, "/session/$sessionId/claim", body = null, dataType = SpeedTestSessionData::class.java)

    /** 全局会话状态（空闲/排队位次/活跃数） */
    suspend fun sessionStatus(): Result<SpeedTestSessionData> =
        json.get(TAG, "/session/status", SpeedTestSessionData::class.java)

    /** 上报测速结果并释放会话（四个数值以字符串传、保留 2 位小数） */
    suspend fun completeSession(
        sessionId: String,
        download: String,
        upload: String,
        ping: String,
        jitter: String,
        log: String = "",
    ): Result<SpeedTestCompleteData> =
        json.post(
            TAG,
            "/session/$sessionId/complete",
            body = SpeedTestCompleteBody(download, upload, ping, jitter, log),
            dataType = SpeedTestCompleteData::class.java,
        )

    /** 取消/释放会话（停止测速、离开页面时兜底调用；失败仅记录不阻断） */
    suspend fun releaseSession(sessionId: String): Result<Unit> =
        json.delete(TAG, "/session/$sessionId")

    /** 拉取最近测速记录（rank/stats），默认 limit=50 */
    suspend fun fetchRankStats(limit: Int = 50): Result<List<SpeedTestRecord>> =
        json.get(TAG, "/rank/stats?limit=$limit", SpeedTestRankData::class.java)
            .map { it.records.orEmpty() }

    // ══════════════════════════════════════════════
    //  probe 探测请求构造（执行与取消由 SpeedTestEngine 管理）
    // ══════════════════════════════════════════════

    private fun probeUrl(kind: String) = "${CampusNetworkClients.BASE_URL}/probe/$kind"

    /** 下载探测：GET garbage?r=<随机>&ckSize=<chunk> */
    fun newDownloadCall(r: String): Call {
        val url = probeUrl("garbage") + "?r=$r&ckSize=${SpeedTestSettings.GARBAGE_CK_SIZE}"
        return CampusNetworkClients.direct.newCall(Request.Builder().url(url).get().build())
    }

    /** 延迟探测：GET empty?r=<随机> */
    fun newPingCall(r: String): Call {
        val url = probeUrl("empty") + "?r=$r"
        return CampusNetworkClients.direct.newCall(Request.Builder().url(url).get().build())
    }

    /** 上传探测：POST empty?r=<随机>，body 为随机载荷，写出过程逐块回调计数 */
    fun newUploadCall(r: String, payload: ByteArray, onWritten: (Int) -> Unit): Call {
        val url = probeUrl("empty") + "?r=$r"
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
        return CampusNetworkClients.direct.newCall(request)
    }
}
