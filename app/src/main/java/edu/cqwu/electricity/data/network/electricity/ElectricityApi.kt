package edu.cqwu.electricity.data.network.electricity

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.data.model.AccountInfo
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BillFilter
import edu.cqwu.electricity.data.model.BillPageInfo
import edu.cqwu.electricity.data.model.BillRecord
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.BuildingResponse
import edu.cqwu.electricity.data.model.BuyListResponse
import edu.cqwu.electricity.data.model.CardLostInfo
import edu.cqwu.electricity.data.model.CardLostResponse
import edu.cqwu.electricity.data.model.CurrentDataResponse
import edu.cqwu.electricity.data.model.H5BillResponse
import edu.cqwu.electricity.data.model.OrderStatusResponse
import edu.cqwu.electricity.data.model.RechargeResponse
import edu.cqwu.electricity.data.model.UsageResponse
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.model.WechatUserResponse
import edu.cqwu.electricity.data.network.HttpClientFactory
import edu.cqwu.electricity.data.network.auth.SessionExpiredException
import edu.cqwu.electricity.data.network.auth.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.iterator

/**
 * API 请求封装类，对应 Python 版 ElectricityQuery 类
 *
 * 使用 OkHttp 直接发送 HTTP 请求，通过 Gson 解析 JSON 响应。
 * 所有方法均为 suspend 函数，需在协程中调用。
 */
class ElectricityApi {

    companion object {
        const val BASE_URL = "https://electricitypay.cqwu.edu.cn"
        const val BUILDING_API = "$BASE_URL/wechat/wx/wechatNode/getAddrByNode"
        const val BALANCE_API = "$BASE_URL/wechat/wx/wechatData/getLeftValue"
        const val SIX_MONTH_API = "$BASE_URL/wechat/wx/wechatData/getSixMonthValue"
        const val MONTH_DAILY_API = "$BASE_URL/wechat/wx/wechatData/getRoomUsedData"
        const val CURRENT_DATA_API = "$BASE_URL/wechat/wx/wechatData/getCurrentData"
        const val RECHARGE_API = "$BASE_URL/wechat/wx/getCQPayOrder"
        const val ROOM_LIST_API = "$BASE_URL/wechat/wx/findUserRoomList"
        const val GET_USER_API = "$BASE_URL/wechat/wx/getWechatUserByOpenId"
        const val BUY_LIST_API = "$BASE_URL/wechat/wx/wechatData/getRoomBuyList"
        const val PAY_CASHIER_API = "https://pay.cqwu.edu.cn/pay/cashier"

        /** 默认请求头（User-Agent 由拦截器自动注入） */
        val HEADERS: Map<String, String> = mapOf(
            "Accept" to "*/*",
            "Origin" to "https://electricitypay.cqwu.edu.cn",
            "Referer" to "https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-add"
        )

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
         * 获取账单详情的完整 URL。
         */
        fun getBillDetailUrl(relativePath: String): String {
            return "$BILL_DETAIL_PREFIX$relativePath"
        }
    }

    private val client = HttpClientFactory.createWithTimeout(10, 10, 10)

    private val gson = Gson()

    /**
     * 获取校区列表
     * 对应 Python 的 get_areas()
     */
    suspend fun getAreas(): Result<List<BuildingNode>> {
        return safeApiCall {
            val url = "${BUILDING_API}?level=build&nodeid=1&superid="
            val json = executeGet(url)
            val response = gson.fromJson(json, BuildingResponse::class.java)
            response.buildingObj ?: emptyList()
        }
    }

    /**
     * 获取指定楼层的房间列表
     * 对应 Python 的 get_rooms(floor_id)
     */
    suspend fun getRooms(floorId: String): Result<List<BuildingNode>> {
        return safeApiCall {
            val url = "${BUILDING_API}?level=room&nodeid=1&superid=$floorId"
            val json = executeGet(url)
            val response = gson.fromJson(json, BuildingResponse::class.java)
            response.buildingObj ?: emptyList()
        }
    }

