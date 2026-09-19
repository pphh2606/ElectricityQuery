package edu.cqwu.electricity.jwxt.schedulequery.data

import edu.cqwu.electricity.jwxt.core.net.JwxtApiBase
import java.net.URLEncoder

/**
 * 「全校课表查询」的接口声明：**选查询对象**（教室 / 教师 / 班级）以及各级筛选项字典。
 *
 * 选中对象之后的取数（校区、周次、节次、课表详情）属于课表域，在 `TimetableApi` 里。
 * 发请求的规矩在 [JwxtApiBase] / `JwxtHttpClient`。
 */
class ScheduleQueryApi : JwxtApiBase() {

    /**
     * 教室列表。实测全校 1314 条，**必须靠 [keyword] 或校区 / 楼栋筛选**（10~20 条一页翻不动）。
     *
     * @param keyword 教室名模糊搜索（接口参数 `classroomName`，实测 1314 → 118）
     * @param arrangedOnly 只看已排课的教室（接口参数 `arranged=1`，实测 1314 → 632）
     */
    suspend fun fetchClassroomList(
        termCode: String,
        pageNumber: Int,
        pageSize: Int = QUERY_PAGE_SIZE,
        keyword: String = "",
        campusCode: String = "",
        buildingCode: String = "",
        arrangedOnly: Boolean = false,
    ): Result<JwxtPagedRows<JwxtClassroomRow>> =
        request(
            path = "biz/v510/schedule/listClassroom?" + queryString(
                "pageNumber" to pageNumber,
                "pageSize" to pageSize,
                "termCode" to termCode,
                "classroomName" to keyword,
                "campusCode" to campusCode,
                "buildingCode" to buildingCode,
                "arranged" to if (arrangedOnly) "1" else "",
            ),
            isPost = false,
        ) { json ->
            val resp = gson.fromJson(json, ClassroomListResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listClassroom")
            resp.data ?: JwxtPagedRows()
        }

    /**
     * 教师列表。实测全校 4090 人。
     *
     * @param keyword 姓名搜索（接口参数 `name`，实测 4090 → 197）
     * @param depart 院系筛选（接口参数 `depart`，实测 4090 → 48）
     * @param sex 性别筛选（接口参数 `sex`，取值 `1` 男 / `2` 女，实测 4090 → 2296）
     * @param teachedOnly 只看本学期有授课任务的（接口参数 `teached=1`，实测 4090 → 1031）
     * @param outSourceOnly 只看外聘教师（接口参数 `outSource=1`，实测 4090 → 2241）
     *
     * 注：职称（接口参数 `jobTitle`）实测**失效**——无论传字典里的 id 还是数据里的真实职称名都返回 0 条
     * （数据里明明有"教授"），故本项目不提供职称筛选，连职称字典接口也不再声明。
     */
    suspend fun fetchTeacherList(
        termCode: String,
        pageNumber: Int,
        pageSize: Int = QUERY_PAGE_SIZE,
        keyword: String = "",
        depart: String = "",
        sex: String = "",
        arrangedOnly: Boolean = false,
        teachedOnly: Boolean = false,
        outSourceOnly: Boolean = false,
    ): Result<JwxtPagedRows<JwxtTeacherRow>> =
        request(
            path = "biz/v510/schedule/listTeacher?" + queryString(
                "pageNumber" to pageNumber,
                "pageSize" to pageSize,
                "termCode" to termCode,
                "name" to keyword,
                "depart" to depart,
                "sex" to sex,
                "arranged" to if (arrangedOnly) "1" else "",
                "teached" to if (teachedOnly) "1" else "",
                "outSource" to if (outSourceOnly) "1" else "",
            ),
            isPost = false,
        ) { json ->
            val resp = gson.fromJson(json, TeacherListResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listTeacher")
            resp.data ?: JwxtPagedRows()
        }

    /**
     * 班级列表。实测全校 4529 条，**接口不支持按班级名搜索**（实测传 `className` 结果不变），
     * 只能靠年级 / 学院（[deptCode]）/ 专业把范围缩下来。
     *
     * @param deptCode 学院代码——接口参数名就是 `deptCode`（网页前端内部叫 `departCode`，发请求时改名）
     */
    suspend fun fetchClassList(
        termCode: String,
        pageNumber: Int,
        pageSize: Int = QUERY_PAGE_SIZE,
        deptCode: String = "",
        gradeCode: String = "",
        arrangedOnly: Boolean = false,
        teachedOnly: Boolean = false,
    ): Result<JwxtPagedRows<JwxtClassRow>> =
        request(
            path = "biz/v510/schedule/listClass?" + queryString(
                "pageNumber" to pageNumber,
                "pageSize" to pageSize,
                "termCode" to termCode,
                "deptCode" to deptCode,
                "gradeCode" to gradeCode,
                "arranged" to if (arrangedOnly) "1" else "",
                "teached" to if (teachedOnly) "1" else "",
            ),
            isPost = false,
        ) { json ->
            val resp = gson.fromJson(json, ClassListResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listClass")
            resp.data ?: JwxtPagedRows()
        }

    /** 校区字典（教室查询的筛选项）；实测返回星湖 / 红河A区 / 红河B区 */
    suspend fun fetchCampusDict(): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/schedule/listCampusDict", "listCampusDict")

    /**
     * 某校区的楼栋列表（教室查询的筛选项）。
     *
     * 参数名是 **`campusNo`**（接口如此）：实测传 `campusCode` 会返回「校区代码不可为空」。
     */
    suspend fun fetchBuildingList(campusNo: String): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/schedule/listBuilding?campusNo=$campusNo", "listBuilding")

    /** 院系字典（教师查询的筛选项） */
    suspend fun fetchDepartList(): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/schedule/departList", "departList")

    /** 性别字典（教师查询的筛选项） */
    suspend fun fetchSexList(): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/schedule/sexList", "sexList")

    /** 年级字典（班级查询级联的第一级） */
    suspend fun fetchLinkGrades(): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/link/grade?refererKey=$SCHEDULE_REFERER_KEY", "grade")

    /** 学院字典（班级查询级联的第二级） */
    suspend fun fetchLinkColleges(): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/link/college?refererKey=$SCHEDULE_REFERER_KEY", "college")

    /** 专业字典（班级查询级联的第三级，依赖年级 + 学院） */
    suspend fun fetchLinkMajors(grade: String, college: String): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/link/major?grade=$grade&college=$college", "major")

    /** 班级字典（班级查询级联的第四级，依赖年级 + 学院 + 专业） */
    suspend fun fetchLinkClasses(grade: String, college: String, major: String): Result<List<JwxtDictItem>> =
        fetchDict("biz/v510/link/class?grade=$grade&college=$college&major=$major", "class")

    /**
     * 某对象（教室 / 教师 / 班级）所在的校区。
     *
     * **必须先调它再查课表**：实测校区与对象不匹配时，课表接口会返回空列表。
     *
     * @param scheduleType 就是 KBLX（`ScheduleTargetType.kblx`）
     */
    suspend fun fetchScheduledCampus(
        termCode: String,
        code: String,
        scheduleType: String,
    ): Result<List<JwxtDictItem>> =
        fetchDict(
            "biz/v510/schedule/getMyScheduledCampus?termCode=$termCode&code=$code&scheduleType=$scheduleType",
            "getMyScheduledCampus",
        )

    /**
     * 字典类接口统一解析：校区 / 楼栋 / 院系 / 性别 / 年级 / 学院 / 专业 / 班级这 8 个接口的
     * 响应信封与数据形状完全一致（`{code,msg,data:[{id,name}]}`），共用一个私有方法。
     */
    private suspend fun fetchDict(path: String, tag: String): Result<List<JwxtDictItem>> =
        request(path, isPost = false) { json ->
            val resp = gson.fromJson(json, JwxtDictResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, tag)
            resp.data ?: emptyList()
        }

    /** 拼查询串：跳过空值，值做 URL 编码（教室名 / 姓名 / 院系名可能含中文） */
    private fun queryString(vararg pairs: Pair<String, Any?>): String =
        pairs.filter { it.second != null && it.second.toString().isNotEmpty() }
            .joinToString("&") { "${it.first}=${URLEncoder.encode(it.second.toString(), "UTF-8")}" }

    private companion object {
        /** 列表接口的默认每页条数 */
        const val QUERY_PAGE_SIZE = 20

        /** 班级查询的级联接口（年级 / 学院）要求的 refererKey（抓包实证） */
        const val SCHEDULE_REFERER_KEY = "jwpc.api.v5.schedule.referer"
    }
}
