@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.settings.ui


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.net.WebVpnSettings
import edu.cqwu.electricity.common.settings.SettingsKeys
import edu.cqwu.electricity.common.settings.SettingsPreferences
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.transparentTopBarColors

/**
 * WebVPN 设置页 — 提供 WebVPN 代理开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebVpnSettingsScreen(
    onBack: () -> Unit,
) {
    val topBarColors = transparentTopBarColors()
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferences(context) }
    var webVpnEnabled by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.WEBVPN_ENABLED)) }

    BlurScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.webvpn_settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { topInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsGroup {
                item {
                    SettingsSwitchEntry(
                        icon = Icons.Outlined.VpnKey,
                        title = stringResource(R.string.settings_webvpn_enabled),
                        subtitle = stringResource(R.string.settings_webvpn_enabled_desc),
                        checked = webVpnEnabled,
                        onCheckedChange = { enabled ->
                            webVpnEnabled = enabled
                            settingsPrefs.set(SettingsKeys.WEBVPN_ENABLED, enabled)
                            WebVpnSettings.enabled = enabled
                        },
                    )
                }
            }
        }
    }
}
