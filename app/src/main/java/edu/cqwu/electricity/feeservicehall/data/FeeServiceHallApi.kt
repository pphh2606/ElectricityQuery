package edu.cqwu.electricity.feeservicehall.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.login.data.CookieParser
import edu.cqwu.electricity.login.data.CookieStore
import edu.cqwu.electricity.login.data.HtmlFormParser
import edu.cqwu.electricity.network.ManualCasFlowTag
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.URLDecoder

// ═══════════════════════════════════════════
//  内部响应模型（不对外暴露）
// ═══════════════════════════════════════════

/**
 * 通用 API 响应解析器。
 *
 * 先解析公共字段检查 [messageCode]，成功后再解析完整类型。
 * 消除每个 API 方法中重复的 messageCode != "0" 判断。
 */
private class ApiBaseResponse(
    @SerializedName("messageCode") val messageCode: String = "",
    @SerializedName("message") val message: String = "",
)

private inline fun <reified T> Gson.parseApiResponse(json: String): Result<T> {
    val base = fromJson(json, ApiBaseResponse::class.java)
    return if (base.messageCode == "0") {
        Result.success(fromJson(json, T::class.java))
    } else {
        Result.failure(ApiBusinessException(base.message))
    }
}

private data class FeeProjectResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: List<FeeCategory>?,
)

private data class OrderListResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: OrderPageData?,
)

private data class UserProfileResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: UserProfile?,
)

// ═══════════════════════════════════════════
//  API 封装
// ═══════════════════════════════════════════

class FeeServiceHallApi {

    private val client = HttpClientFactory.create(
        connectTimeout = 10,
        readTimeout = 10,
        writeTimeout = 10,
    )

