package edu.cqwu.electricity.cardcenter.data

import androidx.annotation.StringRes
import edu.cqwu.electricity.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    @StringRes val statusRes: Int? = null,
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

        val (statusRes, cssClass) = when (status) {
            1 -> (if (tradedirect == 2) R.string.card_bill_status_waiting_other_pay else R.string.card_bill_status_waiting_pay) to "label-warning"
            2 -> R.string.card_bill_status_success to "label-success"
            3 -> R.string.card_bill_status_canceled to "label-default"
            4 -> R.string.card_bill_status_terminated to "label-danger"
            5 -> R.string.card_bill_status_failed to "label-danger"
            else -> R.string.card_bill_status_unknown to ""
        }

        return BillRecord(
            createDate = fmtDate,
            createTime = fmtTime,
            type = tradename,
            billNo = refno,
            merchant = shopname.ifBlank { buyername },
            amount = String.format(Locale.US, "%.2f", amount),
            paymentMethod = "",
            status = "",
            statusCssClass = cssClass,
            statusRes = statusRes,
            detailUrl = "/epay/consume/tradedetail?billno=$refno"
        )
    }
}
