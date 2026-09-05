package edu.cqwu.electricity.campusnetwork.speedtest.data

import com.google.gson.annotations.SerializedName

/**
 * 测速会话相关 DTO —— 字段对照 `fortest/校园网测速API文档.md` §4-5 实测报文。
 * 全部可空防解析崩溃。
 */

/** 统一响应包装 */
data class SpeedTestResponse(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: SpeedTestSessionData? = null,
)

/** POST /session、GET /session/{id}、POST claim 共用 */
data class SpeedTestSessionData(
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("position") val position: Int? = null,
    @SerializedName("active") val active: Int? = null,
    @SerializedName("queue") val queue: Int? = null,
    // claim 会附带 context（与 client-context 同构的身份档案），本功能不展示，忽略解析
)

/** POST complete 响应 data */
data class SpeedTestCompleteData(
    @SerializedName("resultId") val resultId: String? = null,
    @SerializedName("released") val released: Boolean? = null,
    @SerializedName("active") val active: Int? = null,
    @SerializedName("queue") val queue: Int? = null,
)

/** complete 响应整体包装（data 结构不同于 SessionData） */
data class SpeedTestCompleteResponse(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: SpeedTestCompleteData? = null,
)
