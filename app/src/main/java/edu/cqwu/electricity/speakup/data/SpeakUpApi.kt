package edu.cqwu.electricity.speakup.data

import android.util.Log
import com.google.gson.Gson
import edu.cqwu.electricity.speakup.data.ConsultationArea
import edu.cqwu.electricity.speakup.data.ConsultationAreaResponse
import edu.cqwu.electricity.speakup.data.ConsultationMessage
import edu.cqwu.electricity.speakup.data.MessageListResponse
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import edu.cqwu.electricity.login.data.ServiceLoginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

/**
 * 「有话要说」API 请求封装。
 *
 * 流程与 [HallFavoriteApi] 一致：
 * 1. 先通过 [ServiceLoginManager.ensureLogin] 完成 ehall CAS ticket 交换
 * 2. 再调用业务 API 获取咨询区列表
 *
 * 使用 [HttpClientFactory.shared]（共享 CookieJar，自动携带登录态 Cookie）。
 */
class SpeakUpApi {

    companion object {
        /** ehall 受保护页面 URL（用于触发 CAS ticket 交换） */
        private const val EHALL_APP_URL =
            "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwPsZxzxApp/*default/index.do"

        /** 获取咨询区列表 */
        private const val GET_ZXQ_URL =
            "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwPsZxzxApp/modules/fbzxPageViewController/getZxq.do"

        /** 获取留言列表/详情 */
        private const val GET_ZXXX_URL =
            "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwPsZxzxApp/modules/zxylPageViewController/getZxxx.do"

        /** 获取用户角色列表 */
        private const val ROLES_URL =
            "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwpub/mobile/api/authenticated/funauth/users/roles.do"

        /** 设置用户角色 */
        private const val SETUP_ROLE_URL =
            "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwpub/mobile/api/authenticated/users/setupRole.do"

        private const val APP_ID = "6251198080206918"
        private const val APP_NAME = "lwPsZxzxApp"

        private const val TAG = "SpeakUpApi"
    }

    private val gson = Gson()
    private val client get() = HttpClientFactory.shared

    /**
     * 获取咨询区（科室）列表。
     *
     * @return 成功时返回 [ConsultationArea] 列表
     * @throws SessionExpiredException 用户未登录或 Cookie 过期
     */
    suspend fun fetchConsultationAreas(): Result<List<ConsultationArea>> =
        withContext(Dispatchers.IO) {
            try {
                // 步骤 1：确保 ehall session 已初始化
                Log.d(TAG, "[fetchConsultationAreas] 开始，初始化 ehall session")
                ServiceLoginManager.ensureLogin(protectedUrl = EHALL_APP_URL)
                Log.d(TAG, "[fetchConsultationAreas] ehall session 初始化完成")

                // 步骤 2：POST getZxq.do
                val formBody = FormBody.Builder()
                    .add("data", "{}")
                    .build()

                val request = Request.Builder()
                    .url(GET_ZXQ_URL)
                    .post(formBody)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header(
                        "Referer",
                        "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwPsZxzxApp/*default/index.do"
                    )
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                    )
                    .build()

                Log.d(TAG, "[fetchConsultationAreas] POST $GET_ZXQ_URL")
                val response = client.newCall(request).execute()
                val body = response.body.string()
                Log.d(TAG, "[fetchConsultationAreas] 响应长度=${body.length}")

                val result = gson.fromJson(body, ConsultationAreaResponse::class.java)

                if (result.code != "0") {
                    throw RuntimeException("获取咨询区列表失败：${result.msg}")
                }

                Log.d(TAG, "[fetchConsultationAreas] 成功，共 ${result.data.size} 个咨询区")
                Result.success(result.data)
            } catch (e: SessionExpiredException) {
                Log.w(TAG, "[fetchConsultationAreas] Session 过期: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "[fetchConsultationAreas] 异常", e)
                Result.failure(e)
            }
        }

