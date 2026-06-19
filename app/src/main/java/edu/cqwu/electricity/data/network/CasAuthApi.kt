package edu.cqwu.electricity.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * CAS 统一认证登录 API
 * 对应 Python 参考登录流程中的 login() 函数
 *
 * 专用于 CAS 层认证，仅负责向 authserver.cqwu.edu.cn 发送登录请求获取 CASTGC Cookie。
 * epay 层的 JSESSIONID 获取和 ticket 交换由 QrCodeApi 通过 followRedirects 自动完成。
 *
 * 流程：
 * 1. GET LOGIN_URL → 直接获取 CAS 登录页
 * 2. 解析登录页 HTML 提取 salt、lt、execution、dllt
 * 3. AES-CBC 加密密码
 * 4. POST 表单到 LOGIN_URL
 * 5. 从 Cookie 中提取 CASTGC
 *
 * 所有登录均使用用户隔离的 Cookie 存储（UserCookieStore + UserAwareCookieJar），
 * 与系统 CookieManager 完全隔离，避免多用户串号。
 */

class CasAuthApi {

    companion object {
        /** CAS 统一认证登录页 */
        const val LOGIN_URL = "https://authserver.cqwu.edu.cn/authserver/login"
    }


    /**
     * 为指定用户执行 CAS 登录。
     *
     * 使用隔离的临时 UserCookieStore，与持久存储完全隔离：
     * - 登录过程中所有 Cookie 写入临时存储
     * - 登录失败时临时存储随对象销毁，不影响持久存储
     * - 登录成功后由调用方通过 AccountManager.commitLoginCookies() 迁移到持久存储
     */
    suspend fun loginForUser(username: String, password: String): Result<LoginResult> {
        // 每次登录创建全新的隔离 Cookie 存储，避免脏 Cookie 残留
        val tempStore = UserCookieStore()
        val tempClient = HttpClientFactory.createNoRedirect(
            cookieJar = UserAwareCookieJar(tempStore)
        ).newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        return performLogin(username, password, tempClient, tag = "(user)") { url ->
            tempStore.getCookie(url) ?: ""
        }.also { result ->
            // 登录成功时，将临时存储附加到结果中供调用方提交
            result.getOrNull()?.cookieStore = tempStore
        }
    }

