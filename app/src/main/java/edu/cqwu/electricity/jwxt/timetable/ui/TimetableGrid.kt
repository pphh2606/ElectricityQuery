package edu.cqwu.electricity.jwxt.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.isDark
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.model.JwxtSection
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse
import edu.cqwu.electricity.jwxt.timetable.domain.CARD_CONTENT_PADDING
import edu.cqwu.electricity.jwxt.timetable.domain.CARD_GAP
import edu.cqwu.electricity.jwxt.timetable.domain.CARD_HORIZONTAL_PADDING
import edu.cqwu.electricity.jwxt.timetable.domain.CARD_LINE_HEIGHT
import edu.cqwu.electricity.jwxt.timetable.domain.DAY_COLUMN_WIDTH
import edu.cqwu.electricity.jwxt.timetable.domain.DayLanes
import edu.cqwu.electricity.jwxt.timetable.domain.cardMaxLines
import edu.cqwu.electricity.jwxt.timetable.domain.courseCardText
import edu.cqwu.electricity.jwxt.timetable.domain.layoutDay
import edu.cqwu.electricity.jwxt.timetable.domain.sectionRowHeights

/**
 * 课表网格的渲染层：只负责把算好的位置画出来。
 *
 * 从 `TimetableScreen.kt` 拆出，两边职责分开：
 * - 那边是**页面**：顶栏、空态、下拉刷新、周次/学期切换、详情弹窗；
 * - 这里是**网格**：星期表头、节次列、7 个星期列、课程卡片。
 *
 * 「位置怎么算」一律不在这里——撞课分轨与行高在 `timetable/domain/`（有单测），
 * 本文件只负责把 Dp 值放到 `offset` / `height` / `width` 上。
 */

/**
 * 每一节的行高。
 *
 * 先用文字测量算出每门课的正文会折几行（不是"先渲染再量高度"，所以高度不会来回抖），
 * 再把「折几行 → 这一节多高」交给 domain 的 [sectionRowHeights] 做纯计算：
 * 取舍规则（只抬高确实需要的那一行、跨多节的课按节数摊）写在那里，且有单测覆盖。
 */
@Composable
private fun rememberRowHeights(
    courses: List<JwxtTimetableCourse>,
    sections: List<JwxtSection>,
): List<Dp> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 卡片正文样式：测量与渲染必须用同一份。用 remember 缓存，主题字体变化时它才会换新实例，
    // 从而让下面的 remember 重算（只换字体族/字重而不改字号时，宽度与行高都不变）
    val baseStyle = MaterialTheme.typography.labelMedium
    val textStyle = remember(baseStyle) { baseStyle.copy(lineHeight = CARD_LINE_HEIGHT) }

    // 正文可用宽度 = 列宽 − 卡片外间隙 − 卡片左右内边距
    val textWidth = with(density) {
        (DAY_COLUMN_WIDTH - (CARD_GAP + CARD_HORIZONTAL_PADDING) * 2).toPx()
    }.toInt()
    val lineHeight = with(density) { CARD_LINE_HEIGHT.toDp() }
    val verticalPadding = (CARD_GAP + CARD_CONTENT_PADDING) * 2

    return remember(courses, sections, textWidth, lineHeight, textStyle, textMeasurer) {
        // 每门课的正文在列宽下会折几行。用**下标**与 courses 一一对应，不建「课程对象 → 行数」的 Map：
        // Map 会调用课程对象的 hashCode()，万一某个字段混进 null（Gson 塞 null 是本项目踩过的坑）就会崩。
        val lineCounts = courses.map { course ->
            textMeasurer.measure(
                text = courseCardText(course),
                style = textStyle,
                constraints = Constraints(maxWidth = textWidth),
            ).lineCount
        }

        sectionRowHeights(
            courses = courses,
            lineCounts = lineCounts,
            sectionCount = sections.size,
            lineHeight = lineHeight,
            verticalPadding = verticalPadding,
        )
    }
}

/**
 * 课表网格：上方是星期表头（固定），下方是「节次列 + 7 个星期列」的整体纵向滚动区。
 *
 * 调用方已保证 [sections] 与 [courses] 都非空（为空的情况在页面里显示空状态）。
 */
