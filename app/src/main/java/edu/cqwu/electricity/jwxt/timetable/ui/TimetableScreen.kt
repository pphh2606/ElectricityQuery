@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTarget
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 周课表（本地化 jwfw `/jmobile` 的 index 应用 `home/timetable/newTimetable` 页）。
 *
 * 入口：App 首页「我的课表」格子。**只读**：不做拖拽改课、不做本地建课；
 * 「周 / 学期」两种视图——周视图一次取一周，学期视图一次取整学期
 * （`serialNumber` 交给服务端过滤，本地不解析「1-3周,5-9周」这类字符串）。
 *
 * 布局是「行 = 节次、列 = 星期」的网格，骨架参考开源项目拾光课程表（Apache-2.0），
 * 但不使用它的自定义拖拽 Layout——课程位置固定，按 `(节次-1) × 每节高度` 定位即可。
 *
 * 本文件只放**页面**：顶栏、空态、下拉刷新、周次/学期切换与两个弹窗。
 * 网格与课程卡片在 `TimetableGrid.kt`；撞课分轨与行高在 `timetable/domain/`（有单测覆盖）。
 */
@Composable
fun JwxtTimetableScreen(
    onBack: () -> Unit,
    /** 要查的课表目标；null 表示「我的课表」（现有入口不传即可，行为与改造前一致） */
    target: JwxtScheduleTarget? = null,
    viewModel: JwxtTimetableViewModel = viewModel(factory = JwxtTimetableViewModel.Factory(target)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val nav = LocalNavController.current
    // 查课表时标题就是对象名（班级 / 教师 / 教室名）；「我的课表」用默认标题
    val title = target?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.jwxt_timetable_title)
    // 「网页版」的去向：查别人的课表回「全校课表查询」首页，我的课表仍打开课表网页
    val webUrl = if (target == null) JwxtConstants.TIMETABLE_URL else JwxtConstants.SCHEDULE_QUERY_URL

    // 详情弹窗：内容与显隐分成两个状态——关闭动画播放期间内容还要在，不能跟着一起清空
    var detailCourse by remember { mutableStateOf<JwxtTimetableCourse?>(null) }
    var detailVisible by remember { mutableStateOf(false) }

    // 当前打开的「周次 / 学期」选择弹窗；null 表示没打开
    var picker by remember { mutableStateOf<TimetableMode?>(null) }

    // 从登录页返回时自动重试一次（仅在确实因会话过期失败时），与教务首页、成绩页同一套处理
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && viewModel.uiState.value.requiresReLogin) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // 打开网页版：保留一条回到教务原网页的退路（与其它教务页面顶栏一致）
                    IconButton(onClick = { nav.navigate(Routes.unifiedWebViewRoute(webUrl, title)) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = stringResource(R.string.common_web_version),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        // 顶部是「学期 / 周次」切换，下面是内容区——两者分离，任何内容态都不会把切换行一起挡住：
        // 会话过期、或某个学期加载失败（教务「该校历未维护」）时，用户都还能切回别的学期
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            NavigatorRow(
                state = uiState,
                onSelectWeek = viewModel::selectWeek,
                onSelectTerm = viewModel::selectTerm,
                onPick = { picker = uiState.mode },
                onModeChange = viewModel::selectMode,
            )

            PullToRefreshBox(
                // 首次加载也算「正在刷新」，让顶部下拉指示器一进页面就出现（与教务首页、成绩页一致）
                isRefreshing = uiState.isLoading || uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    // 会话过期：内容区显示「重新登录」，顶部的学期 / 周次切换保留
                    uiState.requiresReLogin -> ReLoginContent(
                        requiresReLogin = true,
                        onReLogin = { nav.navigate(Routes.loginRoute()) },
                    )

                    // 某个学期加载失败（如教务回「该校历未维护」）：只在**内容区**提示，
                    // 上面的学期 / 周次切换必须留着——否则用户被全屏错误页挡住，切不回别的学期
                    uiState.errorMessage != null -> ReLoginContent(
                        errorMessage = uiState.errorMessage,
                        requiresReLogin = false,
                        onReLogin = {},
                        // 重试「当前选中的学期」；load() 会重新落到默认学期，等于把用户的选择丢掉
                        onRetry = { viewModel.refresh() },
                    )

                    // 数据还没到：留白（顶部指示器在转），此时不能说「本周暂无课程」
                    uiState.courses.isEmpty() && uiState.isLoading ->
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {}

                    // 没课；或接口没给节次（没有节次就无法定位行，画出格子必然错位）
                    uiState.courses.isEmpty() || uiState.sections.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.jwxt_timetable_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> TimetableGrid(
                        sections = uiState.sections,
                        courses = uiState.courses,
                        onCourseClick = {
                            detailCourse = it
                            detailVisible = true
                        },
                    )
                }
            }
        }
    }

    BottomSheetDialogV2(
        visible = detailVisible,
        onDismissRequest = { detailVisible = false },
        title = detailCourse?.courseName.orEmpty(),
    ) {
        // detailCourse 在关闭后仍保留，弹窗的退出动画里不会变成空壳
        detailCourse?.let { CourseDetailContent(it) }
    }

    // 周次 / 学期选择弹窗：同一个弹窗，按当前模式列不同的项
    picker?.let { mode ->
        BottomSheetDialogV2(
            visible = true,
            onDismissRequest = { picker = null },
            title = stringResource(
                if (mode == TimetableMode.WEEK) R.string.jwxt_timetable_pick_week
                else R.string.jwxt_timetable_pick_term
            ),
        ) {
            if (mode == TimetableMode.WEEK) {
                uiState.weeks.forEach { week ->
                    BottomSheetItem(
                        icon = null,
                        title = stringResource(R.string.jwxt_timetable_week, week.serialNumber),
                        selected = week.serialNumber == uiState.selectedWeek,
                        onClick = {
                            viewModel.selectWeek(week.serialNumber)
                            picker = null
                        },
                    )
                }
            } else {
                uiState.terms.forEach { term ->
                    BottomSheetItem(
                        icon = null,
                        title = term.termName,
                        selected = term.termCode == uiState.selectedTermCode,
                        onClick = {
                            viewModel.selectTerm(term.termCode)
                            picker = null
                        },
                    )
                }
            }
        }
    }
}

