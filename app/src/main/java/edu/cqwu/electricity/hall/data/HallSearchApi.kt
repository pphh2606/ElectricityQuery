package edu.cqwu.electricity.hall.data

import android.util.Log
import com.google.gson.Gson
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * 大厅搜索接口封装。
 *
 * 复用 serviceCenterData.json：
 * - 无 labels/searchKey：返回全部应用与索引标签
 * - 带 labels：按服务角色/服务类别过滤
 * - 带 searchKey：按关键字搜索
 */
class HallSearchApi {

    companion object {
        const val SERVICE_CENTER_DATA_URL = "https://ehall.cqwu.edu.cn/jsonp/serviceCenterData.json"
    }

    private val gson = Gson()
    private val client get() = HttpClientFactory.shared

    suspend fun fetch(
        searchKey: String,
        labels: List<String>,
    ): Result<ServiceCenterSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(SERVICE_CENTER_DATA_URL)
                append("?containLabels=true")
                labels.forEach { labelId ->
                    append("&labels=").append(URLEncoder.encode(labelId, "UTF-8"))
                }
                append("&searchKey=").append(URLEncoder.encode(searchKey, "UTF-8"))
                append("&_=").append(System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://ehall.cqwu.edu.cn/new/index.html?browser=no")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            Log.d("HallSearchApi", "GET $url")
            val response = client.newCall(request).execute()
            val body = response.body.string()
            val result = gson.fromJson(body, ServiceCenterSearchResponse::class.java)

            if (!result.hasLogin) {
                Log.w("HallSearchApi", "用户未登录（hasLogin=false）")
                throw SessionExpiredException("用户未登录")
            }

            Result.success(result)
        } catch (e: SessionExpiredException) {
            Log.w("HallSearchApi", "Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("HallSearchApi", "搜索接口请求失败", e)
            Result.failure(e)
        }
    }
}
