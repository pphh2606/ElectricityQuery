package edu.cqwu.electricity.campusnetwork.common

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 校园网络模块的 HTTP 传输层。
 *
 * 一个文件三件事，按「基类 → 站点常量 → 信封实现」排列：
 * - [CampusHttpBase]：公共客户端、请求构建与执行、错误归类、日志；
 * - [SpeedTestStation]：测速站根地址常量（测速与接入者信息两个功能共用）；
 * - [CampusNetworkHttp]：测速站的 `{code,message,data}` 信封传输。
 *
 * 认证网关的 `{result,message}` 平铺协议**不走** [CampusNetworkHttp]，而是由
 * [edu.cqwu.electricity.campusnetwork.portal.data.PortalApi] 直接继承 [CampusHttpBase]，
 * 只替换响应解码方式。
 *
 * 本文件此前拆成 `common/CampusHttpBase.kt` 与 `station/CampusNetworkHttp.kt` 两个文件，
 * 其中 `station/` 包里只有这一个文件、内容又同属"网络传输"一件事；合并后读传输层代码
 * 不必在两个文件之间跳转，模块文件数也少一个。
 */

/**
 * 校园网络模块的 HTTP 传输基类。
 *
 * 对齐 `payment.data.PayApiBase` 的既有做法：把同一模块下多个 API 类的公共传输逻辑
 * （客户端实例、请求构建、执行、错误归类、日志）收敛到基类，子类只声明协议差异。
 *
 * 模块内有两套**不同协议**，差异全部由子类承担：
 * - 测速站 `speedtest.cqwu.edu.cn`：JSON 请求体 + `{code,message,data}` 信封（见 [CampusNetworkHttp]）；
 * - 认证网关 `222.179.99.144`：表单编码 + `{result,message}` 平铺
 *   （见 [edu.cqwu.electricity.campusnetwork.portal.data.PortalApi]）。
 *
 * 此前两个类各写一份「执行请求 / 判 HTTP 码 / 记日志 / 归类异常」的实现，
 * 本基类把这段统一，同时保留二者刻意的行为差异（如测速站的 DELETE 释放失败不阻断调用方）。
 *
 * 错误语义（两个功能一致）：
 * - HTTP 非 2xx → [CampusNetworkException](SERVER)，message 原样展示；
 * - 连接超时/拒绝 → CAMPUS_OFFLINE，域名无法解析 → NO_NETWORK，解析失败 → PARSE
 *   （均经 [toCampusNetworkException] 归类）；
 * - `CancellationException` 原样上抛，**绝不静默吞掉**；
 * - 失败一律经 AppLog 记录 method + path 与原始异常，方便排查。
 *
 * 可见性说明：类本身是 public，因为子类 [edu.cqwu.electricity.campusnetwork.portal.data.PortalApi]
 * 是公开类型（其构造函数为 internal），而 Kotlin 不允许 public 类继承 internal 基类。
 * 构造函数为 internal，故模块外无法实例化或扩展。
 */
abstract class CampusHttpBase internal constructor(
    protected val client: OkHttpClient = HttpClientFactory.campusClient,
    /** 日志与错误信息中标识调用方（如 "SpeedTestApi"）；默认取子类简单类名 */
    protected val tag: String = "",
) {

    protected val gson: Gson = Gson()

    /**
     * 执行请求并返回成功响应（HTTP 非 2xx 视为失败，直接抛 [CampusNetworkException]）。
     *
     * @param logTag 调用方标签；测速站与接入者信息共用本类，传入各自功能名才能让失败日志
     *               定位到具体功能（默认为基类标签）
     * 调用方必须以 `use { }` 关闭响应体；[body] 的构造见 [jsonBody] / [formBody]。
     */
    protected fun execute(
        method: String,
        url: String,
        logTag: String = this.logTag,
        body: Request.Builder.() -> Unit = {},
    ): Response {
        val request = Request.Builder().url(url).apply(body).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            AppLog.e(logTag, "$method ${request.url.encodedPath} HTTP 失败: ${response.code}")
            throw CampusNetworkException(
                CampusNetworkErrorKind.SERVER,
                userMessage = "HTTP ${response.code}",
            )
        }
        return response
    }

    /** JSON 请求体（[payload] 为 null 时发送空体，与原测速站 POST 语义一致） */
    protected fun jsonBody(payload: Any?): RequestBody =
        (payload?.let { gson.toJson(it) } ?: "").toRequestBody(JSON_MEDIA_TYPE)

    /** 表单请求体 */
    protected fun formBody(form: Map<String, String>): RequestBody =
        FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()

    /**
     * 统一异常归类：把任意异常转成 [CampusNetworkException] 并记日志，
     * 供两个子类共用——测速站（[CampusNetworkHttp]）与认证网关（`portal.data.PortalApi`）。
     *
     * [CancellationException] 原样上抛，保证协程取消不被吞成业务失败。
     */
    protected fun Throwable.logAndClassify(desc: String? = null): CampusNetworkException {
        if (this is CancellationException) throw this
        val what = desc?.let { "$it " }.orEmpty()
        AppLog.e(logTag, "${what}请求失败: $message", this)
        return toCampusNetworkException()
    }

    /** 日志标签：显式 [tag] 优先，否则取子类简单类名 */
    protected val logTag: String get() = tag.ifEmpty { this::class.java.simpleName }

    protected companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** 测速站 API 根地址（speedtest 与 accessor 共用，杜绝两处写死漂移） */
