package edu.cqwu.electricity.data.network.feeservicehall

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.data.network.common.CookieStore
import edu.cqwu.electricity.data.network.pay.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        Result.failure(ApiBusinessException(base.messageCode, base.message))
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

    private val client = HttpClientFactory.createWithTimeout(10, 10, 10)

    private val gson = Gson()
    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    companion object {
        private const val PROJECT_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/pay/cos/merchant/getProjectTypeAndProjectInfoListInit/datalook"
        private const val ORDER_LIST_URL =
            "https://pay.cqwu.edu.cn/api/pay/web/order/pageOrderlist"
        private const val PAY_DOMAIN = "https://pay.cqwu.edu.cn"
        private const val CAS_LOGIN_URL = "$PAY_DOMAIN/casLogin/"
        private const val XTOKEN_COOKIE_NAME = "datalook_reimbursement_token"

        fun buildPaymentUrl(proModelUrl: String?, projectId: String): String {
            val handler = proModelUrl?.let { "${it}Pay" } ?: "commonPay"
            return "https://pay.cqwu.edu.cn/mobile/#/$handler?projectId=$projectId"
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

        /**
         * 自动获取 pay.cqwu.edu.cn 的 JWT Token（datalook_reimbursement_token）。
         *
         * 按需触发完整的 CAS Ticket 交换 → dlyscas 签发链：
         *   1. GET /casLogin/ → 解析 authserver 重定向 URL
         *   2. GET authserver（携带 CASTGC）→ 自动跟随 302 完成 ticket 交换 → 获得 JSESSIONID
         *   3. GET /casLogin/（携带 JSESSIONID）→ 解析 dlyscas 重定向 URL（含 idserial）
         *   4. GET dlyscas 端点 → 从 302 Location 头中提取 JWT Token
         *   5. 将 Token 写入 CookieManager
         *
         * @return Result<String> JWT Token 字符串
         */
        suspend fun obtainPayToken(): Result<String> = withContext(Dispatchers.IO) {
            try {
                Log.d("FeeServiceHallApi", ">>> 自动获取 pay JWT Token 开始")

                // ── 步骤1: 访问 /casLogin/，获取 authserver CAS 登录地址 ──
                val step1Resp = HttpClientFactory.shared.newCall(
                    Request.Builder().url(CAS_LOGIN_URL).get().build()
                ).execute()
                val step1Html = step1Resp.body.string()
                Log.d("FeeServiceHallApi", "步骤1: /casLogin/ 响应码=${step1Resp.code}, HTML长度=${step1Html.length}")

                // 从 HTML 中提取 location.href 重定向地址
                val locationRegex = Regex("""location\.href\s*=\s*['"]([^'"]+)['"]""")
                val redirectUrl = locationRegex.find(step1Html)?.groupValues?.getOrNull(1)
                    ?: return@withContext Result.failure(Exception("无法从 /casLogin/ 解析重定向地址"))
                Log.d("FeeServiceHallApi", "步骤1: 解析到重定向地址=$redirectUrl")

                // ── 步骤2: 访问 authserver CAS 登录页（携带 CASTGC）──
                // HttpClientFactory.shared 带有 CookieStoreOkHttpJar + followRedirects=true，
                // 会自动完成重定向链:
                //   authserver(302+ticket) → /casLogin/?ticket=...(302+JSESSIONID) → /casLogin/(200)
                // 最终 /casLogin/ 返回 JS 重定向到 dlyscas 端点
                val step2Client = HttpClientFactory.shared.newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val step2Resp = step2Client.newCall(
                    Request.Builder().url(redirectUrl).get().build()
                ).execute()
                val step2Html = step2Resp.body.string()
                Log.d("FeeServiceHallApi", "步骤2: CAS认证完成, 响应码=${step2Resp.code}, HTML长度=${step2Html.length}, finalUrl=${step2Resp.request.url}")

                // 从 HTML 中提取 dlyscas 端点地址（含 idserial）
                val dlyscasUrl = locationRegex.find(step2Html)?.groupValues?.getOrNull(1)
                    ?: return@withContext Result.failure(Exception("无法从 CAS 响应解析 dlyscas 地址"))
                Log.d("FeeServiceHallApi", "步骤2: 解析到 dlyscas 地址=$dlyscasUrl")

                // ── 步骤3: 访问 dlyscas 端点，获取 JWT Token ──
                // 必须禁用 followRedirects，因为 302 的 Location 中包含 token 参数
                val step3Client = HttpClientFactory.shared.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                val step3Resp = step3Client.newCall(
                    Request.Builder().url(dlyscasUrl).get().build()
                ).execute()
                val location = step3Resp.header("Location") ?: ""
                Log.d("FeeServiceHallApi", "步骤3: dlyscas 响应码=${step3Resp.code}, Location=$location")

                // 从 Location 中提取 token 参数
                val tokenRegex = Regex("""token=([^&]+)""")
                val token = tokenRegex.find(location)?.groupValues?.getOrNull(1)
                    ?: return@withContext Result.failure(Exception("无法从 dlyscas 响应中提取 JWT Token"))

                // URL 解码 token（JWT 可能包含 URL 编码字符）
                val decodedToken = URLDecoder.decode(token, "UTF-8")
                Log.d("FeeServiceHallApi", ">>> JWT Token 获取成功, 长度=${decodedToken.length}")

                // ── 步骤4: 将 Token 写入 CookieManager ──
                CookieStore.setCookie(PAY_DOMAIN, "$XTOKEN_COOKIE_NAME=$decodedToken")
                CookieStore.setCookie("$PAY_DOMAIN/", "$XTOKEN_COOKIE_NAME=$decodedToken")
                CookieStore.setCookie("$PAY_DOMAIN/casLogin/", "$XTOKEN_COOKIE_NAME=$decodedToken")
                CookieStore.setCookie(PAY_DOMAIN, "datalook_login_status=false")

                Result.success(decodedToken)
            } catch (e: SocketTimeoutException) {
                Log.e("FeeServiceHallApi", ">>> 获取 JWT Token 超时", e)
                Result.failure(Exception("获取 Token 超时，请检查网络连接", e))
            } catch (e: Exception) {
                Log.e("FeeServiceHallApi", ">>> 获取 JWT Token 失败", e)
                Result.failure(e)
            }
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
}