    private val gson = Gson()
    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    companion object {
        private const val PROJECT_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/pay/cos/merchant/getProjectTypeAndProjectInfoListInit/datalook"
        private const val ORDER_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/order/pageOrderlist"
        private const val PAY_DOMAIN = "https://pay.cqwu.edu.cn"
        private const val CLOSE_ORDER_URL = "$PAY_DOMAIN/api/pay/web/order/closeOrderById"
        private const val CAS_LOGIN_URL = "$PAY_DOMAIN/casLogin/"
        private const val XTOKEN_COOKIE_NAME = "datalook_reimbursement_token"

        private val tokenMutex = Mutex()

        fun buildPaymentUrl(proModelUrl: String?, projectId: String): String {
            val handler = proModelUrl?.let { "${it}Pay" } ?: "commonPay"
            return "https://pay.cqwu.edu.cn/mobile/#/$handler?projectId=$projectId"
        }

        /** 构建待支付订单的继续支付链接 */
        fun buildContinuePaymentUrl(orderId: String, projectId: String): String {
            return "https://pay.cqwu.edu.cn/mobile/#/person?orderId=$orderId&projectId=$projectId"
        }

        /** 个人信息 API */
        private const val PROFILE_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/personalCenter/findUserInfoByAccountNum"

        /**
         * 从 CookieStore 获取 X-Token。
         * 同时尝试带路径和不带路径的 URL，确保兼容 CookieManager 的行为差异。
         */
        fun getXToken(): String? {
            return CookieParser.getValue(CookieStore.getCookie(PAY_DOMAIN), XTOKEN_COOKIE_NAME)
                ?: CookieParser.getValue(CookieStore.getCookie("$PAY_DOMAIN/"), XTOKEN_COOKIE_NAME)
                ?: CookieParser.getValue(CookieStore.getCookie("$PAY_DOMAIN/casLogin/"), XTOKEN_COOKIE_NAME)
        }

        /**
         * 自动获取 pay.cqwu.edu.cn 的 JWT Token（datalook_reimbursement_token）。
         *
         * 流程与网页版一致：
         *   1. GET /casLogin/
         *   2. 若跳转 authserver，则跟随 CAS 完成 ticket 交换，再解析 dlyscas 地址
         *   3. 若已登录，/casLogin/ 会直接跳转 dlyscas
         *   4. GET dlyscas → 从 302 Location 中提取 JWT Token
         */
        suspend fun obtainPayToken(): Result<String> = tokenMutex.withLock {
            getXToken()?.let { return@withLock Result.success(it) }
            withContext(Dispatchers.IO) {
                try {
                    Log.d("FeeServiceHallApi", ">>> 自动获取 pay JWT Token 开始")
                    val tokenRegex = Regex("""token=([^&]+)""")
                    val shared = HttpClientFactory.shared

                    // ── 步骤1: 访问 /casLogin/ ──
                    val step1Resp = shared.newCall(
                        Request.Builder().url(CAS_LOGIN_URL).get()
                            .tag(ManualCasFlowTag::class.java, ManualCasFlowTag)
                            .build(),
                    ).execute()
                    val step1Html = step1Resp.body.string()
                    Log.d("FeeServiceHallApi", "步骤1: /casLogin/ 响应码=${step1Resp.code}, HTML长度=${step1Html.length}")

                    val firstRedirect = HtmlFormParser.extractJsRedirect(step1Html)
                        ?: return@withContext Result.failure(Exception("无法从 /casLogin/ 解析重定向地址"))
                    Log.d("FeeServiceHallApi", "步骤1: 解析到重定向地址=$firstRedirect")

                    // ── 步骤2: 仅在需要 CAS 认证时跟随 authserver ──
                    val dlyscasUrl = if (firstRedirect.contains("/authserver/")) {
                        val casClient = shared.newBuilder()
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build()
                        val casResp = casClient.newCall(
                            Request.Builder().url(firstRedirect).get()
                                .tag(ManualCasFlowTag::class.java, ManualCasFlowTag)
                                .build(),
                        ).execute()
                        val casHtml = casResp.body.string()
                        val finalUrl = casResp.request.url.toString()
                        Log.d(
                            "FeeServiceHallApi",
                            "步骤2: CAS认证完成, 响应码=${casResp.code}, HTML长度=${casHtml.length}, finalUrl=$finalUrl",
                        )

                        val tokenFromFinal = tokenRegex.find(finalUrl)?.groupValues?.getOrNull(1)
                        if (tokenFromFinal != null) {
                            return@withContext storePayToken(tokenFromFinal)
                        }
                        HtmlFormParser.extractJsRedirect(casHtml)
                            ?: return@withContext Result.failure(Exception("无法从 CAS 响应解析 dlyscas 地址"))
                    } else {
                        firstRedirect
                    }
                    Log.d("FeeServiceHallApi", "步骤2: 解析到 dlyscas 地址=$dlyscasUrl")

                    // ── 步骤3: 访问 dlyscas，获取 JWT Token ──
                    val step3Client = shared.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build()
                    val step3Resp = step3Client.newCall(
                        Request.Builder().url(dlyscasUrl).get()
                            .tag(ManualCasFlowTag::class.java, ManualCasFlowTag)
                            .build(),
                    ).execute()
                    val location = step3Resp.header("Location") ?: ""
                    Log.d("FeeServiceHallApi", "步骤3: dlyscas 响应码=${step3Resp.code}, Location=$location")

                    val token = tokenRegex.find(location)?.groupValues?.getOrNull(1)
                        ?: return@withContext Result.failure(Exception("无法从 dlyscas 响应中提取 JWT Token"))
                    storePayToken(token)
                } catch (e: SocketTimeoutException) {
                    Log.e("FeeServiceHallApi", ">>> 获取 JWT Token 超时", e)
                    Result.failure(Exception("获取 Token 超时，请检查网络连接", e))
                } catch (e: Exception) {
                    Log.e("FeeServiceHallApi", ">>> 获取 JWT Token 失败", e)
                    Result.failure(e)
                }
            }
        }

        private fun storePayToken(token: String): Result<String> {
            val decodedToken = URLDecoder.decode(token, "UTF-8")
            Log.d("FeeServiceHallApi", ">>> JWT Token 获取成功, 长度=${decodedToken.length}")
            CookieStore.setCookie(PAY_DOMAIN, "$XTOKEN_COOKIE_NAME=$decodedToken")
            CookieStore.setCookie("$PAY_DOMAIN/", "$XTOKEN_COOKIE_NAME=$decodedToken")
            CookieStore.setCookie("$PAY_DOMAIN/casLogin/", "$XTOKEN_COOKIE_NAME=$decodedToken")
            CookieStore.setCookie(PAY_DOMAIN, "datalook_login_status=false")
            return Result.success(decodedToken)
        }
    }