    /**
     * 预设 ehall 角色，确保 WebView 加载时 menu.do 能返回正确的页面配置。
     *
     * 流程：
     * 1. ensureLogin() → 建立 ehall JSESSIONID
     * 2. roles.do → 获取用户的 ROLEID
     * 3. setupRole.do → 在服务端设置角色
     *
     * 调用此方法后，WebView 加载 index.do 时 menu.do 将返回正确的页面列表，
     * 避免因 ROLEID 为空导致的「无权限访问」问题。
     */
    suspend fun preSetupEhallRole(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[preSetupEhallRole] 开始")
            ServiceLoginManager.ensureLogin(protectedUrl = EHALL_APP_URL)

            // 步骤 1：获取用户角色
            val rolesBody = FormBody.Builder()
                .add("data", """{"APPID":"$APP_ID","APPNAME":"$APP_NAME"}""")
                .build()
            val rolesRequest = Request.Builder()
                .url(ROLES_URL)
                .post(rolesBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", EHALL_APP_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            val rolesResponse = client.newCall(rolesRequest).execute()
            val rolesJson = gson.fromJson(rolesResponse.body.string(), RolesResponse::class.java)

            val roleId = rolesJson.data?.firstOrNull { it.active }?.id
            if (roleId.isNullOrBlank()) {
                Log.w(TAG, "[preSetupEhallRole] 未找到活跃角色")
                return@withContext Result.failure(RuntimeException("未找到活跃角色"))
            }
            Log.d(TAG, "[preSetupEhallRole] 获取到角色: $roleId")

            // 步骤 2：设置角色
            val setupBody = FormBody.Builder()
                .add("data", """{"APPID":"$APP_ID","APPNAME":"$APP_NAME","ROLEID":"$roleId"}""")
                .build()
            val setupRequest = Request.Builder()
                .url(SETUP_ROLE_URL)
                .post(setupBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", EHALL_APP_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            val setupResponse = client.newCall(setupRequest).execute()
            val setupResult = gson.fromJson(setupResponse.body.string(), ConsultationAreaResponse::class.java)

            if (setupResult.code != "0") {
                Log.w(TAG, "[preSetupEhallRole] 设置角色失败: ${setupResult.msg}")
                return@withContext Result.failure(RuntimeException("设置角色失败：${setupResult.msg}"))
            }

            Log.d(TAG, "[preSetupEhallRole] 角色设置成功")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            Log.w(TAG, "[preSetupEhallRole] Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "[preSetupEhallRole] 异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取指定咨询区的留言列表（分页）。
     *
     * @param areaCode 咨询区代码（ZXQDM）
     * @param pageNumber 页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 成功时返回 [ConsultationMessage] 列表
     */
    suspend fun fetchMessages(
        areaCode: String,
        pageNumber: Int = 1,
        pageSize: Int = 10
    ): Result<List<ConsultationMessage>> = withContext(Dispatchers.IO) {
        try {
            ServiceLoginManager.ensureLogin(protectedUrl = EHALL_APP_URL)

            val dataJson = """{"pageNumber":$pageNumber,"pageSize":$pageSize,"ZXLXDM":"100","ZXBT":"","ZXQDM":"$areaCode"}"""
            val formBody = FormBody.Builder()
                .add("data", dataJson)
                .build()

            val request = Request.Builder()
                .url(GET_ZXXX_URL)
                .post(formBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", EHALL_APP_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            Log.d(TAG, "[fetchMessages] POST $GET_ZXXX_URL (area=$areaCode, page=$pageNumber)")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            val result = gson.fromJson(body, MessageListResponse::class.java)
            if (result.code != "0") {
                throw RuntimeException("获取留言列表失败：${result.msg}")
            }

            Log.d(TAG, "[fetchMessages] 成功，共 ${result.data.size} 条留言")
            Result.success(result.data)
        } catch (e: SessionExpiredException) {
            Log.w(TAG, "[fetchMessages] Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "[fetchMessages] 异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取单条留言详情（通过 WID）。
     *
     * @param wid 留言的唯一标识
     * @return 成功时返回 [ConsultationMessage]
     */
    suspend fun fetchMessageDetail(wid: String): Result<ConsultationMessage> = withContext(Dispatchers.IO) {
        try {
            ServiceLoginManager.ensureLogin(protectedUrl = EHALL_APP_URL)

            val dataJson = """{"WID":"$wid"}"""
            val formBody = FormBody.Builder()
                .add("data", dataJson)
                .build()

            val request = Request.Builder()
                .url(GET_ZXXX_URL)
                .post(formBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", EHALL_APP_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build()

            Log.d(TAG, "[fetchMessageDetail] POST $GET_ZXXX_URL (WID=$wid)")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            val result = gson.fromJson(body, MessageListResponse::class.java)
            if (result.code != "0" || result.data.isEmpty()) {
                throw RuntimeException("获取留言详情失败：${result.msg}")
            }

            val msg = result.data.first()
            Log.d(TAG, "[fetchMessageDetail] 成功, zxImageList.size=${msg.zxImageList.size}, hfImagesList.size=${msg.hfImagesList.size}")
            if (msg.zxImageList.isNotEmpty()) {
                msg.zxImageList.forEachIndexed { idx, img ->
                    Log.d(TAG, "[fetchMessageDetail] zxImage[$idx]: id=${img.id}, name=${img.name}, middleUrlFull=${img.middleUrlFull}")
                }
            }
            if (msg.hfImagesList.isNotEmpty()) {
                msg.hfImagesList.forEachIndexed { idx, img ->
                    Log.d(TAG, "[fetchMessageDetail] hfImage[$idx]: id=${img.id}, name=${img.name}, middleUrlFull=${img.middleUrlFull}")
                }
            }
            Result.success(msg)
        } catch (e: SessionExpiredException) {
            Log.w(TAG, "[fetchMessageDetail] Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "[fetchMessageDetail] 异常", e)
            Result.failure(e)
        }
    }
}

/** roles.do 响应 */
private data class RolesResponse(
    @com.google.gson.annotations.SerializedName("code") val code: String = "",
    @com.google.gson.annotations.SerializedName("msg") val msg: String = "",
    @com.google.gson.annotations.SerializedName("data") val data: List<RoleItem>? = null
)

private data class RoleItem(
    @com.google.gson.annotations.SerializedName("id") val id: String = "",
    @com.google.gson.annotations.SerializedName("text") val text: String = "",
    @com.google.gson.annotations.SerializedName("active") val active: Boolean = false
)
