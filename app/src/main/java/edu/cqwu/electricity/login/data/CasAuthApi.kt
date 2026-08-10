package edu.cqwu.electricity.login.data

import android.util.Log
import edu.cqwu.electricity.payment.data.HttpClientFactory
import edu.cqwu.electricity.login.data.UserAwareCookieJar
import edu.cqwu.electricity.login.data.UserCookieStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException

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
        val tempClient = HttpClientFactory.create(
            cookieJar = UserAwareCookieJar(tempStore),
            followRedirects = true,
        )

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
            Log.d("CasAuthApi", "开始CAS登录$tag: GET $LOGIN_URL")

            val outcome = CasLoginFlow.login(
                client = client,
                loginPageUrl = LOGIN_URL,
                username = username,
                password = password,
                extraHeaders = emptyMap(),
                enlinkVpn = false,
                existingHtml = null,
            )

            val t4 = System.currentTimeMillis()
            Log.d("CasAuthApi", "登录POST响应$tag: code=${outcome.responseCode}, location=${outcome.location}")

            val cookieString = cookieProvider(LOGIN_URL)
            val castgc = CookieParser.getValue(cookieString, "CASTGC")

            if (castgc == null) {
                Log.e("CasAuthApi", "未获取到 CASTGC$tag, 共有${cookieString.split(";").size}个Cookie")
                throw RuntimeException("登录失败：未能获取到 CASTGC Cookie，请检查账号或密码")
            }

            val t5 = System.currentTimeMillis()
            Log.d("CasAuthApi", "登录总耗时$tag: ${t5 - t0}ms, CASTGC=$castgc")

            Result.success(LoginResult(
                username = username,
                cookieString = cookieString
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e("CasAuthApi", "登录失败$tag", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("CasAuthApi", "登录失败$tag", e)
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
