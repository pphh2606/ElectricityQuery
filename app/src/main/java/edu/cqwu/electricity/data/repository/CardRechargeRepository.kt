package edu.cqwu.electricity.data.repository

import edu.cqwu.electricity.data.network.cardrecharge.CardRechargeApi
import edu.cqwu.electricity.data.network.cardrecharge.CardBasicInfo
import edu.cqwu.electricity.data.network.cardrecharge.CardOrderStatus
import edu.cqwu.electricity.data.network.cardrecharge.CardPaymentChannel
import edu.cqwu.electricity.data.network.cardrecharge.CardPaymentResult
import edu.cqwu.electricity.data.network.cardrecharge.CardRechargeOrderResult

/**
 * 校园卡充值数据仓库层
 * 封装 [CardRechargeApi]，统一管理数据获取和错误处理
 */
class CardRechargeRepository {

    private val api = CardRechargeApi()

    suspend fun queryBasicInfo(
        studentId: String,
        projectId: String = "80bb5ee2189e4ca2bd5dff4513a0dae2"
    ): Result<CardBasicInfo> = api.queryBasicInfo(studentId, projectId)

    suspend fun queryTradeChannels(
        projectId: String = "80bb5ee2189e4ca2bd5dff4513a0dae2"
    ): Result<List<CardPaymentChannel>> = api.queryTradeChannels(projectId)

    suspend fun createOrder(
        projectId: String = "80bb5ee2189e4ca2bd5dff4513a0dae2",
        amountStr: String,
    ): Result<CardRechargeOrderResult> = api.createOrder(projectId, amountStr)

    suspend fun toPayOrderTrade(
        orderNo: String,
        payType: String = "01",
        ip: String = "218.2.101.93",
    ): Result<CardPaymentResult> = api.toPayOrderTrade(orderNo, payType, ip)

    suspend fun queryOrderStatus(orderId: String): Result<CardOrderStatus> = api.queryOrderStatus(orderId)
}
