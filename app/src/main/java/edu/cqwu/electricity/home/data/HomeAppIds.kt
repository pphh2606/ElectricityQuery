package edu.cqwu.electricity.home.data

/**
 * 首页应用中需要原生处理的特殊 appId 常量。
 *
 * 这些 ID 来自服务端 JSON 数据，若服务端调整 ID 则需同步更新此文件。
 * 集中管理避免 [HomeScreen] 中硬编码魔法字符串。
 */
object HomeAppIds {
    /** 支付码 */
    const val PAY_QR = "5339732631940410"

    /** 乘车码 */
    const val BUS_QR = "6980342349549853"

    /** 学生绑定银行卡 → 打开原生银行卡绑定页 */
    const val BANK_CARD_BIND = "5416679606401266"

    /** 学生宿舍电费充值 → 打开原生电费查询 */
    const val DORM_ELECTRICITY = "7624123418505155"

    /** 卡中心 → 原生卡中心页面 */
    const val CARD_CENTER = "5339755469114438"

    /** 通知公告 → 原生通知公告列表页 */
    const val NOTICE = "4804236383747498"

    /** 缴费服务大厅 → 打开原生页面 */
    const val FEE_SERVICE_HALL = "5350760556213516"

    /** 我的信息 → 打开原生页面 */
    const val MY_INFO = "6204672076294065"

    /** 我有话说 → 打开原生咨询区列表页 */
    const val SPEAK_UP = "6251198080206918"
}
