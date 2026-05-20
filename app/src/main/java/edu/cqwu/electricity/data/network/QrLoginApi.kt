package edu.cqwu.electricity.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 扫码登录 API
 *
 * 基于抓包数据，封装扫码登录所需的 5 个步骤：
 * 1. 获取登录页 HTML，解析 lt/execution
 * 2. 获取二维码 UUID
 * 3. 获取二维码图片 URL
 * 4. 轮询扫码状态
 * 5. 提交认证获取 CASTGC
 * 6. （新增）用获取到的 CASTGC 从 /authserver/index.do 提取学号和实名
 *
 * 使用独立的 OkHttpClient + UserCookieStore，与全局 CookieManager 完全隔离，
 * 不携带任何已登录用户的 Cookie（隐私模式），防止因已有 CASTGC 导致扫码登录页被重定向。
 * 登录成功后，将 CASTGC 从独立存储导入到全局 CookieManager，使登录结果生效。
 */
class QrLoginApi {

    /** 扫码登录页面 URL */
    companion object {
        private const val QR_LOGIN_URL = "https://authserver.cqwu.edu.cn/authserver/login?display=qrLogin"
        private const val QR_CODE_GET_URL = "https://authserver.cqwu.edu.cn/authserver/qrCode/get"
        private const val QR_CODE_CODE_URL = "https://authserver.cqwu.edu.cn/authserver/qrCode/code"
        private const val QR_CODE_STATUS_URL = "https://authserver.cqwu.edu.cn/authserver/qrCode/status"
    }

    // ═══ 独立 Cookie 存储 & OkHttpClient（隐私模式）═══

    /** 独立的 Cookie 存储，与全局 CookieManager 完全隔离 */
    private val cookieStore = UserCookieStore()

