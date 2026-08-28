package edu.cqwu.electricity.common.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 单条折线数据定义。
 */
data class LineData(
    /** 图例名称，如 "用电量(度)"、"费用(元)" */
    val label: String,
    /** 数据点数值列表，与 X 轴标签一一对应 */
    val values: List<Double>,
    /** 线条颜色 */
    val color: Color
)

/**
 * 用电数据折线图卡片。
 *
 * 使用 Compose Canvas 自绘折线统计图，支持 1~2 条数据线。
 * X 轴显示自定义标签（如时间/月份），Y 轴自动缩放。
 * 自动适配 Material 3 浅色/深色主题。
 *
 * @param xLabels X 轴标签列表（如 ["1月","2月",...] 或 ["08:00","09:00",...]）
 * @param lines 折线数据列表，支持 1~2 条线（如用电量 + 费用）
 * @param modifier 修饰符
 */
@Composable
fun ElectricityLineChartCard(
    xLabels: List<String>,
    lines: List<LineData>,
    modifier: Modifier = Modifier
) {
    if (lines.isEmpty() || lines.all { it.values.isEmpty() }) return

    // 1.5: 提前获取 density 和 surface 色，供 Canvas DrawScope 使用
    val density = LocalDensity.current.density
    val surfaceColor = MaterialTheme.colorScheme.surface

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── 图例行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = line.color
                        ) {}
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = line.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 折线图 Canvas ──
            val onSurface = MaterialTheme.colorScheme.onSurface
            val labelColor = onSurface.copy(alpha = 0.6f)
            val gridColor = onSurface.copy(alpha = 0.2f)

            // Compose Color 应使用 toArgb() 转为标准 ARGB 整数值
            val labelColorArgb = labelColor.toArgb()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                // 绘图区域边界
                val padLeft = 56f   // Y 轴标签宽度
                val padRight = 16f  // 右侧边距
                val padTop = 8f     // 顶部边距
                val padBottom = 40f // X 轴标签高度

                val drawLeft = padLeft
                val drawRight = size.width - padRight
                val drawBottom = size.height - padBottom

                val drawWidth = drawRight - drawLeft
                val drawHeight = drawBottom - padTop

                if (drawWidth <= 0 || drawHeight <= 0) return@Canvas

                // ── 1.2: 计算全局 Y 轴范围（修复 Double.MIN_VALUE → NEGATIVE_INFINITY） ──
                var globalMin = Double.MAX_VALUE
                var globalMax = Double.NEGATIVE_INFINITY
                var hasValue = false
                lines.forEach { line ->
                    line.values.forEach { v ->
                        hasValue = true
                        if (v < globalMin) globalMin = v
                        if (v > globalMax) globalMax = v
                    }
                }
                if (!hasValue) return@Canvas

                // 如果所有值相同，留出范围
                if (globalMax == globalMin) {
                    globalMin -= 1.0
                    globalMax += 1.0
                }
                // 留 10% 余量
                val yRange = globalMax - globalMin
                val yMin = if (globalMin >= 0.0) 0.0 else globalMin - yRange * 0.1
                val yMax = globalMax + yRange * 0.1

                fun yToPixel(value: Double): Float {
                    return drawBottom - ((value - yMin) / (yMax - yMin) * drawHeight).toFloat()
                }

                fun xToPixel(index: Int): Float {
                    if (xLabels.size <= 1) return drawLeft + drawWidth / 2f
                    return drawLeft + (index.toFloat() / (xLabels.size - 1).toFloat()) * drawWidth
                }

                // 适配屏幕密度的坐标轴文字大小（字号约为原始设计的 30%）
                val yLabelTextSize = 8f * density
                val xLabelTextSize = 7f * density

                // ── 绘制水平网格线（5 条） ──
                val gridLines = 4
                for (i in 0..gridLines) {
                    val yValue = yMin + (yMax - yMin) * (gridLines - i).toDouble() / gridLines.toDouble()
                    val y = yToPixel(yValue)

                    // 网格线
                    drawLine(
                        color = gridColor,
                        start = Offset(drawLeft, y),
                        end = Offset(drawRight, y),
                        strokeWidth = 1f
                    )

                    // 1.1: Y 轴标签（修复 hashCode → value.toInt）
                    val labelText = formatAxisLabel(yValue)
                    val yPaint = Paint().apply {
                        color = labelColorArgb
                        textSize = yLabelTextSize
                        textAlign = Paint.Align.RIGHT
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        drawLeft - 8f,
                        y + 8f,
                        yPaint
                    )
                }

                // ── 绘制 X 轴标签（每隔几个显示一个，避免重叠） ──
                val step = when {
                    xLabels.size <= 6 -> 1
                    xLabels.size <= 12 -> 2
                    xLabels.size <= 24 -> 3
                    else -> 5
                }
                xLabels.forEachIndexed { index, label ->
                    if (index % step == 0) {
                        val x = xToPixel(index)
                        // 1.1: X 轴标签（修复 hashCode → value.toInt）
                        val xPaint = Paint().apply {
                            color = labelColorArgb
                            textSize = xLabelTextSize
                            textAlign = Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x,
                            size.height - 8f,
                            xPaint
                        )
                    }
                }

                // ── 绘制 Y 轴 和 X 轴 ──
                drawLine(
                    color = onSurface.copy(alpha = 0.4f),
                    start = Offset(drawLeft, padTop),
                    end = Offset(drawLeft, drawBottom),
                    strokeWidth = 2f
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.4f),
                    start = Offset(drawLeft, drawBottom),
                    end = Offset(drawRight, drawBottom),
                    strokeWidth = 2f
                )

                // ── 绘制折线 ──
                lines.forEach { line ->
                    val points = line.values.mapIndexed { index, value ->
                        Offset(xToPixel(index), yToPixel(value))
                    }

                    // 1.6: 处理单数据点情况
                    when {
                        points.size >= 2 -> {
                            // 绘制折线路径
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = line.color,
                                style = Stroke(
                                    width = 3f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        points.size == 1 -> {
                            // 单数据点：仅绘制一个较大的实心圆
                            drawCircle(
                                color = line.color,
                                radius = 6f,
                                center = points[0]
                            )
                            // 跳过后续通用数据点绘制（避免重复绘制）
                            return@forEach
                        }
                        else -> return@forEach
                    }

                    // 绘制数据点（空心圆点）
                    // 1.4: 使用 surfaceColor 代替 Color.White，自适应深色模式
                    points.forEach { point ->
                        drawCircle(
                            color = line.color,
                            radius = 5f,
                            center = point
                        )
                        drawCircle(
                            color = surfaceColor,
                            radius = 3f,
                            center = point
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1.3: 格式化 Y 轴标签数值（移除冗余分支，使用 Kotlin 扩展风格）。
 */
private fun formatAxisLabel(value: Double): String = when {
    value >= 100  -> "%.0f".format(value)
    value >= 1    -> "%.1f".format(value)
    value >= 0.01 -> "%.2f".format(value)
    else          -> "%.3f".format(value)
}
