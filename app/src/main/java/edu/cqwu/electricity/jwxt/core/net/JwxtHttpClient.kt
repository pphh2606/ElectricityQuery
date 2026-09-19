package edu.cqwu.electricity.jwxt.core.net

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.jwxt.core.model.JwxtCellLine
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.JwxtSessionManager
import edu.cqwu.electricity.logging.AppLog
import java.lang.reflect.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 教务接口的「发请求规矩」：认证、401 后换票据重试一次、错误归类、JSON 解析——**全项目只有这一处**。
 *
 * 这些原先都写在 `JwxtApi` 里（一个类同时管"怎么发请求"和"有哪些接口"），现在拆开：
 * 各功能的 Api 只声明自己的接口，发请求的活儿交给这里。
 *
 * 认证凭据由同包的 [JwxtConstants]（各功能地址）与 [JwxtSessionManager]（JWT 存取）提供。
 */
class JwxtHttpClient(
    private val client: OkHttpClient = HttpClientFactory.shared,
) {

    /** 课表详情挂了「null 安全」的反序列化器（见文件末尾的 [TimetableCourseDeserializer]），其余接口照旧 */
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(JwxtTimetableCourse::class.java, TimetableCourseDeserializer)
        .create()

    /**
     * 执行请求，必要时重试一次；异常统一归类（会话过期原样上抛，其余封装为 Result.failure）。
     *
     * 业务码 401 说明服务端已不认本地这个 token；而教务的 JWT **没有 `exp` 字段**（本地判断不出过期），
     * 只能撞上才知道。此时 [checkBusinessCode] 已把本地 token 清掉，这里再换一次票据重发一次通常就能成功，
     * 不必把用户赶去重新登录；只有重试后仍失败（CAS 会话真的失效）才交给界面提示。
     */
    suspend fun <T> request(
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
    fun checkBusinessCode(code: Int, msg: String, path: String) {
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
        const val TAG = "JwxtHttpClient"
        const val CODE_SUCCESS = 200
        const val CODE_UNAUTHORIZED = 401

        /** 与项目其它模块一致的会话过期提示，UI 层据此显示「重新登录」 */
        const val SESSION_EXPIRED_MESSAGE = "会话已过期，请重新登录"

        /** 成绩查询的请求体是 JSON（抓包：`Content-Type: application/json`） */
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * 课表详情的反序列化器。
 *
 * Gson 会把接口里的显式 null **直接写进非空字段**（本项目踩过的坑：未排课记录的 `color` 实测就是 null）。
 * 只要有一门课带了 null，之后任何 `hashCode()`（例如把它当成 Map 的 key）或字符串处理都会崩——
 * 2026-09-18 的线上日志里就是这么崩的。
 *
 * 这里逐字段手工读，null 一律归成空串 / 空列表。
 * **以后给 [JwxtTimetableCourse] 加字段，记得在这里补一行。**
 */
private object TimetableCourseDeserializer : JsonDeserializer<JwxtTimetableCourse> {

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): JwxtTimetableCourse {
        val obj = json.asJsonObject
        return JwxtTimetableCourse(
            courseName = obj.stringOrEmpty("courseName"),
            dayOfWeek = obj.intOrZero("dayOfWeek"),
            beginSection = obj.intOrZero("beginSection"),
            endSection = obj.intOrZero("endSection"),
            placeName = obj.stringOrEmpty("placeName"),
            color = obj.stringOrEmpty("color"),
            cellDetail = obj.arrayOrNull("cellDetail") { item ->
                val line = item.asJsonObject
                JwxtCellLine(color = line.stringOrNull("color"), text = line.stringOrEmpty("text"))
            },
            titleDetail = obj.arrayOrNull("titleDetail") { it.stringOrEmpty() },
        )
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.stringOrEmpty(name: String): String = stringOrNull(name).orEmpty()

private fun JsonObject.intOrZero(name: String): Int =
    get(name)?.takeIf { !it.isJsonNull }?.asInt ?: 0

/** 按数组读；字段缺失或不是数组时给 null（对应模型里的可空列表） */
private fun <T> JsonObject.arrayOrNull(name: String, map: (JsonElement) -> T): List<T>? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.map { map(it) }

private fun JsonElement.stringOrEmpty(): String = if (isJsonNull) "" else asString
