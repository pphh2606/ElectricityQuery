package edu.cqwu.electricity.common.ui
import edu.cqwu.electricity.logging.AppLog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.min

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
 * 绘制按画布短边取正方形并双向居中，因此即使调用方传入了非正方形尺寸
 * （例如窄屏下固定尺寸被父约束压小），二维码也不会偏向某一侧。
 * 全部方块合并为单个 [Path]，并借 [drawWithCache] 只在尺寸或内容变化时重建，
 * 不会每帧重复构造上千条子路径。
 *
 * 为获得四周相等的留白并保证扫码可靠，调用方应让本组件保持正方形
 * （配合 `Modifier.aspectRatio(1f)`，不要在同一个 modifier 上再叠加固定高度）。
 *
 * @param content 要编码的二维码内容
 * @param modifier Modifier
 * @param squareCornerFraction 每个方块的圆角占模块大小的比例，范围 0.0~0.5，默认 0.45
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
    // 用 State 持有矩阵：drawWithCache 读取它建立依赖，内容变化时缓存随之失效
    val matrix = remember(content) { mutableStateOf(encodeToMatrix(content)) }

    Spacer(
        modifier = modifier.drawWithCache {
            // 只在尺寸或矩阵变化时执行：格子边长、圆角、路径都在这里算好并缓存
            val path = buildQrPath(matrix.value, squareCornerFraction, size.width, size.height)
            onDrawBehind {
                drawRect(color = backgroundColor, size = size)
                drawPath(path = path, color = primaryColor)
            }
        }
    )
}

/** 将 ZXing 矩阵转为可缓存的二维布尔数组；内容为空或编码失败时返回空数组 */
private fun encodeToMatrix(content: String): Array<BooleanArray> = try {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0)
    val width = bitMatrix.width
    Array(width) { x -> BooleanArray(width) { y -> bitMatrix[x, y] } }
} catch (_: Exception) {
    AppLog.w("QrCodeView", "二维码编码失败，返回空矩阵")
    emptyArray()
}

/**
 * 把所有黑块合并成一个 [Path]：按画布短边取正方形边长并双向居中。
 *
 * 每个黑块的四个角按四邻域判断是否圆角 —— 仅当该角相邻的两个格子都不存在时才圆角，
 * 保证连通区域外轮廓圆润、内部连接处无缝隙。
 */
private fun buildQrPath(
    matrix: Array<BooleanArray>,
    cornerFraction: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): Path {
    val n = matrix.size
    val path = Path()
    if (n == 0) return path

    // 画布不保证是正方形（窄屏下固定尺寸会被父约束压小），按短边取边长并居中
    val side = min(canvasWidth, canvasHeight)
    val cell = side / n
    val radius = cell * cornerFraction.coerceIn(0f, 0.5f)
    val originX = (canvasWidth - side) / 2f
    val originY = (canvasHeight - side) / 2f

    for (x in 0 until n) {
        val top = originY + x * cell
        for (y in 0 until n) {
            if (!matrix[x][y]) continue

            val hasUp = x > 0 && matrix[x - 1][y]
            val hasDown = x < n - 1 && matrix[x + 1][y]
            val hasLeft = y > 0 && matrix[x][y - 1]
            val hasRight = y < n - 1 && matrix[x][y + 1]

            val left = originX + y * cell
            path.addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + cell, top + cell),
                    topLeft = CornerRadius(if (!hasUp && !hasLeft) radius else 0f),
                    topRight = CornerRadius(if (!hasUp && !hasRight) radius else 0f),
                    bottomRight = CornerRadius(if (!hasDown && !hasRight) radius else 0f),
                    bottomLeft = CornerRadius(if (!hasDown && !hasLeft) radius else 0f),
                )
            )
        }
    }
    return path
}
