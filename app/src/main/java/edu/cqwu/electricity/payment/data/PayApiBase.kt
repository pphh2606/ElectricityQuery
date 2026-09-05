package edu.cqwu.electricity.payment.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.lang.reflect.Type

/**
 * 支付 API 公共基类
 *
 * 提取 [edu.cqwu.electricity.electricity.data.ElectricityPayApi]、
 * [edu.cqwu.electricity.cardcenter.data.CardRechargeApi] 和
 * [edu.cqwu.electricity.feeservicehall.data.FeeServiceHallApi] 的公共逻辑：
 * - [PAY_DOMAIN] 常量
 * - [client] / [gson] 实例
 * - [buildBaseRequest] 请求构建（X-Token 从 [PaySessionManager.ensureToken] 获取，请求前保证 token 新鲜）
 * - [autoRetry] 自动重试（认证类失败时刷新 token 重试一次）
 */
abstract class PayApiBase {

    companion object {
        const val PAY_DOMAIN = "https://pay.cqwu.edu.cn"
    }

    protected val client = HttpClientFactory.payClient
    protected val gson = Gson()

    // ── 请求构建 ──

    /**
     * 构建 pay 业务请求。
     *
     * X-Token 通过 [PaySessionManager.ensureToken] 获取：本地 JWT `exp` 校验，
     * 未过期直接返回，过期/缺失自动走重定向链静默刷新 —— 保证业务请求携带的 token 始终新鲜。
     */
    protected suspend fun buildBaseRequest(url: String): Request.Builder {
        val xToken = PaySessionManager.ensureToken()
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", "$PAY_DOMAIN/")
            .addHeader("X-Requested-With", "edu.cqwu.electricity")
            .addHeader("X-Token", xToken)
    }

    // ── 自动重试 ──

    /**
     * 自动重试：请求失败且为**认证类失败**（token 被服务端拒绝/提前失效）时，
     * 清除本地 token、强制刷新后重试一次；网络类失败不刷新，直接返回。
     *
     * 说明：token 的常规过期（24 小时）已由请求前的 [PaySessionManager.ensureToken] 基于
     * JWT `exp` 本地判断并静默刷新，这里只兜底服务端提前失效的场景。
     * 刷新失败时**保留原始异常类型**（如 [SessionExpiredException]），使 UI 层能识别"需要重新登录"。
     */
    protected suspend fun <T> autoRetry(block: suspend () -> Result<T>): Result<T> {
        try {
            val result = block()
            if (result.isFailure && isAuthFailure(result)) {
                AppLog.w("PayApiBase", "认证类失败，强制刷新 token 后重试一次")
                PaySessionManager.clearToken()
                val refresh = runCatching { PaySessionManager.ensureToken() }
                if (refresh.isFailure) {
                    // 保留原始异常（如 SessionExpiredException），不吞成普通 Exception
                    return Result.failure(refresh.exceptionOrNull() ?: Exception("认证失败"))
                }
                return block()
            }
            return result
        } catch (e: CancellationException) {
            throw e
        }
    }

    /** 判断失败是否属于"认证类失败"（刷新 token 可能解决） */
    private fun isAuthFailure(result: Result<*>): Boolean {
        val e = result.exceptionOrNull() ?: return false
        if (e is SessionExpiredException) return true
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("http 401") || msg.contains("http 403")
            || msg.contains("未登录") || msg.contains("登录")
            || msg.contains("token") || msg.contains("认证")
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
