package edu.cqwu.electricity.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 统一的会话管理器。
 *
 * 合并了原 SessionValidator + SessionChecker + CampusphereApi 中的 CAS ticket 交换逻辑。
 * 提供以下能力：
 *
 * 1. **Cookie 验证**：[validateCookie] — 验证 CASTGC 是否有效，提取学号/实名
 * 2. **会话检测**：[isCasLoginPage] / [checkSessionOrThrow] — 检测响应是否为 CAS 登录页
 * 3. **CAS Ticket 交换**：[performCasTicketExchange] — 获取第三方服务的会话 Cookie
 *
 * 使用方式：
 *   // 验证 Cookie
 *   when (val result = SessionManager.validateCookie(userStore)) {
 *       is SessionValidationResult.Valid -> // Cookie 有效
 *       is SessionValidationResult.Invalid -> // 需要重新登录
 *       is SessionValidationResult.NetworkError -> // 网络异常
 *   }
 *
 *   // 检测会话过期
 *   if (SessionManager.isCasLoginPage(responseHtml)) { ... }
 *
 *   // CAS ticket 交换
 *   SessionManager.performCasTicketExchange(client, cookieReader)
 */
object SessionManager {

    private const val INDEX_URL =
        "https://authserver.cqwu.edu.cn/authserver/index.do?locale=zh_CN"

    private const val CAMPUSPHERE_INDEX_URL =
        "https://cqwu.campusphere.net/wec-counselor-stuinfo-apps/student/mobile/index.html"

