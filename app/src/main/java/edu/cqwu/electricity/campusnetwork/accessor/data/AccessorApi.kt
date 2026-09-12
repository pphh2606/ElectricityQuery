package edu.cqwu.electricity.campusnetwork.accessor.data

import edu.cqwu.electricity.campusnetwork.common.CampusNetworkHttp

/**
 * 接入者信息接口封装（校园网测速站 client-context）。
 *
 * 接口特点：
 * - **无 Cookie / 无鉴权头**，"源 IP 即身份"：仅在"已连接校园网且完成上网认证
 *   （SAM 在线）"时可达；其他场景（公网 / 未认证）请求表现为连接超时/失败。
 * - 因此**不需要** App 登录态；网络层跟随全局 WebVPN 实验性开关（见 station 的客户端）。
 *
 * JSON 传输、错误归类（[CampusNetworkException] + AppLog 全量记录，不静默吞掉）
 * 统一由 [CampusNetworkHttp] 承担，本类只声明路径与类型。
 */
class AccessorApi internal constructor(
    private val json: CampusNetworkHttp = CampusNetworkHttp(),
) {

    private companion object {
        const val TAG = "AccessorApi"
    }

    /**
     * 查询当前接入者信息。
     *
     * @return [Result.success] 携带 [AccessorData]；失败为 [CampusNetworkException]
     */
    suspend fun fetchAccessorInfo(): Result<AccessorData> =
        json.get(TAG, "/client-context", AccessorData::class.java)
}
