package edu.cqwu.electricity.electricity.data

import android.util.Log
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.electricity.data.OrderStatusData
import edu.cqwu.electricity.payment.data.ApiResponse
import edu.cqwu.electricity.payment.data.PayApiBase
import edu.cqwu.electricity.feeservicehall.data.ApiBusinessException
import edu.cqwu.electricity.feeservicehall.data.FeeServiceHallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

/**
 * 电费充值支付 API 封装
 *
 * 对标 [edu.cqwu.electricity.cardcenter.data.CardRechargeApi]，
 * 使用电费系统专用的接口（pay.cqwu.edu.cn 域名）。
 *
 * 核心方法：
 * - [fetchShowselectHtml] — OkHttp GET showselect 页面，解析隐藏字段
 * - [gotToPay] — POST /pay/cashier/gotToPay（form-urlencoded），替代 WebView JS 注入
 * - [queryOrderStatus] — GET /pay/cashier/getOrderById/{orderId}
 *
 * 认证机制复用 [FeeServiceHallApi] 的 JWT Token 体系：
 * - 同一域名 pay.cqwu.edu.cn
 * - 同一 Cookie 名 datalook_reimbursement_token
 */
class ElectricityPayApi : PayApiBase() {

    companion object {
        private const val TAG = "ElectricityPayApi"
    }

    // ═══════════════════════════════════════════
    //  API 方法
    // ═══════════════════════════════════════════

