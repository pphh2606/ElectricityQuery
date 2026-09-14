package edu.cqwu.electricity.jwxt.data

/**
 * 教务首页接口的数据模型。
 *
 * 字段名全部来自 `fortest/教务系统.har.json` 的真实响应，**没有猜测字段**。
 * 每个接口配一个响应壳（不用 `Any` 泛型），与项目内 `CardRechargeApi` 等保持一致。
 */

/** `GET biz/home/listLabelService` 响应 */
data class LabelServiceResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtLabelGroup>? = null,
)

/**
 * 服务分组。
 *
 * 实测返回 3 组：`全部 11` / `查询 5` / `申请 6`——顶部三个统计数字就是各组的 [serviceNum]。
 */
data class JwxtLabelGroup(
    val labelName: String = "",
    val labelId: String = "",
    val serviceNum: Int = 0,
    val serviceList: List<JwxtService> = emptyList(),
)

/** 单个服务入口（九宫格的一项） */
data class JwxtService(
    val serviceKey: String = "",
    val serviceName: String = "",
    /** 相对路径，如 `icon/service/cjcx@2x.png`；用 [JwxtConstants.iconUrl] 补全 */
    val serviceIcon: String = "",
    /** hash 路由，如 `#/cjcx_v5`；用 [JwxtConstants.pageUrl] 补全 */
    val url: String = "",
    val appId: String = "",
)

/** `POST biz/home/listMoreScene` 响应 */
data class SceneResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtScene>? = null,
)

/** 首页场景（实测 2 个：学生社会考试报名、学生评教） */
data class JwxtScene(
    val sceneId: String = "",
    val sceneName: String = "",
    val welcomeTip: String = "",
    val sceneCardList: List<JwxtSceneCard> = emptyList(),
)

/** 场景卡片；接口只提供小图标（[cardIcon]，完整 URL），没有插画字段 */
data class JwxtSceneCard(
    val cardId: String = "",
    val cardName: String = "",
    val cardIcon: String = "",
    val serviceList: List<JwxtService> = emptyList(),
)

// ═══════════════════════════════════════════
//  今日课程 / 本学期考试（首页 Tab 卡）
// ═══════════════════════════════════════════

/**
 * `GET biz/v410/schedule/listStudentTodayLesson` 响应（外层）。
 *
 * 教务这批接口是**双层包裹**：`{code,msg,data:{code,msg,data:[…]}}`（与首页那两个单层接口不同），
 * 两层业务码都要校验。
 */
data class TodayLessonResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: TodayLessonPage? = null,
)

/** 今日课程响应内层 */
data class TodayLessonPage(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtTodayLesson> = emptyList(),
)

/**
 * 今日课程的一条。
 *
 * 实测顶层的 `courseName` / `building` / `classroom` / `className` **全是 null**，课程内容
 * 只存在于 [cellDetail]（逐行富文本行），所以 UI 必须逐行渲染、不能按下标取字段；
 * `titleDetail` 与 `todayWeekDay` 网页端未使用，不声明。
 */
data class JwxtTodayLesson(
    val startSession: Int = 0,
    val endSession: Int = 0,
    val startTime: String = "",
    val endTime: String = "",
    val cellDetail: List<JwxtCellLine> = emptyList(),
    /** 形如 `00:正常 01：调课 02：停课 03：补课`，网页取前两位判断「调」/「补」 */
    val classTransferTypeCode: String = "",
)

/** [JwxtTodayLesson.cellDetail] 的一行；[color] 非空表示网页用红字标出（教师/时间/地点行） */
data class JwxtCellLine(
    val color: String? = null,
    val text: String = "",
)

/** `GET biz/v410/examTask/recentExams` 响应（外层，同样是双层包裹） */
data class RecentExamResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: RecentExamPage? = null,
)

/** 本学期考试响应内层 */
data class RecentExamPage(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtExam> = emptyList(),
)

/**
 * 本学期考试的一条。
 *
 * 展示字段：网页直接用 [timeNote]（已含完整日期与时间）当标题。
 * [examStart] / [timeStart] 只用于排序——接口按时间降序返回，UI 需要升序（与「今日课程」一致）。
 * 每行都相同的 `batchName`，以及网页未参与渲染的 `examTaskStatus` / `showKeys` 不声明。
 */
data class JwxtExam(
    val courseName: String = "",
    /** 考试日期 `yyyy-MM-dd`（排序用） */
    val examStart: String = "",
    /** 开考时间 `HH:mm`（同日多场时用） */
    val timeStart: String = "",
    val timeNote: String = "",
    val classroomName: String? = null,
    val seatNo: Int? = null,
    val teacher: String? = null,
)

// ═══════════════════════════════════════════
//  更多服务（listLabelServiceSet）
// ═══════════════════════════════════════════

/** `GET biz/home/listLabelServiceSet` 响应（单层信封，与首页两个接口同款） */
data class LabelServiceSetResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtServiceGroup>? = null,
)

/**
 * 一个分组（全部 / 查询 / 申请）。
 *
 * 注意：本接口的 `serviceList` 装的是**分类**而不是服务——比首页 `listLabelService` 多一层，
 * 所以类型是 [JwxtServiceCategory]。
 */
data class JwxtServiceGroup(
    val labelName: String = "",
    val labelId: String = "",
    val serviceNum: Int = 0,
    val serviceList: List<JwxtServiceCategory> = emptyList(),
)

/** 分组下的一个分类（学籍 / 选课 / 考务 / 其他 / 评教）；服务字段与首页一致，复用 [JwxtService] */
data class JwxtServiceCategory(
    val categoryName: String = "",
    val serviceList: List<JwxtService> = emptyList(),
)
