package edu.cqwu.electricity.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 纯 Compose Canvas 二维码组件
 *
 * 使用 ZXing 生成 QR 码矩阵数据，通过 Compose Canvas 绘制圆角方块。
 * 颜色自动跟随 MaterialTheme.colorScheme 的亮/暗模式。
 *
 * 四邻域感知圆角：
 * 对每个黑块，分别检查上、下、左、右四个邻居。
 * 一个角仅当相邻的两个单元格都不存在时才会被圆角，
 * 只要有一侧有邻居就保持直角，确保连接处平滑无瑕疵。
 * 这使得任意形状的连通区域（横排、竖排、L形、T形等）的
 * 外轮廓自然圆角，内部连接处平滑无缝隙。
 *
 * @param content 要编码的二维码内容
 * @param modifier Modifier
 * @param squareCornerFraction 每个方块的圆角占模块大小的比例，范围 0.0~0.5，默认 0.3
 * @param primaryColor 二维码前景色，默认使用 MaterialTheme.colorScheme.primary
 * @param backgroundColor 二维码背景色，默认使用 MaterialTheme.colorScheme.surface
 */
@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    squareCornerFraction: Float = 0.45f,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    // 将 ZXing BitMatrix 转换为可缓存的二维布尔数组
    val matrix = remember(content) {
        try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0)
            val w = bitMatrix.width
            Array(w) { x ->
                BooleanArray(w) { y -> bitMatrix[x, y] }
            }
        } catch (_: Exception) {
            // 内容为空或编码失败时返回空矩阵
            emptyArray<BooleanArray>()
        }
    }

    Canvas(modifier) {
        if (matrix.isEmpty()) return@Canvas

        val cellSize = size.width / matrix.size
        val n = matrix.size
        val r = cellSize * squareCornerFraction.coerceIn(0f, 0.5f)

        // 绘制背景
        drawRect(color = backgroundColor, size = size)

        // 遍历每个黑块，根据四邻域独立控制四个角的圆角
        for (x in 0 until n) {
            for (y in 0 until n) {
                if (!matrix[x][y]) continue

                val hasUp    = x > 0 && matrix[x - 1][y]
                val hasDown  = x < n - 1 && matrix[x + 1][y]
                val hasLeft  = y > 0 && matrix[x][y - 1]
                val hasRight = y < n - 1 && matrix[x][y + 1]

                // 一个角仅当两侧都无邻居时才圆角（外露的角落）
                // 只要有一侧有邻居就保持直角，确保连接处平滑
                val tl = if (!hasUp && !hasLeft) r else 0f
                val tr = if (!hasUp && !hasRight) r else 0f
                val bl = if (!hasDown && !hasLeft) r else 0f
                val br = if (!hasDown && !hasRight) r else 0f

                val left  = y * cellSize
                val top   = x * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                drawRoundRectPath(
                    left = left, top = top,
                    right = right, bottom = bottom,
                    tl = tl, tr = tr, bl = bl, br = br,
                    color = primaryColor
                )
            }
        }
    }
}

/**
 * 绘制每个角独立控制圆角的圆角矩形
 */
private fun DrawScope.drawRoundRectPath(
    left: Float, top: Float,
    right: Float, bottom: Float,
    tl: Float, tr: Float,
    bl: Float, br: Float,
    color: Color
) {
    val path = Path().apply {
        // 从左上角开始
        arcTo(
            rect = Rect(left, top, left + tl * 2, top + tl * 2),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = true
        )
        // 上边 → 右上角
        lineTo(x = right - tr, y = top)
        arcTo(
            rect = Rect(right - tr * 2, top, right, top + tr * 2),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // 右边 → 右下角
        lineTo(x = right, y = bottom - br)
        arcTo(
            rect = Rect(right - br * 2, bottom - br * 2, right, bottom),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // 下边 → 左下角
        lineTo(x = left + bl, y = bottom)
        arcTo(
            rect = Rect(left, bottom - bl * 2, left + bl * 2, bottom),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // 回到左上角
        lineTo(x = left, y = top + tl)
        close()
    }
    drawPath(path = path, color = color)
}
