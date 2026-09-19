package edu.cqwu.electricity.jwxt.score.data

import edu.cqwu.electricity.jwxt.core.net.JwxtApiBase

/** 「成绩查询」的接口声明；发请求的规矩在 [JwxtApiBase] / `JwxtHttpClient` */
class ScoreApi : JwxtApiBase() {

    /**
     * 学期列表（无参数）。
     *
     * 实测下发「全部学期（`*`）」+ 各具体学期，页面按这个顺序直接铺胶囊按钮。
     * 与课表的 `schedule/termList` 是两个不同接口（路径与字段都不同），不共用。
     */
    suspend fun fetchScoreTerms(): Result<List<JwxtScoreTerm>> =
        request("biz/v510/score/termList", isPost = false) { json ->
            val resp = gson.fromJson(json, ScoreTermResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "termList")
            resp.data ?: emptyList()
        }

    /**
     * 某学期的成绩；[termCode] 传 `*` 时一次返回所有学期的分组。
     *
     * 接口按学期分组下发，这里直接铺平成一条列表——每条成绩自带 `termName`，页面不需要分组结构。
     */
    suspend fun fetchTermScores(termCode: String): Result<List<JwxtScore>> =
        request(
            path = "biz/v510/score/termScore",
            isPost = true,
            jsonBody = gson.toJson(ScoreTermRequest(termCode)),
        ) { json ->
            val resp = gson.fromJson(json, TermScoreResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "termScore")
            resp.data?.termScoreList.orEmpty().flatMap { it.scoreList.orEmpty() }
        }
}
