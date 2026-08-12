package edu.cqwu.electricity.theme.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

/**
 * 登录过期时显示的通用 UI 组件。
 *
 * 包含错误提示文字和操作按钮：
 * - [requiresReLogin] = true：显示「重新登录」按钮
 * - [requiresReLogin] = false：显示「重试」按钮
 *
 * @param errorMessage 错误提示文本
 * @param requiresReLogin 是否需要重新登录
 * @param onReLogin 重新登录按钮点击回调
 * @param onRetry 重试按钮点击回调
 */
@Composable
fun ReLoginContent(
    errorMessage: String?,
    requiresReLogin: Boolean,
    onReLogin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = errorMessage ?: if (requiresReLogin) stringResource(R.string.login_expired) else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (requiresReLogin) {
                Button(onClick = onReLogin) {
                    Text(stringResource(R.string.login_relogin))
                }
            } else {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}
