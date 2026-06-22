package edu.cqwu.electricity.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

/**
 * 校验输入的字符串是否为合法的 URL。
 */
private fun isValidUrl(url: String): Boolean {
    if (url.isBlank()) return false
    // 临时补全协议头以便 java.net.URI 能正确解析
    val normalized = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        "https://$url"
    } else {
        url
    }
    return try {
        val uri = java.net.URI(normalized)
        uri.host != null && uri.host.contains(".")
    } catch (_: Exception) {
        false
    }
}

/**
 * 自动补全协议头：用户输入不含 http(s):// 时补 https://。
 */
private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        "https://$trimmed"
    } else {
        trimmed
    }
}

/**
 * 打开网址对话框，让用户输入 URL 并选择打开方式。
 *
 * @param onDismiss 关闭对话框
 * @param onConfirm 确认时的回调 (url: String, isInternal: Boolean)
 *                   isInternal=true 表示内网打开（通过 WebVPN 代理）
 */
@Composable
fun OpenUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, isInternal: Boolean) -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }
    var isInternal by remember { mutableStateOf(true) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // 对话框弹出后自动聚焦输入框并弹出软键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.open_url_title)) },
        text = {
            Column {
                TextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        urlError = null // 用户输入时清除错误
                    },
                    label = { Text(stringResource(R.string.open_url_label)) },
                    placeholder = { Text(stringResource(R.string.open_url_placeholder)) },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = urlError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (isValidUrl(urlInput)) {
                                onConfirm(normalizeUrl(urlInput), isInternal)
                            } else {
                                urlError = context.getString(R.string.open_url_invalid)
                            }
                        }
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isInternal,
                        onCheckedChange = { isInternal = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.open_url_intranet),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValidUrl(urlInput)) {
                        onConfirm(normalizeUrl(urlInput), isInternal)
                    } else {
                    }
                },
                enabled = urlInput.isNotBlank() && isValidUrl(urlInput)
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
