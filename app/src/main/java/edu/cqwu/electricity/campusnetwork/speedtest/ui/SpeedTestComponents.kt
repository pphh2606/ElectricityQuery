package edu.cqwu.electricity.campusnetwork.speedtest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 校内网络测速页的无状态展示组件与尺寸 token。
 *
 * 本文件只放"不认识页面状态"的哑组件（不引用 [SpeedTestUiState]/`tick`），
 * 便于预览、复用与单测；状态与数据绑定留在 `SpeedTestScreen.kt`。
 * 所有尺寸/颜色数值与原实现逐一相同，集中在此仅为消除各处的重复字面量。
 */
internal object SpeedTestDimens {
    /** 卡片圆角（对齐设置页入口卡片） */
    val CardRadius = 16.dp

    /** 分组标题内边距：标题在卡片外，缩进同设置页 SectionTitle */
    val SectionTitlePadding = PaddingValues(start = 4.dp, top = 8.dp)

    /** 入口行/记录行内边距 */
    val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)

    /** 入口行图标与其右侧内容的间距 */
    val RowIconSpacing = 16.dp

    /** 入口行图标尺寸（设置页为纯图标，无圆形底） */
    val RowIconSize = 24.dp

    /** 入口行右箭头尺寸 */
    val RowArrowSize = 24.dp

    /** 胶囊按钮高度（圆角取一半） */
    val CapsuleHeight = 46.dp
}

/** 分组标题：卡片外的蓝色小标题（样式与缩进对齐设置页 SectionTitle） */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(SpeedTestDimens.SectionTitlePadding),
    )
}

/**
 * 卡片分组容器：16dp 圆角 + surfaceContainerLow 底（对齐设置页入口卡片）。
 *
 * 标题在卡片**外部**，由调用方用 [SectionTitle] 渲染；
 * 网络服务 / 国际学术资源 / 最近测速三个分区共用。
 */
@Composable
internal fun SettingsCard(
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SpeedTestDimens.CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 卡片内分隔线（「最近测速」数据行之间使用） */
@Composable
internal fun CardDivider(palette: SpeedTestPalette) {
    HorizontalDivider(color = palette.divider)
}

/** 撑满可用高度的竖分隔线（调用方需在 `height(IntrinsicSize.Max)` 的 Row 内使用） */
@Composable
internal fun RowDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(color),
    )
}

/**
 * 入口行：纯图标 + 标题/副标题 + 右箭头（对齐设置页入口行）。
 * 整行可点，并按一条语义合并，供读屏一次播报标题与副标题。
 */
@Composable
internal fun IconRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(SpeedTestDimens.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SpeedTestDimens.RowIconSize),
        )
        Spacer(modifier = Modifier.width(SpeedTestDimens.RowIconSpacing))
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
        Spacer(modifier = Modifier.width(SpeedTestDimens.RowIconSpacing))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(SpeedTestDimens.RowArrowSize),
        )
    }
}

/**
 * 数值格：小标签（可带字母后缀与箭头图标）+ 大数字 +（可选）单位。
 *
 * 会话行（18sp/Bold/无单位/2dp 标签间距）与 2×2 四宫格（40sp/Medium/带单位）共用，
 * 差异全部由参数表达，字号字重等默认值即四宫格原值。
 */
@Composable
internal fun MetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    letter: String? = null,
    icon: ImageVector? = null,
    unit: String? = null,
    valueSize: TextUnit = 40.sp,
    valueWeight: FontWeight = FontWeight.Medium,
    verticalPadding: Dp = 18.dp,
    labelSpacing: Dp = 6.dp,
) {
    Column(
        modifier = modifier.padding(vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = if (letter != null) "$label $letter" else label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Serif,
            )
        }
        Spacer(modifier = Modifier.height(labelSpacing))
        Text(
            text = value,
            fontSize = valueSize,
            fontWeight = valueWeight,
            fontFamily = FontFamily.Serif,
            color = color,
        )
        unit?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.85f),
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

/**
 * 全圆角胶囊按钮；[progress] > 0 时在文字下层叠加左侧进度遮罩。
 *
 * 遮罩必须位于按钮背景之上、文字之下，故保留外层 Box 承载底色与遮罩、
 * 内层 Button 透明底色与波纹的分层结构。
 */
@Composable
internal fun PillButton(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    progress: Float = 0f,
    overlayColor: Color = Color.Transparent,
) {
    val shape = RoundedCornerShape(SpeedTestDimens.CapsuleHeight / 2)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SpeedTestDimens.CapsuleHeight)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(overlayColor),
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = fg,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = fg.copy(alpha = 0.9f),
            ),
            shape = shape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

/** 记录行内的小标签（DL/UL）+ 色值 */
@Composable
internal fun SpeedValue(
    label: String,
    value: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.7f),
            fontFamily = FontFamily.Serif,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            fontFamily = FontFamily.Serif,
        )
    }
}
