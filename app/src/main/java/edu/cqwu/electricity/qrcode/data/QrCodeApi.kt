package edu.cqwu.electricity.qrcode.data

import android.util.Log
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 二维码类型：支付码 / 乘车码
 */
enum class QrCodeType {
    PAY,
    BUS
}

/**
 * 获取乘车码/支付码的数据 API
 *
 * 复用 HttpClientFactory.shared 单例的 OkHttpClient（与 CasAuthApi 完全共享同一 CookieJar），
 * 通过 GET 请求获取二维码页面，解析 HTML 提取 <input id="myText"> 的 value。
 *
 * 利用 OkHttp 的 followRedirects=true 自动完成 epay 会话初始化：
 *   1. 首次请求无 JSESSIONID → epay 重定向到 CAS
 *   2. CAS 检测到现有 CASTGC → 自动授权 → 回调 epay
 *   3. epay 下发已认证的 JSESSIONID → 返回二维码页面
 *
 * 对应 Python 参考登录流程中的 extract_qrcode() 和 extract_bus_qrcode()。
 */
class QrCodeApi {

    companion object {
        /** 支付码页面 URL */
        const val TARGET_URL = "http://218.194.176.214:8382/epay/thirdconsume/qrcode"
        /** 乘车码页面 URL */
        const val BUS_TARGET_URL = "http://218.194.176.214:8382/epay/thirdconsume/busqrcode"
    }

    /**
     * 获取二维码字符串
     * @param type 二维码类型（支付码/乘车码）
     * @return Result<String> 二维码字符串，如 "84858613|123456|20260428|..."
     */
    suspend fun fetchQrCode(type: QrCodeType): Result<String> = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()

            val url = when (type) {
                QrCodeType.PAY -> TARGET_URL
                QrCodeType.BUS -> BUS_TARGET_URL
            }

            Log.d("QrCodeApi", "GET $url")
            // 使用 HttpClientFactory.shared 的同一个 OkHttpClient 实例
            // 该 client 包含登录后的 CASTGC Cookie（由 CookieStoreOkHttpJar 桥接系统 CookieManager），
            // 首次请求 epay 时通过 followRedirects 自动完成 ticket 交换获取 JSESSIONID
            val response = HttpClientFactory.shared.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()
            val tHttp = System.currentTimeMillis()
            Log.d("QrCodeApi_DEBUG", "fetchQrCode 网络耗时: ${tHttp - t0}ms, 响应状态=${response.code}, HTML长度=${html.length}")

            // 检查是否被重定向到 CAS 登录页（Cookie 过期）
            SessionManager.checkSessionOrThrow(html)

            // 解析 HTML，提取 <input id="myText"> 的 value
            val value = extractInputValueById(html, "myText")
                ?: throw RuntimeException("获取二维码失败：页面中未找到二维码数据")
            val tParse = System.currentTimeMillis()
            Log.d("QrCodeApi_DEBUG", "fetchQrCode 解析耗时: ${tParse - tHttp}ms")
            Log.d("QrCodeApi_DEBUG", "fetchQrCode 总耗时: ${tParse - t0}ms, 类型=${type.name}")

            Log.d("QrCodeApi", "二维码字符串: $value")
            Result.success(value)
        } catch (e: SessionExpiredException) {
            Log.w("QrCodeApi", "Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("QrCodeApi", "获取二维码失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从 HTML 中提取 <input id="id"> 的 value 属性
     */
    private fun extractInputValueById(html: String, id: String): String? {
        // 匹配 <input ... id="myText" ... value="..." ...>
        val pattern1 = Regex("""<input[^>]*\sid\s*=\s*["']$id["'][^>]*\svalue\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(html)
        if (match1 != null) return match1.groupValues[1]

        // 匹配 <input ... value="..." ... id="myText" ...>
        val pattern2 = Regex("""<input[^>]*\svalue\s*=\s*["']([^"']*)["'][^>]*\sid\s*=\s*["']$id["']""", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(html)
        if (match2 != null) return match2.groupValues[1]

        return null
    }
}

