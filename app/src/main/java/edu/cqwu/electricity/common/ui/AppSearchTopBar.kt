@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.UiStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 搜索态顶栏：整条顶栏变成一个输入框。
 *
 * 两套风格形态不同，但对外接口一致：
 * - Material 3：沿用原来的定高 `TopAppBar`，输入框塞在 title 里（下划线样式）；
 * - MIUI：不画标题栏，只画一行「返回 + 输入框」，用 miuix 的 `InputField`。
 *
 * 页面的 `BackHandler`、提交动作、查询状态仍由各页面自己持有。
 *
 * @param onSubmit 提交搜索（回车 / 键盘搜索键 都触发）；null 表示只能点 [actions] 里的按钮
 */
@Composable
fun AppSearchTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    onSubmit: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIconButton(onClick = onBack) {
                    MiuixIcon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_exit_search),
                    )
                }
                MiuixInputField(
                    query = value,
                    onQueryChange = onValueChange,
                    label = hint,
                    expanded = false,
                    onExpandedChange = {},
                    onSearch = { onSubmit?.invoke() },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                actions()
            }
        }
        return
    }

    TopAppBar(
        title = {
            SearchTextField(
                value = value,
                onValueChange = onValueChange,
                hint = hint,
                focusRequester = focusRequester,
                onSubmit = { onSubmit?.invoke() },
                onClear = onClear,
                showIndicator = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_exit_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = actions,
        colors = transparentTopBarColors(),
    )
}

/**
 * 标题栏下方独立一行的搜索输入框，供「标题栏 + 搜索框」的传统结构使用。
 *
 * 与 [AppSearchTopBar] 的区别：它不占标题栏，标题仍由页面的 [AppTopBar] 负责，
 * 搜索框单独排一行。MIUI 用 miuix 的 InputField，M3 用透明底下划线 TextField。
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalUiStyle.current == UiStyle.MIUI) {
            MiuixInputField(
                query = value,
                onQueryChange = onValueChange,
                label = hint,
                expanded = false,
                onExpandedChange = {},
                onSearch = { onSubmit?.invoke() },
                modifier = Modifier.weight(1f).then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            )
        } else {
            SearchTextField(
                value = value,
                onValueChange = onValueChange,
                hint = hint,
                focusRequester = focusRequester,
                onSubmit = { onSubmit?.invoke() },
                onClear = onClear,
                modifier = Modifier.weight(1f),
            )
        }
        actions()
    }
}

/**
 * 两个搜索组件共用的 M3 输入框：透明底、可选的聚焦下划线、行尾清除按钮。
 *
 * @param showIndicator 是否画聚焦/未聚焦下划线：标题栏里的搜索框画，独立排一行的搜索框不画
 */
@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showIndicator: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        singleLine = true,
        modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = if (showIndicator) MaterialTheme.colorScheme.primary else Color.Transparent,
            unfocusedIndicatorColor = if (showIndicator) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            } else {
                Color.Transparent
            },
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        trailingIcon = {
            // MIUI 的 InputField 自带清理图标，这里只服务 M3
            if (value.isNotEmpty() && onClear != null) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.common_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
