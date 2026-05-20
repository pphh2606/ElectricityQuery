package edu.cqwu.electricity.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 建筑层级节点（校区/楼栋/楼层通用）
 */
data class BuildingNode(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("num")
    val num: String? = null,
    @SerializedName("children")
    val children: List<BuildingNode>? = null
)

/**
 * BuildingNode 的显示名称扩展属性。
 * 优先使用 num（如 "A101"），如果 num 为空或 "0" 则降级为 name。
 * 此模式在 DashboardScreen、BuildingSelectionScreen、ViewModel 中广泛使用，
 * 提取为扩展属性统一维护。
 */
val BuildingNode.displayName: String
    get() = if (num.isNullOrBlank() || num == "0") name else num

/**
 * 建筑列表 API 响应包装
 */
data class BuildingResponse(
    @SerializedName("buildingObj")
    val buildingObj: List<BuildingNode>? = null
)

/**
 * 电费余额查询响应
 */
data class BalanceResponse(
    @SerializedName("ifSuccess")
    val ifSuccess: String = "",
    @SerializedName("resultMsg")
    val resultMsg: String? = null,
    @SerializedName("roomName")
    val roomName: String = "未知",
    @SerializedName("roomId")
    val roomId: String = "",
    @SerializedName("remainEletricCapacity")
    val remainEletricCapacity: Double = 0.0,
    @SerializedName("userBalance")
    val userBalance: Double = 0.0,
    @SerializedName("subsidyBalance")
    val subsidyBalance: Double = 0.0,
    @SerializedName("baseBalance")
    val baseBalance: Double = 0.0,
    @SerializedName("payEnable")
    val payEnable: Int = 0
)

/**
 * 选择步骤枚举
 */
enum class SelectionStep {
    AREA,       // 选择校区
    BUILDING,   // 选择楼栋
    ROOM_GRID,  // 展开式房间分组视图（楼栋→楼层分组+房间网格）
    FLOOR,      // 选择楼层（保留兼容）
    ROOM,       // 选择房间（保留兼容）
    DONE        // 已完成选择
}

/**
 * 用电记录（6个月/本月每日共用）
 */
data class UsageRecord(
    @SerializedName("costTime") val costTime: String = "",
    @SerializedName("consumeTotal") val consumeTotal: Double = 0.0,
    @SerializedName("costTotal") val costTotal: Double = 0.0
)

/**
 * 用电记录 API 响应
 */
data class UsageResponse(
    @SerializedName("ifSuccess") val ifSuccess: String = "",
    @SerializedName("resultMsg") val resultMsg: String? = null,
    @SerializedName("costObj") val costObj: List<UsageRecord>? = null
)

/**
 * 小时级用电记录
 */
data class HourDataRecord(
    @SerializedName("dataTime") val dataTime: String = "",
    @SerializedName("dataTotal") val dataTotal: Double = 0.0
)

/**
 * 电表数据项（电流/电压共用）
 */
data class MeterDataItem(
    @SerializedName("name") val name: String = "",
    @SerializedName("display") val display: Double = 0.0
)

/**
 * 电表实时数据响应
 */
data class CurrentDataResponse(
    @SerializedName("ifSuccess") val ifSuccess: String = "",
    @SerializedName("resultMsg") val resultMsg: String? = null,
    @SerializedName("hourDataObj") val hourDataObj: List<HourDataRecord>? = null,
    @SerializedName("exp4") val exp4: List<MeterDataItem>? = null,  // 电流
    @SerializedName("exp3") val exp3: List<MeterDataItem>? = null,  // 电压
    @SerializedName("exp2") val exp2: String? = null,               // 功率/累计值
    @SerializedName("exp5") val exp5: String? = null                // 电源状态
)

/**
 * 充值订单响应
 */
data class RechargeResponse(
    @SerializedName("payUrl")
    val payUrl: String? = null,
    @SerializedName("message")
    val message: String? = null
)

/**
 * 详情类型枚举
 */
enum class DetailType {
    SIX_MONTH_USAGE,    // 最近6个月用电记录
    MONTH_DAILY_USAGE,  // 本月每日用电
    HOURLY_USAGE,       // 近24h用电明细
    METER_STATUS        // 电表实时状态
}

/**
 * 微信用户信息响应（通过学号查询）
 * 对应 Python 中 query_userid_by_student_id 的响应
 */
data class WechatUserResponse(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("userName")
    val userName: String = "",
    @SerializedName("openId")
    val openId: String = "",
    @SerializedName("createTime")
    val createTime: String = ""
)

