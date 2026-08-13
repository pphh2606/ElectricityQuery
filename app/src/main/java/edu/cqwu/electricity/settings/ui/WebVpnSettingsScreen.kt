package edu.cqwu.electricity.settings.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import edu.cqwu.electricity.network.WebVpnSettings
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences

/**
 * WebVPN 设置页 — 提供 WebVPN 代理开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebVpnSettingsScreen(
    onBack: () -> Unit,
) {
    val topBarColors = currentTopBarColors()
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferences(context) }
    var webVpnEnabled by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.WEBVPN_ENABLED)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.webvpn_settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
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
