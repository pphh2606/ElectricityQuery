package edu.cqwu.electricity.data.network.cardrecharge

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.data.network.HttpClientFactory
import edu.cqwu.electricity.data.network.feeservicehall.ApiBusinessException
import edu.cqwu.electricity.data.network.feeservicehall.FeeServiceHallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 校园卡充值 API 封装
 *
 * 认证机制复用 [FeeServiceHallApi] 的 JWT Token 体系：
 * - 同一域名 pay.cqwu.edu.cn
 * - 同一 Cookie 名 datalook_reimbursement_token
 * - 同一 CAS -> dlyscas -> JWT 签发流程
 */
class CardRechargeApi {

    private val client = HttpClientFactory.createWithTimeout(10, 10, 10)
    private val gson = Gson()
    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    companion object {
        private const val TAG = "CardRechargeApi"
        private const val PAY_DOMAIN = "https://pay.cqwu.edu.cn"
        private const val PROJECT_ID = "80bb5ee2189e4ca2bd5dff4513a0dae2"
    }

    // -- 请求构建 --

    private fun buildBaseRequest(url: String): Request.Builder {
        val xToken = FeeServiceHallApi.getXToken() ?: ""
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", "$PAY_DOMAIN/")
            .addHeader("X-Requested-With", "edu.cqwu.electricity")
            .addHeader("X-Token", xToken)
    }

    /**
     * 自动重试：请求失败且 Token 不存在时自动获取 Token 并重试一次。
     */
    private suspend fun <T> autoRetry(block: suspend () -> Result<T>): Result<T> {
        val result = block()
        if (result.isFailure && FeeServiceHallApi.getXToken().isNullOrBlank()) {
            Log.d(TAG, "Token 不存在，自动获取后重试")
            val tokenResult = FeeServiceHallApi.obtainPayToken()
            if (tokenResult.isFailure) {
                return Result.failure(Exception("未登录，请先打开原网页完成认证"))
            }
            return block()
        }
        return result
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
                val parsed = gson.fromJson(body, CardBasicInfoResponse::class.java)
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
                val parsed = gson.fromJson(body, TradeChannelResponse::class.java)
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
                val parsed = gson.fromJson(body, CardRechargeOrderResponse::class.java)
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
        ip: String = "",
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
                val parsed = gson.fromJson(body, CardPaymentResponse::class.java)
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
                val parsed = gson.fromJson(body, CardOrderStatusResponse::class.java)
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
