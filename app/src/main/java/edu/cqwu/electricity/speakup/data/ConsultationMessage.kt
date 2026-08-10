package edu.cqwu.electricity.speakup.data

import com.google.gson.annotations.SerializedName

/**
 * 留言/回复中的图片项。
 *
 * 对应 `zxImageList` / `hfImagesList` 中的单个图片对象。
 */
data class ImageItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("ts") val ts: String = "",
    @SerializedName("size") val size: String = "",
    @SerializedName("isImage") val isImage: Boolean = true,
    /** 缩略图 URL（相对路径） */
    @SerializedName("smallSizeImageUrl") val smallSizeImageUrl: String = "",
    /** 中等尺寸 URL（相对路径） */
    @SerializedName("middleSizeImageUrl") val middleSizeImageUrl: String = "",
    /** 原图 URL（相对路径） */
    @SerializedName("fileUrl") val fileUrl: String = "",
    @SerializedName("deleteUrl") val deleteUrl: String = ""
) {
    companion object {
        private const val BASE_URL = "https://ehall.cqwu.edu.cn"
    }

    /** 中等尺寸完整 URL */
    val middleUrlFull: String get() = if (middleSizeImageUrl.isNotBlank()) "$BASE_URL$middleSizeImageUrl" else ""

    /** 原图完整 URL */
    val fileUrlFull: String get() = if (fileUrl.isNotBlank()) "$BASE_URL$fileUrl" else ""
}

/**
 * 留言（咨询信息）数据模型。
 *
 * 对应 ehall `getZxxx.do` 接口返回的单条记录。
 * 用于「有话要说」功能的留言浏览和详情展示。
 */
data class ConsultationMessage(
    @SerializedName("WID") val wid: String = "",
    @SerializedName("ZXBT") val zxbt: String = "",               // 标题
    @SerializedName("ZXNR") val zxnr: String = "",               // 内容
    @SerializedName("ZXSJ") val zxsj: String = "",               // 咨询时间
    @SerializedName("ZXR") val zxr: String = "",                 // 咨询人学号
    @SerializedName("ZXRXM") val zxrxm: String = "",             // 咨询人姓名
    @SerializedName("ZXRXH") val zxrxh: String = "",             // 咨询人学号
    @SerializedName("ZXRLX") val zxrlx: String = "",             // 咨询人类型
    @SerializedName("ZXRLXFS") val zxrlxfs: String = "",         // 咨询人联系方式
    @SerializedName("ZXRDWDM") val zxrdwdm: String = "",         // 咨询人单位代码
    @SerializedName("ZXRDWDM_DISPLAY") val zxrdwdmDisplay: String = "", // 咨询人单位名称
    @SerializedName("ZXQDM") val zxqdm: String = "",             // 咨询区代码
    @SerializedName("ZXQDM_DISPLAY") val zxqdmDisplay: String = "",     // 咨询区名称
    @SerializedName("ZXLXDM") val zxlxdm: String = "",           // 咨询类型代码
    @SerializedName("ZXLXDM_DISPLAY") val zxlxdmDisplay: String = "",   // 咨询类型名称
    @SerializedName("SFHF") val sfhf: String = "",               // 是否已回复 ("1"=是)
    @SerializedName("SFHF_DISPLAY") val sfhfDisplay: String = "",       // 回复状态显示
    @SerializedName("HFNR") val hfnr: String = "",               // 回复内容
    @SerializedName("HFR") val hfr: String = "",                 // 回复人工号
    @SerializedName("HFRXM") val hfrxm: String = "",             // 回复人姓名
    @SerializedName("HFSJ") val hfsj: String = "",               // 回复时间
    @SerializedName("HFSJTJ") val hfsjtj: String = "",           // 回复时间差(分钟)
    @SerializedName("SFCB") val sfcb: String = "",               // 是否催办
    @SerializedName("SFCJWT") val sfcjwt: String = "",           // 是否解决问题
    @SerializedName("SFZY") val sfzy: String = "",               // 是否置顶
    @SerializedName("HDTX") val hdtx: String = "",               // 回答提醒
    @SerializedName("isAnswer") val isAnswer: Boolean = false,
    @SerializedName("isNotJudge") val isNotJudge: Boolean = false,
    @SerializedName("singleImg") val singleImg: Boolean = false,
    @SerializedName("zxImageList") val zxImageList: List<ImageItem> = emptyList(),
    @SerializedName("hfImagesList") val hfImagesList: List<ImageItem> = emptyList(),
    // 转办相关字段
    @SerializedName("YZXQDM") val yzxqdm: String = "",           // 原咨询区代码
    @SerializedName("YZXQDM_DISPLAY") val yzxqdmDisplay: String = "",   // 原咨询区名称
    @SerializedName("ZYR") val zyr: String = "",                 // 转办人
    @SerializedName("ZYSJ") val zysj: String = "",               // 转办时间
    @SerializedName("ZYLY") val zyly: String = "",               // 转办理由
    // 评价相关字段
    @SerializedName("score") val score: Int? = null,
    @SerializedName("scores") val scores: List<Int>? = null,
    @SerializedName("scoresReduce") val scoresReduce: List<Any>? = null,
    @SerializedName("HFPF") val hfpf: Int? = null,
    @SerializedName("SFJYJWT") val sfjyjwt: String = "",
    @SerializedName("SFJYJWT_DISPLAY") val sfjyjwtDisplay: String = ""
)

/**
 * `getZxxx.do` 接口响应包装（留言列表/详情共用）。
 */
data class MessageListResponse(
    @SerializedName("code") val code: String = "",
    @SerializedName("msg") val msg: String = "",
    @SerializedName("data") val data: List<ConsultationMessage> = emptyList()
)
