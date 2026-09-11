package edu.cqwu.electricity.campusnetwork.portal.data

import com.google.gson.Gson
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkErrorKind
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkException
import edu.cqwu.electricity.campusnetwork.common.toCampusNetworkException
import edu.cqwu.electricity.common.net.CookieStoreOkHttpJar
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 认证网关（Dr.COM eportal，`http://222.179.99.144:8080`）客户端与接口封装。
 *
 * 与测速站共用底层网络工厂，但**不共用传输**：网关是表单编码 + `{result,...}` 平铺响应，
 * 没有 `{code,message,data}` 信封，详见 [PortalModels]。
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

    /**
     * 跟随全局 WebVPN 开关（与校园网络其它功能保持一致的既定行为）。
     *
     * 网关业务接口均为 POST 直返 200、无重定向语义，故沿用默认的跟随策略。
     */
    val client: OkHttpClient by lazy {
        HttpClientFactory.create(
            cookieJar = CookieStoreOkHttpJar,
            includeWebVpn = true,
        )
    }
}

class PortalApi internal constructor(
    private val client: OkHttpClient = PortalClient.client,
) {

    /**
     * 查询当前会话。
     *
     * [userIndex] 传空即由网关按**请求源 IP** 定位当前会话（实测：空值 + 无 Cookie 也能返回
     * 完整身份并回填 `userIndex`），因此进页面无需先做认证态探测。
     * 在线与否看返回的 `isOnline`（`result` 为 `success`/`wait`），**不要看 message**。
     */
    suspend fun onlineInfo(userIndex: String = ""): Result<PortalOnlineInfo> =
        postJson(
            "getOnlineUserInfo",
            USER_INFO_PATH,
            mapOf("userIndex" to userIndex),
            PortalOnlineInfo::class.java,
        )

    /** 在线切换服务；服务名取自会话响应的 [parseServices]（不硬编码枚举） */
    suspend fun switchService(userIndex: String, serviceName: String): Result<PortalResult> =
        postJson(
            "switchService",
            SWITCH_SERVICE_PATH,
            mapOf("userIndex" to userIndex, "serviceName" to serviceName),
            PortalResult::class.java,
        )

    /** 断开网络（下线）；`userIndex` 必须为真实凭证（传字面量 "null" 会被网关静默忽略） */
    suspend fun logout(userIndex: String): Result<PortalResult> =
        postJson("logout", LOGOUT_PATH, mapOf("userIndex" to userIndex), PortalResult::class.java)

    /**
     * 无感认证开关（当前设备）。
     *
     * `mac` 传空串即由网关取当前在线设备的 MAC（实测行为）。
     * 注意取消操作的服务端响应存在 `result:"success"` + `message:"取消无感认证失败"` 的矛盾组合，
     * 因此调用方应忽略 message、以随后查询的 `hasMabInfo` 实际值为准。
     */
    suspend fun setMab(userIndex: String, enable: Boolean): Result<PortalResult> =
        postJson(
            if (enable) "registerMac" else "cancelMac",
            if (enable) REGISTER_MAC_PATH else CANCEL_MAC_PATH,
            mapOf("mac" to "", "userIndex" to userIndex),
            PortalResult::class.java,
        )

    // ────────────────────────── 传输 ──────────────────────────

    /** 统一兜底：归类网络异常并记录日志；[CancellationException] 原样上抛，绝不吞掉 */
    private fun <T> guard(desc: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.e(TAG, "$desc 失败: ${e.message}", e)
        Result.failure(e.toCampusNetworkException())
    }

    private suspend fun <T> postJson(
        desc: String,
        path: String,
        form: Map<String, String>,
        type: Class<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        guard(desc) {
            val text = postText(desc, path, form)
            gson.fromJson(text, type) ?: throw IllegalStateException("$desc 响应解析失败")
        }
    }

    private fun postText(desc: String, path: String, form: Map<String, String>): String {
        val body = FormBody.Builder()
            .apply { form.forEach { (k, v) -> add(k, v) } }
            .build()
        val request = Request.Builder()
            .url(PortalClient.BASE_URL + path)
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$desc HTTP 失败: ${response.code}")
                throw CampusNetworkException(
                    CampusNetworkErrorKind.SERVER,
                    userMessage = "HTTP ${response.code}",
                )
            }
            response.body.string()
        }
    }

    private companion object {
        const val TAG = "PortalApi"

        const val USER_INFO_PATH = "/eportal/InterFace.do?method=getOnlineUserInfo"
        const val SWITCH_SERVICE_PATH = "/eportal/InterFace.do?method=switchService"
        const val LOGOUT_PATH = "/eportal/InterFace.do?method=logout"
        const val REGISTER_MAC_PATH = "/eportal/InterFace.do?method=registerMac"
        const val CANCEL_MAC_PATH = "/eportal/InterFace.do?method=cancelMac"

        val gson = Gson()
    }
}
