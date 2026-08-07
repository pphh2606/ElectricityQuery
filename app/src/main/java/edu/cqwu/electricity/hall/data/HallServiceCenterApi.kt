package edu.cqwu.electricity.hall.data

import android.util.Log
import com.google.gson.Gson
import edu.cqwu.electricity.hall.data.HallCategory
import edu.cqwu.electricity.hall.data.UserCategoryAppListResponse
import edu.cqwu.electricity.hall.data.extractCategories
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 办事大厅用户分类应用 API 请求封装。
 *
 * 请求 [GET_USER_CATEGORY_APP_LIST_URL] 获取全部分类应用列表，
 * 包含 [HallItem.favorite] 和 [HallItem.favoriteCount] 等信息。
 *
 * 依赖于 [HallFavoriteApi.initEhallSession] 先完成 CAS ticket 交换，
 * 否则请求会因无已认证 JSESSIONID 而失败。
 *
 * 返回所有分类（含应用及收藏信息），若未登录则抛出 [SessionExpiredException]。
 *
 * **注意：** 服务器对未登录用户也会返回应用列表数据，但 [UserCategoryAppListResponse.hasLogin] 为 false。
 * 调用方必须通过此字段判断是否使用服务端数据，避免未登录时错误覆盖本地数据。
 */
class HallServiceCenterApi {

    companion object {
        /** 办事大厅用户分类应用 API（全部应用列表，含 favorite/favoriteCount 信息） */
        const val GET_USER_CATEGORY_APP_LIST_URL = "https://ehall.cqwu.edu.cn/jsonp/getUserCategoryAppList.json"
    }

    private val gson = Gson()
    private val client get() = HttpClientFactory.shared

    /**
     * 获取服务数据中心的应用列表。
     *
     * - 已登录 → `Result.success(List<HallItem>)`（含 favorite/favoriteCount）
     * - Session 过期 → `Result.failure(SessionExpiredException)`
     * - 网络/解析错误 → `Result.failure(Exception)`
     */
    suspend fun fetchServiceData(): Result<List<HallCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = "${GET_USER_CATEGORY_APP_LIST_URL}?_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://ehall.cqwu.edu.cn/new/index.html")
                .build()

            Log.d("HallServiceCenterApi", "GET $url")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            // JSON API 响应直接反序列化，通过 hasLogin 字段判断登录态
            val result = gson.fromJson(body, UserCategoryAppListResponse::class.java)

            // ═══ 修复：检查 hasLogin 字段，未登录时不使用服务端数据 ═══
            if (!result.hasLogin) {
                Log.w("HallServiceCenterApi", "用户未登录（hasLogin=false），拒绝使用服务端数据")
                throw SessionExpiredException("用户未登录")
            }

            val items = result.extractCategories()
            Log.d("HallServiceCenterApi", "服务数据获取成功，分类数: ${items.size}")
            Result.success(items)
        } catch (e: SessionExpiredException) {
            Log.w("HallServiceCenterApi", "Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("HallServiceCenterApi", "获取服务数据失败", e)
            Result.failure(e)
        }
    }
}
