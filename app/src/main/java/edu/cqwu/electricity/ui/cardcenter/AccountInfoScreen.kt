package edu.cqwu.electricity.ui.cardcenter

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.model.AccountInfo
import edu.cqwu.electricity.data.network.electricity.ElectricityApi
import edu.cqwu.electricity.data.network.auth.SessionExpiredException
import edu.cqwu.electricity.ui.components.ReLoginContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 账户信息页面 — 本地化 UI
 *
 * 通过 HTTP 请求获取 EPay 账户信息 HTML，解析后以原生卡片布局展示。
 * 支持下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var accountInfo by remember { mutableStateOf<AccountInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requiresReLogin by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val api = remember { ElectricityApi() }

    fun loadAccountInfo(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) isRefreshing = true else isLoading = true
            errorMessage = null
            requiresReLogin = false

            val result = withContext(Dispatchers.IO) {
                api.fetchAccountInfo()
            }

            result.onSuccess { info ->
                accountInfo = info
                isLoading = false
                isRefreshing = false
            }.onFailure { error ->
                isLoading = false
                isRefreshing = false
                if (error is SessionExpiredException) {
                    requiresReLogin = true
                    errorMessage = "登录已过期，请重新登录"
                } else {
                    errorMessage = error.message ?: "获取账户信息失败"
                }
            }
        }
    }

    // 首次加载
    LaunchedEffect(Unit) {
        loadAccountInfo()
    }

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.card_account_info_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadAccountInfo(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.card_fetching_account),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                errorMessage != null -> {
                    ReLoginContent(
                        errorMessage = errorMessage,
                        requiresReLogin = requiresReLogin,
                        onReLogin = onReLogin,
                        onRetry = { loadAccountInfo() },
                    )
                }

                accountInfo != null -> {
                    AccountInfoContent(
                        info = accountInfo!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * 账户信息内容区 — 两个卡片：基本信息 + 扩展信息
 */
@Composable
private fun AccountInfoContent(
    info: AccountInfo,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 基本信息卡片 ──
        item(key = "basic_info") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(
                        label = stringResource(R.string.card_account_info_name),
                        value = info.name,
                        icon = Icons.Filled.Person
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(
                        label = stringResource(R.string.card_account_info_student_id),
                        value = info.studentId,
                        icon = Icons.Filled.Badge
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(
                        label = stringResource(R.string.card_account_info_balance),
                        value = info.balance,
                        icon = Icons.Filled.Payments,
                        isHighlight = true
                    )
                }
            }
        }

        // ── 扩展信息卡片 ──
        item(key = "extra_info") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(
                        label = stringResource(R.string.card_account_info_school),
                        value = info.school.ifBlank { "-" },
                        icon = Icons.Filled.School
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(
                        label = stringResource(R.string.card_account_info_major),
                        value = info.major.ifBlank { "-" },
                        icon = Icons.Filled.LibraryBooks
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(
                        label = stringResource(R.string.card_account_info_class),
                        value = info.className.ifBlank { "-" },
                        icon = Icons.Filled.Groups
                    )
                }
            }
        }
    }
}

/**
 * 信息行：图标 + 标签 + 值
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlight)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}
