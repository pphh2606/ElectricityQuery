package edu.cqwu.electricity.accountmanagerv2

import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.net.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * 登录日志数据模型：一条认证/维护记录。
 *
 * 对应 authserver userLogs.do 页面表格行：
 * 用户IP（IPv6 + IPv4 链接）/ 登入时间 / 登出时间 / 认证类型 / 客户端类型 / 认证结果。
 */
data class LoginRecord(
    /** IPv6 地址（页面可能同时给出 IPv6 与 IPv4） */
    val ipv6: String?,
    /** IPv4 地址 */
    val ipv4: String?,
    /** 登入时间（HTML 原始文本，如 2026-08-29 16:07:03，不转换） */
    val loginTimeText: String,
    /** 登出时间（HTML 原始文本，当前在线会话可能为空） */
    val logoutTimeText: String,
    /** 认证类型（免登录 / 密码等） */
    val authType: String,
    /** 客户端类型（UA 文本） */
    val clientType: String,
    /** 认证结果（成功 / 失败） */
    val result: String,
)

/**
 * 登录日志分页结果：一页记录 + 分页信息。
 */
data class LoginLogPage(
    val records: List<LoginRecord>,
    val currentPage: Int,
    val totalPages: Int,
    val totalCount: Int,
)

/**
 * CAS 登录日志 API。
 *
 * 对应抓包接口：GET userLogs.do?operType=&result=&startTime=&endTime=&pageIndex=
 * - operType：0=认证记录 1=帐号维护 2=密码维护 3=应用访问
 * - result：空=全部 1=成功 0=失败
 * - startTime/endTime：查询起止日期（yyyy-MM-dd），endTime 可空
 * - pageIndex：页码（1 起，每页 10 条）；分页统计由 HTML 控件文本 "30/3"（总条数/总页数）解析
 *
 * 无状态设计：每次调用用账号 cookie 构建隔离的 UserCookieStore + OkHttpClient
 * （同 DeviceSessionApi / UserNameEditApi 模式），响应为 CAS 登录页时抛 [SessionExpiredException]。
 */
class LoginLogApi {

    companion object {
        private const val TAG = "LoginLogApi"
        private const val LOG_URL = "https://authserver.cqwu.edu.cn/authserver/userLogs.do"

        /** 分页统计：分页控件 span 文本 "30/3"（总条数/总页数） */
        private val PAGE_INFO_REGEX = Regex("""(\d+)\s*/\s*(\d+)""")
    }

    /**
     * 按筛选条件加载指定页的登录日志。
     *
     * @param operType 类型（0 认证记录 / 1 帐号维护 / 2 密码维护 / 3 应用访问）
     * @param result 结果（空=全部 / 1 成功 / 0 失败）
     * @param startTime 开始日期（yyyy-MM-dd，默认 1970-01-01）
     * @param endTime 结束日期（yyyy-MM-dd，可为空）
     * @param pageIndex 页码（从 1 开始，每页 10 条）
     * @throws SessionExpiredException 会话失效（响应为 CAS 登录页）
     */
    suspend fun loadLogs(
        cookies: Map<String, Map<String, String>>,
        operType: String,
        result: String,
        startTime: String,
        endTime: String,
        pageIndex: Int,
    ): Result<LoginLogPage> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("authserver.cqwu.edu.cn")
                .addPathSegments("authserver/userLogs.do")
                .addQueryParameter("operType", operType)
                .addQueryParameter("result", result)
                .addQueryParameter("startTime", startTime)
                .addQueryParameter("endTime", endTime)
                .addQueryParameter("pageIndex", pageIndex.toString())
                .build()
            val html = HttpClientFactory.createIsolated(cookies).newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body.string()
            }
            HtmlFormParser.checkAndThrow(html)
            val records = parseRecords(html)
            val (totalCount, totalPages) = parsePageInfo(html)
            Result.success(
                LoginLogPage(
                    records = records,
                    currentPage = pageIndex,
                    totalPages = totalPages.takeIf { it > 0 } ?: pageIndex,
                    totalCount = totalCount,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "加载登录日志失败: pageIndex=$pageIndex", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════
    //  HTML 解析
    // ═══════════════════════════════════════════

    /**
     * 解析 userLogs.do 页面：遍历 <tr> 数据行，提取 6 列字段。
     *
     * 列结构：用户IP（IPv6+IPv4 链接）| 登入时间 | 登出时间 | 认证类型 | 客户端类型 | 认证结果
     * 无记录行（<td colspan="6">无记录。</td>）与 thead 列头行自动跳过。
     */
    private fun parseRecords(html: String): List<LoginRecord> {
        val records = mutableListOf<LoginRecord>()
        HtmlFormParser.htmlRows(html).forEach { rowHtml ->
            val tds = HtmlFormParser.htmlCells(rowHtml)
            if (tds.size < 6) return@forEach

            val ips = HtmlFormParser.extractIpLinks(rowHtml)

            records += LoginRecord(
                ipv6 = ips.firstOrNull { it.contains(':') },
                ipv4 = ips.firstOrNull { !it.contains(':') },
                loginTimeText = tds[1],
                logoutTimeText = tds[2],
                authType = tds[3],
                clientType = tds[4],
                result = tds[5],
            )
        }
        return records
    }

    /**
     * 解析分页统计：分页控件 span 文本 "30/3"（总条数/总页数）。
     *
     * @return Pair(总条数, 总页数)；解析失败返回 (0, 0)，由调用方回退为当前页
     */
    private fun parsePageInfo(html: String): Pair<Int, Int> {
        val match = PAGE_INFO_REGEX.find(html) ?: return 0 to 0
        val total = match.groupValues[1].toIntOrNull() ?: 0
        val pages = match.groupValues[2].toIntOrNull() ?: 0
        return total to pages
    }
}
