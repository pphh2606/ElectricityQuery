package edu.cqwu.electricity.jwxt.data

import com.google.gson.Gson
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 教务首页（jwfw `/jwmobile`）接口客户端。
 *
 * 只声明首页需要的接口（服务分组、场景卡、今日课程、本学期考试）；请求头按抓包实测固定，
 * token 由 [JwxtSessionManager] 统一提供。
 * 业务码 401 表示服务端已不认这个 token（JWT 无 `exp`，无法本地判过期），此时清掉本地 token
 * 并抛 [SessionExpiredException]，由 UI 统一引导重新登录——**不做自动重试**。
 */
class JwxtApi(
    private val client: OkHttpClient = HttpClientFactory.shared,
) {

    private val gson = Gson()

    /**
     * 首页服务分组（前 3 组用于顶部统计数字，第 1 组的 `serviceList` 用于九宫格）。
     *
     * @param num 只影响接口返回的推荐服务条数，与分组统计无关，沿用抓包中的 7
     */
    suspend fun fetchLabelServices(num: Int = DEFAULT_SERVICE_NUM): Result<List<JwxtLabelGroup>> =
        request("biz/home/listLabelService?num=$num", isPost = false) { json ->
            val resp = gson.fromJson(json, LabelServiceResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listLabelService")
            resp.data ?: emptyList()
        }

    /** 首页场景卡片 */
    suspend fun fetchMoreScenes(): Result<List<JwxtScene>> =
        request("biz/home/listMoreScene", isPost = true) { json ->
            val resp = gson.fromJson(json, SceneResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listMoreScene")
            resp.data ?: emptyList()
        }

    /**
     * 今日课程（无查询参数）。
     *
     * 双层包裹，两层业务码都校验；当天没课时内层 `data` 是空数组（上一份抓包即为该形态）。
     */
    suspend fun fetchTodayLessons(): Result<List<JwxtTodayLesson>> =
        request("biz/v410/schedule/listStudentTodayLesson", isPost = false) { json ->
            val resp = gson.fromJson(json, TodayLessonResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listStudentTodayLesson")
            val page = resp.data ?: return@request emptyList<JwxtTodayLesson>()
            checkBusinessCode(page.code, page.msg, "listStudentTodayLesson")
            page.data
        }

    /** 本学期考试（无查询参数，按考试时间降序返回） */
    suspend fun fetchRecentExams(): Result<List<JwxtExam>> =
        request("biz/v410/examTask/recentExams", isPost = false) { json ->
            val resp = gson.fromJson(json, RecentExamResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "recentExams")
            val page = resp.data ?: return@request emptyList<JwxtExam>()
            checkBusinessCode(page.code, page.msg, "recentExams")
            page.data
        }

    // ── 内部实现 ──

    /** 执行一次请求并解析；异常统一归类（会话过期原样上抛，其余封装为 Result.failure） */
    private suspend fun <T> request(
        path: String,
        isPost: Boolean,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val body = client.newCall(buildRequest(path, isPost)).execute().use { it.body.string() }
            AppLog.body(TAG, "$path → $body")
            Result.success(parse(body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "请求失败：$path", e)
            Result.failure(e)
        }
    }

    /**
     * 构造请求：先确保 token 有效（[JwxtSessionManager.ensureToken] 是挂起函数，因此本方法也是）。
     *
     * 请求头按抓包实测：`Accept` / `Referer` / `X-Requested-With` / `Authorization`；
     * POST 为无内容的空请求体（抓包中 `listMoreScene` 正是 `Content-Length: 0`）。
     */
    private suspend fun buildRequest(path: String, isPost: Boolean): Request {
        val token = JwxtSessionManager.ensureToken()
        val builder = Request.Builder()
            .url(JwxtConstants.BASE + "/" + path)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", JwxtConstants.INDEX_URL)
            // 刻意不带项目包名，避免向教务服务器暴露本应用身份。
            // 取 AJAX 的标准标志值（网页端这里带的是浏览器标识），兼容依赖该头判断 AJAX 的服务端逻辑。
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Authorization", token)
        return if (isPost) {
            builder.post(ByteArray(0).toRequestBody()).build()
        } else {
            builder.get().build()
        }
    }

    /** 业务码校验：401 清 token 并抛 [SessionExpiredException]，其余非 200 视为业务失败 */
    private fun checkBusinessCode(code: Int, msg: String, path: String) {
        if (code == CODE_UNAUTHORIZED) {
            AppLog.w(TAG, "$path 返回 401，清除本地 token")
            JwxtSessionManager.clearToken()
            throw SessionExpiredException(SESSION_EXPIRED_MESSAGE)
        }
        if (code != CODE_SUCCESS) {
            throw IllegalStateException("$path 业务失败：$msg")
        }
    }

    private companion object {
        const val TAG = "JwxtApi"
        const val CODE_SUCCESS = 200
        const val CODE_UNAUTHORIZED = 401
        const val DEFAULT_SERVICE_NUM = 7

        /** 与项目其它模块一致的会话过期提示，UI 层据此显示「重新登录」 */
        const val SESSION_EXPIRED_MESSAGE = "会话已过期，请重新登录"
    }
}
