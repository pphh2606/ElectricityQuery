package edu.cqwu.electricity.jwxt.todaylesson.data

import edu.cqwu.electricity.jwxt.core.net.JwxtApiBase

/**
 * 「今日课程」的接口声明；发请求的规矩在 [JwxtApiBase] / `JwxtHttpClient`。
 *
 * 被教务首页的今日课程卡与桌面小组件共用（后者经 `TodayLessonRepository` 读缓存，不直接调这里）。
 */
class TodayLessonApi : JwxtApiBase() {

    /**
     * 今日课程（无查询参数）。
     *
     * 双层包裹，两层业务码都校验；当天没课时内层 `data` 是空数组（上一份抓包即为该形态）。
     */
    suspend fun fetchTodayLessons(): Result<List<JwxtTodayLesson>> =
        request("biz/v410/schedule/listStudentTodayLesson", isPost = false) { json ->
            val resp = gson.fromJson(json, TodayLessonResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listStudentTodayLesson")
            val page = resp.data ?: return@request emptyList<JwxtTodayLesson>()
            checkBusinessCode(page.code, page.msg, "listStudentTodayLesson")
            // 内层 data 可能是显式 null（当天没课时后端这样返回，实测过），必须兜底
            page.data.orEmpty()
        }
}
