package edu.cqwu.electricity.hall.data

import android.util.Log
import com.google.gson.Gson
import edu.cqwu.electricity.hall.data.FavoriteAppResponse
import edu.cqwu.electricity.hall.data.HallItem
import edu.cqwu.electricity.hall.data.UserFavoritesResponse
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import edu.cqwu.electricity.login.data.ServiceLoginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 办事大厅收藏 API 请求封装。
 *
 * 流程（与 [edu.cqwu.electricity.qrcode.data.QrCodeApi] 的 CAS ticket 交换模式一致）：
 * 1. 先 GET [EHALL_APP_SHOW_URL] 触发 ehall 的 CAS ticket 交换
 *     - ehall 检测到无有效 JSESSIONID → 302 重定向到 CAS
 *     - CAS 检测到现有 CASTGC Cookie → 自动授权 → 回调 ehall
 *     - ehall 下发已认证的 JSESSIONID
 * 2. 再请求 [FAVORITE_APPS_URL] 获取收藏数据
 *
 * 使用 [HttpClientFactory.shared]（共享 CookieJar，自动携带登录态 Cookie）。
 *
 * 返回已筛选 [favorite=true] 的 [HallItem] 列表。
 */
class HallFavoriteApi {

    companion object {
        /** 办事大厅受保护页面 URL（用于触发 CAS ticket 交换） */
        const val EHALL_APP_SHOW_URL = "https://ehall.cqwu.edu.cn/appshow"
        /** 用户收藏应用列表 API */
        const val FAVORITE_APPS_URL = "https://ehall.cqwu.edu.cn/jsonp/userFavoriteApps.json"
        /** 收藏单个应用的 API */
        const val FAVORITE_APP_URL = "https://ehall.cqwu.edu.cn/jsonp/favoriteApp"
        /** 取消收藏单个应用的 API */
        const val UNFAVORITE_APP_URL = "https://ehall.cqwu.edu.cn/jsonp/unFaviroteApp"
    }

    private val gson = Gson()
    private val client get() = HttpClientFactory.shared

    /**
     * 触发 ehall CAS ticket 交换，建立已认证的 ehall JSESSIONID。
     *
     * 委托 [ServiceLoginManager.ensureLogin] 完成标准 CAS ticket 交换。
     *
     * @throws SessionExpiredException 用户未登录或 Cookie 过期
     */
    fun initEhallSession() {
        ServiceLoginManager.ensureLogin(protectedUrl = EHALL_APP_SHOW_URL)
    }

    /**
     * 获取收藏的应用列表。
     *
     * - 已登录且有收藏 → `Result.success(List<HallItem>)`（仅含 favorite=true 的项目）
     * - 未登录 / Session 过期 → `Result.failure(SessionExpiredException)`
     * - 网络/解析错误 → `Result.failure(Exception)`
     */
    suspend fun fetchFavorites(): Result<List<HallItem>> = withContext(Dispatchers.IO) {
        try {
            // ═══ 步骤 1：确保 ehall session 已初始化（幂等） ═══
            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] 开始执行")
            initEhallSession()
            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] initEhallSession 通过")

            // ═══ 步骤 2：请求收藏 API ═══
            val url = "${FAVORITE_APPS_URL}?_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://ehall.cqwu.edu.cn/new/index.html")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] 步骤2: GET $url")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] 收到响应，长度=${body.length}")
            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] 响应前200字: ${body.take(200)}")

            // JSON API 响应直接反序列化，通过 hasLogin 字段判断登录态
            val result = gson.fromJson(body, UserFavoritesResponse::class.java)
            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] Gson解析成功, hasLogin=${result.hasLogin}, searchResult.size=${result.searchResult.size}")

            // 检查 hasLogin 字段
            if (!result.hasLogin) {
                Log.w("HallFavoriteApi_DEBUG", "[fetchFavorites] hasLogin=false，抛出SessionExpiredException")
                throw SessionExpiredException("用户未登录")
            }

            // 筛选 favorite=true 的应用
            val favorites = result.searchResult.filter { it.favorite }
            Log.d("HallFavoriteApi_DEBUG", "[fetchFavorites] 筛选出favorite=true: ${favorites.size}个")
            Result.success(favorites)
        } catch (e: SessionExpiredException) {
            // 用 e.message! 区分是 initEhallSession 还是 hasLogin 抛出的
            Log.w("HallFavoriteApi_DEBUG", "[fetchFavorites] 捕获 SessionExpiredException: message='${e.message}'")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("HallFavoriteApi_DEBUG", "[fetchFavorites] 捕获 Exception", e)
            Result.failure(e)
        }
    }

    /**
     * 切换指定应用的收藏状态。
     *
     * 根据 [addFavorite] 决定调用收藏 API 或取消收藏 API：
     * - addFavorite=true  → 调用 /jsonp/favoriteApp（收藏）
     * - addFavorite=false → 调用 /jsonp/unFaviroteApp（取消收藏）
     *
     * @param appId 目标应用 ID
     * @param addFavorite true=添加收藏, false=取消收藏
     * @return Result<Unit> 成功时返回 Unit
     */
    suspend fun toggleFavorite(appId: String, addFavorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 确保 ehall session 有效
            initEhallSession()

            val baseUrl = if (addFavorite) FAVORITE_APP_URL else UNFAVORITE_APP_URL
            val urlBuilder = StringBuilder("$baseUrl?appId=$appId&type=0&_=${System.currentTimeMillis()}")
            // 收藏 API 需要 favoriteFolderId 参数
            if (addFavorite) {
                urlBuilder.append("&favoriteFolderId=0")
            }
            val url = urlBuilder.toString()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://ehall.cqwu.edu.cn/new/index.html")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            Log.d("HallFavoriteApi", "toggleFavorite: GET $url")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            Log.d("HallFavoriteApi", "toggleFavorite 响应: $body")

            // JSON API 响应直接反序列化，通过 hasLogin 字段判断登录态
            val result = gson.fromJson(body, FavoriteAppResponse::class.java)

            if (!result.hasLogin) {
                throw SessionExpiredException("用户未登录")
            }

            if (!result.isSuccess) {
                throw RuntimeException("切换收藏失败：${result.result}")
            }

            Log.d("HallFavoriteApi", "切换收藏成功: appId=$appId, addFavorite=$addFavorite")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            Log.w("HallFavoriteApi", "Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("HallFavoriteApi", "切换收藏失败", e)
            Result.failure(e)
        }
    }
}
