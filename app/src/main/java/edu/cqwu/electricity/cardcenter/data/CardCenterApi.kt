package edu.cqwu.electricity.cardcenter.data

import edu.cqwu.electricity.logging.AppLog
import com.google.gson.Gson
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.net.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API 请求封装类，对应 Python 版 ElectricityQuery 类
 *
 * 使用 OkHttp 直接发送 HTTP 请求，通过 Gson 解析 JSON 响应。
 * 所有方法均为 suspend 函数，需在协程中调用。
 */
class CardCenterApi {

    companion object {
        // ==================== EPay 常量 ====================
        /** EPay 基础 URL（修复 C：统一收敛硬编码 IP） */
        private const val EPAY_BASE = "http://218.194.176.214:8382"
        /** EPay 账单查询 URL（HTML 版，支持筛选，速度慢 15-20s） */
        private const val BILL_QUERY_URL = "$EPAY_BASE/epay/consume/query"
        /** EPay 账单详情 URL 前缀 */
        private const val BILL_DETAIL_PREFIX = EPAY_BASE
        /** EPay 第三方应用基础路径 */
        private const val EPAY_THIRDAPP = "$EPAY_BASE/epay/thirdapp"
        /** H5 版账单 JSON API（速度快 ~3s，仅支持分页，不支持筛选） */
        private const val H5_BILL_API = "$EPAY_BASE/epay/thirdapp/loadbill.json"

        /** HTML 版账单查询专用 Client（超时 30 秒，基于 HttpClientFactory.shared 构建确保 CookieJar 同步） */
        private val billHtmlClient: OkHttpClient by lazy {
            HttpClientFactory.shared.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Builds the full bill detail URL.
         */
        fun getBillDetailUrl(relativePath: String): String {
            return "$BILL_DETAIL_PREFIX$relativePath"
        }

    }

    private val gson = Gson()

    suspend fun fetchAccountInfo(): Result<AccountInfo> = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val url = "$EPAY_THIRDAPP/balance"
            AppLog.d("CardCenterApi", "获取账户信息: GET $url")

            val response = HttpClientFactory.shared.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()

            // 检查是否被重定向到 CAS 登录页
            HtmlFormParser.checkAndThrow(html)

            // 解析 HTML 提取字段
            val accountInfo = parseAccountInfoHtml(html)
            val elapsed = System.currentTimeMillis() - t0
            AppLog.d("CardCenterApi", "获取账户信息成功: 耗时=${elapsed}ms, $accountInfo")
            Result.success(accountInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            AppLog.e("CardCenterApi", "获取账户信息失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从 EPay 账户信息 HTML 中解析各字段。
     *
     * HTML 结构示例：
     * <div class="weui-cell">
     *   <div class="weui-cell__bd"><p>姓名</p></div>
     *   <div class="weui-cell__ft">示例用户</div>
     * </div>
     */
    private fun parseAccountInfoHtml(html: String): AccountInfo {
        fun extractValue(label: String): String {
            val escapedLabel = Regex.escape(label)
            val regex = Regex(
                """<p>\s*$escapedLabel\s*</p>\s*</div>\s*<div class="weui-cell__ft"[^>]*>([^<]*)""",
                RegexOption.IGNORE_CASE
            )
            return regex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: ""
        }

        return AccountInfo(
            name = extractValue("姓名"),
            studentId = extractValue("学工号"),
            balance = extractValue("账户余额"),
            school = extractValue("学校"),
            major = extractValue("专业"),
            className = extractValue("班级")
        )
    }

    // ==================== 卡挂失相关 API ====================

    /**
     * 获取卡挂失页面的卡信息（通过 HTML 解析）
     *
     * GET http://218.194.176.214:8382/epay/thirdapp/cardlost
     * 使用 HttpClientFactory.shared（与 QrCodeApi 共享同一 CookieJar），
     * 自动完成 CAS ticket 交换获取 JSESSIONID。
     *
     * HTML 结构示例（与账户信息页面一致）：
     * <div class="weui-cells">
     *   <div class="weui-cell">
     *     <div class="weui-cell__bd"><p>卡号</p></div>
     *     <div class="weui-cell__ft">127319</div>
     *   </div>
     *   <div class="weui-cell">
     *     <div class="weui-cell__bd"><p>卡状态</p></div>
     *     <div class="weui-cell__ft" style="color: red;">正常</div>
     *   </div>
     * </div>
     */
    suspend fun fetchCardLostInfo(): Result<CardLostInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$EPAY_THIRDAPP/cardlost"
            AppLog.d("CardCenterApi", "获取卡挂失信息: GET $url")

            val response = HttpClientFactory.shared.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()

            HtmlFormParser.checkAndThrow(html)

            val cardInfo = parseCardLostInfoHtml(html)
            AppLog.d("CardCenterApi", "卡挂失信息: $cardInfo")
            Result.success(cardInfo)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            AppLog.e("CardCenterApi", "获取卡挂失信息失败", e)
            Result.failure(e)
        }
    }

    /**
     * 执行卡挂失
     *
     * POST http://218.194.176.214:8382/epay/thirdapp/docardlost.json
     * 使用 HttpClientFactory.shared（共享 CookieJar）。
     * 请求体为空 JSON: {}
     *
     * 成功响应: { retcode: "0", retmsg: "挂失成功" }
     * 失败响应: { retcode: "xxx", retmsg: "错误信息" }
     */
    suspend fun doCardLost(): Result<CardLostResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$EPAY_THIRDAPP/docardlost.json"
            AppLog.d("CardCenterApi", "执行卡挂失: POST $url")

            val jsonBody = "{}"
            val requestBody =
                jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaType())

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .addHeader("X-Requested-With", "XMLHttpRequest")

