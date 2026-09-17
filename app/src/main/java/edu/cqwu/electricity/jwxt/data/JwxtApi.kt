package edu.cqwu.electricity.jwxt.data

import com.google.gson.Gson
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 教务（jwfw `/jwmobile`）接口客户端。
 *
 * 声明本模块用到的接口：首页的服务分组、场景卡、今日课程、本学期考试，以及成绩查询的学期列表与成绩。
 * 请求头按抓包实测固定，token 由 [JwxtSessionManager] 统一提供。
 * 业务码 401 表示服务端已不认这个 token（JWT 无 `exp`，无法本地判过期），此时清掉本地 token、
 * 换一次票据重发一次（见 [request]）；重试后仍失败才抛 [SessionExpiredException]，由 UI 引导重新登录。
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
     * 「更多服务」的全部分组（单层信封；`data` 是分组数组，分组里再按分类装服务）。
     *
     * 与首页 [fetchLabelServices] 相比只多了一层分类，请求方式完全一致。
     */
    suspend fun fetchServiceSets(): Result<List<JwxtServiceGroup>> =
        request("biz/home/listLabelServiceSet", isPost = false) { json ->
            val resp = gson.fromJson(json, LabelServiceSetResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listLabelServiceSet")
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
            // 内层 data 可能是显式 null（当天没课时后端这样返回，实测过），必须兜底
            page.data.orEmpty()
        }

    /** 本学期考试（无查询参数，按考试时间降序返回） */
    suspend fun fetchRecentExams(): Result<List<JwxtExam>> =
        request("biz/v410/examTask/recentExams", isPost = false) { json ->
            val resp = gson.fromJson(json, RecentExamResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "recentExams")
            val page = resp.data ?: return@request emptyList<JwxtExam>()
            checkBusinessCode(page.code, page.msg, "recentExams")
            // 内层 data 可能是显式 null（近期没有考试时后端这样返回，实测会打崩首页），必须兜底
            page.data.orEmpty()
        }

    /**
     * 成绩查询的学期列表（无参数）。
     *
     * 实测下发「全部学期（`*`）」+ 各具体学期，页面按这个顺序直接铺胶囊按钮。
     */
    suspend fun fetchScoreTerms(): Result<List<JwxtScoreTerm>> =
        request("biz/v510/score/termList", isPost = false) { json ->
            val resp = gson.fromJson(json, ScoreTermResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "termList")
            resp.data ?: emptyList()
        }

    /**
     * 某学期的成绩；[termCode] 传 `*` 时一次返回所有学期的分组。
     *
     * 接口按学期分组下发，这里直接铺平成一条列表——每条成绩自带 `termName`，页面不需要分组结构。
     */
    suspend fun fetchTermScores(termCode: String): Result<List<JwxtScore>> =
        request(
            path = "biz/v510/score/termScore",
            isPost = true,
            jsonBody = gson.toJson(ScoreTermRequest(termCode)),
        ) { json ->
            val resp = gson.fromJson(json, TermScoreResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "termScore")
            resp.data?.termScoreList.orEmpty().flatMap { it.scoreList.orEmpty() }
        }

    // ── 内部实现 ──

    /**
     * 执行请求，必要时重试一次；异常统一归类（会话过期原样上抛，其余封装为 Result.failure）。
     *
     * 业务码 401 说明服务端已不认本地这个 token；而教务的 JWT **没有 `exp` 字段**（本地判断不出过期），
     * 只能撞上才知道。此时 [checkBusinessCode] 已把本地 token 清掉，这里再换一次票据重发一次通常就能成功，
     * 不必把用户赶去重新登录；只有重试后仍失败（CAS 会话真的失效）才交给界面提示。
     */
    private suspend fun <T> request(
        path: String,
        isPost: Boolean,
        jsonBody: String? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(execute(path, isPost, jsonBody, parse))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            AppLog.d(TAG, "$path 会话失效，清除本地 token 后重试一次")
            JwxtSessionManager.clearToken()
            try {
                Result.success(execute(path, isPost, jsonBody, parse))
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                AppLog.e(TAG, "请求失败（重换 token 后仍失败）：$path", e2)
                Result.failure(e2)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "请求失败：$path", e)
            Result.failure(e)
        }
    }

    /** 真正发一次请求并解析（不含重试）。网关故障（如 502）返回的是 HTML 错误页，用状态码当错误信息 */
    private suspend fun <T> execute(path: String, isPost: Boolean, jsonBody: String?, parse: (String) -> T): T {
        val response = client.newCall(buildRequest(path, isPost, jsonBody)).execute()
        return response.use {
            val body = it.body.string()
            AppLog.body(TAG, "$path → $body")
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            parse(body)
        }
    }

    /**
     * 构造请求：先确保 token 有效（[JwxtSessionManager.ensureToken] 是挂起函数，因此本方法也是）。
     *
     * 请求头按抓包实测：`Accept` / `Referer` / `X-Requested-With` / `Authorization`。
     */
    private suspend fun buildRequest(path: String, isPost: Boolean, jsonBody: String?): Request {
        val token = JwxtSessionManager.ensureToken()
        val builder = Request.Builder()
            .url(JwxtConstants.BASE + "/" + path)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", JwxtConstants.INDEX_URL)
            // 刻意不带项目包名，避免向教务服务器暴露本应用身份。
            // 取 AJAX 的标准标志值（网页端这里带的是浏览器标识），兼容依赖该头判断 AJAX 的服务端逻辑。
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Authorization", token)
        return when {
            // 带内容的 POST：成绩查询按学期传 {"termCode":"…"}（抓包实证）
            jsonBody != null -> builder.post(jsonBody.toRequestBody(JSON_MEDIA_TYPE)).build()
            // 无内容的 POST：抓包中 listMoreScene 正是 Content-Length: 0
            isPost -> builder.post(ByteArray(0).toRequestBody()).build()
            else -> builder.get().build()
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

        /** 成绩查询的请求体是 JSON（抓包：`Content-Type: application/json`） */
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
