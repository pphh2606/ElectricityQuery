package edu.cqwu.electricity.payment.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.feeservicehall.data.FeeServiceHallApi
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.lang.reflect.Type

/**
 * 支付 API 公共基类
 *
 * 提取 [edu.cqwu.electricity.electricity.data.ElectricityPayApi] 和
 * [edu.cqwu.electricity.cardcenter.data.CardRechargeApi] 的公共逻辑：
 * - [PAY_DOMAIN] 常量
 * - [client] / [gson] 实例
 * - [buildBaseRequest] 请求构建
 * - [autoRetry] 自动重试（可通过 [defaultErrorMsg] 自定义错误消息）
 */
abstract class PayApiBase {

    companion object {
        const val PAY_DOMAIN = "https://pay.cqwu.edu.cn"
    }

    protected val client = HttpClientFactory.payClient
    protected val gson = Gson()

    /** Token 获取失败时的默认错误消息，子类可覆盖以自定义 */
    protected open val defaultErrorMsg: String = "未登录，请先完成认证"

    // ── 请求构建 ──

    protected fun buildBaseRequest(url: String): Request.Builder {
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
     * 保留 Phase 1 修复的 CancellationException 处理。
     */
    protected suspend fun <T> autoRetry(block: suspend () -> Result<T>): Result<T> {
        try {
            val result = block()
            if (result.isFailure && FeeServiceHallApi.getXToken().isNullOrBlank()) {
                val tokenResult = FeeServiceHallApi.obtainPayToken()
                if (tokenResult.isFailure) {
                    return Result.failure(Exception(defaultErrorMsg))
                }
                return block()
            }
            return result
        } catch (e: CancellationException) {
            throw e
        }
    }

    // ── 泛型响应解析 ──

    /**
     * 解析 [ApiResponse] 包装的 JSON 响应。
     *
     * @param body JSON 字符串
     * @param dataType data 字段的实际类型（如 `CardBasicInfo::class.java`）
     */
    internal fun <T> parseApiResponse(body: String, dataType: Type): ApiResponse<T> {
        val type = TypeToken.getParameterized(ApiResponse::class.java, dataType).type
        return gson.fromJson(body, type)
    }
}

// ═══════════════════════════════════════════
//  通用 API 响应包装
// ═══════════════════════════════════════════

/**
 * 统一的 API 响应包装类。
 *
 * 消除 CardRechargeModels / ElectricityPayModels 中多个结构完全相同的
 * `{ messageCode, message, data }` 包装类，统一使用 `ApiResponse<T>` 替代。
 */
internal data class ApiResponse<T>(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?,
)