    /** 使用独立 CookieJar 的 OkHttpClient，不携带任何已有登录信息 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .cookieJar(UserAwareCookieJar(cookieStore))
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(UserAgentInterceptor)
        .build()

    // ═══════════════════════════════════════════

    /**
     * ① 获取扫码登录页面，解析隐藏的 lt 和 execution 参数。
     */
    suspend fun fetchLoginPage(): Result<LoginPageData> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder()
                    .url(QR_LOGIN_URL)
                    .get()
                    .build()
            ).execute()

            val html = response.body?.string()
                ?: throw RuntimeException("获取扫码登录页面失败：响应体为空")

            // 解析 lt (Login Ticket)
            val lt = extractInputValue(html, "lt")
                ?: throw RuntimeException("无法从扫码登录页解析 lt")

            // 解析 execution (Spring Web Flow 状态)
            val execution = extractInputValue(html, "execution")
                ?: throw RuntimeException("无法从扫码登录页解析 execution")

            Result.success(LoginPageData(lt, execution))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ② 获取二维码 UUID。
     */
    suspend fun fetchQrCodeUuid(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ts = System.currentTimeMillis()
            val response = client.newCall(
                Request.Builder()
                    .url("$QR_CODE_GET_URL?ts=$ts")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Accept", "text/plain, */*; q=0.01")
                    .get()
                    .build()
            ).execute()

            val uuid = response.body?.string()?.trim()
                ?: throw RuntimeException("获取二维码 UUID 失败：响应体为空")

            if (uuid.isBlank()) {
                throw RuntimeException("获取二维码 UUID 失败：UUID 为空")
            }

            Result.success(uuid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ③ 获取二维码图片 URL（保留供外部使用）。
     */
    fun getQrCodeImageUrl(uuid: String): String {
        return "$QR_CODE_CODE_URL?uuid=$uuid"
    }

    /**
     * ③′ 下载二维码图片并解码为二维码内容字符串。
     *
     * 使用 ZXing 解码从服务端获取的二维码图片，
     * 将图片内容解析为文本字符串，供本地 QrCodeView 渲染。
     */
    suspend fun downloadAndDecodeQrCode(uuid: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder()
                    .url(getQrCodeImageUrl(uuid))
                    .get()
                    .build()
            ).execute()

            val bytes = response.body?.bytes()
                ?: throw RuntimeException("下载二维码图片失败：响应体为空")

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw RuntimeException("二维码图片解码失败：Bitmap 为空")

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            val decodedText = result.text

            if (decodedText.isBlank()) {
                throw RuntimeException("二维码解码结果为空")
            }

            android.util.Log.d("QrLoginApi", "二维码解码成功: $decodedText")
            Result.success(decodedText)
        } catch (e: Exception) {
            android.util.Log.e("QrLoginApi", "下载并解码二维码失败", e)
            Result.failure(e)
        }
    }

    /**
     * ④ 轮询扫码状态。
     *
     * @return "0"=等待扫码, "1"=已确认(可提交认证), "2"=已扫码(待确认), "3"=过期
     */
    suspend fun pollQrCodeStatus(uuid: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ts = System.currentTimeMillis()
            val response = client.newCall(
                Request.Builder()
                    .url("$QR_CODE_STATUS_URL?ts=$ts&uuid=$uuid")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Accept", "text/plain, */*; q=0.01")
                    .get()
                    .build()
            ).execute()

            val status = response.body?.string()?.trim()
                ?: throw RuntimeException("获取扫码状态失败：响应体为空")

            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ⑤ 提交扫码认证。
     *
     * 手机端确认后，提交 lt + uuid 参数，CAS 服务器返回 302 + Set-Cookie: CASTGC=xxx。
     *
     * ⚠️ 注意：必须禁用 followRedirects（使用独立的临时客户端），
     * 因为 CAS 的重定向链（POST→302→index.do→302→...→j_spring_cas_security_check）
     * 最后一步耗时可能超过 15 秒，导致 readTimeout 超时异常。
     * 我们只需要第一个 302 响应的 Set-Cookie: CASTGC，无需跟随重定向。
     *
     * 获取到 CASTGC 后，会自动查询 /authserver/index.do 提取学号和实名，
     * 并将 CASTGC 保存到 AccountManager，确保后续智能切换可用。
     */
    suspend fun submitQrLogin(lt: String, uuid: String, execution: String): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("lt", lt)
                .add("uuid", uuid)
                .add("dllt", "qrLogin")
                .add("execution", execution)
                .add("_eventId", "submit")
                .add("rmShown", "1")
                .build()

            // 使用独立的临时客户端（禁用 followRedirects），只请求第一个 302 响应
            // 避免 CAS 重定向链最后一步超时（>15s）
            val submitClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(UserAgentInterceptor)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(UserAwareCookieJar(cookieStore))
                .followRedirects(false)       // ← 关键：不跟随重定向
                .followSslRedirects(false)
                .build()

            val response = submitClient.newCall(
                Request.Builder()
                    .url(QR_LOGIN_URL)
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()
            ).execute()

            // CAS 服务器返回 302 重定向，同时设置 CASTGC Cookie
            val location = response.header("Location") ?: ""
            android.util.Log.d("QrLoginApi", "提交认证响应: code=${response.code}, Location=$location")

            // 从独立 cookieStore 中提取 CASTGC（由 UserAwareCookieJar 自动从 Set-Cookie 保存到 store）
            val castgc = getCookieValueFromStore(
                cookieStore, "https://authserver.cqwu.edu.cn", "CASTGC"
            )

            if (castgc == null) {
                throw RuntimeException("扫码登录失败：未能获取到 CASTGC Cookie")
            }

            // ═══ 将私有 cookieStore 中的所有 Cookie 导入到全局 CookieManager ═══
            // 注意：必须复制所有 Cookie（route、JSESSIONID、CASTGC、CASPRIVACY 等），
            // 不能只复制 CASTGC。CookieManager.setCookie() 一次只接受一个 Cookie，
            // 所以需要从 getAllCookies() 逐个复制。
            val allCookiesMap = cookieStore.getAllCookies()
            for ((domain, domainCookies) in allCookiesMap) {
                for ((cookieName, cookieValue) in domainCookies) {
                    CookieStore.setCookie(domain, "$cookieName=$cookieValue")
                }
            }

            // ═══ 提取用户信息并保存到 AccountManager ═══
            // 使用同一个独立 client（含全部 Cookie）请求 /authserver/index.do
            var username = ""
            try {
                val indexResponse = client.newCall(
                    Request.Builder()
                        .url("https://authserver.cqwu.edu.cn/authserver/index.do?locale=zh_CN")
                        .get()
                        .build()
                ).execute()

                val html = indexResponse.body?.string()
                if (html != null) {
                    val usernameRegex = Regex("""data-name="id">([^<]+)</div>""")
                    val extracted = usernameRegex.find(html)?.groupValues?.getOrNull(1)
                    if (extracted != null) {
                        username = extracted
                        // 提取实名（仅用于日志）
                        val nameRegex = Regex("""data-name="name">([^<]+)</div>""")
                        val realName = nameRegex.find(html)?.groupValues?.getOrNull(1) ?: ""
                        android.util.Log.d("QrLoginApi",
                            "扫码登录: 学号=$username, 实名=$realName")

                        // ★★★ 拷贝私有 cookieStore 中所有 Cookie 到 AccountManager ★★★
                        // 使用 getAllCookies() 遍历所有域名和所有 Cookie，逐个复制
                        val userStore = AccountManager.getCookiesForUser(username)
                        val allCookies = cookieStore.getAllCookies()
                        for ((domain, domainCookies) in allCookies) {
                            for ((cookieName, cookieValue) in domainCookies) {
                                userStore.setCookie(domain, "$cookieName=$cookieValue")
                            }
                        }
                        // 切换到该用户的 Cookie 环境
                        AccountManager.switchToUser(username)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("QrLoginApi", "提取用户信息失败（不影响登录本身）", e)
            }

            val cookieString = cookieStore.getCookie("https://authserver.cqwu.edu.cn") ?: ""
            android.util.Log.d("QrLoginApi", "扫码登录成功! CASTGC=$castgc, username=$username")

            Result.success(LoginResult(
                username = username,
                cookieString = cookieString
            ))
        } catch (e: Exception) {
            android.util.Log.e("QrLoginApi", "扫码登录失败", e)
            Result.failure(e)
        }
    }

    /**
     * 暴露内部的独立 cookieStore，供外部在扫码登录成功后
     * 将 CASTGC 导入到 AccountManager 的该用户 Cookie 存储。
     */
    fun getCookieStore(): UserCookieStore = cookieStore

    // ==================== 辅助方法 ====================

    /**
     * 从 UserCookieStore 中解析指定名称的 Cookie 值
     */
    private fun getCookieValueFromStore(store: UserCookieStore, url: String, name: String): String? {
        val cookieString = store.getCookie(url) ?: return null
        val prefix = "$name="
        return cookieString.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
    }

    /**
     * 从 HTML 中提取 <input name="name"> 的 value 属性
     */
    private fun extractInputValue(html: String, name: String): String? {
        val pattern1 = Regex("""<input[^>]*\sname\s*=\s*["']$name["'][^>]*\svalue\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(html)
        if (match1 != null) return match1.groupValues[1]

        val pattern2 = Regex("""<input[^>]*\svalue\s*=\s*["']([^"']*)["'][^>]*\sname\s*=\s*["']$name["']""", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(html)
        if (match2 != null) return match2.groupValues[1]

        return null
    }
}

/**
 * 扫码登录页面数据：包含隐藏表单参数
 */
data class LoginPageData(
    val lt: String,
    val execution: String,
)
