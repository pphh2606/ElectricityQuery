package edu.cqwu.electricity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

/**
 * 统一的 Tab 内嵌/独立页面 Scaffold 包装器。
 *
 * 当 [showTopBar] = true 时显示自带的 TopAppBar（含返回按钮和 actions），
 * 当 [showTopBar] = false 时仅透传 [PaddingValues(0.dp)] 给内容，
 * 用于被 [ElectricityMainScreen] 的 Tab 内嵌时不自带顶栏。
 *
 * @param showTopBar 是否显示自带的 TopAppBar
 * @param title 顶栏标题
 * @param onBack 返回按钮回调
 * @param actions 顶栏右侧操作区（可选）
 * @param content 内容区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    showTopBar: Boolean,
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    if (showTopBar) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = actions,
                        colors = topBarColors
                    )
                }
            ) { paddingValues ->
                content(paddingValues)
            }
        }
    } else {
        content(PaddingValues(0.dp))
    }
}
