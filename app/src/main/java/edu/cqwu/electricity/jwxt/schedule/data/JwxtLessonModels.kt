package edu.cqwu.electricity.jwxt.schedule.data

/**
 * 今日课程的一条（已清洗）。
 *
 * 原定义在 `jwxt/ui/JwxtHomeViewModel.kt`（UI 层）里，但课表缓存与仓库（数据层）也要用它，
 * 会让「数据层反向依赖 UI 层」，故搬到课表数据包：UI 与数据层都引这里，依赖方向保持单向。
 *
 * [sectionRange] 形如 `5-6`、[timeRange] 形如 `14:30-16:10`——含中文的「节（时间）」拼接
 * 交给 UI 层用字符串资源完成，本层不产出中文。
 */
data class JwxtLessonUi(
    val sectionRange: String,
    val timeRange: String,
    /** 调/补标签的原始码（`01` 调课 / `03` 补课），空串表示正常；文案由 UI 层映射 */
    val transferTag: String,
    val lines: List<JwxtLessonLine>,
)

/** 课程正文的一行（已去掉 HTML 标签）；[highlight] 对应接口 `color` 非空（网页的红字行） */
data class JwxtLessonLine(
    val text: String,
    val highlight: Boolean,
)
