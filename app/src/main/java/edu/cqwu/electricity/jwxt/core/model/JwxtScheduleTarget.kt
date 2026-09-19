package edu.cqwu.electricity.jwxt.core.model

/**
 * 课表查询对象类型；[kblx] 是接口的课表类型编码（实测：`01` 教室 / `02` 教师 / `05` 班级）。
 *
 * 放在共享层：由「全校课表查询」产出（用户选中的对象），由「课表」消费（按它取节次、取课表）。
 */
enum class ScheduleTargetType(val kblx: String) {
    CLASSROOM("01"),
    TEACHER("02"),
    CLASS("05"),
    ;

    companion object {
        fun fromKblx(kblx: String): ScheduleTargetType? = entries.firstOrNull { it.kblx == kblx }
    }
}

/**
 * 一次课表查询的目标（教室 / 教师 / 班级其一）。
 *
 * [campusCode] 由 `ScheduleQueryApi.fetchScheduledCampus` 拿到——**不能写死**：实测校区与对象不匹配时，
 * 课表接口会返回空列表（同一个教师换错校区就是 0 条）。
 */
data class JwxtScheduleTarget(
    val type: ScheduleTargetType,
    val code: String,
    val name: String,
    val campusCode: String,
)