    /**
     * CAS 登录核心实现。
     *
     * @param username    学号
     * @param password    密码
     * @param client      用于 HTTP 请求的 OkHttpClient
     * @param tag         日志标签后缀
     * @param cookieProvider 从指定 URL 获取 Cookie 字符串的函数
     * @return Result<LoginResult>
     */
    private suspend fun performLogin(
        username: String,
        password: String,
        client: OkHttpClient,
        tag: String,
        cookieProvider: (String) -> String
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()

            // === 诊断日志：记录开始登录时的 Cookie 状态 ===
            val preLoginCookie = cookieProvider(LOGIN_URL)
            android.util.Log.d("CasAuthApi", "登录前Cookie状态$tag: $preLoginCookie")

            // 步骤 1：直接 GET CAS 登录页
            android.util.Log.d("CasAuthApi", "步骤1$tag: GET $LOGIN_URL")
            val loginPageResp = client.newCall(
                Request.Builder().url(LOGIN_URL).get().build()
            ).execute()

            // === 诊断日志：记录 GET 响应详情 ===
            val loginPageFinalUrl = loginPageResp.request.url.toString()
            val loginPageCode = loginPageResp.code
            android.util.Log.d("CasAuthApi", "步骤1响应$tag: code=$loginPageCode, finalUrl=$loginPageFinalUrl")

            val loginPageHtml = loginPageResp.body.string()
            val t1 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤1耗时$tag: ${t1 - t0}ms，登录页HTML长度: ${loginPageHtml.length}")

            // === 诊断日志：HTML 头部快照（检查是否包含关键字段）===
            val htmlHeadPreview = loginPageHtml.take(300)
            android.util.Log.d("CasAuthApi", "步骤1 HTML头部预览$tag: $htmlHeadPreview")

            // 步骤 2：解析登录页参数
            val salt = HtmlFormParser.extractRegex(loginPageHtml, """var pwdDefaultEncryptSalt = "(.+?)"""")
                ?: throw RuntimeException("无法获取加密 salt")
            val lt = HtmlFormParser.extractInputValue(loginPageHtml, "lt")
                ?: throw RuntimeException("无法获取 lt")
            val execution = HtmlFormParser.extractInputValue(loginPageHtml, "execution")
                ?: throw RuntimeException("无法获取 execution")
            val dllt = HtmlFormParser.extractInputValue(loginPageHtml, "dllt") ?: ""

            val t2 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤2耗时$tag: ${t2 - t1}ms, salt=$salt, lt=$lt, execution=$execution, dllt=$dllt")

            // 步骤 3：AES-CBC 加密密码
            val encryptedPassword = AesEncrypt.encryptPassword(password, salt)
            val t3 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤3耗时$tag: ${t3 - t2}ms")

            // 步骤 4：POST 登录表单
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", encryptedPassword)
                .add("captchaResponse", "")
                .add("rememberMe", "on")
                .add("lt", lt)
                .add("dllt", dllt)
                .add("execution", execution)
                .add("_eventId", "submit")
                .add("rmShown", "1")
                .build()

            // === 诊断日志：POST 前记录 ===
            android.util.Log.d("CasAuthApi", "步骤4$tag: POST $LOGIN_URL (username=$username, lt前4位=${lt.take(4)})")

            val loginResp = client.newCall(
                Request.Builder()
                    .url(LOGIN_URL)
                    .post(formBody)
                    .build()
            ).execute()

            val t4 = System.currentTimeMillis()

            // === 诊断日志：POST 响应详情 ===
            val postFinalUrl = loginResp.request.url.toString()
            val postResponseBody = loginResp.body.string()
            val postBodyLen = postResponseBody.length
            android.util.Log.d("CasAuthApi", "步骤4耗时$tag: ${t4 - t3}ms, 登录响应 code=${loginResp.code}, finalUrl=$postFinalUrl, bodyLen=$postBodyLen")

            // === 诊断日志：POST 后的 Cookie 状态 ===
            val postLoginCookie = cookieProvider(LOGIN_URL)
            android.util.Log.d("CasAuthApi", "登录后Cookie状态$tag: $postLoginCookie")

            // 如果响应体可读，检查是否包含错误提示
            if (postResponseBody.length < 2000) {
                android.util.Log.d("CasAuthApi", "POST响应体内容$tag: ${postResponseBody.take(500)}")
            }

            // 步骤 5：从 Cookie（全局 CookieManager 或 UserCookieStore）中提取 CASTGC
            val cookieString = cookieProvider(LOGIN_URL)
            val castgc = cookieString.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("CASTGC=") }
                ?.substringAfter("CASTGC=")

            if (castgc == null) {
                // === 诊断日志：未获取到 CASTGC，列出所有 Cookie ===
                val allCookies = cookieString.split(";").map { it.trim() }.filter { it.isNotBlank() }
                android.util.Log.e("CasAuthApi", "未获取到 CASTGC$tag, 共有${allCookies.size}个Cookie: ${allCookies.joinToString(", ")}")
                throw RuntimeException("登录失败：未能获取到 CASTGC Cookie，请检查账号或密码")
            }

            val t5 = System.currentTimeMillis()
            android.util.Log.d("CasAuthApi", "步骤5耗时$tag: ${t5 - t4}ms, CASTGC=$castgc")
            android.util.Log.d("CasAuthApi", "登录总耗时$tag: ${t5 - t0}ms")

            Result.success(LoginResult(
                username = username,
                cookieString = cookieString
            ))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            // === 诊断日志：超时时的 Cookie 状态 ===
            val timeoutCookie = try { cookieProvider(LOGIN_URL) } catch (ex: Exception) { "(获取失败: ${ex.message})" }
            android.util.Log.e("CasAuthApi", "=== Socket超时诊断 === 登录失败$tag, 超时时刻Cookie状态: $timeoutCookie")
            android.util.Log.e("CasAuthApi", "=== Socket超时诊断 === 异常信息: ${e.message}")
            android.util.Log.e("CasAuthApi", "登录失败$tag", e)
            Result.failure(e)
        } catch (e: Exception) {
            // === 诊断日志：异常时的 Cookie 状态 ===
            val exceptionCookie = try { cookieProvider(LOGIN_URL) } catch (ex: Exception) { "(获取失败: ${ex.message})" }
            android.util.Log.e("CasAuthApi", "=== 异常诊断 === 登录失败$tag, 异常类型=${e::class.simpleName}, 异常时刻Cookie状态: $exceptionCookie")
            android.util.Log.e("CasAuthApi", "登录失败$tag", e)
            Result.failure(e)
        }
    }

    // HTML 解析已统一使用 HtmlFormParser
}

/**
 * CAS 登录结果
 *
 * @param username 登录用户名（学号）
 * @param cookieString 完整 Cookie 字符串（包含 CASTGC 等），用于展示和调试
 */
data class LoginResult(
    val username: String,
    val cookieString: String,
    /** 登录成功后的临时 Cookie 存储，由调用方通过 AccountManager.commitLoginCookies() 提交到持久存储 */
    var cookieStore: UserCookieStore? = null
)
