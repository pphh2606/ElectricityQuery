package edu.cqwu.electricity.feeservicehall.data

import edu.cqwu.electricity.logging.AppLog
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.payment.data.PayApiBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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

/**
 * 缴费大厅（pay.cqwu.edu.cn）API 封装。
 *
 * 认证复用 [PayApiBase]：X-Token 由 [edu.cqwu.electricity.payment.data.PaySessionManager] 统一管理
 * （JWT 本地 exp 校验 + 过期静默刷新），业务请求前保证 token 新鲜。
 */
class FeeServiceHallApi : PayApiBase() {

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    companion object {
        private const val TAG = "FeeServiceHallApi"
        private const val PROJECT_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/pay/cos/merchant/getProjectTypeAndProjectInfoListInit/datalook"
        private const val ORDER_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/order/pageOrderlist"
        private const val CLOSE_ORDER_URL = PayApiBase.PAY_DOMAIN + "/api/pay/web/order/closeOrderById"

        /** 个人信息 API */
        private const val PROFILE_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/personalCenter/findUserInfoByAccountNum"

        fun buildPaymentUrl(proModelUrl: String?, projectId: String): String {
            val handler = proModelUrl?.let { "${it}Pay" } ?: "commonPay"
            return "https://pay.cqwu.edu.cn/mobile/#/$handler?projectId=$projectId"
        }

        /** 构建待支付订单的继续支付链接 */
        fun buildContinuePaymentUrl(orderId: String, projectId: String): String {
            return "https://pay.cqwu.edu.cn/mobile/#/person?orderId=$orderId&projectId=$projectId"
        }
    }

    suspend fun fetchProjects(): Result<List<FeeCategory>> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val request = buildBaseRequest(PROJECT_LIST_URL).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                gson.parseApiResponse<FeeProjectResponse>(body).map { it.data ?: emptyList() }
            } catch (e: ApiBusinessException) {
                AppLog.e(TAG, "缴费大厅项目列表业务失败", e)
                Result.failure(e)
            } catch (e: Exception) {
                AppLog.e(TAG, "缴费大厅项目列表加载失败", e)
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
                AppLog.e(TAG, "缴费大厅订单列表业务失败", e)
                Result.failure(e)
            } catch (e: Exception) {
                AppLog.e(TAG, "缴费大厅订单列表加载失败", e)
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
                AppLog.e(TAG, "缴费大厅个人信息业务失败", e)
                Result.failure(e)
            } catch (e: Exception) {
                AppLog.e(TAG, "缴费大厅个人信息加载失败", e)
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
                AppLog.e(TAG, "缴费大厅关闭订单业务失败", e)
                Result.failure(e)
            } catch (e: Exception) {
                AppLog.e(TAG, "缴费大厅关闭订单失败", e)
                Result.failure(e)
            }
        }
    }
}
