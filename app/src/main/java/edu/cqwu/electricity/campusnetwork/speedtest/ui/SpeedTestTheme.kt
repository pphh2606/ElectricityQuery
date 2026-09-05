package edu.cqwu.electricity.campusnetwork.speedtest.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme

/**
 * 网速测试页专用语义色板。
 *
 * 色值来源：官网页面打包 CSS（`fortest/校园网测速.har.txt` → `index-CY0zSaht.css`）的
 * `:root`（浅色）与 `.dark`（深色）hsl 变量 + 页面 JS 内指标专用类（blue-600/blue-400、red-600/red-400）。
 * 不注入全局 theme，仅本页按当前深浅模式取值。
 */
data class SpeedTestPalette(
    /** 下载数字/单位颜色 */
    val download: Color,
    /** 上传/停止数字、停止按钮颜色 */
    val upload: Color,
    /** 延迟数字颜色（与下载一致蓝） */
    val ping: Color,
    /** 抖动数字颜色（深灰/近黑） */
    val jitter: Color,
    /** 开始按钮底色（浅=近黑 / 深=近白）与按钮文字（取反） */
    val startButton: Color,
    val startButtonText: Color,
    /** 停止按钮底色 */
    val stopButton: Color,
    val stopButtonText: Color,
    /** 按钮内进度填充遮罩 */
    val progressOverlay: Color,
    /** 指标小标签/次级文字 */
    val label: Color,
    /** 分隔线 */
    val divider: Color,
    /** 提示横幅底与文字 */
    val bannerBackground: Color,
    val bannerText: Color,
)

/** 根据当前 MaterialTheme 深浅模式取官方两套色板 */
@androidx.compose.runtime.Composable
fun speedTestPalette(): SpeedTestPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        SpeedTestPalette(
            download = Color(0xFF60A5FA),      // blue-400
            upload = Color(0xFFF87171),        // red-400
            ping = Color(0xFF60A5FA),
            jitter = Color(0xFF9CA3AF),        // gray-400
            startButton = Color(0xFFFAFAFA),   // .dark primary 98%
            startButtonText = Color(0xFF0A0A0A),
            stopButton = Color(0xFFEF4444),    // red-500
            stopButtonText = Color(0xFFFFFFFF),
            progressOverlay = Color(0xFFFFFFFF).copy(alpha = 0.18f),
            label = Color(0xFFA3A3A3),         // muted-foreground 63.9%
            divider = Color(0xFF262626),       // border 14.9%
            bannerBackground = Color(0xFF2A2820),
            bannerText = Color(0xFFD6D3C8),
        )
    } else {
        SpeedTestPalette(
            download = Color(0xFF2563EB),      // blue-600
            upload = Color(0xFFDC2626),        // red-600
            ping = Color(0xFF2563EB),
            jitter = Color(0xFF374151),        // gray-700
            startButton = Color(0xFF0A0A0A),   // :root primary 9%
            startButtonText = Color(0xFFFFFFFF),
            stopButton = Color(0xFFE53935),
            stopButtonText = Color(0xFFFFFFFF),
            progressOverlay = Color(0xFF000000).copy(alpha = 0.20f),
            label = Color(0xFF737373),         // muted-foreground 45.1%
            divider = Color(0xFFE5E5E5),       // border 89.8%
            bannerBackground = Color(0xFFFFF8E1),
            bannerText = Color(0xFF5F5A48),
        )
    }
}
