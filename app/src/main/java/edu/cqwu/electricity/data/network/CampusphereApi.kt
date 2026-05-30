package edu.cqwu.electricity.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.data.model.StudentInfo
import edu.cqwu.electricity.data.model.StudentInfoResponse

/**
 * campusphere.net 学生信息服务 API。
 *
 * 流程：
 *   1. 直接 POST {} → getStuMainMustInfos
 *   2. 若响应 JSON 中包含 WEC-HASLOGIN:false（未登录）:
 *      a. 访问 /student/mobile/index.html 触发 CAS ticket 交换
 *      b. OkHttp 自动跟随 302 → authserver(携带CASTGC) → 获得 MOD_AUTH_CAS
 *      c. 重试 POST {} → getStuMainMustInfos
 *   3. 成功 → 返回 StudentInfo
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
     */
    private fun createClient(): Pair<OkHttpClient, (String) -> String?> {
        val activeUser = AccountManager.getActiveUser()
        if (activeUser != null) {
            val store = AccountManager.getCookiesForUser(activeUser)
            store.syncFromCookieManager()
            return SharedHttpClient.createClientForUser(activeUser) to { store.getCookie(it) }
        }
        return SharedHttpClient.client to { CookieStore.getCookie(it) }
    }

    /**
     * 执行 CAS ticket 交换，获取 campusphere 域下的 MOD_AUTH_CAS Cookie。
     *
     * 流程：
     *   1. GET /student/mobile/index.html（未登录 → 302）
     *   2. 跟随 302 → authserver/login?service=...（携带已有 CASTGC）
     *   3. authserver 验证通过 → 302 + ticket → Set-Cookie: MOD_AUTH_CAS
     *   4. 最终回到 index.html（已认证）
     */
    private fun doCasTicketExchange(client: OkHttpClient, cookieReader: (String) -> String?) {
        android.util.Log.d(TAG, ">>> 执行 CAS ticket 交换 >>>")

        val resp = client.newCall(
            Request.Builder()
                .url(INDEX_URL)
                .addHeader("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()
        ).execute()

        // 消费响应体
        resp.body.string()
        val finalUrl = resp.request.url.toString()
        val code = resp.code
        android.util.Log.d(TAG, "CAS ticket 交换: code=$code, finalUrl=${finalUrl.take(100)}")

        // 检查是否获得了 MOD_AUTH_CAS
        val campusCookie = cookieReader(BASE)
        val hasModAuthCas = campusCookie?.contains("MOD_AUTH_CAS=") == true
        val hasJsessionid = campusCookie?.contains("JSESSIONID=") == true
        android.util.Log.d(TAG, "交换后 Cookie: MOD_AUTH_CAS=$hasModAuthCas, JSESSIONID=$hasJsessionid, cookie=${campusCookie?.take(80)}")

        if (!hasModAuthCas) {
            android.util.Log.w(TAG, "CAS ticket 交换失败，仍未获取到 MOD_AUTH_CAS")
            throw SessionExpiredException("校园信息会话已过期，请先登录")
        }
    }

    /**
     * 获取学生个人信息。
     * 直接 POST {} → getStuMainMustInfos。
     * 若未登录（WEC-HASLOGIN:false），自动执行 CAS ticket 交换后重试一次。
     */
    suspend fun fetchStudentInfo(): Result<StudentInfo> = withContext(Dispatchers.IO) {
        try {
            val (client, cookieReader) = createClient()

            if (BuildConfig.DEBUG) {
                val campusCookie = cookieReader(BASE)
                val authCookie = cookieReader(AUTH_SERVER)
                android.util.Log.d(TAG,
                    "activeUser=${AccountManager.getActiveUser()}, " +
                    "campusphere=${campusCookie != null}(len=${campusCookie?.length ?: 0}), " +
                    "auth=${authCookie != null}(len=${authCookie?.length ?: 0})")
            }

            // ── 第 1 次请求 ──
            var result = doFetchStudentInfo(client)
            if (result.isSuccess) return@withContext result

            // ── 如果失败原因是未登录 → 执行 CAS ticket 交换 ──
            val cause = result.exceptionOrNull()
            if (cause is NotLoggedInException) {
                android.util.Log.d(TAG, "未登录，执行 CAS ticket 交换后重试")
                doCasTicketExchange(client, cookieReader)
                // ── 第 2 次请求（重试） ──
                result = doFetchStudentInfo(client)
                if (result.isSuccess) return@withContext result
            }

            // 最终失败
            result
        } catch (e: SessionExpiredException) {
            android.util.Log.e(TAG, "会话已过期", e)
            Result.failure(e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取学生信息失败", e)
            Result.failure(e)
        }
    }

    /** 内部：执行一次 POST 请求，不重试 */
    private fun doFetchStudentInfo(client: OkHttpClient): Result<StudentInfo> {
        return try {
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

                android.util.Log.d(TAG, "API 响应(前200): ${responseBody.take(200)}")

                // ⚠️ 检查未登录响应（body 是 JSON，不是 HTML，不能用 SessionChecker）
                if (isNotLoggedIn(responseBody)) {
                    android.util.Log.w(TAG, "API 返回 WEC-HASLOGIN:false，未登录")
                    throw NotLoggedInException()
                }

                if (SessionChecker.isCasLoginPage(responseBody)) {
                    throw SessionExpiredException("会话已过期，请重新登录")
                }

                if (!response.isSuccessful) {
                    val msg = "HTTP ${response.code}: ${response.message}"
                    if (response.code in listOf(401, 403)) {
                        throw SessionExpiredException(msg)
                    }
                    throw Exception(msg)
                }

                val infoResponse = gson.fromJson(responseBody, StudentInfoResponse::class.java)
                if (infoResponse.code != "0") {
                    throw Exception("获取学生信息失败: ${infoResponse.message}")
                }

                infoResponse.datas?.firstOrNull()
                    ?: throw Exception("学生信息为空")
            }

            android.util.Log.d(TAG, "学生信息获取成功: ${info.userName}(${info.userId})")
            Result.success(info)
        } catch (e: NotLoggedInException) {
            Result.failure(e)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取菜单列表。
     * 先调用 fetchStudentInfo() 确保已登录，再请求菜单。
     */
    suspend fun fetchMenuList(): Result<List<MenuCategory>> = withContext(Dispatchers.IO) {
        try {
            // 先确保已登录（复用 fetchStudentInfo 的认证逻辑）
            val authResult = fetchStudentInfo()
            if (authResult.isFailure && authResult.exceptionOrNull() !is NotLoggedInException) {
                // 如果不是未登录错误，直接返回失败
                return@withContext Result.failure(authResult.exceptionOrNull()!!)
            }

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
                if (SessionChecker.isCasLoginPage(responseBody)) {
                    throw SessionExpiredException("会话已过期，请重新登录")
                }

                val menuResponse = gson.fromJson(responseBody, MenuListResponse::class.java)
                if (menuResponse.code != "0") {
                    throw Exception("获取菜单失败: ${menuResponse.message}")
                }
                menuResponse.datas ?: emptyList()
            }

            Result.success(list)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取菜单列表失败", e)
            Result.failure(e)
        }
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