    /** 复用的 OkHttpClient 基础配置，cookieJar 通过 newBuilder() 动态替换 */
    private val baseClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor)
            .build()
    }

    // ═══════════════════════════════════════════
    //  1. Cookie 验证
    // ═══════════════════════════════════════════

    /**
     * 验证指定用户的 Cookie 是否有效。
     *
     * 使用 GET /authserver/index.do 验证 CASTGC 是否有效，
     * 同时从返回的个人设置页 HTML 中提取学号和实名信息。
     *
     * 原理：
     * - 有效 Cookie → 返回 200 + 个人设置页 HTML（含 data-name="id" 学号、data-name="name" 姓名）
     * - 无效 Cookie → 被重定向回 CAS 登录页 HTML（含 casLoginForm / pwdDefaultEncryptSalt 特征）
     *
     * @param userStore 该用户的 [UserCookieStore]
     * @param syncFromSystem 是否在 UserCookieStore 为空时从系统 CookieManager 兜底导入。
     *                       启动验证时应为 true（系统 CookieManager 中有上次登录的 Cookie）；
     *                       账号切换时应为 false（系统 CookieManager 中是当前用户的 Cookie，不是目标用户的）。
     * @return [SessionValidationResult.Valid]（Cookie 有效）、
     *         [SessionValidationResult.Invalid]（Cookie 过期）、
     *         [SessionValidationResult.NetworkError]（网络异常）
     */
    suspend fun validateCookie(
        userStore: UserCookieStore,
        syncFromSystem: Boolean = true,
    ): SessionValidationResult = withContext(Dispatchers.IO) {
        try {
            // 兜底：如果 UserCookieStore 为空，尝试从系统 CookieManager 导入
            if (syncFromSystem) {
                val existingCookie = userStore.getCookie("https://authserver.cqwu.edu.cn")
                if (existingCookie.isNullOrBlank()) {
                    android.util.Log.d("SessionManager", "UserCookieStore 为空，从系统 CookieManager 兜底导入")
                    userStore.syncFromCookieManager()
                }
            }

            val client = baseClient.newBuilder()
                .cookieJar(UserAwareCookieJar(userStore))
                .build()

            val response = client.newCall(
                Request.Builder()
                    .url(INDEX_URL)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()

            // 检查是否是 CAS 登录页（Cookie 无效场景）
            if (HtmlFormParser.isCasLoginPage(html)) {
                android.util.Log.d("SessionManager", "Cookie 无效：响应为 CAS 登录页")
                return@withContext SessionValidationResult.Invalid
            }

            // 提取学号和实名
            val username = HtmlFormParser.extractUsername(html)
                ?: run {
                    android.util.Log.w("SessionManager", "无法从 index.do 提取学号，HTML长度=${html.length}")
                    return@withContext SessionValidationResult.Invalid
                }

            val realName = HtmlFormParser.extractRealName(html) ?: ""

            android.util.Log.d("SessionManager", "Cookie 有效！学号=$username, 实名=$realName")

            SessionValidationResult.Valid(CasUserInfo(username = username, realName = realName))
        } catch (e: SocketTimeoutException) {
            android.util.Log.w("SessionManager", "验证 Cookie 网络超时", e)
            SessionValidationResult.NetworkError("网络超时，请检查网络连接")
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.w("SessionManager", "验证 Cookie DNS 解析失败", e)
            SessionValidationResult.NetworkError("无法连接服务器，请检查网络")
        } catch (e: Exception) {
            android.util.Log.w("SessionManager", "验证 Cookie 时发生异常", e)
            SessionValidationResult.NetworkError("网络异常: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════
    //  2. 会话检测（委托 HtmlFormParser）
    // ═══════════════════════════════════════════

    /**
     * 判断响应 HTML 是否为 CAS 登录页。
     *
     * @param html HTTP 响应的 HTML 字符串
     * @return true 表示该页面是 CAS 登录页（Session 过期）
     */
    fun isCasLoginPage(html: String): Boolean = HtmlFormParser.isCasLoginPage(html)

    /**
     * 检查 HTML 是否为 CAS 登录页，是则抛出 [SessionExpiredException]。
     */
    fun checkSessionOrThrow(html: String) = HtmlFormParser.checkAndThrow(html)

    // ═══════════════════════════════════════════
    //  3. CAS Ticket 交换
    // ═══════════════════════════════════════════

    /**
     * 执行 CAS ticket 交换，获取 campusphere 域下的 MOD_AUTH_CAS Cookie。
     *
     * 流程：
     *   1. GET /student/mobile/index.html（未登录 → 302）
     *   2. 跟随 302 → authserver/login?service=...（携带已有 CASTGC）
     *   3. authserver 验证通过 → 302 + ticket → Set-Cookie: MOD_AUTH_CAS
     *   4. 最终回到 index.html（已认证）
     *
     * @param client OkHttpClient（需已携带 CASTGC Cookie）
     * @param cookieReader 从指定 URL 获取 Cookie 字符串的函数
     * @throws SessionExpiredException 如果 ticket 交换失败
     */
    fun performCasTicketExchange(client: OkHttpClient, cookieReader: (String) -> String?) {
        android.util.Log.d("SessionManager", ">>> 执行 CAS ticket 交换 >>>")

        val resp = client.newCall(
            Request.Builder()
                .url(CAMPUSPHERE_INDEX_URL)
                .addHeader("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()
        ).execute()

        // 消费响应体
        resp.body.string()
        val finalUrl = resp.request.url.toString()
        val code = resp.code
        android.util.Log.d("SessionManager", "CAS ticket 交换: code=$code, finalUrl=${finalUrl.take(100)}")

        // 检查是否获得了 MOD_AUTH_CAS
        val campusCookie = cookieReader("https://cqwu.campusphere.net")
        val hasModAuthCas = campusCookie?.contains("MOD_AUTH_CAS=") == true
        val hasJsessionid = campusCookie?.contains("JSESSIONID=") == true
        android.util.Log.d("SessionManager", "交换后 Cookie: MOD_AUTH_CAS=$hasModAuthCas, JSESSIONID=$hasJsessionid")

        if (!hasModAuthCas) {
            android.util.Log.w("SessionManager", "CAS ticket 交换失败，仍未获取到 MOD_AUTH_CAS")
            throw SessionExpiredException("校园信息会话已过期，请先登录")
        }
    }
}
