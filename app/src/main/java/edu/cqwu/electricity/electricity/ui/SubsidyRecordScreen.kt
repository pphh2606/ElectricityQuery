package edu.cqwu.electricity.electricity.ui

import android.content.res.Resources
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
import edu.cqwu.electricity.electricity.data.SubsidyRecord
import edu.cqwu.electricity.theme.ui.resolve
import java.util.Locale

/**
 * 补助记录页面。
 *
 * 复用 [RecordListScreen] 通用模板：
 * - 日期筛选：起止日期（由外壳统一渲染，与表格组成整体）
 * - 表格：时间/补助类型/补助量/金额（4列）+ 底部总计
 * - 内容可在表格与折线图之间切换（标题栏按钮）；折线按发放时间逐笔连线（与表格同口径）
 *
 * 补助接口不区分能源类型与粒度，与 [SubsidyRecordViewModel] 绑定。
 */
@Composable
fun SubsidyRecordScreen(
    viewModel: SubsidyRecordViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val resources = LocalResources.current

    val columns = listOf(
        TableColumn(
            title = stringResource(R.string.subsidy_record_header_time),
            weight = 2f,
        ),
        TableColumn(
            title = stringResource(R.string.subsidy_record_header_type),
            weight = 1f,
        ),
        TableColumn(
            title = stringResource(R.string.subsidy_record_header_quantity),
            weight = 1f,
            alignEnd = true,
        ),
        TableColumn(
            title = stringResource(R.string.subsidy_record_header_cost),
            weight = 1f,
            alignEnd = true,
        ),
    )

    val rows = state.records.map { record ->
        listOf(
            record.subsidyTime,
            feeTypeName(record.feeType, resources),
            String.format(Locale.US, "%.2f", record.quantity),
            String.format(Locale.US, "%.2f", record.amount),
        )
    }

    val footer = listOf(
        stringResource(R.string.subsidy_record_total),
        "",
        String.format(Locale.US, "%.2f", state.totalQuantity),
        String.format(Locale.US, "%.2f", state.totalAmount),
    )

    val noDataText = stringResource(R.string.subsidy_record_no_data)
    val quantityLabel = stringResource(R.string.subsidy_record_header_quantity)
    val costLabel = stringResource(R.string.subsidy_record_header_cost)

    // 补助页为单页内容（无 Tab）；查询中且无数据时表格区留白，进度由顶部刷新指示提示
    val page = RecordTablePageV2(
        rows = rows,
        footer = footer,
        error = state.error?.resolve(resources),
        emptyText = noDataText,
        isLoading = state.isRefreshing,
    )

    // 折线图视图：当前补助记录 → 图表数据（按发放时间逐笔，量 + 金额两条线，与表格同口径）
    val chartData = buildSubsidyChartDataV2(
        records = state.records,
        quantityLabel = quantityLabel,
        amountLabel = costLabel,
        quantityColor = MaterialTheme.colorScheme.primary,
        amountColor = MaterialTheme.colorScheme.tertiary,
    )
    val chartPages = listOf(
        RecordChartContentV2(
            chartData = if (state.records.isNotEmpty()) chartData else null,
            error = state.error?.resolve(resources),
            emptyText = noDataText,
            isLoading = state.isRefreshing,
        ),
    )

    // 导出/复制文本的预翻译文案（拼装逻辑在 RecordTextContentV2，可单测）
    val textLabels = SubsidyReportTextLabelsV2(
        title = stringResource(R.string.subsidy_record_export_title),
        filterDescription = stringResource(R.string.subsidy_record_filter_desc, state.beginTime, state.endTime),
        headerTime = stringResource(R.string.subsidy_record_header_time),
        headerType = stringResource(R.string.subsidy_record_header_type),
        headerQuantity = quantityLabel,
        headerCost = costLabel,
        total = stringResource(R.string.subsidy_record_total),
        noData = noDataText,
    )

    RecordListScreen(
        title = stringResource(R.string.subsidy_record_title),
        onBack = onBack,
        isRefreshing = state.isRefreshing,
        columns = columns,
        pages = listOf(page),
        chartPages = chartPages,
        chartMode = state.viewMode == RecordViewModeV2.CHART,
        onToggleViewMode = { viewModel.toggleViewMode() },
        beginLabel = stringResource(R.string.subsidy_record_begin_time),
        endLabel = stringResource(R.string.subsidy_record_end_time),
        beginValue = state.beginTime,
        endValue = state.endTime,
        onBeginChange = { viewModel.setBeginTime(it) },
        onEndChange = { viewModel.setEndTime(it) },
        onRefresh = { viewModel.refresh() },
        textContent = {
            buildSubsidyReportTextV2(state, textLabels) { feeTypeName(it, resources) }
        },
        exportTitle = stringResource(R.string.subsidy_record_export_title),
        exportFileName = "electricity_subsidy_record.txt",
        onDispose = { viewModel.clearState() },
    )
}

/**
 * 补助类型映射：0/20=电费, 1=水费, 2=气费, 3=热费，未知显示"—"。
 */
private fun feeTypeName(feeType: Int, resources: Resources): String {
    return when (feeType) {
        0, 20 -> resources.getString(R.string.subsidy_type_electric)
        1 -> resources.getString(R.string.subsidy_type_water)
        2 -> resources.getString(R.string.subsidy_type_gas)
        3 -> resources.getString(R.string.subsidy_type_heat)
        else -> resources.getString(R.string.subsidy_type_unknown)
    }
}

/**
 * 将补助记录投影为折线图数据 v2（纯函数，可单测）。
 *
 * X 轴为每笔补助的发放日期（到日），逐笔等距连线。
 * 量/金额两条线与表格同口径（补助类型混合也照画）。
 *
 * @param records 补助记录（可为空）
 * @param quantityLabel 图例"补助量"文案
 * @param amountLabel 图例"金额"文案
 * @param quantityColor 补助量线颜色
 * @param amountColor 金额线颜色
 */
fun buildSubsidyChartDataV2(
    records: List<SubsidyRecord>,
    quantityLabel: String,
    amountLabel: String,
    quantityColor: Color,
    amountColor: Color,
): ChartDataV2 {
    if (records.isEmpty()) return ChartDataV2(emptyList(), emptyList())
    return ChartDataV2(
        xLabels = records.map { subsidyTimeLabelV2(it.subsidyTime) },
        series = listOf(
            ChartSeriesV2(label = quantityLabel, values = records.map { it.quantity }, color = quantityColor),
            ChartSeriesV2(label = amountLabel, values = records.map { it.amount }, color = amountColor),
        ),
    )
}

/**
 * 补助发放时间 → X 轴标签（保留到日 yyyy-MM-dd）。
 */
fun subsidyTimeLabelV2(time: String): String =
    if (time.length >= 10) time.substring(0, 10) else time
