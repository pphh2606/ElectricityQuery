package edu.cqwu.electricity.ui.feeservicehall

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp

/** 功能菜单项数据 */
private data class ProfileMenuItem(
    val icon: ImageVector,
    @androidx.annotation.StringRes val titleRes: Int,
    val url: String,
)

/** 第一组菜单：账户服务 */
private val accountServiceItems = listOf(
    ProfileMenuItem(Icons.Default.Lock, R.string.fee_profile_pwd_change, "https://pay.cqwu.edu.cn/mobile/#/password"),
    ProfileMenuItem(Icons.Default.Description, R.string.fee_profile_invoice_mgmt, "https://pay.cqwu.edu.cn/mobile/#/invoiceTitleList"),
    ProfileMenuItem(Icons.Default.BarChart, R.string.fee_profile_fun_bill, "https://pay.cqwu.edu.cn/mobile/#/more"),
)

/** 第二组菜单：查询服务 */
private val queryServiceItems = listOf(
    ProfileMenuItem(Icons.Default.Search, R.string.fee_profile_query_pay, "https://pay.cqwu.edu.cn/mobile/#/searchPay"),
    ProfileMenuItem(Icons.Default.Receipt, R.string.fee_profile_view_invoice, "https://pay.cqwu.edu.cn/mobile/#/billShowList"),
    ProfileMenuItem(Icons.AutoMirrored.Filled.Send, R.string.fee_profile_advance_invoice, "https://pay.cqwu.edu.cn/mobile/#/advanceBilling"),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ProfileInfoCard(uiState)

        Spacer(Modifier.height(16.dp))

        ProfileMenuCard(accountServiceItems, onNavigateToWebView)
        Spacer(Modifier.height(16.dp))
        ProfileMenuCard(queryServiceItems, onNavigateToWebView)
    }
}

/**
 * 功能菜单卡片（循环渲染）
 */
@Composable
private fun ProfileMenuCard(
    items: List<ProfileMenuItem>,
    onNavigateToWebView: (url: String, title: String) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val itemTitle = stringResource(item.titleRes)
            ProfileOptionItem(
                icon = item.icon,
                title = itemTitle,
                onClick = { onNavigateToWebView(item.url, itemTitle) },
            )
            if (index < items.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(uiState: FeeServiceHallUiState) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        when {
            uiState.isProfileLoading -> {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.profileError != null -> {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.fee_profile_load_failed, uiState.profileError ?: ""), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val profile = uiState.profile
                val initial = profile?.name?.firstOrNull()?.toString() ?: "?"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.Top,
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

@Composable
private fun ProfileOptionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
