package edu.cqwu.electricity.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════
//  数据模型 - 缴费项目
// ═══════════════════════════════════════════

data class FeeCategory(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("children") val children: List<FeeItem>?,
)

data class FeeItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("imgUrl") val imgUrl: String?,
    @SerializedName("proModelUrl") val proModelUrl: String?,
)

private data class FeeProjectResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: List<FeeCategory>?,
)

// ═══════════════════════════════════════════
//  数据模型 - 订单
// ═══════════════════════════════════════════

data class OrderRecord(
    @SerializedName("id") val id: String,
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("productDesc") val productDesc: String?,
    @SerializedName("amount") val amount: Long, // 单位：分
    @SerializedName("status") val status: String,
    @SerializedName("displayStatus") val displayStatus: String?,
    @SerializedName("createDate") val createDate: String?,
    @SerializedName("actualCloseTime") val actualCloseTime: String?,
    @SerializedName("imgUrl") val imgUrl: String?,
    @SerializedName("proModelUrl") val proModelUrl: String?,
    @SerializedName("tradeChannel") val tradeChannel: String?,
) {
    /** 金额（单位：元），从分的转换 */
    val amountYuan: Double get() = amount / 100.0

    /** 状态显示文本 */
    val statusDisplay: String get() = when (status) {
        "COMPLETED" -> "已支付"
        "PENDING" -> "待支付"
        "REFUND" -> "已退款"
        "CLOSED" -> "已关闭"
        else -> status
    }
}

data class OrderPageData(
    @SerializedName("records") val records: List<OrderRecord>,
    @SerializedName("current") val current: Int?,
    @SerializedName("pages") val pages: Int?,
    @SerializedName("total") val total: Long?,
)

private data class OrderListResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: OrderPageData?,
)

// ═══════════════════════════════════════════
//  数据模型 - 个人资料
// ═══════════════════════════════════════════

data class UserProfile(
    @SerializedName("name") val name: String?,
    @SerializedName("deptName") val deptName: String?,
    @SerializedName("accountNum") val accountNum: String?,
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    companion object {
        private const val PROJECT_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/pay/cos/merchant/getProjectTypeAndProjectInfoListInit/datalook"
        private const val ORDER_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/order/pageOrderlist"
        private const val PAY_DOMAIN = "https://pay.cqwu.edu.cn"
        private const val XTOKEN_COOKIE_NAME = "datalook_reimbursement_token"

        fun buildPaymentUrl(proModelUrl: String?, projectId: String): String {
            val handler = proModelUrl?.let { "${it}Pay" } ?: "commonPay"
            return "https://pay.cqwu.edu.cn/mobile/#/$handler?projectId=$projectId"
        }

        fun buildOrderDetailUrl(orderNo: String): String {
            return "https://pay.cqwu.edu.cn/mobile/#/orderDetail?orderNo=$orderNo"
        }

        /** 个人信息 API */
        private const val PROFILE_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/personalCenter/findUserInfoByAccountNum"

        /**
         * 从 CookieStore 获取 X-Token。
         * 同时尝试带路径和不带路径的 URL，确保兼容 CookieManager 的行为差异。
         */
        fun getXToken(): String? {
            return CookieStore.getCookieValue(PAY_DOMAIN, XTOKEN_COOKIE_NAME)
                ?: CookieStore.getCookieValue("$PAY_DOMAIN/", XTOKEN_COOKIE_NAME)
                ?: CookieStore.getCookieValue("$PAY_DOMAIN/casLogin/", XTOKEN_COOKIE_NAME)
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

    suspend fun fetchProjects(): Result<List<FeeCategory>> = withContext(Dispatchers.IO) {
        try {
            if (getXToken().isNullOrBlank()) {
                return@withContext Result.failure(Exception("未登录，请先打开原网页完成认证"))
            }
            val request = buildBaseRequest(PROJECT_LIST_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应为空"))
            val apiResponse = gson.fromJson(body, FeeProjectResponse::class.java)
            if (apiResponse.messageCode != "0") {
                return@withContext Result.failure(
                    Exception(apiResponse.message.ifBlank { "请求失败(messageCode=${apiResponse.messageCode})" })
                )
            }
            Result.success(apiResponse.data ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
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
    ): Result<OrderPageData> = withContext(Dispatchers.IO) {
        try {
            if (getXToken().isNullOrBlank()) {
                return@withContext Result.failure(Exception("未登录，请先打开原网页完成认证"))
            }

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
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应为空"))
            val apiResponse = gson.fromJson(body, OrderListResponse::class.java)
            if (apiResponse.messageCode != "0") {
                return@withContext Result.failure(
                    Exception(apiResponse.message.ifBlank { "请求失败" })
                )
            }
            Result.success(apiResponse.data ?: OrderPageData(emptyList(), null, null, null))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 查询个人信息（"我的" Tab）。
     */
    suspend fun fetchUserProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            if (getXToken().isNullOrBlank()) {
                return@withContext Result.failure(Exception("未登录"))
            }
            val bodyJson = gson.toJson(mapOf(
                "accountNum" to "",
                "schoolCode" to "datalook",
                "dataSource" to "PAY",
            ))
            val request = buildBaseRequest(PROFILE_URL)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应为空"))
            val apiResponse = gson.fromJson(body, UserProfileResponse::class.java)
            if (apiResponse.messageCode != "0") {
                return@withContext Result.failure(
                    Exception(apiResponse.message.ifBlank { "请求失败" })
                )
            }
            Result.success(apiResponse.data ?: UserProfile(null, null, null))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
