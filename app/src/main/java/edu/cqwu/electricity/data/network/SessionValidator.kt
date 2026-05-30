package edu.cqwu.electricity.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * CAS 会话验证结果：学号和实名
 *
 * @param username 学号，如 "2024XXXX0000"
 * @param realName 实名，如 "示例用户"
 */
data class CasUserInfo(
    val username: String,
    val realName: String,
)

/**
 * Cookie 验证结果密封类。
 * 区分「有效」「Cookie 过期」「网络错误」三种情况。
 */
sealed class SessionValidationResult {
    data class Valid(val info: CasUserInfo) : SessionValidationResult()
    data object Invalid : SessionValidationResult()
    data class NetworkError(val message: String) : SessionValidationResult()
}

/**
 * Cookie 会话验证器。
 *
 * 使用 GET /authserver/index.do 验证 CASTGC 是否有效，
 * 同时从返回的个人设置页 HTML 中提取学号和实名信息。
 *
 * 原理：
 * - 有效 Cookie → 返回 200 + 个人设置页 HTML（含 data-name="id" 学号、data-name="name" 姓名）
 * - 无效 Cookie → 被重定向回 CAS 登录页 HTML（含 casLoginForm / pwdDefaultEncryptSalt 特征）
 *
 * 参考抓包数据：
 * ```html
 * <div class="index-nav-name" data-name="name">示例用户</div>
 * <div class="index-nav-id" data-name="id">2024XXXX0000</div>
 * ```
 */
object SessionValidator {

    private const val INDEX_URL =
        "https://authserver.cqwu.edu.cn/authserver/index.do?locale=zh_CN"

    /** 复用的 OkHttpClient 基础配置，cookieJar 通过 newBuilder() 动态替换 */
    private val baseClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(UserAgentInterceptor)
            .build()
    }

    /** 预编译正则，避免每次调用临时构造 */
    private val USERNAME_REGEX = Regex("""data-name="id">([^<]+)</div>""")
    private val REAL_NAME_REGEX = Regex("""data-name="name">([^<]+)</div>""")

    /**
     * 验证指定用户的 Cookie 是否有效。
     *
     * @param userStore 该用户的 [UserCookieStore]（可能为空，会从系统 [CookieManager] 兜底读取）
     * @return [SessionValidationResult.Valid]（Cookie 有效）、
     *         [SessionValidationResult.Invalid]（Cookie 过期）、
     *         [SessionValidationResult.NetworkError]（网络异常）
     */
    suspend fun validate(userStore: UserCookieStore): SessionValidationResult = withContext(Dispatchers.IO) {
        try {
            // === 兜底：如果 UserCookieStore 为空，尝试从系统 CookieManager 导入 ===
            val existingCookie = userStore.getCookie("https://authserver.cqwu.edu.cn")
            if (existingCookie.isNullOrBlank()) {
                android.util.Log.d("SessionValidator", "UserCookieStore 为空，尝试从系统 CookieManager 兜底")
                userStore.syncFromCookieManager()
                val afterSync = userStore.getCookie("https://authserver.cqwu.edu.cn")
                if (afterSync != null) {
                    android.util.Log.d("SessionValidator", "从系统 CookieManager 兜底成功")
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
            if (SessionChecker.isCasLoginPage(html)) {
                android.util.Log.d("SessionValidator", "Cookie 无效：响应为 CAS 登录页")
                return@withContext SessionValidationResult.Invalid
            }

            // 提取学号：data-name="id">学号</div>
            val username = USERNAME_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?: run {
                    android.util.Log.w("SessionValidator", "无法从 index.do 提取学号，HTML长度=${html.length}")
                    return@withContext SessionValidationResult.Invalid
                }

            // 提取实名：data-name="name">姓名</div>
            val realName = REAL_NAME_REGEX.find(html)?.groupValues?.getOrNull(1) ?: ""

            android.util.Log.d("SessionValidator", "Cookie 有效！学号=$username, 实名=$realName")

            SessionValidationResult.Valid(CasUserInfo(username = username, realName = realName))
        } catch (e: SocketTimeoutException) {
            android.util.Log.w("SessionValidator", "验证 Cookie 网络超时", e)
            SessionValidationResult.NetworkError("网络超时，请检查网络连接")
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.w("SessionValidator", "验证 Cookie DNS 解析失败", e)
            SessionValidationResult.NetworkError("无法连接服务器，请检查网络")
        } catch (e: Exception) {
            android.util.Log.w("SessionValidator", "验证 Cookie 时发生异常", e)
            SessionValidationResult.NetworkError("网络异常: ${e.message}")
        }
    }
}
