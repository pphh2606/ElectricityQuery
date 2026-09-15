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
    /** 课程 / 教学班名（`cellDetail` 第一条无颜色行，如 `数据结构*001班`）；空串表示接口没给 */
    val courseName: String,
    /** 任课教师（`cellDetail` 里 `data-kblx="02"` 的锚文本）；空串表示接口没给 */
    val teacher: String,
    /** 上课教室（`data-kblx="01"` 的锚文本）；空串表示接口没给 */
    val classroom: String,
    /** 完整正文逐行（已去 HTML 标签）：教务首页卡片按原样逐行展示，桌面小组件只用上面几个字段 */
    val lines: List<JwxtLessonLine>,
)

/**
 * 课程正文的一行（已去掉 HTML 标签），拆成若干片段。
 *
 * 拆片段是为了对齐网页：网页把 `cellDetail` 每行原样渲染，行内 `<a>`（教师 / 教室 / 班级）
 * 由全局样式着主题色，接口的 `color` 字段网页并不使用——所以本项目也不再做「整行高亮」。
 */
data class JwxtLessonLine(val spans: List<JwxtLessonSpan>)

/** 行内片段；[isLink] 为 true 表示网页里是 `<a>`，UI 用主题色显示，其余用次要文字色 */
data class JwxtLessonSpan(val text: String, val isLink: Boolean)
