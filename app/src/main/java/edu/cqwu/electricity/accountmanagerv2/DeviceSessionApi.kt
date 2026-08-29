package edu.cqwu.electricity.accountmanagerv2

import com.google.gson.Gson
import edu.cqwu.electricity.login.data.HtmlFormParser
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/**
 * 登录设备管理数据模型：一条在线会话。
 *
 * 对应 authserver userOnline.do 页面表格行：
 * 用户IP（IPv6 + IPv4 两个链接）/ 登入时间 / 认证类型 / 客户端类型（UA）/ 操作。
 */
data class DeviceSession(
    /** 会话 UUID（removeOnlineUser.do 的 tokenId 参数） */
    val id: String,
    /** IPv6 地址（页面可能同时给出 IPv6 与 IPv4，优先展示 IPv6） */
    val ipv6: String?,
    /** IPv4 地址 */
    val ipv4: String?,
    /** 登入时间（HTML 页面提取的原始文本，如 2026-08-29 16:07:03，不转换） */
    val loginTimeText: String,
    /** 认证类型（免登录 / 密码等，页面按此分组） */
    val authType: String,
    /** 客户端类型（UA 文本） */
    val clientType: String,
    /** 是否为当前浏览器（当前会话不可踢出） */
    val isCurrent: Boolean,
)

/**
 * CAS 登录设备管理 API。
 *
 * 对应抓包接口：
 * - GET  userOnline.do      → HTML 表格：按认证类型分组列出全部在线会话
 * - POST removeOnlineUser.do → {"res":"success"} 踢出指定会话（tokenId=会话UUID）
 *
 * 无状态设计：每次调用用账号 cookie 构建隔离的 UserCookieStore + OkHttpClient
 * （同 UserNameEditApi / PasswordChangeApi 模式），响应为 CAS 登录页时抛 [SessionExpiredException]。
 */
class DeviceSessionApi {

    private val gson = Gson()

    companion object {
        private const val TAG = "DeviceSessionApi"
        private const val ONLINE_URL = "https://authserver.cqwu.edu.cn/authserver/userOnline.do"
        private const val REMOVE_URL = "https://authserver.cqwu.edu.cn/authserver/removeOnlineUser.do"

        /** 会话 UUID：removeOnline('uuid') */
        private val ID_REGEX = Regex("removeOnline\\s*\\(\\s*'([0-9a-fA-F-]+)'\\s*\\)")
        /** 登入时间：yyyy-MM-dd HH:mm:ss */
        private val TIME_REGEX = Regex("""(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        /** 当前浏览器标记（操作列文字，无踢出按钮） */
        private const val CURRENT_MARKER = "当前浏览器"
    }

    /**
     * 加载当前账号的全部在线会话。
     *
     * @return 按页面顺序返回会话列表（含当前会话，UI 层决定是否展示踢出按钮）
     * @throws SessionExpiredException 会话失效（响应为 CAS 登录页）
     */
    suspend fun loadSessions(cookies: Map<String, Map<String, String>>): Result<List<DeviceSession>> =
        withContext(Dispatchers.IO) {
            try {
                val html = HttpClientFactory.createIsolated(cookies).newCall(
                    Request.Builder()
                        .url(ONLINE_URL)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body.string()
                }
                HtmlFormParser.checkAndThrow(html)
                Result.success(parseSessions(html))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "加载在线会话失败", e)
                Result.failure(e)
            }
        }

    /**
     * 踢出指定在线会话（服务端注销该会话，被踢设备需重新登录）。
     *
     * @param sessionId 会话 UUID（[DeviceSession.id]）
     * @return 仅服务端返回 `{"res":"success"}` 视为成功
     */
    suspend fun removeSession(cookies: Map<String, Map<String, String>>, sessionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = HttpClientFactory.createIsolated(cookies).newCall(
                    Request.Builder()
                        .url(REMOVE_URL)
                        .post(FormBody.Builder().add("tokenId", sessionId).build())
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body.string()
                }
                HtmlFormParser.checkAndThrow(body)
                val result = gson.fromJson(body, RemoveResponse::class.java)
                if (result.res != "success") {
                    throw IOException("踢出失败: res=${result.res}")
                }
                AppLog.d(TAG, "踢出会话成功: $sessionId")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "踢出会话失败: $sessionId", e)
                Result.failure(e)
            }
        }

    // ═══════════════════════════════════════════
    //  HTML 解析
    // ═══════════════════════════════════════════

    /**
     * 解析 userOnline.do 页面：遍历 <tr> 数据行，提取会话字段。
     *
     * 列结构：用户IP（IPv6+IPv4 链接）| 登入时间 | 认证类型 | 客户端类型 | 操作
     * 操作列：当前会话显示"当前浏览器"，其他会话含 removeOnline('uuid')。
     * 无记录行（<td colspan="4">无记录。</td>）与 thead 列头行自动跳过。
     */
    private fun parseSessions(html: String): List<DeviceSession> {
        val sessions = mutableListOf<DeviceSession>()
        HtmlFormParser.htmlRows(html).forEach { rowHtml ->
            val tds = HtmlFormParser.htmlCells(rowHtml)
            if (tds.size < 4) return@forEach

            val opCell = tds.getOrNull(4).orEmpty()
            val sessionId = ID_REGEX.find(rowHtml)?.groupValues?.getOrNull(1)
                ?: return@forEach // 操作列无 removeOnline 且非当前行 → 跳过

            val isCurrent = opCell.contains(CURRENT_MARKER)
            val ips = HtmlFormParser.extractIpLinks(rowHtml)
            val timeText = TIME_REGEX.find(rowHtml)?.groupValues?.getOrNull(1)

            sessions += DeviceSession(
                id = sessionId,
                ipv6 = ips.firstOrNull { it.contains(':') },
                ipv4 = ips.firstOrNull { !it.contains(':') },
                loginTimeText = timeText.orEmpty(),
                authType = tds.getOrNull(2)?.trim().orEmpty(),
                clientType = tds.getOrNull(3)?.trim().orEmpty(),
                isCurrent = isCurrent,
            )
        }
        return sessions
    }

    private data class RemoveResponse(val res: String = "")
}