internal object SpeedTestStation {

    const val BASE_URL = "https://speedtest.cqwu.edu.cn/api/speedlyst"
}

/**
 * 测速站（speedtest.cqwu.edu.cn/api/speedlyst）的统一 JSON 信封传输。
 *
 * 站点特点：无 Cookie / 无鉴权、"源 IP 即身份"，仅在校园网内且完成上网认证时可达。
 *
 * 客户端为 [HttpClientFactory.campusClient]（与认证网关共用同一个实例，配置一致）：
 * 跟随全局 WebVPN 开关（[edu.cqwu.electricity.common.net.WebVpnSettings]，实验性功能）——
 * 开关关闭时拦截器直接放行，与直连一致；开启时全部请求（含 probe）经 WebVPN 转换与自动登录。
 *
 * `data` 统一为 `{code, message, data}` 信封，`data` 目标类型均为具体业务类（非顶层泛型），
 * 因此按 Class 反序列化即可，无需 TypeToken。
 */
internal class CampusNetworkHttp(
    client: OkHttpClient = HttpClientFactory.campusClient,
) : CampusHttpBase(client = client, tag = "CampusNetworkHttp") {

    private data class Envelope(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("message") val message: String? = null,
        /** 先以 JsonElement 兜住，校验 code 后再按目标类型反序列化 */
        @SerializedName("data") val data: JsonElement? = null,
    )

    /** GET：解析 data 为目标类型 */
    suspend fun <T> get(tag: String, path: String, dataType: Class<T>): Result<T> =
        requestJson(tag, "GET", path, body = null, dataType = dataType)

    /** POST：body 为 null 时发送空 JSON 体（与官网创建会话一致） */
    suspend fun <T> post(tag: String, path: String, body: Any?, dataType: Class<T>): Result<T> =
        requestJson(tag, "POST", path, body, dataType)

    /**
     * DELETE：不解析响应体，成功即 Unit。
     * 语义为"兜底释放会话"：失败仅记录日志、仍返回成功，不阻断调用方清理流程。
     */
    suspend fun delete(tag: String, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 刻意不走 execute()：本接口的失败只记日志、不抛异常，与其它接口语义相反
            client.newCall(Request.Builder().url(SpeedTestStation.BASE_URL + path).delete().build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        AppLog.e(tag, "DELETE $path HTTP 失败: ${response.code}")
                    }
                }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(tag, "DELETE $path 请求失败: ${e.message}", e)
            Result.success(Unit) // 释放失败不阻断调用方流程，仅记录
        }
    }

    private suspend fun <T> requestJson(
        tag: String,
        method: String,
        path: String,
        body: Any?,
        dataType: Class<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            // logTag 传调用方标签（AccessorApi / SpeedTestApi），失败日志可定位到具体功能
            val text = execute(method, SpeedTestStation.BASE_URL + path, logTag = tag) {
                if (method == "POST") post(jsonBody(body)) else get()
            }.use { it.body.string() }

            val envelope = gson.fromJson(text, Envelope::class.java)
                ?: throw IllegalStateException("$method $path 响应为空")
            if (envelope.code != 0) {
                AppLog.e(tag, "$method $path 业务失败: code=${envelope.code}, message=${envelope.message}")
                return@withContext Result.failure(
                    CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = envelope.message)
                )
            }
            val data = envelope.data
                ?: throw IllegalStateException("$method $path data 为空")
            val parsed = gson.fromJson(data, dataType)
                ?: throw IllegalStateException("$method $path data 反序列化失败")
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // logAndClassify 统一记日志并归类；execute() 抛出的 HTTP 失败已带正确分类，原样返回
            Result.failure(e.logAndClassify("$method $path"))
        }
    }
}
