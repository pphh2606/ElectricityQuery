package edu.cqwu.electricity.jwxt.core.model

/**
 * 「课表」相关的**共用**接口模型：`biz/v410/schedule/`（我的课表）与
 * `biz/v510/schedule/`、学校定制的 `biz/v510/cqwu/schedule/`（全校课表查询）都用这一套。
 *
 * 放在共享层而不是某个功能里，是因为「我的课表」与「全校课表查询」两个功能都要用它们；
 * 字段取自 `fortest/课表网页抓包.har.json` 与 `fortest/更多课表.har.json` 的真实响应，
 * 页面用不到的字段不声明。
 */

/** `GET biz/v410/schedule/termList` 响应 */
data class ScheduleTermResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtScheduleTerm>? = null,
)

/** 一个学期；`currentFlag` 为 true 即当前学期 */
data class JwxtScheduleTerm(
    val termCode: String = "",
    val termName: String = "",
    val currentFlag: Boolean = false,
)

/** `GET biz/v410/schedule/getTermWeeks` 响应 */
data class TermWeeksResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtTermWeek>? = null,
)

/** 一周；`curWeek` 为 true 即当前周 */
data class JwxtTermWeek(
    val serialNumber: Int = 0,
    val curWeek: Boolean = false,
)

/** `GET biz/v510/cqwu/schedule/getQxSectionList` 响应 */
data class SectionListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtSection>? = null,
)

/**
 * 课表的一行（一节课）。
 *
 * 实测 `startTime` / `endTime` 都是 null（学校没配节次时间），所以左侧只显示 [name]（「第1节」）；
 * 接口还下发分组名（`timePeriod`，如「上午[01]」），页面不显示分组，故不声明。
 */
data class JwxtSection(
    val name: String = "",
)

/**
 * `POST biz/v410/schedule/getMyScheduleDetail` 请求体。
 *
 * [serialNumber] 用 [Any] 是照抄网页的真实形态：整学期时是**空字符串**、查某一周时是**数字**
 * （抓包：`{"termCode":"2026-2027-1","campusCode":"","serialNumber":3}`）。Gson 按运行时类型
 * 序列化，两种形态都能原样发给服务端。
 */
data class ScheduleDetailRequest(
    val termCode: String,
    val campusCode: String,
    val serialNumber: Any,
)

/**
 * `POST biz/v510/schedule/getScheduleDetail` 请求体（「全校课表查询」用）。
 *
 * 字段名是**大写** `KBLX` / `CODE`（抓包实证）：写成小写服务端会返回
 * `{"code":500,"msg":"[[KBLX]为空!]"}`。
 */
data class ScheduleTargetDetailRequest(
    val termCode: String,
    val campusCode: String,
    val KBLX: String,
    val CODE: String,
    val serialNumber: Any,
)

/** `POST biz/v410/schedule/getMyScheduleDetail` 响应 */
data class ScheduleDetailResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: ScheduleDetailData? = null,
)

/** 课表主体；画格子用的是 [arrangedList]（已排课） */
data class ScheduleDetailData(
    val arrangedList: List<JwxtTimetableCourse>? = null,
    /**
     * 未排课的课程——它的 `dayOfWeek`/`beginSection`/`placeName`/`color` 实测全是 null，网格里放不下。
     * 本轮只解析、不显示：留在模型里，将来做「未排课列表」时不必再动接口层。
     */
    val notArrangeList: List<JwxtTimetableCourse>? = null,
)

/**
 * 一门已排课的课程。
 *
 * [dayOfWeek] 1=周一 … 7=周日（对应课表的列），[beginSection]/[endSection] 是第几节到第几节（对应行）；
 * [color] 是卡片背景色（形如 `#FFF0CC`，教务下发的都是浅色）；
 * [cellDetail] 是服务端排好版的正文逐行（卡片上用），[titleDetail] 是更完整的详情文本（弹窗用）。
 * 接口还下发上课时间（`beginTime`/`endTime`）与周次、教师、班级等字段，页面都从上面两份正文里取，
 * 故不声明——将来要单独用它们时再加。
 */
data class JwxtTimetableCourse(
    val courseName: String = "",
    val dayOfWeek: Int = 0,
    val beginSection: Int = 0,
    val endSection: Int = 0,
    val placeName: String = "",
    val color: String = "",
    /** 复用「今日课程」的正文行模型：接口两组接口的这一层结构完全一致 */
    val cellDetail: List<JwxtCellLine>? = null,
    val titleDetail: List<String>? = null,
)
