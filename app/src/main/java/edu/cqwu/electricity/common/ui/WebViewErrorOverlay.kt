package edu.cqwu.electricity.common.ui

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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WifiOff
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

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
                Text(stringResource(R.string.webview_reload))
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
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.webview_toggle_network))
                    }
                }

                // ── 网络设置按钮（可选） ──
                if (onNetworkSettings != null) {
                    TextButton(onClick = onNetworkSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.webview_network_settings))
                    }
                }
            }
        }
    }
}

/**
 * 根据错误码解析用户友好的错误信息
 */
@Composable
private fun resolveErrorInfo(
    errorCode: Int,
    description: String,
    isHttpError: Boolean
): ErrorDisplayInfo {
    if (isHttpError) {
        return when (errorCode) {
            404 -> ErrorDisplayInfo(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.webview_error_page_not_found),
                description = stringResource(R.string.webview_error_page_not_found_desc)
            )
            500 -> ErrorDisplayInfo(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.webview_error_server_error),
                description = stringResource(R.string.webview_error_server_error_desc)
            )
            502, 503, 504 -> ErrorDisplayInfo(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.webview_error_service_unavailable),
                description = stringResource(R.string.webview_error_service_unavailable_desc, errorCode)
            )
            else -> ErrorDisplayInfo(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.webview_error_load_failed),
                description = stringResource(R.string.webview_error_http_generic_desc, errorCode)
            )
        }
    }

    // 根据错误描述字符串匹配（如 "net::ERR_EMPTY_RESPONSE"），比数字错误码更可靠
    return when {
        description.contains("ERR_INTERNET_DISCONNECTED") -> ErrorDisplayInfo(
            icon = Icons.Outlined.WifiOff,
            title = stringResource(R.string.webview_error_network_disconnected),
            description = stringResource(R.string.webview_error_network_disconnected_desc)
        )
        description.contains("ERR_TIMED_OUT") || description.contains("ERR_CONNECTION_TIMED_OUT") -> ErrorDisplayInfo(
            icon = Icons.Outlined.Timer,
            title = stringResource(R.string.webview_error_timeout),
            description = stringResource(R.string.webview_error_timeout_desc)
        )
        description.contains("ERR_NAME_NOT_RESOLVED") || description.contains("ERR_DNS") -> ErrorDisplayInfo(
            icon = Icons.Outlined.Dns,
            title = stringResource(R.string.webview_error_dns_failed),
            description = stringResource(R.string.webview_error_dns_failed_desc)
        )
        description.contains("ERR_CONNECTION_REFUSED") -> ErrorDisplayInfo(
            icon = Icons.Outlined.SignalWifiOff,
            title = stringResource(R.string.webview_error_connection_refused),
            description = stringResource(R.string.webview_error_connection_refused_desc)
        )
        description.contains("ERR_EMPTY_RESPONSE") -> ErrorDisplayInfo(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.webview_error_no_response),
            description = stringResource(R.string.webview_error_no_response_desc)
        )
        description.contains("ERR_ADDRESS_UNREACHABLE") -> ErrorDisplayInfo(
            icon = Icons.Outlined.SignalWifiOff,
            title = stringResource(R.string.webview_error_unreachable),
            description = stringResource(R.string.webview_error_unreachable_desc)
        )
        description.contains("ERR_CONNECTION_RESET") || description.contains("ERR_CONNECTION_CLOSED") -> ErrorDisplayInfo(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.webview_error_no_response),
            description = stringResource(R.string.webview_error_no_response_desc)
        )
        description.contains("ERR_SSL") || description.contains("ERR_CERT") -> ErrorDisplayInfo(
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.webview_error_load_failed),
            description = stringResource(R.string.webview_error_connection_refused_desc)
        )
        else -> ErrorDisplayInfo(
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.webview_error_load_failed),
            description = description.ifBlank { stringResource(R.string.webview_error_generic_desc) }
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
