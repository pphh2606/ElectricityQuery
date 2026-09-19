package edu.cqwu.electricity.jwxt.timetable.domain

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse

/**
 * 课程卡片的正文：卡片上显示什么、以及从哪一行取「教师 / 时间 / 地点」。
 *
 * 从 `TimetableScreen.kt` 抽到 domain 层：界面「量行数」与「真正渲染」必须共用这里的同一份内容，
 * 否则算出的行高与画出来的行数会对不上。两处共用同一份，也是原实现里特意写明的约束。
 */

/** 第二行（教师 / 时间 / 地点）比课程名小一号 */
private val CARD_INFO_FONT_SIZE = 11.sp

/** 接口正文里任课教师链接的标记（`data-kblx="02"` 教师、`"01"` 教室、`"05"` 班级） */
private const val TEACHER_LINK_MARK = "data-kblx=\"02\""

/** 课程卡片上的正文（课程名 + 教师/时间/地点那条）；量行数与真正渲染共用同一份内容 */
internal fun courseCardText(course: JwxtTimetableCourse): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(course.courseName) }

    val info = courseInfoLine(course)
    if (info.isNotBlank()) {
        append('\n')
        withStyle(SpanStyle(fontSize = CARD_INFO_FONT_SIZE)) { append(info) }
    }
}

/**
 * 卡片第二行显示的内容：接口正文里「教师 / 时间 / 地点」那一条（即第一条带教师链接的行）。
 *
 * 以前只取 `placeName` 字段，会丢掉授课方式、教师和时间；整条显示与网页版一致
 * （形如 `[讲授] 李春花 14:30-16:10 N知津-A205`，前面通常还会带上上课周次）。
 * 接口没给这类行时退回 `placeName`。
 */
internal fun courseInfoLine(course: JwxtTimetableCourse): String =
    course.cellDetail.orEmpty()
        .firstOrNull { it.text.contains(TEACHER_LINK_MARK) }
        ?.text
        ?.let(HtmlFormParser::stripHtml)
        .orEmpty()
        .ifBlank { course.placeName }

/** 教师标记：锚文本是人名 */
internal const val KB_TEACHER = "02"

/** 教室标记：锚文本是地点 */
internal const val KB_CLASSROOM = "01"

/** 带 `data-kblx` 的锚，形如 `<a data-kblx="01" data-code="…">N知津-A309</a>` */
private val KBLX_ANCHOR = Regex("""data-kblx="(\d+)"[^>]*>([^<]*)<""")

/** 正文里形如 `08:10-09:50` 的上课时间（容忍 `16:20 - 18:00` 这种带空格的写法） */
private val TIME_RANGE = Regex("""\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}""")

/**
 * 从课程正文里抠出上课时间（形如 `08:10-09:50`）；抠不到返回空串。
 *
 * 课表接口**没有**单独的时间字段（[JwxtTimetableCourse] 只有节次号），时间只存在于
 * 服务端排好版的正文里，所以只能这样取。取不到就退化成只显示节次，不影响其它内容。
 * 正文里的周次（`1-16周`）与日期不会误匹配——它们没有冒号。
 */
internal fun timeRangeOf(course: JwxtTimetableCourse): String =
    course.cellDetail.orEmpty()
        .firstNotNullOfOrNull { line -> TIME_RANGE.find(line.text)?.value }
        .orEmpty()
        .filterNot { it == ' ' }

/**
 * 取指定 `data-kblx` 标记的锚文本（重复出现时取第一处）；没有该标记时返回空串。
 *
 * [kblx] 用 [KB_TEACHER] 或 [KB_CLASSROOM]。不能按行号取——实测正文行数不固定，
 * 同一门课的相关行还可能重复出现。
 */
internal fun anchorTextOf(course: JwxtTimetableCourse, kblx: String): String =
    course.cellDetail.orEmpty()
        .firstNotNullOfOrNull { line ->
            KBLX_ANCHOR.findAll(line.text)
                .firstOrNull { it.groupValues[1] == kblx }
                ?.groupValues?.get(2)
                ?.trim()
        }
        .orEmpty()
