package edu.cqwu.electricity.data.network.sso

import android.util.Log
import edu.cqwu.electricity.data.network.pay.HttpClientFactory
import edu.cqwu.electricity.data.network.common.CookieStore
import edu.cqwu.electricity.data.network.auth.SessionExpiredException
import okhttp3.Request

/**
 * 统一的服务授权管理器（Step 2）。
 *
 * 负责使用 CAS CASTGC Cookie 获取各第三方服务的 session Cookie。
 * 与 auth/ 中的 SessionManager（Step 1 认证验证）职责分离。
 *
 * 标准 CAS ticket 交换流程：
 *   1. GET 服务受保护页面 URL（携带 CASTGC）
 *   2. 服务发现无 session → 302 到 authserver/login?service=URL
 *   3. CAS 检测到 CASTGC → 签发 Service Ticket → 302 回 URL?ticket=ST-xxx
 *   4. 服务验证 ticket → 下发 session Cookie
 *
 * 只要 OkHttpClient 配置了 followRedirects=true 且携带 CASTGC，
 * 整个流程由 OkHttp 自动完成。
 */
object ServiceLoginManager {

    private const val TAG = "ServiceLoginManager"

    /**
     * 确保指定服务的登录态有效。
     *
     * 通过访问服务的受保护页面触发 CAS ticket 交换。
     * 使用 HttpClientFactory.shared（自动携带 CASTGC + followRedirects=true）。
     *
     * @param protectedUrl 服务的受保护页面 URL
     * @param serviceDomain 服务域名（可选，用于验证 Cookie 是否获取成功）
     * @param expectedCookie 期望获得的 Cookie 名（可选，如 "MOD_AUTH_CAS"）
     * @throws SessionExpiredException 如果 CASTGC 无效或 ticket 交换失败
     */
    fun ensureLogin(
        protectedUrl: String,
        serviceDomain: String? = null,
        expectedCookie: String? = null,
    ) {
        Log.d(TAG, ">>> 确保服务登录: $protectedUrl")

        val client = HttpClientFactory.shared
        val resp = client.newCall(
            Request.Builder()
                .url(protectedUrl)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()
        ).execute()

        resp.body.string()  // 消费响应体
        val finalUrl = resp.request.url.toString()
        Log.d(TAG, "服务登录完成: code=${resp.code}, finalUrl=${finalUrl.take(100)}")

        // 可选验证：检查是否获得了预期的 session Cookie
        if (serviceDomain != null && expectedCookie != null) {
            val cookies = CookieStore.getCookie(serviceDomain)
            val hasCookie = cookies?.contains("$expectedCookie=") == true
            Log.d(TAG, "Cookie 验证: $expectedCookie=$hasCookie")

            if (!hasCookie) {
                Log.w(TAG, "服务授权失败: $serviceDomain 未获得 $expectedCookie")
                throw SessionExpiredException("服务授权失败，请重新登录")
            }
        }
    }
}