@Composable
internal fun TimetableGrid(
    sections: List<JwxtSection>,
    courses: List<JwxtTimetableCourse>,
    onCourseClick: (JwxtTimetableCourse) -> Unit,
) {
    // 横向滚动位置由表头与网格共享，滑动时两行始终对齐；纵向同理（节次列与网格）
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    // 每天一列：先算好各列的轨道排布。表头与网格共用同一份结果，宽度才不会错位
    val dayLanes = remember(courses) {
        (1..JwxtConstants.DAYS_IN_WEEK).associateWith { day -> layoutDay(courses.filter { it.dayOfWeek == day }) }
    }

    // 每一节的行高（按各行内容需要算），节次列与各天共用
    val rowHeights = rememberRowHeights(courses, sections)

    // 各节的纵向起点（前缀和）：第 i 节从 rowOffsets[i] 开始，整列高度是 rowOffsets.last()；7 列共用一份
    val rowOffsets = remember(rowHeights) { rowHeights.runningFold(0.dp) { acc, h -> acc + h } }

    Column(modifier = Modifier.fillMaxSize()) {
        // 表头：不参与纵向滚动；左侧给节次列让出同样的宽度
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(SECTION_COLUMN_WIDTH))
            Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                DayHeaders(dayLanes)
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // 节次列固定在左侧，不随横向滚动移动
            Column(
                modifier = Modifier
                    .width(SECTION_COLUMN_WIDTH)
                    .verticalScroll(verticalScroll),
            ) {
                SectionColumn(sections, rowHeights)
            }

            // 课程网格：横向按天滑动（照网页一屏约 5 天），纵向与节次列同步
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll),
            ) {
                for (day in 1..JwxtConstants.DAYS_IN_WEEK) {
                    DayColumn(
                        layout = dayLanes.getValue(day),
                        rowHeights = rowHeights,
                        rowOffsets = rowOffsets,
                        onCourseClick = onCourseClick,
                    )
                }
            }
        }
    }
}

