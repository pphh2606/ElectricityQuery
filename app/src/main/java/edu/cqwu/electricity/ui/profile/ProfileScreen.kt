package edu.cqwu.electricity.ui.profile

import android.util.Log
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import edu.cqwu.electricity.data.local.NightMode
import edu.cqwu.electricity.ui.navigation.Routes
import edu.cqwu.electricity.ui.theme.LocalNavController
import edu.cqwu.electricity.ui.theme.LocalNightModeState
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.network.auth.AccountManager
import edu.cqwu.electricity.data.network.common.WebVpnEncoder
import edu.cqwu.electricity.ui.components.OpenUrlDialog
import edu.cqwu.electricity.ui.login.AccountManagerSheet

/**
 * 「我的」页面 TopAppBar，由 [MainTabScreen] 在 Scaffold.topBar 中按页面切换调用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar() {
    val nav = LocalNavController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.profile_title),
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
                    NightMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                    NightMode.LIGHT -> Icons.Outlined.LightMode
                    NightMode.DARK -> Icons.Outlined.DarkMode
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.profile_toggle_night_mode),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { nav.navigate(Routes.SETTINGS) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.common_settings),
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
fun ProfilePageContent() {
    val _profilePerfStart = System.currentTimeMillis()
    androidx.compose.runtime.SideEffect {
        android.util.Log.d("TabPerf", "ProfilePageContent composition done, elapsed=${System.currentTimeMillis() - _profilePerfStart}ms")
    }
    val nav = LocalNavController.current
    val context = LocalContext.current
    var showOpenUrlDialog by remember { mutableStateOf(false) }
    var showAccountManagerSheet by remember { mutableStateOf(false) }

    // 响应式刷新用户名：页面从后台恢复时重新读取（支持切换账号后自动更新）
    var usernameRefreshKey by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usernameRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val username = remember(usernameRefreshKey) {
        val t0 = System.currentTimeMillis()
        val result = AccountManager.getActiveUser()
            ?: AccountStore.getInstance(context).getAllAccountNames().firstOrNull()
        android.util.Log.d("TabPerf", "ProfilePageContent username lookup cost=${System.currentTimeMillis() - t0}ms")
        result
    }

    val isLoggedIn = username != null
    val avatarText = if (isLoggedIn && username.isNotEmpty()) {
        username.first().toString()
    } else {
        "?"
    }
    val displayStudentId = username ?: stringResource(R.string.profile_not_logged_in)

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
                        nav.navigate(Routes.MY_INFO)
                    } else {
                        nav.navigate(Routes.LOGIN)
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

                // ── 中间：姓名（可选）+ 学号 ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = displayStudentId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                        onClick = { showAccountManagerSheet = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.profile_manage_accounts),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isLoggedIn) {
                            nav.navigate(Routes.MY_INFO)
                        } else {
                            nav.navigate(Routes.LOGIN)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (isLoggedIn) stringResource(R.string.profile_my_info_cd) else null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ── 功能入口 ──
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                ProfileEntry(
                    icon = Icons.Outlined.OpenInBrowser,
                    title = stringResource(R.string.profile_open_url),
                    onClick = { showOpenUrlDialog = true },
                )
                ProfileEntry(
                    icon = Icons.Outlined.Feedback,
                    title = stringResource(R.string.profile_feedback),
                    onClick = { nav.navigate(Routes.FEEDBACK) },
                )
                ProfileEntry(
                    icon = Icons.Outlined.AddToHomeScreen,
                    title = stringResource(R.string.profile_add_shortcut),
                    onClick = { nav.navigate(Routes.ADD_SHORTCUT) },
                )
            }
        }

        // ── 底部区域（占位） ──
        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── 账号管理弹窗 ──
    AccountManagerSheet(
        show = showAccountManagerSheet,
        onDismiss = { showAccountManagerSheet = false },
        onNavigateToLogin = {
            showAccountManagerSheet = false
            nav.navigate(Routes.LOGIN)
        },
        onNavigateToAddAccount = {
            showAccountManagerSheet = false
            nav.navigate(Routes.NEW_ACCOUNT_LOGIN)
        },
        onSwitchSuccess = {
            // 切换成功后刷新用户名（Lifecycle ON_RESUME 已自动处理，
            // 此处额外 +1 确保即时更新）
            usernameRefreshKey++
        },
    )

    // ── 打开网址对话框 ──
    if (showOpenUrlDialog) {
        OpenUrlDialog(
            onDismiss = { showOpenUrlDialog = false },
            onConfirm = { url, isInternal ->
                showOpenUrlDialog = false
                val finalUrl = if (isInternal) {
                    try {
                        WebVpnEncoder.transform(url)
                    } catch (e: Exception) {
                        Log.w("ProfileScreen", "WebVpnEncoder.transform failed for: $url", e)
                        url
                    }
                } else {
                    url
                }
                nav.navigate(Routes.unifiedWebViewRoute(finalUrl, context.getString(R.string.profile_open_url)))
            }
        )
    }
}

/**
 * 个人页面功能入口条目，与 SettingsScreen 的 SettingsEntry 风格一致。
 */
@Composable
private fun ProfileEntry(
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

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

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