    /**
     * 查询电费余额
     * 对应 Python 的 query_balance(room_id)
     */
    suspend fun queryBalance(roomId: String, userId: String = "0"): Result<BalanceResponse> {
        return safeApiCall {
            val urlString = "${BALANCE_API}?roomId=$roomId&userId=$userId&nodeId=1"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, BalanceResponse::class.java)
        }
    }

    /**
     * 查询最近6个月用电记录
     * 对应 Python 的 query_six_month_usage(room_id)
     */
    suspend fun querySixMonthUsage(roomId: String, userId: String = "0"): Result<UsageResponse> {
        return safeApiCall {
            val urlString = "${SIX_MONTH_API}?roomId=$roomId&userId=$userId&nodeId=1&costType=0"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, UsageResponse::class.java)
        }
    }

    /**
     * 查询本月每日用电记录
     * 对应 Python 的 query_month_daily_usage(room_id)
     */
    suspend fun queryMonthDailyUsage(roomId: String, userId: String = "0"): Result<UsageResponse> {
        return safeApiCall {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            val endTime = dateFormat.format(calendar.time)
            // 设置为本月1号
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val beginTime = dateFormat.format(calendar.time)

            val urlString = "${MONTH_DAILY_API}?roomId=$roomId&userId=$userId&nodeId=1&costType=0&dataType=1&beginTime=$beginTime&endTime=$endTime"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, UsageResponse::class.java)
        }
    }

    /**
     * 查询电表实时数据（用于近24h用电明细和电表实时状态）
     * 对应 Python 的 query_current_data(room_id, meter_id)
     */
    suspend fun queryCurrentData(roomId: String, meterId: String? = null, userId: String = "0"): Result<CurrentDataResponse> {
        return safeApiCall {
            // 从 roomId 推导 meterId：去掉首字母 'H'
            val effectiveMeterId = meterId ?: if (roomId.startsWith("H")) roomId.substring(1) else roomId

            val urlString = "${CURRENT_DATA_API}?meterId=$effectiveMeterId&userId=$userId&roomId=$roomId&nodeId=1&meterType=1"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, CurrentDataResponse::class.java)
        }
    }

    // ==================== 充值 API ====================

    /**
     * 创建充值订单
     * 对应 Python 的 recharge() 中 POST 到 RECHARGE_API 的部分
     *
     * POST /wechat/wx/getCQPayOrder
     * Body: { userId, nodeId, roomId, openId, roomName, payFee }
     * Response: { payUrl: "https://pay.cqwu.edu.cn/PayPreService/showselect..." }
     */
    suspend fun createRechargeOrder(
        roomId: String,
        roomName: String,
        amount: Double,
        userId: String = "0",
        openId: String = ""
    ): Result<String> {
        return safeApiCall {
            val payload = mapOf(
                "userId" to userId,
                "nodeId" to "1",
                "roomId" to roomId,
                "openId" to openId,
                "roomName" to roomName,
                "payFee" to String.format("%.2f", amount)
            )
            val jsonBody = gson.toJson(payload)
            Log.d("ElectricityApi", "充值请求 Body: $jsonBody")

            val requestBody = jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(RECHARGE_API)
                .post(requestBody)

            // 添加充值专用 headers（User-Agent 由拦截器自动注入）
            requestBuilder.addHeader("Content-Type", "application/json; charset=UTF-8")
            requestBuilder.addHeader("X-Requested-With", "XMLHttpRequest")

            Log.d("ElectricityApi", "充值请求 URL: $RECHARGE_API")
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body.string()
            Log.d("ElectricityApi", "充值响应: $body")

            val rechargeResponse = gson.fromJson(body, RechargeResponse::class.java)
            val payUrl = rechargeResponse.payUrl
            if (payUrl.isNullOrBlank()) {
                val errorMsg = rechargeResponse.message ?: "创建订单失败：payUrl 为空"
                throw RuntimeException(errorMsg)
            }
            payUrl
        }
    }

    // ==================== 充值记录查询 API ====================

    /**
     * 通过学号（openId）查询用户信息，获取 userId
     * 对应 Python 的 query_userid_by_student_id(student_id)
     *
     * GET /wechat/wx/getWechatUserByOpenId?openId={studentId}
     * Response: { id, userName, openId, createTime }
     */
    suspend fun queryUseridByStudentId(studentId: String): Result<WechatUserResponse> {
        return safeApiCall {
            val urlString = "${GET_USER_API}?openId=$studentId"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, WechatUserResponse::class.java)
        }
    }

    /**
     * 通过 userId 查询用户绑定的房间列表
     * GET /wechat/wx/findUserRoomList?userId={userId}
     * Response: [{ id, userId, nodeId, roomId, fullName, roomName }]
     */
    suspend fun queryUserRoomList(userId: String): Result<List<UserRoomInfo>> {
        return safeApiCall {
            val urlString = "${ROOM_LIST_API}?userId=$userId"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)
            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            val type = object : TypeToken<List<UserRoomInfo>>() {}.type
            gson.fromJson(json, type)
        }
    }

    /**
     * 查询房间充值记录
     * 对应 Python 的 query_buy_list(room_id, user_id, begin_time, end_time)
     *
     * GET /wechat/wx/wechatData/getRoomBuyList?roomId={roomId}&userId={userId}&nodeId=1&beginTime={beginTime}&endTime={endTime}
     * Response: { ifSuccess, resultMsg, buyObj: [{ userName, buyTime, buyTotal, orderNum }] }
     */
    suspend fun queryBuyList(
        roomId: String,
        userId: String,
        beginTime: String,
        endTime: String
    ): Result<BuyListResponse> {
        return safeApiCall {
            val urlString = "${BUY_LIST_API}?roomId=$roomId&userId=$userId&nodeId=1&beginTime=$beginTime&endTime=$endTime"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, BuyListResponse::class.java)
        }
    }

    // ==================== 支付相关 API ====================

    /**
     * 查询订单状态（轮询用）
     * GET /pay/cashier/getOrderById/{orderId}
     * 对应 showselect 页面 JavaScript 中的 queryOrderStatus() 函数
     */
    suspend fun getOrderStatus(orderId: String): Result<OrderStatusResponse> {
        return safeApiCall {
            val url = "${PAY_CASHIER_API}/getOrderById/$orderId"
            Log.d("ElectricityApi", "查询订单状态: $url")

            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .addHeader("Referer", "https://pay.cqwu.edu.cn/")
                .addHeader("X-Requested-With", "XMLHttpRequest")

            // 添加其他默认请求头（User-Agent 由拦截器自动注入）
            HEADERS.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("查询订单状态 HTTP ${response.code}: ${response.message}")
            }
            val body = response.body.string()
            Log.d("ElectricityApi", "订单状态响应: $body")

            gson.fromJson(body, OrderStatusResponse::class.java)
        }
    }

    // ==================== EPay 账户信息查询 ====================

    /**
     * 获取 EPay 账户信息（通过 HTML 解析）
     *
     * 使用 HttpClientFactory.shared（与 QrCodeApi 共享同一 CookieJar），
     * 自动完成 CAS ticket 交换获取 JSESSIONID。
     *
     * GET http://218.194.176.214:8382/epay/thirdapp/balance
     * 返回 HTML 页面，用正则解析关键字段。
     */
    suspend fun fetchAccountInfo(): Result<AccountInfo> = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val url = "$EPAY_THIRDAPP/balance"
            Log.d("ElectricityApi", "获取账户信息: GET $url")

            val response = HttpClientFactory.shared.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()

            // 检查是否被重定向到 CAS 登录页
            SessionManager.checkSessionOrThrow(html)

            // 解析 HTML 提取字段
            val accountInfo = parseAccountInfoHtml(html)
            val elapsed = System.currentTimeMillis() - t0
            Log.d("ElectricityApi", "获取账户信息成功: 耗时=${elapsed}ms, $accountInfo")
            Result.success(accountInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("ElectricityApi", "获取账户信息失败", e)
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
                """<p>\s*$escapedLabel\s*</p>\s*</div>\s*<div class="weui-cell__ft">([^<]*)""",
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
            Log.d("ElectricityApi", "获取卡挂失信息: GET $url")

            val response = HttpClientFactory.shared.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()

            val html = response.body.string()

            SessionManager.checkSessionOrThrow(html)

            val cardInfo = parseCardLostInfoHtml(html)
            Log.d("ElectricityApi", "卡挂失信息: $cardInfo")
            Result.success(cardInfo)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("ElectricityApi", "获取卡挂失信息失败", e)
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
            Log.d("ElectricityApi", "执行卡挂失: POST $url")

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

            Log.d("ElectricityApi", "卡挂失响应: $body")

            val cardLostResponse = gson.fromJson(body, CardLostResponse::class.java)
            Result.success(cardLostResponse)
        } catch (e: Exception) {
            Log.e("ElectricityApi", "执行卡挂失失败", e)
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
                """<p>\s*$escapedLabel\s*</p>\s*</div>\s*<div class="weui-cell__ft">([^<]*)""",
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
                SessionManager.checkSessionOrThrow(html)
                val allZones = parseAllZones(html)
                Log.d("ElectricityApi", "四区解析完成: zone数量=${allZones.size}")
                Result.success(allZones)
            } catch (e: SessionExpiredException) {
                Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ElectricityApi", "获取账单失败", e)
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

        Log.d("ElectricityApi", "获取账单: POST $BILL_QUERY_URL, filter=$filter")

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

            Log.d("ElectricityApi", "H5获取账单: POST $H5_BILL_API, pageno=$pageNo")

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
            SessionManager.checkSessionOrThrow(json)

            val h5Response = gson.fromJson(json, H5BillResponse::class.java)
            // 修复 1：校验 retcode，服务器返回错误码时抛异常
            if (h5Response.retcode != "0") {
                throw RuntimeException(
                    h5Response.retmsg ?: "H5 账单接口错误 (retcode=${h5Response.retcode})"
                )
            }
            Log.d(
                "ElectricityApi", "H5账单解析完成: ${h5Response.dtls?.size ?: 0}条, " +
                        "页码=${h5Response.pageno}/${h5Response.totalpage}, retcode=${h5Response.retcode}"
            )
            Result.success(h5Response)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ElectricityApi", "H5获取账单失败", e)
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

            Log.d("ElectricityApi", "解析 zone[$zoneId]: ${records.size}条, 第${currentPage}/${totalPages}页")

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
            Log.w("ElectricityApi", "解析账单行失败", e)
            return null
        }
    }

    // ==================== 内部方法 ====================

    private fun executeGet(url: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder().url(url).get()
        // 添加其他默认请求头（User-Agent 由拦截器自动注入）
        HEADERS.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        extraHeaders.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        Log.d("ElectricityApi", "请求 URL: $url")
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.message}")
        }
        val body = response.body.string()
        Log.d("ElectricityApi", "响应体原始内容: $body")
        return body
    }

    /**
     * 统一异常捕获，将异常转换为 Result.failure()
     * 注意：必须重新抛出 CancellationException，避免吞掉协程取消信号
     */
    private suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(call())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}