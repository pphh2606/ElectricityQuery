package edu.cqwu.electricity.jwxt.home.data

import edu.cqwu.electricity.jwxt.core.net.JwxtApiBase

/** 教务首页的接口声明；发请求的规矩在 [JwxtApiBase] / `JwxtHttpClient` */
class HomeApi : JwxtApiBase() {

    /**
     * 首页服务分组（前 3 组用于顶部统计数字，第 1 组的 `serviceList` 用于九宫格）。
     *
     * @param num 只影响接口返回的推荐服务条数，与分组统计无关，沿用抓包中的 7
     */
    suspend fun fetchLabelServices(num: Int = DEFAULT_SERVICE_NUM): Result<List<JwxtLabelGroup>> =
        request("biz/home/listLabelService?num=$num", isPost = false) { json ->
            val resp = gson.fromJson(json, LabelServiceResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listLabelService")
            resp.data ?: emptyList()
        }

    /** 首页场景卡片 */
    suspend fun fetchMoreScenes(): Result<List<JwxtScene>> =
        request("biz/home/listMoreScene", isPost = true) { json ->
            val resp = gson.fromJson(json, SceneResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listMoreScene")
            resp.data ?: emptyList()
        }

    /** 本学期考试（无查询参数，按考试时间降序返回） */
    suspend fun fetchRecentExams(): Result<List<JwxtExam>> =
        request("biz/v410/examTask/recentExams", isPost = false) { json ->
            val resp = gson.fromJson(json, RecentExamResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "recentExams")
            val page = resp.data ?: return@request emptyList<JwxtExam>()
            checkBusinessCode(page.code, page.msg, "recentExams")
            // 内层 data 可能是显式 null（近期没有考试时后端这样返回，实测会打崩首页），必须兜底
            page.data.orEmpty()
        }

    private companion object {
        /** 首页服务分组的推荐条数（沿用抓包中的 7） */
        const val DEFAULT_SERVICE_NUM = 7
    }
}
