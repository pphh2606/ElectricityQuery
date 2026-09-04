package edu.cqwu.electricity.electricity.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.ChartDataV2
import edu.cqwu.electricity.common.ui.ChartSeriesV2
import edu.cqwu.electricity.electricity.data.UsageGranularityV2
import edu.cqwu.electricity.electricity.data.UsageRecordV2
import edu.cqwu.electricity.theme.ui.resolve
import java.util.Locale

/**
 * 用量报表（用电明细）页面 V2。
 *
 * 复用 [RecordListScreen] 通用模板：顶部 Tab（小时/每日/每月）+ 起止日期筛选，
 * 内容可在表格与折线图之间切换（标题栏按钮），折线图跟随当前粒度缓存数据。
 *
 * 固定查询电费（costType=0），与 [UsageRecordViewModelV2] 绑定。
 */
@Composable
fun UsageRecordScreenV2(
    viewModel: UsageRecordViewModelV2,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val resources = LocalResources.current

    // 顶部 Tab 文案：顺序与 UsageGranularityV2.entries 一致（小时/每日/每月）
    val dataTypeOptions = listOf(
        stringResource(R.string.usage_record_v2_hourly),
        stringResource(R.string.usage_record_v2_daily),
        stringResource(R.string.usage_record_v2_monthly),
    )

    val columns = listOf(
        TableColumn(
            title = stringResource(R.string.usage_record_v2_header_time),
            weight = 2f,
        ),
        TableColumn(
            title = stringResource(R.string.usage_record_v2_header_usage),
            weight = 1f,
            alignEnd = true,
        ),
        TableColumn(
            title = stringResource(R.string.usage_record_v2_header_cost),
            weight = 1f,
            alignEnd = true,
        ),
    )

    val noDataText = stringResource(R.string.usage_record_v2_no_data)
    val totalLabel = stringResource(R.string.usage_record_v2_total)
    val usageLabel = stringResource(R.string.usage_record_v2_header_usage)
    val costLabel = stringResource(R.string.usage_record_v2_header_cost)

    // 每个粒度（Tab）同时产出表格页与折线页：共用同一套错误/加载判定，避免重复计算
    val usageColor = MaterialTheme.colorScheme.primary
    val costColor = MaterialTheme.colorScheme.tertiary
    val pageViews = UsageGranularityV2.entries.map { granularity ->
        val tab = state.tabs[granularity] ?: UsageTabContentV2()
        val hasErrorWithoutData = tab.error != null && tab.records.isEmpty()
        val isLoading = tab.records.isEmpty() && tab.error == null && state.isRefreshing
        val errorText = if (hasErrorWithoutData) tab.error?.resolve(resources) else null
        val chartData = buildUsageChartDataV2(
            records = tab.records,
            granularity = granularity,
            usageLabel = usageLabel,
            costLabel = costLabel,
            usageColor = usageColor,
            costColor = costColor,
        )
        UsagePageViewsV2(
            table = RecordTablePageV2(
                rows = tab.records.map { record ->
                    listOf(
                        record.costTime,
                        String.format(Locale.US, "%.2f", record.consumeTotal),
                        String.format(Locale.US, "%.2f", record.costTotal),
                    )
                },
                footer = listOf(
                    totalLabel,
                    String.format(Locale.US, "%.2f", tab.totalConsume),
                    String.format(Locale.US, "%.2f", tab.totalCost),
                ),
                // 有缓存数据时忽略本次错误，继续展示缓存
                error = errorText,
                emptyText = noDataText,
                // 查询中且该页无数据：留白（顶部刷新指示已提示进度）
                isLoading = isLoading,
            ),
            chart = RecordChartContentV2(
                chartData = if (tab.records.isNotEmpty()) chartData else null,
                error = errorText,
                emptyText = noDataText,
                isLoading = isLoading,
            ),
        )
    }
    val pages = pageViews.map { it.table }
    val chartPages = pageViews.map { it.chart }

    // 导出/复制文本的预翻译文案（拼装逻辑在 RecordTextContentV2，可单测）
    val textLabels = UsageReportTextLabelsV2(
        title = stringResource(R.string.usage_record_v2_export_title),
        filterDescription = stringResource(
            R.string.usage_record_v2_filter_desc,
            state.beginTime,
            state.endTime,
            dataTypeOptions[state.granularity.ordinal],
        ),
        headerTime = stringResource(R.string.usage_record_v2_header_time),
        headerUsage = usageLabel,
        headerCost = costLabel,
        total = totalLabel,
        noData = noDataText,
    )

    RecordListScreen(
        title = stringResource(R.string.usage_record_v2_title),
        onBack = onBack,
        isRefreshing = state.isRefreshing,
        columns = columns,
        pages = pages,
        chartPages = chartPages,
        chartMode = state.viewMode == RecordViewModeV2.CHART,
        onToggleViewMode = { viewModel.toggleViewMode() },
        beginLabel = stringResource(R.string.usage_record_v2_begin_time),
        endLabel = stringResource(R.string.usage_record_v2_end_time),
        beginValue = state.beginTime,
        endValue = state.endTime,
        onBeginChange = { viewModel.setBeginTime(it) },
        onEndChange = { viewModel.setEndTime(it) },
        tabs = dataTypeOptions,
        initialTabIndex = state.granularity.ordinal,
        onTabSelected = { viewModel.selectGranularity(UsageGranularityV2.fromTabIndex(it)) },
        onRefresh = { viewModel.refresh() },
        textContent = { buildUsageReportTextV2(state, textLabels) },
        exportTitle = stringResource(R.string.usage_record_v2_export_title),
        exportFileName = "electricity_usage_record.txt",
        onDispose = { viewModel.clearState() },
    )
}

/**
 * 将某一粒度的用量记录投影为折线图数据 v2（纯函数，可单测）。
 *
 * X 轴标签按粒度精简：小时→保留到分钟、每日→日期、每月→年月。
 *
 * @param records 该粒度的记录（可为空）
 * @param granularity 粒度（决定 X 轴标签格式）
 * @param usageLabel 图例"用量"文案（多语言由调用方传入）
 * @param costLabel 图例"金额"文案
 * @param usageColor 用量线颜色
 * @param costColor 金额线颜色
 */
fun buildUsageChartDataV2(
    records: List<UsageRecordV2>,
    granularity: UsageGranularityV2,
    usageLabel: String,
    costLabel: String,
    usageColor: Color,
    costColor: Color,
): ChartDataV2 {
    if (records.isEmpty()) return ChartDataV2(emptyList(), emptyList())
    return ChartDataV2(
        xLabels = records.map { usageTimeLabelV2(it.costTime, granularity) },
        series = listOf(
            ChartSeriesV2(label = usageLabel, values = records.map { it.consumeTotal }, color = usageColor),
            ChartSeriesV2(label = costLabel, values = records.map { it.costTotal }, color = costColor),
        ),
    )
}

/**
 * 用量时间 → X 轴标签（小时粒度到分、每日粒度到日、每月粒度到年月）。
 */
fun usageTimeLabelV2(time: String, granularity: UsageGranularityV2): String = when (granularity) {
    UsageGranularityV2.HOURLY -> if (time.length >= 16) time.substring(0, 16) else time
    UsageGranularityV2.DAILY -> if (time.length >= 10) time.substring(0, 10) else time
    UsageGranularityV2.MONTHLY -> if (time.length >= 7) time.substring(0, 7) else time
}

/**
 * 单个粒度的两种视图内容（表格 + 折线），一次计算避免重复的错误/加载判定。
 */
private data class UsagePageViewsV2(
    val table: RecordTablePageV2,
    val chart: RecordChartContentV2,
)