/**
 * 单条充值记录
 */
data class BuyRecord(
    @SerializedName("userName")
    val userName: String = "",
    @SerializedName("buyTime")
    val buyTime: String = "",
    @SerializedName("buyTotal")
    val buyTotal: Double = 0.0,
    @SerializedName("orderNum")
    val orderNum: String = ""
)

/**
 * 充值记录列表 API 响应
 * 对应 Python 中 query_buy_list 的响应
 * Response: { ifSuccess, resultMsg, buyObj: [{ userName, buyTime, buyTotal, orderNum }] }
 */
data class BuyListResponse(
    @SerializedName("ifSuccess")
    val ifSuccess: String = "",
    @SerializedName("resultMsg")
    val resultMsg: String? = null,
    @SerializedName("buyObj")
    val buyObj: List<BuyRecord>? = null
)

/**
 * 用户房间信息（findUserRoomList 响应项）
 * GET /wechat/wx/findUserRoomList?userId={userId}
 * Response: [{ id, userId, nodeId, roomId, fullName, roomName, ... }]
 */
data class UserRoomInfo(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("userId") val userId: Int = 0,
    @SerializedName("nodeId") val nodeId: Int = 1,
    @SerializedName("roomId") val roomId: String = "",
    @SerializedName("fullName") val fullName: String = "",
    @SerializedName("roomName") val roomName: String = ""
)

// ==================== 支付相关模型 ====================

/**
 * 支付方式枚举
 */
enum class PaymentMethod(val payType: String, val displayName: String) {
    WECHAT("02", "微信支付"),
    ALIPAY("01", "支付宝")
}

/**
 * 订单状态查询响应（getOrderById）
 * GET /pay/cashier/getOrderById/{orderId}
 */
data class OrderStatusResponse(
    val messageCode: String?,
    val message: String?,
    val data: OrderStatusData?
)

data class OrderStatusData(
    val status: String?,
    val returnUrl: String?,
    val notifyUrl: String?,
    val orderNo: String?,
    val paymentOrderNo: String?
)

// ==================== 通知公告相关模型 ====================

/**
 * 单条通知公告
 */
data class NoticeItem(
    val wid: String = "",
    val noticeTitle: String = "",
    val sendDepartment: String = "",
    val sendTime: String = "",
    val sendTimeDesc: String = "",
    val clickNumber: String = ""
)

/**
 * 通知公告 API 响应中的 qp 字段
 */
data class NoticeQp(
    val aList: List<NoticeItem>? = null,
    val pageNo: Int = 0,
    val pageSize: Int = 10,
    val totalItem: Int = 0
)

/**
 * 通知公告 API 根响应
 * GET /publicapp/sys/tzggxt/api/getUseNoticePage.do
 */
data class NoticeResponse(
    val qp: NoticeQp? = null
)

/**
 * 通知公告详情 API 响应中的单个通知数据
 * 注意：实际接口返回结构为 { attchList: [], list: [ { ... } ] }
 * 详情数据在 list 数组的第一个元素中
 * sendTimeDesc 可能为 null
 */
data class NoticeDetailQp(
    val noticeTitle: String = "",
    val noticeContent: String = "",
    val sendDepartment: String = "",
    val sendPeople: String? = null,
    val sendTime: String = "",
    val sendTimeDesc: String? = null,
    val clickNumber: String = "",
    val noticeDesc: String = ""
)

/**
 * 通知公告详情 API 根响应
 * GET /publicapp/sys/tzggxt/api/getOneNoticeInfo.do?noticeId=xxx
 * 实际结构: { attchList: [], list: [NoticeDetailQp] }
 */
data class NoticeDetailResponse(
    val attchList: List<Any>? = null,
    val list: List<NoticeDetailQp>? = null
)

// ==================== 卡挂失相关模型 ====================

/**
 * 卡挂失 API 响应
 * POST /epay/thirdapp/docardlost.json
 * Response: { retcode: "0", retmsg: "挂失成功" }
 */
data class CardLostResponse(
    val retcode: String = "",
    val retmsg: String = ""
)

/**
 * 卡挂失页面的卡信息（从 HTML 解析）
 */
data class CardLostInfo(
    val cardNumber: String = "",   // 卡号
    val cardStatus: String = ""    // 卡状态（正常/挂失等）
)

// ==================== 账单相关模型 ====================

/**
 * 单条交易记录。
 * 从 /epay/consume/query HTML 的 <tr> 行解析得到。
 */
