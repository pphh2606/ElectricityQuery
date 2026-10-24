package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import edu.cqwu.electricity.common.settings.AppLanguage
import edu.cqwu.electricity.common.settings.SettingsKeys
import edu.cqwu.electricity.common.settings.SettingsPreferences
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import edu.cqwu.electricity.common.ui.LanguageSwitchSheet
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
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferences(context) }

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLogLevelSheet by remember { mutableStateOf(false) }
    val currentLanguage by remember { mutableStateOf(settingsPrefs.getAppLanguage()) }
    var autoUpdateEnabled by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.AUTO_UPDATE_ENABLED)) }
    var checkCiUpdates by remember { mutableStateOf(settingsPrefs.get(SettingsKeys.CHECK_CI_UPDATES)) }
    var updateTimeoutMs by remember { mutableIntStateOf(settingsPrefs.get(SettingsKeys.UPDATE_TIMEOUT_MS)) }

    CollapsibleScaffold(
        title = stringResource(R.string.config_title),
        onBack = onBack,
    ) { topInset, scrollModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(scrollModifier)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Spacer(modifier = Modifier.height(topInset + 8.dp))
            SettingsGroup {
                // ── WebVPN 设置 ──
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.VpnKey,
                        title = stringResource(R.string.webvpn_settings_title),
                        subtitle = stringResource(R.string.webvpn_settings_desc),
                        onClick = onNavigateToWebVpn,
                    )
                }
                // ── 浏览器标识 ──
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Smartphone,
                        title = stringResource(R.string.config_user_agent),
                        subtitle = stringResource(R.string.config_user_agent_desc),
                        onClick = onNavigateToUserAgent,
                    )
                }
                // ── 语言 ──
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.language_title),
                        subtitle = if (currentLanguage == AppLanguage.SYSTEM) {
                            stringResource(R.string.language_system)
                        } else {
                            stringResource(currentLanguage.labelRes)
                        },
                        onClick = { showLanguageSheet = true },
                    )
                }
                // ── 日志等级 ──
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.config_log_level),
                        subtitle = stringResource(R.string.config_log_level_desc),
                        onClick = { showLogLevelSheet = true },
                    )
                }
                // ── 清除存储空间 ──
                item {
                    SettingsNavigationRow(
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
            SettingsGroup {
                item {
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
                }
                item {
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
                }
                item {
                    SettingsGroupBlock {
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
                                valueRange =
                                    MIN_UPDATE_TIMEOUT_MS / 1000f..MAX_UPDATE_TIMEOUT_MS / 1000f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 语言选择 BottomSheet（复用 LanguageSwitchSheet）──
    LanguageSwitchSheet(showSheet = showLanguageSheet, onDismiss = { showLanguageSheet = false })
    LogLevelSheet(showSheet = showLogLevelSheet, onDismiss = { showLogLevelSheet = false })
}
