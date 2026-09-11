package edu.cqwu.electricity.campusnetwork.portal.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * eportal（Dr.COM 认证网关，`222.179.99.144`）响应模型。
 *
 * 与测速站（`speedtest.cqwu.edu.cn`，见 `common/CampusNetworkJson`）**不是同一套协议**：
 * 网关响应没有 `{code,message,data}` 信封，成败由顶层 `result` 决定，且存在第三态 `wait`
 * ——字段不完整但仍带身份的过渡态，**不是失败**。
 *
 * 实测结论（2026-09-11，校园网内直连，见 `fortest/校园网连接-eportal认证流程与API文档.md`）：
 * - **空 `userIndex` + 空 Cookie** 调在线信息接口即返回完整身份并**回填 userIndex**
 *   → 网关按**请求源 IP** 定位会话，进页面无需先做认证态探测；
 * - 篡改 `userIndex` 的 IP 段或 nasip 段一律 `result:"fail"`，且 message 与"真离线"完全相同
 *   → 判在线**只能看 `result`**，不能看 message（「用户可能已经下线」是通用兜底文案）；
 * - 可选服务有两个来源：`serviceList`（HTML 的 `selectService("…")`）与 wait 态的 `mabInfo`（`@` 分隔串）；
 * - `mabInfo` 存在**字段复用**：success 态是已绑定无感认证设备数组，wait 态是服务串。
 */

/** 网关业务响应的公共部分（logout / switchService / registerMac / cancelMac 等） */
data class PortalResult(
    @SerializedName("result") val result: String? = null,
    /** 服务端中文提示，可直接展示（如「您未绑定服务对应的运营商!」） */
    @SerializedName("message") val message: String? = null,
) {
    val isSuccess: Boolean get() = result == SUCCESS
    val isWait: Boolean get() = result == WAIT

    /** 既非成功也非过渡态（含 result 缺失） */
    val isFailure: Boolean get() = !isSuccess && !isWait

    companion object {
        const val SUCCESS = "success"
        const val WAIT = "wait"
        const val FAIL = "fail"
    }
}

/**
 * 已绑定无感认证的设备（`mabInfo` 数组元素）。
 *
 * **刻意不声明 `password` 字段**：实测该数组里带 md5 凭据，解析进来就有落盘/打印风险，
 * Gson 会忽略未声明字段。
 */
data class PortalMabDevice(
    /** 设备 MAC，12 位无分隔 hex（如 `F83DC6EF1260`），可用于逐台解绑 */
    @SerializedName("userMac") val userMac: String? = null,
    @SerializedName("client_hostname") val hostName: String? = null,
    /** 设备类型，如「电脑」 */
    @SerializedName("deviceType") val deviceType: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    /** 无感认证到期时间 */
    @SerializedName("macExpireTime") val expireTime: String? = null,
)

/**
 * 在线会话详情（`InterFace.do?method=getOnlineUserInfo`）。
 *
 * 只声明 App 实际展示的字段——网关实测返回 60+ 字段，其余一律不解析，避免模型膨胀。
 */
