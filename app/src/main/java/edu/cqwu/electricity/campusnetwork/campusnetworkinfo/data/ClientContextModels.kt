package edu.cqwu.electricity.campusnetwork.campusnetworkinfo.data

import com.google.gson.annotations.SerializedName

/**
 * 接入者信息响应模型（GET /api/speedlyst/client-context 的 data 部分）。
 * 统一响应包装 `{code,message,data}` 已收敛到 common（CampusNetworkJson），此处仅保留 data 业务形状。
 * 字段清单对照 `fortest/校园网测速API文档.md` §1 整理，全部可空，
 * 避免后端某字段缺失/为 null 时 Gson 解析崩溃。未知新字段会被忽略，不影响展示。
 */
data class ClientContextData(
    /** 请求方 IP */
    @SerializedName("ip") val ip: String? = null,
    /** 识别来源：sam（校园网 SAM 命中）/ ip2region（公网归属地）等 */
    @SerializedName("source") val source: String? = null,
    /** 展示串，如 "10.140.77.234 - 校园网 / 红河宿舍无线 / 重庆电信" */
    @SerializedName("processedString") val processedString: String? = null,
    @SerializedName("rawIspInfo") val rawIspInfo: RawIspInfo? = null,
    /** 命中方式：onlineIpv4（SAM 在线命中）/ archiveIp（SAM 档案命中）等 */
    @SerializedName("matchedBy") val matchedBy: String? = null,
    /** 用户档案（仅校园网 SAM 命中时返回） */
    @SerializedName("row") val row: SamUserRow? = null,
    /** 公网归属地（未命中校园网时返回） */
    @SerializedName("region") val region: PublicRegion? = null,
    /** SAM 查询异常信息，正常为 null */
    @SerializedName("samError") val samError: String? = null,
)

/** rawIspInfo 原始运营商/接入点信息 */
data class RawIspInfo(
    @SerializedName("source") val source: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("isp") val isp: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("organization") val organization: String? = null,
    @SerializedName("label") val label: String? = null,
)

/**
 * 用户档案（SAM）。
 * 注意：接口按"源 IP 即身份"反查，含个人隐私字段，仅用于界面展示，
 * 不得写入日志或做本地持久化。
 */
data class SamUserRow(
    /** 用户类型：student / teacher 等 */
    @SerializedName("userType") val userType: String? = null,
    /** 学工号 */
    @SerializedName("userNo") val userNo: String? = null,
    @SerializedName("name") val name: String? = null,
    /** 性别：1=男，2=女（以实际返回为准） */
    @SerializedName("sex") val sex: Int? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("deptId") val deptId: String? = null,
    @SerializedName("deptName") val deptName: String? = null,
    @SerializedName("sourceId") val sourceId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("majorCode") val majorCode: String? = null,
    @SerializedName("majorName") val majorName: String? = null,
    @SerializedName("grade") val grade: String? = null,
    @SerializedName("className") val className: String? = null,
    /** SAM 账号 */
    @SerializedName("archiveUserId") val archiveUserId: String? = null,
    @SerializedName("archiveUserName") val archiveUserName: String? = null,
    /** 用户组：本科生 / 教职工 等 */
    @SerializedName("archiveUserGroupName") val archiveUserGroupName: String? = null,
    @SerializedName("archiveUserTemplateName") val archiveUserTemplateName: String? = null,
    @SerializedName("archiveUserPackageName") val archiveUserPackageName: String? = null,
    /** 计费策略，如"融合套餐" */
    @SerializedName("archivePolicyId") val archivePolicyId: String? = null,
    @SerializedName("archiveStateFlag") val archiveStateFlag: Int? = null,
    /** 档案在线状态位 */
    @SerializedName("archiveOnlineState") val archiveOnlineState: Int? = null,
    @SerializedName("archiveCreatedAt") val archiveCreatedAt: String? = null,
    @SerializedName("archiveLastLogoutAt") val archiveLastLogoutAt: String? = null,
    @SerializedName("archiveNextBillingAt") val archiveNextBillingAt: String? = null,
    @SerializedName("archiveFreeAuth") val archiveFreeAuth: Int? = null,
    @SerializedName("archiveIp") val archiveIp: String? = null,
    @SerializedName("archiveSelfServicePermission") val archiveSelfServicePermission: String? = null,
    @SerializedName("onlineMac") val onlineMac: String? = null,
    @SerializedName("onlineIpv4") val onlineIpv4: String? = null,
    @SerializedName("onlineNasIp") val onlineNasIp: String? = null,
    @SerializedName("onlineNasPort") val onlineNasPort: Int? = null,
    @SerializedName("onlineConnectedAt") val onlineConnectedAt: String? = null,
    @SerializedName("onlineAccessType") val onlineAccessType: Int? = null,
    @SerializedName("onlineGroupId") val onlineGroupId: String? = null,
    @SerializedName("onlineTemplateId") val onlineTemplateId: String? = null,
    @SerializedName("onlinePackageName") val onlinePackageName: String? = null,
    @SerializedName("onlinePolicyId") val onlinePolicyId: String? = null,
    @SerializedName("onlineServiceId") val onlineServiceId: String? = null,
    @SerializedName("onlineAreaName") val onlineAreaName: String? = null,
)

/** 公网归属地（region 分支） */
data class PublicRegion(
    @SerializedName("region") val region: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("province") val province: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("isp") val isp: String? = null,
    @SerializedName("countryCode") val countryCode: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("isPublic") val isPublic: Boolean? = null,
)
