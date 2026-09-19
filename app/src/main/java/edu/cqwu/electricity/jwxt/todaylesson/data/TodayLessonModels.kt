package edu.cqwu.electricity.jwxt.todaylesson.data

import edu.cqwu.electricity.jwxt.core.model.JwxtCellLine

/**
 * 「今日课程」接口（`biz/v410/schedule/listStudentTodayLesson`）的数据模型。
 *
 * 字段取自 `fortest/教务系统.har.json` 的真实响应，**页面用不到的字段不声明**。
 */

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
    /** 可空：实测「近期没有数据」时接口返回显式 null（不是空数组），见 TodayLessonApi 里的 orEmpty() */
    val data: List<JwxtTodayLesson>? = null,
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
    val cellDetail: List<JwxtCellLine>? = null,
    /** 形如 `00:正常 01：调课 02：停课 03：补课`，网页取前两位判断「调」/「补」 */
    val classTransferTypeCode: String = "",
)
