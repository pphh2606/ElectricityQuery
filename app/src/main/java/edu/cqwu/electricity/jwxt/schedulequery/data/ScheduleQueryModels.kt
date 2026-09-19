package edu.cqwu.electricity.jwxt.schedulequery.data

import com.google.gson.annotations.SerializedName

/**
 * 「全校课表查询」的接口模型（教务 pkApp 在 `biz/v510/schedule/` 与 `biz/v510/link/` 下那批接口）。
 *
 * 字段取自 `fortest/更多课表.har.json` 的真实响应，**页面用不到的字段不声明**。
 * 与「我的课表」是两套接口：那套（v410 `getMyScheduleDetail`）服务端按会话身份返回"我自己的"，
 * 这套按课表类型编码 KBLX + CODE 查**任意对象**（教室 / 教师 / 班级），类型见共享层的
 * `JwxtScheduleTarget` 与 `ScheduleTargetType`。
 */

/** 分页壳；教室 / 教师 / 班级三个列表接口共用，各自再配一个响应壳（与项目其它接口一致，不用 `Any` 泛型） */
data class JwxtPagedRows<T>(
    val total: Int = 0,
    /** 可空：Gson 会把接口的显式 null 直接塞进字段，取用处用 orEmpty() 兜底 */
    val rows: List<T>? = null,
)

/** 字典类接口的统一响应信封：校区 / 楼栋 / 院系 / 职称 / 性别 / 年级 / 学院 / 专业 / 班级，`data` 直接是数组 */
data class JwxtDictResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtDictItem>? = null,
)

/** 通用字典项；校区字典与"已排课校区"用的是 `campusCode` 字段名，故用 alternate 兼容 */
data class JwxtDictItem(
    @SerializedName(value = "id", alternate = ["campusCode"])
    val id: String = "",
    val name: String = "",
)

// ── 教室课表 ──

/** `GET biz/v510/schedule/listClassroom` 响应 */
data class ClassroomListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: JwxtPagedRows<JwxtClassroomRow>? = null,
)

/** 教室列表的一行（实测全校 1314 条，按 `classroomName` 可搜索） */
data class JwxtClassroomRow(
    val classroomName: String = "",
    val classroomCode: String = "",
    val campusCode: String = "",
    val campusName: String = "",
    val buildingCode: String = "",
    val buildingName: String = "",
    val scheduleStatusCode: String = "",
    val scheduleStatusName: String = "",
)

// ── 教师课表 ──

/** `GET biz/v510/schedule/listTeacher` 响应 */
data class TeacherListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: JwxtPagedRows<JwxtTeacherRow>? = null,
)

/** 教师列表的一行（实测全校 4090 人，按 `name` 可搜索） */
data class JwxtTeacherRow(
    val code: String = "",
    val name: String = "",
    val depart: String = "",
    val sex: String = "",
    val jobTitle: String = "",
    /** 是否已排课（`是` / `否`） */
    val arranged: String = "",
    val teached: String = "",
    val outSource: String = "",
)

// ── 班级课表 ──

/** `GET biz/v510/schedule/listClass` 响应 */
data class ClassListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: JwxtPagedRows<JwxtClassRow>? = null,
)

/**
 * 班级列表的一行（实测全校 4529 条）。
 *
 * 注意：这个接口**不支持按班级名搜索**（实测传 `className` 结果不变），只能靠
 * 年级 → 学院 → 专业 级联把范围缩下来。
 */
data class JwxtClassRow(
    val classCode: String = "",
    val className: String = "",
    val gradeCode: String = "",
    val gradeName: String = "",
    val deptCode: String = "",
    val deptName: String = "",
    val majorCode: String = "",
    val majorName: String = "",
    val scheduleStatusName: String = "",
    val teachStatusName: String = "",
)
