package edu.cqwu.electricity.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import edu.cqwu.electricity.data.local.NightMode
import edu.cqwu.electricity.ui.theme.LocalNightModeState
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.data.network.WebVpnEncoder
import edu.cqwu.electricity.ui.components.OpenUrlDialog

/** 「我的信息」H5 页面 URL（与首页「我的信息」一致） */
private const val MY_INFO_URL = "https://cqwu.campusphere.net/wec-counselor-stuinfo-apps/student/mobile/index.html"
private const val MY_INFO_TITLE = "我的信息"

/**
 * 「我的」页面 TopAppBar，由 [MainTabScreen] 在 Scaffold.topBar 中按页面切换调用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar(
    onNavigateToSettings: () -> Unit = {},
) {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    TopAppBar(
        title = {
            Text(
                text = "我的",
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            val nightModeState = LocalNightModeState.current

            IconButton(onClick = {
                val modes = NightMode.entries
                val nextIndex = (nightModeState.nightMode.ordinal + 1) % modes.size
                nightModeState.onNightModeChange(modes[nextIndex])
            }) {
                val modes = NightMode.entries
                val nextIndex = (nightModeState.nightMode.ordinal + 1) % modes.size
                val nextMode = modes[nextIndex]
                val icon = when (nextMode) {
                    NightMode.SYSTEM -> Icons.Default.BrightnessAuto
                    NightMode.LIGHT -> Icons.Default.LightMode
                    NightMode.DARK -> Icons.Default.DarkMode
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "切换夜间模式",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onNavigateToSettings,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                )
            }
        },
        colors = topBarColors,
    )
}

/**
 * 「我的」页面内容（不含 Scaffold / TopAppBar / BottomBar），
 * 由 [MainTabScreen] 的 HorizontalPager 在 page 1 中调用。
 */
@Composable
fun ProfilePageContent(
    onNavigateToWebView: (url: String, title: String) -> Unit = { _, _ -> },
    onNavigateToLogin: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToMyInfo: () -> Unit = {},
) {
    val context = LocalContext.current
    var showOpenUrlDialog by remember { mutableStateOf(false) }

    // 获取当前登录学号：优先取内存中的活跃用户，其次取本地持久化的最近登录学号
    val username = remember {
        AccountManager.getActiveUser()
            ?: AccountStore(context).getAllAccountNames().firstOrNull()
    }

    val isLoggedIn = username != null
    val avatarText = if (isLoggedIn && username!!.isNotEmpty()) {
        username.first().toString()
    } else {
        "?"
    }
    val displayStudentId = username ?: "未登录"
    val displayName = "未设置" // 占位，后续与 API 联动

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // ── 用户信息卡片 ──
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isLoggedIn) {
                        onNavigateToMyInfo()
                    } else {
                        onNavigateToLogin()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── 左侧：圆形头像 ──
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ── 中间：姓名 + 学号 ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayStudentId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLoggedIn)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ── 右侧：编辑按钮（仅登录后显示）+ > 箭头 ──
                if (isLoggedIn) {
                    IconButton(
                        onClick = onNavigateToLogin,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "管理账号",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isLoggedIn) {
                            onNavigateToMyInfo()
                        } else {
                            onNavigateToLogin()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isLoggedIn) "我的信息" else null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ── 打开网址 ──
        Spacer(modifier = Modifier.height(16.dp))
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showOpenUrlDialog = true },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "打开网址",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "打开网址",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 意见反馈 ──
        Spacer(modifier = Modifier.height(16.dp))
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToFeedback() },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Feedback,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "意见反馈",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "意见反馈",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 底部区域（占位） ──
        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── 打开网址对话框 ──
    if (showOpenUrlDialog) {
        OpenUrlDialog(
            onDismiss = { showOpenUrlDialog = false },
            onConfirm = { url, isInternal ->
                showOpenUrlDialog = false
                val finalUrl = if (isInternal) {
                    try {
                        WebVpnEncoder.transform(url)
                    } catch (_: Exception) {
                        url
                    }
                } else {
                    url
                }
                onNavigateToWebView(finalUrl, "打开网址")
            }
        )
    }
}
