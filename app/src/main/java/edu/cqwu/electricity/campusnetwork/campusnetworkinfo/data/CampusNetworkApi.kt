package edu.cqwu.electricity.campusnetwork.campusnetworkinfo.data

import edu.cqwu.electricity.campusnetwork.common.CampusNetworkJson

/**
 * 接入者信息接口封装（校园网测速站 client-context）。
 *
 * 接口特点：
 * - **无 Cookie / 无鉴权头**，"源 IP 即身份"：仅在"已连接校园网且完成上网认证
 *   （SAM 在线）"时可达；其他场景（公网 / 未认证）请求表现为连接超时/失败。
 * - 因此**不需要** App 登录态，也**不经过** WebVPN。
 *
 * JSON 传输、错误归类（[CampusNetworkException] + AppLog 全量记录，不静默吞掉）
 * 统一由 common 的 [CampusNetworkJson] 承担，本类只声明路径与类型。
 */
class CampusNetworkApi internal constructor(
    private val json: CampusNetworkJson = CampusNetworkJson(),
) {

    private companion object {
        const val TAG = "CampusNetworkApi"
    }

    /**
     * 查询当前接入者信息。
     *
     * @return [Result.success] 携带 [ClientContextData]；失败为 [CampusNetworkException]
     */
    suspend fun fetchClientContext(): Result<ClientContextData> =
        json.get(TAG, "/client-context", ClientContextData::class.java)
}
