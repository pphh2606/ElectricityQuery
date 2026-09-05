package edu.cqwu.electricity.common.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 单条折线数据 v2。
 *
 * @param label 图例名称，如 "用电量(度)"、"金额(元)"
 * @param values 数据点数值，与 X 轴标签一一对应（支持空/单点/全同值）
 * @param color 线条与图例颜色
 */
data class ChartSeriesV2(
    val label: String,
    val values: List<Double>,
    val color: Color,
)

/**
 * 折线统计图数据 v2。
 *
 * @param xLabels X 轴标签（与每条线的 values 长度一致）
 * @param series 一条或多条线
 */
data class ChartDataV2(
    val xLabels: List<String>,
    val series: List<ChartSeriesV2>,
)

/** 判断数据是否足以画图（至少一条线且该线有点） */
fun chartDataHasPointsV2(data: ChartDataV2): Boolean =
    data.xLabels.isNotEmpty() && data.series.any { it.values.isNotEmpty() }

/** 画布内边距（dp），绘制与手势共用同一套值保证坐标一致 */
private val ChartPadX = 8.dp
private val ChartPadTop = 8.dp
private val ChartPadBottom = 26.dp

/**
 * 通用折线统计图 v2（纯 Compose Canvas 自绘，无第三方依赖）。
 *
 * - 支持多条线（颜色/图例自定）；各线按自身范围归一化后同图比较趋势（图例注明单位）
 * - Y 轴自动缩放；全平线（min==max）画在中间；数据点少时画圆点辅助读数
 * - **X 轴只显示最左、最右两个标签**（单点只显示一个），避免重合
 * - **按压拖动显示数据详情（股票式）**：手指按住后出现竖参考线与高亮点，
 *   顶部图例行实时追加各线数值、x 轴日期靠右显示，拖动跟踪最近点，松手消失
 * - 网格/文字颜色取自主题，自动适配深浅色
 *
 * @param data 图表数据（调用方保证有可画点，见 [chartDataHasPointsV2]）
 * @param showGestureDetails 是否启用按压查看详情（默认开启）
 * @param modifier 修饰符（内部为单行图例/提示 + 画布，纵向分配）
 */