data class BillRecord(
    val createDate: String = "",        // 创建日期，如 "2026.05.07"
    val createTime: String = "",        // 创建时间，如 "184939"
    val type: String = "",              // 交易类型，如 "POS消费"、"微信充值"
    val billNo: String = "",            // 交易号，如 "20260507184941106641"
    val merchant: String = "",          // 对方（商户名称），如 "粥肠粉，铁板饭"
    val amount: String = "",            // 金额，如 "3.40"
    val paymentMethod: String = "",     // 付款方式，如 "现金"
    val status: String = "",            // 状态文字，如 "交易成功"
    val statusCssClass: String = "",    // 状态 CSS class，如 "label-success"
    val detailUrl: String = ""          // 详情页相对路径
)

/**
 * 账单分页信息 + 当前页数据
 */
data class BillPageInfo(
    val records: List<BillRecord>,
    val currentPage: Int,
    val totalPages: Int
) {
    val hasNext: Boolean get() = currentPage < totalPages
    val hasPrev: Boolean get() = currentPage > 1
}

/**
 * 账单筛选条件，对应 HTML 表单字段。
 *
 * @param tabNo 标签页：1=全部, 2=未付款, 4=成功, 5=失败
 * @param tradeName 商户名称关键字搜索
 * @param startTime 开始日期 YYYY-MM-DD
 * @param endTime 结束日期 YYYY-MM-DD
 * @param timeType 日期类型：1=创建时间, 2=付款时间, 3=收款时间
 * @param tradeDirect 资金流向：1=支出, 2=收入（可多选）
 * @param pageNo 当前页码（从1开始）
 */
data class BillFilter(
    val tabNo: Int = 1,
    val tradeName: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val timeType: Int = 1,
    val tradeDirect: Set<Int> = emptySet(),
    val pageNo: Int = 1
)

// ==================== H5 版账单 JSON 响应模型 ====================

/**
 * H5 版账单 API 的 JSON 响应。
 * POST /epay/thirdapp/loadbill.json
 *
 * Response 示例:
 * { "pageno": 2, "totalpage": 10, "retcode": "0", "retmsg": null,
 *   "dtls": [ { "id": "xxx", "refno": "20260507184941106641", "tradename": "POS消费",
 *               "shopname": "示例商户", "buyername": "示例用户",
 *               "amount": 3.40, "createtime": 1746636599000,
 *               "paytime": 1746636599000, "tradedirect": 1, "status": 2,
 *               "userid": "xxx" } ] }
 */
data class H5BillResponse(
    val pageno: Int = 1,
    val totalpage: Int = 1,
    val dtls: List<H5BillItem>? = null,
    val retcode: String = "",
    val retmsg: String? = null
)

/**
 * H5 版单条交易记录。
 */
data class H5BillItem(
    val id: String = "",
    val refno: String = "",
    val tradename: String = "",        // 交易类型："POS消费"、"微信充值"
    val shopname: String = "",         // 商户名称："特色套餐"
    val buyername: String = "",
    val amount: Double = 0.0,
    val createtime: Long = 0L,         // 毫秒时间戳
    val paytime: Long = 0L,
    val tradedirect: Int = 1,          // 1=支出, 2=收入
    val status: Int = 2,               // 1=等待付款, 2=成功, 3=取消, 4=终止, 5=失败
    val userid: String = ""
) {
    fun toBillRecord(): BillRecord {
        // 修复 2：服务器时间戳为东八区，强制指定时区防止设备时区偏差
        val shanghaiTz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        val date = Date(createtime)
        val dateFmt = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).apply { timeZone = shanghaiTz }
        val timeFmt = SimpleDateFormat("HHmmss", Locale.getDefault()).apply { timeZone = shanghaiTz }
        val fmtDate = dateFmt.format(date)
        val fmtTime = timeFmt.format(date)

        val (statusText, cssClass) = when (status) {
            1 -> (if (tradedirect == 2) "等待对方付款" else "等待付款") to "label-warning"
            2 -> "交易成功" to "label-success"
            3 -> "交易取消" to "label-default"
            4 -> "交易终止" to "label-danger"
            5 -> "交易失败" to "label-danger"
            else -> "未知" to ""
        }

        return BillRecord(
            createDate = fmtDate,
            createTime = fmtTime,
            type = tradename,
            billNo = refno,
            merchant = shopname.ifBlank { buyername },
            amount = String.format("%.2f", amount),
            paymentMethod = "",
            status = statusText,
            statusCssClass = cssClass,
            detailUrl = "/epay/consume/tradedetail?billno=$refno"
        )
    }
}
