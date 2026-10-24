package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.transparentTopBarColors

/**
 * 页面统一外壳：大标题随滚动折叠成小标题，标题栏浮在内容之上做模糊。
 *
 * @param onBack 为 null 时不画返回键（底部导航的根页面）。
 * @param content 参数一为标题栏当前高度（内容首项留白用）；参数二必须挂在滚动容器之前
 * （`Modifier.fillMaxSize().then(scrollModifier).verticalScroll(...)`）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollapsibleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    expandedTopContent: @Composable () -> Unit = {},
    content: @Composable (topInset: Dp, scrollModifier: Modifier) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    BlurScaffold(
        modifier = modifier,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LargeFlexibleTopAppBar(
                    title = { Text(text = title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                )
                            }
                        }
                    },
                    actions = actions,
                    colors = transparentTopBarColors(),
                    scrollBehavior = scrollBehavior,
                )
                expandedTopContent()
            }
        },
    ) { topInset ->
        content(topInset, Modifier.nestedScroll(scrollBehavior.nestedScrollConnection))
    }
}