            val response = HttpClientFactory.shared.newCall(requestBuilder.build()).execute()

            val body = response.body.string()

            AppLog.body("CardCenterApi", "卡挂失响应: $body")

            val cardLostResponse = gson.fromJson(body, CardLostResponse::class.java)
            Result.success(cardLostResponse)
        } catch (e: Exception) {
            AppLog.e("CardCenterApi", "执行卡挂失失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从卡挂失 HTML 页面中解析卡号和卡状态。
     *
     * 复用与 parseAccountInfoHtml 相同的正则提取逻辑。
     */
    private fun parseCardLostInfoHtml(html: String): CardLostInfo {
        fun extractValue(label: String): String {
            val escapedLabel = Regex.escape(label)
            val regex = Regex(
                """<p>\s*$escapedLabel\s*</p>\s*</div>\s*<div class="weui-cell__ft"[^>]*>([^<]*)""",
                RegexOption.IGNORE_CASE
            )
            return regex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: ""
        }

        return CardLostInfo(
            cardNumber = extractValue("卡号"),
            cardStatus = extractValue("卡状态")
        )
    }

    /**
     * 获取全部 4 个标签页的账单数据（一次请求，四区解析）。
     *
     * 服务器 HTML API 总是返回包含全部 4 个 zone 的完整 HTML。
     * 此方法一次解析全部 zone：zone_show_box_1（全部）、zone_show_box_2（未付款）、
     * zone_show_box_4（成功）、zone_show_box_5（失败）。
     *
     * @param filter 筛选条件（仅筛选相关字段生效，tabNo 被忽略）
     * @return Map<tabNo, BillPageInfo> 包含全部 4 个标签页的数据
     */
    suspend fun fetchBillsAllZones(filter: BillFilter): Result<Map<Int, BillPageInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val html = postBillQuery(filter)
                HtmlFormParser.checkAndThrow(html)
                val allZones = parseAllZones(html)
                AppLog.d("CardCenterApi", "四区解析完成: zone数量=${allZones.size}")
                Result.success(allZones)
            } catch (e: SessionExpiredException) {
                Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLog.e("CardCenterApi", "获取账单失败", e)
                Result.failure(e)
            }
        }

    /**
     * 执行账单查询的 HTTP POST 请求，返回原始 HTML。
     */
    private fun postBillQuery(filter: BillFilter): String {
        val formBuilder = FormBody.Builder()
            .add("pageNo", filter.pageNo.toString())
            .add("tabNo", filter.tabNo.toString())
            .add("pager.offset", ((filter.pageNo - 1) * 10).toString())

        if (filter.tradeName.isNotBlank()) {
            formBuilder.add("tradename", filter.tradeName)
        }
        if (filter.startTime.isNotBlank()) {
            formBuilder.add("starttime", filter.startTime)
        }
        if (filter.endTime.isNotBlank()) {
            formBuilder.add("endtime", filter.endTime)
        }
        formBuilder.add("timetype", filter.timeType.toString())

        for (direct in filter.tradeDirect) {
            formBuilder.add("tradedirect", direct.toString())
        }

        val requestBody = formBuilder.build()

        AppLog.d("CardCenterApi", "获取账单: POST $BILL_QUERY_URL, filter=$filter")

        val response = billHtmlClient.newCall(
            Request.Builder()
                .url(BILL_QUERY_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()
        ).execute()

        return response.body.string()
    }

    /**
     * 使用 H5 JSON API 获取账单（速度快，约 3 秒）。
     *
     * 通过 HttpClientFactory.shared（共享 CookieJar）自动携带 JSESSIONID。
     *
     * POST http://218.194.176.214:8382/epay/thirdapp/loadbill.json
     * Content-Type: application/x-www-form-urlencoded; charset=UTF-8
     * Body: pageno=1
     * Response JSON: { "pageno":1, "totalpage":10, "dtls":[{...}], "retcode":"0" }
     *
     * @param pageNo 页码（从 1 开始）
     * @return H5BillResponse 包含记录列表和分页信息
     */
    suspend fun fetchBillsH5(pageNo: Int): Result<H5BillResponse> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("pageno", pageNo.toString())
                .build()

            AppLog.d("CardCenterApi", "H5获取账单: POST $H5_BILL_API, pageno=$pageNo")

            val response = HttpClientFactory.shared.newCall(
                Request.Builder()
                    .url(H5_BILL_API)
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Referer", "$EPAY_BASE/epay/thirdapp/bill")
                    .build()
            ).execute()

            val json = response.body.string()

            // 检查是否被重定向到 CAS 登录页
            HtmlFormParser.checkAndThrow(json)

            val h5Response = gson.fromJson(json, H5BillResponse::class.java)
            // 修复 1：校验 retcode，服务器返回错误码时抛异常
            if (h5Response.retcode != "0") {
                throw RuntimeException(
                    h5Response.retmsg ?: "H5 账单接口错误 (retcode=${h5Response.retcode})"
                )
            }
            AppLog.d(
                "CardCenterApi", "H5账单解析完成: ${h5Response.dtls?.size ?: 0}条, " +
                        "页码=${h5Response.pageno}/${h5Response.totalpage}, retcode=${h5Response.retcode}"
            )
            Result.success(h5Response)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e("CardCenterApi", "H5获取账单失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从账单 HTML 中解析全部 4 个 zone 的交易记录和分页信息。
     *
     * HTML 结构（由响应文件 [点击未付款的响应.txt] 确认）：
     * - 服务器始终返回全部 4 个 zone，无论提交的 tabNo 是什么
     * - 每个 zone 的 span id 为：zone_show_box_1, zone_show_box_2, zone_show_box_4, zone_show_box_5
     * - 每个 zone 内包含 <table> → <tbody> → <tr> 记录
     * - 每个 zone 的页脚独立包含 fontred 分页信息（或空底板）
     *
     * fontred 检查限定在 zone 内部，避免其他 zone 的分页导致误报。
     *
     * @param html 服务端返回的完整 HTML
     * @return Map<tabNo, BillPageInfo>
     */
    private fun parseAllZones(html: String): Map<Int, BillPageInfo> {
        val result = mutableMapOf<Int, BillPageInfo>()
        // tabNo → zone ID 映射
        val zoneMap = mapOf(
            1 to "zone_show_box_1",
            2 to "zone_show_box_2",
            4 to "zone_show_box_4",
            5 to "zone_show_box_5"
        )

        val rowRegex = Regex(
            """<tr[^>]*>(.*?)</tr>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val paginationRegex = Regex(
            """当前\s*<b[^>]*class="fontred"[^>]*>\s*(\d+)\s*</b>\s*/\s*(\d+)\s*页""",
            RegexOption.IGNORE_CASE
        )

        for ((tabNo, zoneId) in zoneMap) {
            // 提取 zone 内容：以注释 <!-- @end of zone [$zoneId]@ --> 为边界，
            // 避免内层 <span> 的 </span> 导致 (.*?) 非贪婪匹配提前截断。
            val zoneRegex = Regex(
                """id="aazone\.$zoneId"[^>]*>(.*?)<!-- @end of zone \[$zoneId\]@ -->""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val zoneContent = zoneRegex.find(html)?.groupValues?.get(1) ?: continue

            // 从 zone 内提取 tbody
            val tbodyRegex = Regex(
                """<tbody[^>]*>(.*?)</tbody>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val tbodyContent = tbodyRegex.find(zoneContent)?.groupValues?.get(1) ?: ""

            // 解析 <tr> 记录
            val records = mutableListOf<BillRecord>()
            for (rowMatch in rowRegex.findAll(tbodyContent)) {
                parseBillRow(rowMatch.groupValues[1])?.let { records.add(it) }
            }

            // 从 zone 内提取分页信息（仅检查当前 zone，避免误报）
            val (currentPage, totalPages) = paginationRegex.find(zoneContent)?.let {
                (it.groupValues[1].toIntOrNull() ?: 1) to (it.groupValues[2].toIntOrNull() ?: 1)
            } ?: (1 to 1)

            AppLog.d("CardCenterApi", "解析 zone[$zoneId]: ${records.size}条, 第${currentPage}/${totalPages}页")

            result[tabNo] = BillPageInfo(
                records = records,
                currentPage = currentPage,
                totalPages = totalPages
            )
        }

        if (result.isEmpty()) {
            throw RuntimeException("账单页面结构已变更：找不到任何 zone 数据，请检查 HTML 或更新 App")
        }

        return result
    }

    /** 辅助函数：剥离 HTML 标签，提取纯文本内容 */
    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("""<[^>]*>"""), "").trim()
    }

    /**
     * 解析单行 <tr> HTML，返回 BillRecord（修复 B：使用 (.*?) + 内层标签剥离）。
     */
    private fun parseBillRow(rowHtml: String): BillRecord? {
        try {
            // 提取所有 <td> 内容（使用非贪婪 .*? + DOT_MATCHES_ALL）
            val tdRegex = Regex(
                """<td[^>]*>(.*?)</td>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val tds = tdRegex.findAll(rowHtml).map { it.groupValues[1].trim() }.toList()

            // 需要有至少 7 个 <td>
            if (tds.size < 7) return null

            // 第1个 td: 日期和时间（修复 B：.*? + 剥离内层标签）
            val dateRegex = Regex("""<div[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val dateMatch = dateRegex.find(tds[0])
            val date = stripHtmlTags(dateMatch?.groupValues?.getOrNull(1) ?: "")

            val timeRegex = Regex(
                """<div class="span_2"[^>]*>(.*?)</div>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val timeMatch = timeRegex.find(tds[0])
            val time = stripHtmlTags(timeMatch?.groupValues?.getOrNull(1) ?: "")

            // 第2个 td: 交易类型（从 <a class="span_1"> 中提取）
            val typeRegex = Regex(
                """<a[^>]*class="span_1"[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val type = stripHtmlTags(typeRegex.find(tds[1])?.groupValues?.getOrNull(1) ?: "")

            // 提取详情 URL
            val urlRegex = Regex(
                """<a\s+href="([^"]+)"[^>]*class="span_1"[^>]*>""",
                RegexOption.IGNORE_CASE
            )
            val detailUrl = urlRegex.find(tds[1])?.groupValues?.getOrNull(1)?.trim() ?: ""

            // 提取交易号
            val billNoRegex = Regex("""交易号[：:](.*?)(?:<|$)""")
            val billNo = stripHtmlTags(billNoRegex.find(tds[1])?.groupValues?.getOrNull(1) ?: "")

            // 第3个 td: 对方
            val merchant = tds[2].replace("&nbsp;", "").trim()

            // 第4个 td: 金额
            val amount = tds[3].replace("&nbsp;", "").trim()

            // 第5个 td: 付款方式
            val paymentMethod = tds[4].replace("&nbsp;", "").trim()

            // 第6个 td: 状态（解析 <span class="label xxx">内容</span>）
            val statusRegex = Regex(
                """<span class="label ([^"]+)"[^>]*>(.*?)</span>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val statusMatch = statusRegex.find(tds[5])
            val statusCssClass = statusMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
            val status = stripHtmlTags(statusMatch?.groupValues?.getOrNull(2) ?: "")
                .ifBlank { tds[5].replace("&nbsp;", "").trim() }

            return BillRecord(
                createDate = date,
                createTime = time,
                type = type,
                billNo = billNo,
                merchant = merchant,
                amount = amount,
                paymentMethod = paymentMethod,
                status = status,
                statusCssClass = statusCssClass,
                detailUrl = detailUrl
            )
        } catch (e: Exception) {
            AppLog.w("CardCenterApi", "解析账单行失败", e)
            return null
        }
    }

    // ==================== 内部方法 ====================

}
