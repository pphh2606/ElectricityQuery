package edu.cqwu.electricity.ui.notice

import android.util.Log
import com.google.gson.Gson
import edu.cqwu.electricity.data.model.NoticeDetailQp
import edu.cqwu.electricity.data.model.NoticeDetailResponse
import edu.cqwu.electricity.data.model.NoticeItem
import edu.cqwu.electricity.data.model.NoticeResponse
import edu.cqwu.electricity.data.network.SharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 通知公告 API
 *
 * 使用 SharedHttpClient（与 CAS 登录共享 CookieJar），
 * 自动携带 _WEU、MOD_AUTH_CAS 等认证 Cookie。
 */
class NoticeApi {

    private val gson = Gson()

    companion object {
        private const val TAG = "NoticeApi"
        private const val NOTICE_API_URL =
            "https://ehall.cqwu.edu.cn/publicapp/sys/tzggxt/api/getUseNoticePage.do"
        private const val NOTICE_DETAIL_URL =
            "https://ehall.cqwu.edu.cn/publicapp/sys/tzggxt/api/getOneNoticeInfo.do"
        private const val PAGE_SIZE = 10
    }

    /**
     * 获取指定页的通知公告列表
     *
     * @param pageNo 页码（从 0 开始）
     * @param keyword 搜索关键词（不为 null 时添加 cti 参数）
     * @return Result 包含 { items: List<NoticeItem>, totalItem: Int }
     */
    suspend fun fetchNoticePage(pageNo: Int, keyword: String? = null): Result<NoticePageResult> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("$NOTICE_API_URL?pageNo=$pageNo&pageSize=$PAGE_SIZE")
                if (!keyword.isNullOrBlank()) {
                    append("&cti=$keyword")
                } else {
                    append("&column=")
                }
                append("&sortType=1")
            }
            Log.d(TAG, "请求通知公告: GET $url")

            val response = SharedHttpClient.client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Referer", "https://ehall.cqwu.edu.cn/publicapp/sys/tzggxt/mobile/index.html")
                    .get()
                    .build()
            ).execute()

            val body = response.body.string()

            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }

            Log.d(TAG, "通知公告响应: ${body.take(200)}")

            val noticeResponse = gson.fromJson(body, NoticeResponse::class.java)
            val qp = noticeResponse.qp
            val items = qp?.aList ?: emptyList()
            val totalItem = qp?.totalItem ?: 0

            Log.d(TAG, "解析结果: 当前页${items.size}条, 总共${totalItem}条")
            Result.success(NoticePageResult(items = items, totalItem = totalItem))
        } catch (e: Exception) {
            Log.e(TAG, "获取通知公告失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取指定通知的详情
     *
     * 实际接口返回结构：
     * { "attchList": [], "list": [ { noticeTitle, noticeContent, ... } ] }
     * 详情数据在 list 数组的第一个元素中。
     *
     * @param wid 通知 ID
     * @return Result 包含通知详情
     */
    suspend fun fetchNoticeDetail(wid: String): Result<NoticeDetailQp> = withContext(Dispatchers.IO) {
        try {
            val url = "$NOTICE_DETAIL_URL?noticeId=$wid"
            Log.d(TAG, "请求通知详情: GET $url")

            val response = SharedHttpClient.client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Referer", "https://ehall.cqwu.edu.cn/publicapp/sys/tzggxt/mobile/index.html")
                    .get()
                    .build()
            ).execute()

            val body = response.body.string()

            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }

            Log.d(TAG, "通知详情响应: ${body.take(300)}")

            val detailResponse = gson.fromJson(body, NoticeDetailResponse::class.java)
            val detail = detailResponse.list?.firstOrNull()
                ?: throw RuntimeException("通知详情数据为空")

            Log.d(TAG, "解析详情: title=${detail.noticeTitle.take(30)}")
            Result.success(detail)
        } catch (e: Exception) {
            Log.e(TAG, "获取通知详情失败", e)
            Result.failure(e)
        }
    }
}

/**
 * 通知公告分页结果
 */
data class NoticePageResult(
    val items: List<NoticeItem>,
    val totalItem: Int
)
