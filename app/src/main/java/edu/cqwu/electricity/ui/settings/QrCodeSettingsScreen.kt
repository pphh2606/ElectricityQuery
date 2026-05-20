package edu.cqwu.electricity.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.QrCodeColorMode
import edu.cqwu.electricity.ui.components.QrCodeView
import edu.cqwu.electricity.ui.theme.LocalQrCodeSettings
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

/** 圆角滑块固定点位 */
private val CORNER_RADIUS_STEPS = listOf(0, 10, 20, 30, 40, 50)

/**
 * 二维码设置页面
 *
 * 提供：
 * - 二维码实时预览（应用当前颜色和圆角设置）
 * - 颜色模式选择：MD3 主题色 / 黑色（夜间模式自动切换为白色）
 * - 圆角度滑块：固定整数点位 [0, 10, 20, 30, 40, 50]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeSettingsScreen(
    onBack: () -> Unit,
) {
    val qrCodeSettings = LocalQrCodeSettings.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 深色模式下背景强制白色（确保扫码可读），前景色遵循用户设置
    val primaryColor = when (qrCodeSettings.colorMode) {
        QrCodeColorMode.THEME_SNAKE -> MaterialTheme.colorScheme.primary
        QrCodeColorMode.MONOCHROME -> Color.Black
    }
    // 深色模式下 MD3 primary 偏浅（为深色背景设计），在白色背景上需加深
    val effectivePrimaryColor = if (isDarkTheme && qrCodeSettings.colorMode == QrCodeColorMode.THEME_SNAKE) {
        val c = primaryColor
        val darkenFactor = 0.45f
        Color(c.red * darkenFactor, c.green * darkenFactor, c.blue * darkenFactor, c.alpha)
    } else {
        primaryColor
    }
    val qrBackgroundColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.surface

    // 圆角分数（0~50 int → 0.0f~0.5f float）
    val cornerFraction = qrCodeSettings.cornerRadius / 100f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "二维码设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── 二维码实时预览 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCodeView(
                        content = "QR Code Preview",
                        modifier = Modifier.size(240.dp),
                        squareCornerFraction = cornerFraction,
                        primaryColor = effectivePrimaryColor,
                        backgroundColor = qrBackgroundColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 颜色选择 ──
            SectionTitle(title = "样式设置")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ColorModeRow(
                        title = "MD3 主题色",
                        subtitle = "使用当前主题色作为二维码黑块颜色",
                        selected = qrCodeSettings.colorMode == QrCodeColorMode.THEME_SNAKE,
                        onClick = { qrCodeSettings.onColorModeChange(QrCodeColorMode.THEME_SNAKE) },
                        colorPreview = MaterialTheme.colorScheme.primary,
                    )
                    ColorModeRow(
                        title = "黑色",
                        subtitle = "使用黑色作为二维码黑块颜色",
                        selected = qrCodeSettings.colorMode == QrCodeColorMode.MONOCHROME,
                        onClick = { qrCodeSettings.onColorModeChange(QrCodeColorMode.MONOCHROME) },
                        colorPreview = if (isDarkTheme) Color.White else Color.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 圆角度 ──
            SectionTitle(title = "圆角度")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    // 当前值显示
                    Text(
                        text = "${qrCodeSettings.cornerRadius}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 滑块（steps = 4 表示中间 4 个 tick，加上两端共 6 个整数点位：0,10,20,30,40,50）
                    Slider(
                        value = qrCodeSettings.cornerRadius.toFloat(),
                        onValueChange = { newValue ->
                            // 吸附到最近的固定点位
                            val snapped = CORNER_RADIUS_STEPS.minByOrNull {
                                kotlin.math.abs(it - newValue)
                            } ?: newValue.toInt()
                            qrCodeSettings.onCornerRadiusChange(snapped)
                        },
                        valueRange = 0f..50f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 固定点位标签
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        CORNER_RADIUS_STEPS.forEach { step ->
                            Text(
                                text = "$step",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 屏幕高亮 ──
            SectionTitle(title = "显示")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "屏幕高亮",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "显示二维码时自动调高屏幕亮度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = qrCodeSettings.screenBrightnessEnabled,
                        onCheckedChange = qrCodeSettings.onScreenBrightnessEnabledChange,
                    )
                }
            }
        }
    }
}

/**
 * 颜色模式选择行 — 与 [PersonalizationScreen] 的 [ThemeColorRow] 风格一致
 */
@Composable
private fun ColorModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    colorPreview: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colorPreview, RoundedCornerShape(6.dp)),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * 分区标题 — 与 [PersonalizationScreen] 复用相同样式
 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
