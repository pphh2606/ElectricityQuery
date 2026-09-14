@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.jwxt.data.JwxtConstants
import kotlinx.coroutines.launch

/**
 * 教务首页的「今日课程 / 本学期考试」Tab 卡。
 *
 * 从 `JwxtHomeScreen.kt` 拆出；数据由 [JwxtHomeViewModel] 清洗好后传进来，这里只管渲染。
 */

/**
 * 课表 / 考试卡片：两个 tab 可切换，内容来自教务接口。
 *
 * 结构照 `cardcenter/ui/BillScreen.kt` 的 `PrimaryTabRow` + `HorizontalPager` 写法。
 * 内容区（外层已是 `LazyColumn`，再嵌 lazy 列表会因无限高度约束崩溃）**不做卡内滚动**：
 * 内容多高卡片就多高，整页由外层 `LazyColumn` 统一滚动，避免嵌套滚动的手势冲突。
 *
 * 两个 tab 内容不等高会让切换时整页上移，所以每一页都把两份内容叠放（非当前份透明占位），
 * 使两页等高、都等于内容较多的那一页。
 */
@Composable
internal fun JwxtLessonExamCard(
    lessons: List<JwxtLessonUi>,
    exams: List<JwxtExamUi>,
    lessonError: String?,
    examError: String?,
) {
    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                divider = {},
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(text = stringResource(R.string.jwxt_tab_today_lesson)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(text = stringResource(R.string.jwxt_tab_term_exam)) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                // 两个 tab 的内容都叠放在每一页里，只有当前 tab 可见：这样每一页的高度都等于
                // 两者中的较大者，切换 tab 时卡片高度不变、页面不会突然上移。
                // （HorizontalPager 是懒加载容器，不支持 IntrinsicSize.Max，无法用修饰符直接取最大高度。）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(modifier = if (page == 0) Modifier else HiddenTabContent) {
                        LessonTabContent(lessons = lessons, error = lessonError)
                    }
                    Box(modifier = if (page == 1) Modifier else HiddenTabContent) {
                        ExamTabContent(exams = exams, error = examError)
                    }
                }
            }
        }
    }
}

/** 今日课程页：失败 → 错误文案；空 → 空状态；否则按节次逐条渲染（ViewModel 已排序） */
@Composable
private fun LessonTabContent(lessons: List<JwxtLessonUi>, error: String?) {
    // 自带 Column：外层是"两个 tab 叠放取最大高度"的 Box，而 Box 会把子项叠在同一位置，
    // 所以垂直排列必须由内容自己负责
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when {
            error != null -> TabMessage(error)
            lessons.isEmpty() -> TabMessage(stringResource(R.string.jwxt_lesson_empty))
            else -> lessons.forEachIndexed { index, lesson ->
                if (index > 0) ItemDivider()
                JwxtLessonItem(lesson)
            }
        }
    }
}

/** 本学期考试页：失败 → 错误文案；空 → 空状态；否则逐条渲染（ViewModel 已按考试时间升序） */
@Composable
private fun ExamTabContent(exams: List<JwxtExamUi>, error: String?) {
    // 同 LessonTabContent：垂直排列由内容自己负责，外层 Box 只做叠放定高
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when {
            error != null -> TabMessage(error)
            exams.isEmpty() -> TabMessage(stringResource(R.string.jwxt_exam_empty))
            else -> exams.forEachIndexed { index, exam ->
                if (index > 0) ItemDivider()
                JwxtExamItem(exam)
            }
        }
    }
}

/** 条目之间的分隔线（两端各留 8dp，避免贴着文字） */
@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 卡片内提示（空状态 / 加载失败）；固定 [TAB_CONTENT_HEIGHT] 高度，切 tab 时卡片高度不跳动 */
@Composable
private fun TabMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TAB_CONTENT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 一条课程：标题行 =「节次 节（时间）」+ 调/补标签；正文 = 接口 `cellDetail` 逐行。
 *
 * 正文行数不固定（实测 3–4 行，其中一行是全部上课班级、可能很长）。**不做行数截断**：
 * 网页原版正文只有 `word-break: break-all`，课表信息要完整展示。
 */
@Composable
private fun JwxtLessonItem(lesson: JwxtLessonUi) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.jwxt_lesson_section_time, lesson.sectionRange, lesson.timeRange),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TransferTag(lesson.transferTag)
        }

        lesson.lines.forEach { line ->
            Text(
                text = line.text,
                // 字号对齐网页原版 `.descripText`（14px）
                style = MaterialTheme.typography.bodyMedium,
                // 网页把「教师 时间 地点」那行标成红字；这里用主题 error 色，深色模式下同样可读
                color = if (line.highlight) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 调/补标签：仅调课（01）与补课（03）显示，正常课不显示（与网页端一致） */
@Composable
private fun TransferTag(code: String) {
    val label = when (code) {
        JwxtConstants.TRANSFER_TYPE_ADJUST -> stringResource(R.string.jwxt_lesson_tag_adjust)
        JwxtConstants.TRANSFER_TYPE_MAKEUP -> stringResource(R.string.jwxt_lesson_tag_makeup)
        else -> return
    }

    Text(
        text = label,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

/**
 * 一条考试：时间（接口 `timeNote` 已含完整日期与场次）→ 课程名 →「地点 · 座位 · 教师」。
 *
 * 地点/座位/教师可能缺项，缺的那段不显示。
 */
@Composable
private fun JwxtExamItem(exam: JwxtExamUi) {
    val place = if (exam.place.isNotBlank()) stringResource(R.string.jwxt_exam_place, exam.place) else null
    val seat = if (exam.seat.isNotBlank()) stringResource(R.string.jwxt_exam_seat, exam.seat) else null
    val teacher = if (exam.teacher.isNotBlank()) stringResource(R.string.jwxt_exam_teacher, exam.teacher) else null
    val details = listOfNotNull(place, seat, teacher)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = exam.timeNote,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = exam.courseName,
            // 字号对齐网页原版 `.descripTitle`（16px）
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                // 字号对齐网页原版 `.descripText`（14px）；不截断，地点/座位/教师完整展示
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val TAB_COUNT = 2
private val TAB_CONTENT_HEIGHT = 120.dp

/** 非当前 tab 的内容：透明、保留占位（让两个 tab 的高度取较大者），并清空语义避免被读屏读到 */
private val HiddenTabContent = Modifier
    .alpha(0f)
    .clearAndSetSemantics {}
