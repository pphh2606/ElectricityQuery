package edu.cqwu.electricity.data.model

/**
 * 账户信息数据类
 *
 * 从 EPay 账户信息页面（/epay/thirdapp/balance）的 HTML 中解析得到。
 *
 * @property name 姓名
 * @property studentId 学工号
 * @property balance 账户余额（含单位，如 "53.49￥"）
 * @property school 学校
 * @property major 专业
 * @property className 班级
 */
data class AccountInfo(
    val name: String = "",
    val studentId: String = "",
    val balance: String = "",
    val school: String = "",
    val major: String = "",
    val className: String = ""
)