    private fun buildBaseRequest(url: String): Request.Builder {
        val xToken = getXToken() ?: ""
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", "https://pay.cqwu.edu.cn/mobile/")
            .addHeader("X-Requested-With", "edu.cqwu.electricity")
            .addHeader("X-Token", xToken)
    }

    /**
     * 执行 API 调用，请求失败且 Token 不存在时自动获取 Token 并重试一次。
     *
     * 优化说明（避免预检请求）：
     *   不先在 ensureToken() 中发测试请求验证 Token 有效性，而是直接发实际请求。
     *   仅当请求失败且 getXToken() 返回空（说明 Token 不存在或被清除）时，
     *   才触发 obtainPayToken() 获取新 Token，然后重试一次原请求。
     *   这样在 Token 有效的情况下，100% 不产生额外网络开销。
     */
    private suspend fun <T> autoRetry(block: suspend () -> Result<T>): Result<T> {
        val result = block()
        if (result.isFailure && getXToken().isNullOrBlank()) {
            Log.d("FeeServiceHallApi", "Token 不存在，自动获取后重试")
            val tokenResult = obtainPayToken()
            if (tokenResult.isFailure) {
                return Result.failure(Exception("未登录，请先打开原网页完成认证"))
            }
            return block()
        }
        return result
    }

    suspend fun fetchProjects(): Result<List<FeeCategory>> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val request = buildBaseRequest(PROJECT_LIST_URL).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                gson.parseApiResponse<FeeProjectResponse>(body).map { it.data ?: emptyList() }
            } catch (e: ApiBusinessException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 查询订单列表（分页）。
     *
     * @param pageCurrent 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param projectName 项目名称筛选（可选）
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate 结束日期（yyyy-MM-dd）
     */
    suspend fun fetchOrders(
        pageCurrent: Int = 1,
        pageSize: Int = 10,
        projectName: String = "",
        projectId: String = "",
        startDate: String,
        endDate: String,
    ): Result<OrderPageData> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = gson.toJson(mapOf(
                    "pageCurrent" to pageCurrent,
                    "pageSize" to pageSize,
                    "projectName" to projectName,
                    "projectId" to projectId,
                    "startCreateDate" to startDate,
                    "endCreateDate" to endDate,
                    "schoolCode" to "datalook",
                ))

                val request = buildBaseRequest(ORDER_LIST_URL)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body.string()
                gson.parseApiResponse<OrderListResponse>(body).map { it.data ?: OrderPageData(emptyList(), null, null, null) }
            } catch (e: ApiBusinessException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 查询个人信息（"我的" Tab）。
     */
    suspend fun fetchUserProfile(): Result<UserProfile> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = gson.toJson(mapOf(
                    "accountNum" to "",
                    "schoolCode" to "datalook",
                    "dataSource" to "PAY",
                ))
                val request = buildBaseRequest(PROFILE_URL)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                gson.parseApiResponse<UserProfileResponse>(body).map { it.data ?: UserProfile(null, null, null) }
            } catch (e: ApiBusinessException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 关闭待支付订单。
     *
     * @param orderId 订单 ID（即 OrderRecord.id）
     */
    suspend fun closeOrder(orderId: String): Result<Unit> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val request = buildBaseRequest("$CLOSE_ORDER_URL/$orderId")
                    .post("".toRequestBody(jsonMediaType))
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val base = gson.fromJson(body, ApiBaseResponse::class.java)
                if (base.messageCode == "0") {
                    Result.success(Unit)
                } else {
                    Result.failure(ApiBusinessException(base.message))
                }
            } catch (e: ApiBusinessException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
