package edu.cqwu.electricity.campusnetwork.campusnetworkinfo.data
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkErrorKind
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkException
import edu.cqwu.electricity.campusnetwork.common.toCampusNetworkException

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 接入者信息接口封装（校园网测速站 client-context）。
 *
 * 接口特点：
 * - **无 Cookie / 无鉴权头**，"源 IP 即身份"：仅在"已连接校园网且完成上网认证
 *   （SAM 在线）"时可达；其他场景（公网 / 未认证）请求表现为连接超时/失败。
 * - 因此**不需要** App 登录态，也**不经过** WebVPN —— 使用独立无 Cookie 客户端，
 *   避免与其它业务域名（CookieJar / WebVPN 拦截）互相干扰。
 *
 * 错误处理约定：任何异常都归类为 [CampusNetworkException] 上抛，并在本层
 * 以 AppLog.e 记录完整堆栈，不静默吞掉。
 */
class CampusNetworkApi {

    companion object {
        /** 测速站 API 根地址 */
        private const val BASE_URL = "https://speedtest.cqwu.edu.cn/api/speedlyst"
        private const val CLIENT_CONTEXT_URL = "$BASE_URL/client-context"
        private const val TAG = "CampusNetworkApi"
    }

    private val gson = Gson()

    /**
     * 独立直连客户端：无 CookieJar、不启用 WebVPN 拦截，仍注入用户 UA。
     * 用 create() 而非 shared，避免将登录 Cookie 带给内网接口 / WebVPN 误转换。
     */
    private val client by lazy {
        HttpClientFactory.create(
            cookieJar = null,
            includeWebVpn = false,
        )
    }

    /**
     * 查询当前接入者信息。
     *
     * @return [Result.success] 携带 [ClientContextData]；失败为 [CampusNetworkException]
     */
    suspend fun fetchClientContext(): Result<ClientContextData> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CLIENT_CONTEXT_URL)
                .get()
                .header("Accept", "application/json")
                .build()

            AppLog.d(TAG, "GET $CLIENT_CONTEXT_URL")
            val response = client.newCall(request).execute()
            val body = response.use { it.body.string() }

            if (!response.isSuccessful) {
                AppLog.e(TAG, "接入者信息 HTTP 失败: ${response.code}")
                return@withContext Result.failure(
                    CampusNetworkException(
                        kind = CampusNetworkErrorKind.SERVER,
                        userMessage = "HTTP ${response.code}",
                        cause = null,
                    )
                )
            }

            val parsed = gson.fromJson(body, ClientContextResponse::class.java)
                ?: throw IllegalStateException("接入者信息响应为空")
            if (parsed.code != 0) {
                AppLog.e(TAG, "接入者信息业务失败: code=${parsed.code}, message=${parsed.message}")
                return@withContext Result.failure(
                    CampusNetworkException(
                        kind = CampusNetworkErrorKind.SERVER,
                        userMessage = parsed.message,
                        cause = null,
                    )
                )
            }
            val data = parsed.data
                ?: throw IllegalStateException("接入者信息 data 为空")
            Result.success(data)
        } catch (e: CancellationException) {
            // 协程取消必须继续向上抛，不能当作普通失败吞掉
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "接入者信息请求失败: ${e.message}", e)
            Result.failure(e.toCampusNetworkException())
        }
    }
}