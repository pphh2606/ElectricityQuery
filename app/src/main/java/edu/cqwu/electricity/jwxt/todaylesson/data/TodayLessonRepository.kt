package edu.cqwu.electricity.jwxt.todaylesson.data

import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.jwxt.core.JwxtConstants

/**
 * 今日课程仓库：教务首页那张「今日课程」卡片的取数入口。
 *
 * 职责：网络取数 → 清洗成 [JwxtLessonUi]。
 * 这段逻辑原来挂在 `JwxtHomeViewModel`（UI 层）里，搬到数据层后既能让单测直接验证，也不再受界面变动影响。
 * （周课表页用的是另一套接口 `getMyScheduleDetail`，与这里不共用。）
 *
 * 它原先还负责"为桌面小组件落缓存 + 发缓存已更新信号"；旧小组件删除后这两件事都没有使用者了，
 * 于是回归成纯粹的取数入口——桌面上那份数据现在由 `timetable` 的 V2 小组件自己维护（数据源是课表接口）。
 */
object TodayLessonRepository {

    private val api = TodayLessonApi()

    /**
     * 拉取今日课程并清洗成 UI 模型。
     *
     * 失败（含会话过期）原样返回 `Result.failure`，由调用方决定如何呈现——不做自动重试。
     */
    suspend fun fetchTodayLessons(): Result<List<JwxtLessonUi>> =
        api.fetchTodayLessons().map { toUiLessons(it) }

    /** `data-kblx` 标记：`02` 教师（锚文本是人名）、`01` 教室（锚文本是地点），`05` 是教学班 */
    private const val KB_TEACHER = "02"
    private const val KB_CLASSROOM = "01"

    /** 匹配 `cellDetail` 里的语义锚：`<a data-kblx="01" data-code="…">N知津-A309</a>` */
    private val KBLX_ANCHOR = Regex("""data-kblx="(\d+)"[^>]*>([^<]*)<""")

    /** 匹配行内任意锚：网页把这些 `<a>`（教师 / 教室 / 班级）渲染成主题色 */
    private val LINK_ANCHOR = Regex("""<a\b[^>]*>([^<]*)</a>""", RegexOption.IGNORE_CASE)

    /**
     * 今日课程映射（原 `JwxtHomeViewModel.buildLessons()`）。
     *
     * 接口返回顺序是乱的（实测 5-6 → 1-2 → 3-4 → 7-8 节），按开始节次升序排。
     * 课程内容只能来自 `cellDetail`（顶层 `courseName`/`classroom` 等实测全为 null）：
     * 无颜色行是课程名与上课班级，红字行是「教师 + 时间 + 教室」的富文本。
     * 教师与教室另按 `data-kblx` 标记单独提取，供桌面小组件按三行展示——不能按行号取，
     * 实测行数不固定（3 行与 4 行都有），同一门课的红字行还可能重复出现。
     *
     * 可见性为 internal：让单测能用真实抓包数据直接验证清洗结果。
     */
    internal fun toUiLessons(lessons: List<JwxtTodayLesson>): List<JwxtLessonUi> =
        lessons.sortedBy { it.startSession }.map { lesson ->
            JwxtLessonUi(
                sectionRange = "${lesson.startSession}-${lesson.endSession}",
                timeRange = "${lesson.startTime}-${lesson.endTime}",
                transferTag = lesson.classTransferTypeCode.take(2)
                    .takeIf { it == JwxtConstants.TRANSFER_TYPE_ADJUST || it == JwxtConstants.TRANSFER_TYPE_MAKEUP }
                    .orEmpty(),
                courseName = courseNameOf(lesson),
                teacher = anchorText(lesson, KB_TEACHER),
                classroom = anchorText(lesson, KB_CLASSROOM),
                lines = lesson.cellDetail.orEmpty()
                    .map { toSpans(it.text) }
                    .filter { it.isNotEmpty() }
                    .map { JwxtLessonLine(it) },
            )
        }

    /**
     * 课程 / 教学班名：`cellDetail` 第一条无颜色行（如 `数据结构*001班`）。
     *
     * 紧随其后的第二行（`数据结构*`）是它的前缀，属接口冗余，不取——这正是桌面上课程名重复的原因。
     */
    private fun courseNameOf(lesson: JwxtTodayLesson): String =
        lesson.cellDetail.orEmpty()
            .firstOrNull { it.color == null }
            ?.let { HtmlFormParser.stripHtml(it.text) }
            .orEmpty()

    /** 取指定 `data-kblx` 标记的锚文本（重复出现时取第一处）；没有该标记时返回空串 */
    private fun anchorText(lesson: JwxtTodayLesson, kblx: String): String =
        lesson.cellDetail.orEmpty()
            .firstNotNullOfOrNull { line ->
                KBLX_ANCHOR.findAll(line.text)
                    .firstOrNull { it.groupValues[1] == kblx }
                    ?.groupValues?.get(2)
                    ?.trim()
            }
            .orEmpty()

    /**
     * 把一行富文本拆成片段：先取整行纯文本，再把每个 `<a>` 的文本标成链接片段。
     *
     * 用「上次位置之后继续 indexOf」的顺序定位，而不是在整行文本上重新匹配，
     * 这样拼起来的结果与整行 `stripHtml` 逐字一致——词间空格不会因为拆段而丢掉。
     * 定位不到（接口格式变化）时该锚只当普通文本处理：不影响其它片段，也不会崩。
     */
    private fun toSpans(html: String): List<JwxtLessonSpan> {
        val text = HtmlFormParser.stripHtml(html)
        if (text.isEmpty()) return emptyList()

        val spans = mutableListOf<JwxtLessonSpan>()
        var cursor = 0
        LINK_ANCHOR.findAll(html).forEach { match ->
            val link = HtmlFormParser.stripHtml(match.groupValues[1])
            val start = if (link.isEmpty()) -1 else text.indexOf(link, cursor)
            if (start < 0) return@forEach
            if (start > cursor) spans += JwxtLessonSpan(text.substring(cursor, start), isLink = false)
            spans += JwxtLessonSpan(text.substring(start, start + link.length), isLink = true)
            cursor = start + link.length
        }
        if (cursor < text.length) spans += JwxtLessonSpan(text.substring(cursor), isLink = false)
        return spans
    }
}
