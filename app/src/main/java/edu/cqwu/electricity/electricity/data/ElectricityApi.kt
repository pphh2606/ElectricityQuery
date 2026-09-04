package edu.cqwu.electricity.electricity.data

import edu.cqwu.electricity.logging.AppLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

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
        const val CURRENT_DATA_API = "$BASE_URL/wechat/wx/wechatData/getCurrentData"
        const val RECHARGE_API = "$BASE_URL/wechat/wx/getCQPayOrder"
        const val ROOM_LIST_API = "$BASE_URL/wechat/wx/findUserRoomList"
        const val GET_USER_API = "$BASE_URL/wechat/wx/getWechatUserByOpenId"
        const val BUY_LIST_API = "$BASE_URL/wechat/wx/wechatData/getRoomBuyList"
        const val USAGE_DATA_API = "$BASE_URL/wechat/wx/wechatData/getRoomUsedData"
        const val SUBSIDY_DATA_API = "$BASE_URL/wechat/wx/wechatData/getRoomSubsidyData"
        val HEADERS: Map<String, String> = mapOf(
            "Accept" to "*/*",
            "Origin" to "https://electricitypay.cqwu.edu.cn",
            "Referer" to "https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-add"
        )
    }

    private val client = HttpClientFactory.create(
        connectTimeout = 10,
        readTimeout = 10,
        writeTimeout = 10,
    )

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
     * 查询电表实时数据（用于电表实时状态）
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
                "payFee" to String.format(Locale.US, "%.2f", amount)
            )
            val jsonBody = gson.toJson(payload)
            AppLog.body("ElectricityApi", "充值请求 Body: $jsonBody")

            val requestBody = jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(RECHARGE_API)
                .post(requestBody)

            // 添加充值专用 headers（User-Agent 由拦截器自动注入）
            requestBuilder.addHeader("Content-Type", "application/json; charset=UTF-8")
            requestBuilder.addHeader("X-Requested-With", "XMLHttpRequest")

            AppLog.d("ElectricityApi", "充值请求 URL: $RECHARGE_API")
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body.string()
            AppLog.body("ElectricityApi", "充值响应: $body")

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

    /**
     * 查询房间用电记录（用量报表页）
     * 对应 Python 的 query_month_daily_usage / getRoomUsedData
     *
     * GET /wechat/wx/wechatData/getRoomUsedData
     *   ?roomId={roomId}&userId={userId}&nodeId=1&costType=0&dataType={dataType}&beginTime={beginTime}&endTime={endTime}
     *
     * @param roomId 房间号
     * @param userId 用户 id（默认 "0"）
     * @param dataType 数据粒度：0=小时数据, 1=每日数据, 2=每月数据
     * @param beginTime 起始日期 yyyy-MM-dd
     * @param endTime 结束日期 yyyy-MM-dd
     */
    suspend fun queryRoomUsageDataV2(
        roomId: String,
        userId: String = "0",
        dataType: Int,
        beginTime: String,
        endTime: String
    ): Result<UsageRecordV2Response> {
        return safeApiCall {
            val urlString = "$USAGE_DATA_API?roomId=$roomId&userId=$userId&nodeId=1&costType=0&dataType=$dataType&beginTime=$beginTime&endTime=$endTime"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, UsageRecordV2Response::class.java)
        }
    }

    /**
     * 查询房间补助记录
     * 对应 Python 的 getRoomSubsidyData
     *
     * GET /wechat/wx/wechatData/getRoomSubsidyData
     *   ?roomId={roomId}&userId={userId}&nodeId=1&beginTime={beginTime}&endTime={endTime}
     *
     * @param roomId 房间号
     * @param userId 用户 id（默认 "0"）
     * @param beginTime 起始日期 yyyy-MM-dd
     * @param endTime 结束日期 yyyy-MM-dd
     */
    suspend fun queryRoomSubsidyData(
        roomId: String,
        userId: String = "0",
        beginTime: String,
        endTime: String
    ): Result<SubsidyRecordResponse> {
        return safeApiCall {
            val urlString = "$SUBSIDY_DATA_API?roomId=$roomId&userId=$userId&nodeId=1&beginTime=$beginTime&endTime=$endTime"
            val authHeader = RSAEncrypt.buildAuthorization(urlString)

            val json = executeGet(
                url = urlString,
                extraHeaders = mapOf("Authorization" to authHeader)
            )
            gson.fromJson(json, SubsidyRecordResponse::class.java)
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
    // ==================== 内部方法 ====================

    private fun executeGet(url: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder().url(url).get()
        // 添加其他默认请求头（User-Agent 由拦截器自动注入）
        HEADERS.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        extraHeaders.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        AppLog.d("ElectricityApi", "请求 URL: $url")
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.message}")
        }
        val body = response.body.string()
        AppLog.body("ElectricityApi", "响应体原始内容: $body")
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