/** 星期表头：每格宽度与它下面的网格列一致（撞课那天会一起变宽，才不会错位） */
@Composable
private fun DayHeaders(dayLanes: Map<Int, DayLanes>) {
    DAY_LABEL_RES.forEachIndexed { index, label ->
        Box(
            modifier = Modifier.width(dayLanes.getValue(index + 1).width),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 左侧节次列：每节高度与网格一致（接口不给节次时间，所以只显示「第N节」）；宽度由调用方给 */
@Composable
private fun SectionColumn(sections: List<JwxtSection>, rowHeights: List<Dp>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        sections.forEachIndexed { index, section ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeights[index]),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * 星期列：把该天的课程按「第几节到第几节」纵向定位。
 *
 * 宽度分两级（与网页版一致——网页是"每个重叠组各自横向 flex 平分"）：
 * - 整列宽度 = 基准宽 × 当天**最挤的那一组**有几门课并排（见 [DayLanes.width]）；
 * - 每门课再按**它所在那一组**的轨道数平分列宽：3 门重叠各占 1/3、2 门重叠各占 1/2，
 *   独自一门则占满整列——而不是所有课都挤成一个基准宽。
 */
@Composable
private fun DayColumn(
    layout: DayLanes,
    rowHeights: List<Dp>,
    rowOffsets: List<Dp>,
    onCourseClick: (JwxtTimetableCourse) -> Unit,
) {
    val lineHeight = with(LocalDensity.current) { CARD_LINE_HEIGHT.toDp() }

    Box(modifier = Modifier.width(layout.width).height(rowOffsets.last())) {
        layout.slots.forEach { slot ->
            val course = slot.course
            val begin = (course.beginSection - 1).coerceIn(0, rowHeights.lastIndex)
            val end = (course.endSection - 1).coerceIn(begin, rowHeights.lastIndex)
            val top = rowOffsets[begin]
            val height = rowOffsets[end + 1] - top
            // 本组内平分列宽：3 门各 1/3、2 门各 1/2、单独一门占满
            val laneWidth = layout.width / slot.laneCount

            CourseCard(
                course = course,
                maxLines = cardMaxLines(height, lineHeight),
                onClick = { onCourseClick(course) },
                modifier = Modifier
                    .offset(x = laneWidth * slot.lane, y = top)
                    .width(laneWidth)
                    .height(height),
            )
        }
    }
}

/**
 * 课程卡片：接口给的浅色背景 + 课程名 + 教师/时间/地点。
 *
 * 日间：背景直接用教务下发的浅色，文字固定深色（接口给的都是浅色，跟随主题反而可能白字落在浅底上）。
 * 夜间：把浅色转成「同色相、更饱和、更暗」的卡片色（见 [toNightCardColor]），文字换成主题的浅色。
 *
 * [maxLines] 由调用方按这个格子的高度给出，而高度本身是按各行内容算的（见 [rememberRowHeights]），
 * 所以文字正常都放得下、不会出现省略号。
 */
@Composable
private fun CourseCard(
    course: JwxtTimetableCourse,
    maxLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与 Theme.kt 同一套判定：尊重 App 内的「夜间模式」设置，而不是只看系统
    val isDark = LocalAppSettingsState.current.nightMode.isDark(isSystemInDarkTheme())
    val background = courseColor(course.color)?.let { if (isDark) it.toNightCardColor() else it }
        ?: MaterialTheme.colorScheme.secondaryContainer
    // 正文拼接（取教师/时间行 + 去 HTML 标签）只在课程对象变化时做一次
    val text = remember(course) { courseCardText(course) }

    Text(
        text = text,
        modifier = modifier
            .padding(CARD_GAP)
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = CARD_HORIZONTAL_PADDING, vertical = CARD_CONTENT_PADDING),
        style = MaterialTheme.typography.labelMedium.copy(lineHeight = CARD_LINE_HEIGHT),
        color = if (isDark) MaterialTheme.colorScheme.onSurface else CARD_TEXT_COLOR,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 接口给的色值形如 `#FFF0CC`；不是这个形态就返回 null，由调用方用主题色兜底 */
private fun courseColor(hex: String): Color? {
    val raw = hex.removePrefix("#")
    if (raw.length != 6) return null
    return raw.toLongOrNull(16)?.let { Color(it or 0xFF000000L) }
}

/**
 * 夜间卡片色：接口下发的是**低饱和度的浅色**（如 `#FFF0CC` 只有约 20% 饱和度、明度接近 1），
 * 直接压暗只会得到一片「发亮的灰」。所以转到 HSV 空间里调：
 * 饱和度拉高（去掉灰）、明度压低（不再发亮），**色相不变**——黄课仍是黄调、蓝课仍是蓝调。
 *
 * 想调观感改上面三个常量即可：[NIGHT_CARD_SATURATION_SCALE] 与 [NIGHT_CARD_MIN_SATURATION]
 * 决定"上色"程度，[NIGHT_CARD_VALUE_SCALE] 决定整体明暗。
 */
private fun Color.toNightCardColor(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * NIGHT_CARD_SATURATION_SCALE).coerceIn(NIGHT_CARD_MIN_SATURATION, 1f)
    hsv[2] *= NIGHT_CARD_VALUE_SCALE
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 星期表头的文案，下标 0 对应周一（接口 `dayOfWeek` 为 1..7） */
private val DAY_LABEL_RES = intArrayOf(
    R.string.jwxt_timetable_mon,
    R.string.jwxt_timetable_tue,
    R.string.jwxt_timetable_wed,
    R.string.jwxt_timetable_thu,
    R.string.jwxt_timetable_fri,
    R.string.jwxt_timetable_sat,
    R.string.jwxt_timetable_sun,
)

private val SECTION_COLUMN_WIDTH = 44.dp

private val CARD_CORNER = 6.dp

/** 日间卡片文字色：背景是接口下发的固定浅色，故固定深色（夜间由 [CourseCard] 换成主题浅色） */
private val CARD_TEXT_COLOR = Color(0xFF333333)

/** 夜间卡片：接口浅色的饱和度提升倍数（越大颜色越"正"、越不灰） */
private const val NIGHT_CARD_SATURATION_SCALE = 3f

/** 夜间卡片：饱和度下限——接口浅色本身饱和度太低（有的只有 9%），只乘倍数仍会发灰，垫个底 */
private const val NIGHT_CARD_MIN_SATURATION = 0.4f

/** 夜间卡片：接口浅色的明度倍数（接口浅色明度接近 1，乘完即夜间实际亮度；越小越暗） */
private const val NIGHT_CARD_VALUE_SCALE = 0.40f
