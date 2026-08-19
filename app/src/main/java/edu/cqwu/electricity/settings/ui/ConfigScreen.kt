package edu.cqwu.electricity.settings.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.AppLanguage
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences
import edu.cqwu.electricity.theme.ui.LanguageSwitchSheet
import kotlin.math.roundToInt

private const val MIN_UPDATE_TIMEOUT_MS = 1000
private const val MAX_UPDATE_TIMEOUT_MS = 10000

/**
 * 配置页 — 包含浏览器标识和语言切换设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    onNavigateToUserAgent: () -> Unit,
    onNavigateToStorageClear: () -> Unit = {},
    onNavigateToWebVpn: () -> Unit,
) {
    val topBarColors = currentTopBarColors()
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferences(context) }

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLogLevelSheet by remember { mutableStateOf(false) }
    val currentLanguage by remember { mutableStateOf(settingsPrefs.getAppLanguage()) }
    var autoUpdateEnabled by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.AUTO_UPDATE_ENABLED)) }
    var checkCiUpdates by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.CHECK_CI_UPDATES)) }
    var updateTimeoutMs by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.UPDATE_TIMEOUT_MS)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.config_title), fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    // ── WebVPN 设置 ──
                    ConfigEntry(
                        icon = Icons.Outlined.VpnKey,
                        title = stringResource(R.string.webvpn_settings_title),
                        subtitle = stringResource(R.string.webvpn_settings_desc),
                        onClick = onNavigateToWebVpn,
                    )

                    // ── 浏览器标识 ──
                    ConfigEntry(
                        icon = Icons.Outlined.Smartphone,
                        title = stringResource(R.string.config_user_agent),
                        subtitle = stringResource(R.string.config_user_agent_desc),
                        onClick = onNavigateToUserAgent,
                    )

                    // ── 语言 ──
                    ConfigEntry(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.language_title),
                        subtitle = if (currentLanguage == AppLanguage.SYSTEM) {
                            stringResource(R.string.language_system)
                } else {
                    stringResource(currentLanguage.labelRes)
                },
                        onClick = { showLanguageSheet = true },
                    )

                    // ── 日志等级 ──
                    ConfigEntry(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.config_log_level),
                        subtitle = stringResource(R.string.config_log_level_desc),
                        onClick = { showLogLevelSheet = true },
                    )

                    // ── 清除存储空间 ──
                    ConfigEntry(
                        icon = Icons.Outlined.CleaningServices,
                        title = stringResource(R.string.storage_clear_title),
                        subtitle = stringResource(R.string.storage_clear_desc),
                        onClick = onNavigateToStorageClear,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.config_update_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    SettingsSwitchEntry(
                        icon = Icons.Outlined.SystemUpdate,
                        title = stringResource(R.string.update_settings_auto_update),
                        subtitle = stringResource(R.string.update_settings_auto_update_desc),
                        checked = autoUpdateEnabled,
                        onCheckedChange = { enabled ->
                            autoUpdateEnabled = enabled
                            settingsPrefs.set(SettingsKeys.AUTO_UPDATE_ENABLED, enabled)
                        },
                    )
                    SettingsSwitchEntry(
                        icon = Icons.Outlined.RocketLaunch,
                        title = stringResource(R.string.update_settings_check_ci),
                        subtitle = stringResource(R.string.update_settings_check_ci_desc),
                        checked = checkCiUpdates,
                        onCheckedChange = { enabled ->
                            checkCiUpdates = enabled
                            settingsPrefs.set(SettingsKeys.CHECK_CI_UPDATES, enabled)
                        },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.update_settings_timeout),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(
                                    R.string.update_settings_timeout_value,
                                    updateTimeoutMs / 1000f,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Slider(
                            value = updateTimeoutMs / 1000f,
                            onValueChange = { seconds ->
                                val newMs = (seconds * 1000)
                                    .roundToInt()
                                    .coerceIn(MIN_UPDATE_TIMEOUT_MS, MAX_UPDATE_TIMEOUT_MS)
                                updateTimeoutMs = newMs
                                settingsPrefs.set(SettingsKeys.UPDATE_TIMEOUT_MS, newMs)
                            },
                            valueRange = MIN_UPDATE_TIMEOUT_MS / 1000f..MAX_UPDATE_TIMEOUT_MS / 1000f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // ── 语言选择 BottomSheet（复用 LanguageSwitchSheet）──
    LanguageSwitchSheet(showSheet = showLanguageSheet, onDismiss = { showLanguageSheet = false })
    LogLevelSheet(showSheet = showLogLevelSheet, onDismiss = { showLogLevelSheet = false })
}

/**
 * 配置页面条目，与 SettingsScreen 的 SettingsEntry 样式一致。
 */
@Composable
private fun ConfigEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
