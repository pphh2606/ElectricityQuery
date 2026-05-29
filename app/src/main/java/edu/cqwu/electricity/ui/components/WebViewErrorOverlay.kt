package edu.cqwu.electricity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * WebView 加载错误叠加层
 *
 * 当 WebView 加载失败时，覆盖在 WebView 上方显示友好的错误提示界面，
 * 替代 Android 原生的丑陋错误页面。
 *
 * @param errorCode Chromium 网络错误码或 HTTP 状态码
 * @param description 错误描述文本
 * @param isHttpError 是否为 HTTP 层面的错误（4xx/5xx）
 * @param onRetry 重新加载按钮回调
 * @param onToggleVpn 切换内/外网访问按钮回调，null 时不显示该按钮
 * @param onNetworkSettings 打开系统网络设置按钮回调，null 时不显示该按钮
 * @param modifier Modifier
 */
@Composable
fun WebViewErrorOverlay(
    errorCode: Int,
    description: String,
    isHttpError: Boolean = false,
    onRetry: () -> Unit,
    onToggleVpn: (() -> Unit)? = null,
    onNetworkSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val errorInfo = resolveErrorInfo(errorCode, description, isHttpError)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* 消费触摸事件，防止手势穿透到下方的 WebView */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // ── 错误图标 ──
            Icon(
                imageVector = errorInfo.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 错误标题 ──
            Text(
                text = errorInfo.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 错误描述 ──
            Text(
                text = errorInfo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── 重新加载按钮 ──
            FilledTonalButton(onClick = onRetry) {
                Text("重新加载")
            }

            // ── 操作按钮行 ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── 切换内/外网访问按钮（可选） ──
                if (onToggleVpn != null) {
                    TextButton(onClick = onToggleVpn) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("切换内/外网")
                    }
                }

                // ── 网络设置按钮（可选） ──
                if (onNetworkSettings != null) {
                    TextButton(onClick = onNetworkSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("网络设置")
                    }
                }
            }
        }
    }
}

/**
 * 根据错误码解析用户友好的错误信息
 */
private fun resolveErrorInfo(
    errorCode: Int,
    description: String,
    isHttpError: Boolean
): ErrorDisplayInfo {
    if (isHttpError) {
        return when (errorCode) {
            404 -> ErrorDisplayInfo(
                icon = Icons.Filled.ErrorOutline,
                title = "页面未找到",
                description = "请求的页面不存在（HTTP 404）"
            )
            500 -> ErrorDisplayInfo(
                icon = Icons.Filled.ErrorOutline,
                title = "服务器内部错误",
                description = "服务器遇到问题，请稍后重试（HTTP 500）"
            )
            502, 503, 504 -> ErrorDisplayInfo(
                icon = Icons.Filled.CloudOff,
                title = "服务暂时不可用",
                description = "服务器正在维护或过载，请稍后重试（HTTP $errorCode）"
            )
            else -> ErrorDisplayInfo(
                icon = Icons.Filled.ErrorOutline,
                title = "网页加载失败",
                description = "服务器返回错误（HTTP $errorCode）"
            )
        }
    }

    return when (errorCode) {
        -2 -> ErrorDisplayInfo(
            icon = Icons.Filled.WifiOff,
            title = "网络连接已断开",
            description = "请检查网络设置后重试"
        )
        -8 -> ErrorDisplayInfo(
            icon = Icons.Filled.Timer,
            title = "连接超时",
            description = "请使用校园网环境或稍后重试"
        )
        -15 -> ErrorDisplayInfo(
            icon = Icons.Filled.Dns,
            title = "无法解析服务器地址",
            description = "DNS 解析失败，请检查网络连接"
        )
        -106 -> ErrorDisplayInfo(
            icon = Icons.Filled.SignalWifiOff,
            title = "服务器拒绝连接",
            description = "请使用校园网环境或稍后重试"
        )
        -324 -> ErrorDisplayInfo(
            icon = Icons.Filled.CloudOff,
            title = "服务器未响应",
            description = "请使用校园网环境打开"
        )
        -109 -> ErrorDisplayInfo(
            icon = Icons.Filled.SignalWifiOff,
            title = "网络地址不可达",
            description = "无法访问目标服务器，请检查网络连接"
        )
        else -> ErrorDisplayInfo(
            icon = Icons.Filled.ErrorOutline,
            title = "网页加载失败",
            description = description.ifBlank { "请检查网络连接后重试" }
        )
    }
}

/**
 * 错误展示信息数据类
 */
private data class ErrorDisplayInfo(
    val icon: ImageVector,
    val title: String,
    val description: String
)
