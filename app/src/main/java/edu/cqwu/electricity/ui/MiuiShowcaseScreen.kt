package edu.cqwu.electricity.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * miuix 基础组件预览页。
 *
 * 独立使用一套 MiuixTheme，保证在 Material 3 界面风格下打开也能看到 miuix 的真实观感；
 * 不接入任何业务状态，只用来对比两种组件体系的样式差异。
 */
@Composable
fun MiuiShowcaseScreen(onBack: () -> Unit) {
    val controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(
                title = stringResource(R.string.miui_showcase_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val colors = MiuixTheme.colorScheme

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "MIUI Card",
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.miui_showcase_desc),
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Button / TextButton")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) {
                            Text(text = "Primary")
                        }
                        TextButton(text = "Text", onClick = {})
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    var switchA by remember { mutableStateOf(true) }
                    var switchB by remember { mutableStateOf(false) }
                    Text(text = "Switch")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Enabled")
                        Switch(checked = switchA, onCheckedChange = { switchA = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Disabled")
                        Switch(checked = switchB, onCheckedChange = { switchB = it })
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    var text by remember { mutableStateOf("") }
                    Text(text = "TextField")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "Input",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Colors")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSwatch("primary", colors.primary)
                        ColorSwatch("secondary", colors.secondary)
                        ColorSwatch("error", colors.error)
                        ColorSwatch("surface", colors.surface)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(color, RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label)
    }
}