    /**
     * 加载 showselect 页面并解析隐藏字段
     *
     * 用 OkHttp GET + HTML 解析替代隐藏 WebView 加载。
     * 从 showselect HTML 中提取 orderNo、orderId 等关键字段。
     *
     * @param payUrl showselect 页面 URL（来自 getCQPayOrder 响应）
     * @return ShowselectPageData 包含 orderNo, orderId 等
     */
    suspend fun fetchShowselectHtml(payUrl: String): Result<ShowselectPageData> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "加载 showselect 页面: ${payUrl.take(100)}...")
                val request = Request.Builder()
                    .url(payUrl)
                    .get()
                    .addHeader("Referer", "https://electricitypay.cqwu.edu.cn/")
                    .build()
                val response = client.newCall(request).execute()
                val html = response.body.string()
                Log.d(TAG, "showselect HTML 长度: ${html.length}")

                val data = parseShowselectHtml(html)
                Log.d(TAG, "解析结果: orderNo=${data.orderNo}, orderId=${data.orderId}")
                Result.success(data)
            } catch (e: Exception) {
                Log.e(TAG, "加载 showselect 页面失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 提交支付请求，获取支付宝/微信支付信息
     *
     * 对标校园卡的 toPayOrderTrade()，但使用不同的接口：
     * - 校园卡: POST /api/pay/web/third/toPayOrderTrade/ (JSON body)
     * - 电费:   POST /pay/cashier/gotToPay (form-urlencoded)
     *
     * @param orderNo 订单号（从 showselect HTML 解析）
     * @param payType 支付方式："01"=支付宝, "02"=微信
     * @param publictype 公共类型（从 showselect HTML 解析）
     * @param openId 用户 openId（从 showselect HTML 解析）
     */
    suspend fun gotToPay(
        orderNo: String,
        payType: String,
        publictype: String = "",
        openId: String = "",
    ): Result<GotToPayResult> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/pay/cashier/gotToPay"
                Log.d(TAG, "gotToPay: orderNo=$orderNo, payType=$payType")

                // 使用 FormBody（form-urlencoded），不同于校园卡的 JSON body
                val formBody = FormBody.Builder()
                    .add("payType", payType)
                    .add("publictype", publictype)
                    .add("orderTradeNo", orderNo)
                    .add("userIp", "218.194.188.173")
                    .add("tradeType", "WAP")
                    .add("openId", openId)
                    .build()

                val request = buildBaseRequest(url)
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .addHeader("Origin", "https://pay.cqwu.edu.cn")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body.string()
                Log.d(TAG, "gotToPay 响应: ${body.take(200)}...")

                val parsed: ApiResponse<GotToPayResponseData> = parseApiResponse(body, GotToPayResponseData::class.java)
                if (parsed.messageCode == "0" && parsed.data != null) {
                    val d = parsed.data
                    Result.success(
                        GotToPayResult(
                            payType = d.payType ?: payType,
                            tradeType = d.tradeType ?: "WAP",
                            sbHtml = d.sbHtml,
                            mwebUrl = d.mwebUrl,
                            paymentOrderNo = d.paymentOrderNo,
                        )
                    )
                } else {
                    Result.failure(ApiBusinessException(parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "gotToPay 失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 查询订单状态（轮询用）
     *
     * 从 ElectricityApi 迁移到此处，统一使用 pay.cqwu.edu.cn 的 JWT 认证。
     *
     * @param orderId 订单 ID（从 showselect HTML 解析）
     */
    suspend fun queryOrderStatus(orderId: String): Result<OrderStatusData> = autoRetry {
        withContext(Dispatchers.IO) {
            try {
                val url = "$PAY_DOMAIN/pay/cashier/getOrderById/$orderId"
                Log.d(TAG, "查询订单状态: $url")

                val request = buildBaseRequest(url)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body.string()
                Log.d(TAG, "订单状态响应: ${body.take(200)}...")

                val parsed: ApiResponse<OrderStatusData> = parseApiResponse(body, OrderStatusData::class.java)
                if (parsed.messageCode == "0" && parsed.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(Exception(parsed.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询订单状态失败", e)
                Result.failure(e)
            }
        }
    }

    // ═══════════════════════════════════════════
    //  HTML 解析
    // ═══════════════════════════════════════════

    /**
     * 从 showselect HTML 中解析隐藏字段
     *
     * HTML 结构（从 HAR 中提取）：
     * ```html
     * <input type="hidden" id="orderNo" value="26062401414879542841"/>
     * <input type="hidden" id="openId" value=""/>
     * <input type="hidden" id="orderId" value="dea336eb72c64dccb189f5019b8aea45"/>
     * <input type="hidden" id="publictype" value=""/>
     * <input type="hidden" id="projectId" value="6008886e0cad4ea696b31c019395fb44"/>
     * <input type="hidden" id="productDesc" value="学生宿舍电费充值"/>
     * ```
     */
    private fun parseShowselectHtml(html: String): ShowselectPageData {
        fun extractHiddenField(id: String): String {
            val regex = Regex(
                """<input[^>]*type\s*=\s*["']hidden["'][^>]*id\s*=\s*["']${Regex.escape(id)}["'][^>]*value\s*=\s*["']([^"']*?)["']""",
                RegexOption.IGNORE_CASE
            )
            // 也尝试 id 在 type 之前的情况
            return regex.find(html)?.groupValues?.getOrNull(1)
                ?: run {
                    val regex2 = Regex(
                        """<input[^>]*id\s*=\s*["']${Regex.escape(id)}["'][^>]*type\s*=\s*["']hidden["'][^>]*value\s*=\s*["']([^"']*?)["']""",
                        RegexOption.IGNORE_CASE
                    )
                    regex2.find(html)?.groupValues?.getOrNull(1) ?: ""
                }
        }

        return ShowselectPageData(
            orderNo = extractHiddenField("orderNo"),
            orderId = extractHiddenField("orderId"),
            publictype = extractHiddenField("publictype"),
            openId = extractHiddenField("openId"),
            projectId = extractHiddenField("projectId"),
            productDesc = extractHiddenField("productDesc"),
        )
    }
}

// ═══════════════════════════════════════════
//  数据模型 - 电费充值支付流程
// ═══════════════════════════════════════════

/**
 * showselect 页面解析结果
 *
 * 从 showselect HTML 的隐藏字段中提取，
 * 替代 WebView 加载 + JS 读取 DOM 的方式。
 *
 * HTML 示例：
 * ```html
 * <input type="hidden" id="orderNo" value="26062401414879542841"/>
 * <input type="hidden" id="orderId" value="dea336eb72c64dccb189f5019b8aea45"/>
 * <input type="hidden" id="publictype" value=""/>
 * <input type="hidden" id="openId" value=""/>
 * <input type="hidden" id="projectId" value="6008886e0cad4ea696b31c019395fb44"/>
 * <input type="hidden" id="productDesc" value="学生宿舍电费充值"/>
 * ```
 */
data class ShowselectPageData(
    /** 订单号（用于 gotToPay 请求） */
    val orderNo: String,
    /** 订单 ID（用于轮询订单状态） */
    val orderId: String,
    /** 公共类型（通常为空） */
    val publictype: String,
    /** 用户 openId */
    val openId: String,
    /** 项目 ID */
    val projectId: String,
    /** 产品描述 */
    val productDesc: String,
)

/**
 * gotToPay 响应数据
 *
 * POST /pay/cashier/gotToPay 的成功响应。
 * 对标校园卡的 [edu.cqwu.electricity.cardcenter.data.CardPaymentResult]。
 *
 * - 支付宝：返回 sbHtml（自动提交表单 HTML）
 * - 微信：返回 mwebUrl（H5 支付页 URL）
 */
data class GotToPayResult(
    /** 支付方式代码："01"=支付宝, "02"=微信 */
    val payType: String,
    /** 交易类型："WAP" */
    val tradeType: String,
    /** 支付宝：自动提交表单 HTML（通过 loadDataWithBaseURL 加载到 WebView） */
    val sbHtml: String?,
    /** 微信：H5 支付页 URL（通过 loadUrl 加载到 WebView） */
    val mwebUrl: String?,
    /** 支付单号 */
    val paymentOrderNo: String?,
)

// ═══════════════════════════════════════════
//  内部数据结构（gotToPay data 字段）
// ═══════════════════════════════════════════

/**
 * gotToPay 响应 data 字段
 *
 * 响应包装已统一为 [ApiResponse]，
 * 此类仅保留 data 内部结构。
 */
internal data class GotToPayResponseData(
    @SerializedName("payType") val payType: String?,
    @SerializedName("tradeType") val tradeType: String?,
    @SerializedName("sbHtml") val sbHtml: String?,
    @SerializedName("mwebUrl") val mwebUrl: String?,
    @SerializedName("paymentOrderNo") val paymentOrderNo: String?,
)
