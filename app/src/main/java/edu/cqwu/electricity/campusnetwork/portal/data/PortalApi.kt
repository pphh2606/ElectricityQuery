package edu.cqwu.electricity.campusnetwork.portal.data

import edu.cqwu.electricity.campusnetwork.common.CampusHttpBase
import edu.cqwu.electricity.common.net.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 认证网关（Dr.COM eportal，`http://222.179.99.144:8080`）客户端与接口封装。
 *
 * 与测速站共用底层网络工厂，但**不共用传输协议**：网关是表单编码 + `{result,...}` 平铺响应，
 * 没有 `{code,message,data}` 信封，详见 [PortalModels]。执行/判码/日志/异常归类由
 * [CampusHttpBase] 统一承担，本类只声明路径与平铺解析。
 *
 * 实测结论（2026-09-11，校园网内直连）：
 * - **判在线只看 `result`**：空 `userIndex` + 空 Cookie 查询即返回完整身份并回填 `userIndex`
 *   （网关按**请求源 IP** 定位会话）；而 `fail` 的 message 与"userIndex 与会话不匹配"场景
 *   **完全一致**，因此 message 不参与状态判断；
 * - `wait` 是过渡态（字段不完整但身份可用），由调用方重试，本层不做隐藏重试；
 * - 业务失败（`result:"fail"`）不抛异常，原样返回——网关 message 本身就是可直接展示的中文提示；
 * - 网络层失败按 `common` 统一规则归类（超时/拒绝 → CAMPUS_OFFLINE 等），技术细节进 AppLog，
 *   不静默吞掉；`CancellationException` 原样上抛。
 */
object PortalClient {

    /** 网关根地址（门户页与全部 API 同源；静态资源在 :8081，App 不需要） */
    const val BASE_URL = "http://222.179.99.144:8080"
}

class PortalApi internal constructor(
    client: okhttp3.OkHttpClient = HttpClientFactory.campusClient,
) : CampusHttpBase(client = client, tag = "PortalApi") {

    /**
     * 查询当前会话。
     *
     * [userIndex] 传空即由网关按**请求源 IP** 定位当前会话（实测：空值 + 无 Cookie 也能返回
     * 完整身份并回填 `userIndex`），因此进页面无需先做认证态探测。
     * 在线与否看返回的 `isOnline`（`result` 为 `success`/`wait`），**不要看 message**。
     */
    suspend fun onlineInfo(userIndex: String = ""): Result<PortalOnlineInfo> =
        postFlat(
            "getOnlineUserInfo",
            USER_INFO_PATH,
            mapOf("userIndex" to userIndex),
            PortalOnlineInfo::class.java,
        )

    /** 在线切换服务；服务名取自会话响应的 [parseServices]（不硬编码枚举） */
    suspend fun switchService(userIndex: String, serviceName: String): Result<PortalResult> =
        postFlat(
            "switchService",
            SWITCH_SERVICE_PATH,
            mapOf("userIndex" to userIndex, "serviceName" to serviceName),
            PortalResult::class.java,
        )

    /** 断开网络（下线）；`userIndex` 必须为真实凭证（传字面量 "null" 会被网关静默忽略） */
    suspend fun logout(userIndex: String): Result<PortalResult> =
        postFlat("logout", LOGOUT_PATH, mapOf("userIndex" to userIndex), PortalResult::class.java)

    /**
     * 无感认证开关（当前设备）。
     *
     * `mac` 传空串即由网关取当前在线设备的 MAC（实测行为）。
     * 注意取消操作的服务端响应存在 `result:"success"` + `message:"取消无感认证失败"` 的矛盾组合，
     * 因此调用方应忽略 message、以随后查询的 `hasMabInfo` 实际值为准。
     */
    suspend fun setMab(userIndex: String, enable: Boolean): Result<PortalResult> =
        postFlat(
            if (enable) "registerMac" else "cancelMac",
            if (enable) REGISTER_MAC_PATH else CANCEL_MAC_PATH,
            mapOf("mac" to "", "userIndex" to userIndex),
            PortalResult::class.java,
        )

    /**
     * 表单 POST 平铺 JSON 响应：请求构建与执行委托 [CampusHttpBase.execute]，
     * 异常统一经 [CampusHttpBase.logAndClassify] 归类（HTTP 非 2xx / 无网络等）。
     */
    private suspend fun <T> postFlat(
        desc: String,
        path: String,
        form: Map<String, String>,
        type: Class<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val text = execute("POST", PortalClient.BASE_URL + path) { post(formBody(form)) }
                .use { it.body.string() }
            Result.success(
                gson.fromJson(text, type) ?: throw IllegalStateException("$desc 响应解析失败"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // execute() 抛出的 HTTP 失败已带正确分类，logAndClassify 对其原样返回
            Result.failure(e.logAndClassify(desc))
        }
    }

    private companion object {
        const val USER_INFO_PATH = "/eportal/InterFace.do?method=getOnlineUserInfo"
        const val SWITCH_SERVICE_PATH = "/eportal/InterFace.do?method=switchService"
        const val LOGOUT_PATH = "/eportal/InterFace.do?method=logout"
        const val REGISTER_MAC_PATH = "/eportal/InterFace.do?method=registerMac"
        const val CANCEL_MAC_PATH = "/eportal/InterFace.do?method=cancelMac"
    }
}
