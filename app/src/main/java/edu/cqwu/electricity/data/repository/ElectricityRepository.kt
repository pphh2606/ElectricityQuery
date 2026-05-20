package edu.cqwu.electricity.data.repository

import edu.cqwu.electricity.data.model.AccountInfo
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.BuyListResponse
import edu.cqwu.electricity.data.model.CardLostInfo
import edu.cqwu.electricity.data.model.CardLostResponse
import edu.cqwu.electricity.data.model.CurrentDataResponse
import edu.cqwu.electricity.data.model.OrderStatusResponse
import edu.cqwu.electricity.data.model.UsageResponse
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.model.WechatUserResponse
import edu.cqwu.electricity.data.network.ElectricityApi

/**
 * 数据仓库层
 * 封装 ElectricityApi，统一管理数据获取和错误处理
 */
class ElectricityRepository {

    private val api = ElectricityApi()

    /**
     * 获取校区列表
     */
    suspend fun getAreas(): Result<List<BuildingNode>> {
        return api.getAreas()
    }

    /**
     * 获取指定楼层的房间列表
     */
    suspend fun getRooms(floorId: String): Result<List<BuildingNode>> {
        return api.getRooms(floorId)
    }

    /**
     * 查询电费余额
     */
    suspend fun queryBalance(roomId: String): Result<BalanceResponse> {
        return api.queryBalance(roomId)
    }

    /**
     * 查询最近6个月用电记录
     */
    suspend fun querySixMonthUsage(roomId: String): Result<UsageResponse> {
        return api.querySixMonthUsage(roomId)
    }

    /**
     * 查询本月每日用电记录
     */
    suspend fun queryMonthDailyUsage(roomId: String): Result<UsageResponse> {
        return api.queryMonthDailyUsage(roomId)
    }

    /**
     * 查询电表实时数据（电压/电流/功率等）
     */
    suspend fun queryCurrentData(roomId: String): Result<CurrentDataResponse> {
        return api.queryCurrentData(roomId)
    }

    /**
     * 创建充值订单
     * @param roomId 房间 ID
     * @param roomName 房间名称（账号充值模式传 fullName）
     * @param amount 充值金额（元）
     * @param userId 用户 ID（账号充值模式使用实际 userId，默认 "0"）
     * @param openId 学号/openId（账号充值模式传入 studentId，默认 ""）
     * @return Result 包含 payUrl
     */
    suspend fun createRechargeOrder(roomId: String, roomName: String, amount: Double, userId: String = "0", openId: String = ""): Result<String> {
        return api.createRechargeOrder(roomId, roomName, amount, userId, openId)
    }

    // ========== 账号充值（学号模式）==========

    /**
     * 通过 userId 查询用户绑定的房间列表
     */
    suspend fun queryUserRoomList(userId: String): Result<List<UserRoomInfo>> {
        return api.queryUserRoomList(userId)
    }

    // ========== 充值记录查询 ==========

    /**
     * 通过学号查询用户信息
     */
    suspend fun queryUseridByStudentId(studentId: String): Result<WechatUserResponse> {
        return api.queryUseridByStudentId(studentId)
    }

    /**
     * 查询房间充值记录
     */
    suspend fun queryBuyList(
        roomId: String,
        userId: String,
        beginTime: String,
        endTime: String
    ): Result<BuyListResponse> {
        return api.queryBuyList(roomId, userId, beginTime, endTime)
    }

    /**
     * 查询 EPay 账户信息
     */
    suspend fun queryAccountInfo(): Result<AccountInfo> {
        return api.fetchAccountInfo()
    }

    /**
     * 查询订单状态（轮询用）
     * 由 PaymentWebViewOverlay 轮询调用
     */
    suspend fun queryOrderStatus(orderId: String): Result<OrderStatusResponse> {
        return api.getOrderStatus(orderId)
    }

    // ========== 卡挂失 ==========

    /**
     * 获取卡挂失页面的卡信息
     */
    suspend fun queryCardLostInfo(): Result<CardLostInfo> {
        return api.fetchCardLostInfo()
    }

    /**
     * 执行卡挂失
     */
    suspend fun performCardLost(): Result<CardLostResponse> {
        return api.doCardLost()
    }
}
