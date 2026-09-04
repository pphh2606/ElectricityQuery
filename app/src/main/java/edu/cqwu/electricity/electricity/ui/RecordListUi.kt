package edu.cqwu.electricity.electricity.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.ChartDataV2
import edu.cqwu.electricity.common.ui.LineChartV2
import edu.cqwu.electricity.common.ui.chartDataHasPointsV2
import edu.cqwu.electricity.theme.ui.UiMessage

/**
 * 把仓库失败结果转为界面消息：空文案 → 本地资源通用"查询失败"；
 * 有文案（服务器 resultMsg / 网络层文本）→ 原样展示（不翻译）。
 */
internal fun toRecordErrorUiMessage(message: String?): UiMessage =
    if (message.isNullOrBlank()) UiMessage(res = R.string.record_query_failed)
    else UiMessage(raw = message)

/**
 * 记录表格列定义。
 *
 * @param title 表头文案
 * @param weight 列宽权重
 * @param alignEnd 表头与单元格是否右对齐
 */
data class TableColumn(
    val title: String,
    val weight: Float,
    val alignEnd: Boolean = false,
)

/**
 * 通用记录数据表格：表头 + 数据行 + 可选底部总计。
 *
 * 与数据模型解耦：调用方把每条记录映射为各列显示文本（含格式化），
 * 表格组件只负责按 [columns] 渲染排版。
 *
 * @param columns 列定义
 * @param rows 每行 → 各列显示文本
 * @param footer 底部总计行文本；null 时不显示总计
 */
@Composable
fun RecordDataTable(
    columns: List<TableColumn>,
    rows: List<List<String>>,
    footer: List<String>? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEach { column ->
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.weight(column.weight)
                )
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEachIndexed { index, column ->
                        Text(
                            text = row.getOrElse(index) { "" },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.weight(column.weight)
                        )
                    }
                }
                HorizontalDivider()
            }
        }

        // 底部总计
        if (footer != null) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEachIndexed { index, column ->
                    Text(
                        text = footer.getOrElse(index) { "" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.weight(column.weight)
                    )
                }
            }
        }
    }
}

/**
 * 单个 Tab（或补助页单页）的表格内容描述。
 *
 * 让外壳能按页渲染各自数据（用量报表三个 Tab 各持缓存）：
 * - [rows]/[footer]：该页格式化后的表格数据与总计行
 * - [error]：该页错误文案（有数据时调用方通常传 null，保留缓存展示）
 * - [emptyText]：该页查询完成但无数据时的空态文案
 * - [isLoading]：该页"查询中且无数据"（表格区留白，仅顶部刷新指示提示）
 */
data class RecordTablePageV2(
    val rows: List<List<String>> = emptyList(),
    val footer: List<String>? = null,
    val error: String? = null,
    val emptyText: String = "",
    val isLoading: Boolean = false,
)

/**
 * 通用记录结果区：错误态 / 加载留白 / 数据表格（含底部总计）/ 空态 切换。
 *
 * 供记录页外壳在"无 Tab（补助记录）或有 Tab 分页（用量报表）"两种布局中复用：
 * - 出错时显示错误文案
 * - 查询中（[RecordTablePageV2.isLoading]）且无数据时**留白**
 * - 有数据时显示表格，查询完成但无数据显示空态
 *
 * @param columns 表格列定义
 * @param page 该页内容（行/总计/错误/空态/加载留白）
 */
@Composable
fun RecordTableArea(
    columns: List<TableColumn>,
    page: RecordTablePageV2,
) {
    val error = page.error
    when {
        error != null -> {
            RecordCenteredText(error, MaterialTheme.colorScheme.error)
        }

        page.isLoading -> {
            // 查询中且无数据：留白，避免闪"暂无数据"；进度由顶部下拉刷新指示提示
            Box(modifier = Modifier.fillMaxSize())
        }

        page.rows.isNotEmpty() -> {
            RecordDataTable(columns = columns, rows = page.rows, footer = page.footer)
        }

        else -> {
            RecordCenteredText(page.emptyText, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 通用记录结果区共享：居中的错误/空态文本。
 */
@Composable
private fun RecordCenteredText(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}

/**
 * 记录页视图模式 v2：表格 / 折线图。
 */
enum class RecordViewModeV2 { TABLE, CHART }

/**
 * 记录页"折线图视图"的内容描述。
 *
 * 折线图展示的是与表格同一份筛选数据的另一种画法，状态语义一致：
 * - [error] 非空显示错误
 * - [isLoading] 为 true 且无数据时留白（顶部刷新指示提示进度）
 * - [chartData] 为空或无可画点时显示 [emptyText]
 * - 否则渲染 [LineChartV2]
 */
data class RecordChartContentV2(
    val chartData: ChartDataV2? = null,
    val error: String? = null,
    val emptyText: String = "",
    val isLoading: Boolean = false,
)

/**
 * 折线图视图内容区（与表格共享错误/加载留白/空态语义）。
 */
@Composable
fun RecordChartArea(
    content: RecordChartContentV2,
    modifier: Modifier = Modifier,
) {
    val error = content.error
    when {
        error != null -> {
            RecordCenteredText(error, MaterialTheme.colorScheme.error)
        }

        content.isLoading -> {
            Box(modifier = Modifier.fillMaxSize())
        }

        content.chartData == null || !chartDataHasPointsV2(content.chartData) -> {
            RecordCenteredText(content.emptyText, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        else -> {
            LineChartV2(data = content.chartData, modifier = modifier)
        }
    }
}
