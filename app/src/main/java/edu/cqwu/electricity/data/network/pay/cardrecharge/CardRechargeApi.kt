package edu.cqwu.electricity.data.network.pay.cardrecharge

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.data.network.pay.ApiResponse
import edu.cqwu.electricity.data.network.pay.PayApiBase
import edu.cqwu.electricity.data.network.feeservicehall.ApiBusinessException
import edu.cqwu.electricity.data.network.feeservicehall.FeeServiceHallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 校园卡充值 API 封装
 *
 * 认证机制复用 [FeeServiceHallApi] 的 JWT Token 体系：
 * - 同一域名 pay.cqwu.edu.cn
 * - 同一 Cookie 名 datalook_reimbursement_token
 * - 同一 CAS -> dlyscas -> JWT 签发流程
 */
class CardRechargeApi : PayApiBase() {

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    override val defaultErrorMsg: String = "未登录，请先打开原网页完成认证"

    companion object {
        private const val TAG = "CardRechargeApi"
        private const val PROJECT_ID = "80bb5ee2189e4ca2bd5dff4513a0dae2"
        private const val DEFAULT_IP = "218.2.101.93"
    }

    // ═══════════════════════════════════════════
    //  API 方法
    // ═══════════════════════════════════════════

    /**
     * 查询校园卡基本信息
     */
    suspend fun queryBasicInfo(
        studentId: String,
        projectId: String = PROJECT_ID
    ): Result<CardBasicInfo> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/api/pay/web/eCardRecharge/queryBasicInfo/$studentId/$projectId"
                val request = buildBaseRequest(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed: ApiResponse<CardBasicInfo> = parseApiResponse(body, CardBasicInfo::class.java)
                if (parsed.messageCode == "0" && parsed.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(ApiBusinessException(parsed.messageCode, parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryBasicInfo 失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 查询可用支付渠道
     */
    suspend fun queryTradeChannels(
        projectId: String = PROJECT_ID
    ): Result<List<CardPaymentChannel>> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/api/pay/open/pay/payrouterref/queryTradeChannel/$projectId/H5"
                val request = buildBaseRequest(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val listType = TypeToken.getParameterized(List::class.java, CardPaymentChannel::class.java).type
                val parsed: ApiResponse<List<CardPaymentChannel>> = parseApiResponse(body, listType)
                if (parsed.messageCode == "0") {
                    Result.success(parsed.data ?: emptyList())
                } else {
                    Result.failure(ApiBusinessException(parsed.messageCode, parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryTradeChannels 失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 创建充值订单
     *
     * @param amountStr 金额字符串（元），如 "0.02"
     */
    suspend fun createOrder(
        projectId: String = PROJECT_ID,
        amountStr: String,
    ): Result<CardRechargeOrderResult> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/api/pay/web/eCardRecharge/createOrder"
                val bodyJson = gson.toJson(mapOf(
                    "schoolCode" to "datalook",
                    "projectId" to projectId,
                    "cardId" to "",
                    "cardType" to "",
                    "payamtStr" to amountStr,
                ))
                val request = buildBaseRequest(url)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed: ApiResponse<CardRechargeOrderData> = parseApiResponse(body, CardRechargeOrderData::class.java)
                if (parsed.messageCode == "0") {
                    val trade = parsed.data?.payOrderTrade
                    if (trade != null) {
                        Result.success(trade)
                    } else {
                        Result.failure(Exception("订单创建响应缺少 PayOrderTrade"))
                    }
                } else {
                    Result.failure(ApiBusinessException(parsed.messageCode, parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "createOrder 失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 提交支付请求，获取支付宝自动提交表单 HTML
     *
     * @param payType 支付渠道代码，"01"=支付宝
     * @param ip 客户端 IP，传空字符串
     */
    suspend fun toPayOrderTrade(
        orderNo: String,
        payType: String = "01",
        ip: String = DEFAULT_IP,
    ): Result<CardPaymentResult> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/api/pay/web/third/toPayOrderTrade/"
                val bodyJson = gson.toJson(mapOf(
                    "tradeType" to "WAP",
                    "payType" to payType,
                    "orderNo" to orderNo,
                    "ip" to ip,
                    "schoolCode" to "datalook",
                    "dataSource" to "PAY",
                    "returnUrl" to null,
                ))
                val request = buildBaseRequest(url)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed: ApiResponse<CardPaymentResult> = parseApiResponse(body, CardPaymentResult::class.java)
                if (parsed.messageCode == "0" && parsed.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(ApiBusinessException(parsed.messageCode, parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "toPayOrderTrade 失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 查询订单状态（轮询用）
     */
    suspend fun queryOrderStatus(orderId: String): Result<CardOrderStatus> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/api/pay/pay/orderTrade/getOrdersSate/$orderId"
                val request = buildBaseRequest(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed: ApiResponse<CardOrderStatus> = parseApiResponse(body, CardOrderStatus::class.java)
                if (parsed.messageCode == "0" && parsed.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(ApiBusinessException(parsed.messageCode, parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryOrderStatus 失败", e)
                Result.failure(e)
            }
        }
    }
}

// ═══════════════════════════════════════════
//  数据模型 - 校园卡充值
// ═══════════════════════════════════════════

/**
 * 校园卡基本信息（queryBasicInfo 响应）
 */
data class CardBasicInfo(
    @SerializedName("username") val username: String,
    @SerializedName("idserial") val idserial: String,
    @SerializedName("projectId") val projectId: String,
    @SerializedName("status") val status: String,
    @SerializedName("maxBalance") val maxBalance: String,
    @SerializedName("expiredate") val expiredate: String,
) {
    /** 最大余额（元），从分转换 */
    val maxBalanceYuan: Double get() = maxBalance.toLongOrNull()?.div(100.0) ?: 1000.0

    /** 卡是否正常可用 */
    val isNormal: Boolean get() = status == "normal"
}

/**
 * 充值订单创建响应（createOrder → PayOrderTrade 字段）
 */
data class CardRechargeOrderResult(
    @SerializedName("id") val orderId: String,
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("status") val status: String,
    @SerializedName("schdualCloseTime") val schdualCloseTime: String?,
    @SerializedName("productDesc") val productDesc: String?,
)

/**
 * 支付渠道（queryTradeChannel 响应）
 */
data class CardPaymentChannel(
    @SerializedName("id") val id: String,
    @SerializedName("channelName") val channelName: String,
    @SerializedName("code") val code: String,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("interfaceType") val interfaceType: String,
)

/**
 * 支付提交结果（toPayOrderTrade 响应）
 */
data class CardPaymentResult(
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("sbHtml") val sbHtml: String?,
    @SerializedName("mwebUrl") val mwebUrl: String?,
    @SerializedName("payType") val payType: String,
    @SerializedName("tradeType") val tradeType: String,
)

/**
 * 订单状态（getOrdersSate 响应）
 */
data class CardOrderStatus(
    @SerializedName("id") val id: String,
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("status") val status: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("productDesc") val productDesc: String?,
    @SerializedName("schdualCloseTime") val schdualCloseTime: String?,
) {
    val amountYuan: Double get() = amount / 100.0

    val statusDisplay: String get() = when (status) {
        "COMPLETED" -> "已支付"
        "PENDING_PAYMENT" -> "待支付"
        "CLOSED" -> "已关闭"
        else -> status
    }
}

// ═══════════════════════════════════════════
//  内部数据结构（createOrder 嵌套 data）
// ═══════════════════════════════════════════

/**
 * createOrder 响应的 data 嵌套结构
 *
 * 注意：响应包装已统一为 [ApiResponse]，
 * 此类仅保留嵌套 data 内部结构。
 */
internal data class CardRechargeOrderData(
    @SerializedName("PayOrderTrade") val payOrderTrade: CardRechargeOrderResult?,
)
