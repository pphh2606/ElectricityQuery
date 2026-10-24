package edu.cqwu.electricity.feeservicehall.ui

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.settings.ui.SettingsGroup
import edu.cqwu.electricity.settings.ui.SettingsNavigationRow
import edu.cqwu.electricity.settings.ui.SettingsGroup
import edu.cqwu.electricity.settings.ui.SettingsNavigationRow

/** 功能菜单项数据 */
private data class ProfileMenuItem(
    val icon: ImageVector,
    @androidx.annotation.StringRes val titleRes: Int,
    val url: String,
)

/** 第一组菜单：账户服务 */
private val accountServiceItems = listOf(
    ProfileMenuItem(Icons.Outlined.Lock, R.string.fee_profile_pwd_change, "https://pay.cqwu.edu.cn/mobile/#/password"),
    ProfileMenuItem(Icons.Outlined.Description, R.string.fee_profile_invoice_mgmt, "https://pay.cqwu.edu.cn/mobile/#/invoiceTitleList"),
    ProfileMenuItem(Icons.Outlined.BarChart, R.string.fee_profile_fun_bill, "https://pay.cqwu.edu.cn/mobile/#/more"),
)

/** 第二组菜单：查询服务 */
private val queryServiceItems = listOf(
    ProfileMenuItem(Icons.Outlined.Search, R.string.fee_profile_query_pay, "https://pay.cqwu.edu.cn/mobile/#/searchPay"),
    ProfileMenuItem(Icons.Outlined.Receipt, R.string.fee_profile_view_invoice, "https://pay.cqwu.edu.cn/mobile/#/billShowList"),
    ProfileMenuItem(Icons.AutoMirrored.Outlined.Send, R.string.fee_profile_advance_invoice, "https://pay.cqwu.edu.cn/mobile/#/advanceBilling"),
)

/**
 * "我的" Tab 内容
 *
 * 上半部分：个人信息卡片（账号、姓名、学院）
 * 下半部分：功能选项（修改密码、发票抬头管理、趣味账单等）
 */
@Composable
internal fun FeeServiceHallProfileTab(
    uiState: FeeServiceHallUiState,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onReLogin: () -> Unit,
    onRetryProfile: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(topInset))
        ProfileInfoCard(uiState, onReLogin, onRetryProfile)

        Spacer(Modifier.height(16.dp))

        ProfileMenuGroup(accountServiceItems, onNavigateToWebView)
        Spacer(Modifier.height(16.dp))
        ProfileMenuGroup(queryServiceItems, onNavigateToWebView)
    }
}

@Composable
private fun ProfileMenuGroup(
    items: List<ProfileMenuItem>,
    onNavigateToWebView: (url: String, title: String) -> Unit,
) {
    SettingsGroup {
        items.forEach { item ->
            item {
                val itemTitle = stringResource(item.titleRes)
                SettingsNavigationRow(
                    icon = item.icon,
                    title = itemTitle,
                    onClick = { onNavigateToWebView(item.url, itemTitle) },
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(
    uiState: FeeServiceHallUiState,
    onReLogin: () -> Unit,
    onRetryProfile: () -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        when {
            uiState.isProfileLoading -> {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.profileError != null -> {
                ReLoginContent(
                    errorMessage = stringResource(R.string.fee_profile_load_failed, uiState.profileError ?: ""),
                    requiresReLogin = uiState.profileRequiresReLogin,
                    onReLogin = onReLogin,
                    onRetry = onRetryProfile,
                )
            }
            else -> {
                val profile = uiState.profile
                val initial = profile?.name?.firstOrNull()?.toString() ?: "?"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = initial, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        InfoRow(label = stringResource(R.string.fee_profile_account), value = profile?.accountNum ?: "-")
                        Spacer(Modifier.height(4.dp))
                        InfoRow(label = stringResource(R.string.fee_profile_name), value = profile?.name ?: "-")
                        Spacer(Modifier.height(4.dp))
                        InfoRow(label = stringResource(R.string.fee_profile_dept), value = profile?.deptName ?: "-")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
