package edu.cqwu.electricity.notice.data

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

