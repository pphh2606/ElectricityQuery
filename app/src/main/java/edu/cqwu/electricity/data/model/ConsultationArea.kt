package edu.cqwu.electricity.data.model

import com.google.gson.annotations.SerializedName

/**
 * 咨询区（科室）数据模型。
 *
 * 对应 ehall `getZxq.do` 接口返回的单条记录。
 */
data class ConsultationArea(
    @SerializedName("WID")
    val wid: String = "",

    /** 咨询区代码（用于构造发布留言 WebView URL） */
    @SerializedName("ZXQDM")
    val zxqdm: String = "",

    /** 咨询区名称（如"党政办公室"、"基建后勤处"） */
    @SerializedName("ZXQMC")
    val zxqmc: String = "",

    /** 是否使用 */
    @SerializedName("SFSY")
    val sfsy: String = "",

    /** 排序 */
    @SerializedName("PX")
    val px: String = ""
)

/**
 * `getZxq.do` 接口响应包装。
 */
data class ConsultationAreaResponse(
    @SerializedName("code")
    val code: String = "",
    @SerializedName("msg")
    val msg: String = "",
    @SerializedName("data")
    val data: List<ConsultationArea> = emptyList()
)
