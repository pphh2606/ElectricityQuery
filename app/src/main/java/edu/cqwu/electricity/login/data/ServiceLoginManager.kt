package edu.cqwu.electricity.login.data

import android.util.Log
import edu.cqwu.electricity.payment.data.HttpClientFactory
import edu.cqwu.electricity.login.data.CookieStore
import edu.cqwu.electricity.login.data.CookieStoreOkHttpJar
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.login.data.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * 统一的服务授权管理器（Step 2）。
 *
 * 负责使用 CAS CASTGC Cookie 获取各第三方服务的 session Cookie。
 * 与 auth/ 中的 SessionManager（Step 1 认证验证）职责分离。
 *
 * 新版 IAP 认证流程（手动逐步跟踪 302 重定向链）：
 *   1. GET 服务受保护页面 → 302 → /iap/login?service=...
 *   2. GET /iap/login → 302 → authserver/login?service=.../iap/loginSuccess?sessionToken=xxx
 *   3. GET authserver/login（携带 CASTGC）→ 302 → /iap/loginSuccess?ticket=ST-xxx
 *   4. GET /iap/loginSuccess → 302 → /index.html?ticket=ST-iap:xxx
 *   5. GET /index.html?ticket=ST-iap:xxx → 200 + Set-Cookie: MOD_AUTH_CAS
 *
 * 使用 followRedirects=false 的客户端，手动控制每一步重定向，
 * 避免被 IAP 的 JS 中间页面（如 api.campushoy.com）阻断。
 */
object ServiceLoginManager {

    private const val TAG = "ServiceLoginManager"
    private const val MAX_REDIRECTS = 10

    /** JS 重定向检测正则（防御性措施，HAR 显示 IAP 流程全部是 HTTP 302） */
    private val JS_REDIRECT_REGEX = Regex(
        """(?:window\.location(?:\.href)?|location\.href)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 确保指定服务的登录态有效。
     *
     * 通过手动逐步跟踪 302 重定向链完成 CAS + IAP ticket 交换。
     * 使用 followRedirects=false 的客户端，Cookie 通过 CookieStoreOkHttpJar 桥接到系统 CookieManager。
     *
     * @param protectedUrl 服务的受保护页面 URL
     * @param serviceDomain 服务域名（可选，用于验证 Cookie 是否获取成功）
     * @param expectedCookie 期望获得的 Cookie 名（可选，如 "MOD_AUTH_CAS"）
     * @throws SessionExpiredException 如果 CASTGC 无效或 ticket 交换失败
     * @throws IOException 如果网络错误或重定向异常
     */
    fun ensureLogin(
        protectedUrl: String,
        serviceDomain: String? = null,
        expectedCookie: String? = null,
    ) {
        Log.d(TAG, ">>> 确保服务登录: $protectedUrl")

        // 创建禁用自动重定向的客户端，但保持 Cookie 桥接
        val client = HttpClientFactory.createNoRedirect(CookieStoreOkHttpJar)

        var currentUrl = protectedUrl
        var redirectCount = 0

        redirectLoop@ while (redirectCount < MAX_REDIRECTS) {
            Log.d(TAG, "重定向链 Step ${redirectCount + 1}: ${currentUrl.take(100)}")

            val request = Request.Builder()
                .url(currentUrl)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body.string()

                when (response.code) {
                    in 300..399 -> {
                        // 处理 HTTP 重定向
                        val location = response.header("Location")
                        if (location.isNullOrEmpty()) {
                            throw IOException("HTTP ${response.code} 重定向但无 Location header: $currentUrl")
                        }
                        currentUrl = resolveUrl(currentUrl, location)
                        redirectCount++
                        Log.d(TAG, "  → ${response.code} → ${currentUrl.take(100)}")
                        // use 块自然结束，while 循环继续
                    }
                    in 200..299 -> {
                        // 非重定向响应
                        // 检查是否为 CAS 登录页（CASTGC 无效）
                        if (SessionManager.isCasLoginPage(body)) {
                            Log.w(TAG, "检测到 CAS 登录页，CASTGC 无效")
                            throw SessionExpiredException("会话已过期，请重新登录")
                        }
                        // 检查是否为 JS 重定向（防御性措施）
                        val jsRedirect = extractJsRedirect(body)
                        if (jsRedirect != null) {
                            currentUrl = resolveUrl(currentUrl, jsRedirect)
                            redirectCount++
                            Log.d(TAG, "  → JS redirect → ${currentUrl.take(100)}")
                            return@use // 退出 use 块，while 循环继续
                        }
                        // 真正的最终页面 → 跳出 while 循环
                        Log.d(TAG, "重定向链完成: code=${response.code}, url=${currentUrl.take(100)}")
                        break@redirectLoop
                    }
                    else -> {
                        // 4xx/5xx：重定向链可能已在之前的步骤中完成了票据交换
                        // （如 ehall /appshow 返回 404，但 JSESSIONID 已在 302 链中设置）
                        Log.w(TAG, "重定向链终止于 HTTP ${response.code}: $currentUrl")
                        break@redirectLoop
                    }
                }
            }
        }

        // 检查是否超出最大重定向次数
        if (redirectCount >= MAX_REDIRECTS) {
            throw IOException("重定向次数超过上限 $MAX_REDIRECTS: $currentUrl")
        }

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

    /**
     * 解析重定向 URL，支持绝对 URL、相对路径和协议相对 URL。
     */
    private fun resolveUrl(baseUrl: String, location: String): String {
        val baseHttpUrl = baseUrl.toHttpUrl()
        return baseHttpUrl.resolve(location)?.toString()
            ?: throw IOException("无法解析重定向 URL: $location (base: $baseUrl)")
    }

    /**
     * 从 HTML 响应中提取 JS 重定向目标 URL（防御性措施）。
     * 匹配 window.location.href / window.location.replace 等模式。
     */
    private fun extractJsRedirect(html: String): String? {
        return JS_REDIRECT_REGEX.find(html)?.groupValues?.getOrNull(1)
    }
}