@Composable
fun LineChartV2(
    data: ChartDataV2,
    modifier: Modifier = Modifier,
    showGestureDetails: Boolean = true,
) {
    val n = data.xLabels.size
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurface.copy(alpha = 0.14f)
    val labelColor = onSurface.copy(alpha = 0.62f)

    // 当前按压位置对应的数据点下标（null = 未按压）
    var hoverIndex by remember(data.xLabels) { mutableStateOf<Int?>(null) }

    val gestureModifier = if (showGestureDetails) {
        Modifier.pointerInput(n) {
            val padX = ChartPadX.toPx() // PointerInputScope 本身是 Density
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                hoverIndex = indexAtXInternal(down.position.x, size.width.toFloat(), n, padX)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.pressed }
                        ?: break
                    hoverIndex = indexAtXInternal(change.position.x, size.width.toFloat(), n, padX)
                    if (change.changedToUp()) break
                }
                hoverIndex = null
            }
        }
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxSize()) {
        val hovered = hoverIndex?.takeIf { it in data.xLabels.indices }

        // ── 图例 / 按压提示单行 ──
        // 平时仅显示图例；按压后图例追加当前数值、右侧显示当前 x 轴日期（行高恒定不跳动）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            data.series.forEachIndexed { index, line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = line.color,
                    ) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    val valueText = hovered?.let { line.values.getOrNull(it) }
                    Text(
                        text = if (valueText != null) "${line.label} ${formatValue(valueText)}" else line.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (index < data.series.lastIndex) {
                    Spacer(modifier = Modifier.width(20.dp))
                }
            }

            // 弹性空隙：把按压时的时间挤到行右侧
            Spacer(modifier = Modifier.weight(1f))

            if (hovered != null) {
                Text(
                    text = data.xLabels[hovered],
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    maxLines = 1,
                )
            }
        }

        // ── 画布（含按压十字线/高亮点），占图例行之外的剩余高度 ──
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(gestureModifier)
        ) {
            val labelTextSize = 11.dp.toPx()
            val padLeft = ChartPadX.toPx()
            val padRight = ChartPadX.toPx()
            val padTop = ChartPadTop.toPx()
            val padBottom = ChartPadBottom.toPx()
            val drawW = size.width - padLeft - padRight
            val drawH = size.height - padTop - padBottom

            fun xAt(index: Int): Float =
                if (n <= 1) padLeft + drawW / 2f
                else padLeft + index * drawW / (n - 1)

            fun yNorm(value: Double, seriesValues: List<Double>): Float {
                val maxV = seriesValues.maxOrNull() ?: 0.0
                val minV = seriesValues.minOrNull() ?: 0.0
                val range = maxV - minV
                val norm = if (range <= 0.0) 0.5 else ((value - minV) / range).coerceIn(0.0, 1.0)
                return padTop + (1f - norm.toFloat()) * drawH
            }

            // 背景网格：底部、中间、顶部三条水平线
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = padTop + fraction * drawH
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, y),
                    end = Offset(size.width - padRight, y),
                    strokeWidth = 1f,
                )
            }

            // 各条线
            data.series.forEach { line ->
                if (line.values.isEmpty()) return@forEach
                val path = Path()
                line.values.forEachIndexed { index, value ->
                    val x = xAt(index)
                    val y = yNorm(value, line.values)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = line.color,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // 数据点少时画圆点帮助读数
                if (n <= 24) {
                    line.values.forEachIndexed { index, value ->
                        drawCircle(
                            color = line.color,
                            radius = 3f,
                            center = Offset(xAt(index), yNorm(value, line.values)),
                        )
                    }
                }
            }

            // 按压指示：竖参考线 + 各线高亮点
            if (hovered != null) {
                val lineX = xAt(hovered)
                drawLine(
                    color = onSurface.copy(alpha = 0.45f),
                    start = Offset(lineX, padTop),
                    end = Offset(lineX, size.height - padBottom),
                    strokeWidth = 1.5f,
                )
                data.series.forEach { line ->
                    val value = line.values.getOrNull(hovered) ?: return@forEach
                    drawCircle(
                        color = line.color,
                        radius = 5f,
                        center = Offset(lineX, yNorm(value, line.values)),
                    )
                }
            }

            // X 轴：只显示最左、最右两个标签（单点只画一个）
            val paint = Paint().apply {
                color = labelColor.toArgb()
                textSize = labelTextSize
            }
            val tickIndices = if (n > 1) listOf(0, n - 1) else listOf(0)
            for (index in tickIndices) {
                val text = data.xLabels.getOrNull(index) ?: continue
                val textWidth = paint.measureText(text)
                if (textWidth >= drawW) continue
                val textX = (xAt(index) - textWidth / 2f)
                    .coerceIn(padLeft, padLeft + drawW - textWidth)
                val textBaseline = size.height - (padBottom - labelTextSize) / 2f
                drawLabelText(text, textX, textBaseline, paint)
            }
        }
    }
}

/** 画布内边距/常量与手势共用：把 X 像素坐标换算为最近的数据点下标 */
private fun indexAtXInternal(x: Float, widthPx: Float, pointCount: Int, padX: Float): Int {
    val drawW = widthPx - padX * 2f
    if (drawW <= 0f) return 0
    val step = drawW / (pointCount - 1)
    return ((x - padX) / step).roundToInt().coerceIn(0, pointCount - 1)
}

/** 数值显示：保留两位小数 */
internal fun formatValue(value: Double): String =
    String.format(Locale.US, "%.2f", value)

/** 在 DrawScope 中绘制一行小号文本（复用 nativeCanvas，避免 TextMeasurer 依赖） */
private fun DrawScope.drawLabelText(
    text: String,
    x: Float,
    baselineY: Float,
    paint: Paint,
) {
    drawContext.canvas.nativeCanvas.drawText(text, x, baselineY, paint)
}
