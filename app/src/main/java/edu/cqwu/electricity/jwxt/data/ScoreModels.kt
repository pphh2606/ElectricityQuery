package edu.cqwu.electricity.jwxt.data

/**
 * 「成绩查询」接口（`biz/v510/score/`）的数据模型，字段取自 `fortest/成绩查询.har.txt` 的真实响应。
 * 页面用不到的字段（`courseEnglishName`、`majorFlag`、`courseId`、`isShowScoreItem`、`isToEvaluation` 等）不声明。
 */

/** `GET biz/v510/score/termList` 响应 */
data class ScoreTermResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtScoreTerm>? = null,
)

/** 一个学期选项；`*` 是接口下发的虚拟项「全部学期」（传它时 [TermScorePage] 会含多个分组） */
data class JwxtScoreTerm(
    val termCode: String = "",
    val termName: String = "",
)

/** `POST biz/v510/score/termScore` 请求体 */
data class ScoreTermRequest(val termCode: String)

/** `POST biz/v510/score/termScore` 响应 */
data class TermScoreResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: TermScorePage? = null,
)

/** 成绩响应内层；`termCode` 传具体学期时 [termScoreList] 只有一项 */
data class TermScorePage(
    val termScoreList: List<JwxtTermScore>? = null,
)

/** 一个学期的成绩分组；学期名在每条成绩上（[JwxtScore.termName]），分组层只需要成绩列表 */
data class JwxtTermScore(
    val scoreList: List<JwxtScore>? = null,
)

/**
 * 一条成绩记录。
 *
 * [coursePoint] 是**学分**——字段名容易被读成"绩点"，但网页按「X学分」显示（实测出现过 0.25）。
 * [passFlag] 保留可空：网页在 `null` 时不给成绩着色，本地照此处理。
 */
data class JwxtScore(
    val id: String = "",
    val termName: String = "",
    val courseName: String = "",
    val score: String = "",
    val passFlag: Boolean? = null,
    val coursePoint: String = "",
    val courseTypeName: String = "",
    /** 修读方式（初修 / 重修…） */
    val examProp: String = "",
    /** 考核方式（考试 / 考查…）；实测存在空串，空的整段不显示 */
    val examType: String = "",
    /** 是否有成绩详情页；网页以它（或「成绩分项」）决定这条能否点进去 */
    val isToDetail: Boolean = false,
)
