package edu.cqwu.electricity.campusnetwork.speedtest.data

import com.google.gson.annotations.SerializedName

/**
 * 测速会话/历史 DTO —— 字段对照 `fortest/校园网测速API文档.md` §3-5 实测报文。
 * 统一响应包装 `{code,message,data}` 已收敛到 common（[edu.cqwu.electricity.campusnetwork.common.CampusNetworkJson]），
 * 这里只保留各接口的 data 业务形状；字段全部可空防解析崩溃。
 */

/** POST /session、GET /session/{id}、POST claim、GET session/status 共用 */
data class SpeedTestSessionData(
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("position") val position: Int? = null,
    @SerializedName("active") val active: Int? = null,
    @SerializedName("queue") val queue: Int? = null,
    // claim 会附带 context（与 client-context 同构的身份档案），本功能不展示，忽略解析
)

/** POST complete 请求体（实测四个数值均以字符串传、保留 2 位小数） */
data class SpeedTestCompleteBody(
    @SerializedName("download") val download: String,
    @SerializedName("upload") val upload: String,
    @SerializedName("ping") val ping: String,
    @SerializedName("jitter") val jitter: String,
    @SerializedName("log") val log: String = "",
)

/** POST complete 响应 data */
data class SpeedTestCompleteData(
    @SerializedName("resultId") val resultId: String? = null,
    @SerializedName("released") val released: Boolean? = null,
    @SerializedName("active") val active: Int? = null,
    @SerializedName("queue") val queue: Int? = null,
)

/** GET /rank/stats 响应 data */
data class SpeedTestRankData(
    @SerializedName("records") val records: List<SpeedTestRecord>? = null,
)

/** 单条最近测速记录（数值均为字符串、2 位小数） */
data class SpeedTestRecord(
    @SerializedName("uuid") val uuid: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("download") val download: String? = null,
    @SerializedName("upload") val upload: String? = null,
    @SerializedName("ping") val ping: String? = null,
    @SerializedName("jitter") val jitter: String? = null,
    @SerializedName("ipAddress") val ipAddress: String? = null,
    @SerializedName("isp") val isp: String? = null,
)
