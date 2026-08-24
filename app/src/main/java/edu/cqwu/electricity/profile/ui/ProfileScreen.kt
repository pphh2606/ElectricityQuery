package edu.cqwu.electricity.profile.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import edu.cqwu.electricity.logging.AppLog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.network.WebVpnEncoder
import edu.cqwu.electricity.settings.data.NightMode
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.OpenUrlDialog

/**
 * 「我的」页面 TopAppBar，由 [MainTabScreen] 在 Scaffold.topBar 中按页面切换调用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar() {
    val nav = LocalNavController.current
    val topBarColors = currentTopBarColors()

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.profile_title),
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            val appSettings = LocalAppSettingsState.current

            IconButton(onClick = {
                val modes = NightMode.entries
                val nextIndex = (appSettings.nightMode.ordinal + 1) % modes.size
                appSettings.updateNightMode(modes[nextIndex])
            }) {
                val modes = NightMode.entries
                val nextIndex = (appSettings.nightMode.ordinal + 1) % modes.size
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
fun ProfilePageContent(
    onOpenHalfScreen: (url: String, title: String) -> Unit = { _, _ -> },
) {
    val _profilePerfStart = System.currentTimeMillis()
    androidx.compose.runtime.SideEffect {
        AppLog.d("TabPerf", "ProfilePageContent composition done, elapsed=${System.currentTimeMillis() - _profilePerfStart}ms")
    }
    val nav = LocalNavController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    var showOpenUrlDialog by remember { mutableStateOf(false) }

    // 响应式刷新用户名：页面从后台恢复时重新读取（支持切换账号后自动更新）
    var usernameRefreshKey by remember { mutableIntStateOf(0) }
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
        val result = AccountSessionStore.getActiveAccount()?.username
        AppLog.d("TabPerf", "ProfilePageContent username lookup cost=${System.currentTimeMillis() - t0}ms")
        result
    }

    val isLoggedIn = username != null
    val avatarText = if (isLoggedIn && username.isNotEmpty()) {
        username.first().toString()
    } else {
        "?"
    }
    val displayStudentId = username ?: stringResource(R.string.profile_not_logged_in)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 用户信息卡片 ──
        item(key = "user_card") {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isLoggedIn) {
                            nav.navigate(Routes.MY_INFO)
                        } else {
                            nav.navigate(Routes.loginRoute())
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

                    // ── 右侧：编辑按钮（仅登录后显示，打开账号管理页）+ > 箭头 ──
                    if (isLoggedIn) {
                        IconButton(
                            onClick = { nav.navigate(Routes.ACCOUNT_MANAGER) },
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
                                nav.navigate(Routes.loginRoute())
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
        }

        // ── 功能入口 ──
        item(key = "menu") {
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
                        icon = Icons.AutoMirrored.Outlined.AddToHomeScreen,
                        title = stringResource(R.string.profile_add_shortcut),
                        onClick = { nav.navigate(Routes.ADD_SHORTCUT) },
                    )
                    ProfileEntry(
                        icon = Icons.Outlined.PersonSearch,
                        title = stringResource(R.string.person_search_title),
                        onClick = { nav.navigate(Routes.PERSON_SEARCH) },
                    )
                }
            }
        }
    }

    // ── 打开网址底部弹窗 ──
    OpenUrlDialog(
        visible = showOpenUrlDialog,
        onDismiss = { showOpenUrlDialog = false },
        onConfirm = { url, isInternal, useHalfScreen ->
            showOpenUrlDialog = false
            val finalUrl = if (isInternal) {
                try {
                    WebVpnEncoder.transform(url)
                } catch (e: Exception) {
                    AppLog.w("ProfileScreen", "WebVpnEncoder.transform failed for: $url", e)
                    url
                }
            } else {
                url
            }
            val title = resources.getString(R.string.profile_open_url)
            if (useHalfScreen) {
                onOpenHalfScreen(finalUrl, title)
            } else {
                nav.navigate(Routes.unifiedWebViewRoute(finalUrl, title))
            }
        }
    )
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