/**
 * 导航行：一行里同时管住「学期」和「周次」。
 *
 * - `‹` / `›` 按当前模式切上一/下一个（周视图切周、学期视图切学期），到两端自动置灰；
 * - 中间是可点的当前范围，点开弹出选择列表；
 * - 右边是「周 / 学期」模式切换。
 */
@Composable
private fun NavigatorRow(
    state: TimetableUiState,
    onSelectWeek: (Int) -> Unit,
    onSelectTerm: (String) -> Unit,
    onPick: () -> Unit,
    onModeChange: (TimetableMode) -> Unit,
) {
    val isWeek = state.mode == TimetableMode.WEEK
    val prev = if (isWeek) state.prevWeek else state.olderTermCode
    val next = if (isWeek) state.nextWeek else state.newerTermCode
    val label = when {
        // 周次还没取到时不要显示「第 0 周」
        isWeek && state.weeks.isEmpty() -> ""
        isWeek -> stringResource(R.string.jwxt_timetable_week, state.selectedWeek)
        else -> state.currentTerm?.termName.orEmpty()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                if (isWeek) state.prevWeek?.let(onSelectWeek) else state.olderTermCode?.let(onSelectTerm)
            },
            enabled = prev != null,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.jwxt_timetable_prev_week),
            )
        }

        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(
            onClick = {
                if (isWeek) state.nextWeek?.let(onSelectWeek) else state.newerTermCode?.let(onSelectTerm)
            },
            enabled = next != null,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.jwxt_timetable_next_week),
            )
        }

        // 「周 / 学期」模式切换
        SingleChoiceSegmentedButtonRow {
            TimetableMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TimetableMode.entries.size),
                ) {
                    Text(
                        text = stringResource(
                            if (mode == TimetableMode.WEEK) R.string.jwxt_timetable_mode_week
                            else R.string.jwxt_timetable_mode_term
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 详情弹窗内容：用服务端排好版的 `titleDetail`（比卡片正文更完整）逐行显示。
 *
 * 正文里的 `<a data-kblx=…>教师</a>` 这类标签由 [HtmlFormParser.stripHtml] 去掉（Compose 不解析 HTML）；
 * 接口给空列表时弹窗只剩标题，不再多占一句「暂无课程」的文案。
 */
@Composable
private fun CourseDetailContent(course: JwxtTimetableCourse) {
    course.titleDetail.orEmpty()
        .map(HtmlFormParser::stripHtml)
        .filter { it.isNotBlank() }
        .forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
}
