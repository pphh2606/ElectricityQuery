package edu.cqwu.electricity.profile.data

import edu.cqwu.electricity.logging.AppLog
import com.google.gson.Gson
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.common.net.CookieStore
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.login.domain.AutoLoginCoordinatorV2
import edu.cqwu.electricity.common.net.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * campusphere.net 学生信息服务 API。
 *
 * 流程：POST {} → 接口；若响应含 WEC-HASLOGIN:false（未登录），访问
 * /student/mobile/index.html 触发 CAS ticket 交换（经 [AutoLoginCoordinatorV2]），
 * 成功后重试一次原请求。认证去重由会话层 single-flight 承担。
 */
class CampusphereApi {

    companion object {
        private const val BASE = "https://cqwu.campusphere.net"
        private const val INDEX_URL =
            "$BASE/wec-counselor-stuinfo-apps/student/mobile/index.html"
        private const val INFO_API =
            "$BASE/wec-counselor-stuinfo-apps/student/detail/getStuMainMustInfos"
        private const val MENU_API =
            "$BASE/wec-counselor-stuinfo-apps/student/detail/getStuMenuList"
        private const val AUTH_SERVER = "https://authserver.cqwu.edu.cn"

        private val gson = Gson()
        private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()
        private const val TAG = "CampusphereApi"

        /** 未登录响应体（WEC-HASLOGIN=false）的检测 */
        private fun isNotLoggedIn(responseBody: String): Boolean {
            return responseBody.contains("WEC-HASLOGIN") &&
                (responseBody.contains("\"WEC-HASLOGIN\":false") ||
                    responseBody.contains("WEC-HASLOGIN\":false"))
        }
    }

    /**
     * 创建 OkHttpClient 并获取 Cookie 读取器。
     * 系统 CookieManager 始终只反映当前激活账号的登录态，直接使用共享 client 即可。
     */
    private fun createClient(): Pair<OkHttpClient, (String) -> String?> {
        return HttpClientFactory.shared to { CookieStore.getCookie(it) }
    }

    /** 执行 CAS ticket 交换（委托给 [AutoLoginCoordinatorV2]）。 */
    private fun doCasTicketExchange() {
        AutoLoginCoordinatorV2.ensureService(
            protectedUrl = INDEX_URL,
            serviceDomain = BASE,
            expectedCookie = "MOD_AUTH_CAS"
        )
    }

    /**
     * 获取学生个人信息。直接 POST {} → getStuMainMustInfos；
     * 未登录（WEC-HASLOGIN:false）时自动执行 CAS ticket 交换后重试一次。
     */
    suspend fun fetchStudentInfo(): Result<StudentInfo> = fetchWithAutoLogin("学生信息") {
        doFetchStudentInfo()
    }

    /** 内部：执行一次学生信息请求，不重试 */
    private suspend fun doFetchStudentInfo(): Result<StudentInfo> {
        return try {
            val (client, cookieReader) = createClient()

            if (BuildConfig.DEBUG) {
                val campusCookie = cookieReader(BASE)
                val authCookie = cookieReader(AUTH_SERVER)
                AppLog.d(
                    TAG,
                    "activeUser=${AccountSessionStore.getActiveAccount()?.username}, " +
                        "campusphere=${campusCookie != null}(len=${campusCookie?.length ?: 0}), " +
                        "auth=${authCookie != null}(len=${authCookie?.length ?: 0})",
                )
            }

            val bodyJson = "{}"
            val request = Request.Builder()
                .url(INFO_API)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Origin", BASE)
                .addHeader("Referer", INDEX_URL)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val info = client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()

                AppLog.body(TAG, "API 响应(前200): $responseBody")

                // ⚠️ 检查未登录响应（body 是 JSON，不是 HTML，不能用 SessionChecker）
                if (isNotLoggedIn(responseBody)) {
                    AppLog.w(TAG, "API 返回 WEC-HASLOGIN:false，未登录")
                    throw NotLoggedInException()
                }
                if (HtmlFormParser.isCasLoginPage(responseBody)) {
                    throw SessionExpiredException("会话已过期，请重新登录")
                }
                if (!response.isSuccessful) {
                    val msg = "HTTP ${response.code}: ${response.message}"
                    if (response.code in listOf(401, 403)) throw SessionExpiredException(msg)
                    throw Exception(msg)
                }

                val infoResponse = gson.fromJson(responseBody, StudentInfoResponse::class.java)
                if (infoResponse.code != "0") throw Exception("获取学生信息失败: ${infoResponse.message}")
                infoResponse.datas?.firstOrNull()
                    ?: throw Exception("学生信息为空")
            }

            AppLog.d(TAG, "学生信息获取成功: userName=${info.userName}, userId=${info.userId}")
            Result.success(info)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: NotLoggedInException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取菜单列表。与 [fetchStudentInfo] 相同：未登录时触发 CAS ticket 交换后重试一次；
     * 不内部调用 fetchStudentInfo 全流程，避免与主页信息加载并发重复登录。
     */
    suspend fun fetchMenuList(): Result<List<MenuCategory>> = fetchWithAutoLogin("菜单列表") {
        doFetchMenuList()
    }

    /** 内部：执行一次菜单列表请求，不重试 */
    private suspend fun doFetchMenuList(): Result<List<MenuCategory>> = withContext(Dispatchers.IO) {
        try {
            val (client, _) = createClient()
            val bodyJson = "{}"
            val request = Request.Builder()
                .url(MENU_API)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Origin", BASE)
                .addHeader("Referer", INDEX_URL)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val list = client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()

                if (isNotLoggedIn(responseBody)) {
                    throw NotLoggedInException()
                }
                if (HtmlFormParser.isCasLoginPage(responseBody)) {
                    throw SessionExpiredException("会话已过期，请重新登录")
                }

                val menuResponse = gson.fromJson(responseBody, MenuListResponse::class.java)
                if (menuResponse.code != "0") throw Exception("获取菜单失败: ${menuResponse.message}")
                menuResponse.datas ?: emptyList()
            }

            Result.success(list)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: NotLoggedInException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 共享骨架：执行一次 [request]；若失败仅为"未登录"，完成一次 CAS ticket 交换后重试一次。
     * 网络失败（非未登录）原样返回。认证去重由会话层 single-flight 承担，并发调用不重复登录。
     */
    private suspend fun <T> fetchWithAutoLogin(
        subject: String,
        request: suspend () -> Result<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        var result = request()
        if (result.isFailure && result.exceptionOrNull() is NotLoggedInException) {
            AppLog.d(TAG, "$subject 未登录，执行 CAS ticket 交换后重试")
            doCasTicketExchange()
            result = request()
        }
        result
    }
}

/** 未登录异常（WEC-HASLOGIN:false） */
class NotLoggedInException : Exception("未登录到校园信息门户")

// ═══════════════════════════════════════════
//  菜单列表数据模型
// ═══════════════════════════════════════════

data class MenuCategory(
    val formCode: String = "",
    val formName: String = "",
    val children: List<MenuCategory>? = null,
    val sort: Int? = null,
    val status: Boolean = true,
    val dataType: String? = null,
)

internal data class MenuListResponse(
    val code: String = "",
    val message: String = "",
    val datas: List<MenuCategory>? = null,
)
