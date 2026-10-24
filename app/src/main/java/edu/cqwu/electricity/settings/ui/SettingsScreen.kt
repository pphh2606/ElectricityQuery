package edu.cqwu.electricity.settings.ui


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 设置页面 — 与项目其他页面统一风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val nav = LocalNavController.current
    CollapsibleScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { topInset, scrollModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(scrollModifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(topInset + 8.dp))
            SettingsGroup {
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.ManageAccounts,
                        title = stringResource(R.string.settings_account_manager),
                        subtitle = stringResource(R.string.settings_account_manager_desc),
                        onClick = { nav.navigate(Routes.ACCOUNT_MANAGER) },
                    )
                }
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.settings_personalization),
                        subtitle = stringResource(R.string.settings_personalization_desc),
                        onClick = { nav.navigate(Routes.PERSONALIZATION) },
                    )
                }
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.settings_config),
                        subtitle = stringResource(R.string.settings_config_desc),
                        onClick = { nav.navigate(Routes.CONFIG) },
                    )
                }
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.settings_backup_restore),
                        subtitle = stringResource(R.string.settings_backup_restore_desc),
                        onClick = { nav.navigate(Routes.SETTINGS_BACKUP_RESTORE) },
                    )
                }
                item {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_desc),
                        onClick = { nav.navigate(Routes.ABOUT) },
                    )
                }
            }
        }
    }
}
