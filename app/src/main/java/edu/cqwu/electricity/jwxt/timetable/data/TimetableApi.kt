package edu.cqwu.electricity.jwxt.timetable.data

import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTerm
import edu.cqwu.electricity.jwxt.core.model.JwxtSection
import edu.cqwu.electricity.jwxt.core.model.JwxtTermWeek
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse
import edu.cqwu.electricity.jwxt.core.model.ScheduleDetailRequest
import edu.cqwu.electricity.jwxt.core.model.ScheduleDetailResponse
import edu.cqwu.electricity.jwxt.core.model.ScheduleTargetDetailRequest
import edu.cqwu.electricity.jwxt.core.model.ScheduleTermResponse
import edu.cqwu.electricity.jwxt.core.model.SectionListResponse
import edu.cqwu.electricity.jwxt.core.model.TermWeeksResponse
import edu.cqwu.electricity.jwxt.core.net.JwxtApiBase
import edu.cqwu.electricity.jwxt.core.JwxtConstants

/**
 * 「课表」的接口声明：我的课表（v410）与查任意对象的课表（v510）都在这里，
 * 因为它们是同一件事的两条路径（[fetchScheduleDetail] 取自己的、[fetchTargetScheduleDetail] 取别人的）。
 *
 * 发请求的规矩在 [JwxtApiBase] / `JwxtHttpClient`。
 */
class TimetableApi : JwxtApiBase() {

    /**
     * 学期列表（无参数）。
     *
     * 「我的课表」与「全校课表查询」都要用它选学期，所以放在这里（后者依赖课表域的这个接口）。
     */
    suspend fun fetchScheduleTerms(): Result<List<JwxtScheduleTerm>> =
        request("biz/v410/schedule/termList", isPost = false) { json ->
            val resp = gson.fromJson(json, ScheduleTermResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "termList")
            resp.data ?: emptyList()
        }

    /** 某学期的周次列表（`curWeek` 标记当前周） */
    suspend fun fetchTermWeeks(termCode: String): Result<List<JwxtTermWeek>> =
        request("biz/v410/schedule/getTermWeeks?termCode=$termCode", isPost = false) { json ->
            val resp = gson.fromJson(json, TermWeeksResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "getTermWeeks")
            resp.data ?: emptyList()
        }

    /**
     * 课表左侧的节次列（学校定制的 v510 接口）。
     *
     * [kblx] 是课表类型：`03` 我的课表（默认）/ `01` 教室 / `02` 教师 / `05` 班级；
     * [campusCode] 查别人的课表时要传对象所在校区——实测写死空串在查教室时拿不到节次。
     */
    suspend fun fetchSections(
        termCode: String,
        campusCode: String = "",
        kblx: String = JwxtConstants.KBLX_MY_SCHEDULE,
    ): Result<List<JwxtSection>> =
        request(
            path = "biz/v510/cqwu/schedule/getQxSectionList?termCode=$termCode&campusCode=$campusCode&week=&kblx=$kblx",
            isPost = false,
        ) { json ->
            val resp = gson.fromJson(json, SectionListResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "getQxSectionList")
            resp.data ?: emptyList()
        }

    /**
     * **我的**课表详情（v410，服务端按会话身份取数）；[week] 为 null 表示整学期（请求体里是空串）。
     *
     * 周次过滤由服务端完成——网页前端同样不解析「1-3周,5-9周」这类字符串。
     */
    suspend fun fetchScheduleDetail(termCode: String, week: Int?): Result<List<JwxtTimetableCourse>> =
        request(
            path = "biz/v410/schedule/getMyScheduleDetail",
            isPost = true,
            jsonBody = gson.toJson(ScheduleDetailRequest(termCode, "", week ?: "")),
        ) { json ->
            val resp = gson.fromJson(json, ScheduleDetailResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "getMyScheduleDetail")
            resp.data?.arrangedList.orEmpty()
        }

    /**
     * **指定对象**的课表详情（v510）；[kblx] 用 `ScheduleTargetType.kblx`，[week] 为 null 表示整学期。
     *
     * 与 [fetchScheduleDetail] 是两套接口，**不能互相替代**：实测在 v410 的请求体里塞 CODE / KBLX
     * 会被服务端忽略，永远返回自己的课表。
     */
    suspend fun fetchTargetScheduleDetail(
        termCode: String,
        week: Int?,
        kblx: String,
        code: String,
        campusCode: String,
    ): Result<List<JwxtTimetableCourse>> =
        request(
            path = "biz/v510/schedule/getScheduleDetail",
            isPost = true,
            jsonBody = gson.toJson(ScheduleTargetDetailRequest(termCode, campusCode, kblx, code, week ?: "")),
        ) { json ->
            val resp = gson.fromJson(json, ScheduleDetailResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "getScheduleDetail")
            resp.data?.arrangedList.orEmpty()
        }
}
