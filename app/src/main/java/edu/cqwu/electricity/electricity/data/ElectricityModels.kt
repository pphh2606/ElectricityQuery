package edu.cqwu.electricity.electricity.data

import com.google.gson.annotations.SerializedName

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
    ROOM_GRID,  // 展开式房间分组视图（楼栋→楼层分组+房间网格）
    DONE        // 已完成选择
}

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

// ==================== 充值记录时间范围枚举 ====================

/**
 * 充值记录查询的时间范围枚举。
 * 用于统一管理充值记录查询的时间范围选项。
 */
enum class RechargeTimeRange(val days: Long) {
    ONE_MONTH(30L),
    THREE_MONTHS(90L),
    ONE_YEAR(365L),
    FOUR_YEARS(1460L);

    companion object {
        /** 通过索引值（0=一个月, 1=三个月, 2=一年, 3=四年）获取枚举 */
        fun fromIndex(index: Int): RechargeTimeRange =
            entries.getOrElse(index) { ONE_MONTH }
    }
}