data class PortalOnlineInfo(
    @SerializedName("result") val result: String? = null,
    @SerializedName("message") val message: String? = null,
    /** 服务端回填的会话凭证（空 userIndex 查询时也会回填） */
    @SerializedName("userIndex") val userIndex: String? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("userIp") val userIp: String? = null,
    /** 当前设备真实 MAC，12 位无分隔 hex（如 `5ab3f8aab9fe`） */
    @SerializedName("userMac") val userMac: String? = null,
    /** 当前服务名（校内网 / 中国移动 / 中国电信） */
    @SerializedName("service") val service: String? = null,
    @SerializedName("userGroup") val userGroup: String? = null,
    @SerializedName("userPackage") val userPackage: String? = null,
    /** 剩余在线时长，中文格式「0 天 0 小时 19 分钟 50 秒」；wait 态可能为 null */
    @SerializedName("maxLeavingTime") val maxLeavingTime: String? = null,
    /** 流量上限，形如「3653209297306.04MB」（量级极大表示不限）；wait 态可能为 null */
    @SerializedName("maxFlow") val maxFlow: String? = null,
    @SerializedName("webGateIp") val webGateIp: String? = null,
    /** 登录方式，实测为 "3" */
    @SerializedName("loginType") val loginType: String? = null,
    /** 当前设备是否已绑定无感认证 */
    @SerializedName("hasMabInfo") val hasMabInfo: Boolean? = null,
    /** 账号是否允许无感认证 */
    @SerializedName("isAlowMab") val isAlowMab: Boolean? = null,
    /** 无感认证设备绑定上限（实测为 2） */
    @SerializedName("mabInfoMaxCount") val mabInfoMaxCount: Int? = null,
    /** 可选服务的 HTML 片段（`selectService("校内网",…)` × N） */
    @SerializedName("serviceList") val serviceList: String? = null,
    /**
     * 字段复用：success 态为已绑定无感认证设备数组；wait 态为 `校内网@中国移动@中国电信` 服务串。
     * 必须按结构判断（见 [parseMabDevices] / [parseServices]），不可盲解。
     */
    @SerializedName("mabInfo") val mabInfo: String? = null,
    /** 网关通知数组的 JSON 字符串（如弱密码提醒） */
    @SerializedName("notify") val notify: String? = null,
    /** 自助服务免密登录链接（含服务端下发的 md5 凭据：禁止落盘、打日志、跨会话缓存） */
    @SerializedName("selfUrl") val selfUrl: String? = null,
    /** 离线时为 "http://" */
    @SerializedName("redirectUrl") val redirectUrl: String? = null,
) {
    val isSuccess: Boolean get() = result == PortalResult.SUCCESS

    /** 过渡态：数据不完整，需稍后重试，不是失败 */
    val isWait: Boolean get() = result == PortalResult.WAIT

    /**
     * 是否已认证在线。
     *
     * 唯一判据是 `result`：`fail` 既可能是"未认证"，也可能是"userIndex 与会话不匹配"，
     * 还可能是"设备经无感认证上线、门户会话表里没有条目"（实测此时公网仍可访问）。
     * （实测两者响应完全一致），因此不解析 message。
     */
    val isOnline: Boolean get() = isSuccess || isWait
}

// ────────────────────────── 解析（纯函数，供 ViewModel 与单测使用） ──────────────────────────

private val gson = Gson()

/** 可选服务：优先 HTML 的 `selectService("…")`，回退 wait 态 `mabInfo` 的 `@` 分隔串 */
fun parseServices(info: PortalOnlineInfo): List<String> =
    parseServicesFromHtml(info.serviceList).ifEmpty { parseServicesFromDelimited(info.mabInfo) }

/** 已绑定无感认证设备；非 JSON 数组（wait 态的服务串）返回空列表 */
fun parseMabDevices(raw: String?): List<PortalMabDevice> =
    jsonArray(raw)?.map { gson.fromJson(it, PortalMabDevice::class.java) }.orEmpty()

/** 网关通知（如弱密码提醒）；非 JSON 数组返回空列表 */
fun parseNotices(raw: String?): List<String> =
    jsonArray(raw)?.mapNotNull { it.asString }.orEmpty()

private val SERVICE_CALL_RE = Regex("""selectService\("([^"]+)"""")

private fun parseServicesFromHtml(html: String?): List<String> =
    if (html.isNullOrBlank()) {
        emptyList()
    } else {
        SERVICE_CALL_RE.findAll(html).map { it.groupValues[1] }.distinct().toList()
    }

private fun parseServicesFromDelimited(raw: String?): List<String> {
    val text = raw?.trim().orEmpty()
    // 数组形式属设备列表（success 态），不是服务串
    if (text.isEmpty() || text.startsWith("[")) return emptyList()
    return text.split("@").map { it.trim() }.filter { it.isNotEmpty() }
}

/** 把字符串形式的 JSON 数组拆成元素；非数组（含 null）返回 null */
private fun jsonArray(raw: String?): List<JsonElement>? {
    val text = raw?.trim().orEmpty()
    if (!text.startsWith("[")) return null
    val element = gson.fromJson(text, JsonElement::class.java)
    return element?.takeIf { it.isJsonArray }?.asJsonArray?.toList()
}
