package edu.cqwu.electricity.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.R
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

/**
 * 关于页面
 *
 * 显示应用图标、名称、版本号以及项目主页/开发者/联系方式等信息，
 * 与其他设置页面风格一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val appName = stringResource(id = R.string.app_name)
    var showContactSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ─── 上方空白间距 ───
            Spacer(modifier = Modifier.height(64.dp))

            // ─── 应用图标 ───
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 应用名称 ───
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ─── 版本号（动态获取） ───
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ─── 选项列表卡片（与其他设置页风格一致） ───
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    // 项目主页
                    AboutEntry(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.about_project_home),
                        subtitle = "https://github.com/pphh2606/ElectricityQuery",
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/pphh2606/ElectricityQuery")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                snackbar.show(context.getString(R.string.common_no_browser))
                            }
                        },
                    )

                    // 构建信息
                    AboutEntry(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.about_build_info),
                        subtitle = "Build Time: ${BuildConfig.BUILD_TIME}\nCommit Hash: ${BuildConfig.GIT_COMMIT_HASH}",
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/pphh2606/ElectricityQuery/commit/${BuildConfig.GIT_COMMIT_HASH}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                snackbar.show(context.getString(R.string.common_no_browser))
                            }
                        },
                    )

                    // 开发者
                    AboutEntry(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.about_developer),
                        subtitle = "pphh2606",
                        onClick = {
                            // 预留：可跳转开发者主页
                        },
                    )

                    // 联系方式（点击弹出底部选择弹窗）
                    AboutEntry(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = stringResource(R.string.about_contact),
                        subtitle = null,
                        onClick = { showContactSheet = true },
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ─── 底部脚注 ───
            Text(
                text = stringResource(R.string.about_ai_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            // 底部留白
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ─── 联系方式底部弹窗 ───
    if (showContactSheet) {
        BottomSheetDialog(
            onDismissRequest = { showContactSheet = false },
            title = stringResource(R.string.about_contact_title),
        ) {
            // QQ
            BottomSheetItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(R.string.about_contact_qq),
                onClick = {
                    showContactSheet = false
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("mqq://card/show_pslcard?src_type=internal&version=1&uin=2606841932&card_type=person&source=sharecard")
                        )
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        snackbar.show(context.getString(R.string.about_install_qq))
                    }
                },
            )

            // 哔哩哔哩（复制 UID）
            BottomSheetItem(
                icon = Icons.Default.VideogameAsset,
                title = stringResource(R.string.about_contact_bilibili),
                onClick = {
                    showContactSheet = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Bilibili UID", "1858606373"))
                    snackbar.show(context.getString(R.string.common_copied_to_clipboard))
                },
            )

            // 邮箱
            BottomSheetItem(
                icon = Icons.Default.Email,
                title = stringResource(R.string.about_contact_email),
                onClick = {
                    showContactSheet = false
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:2606841932@qq.com")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        snackbar.show(context.getString(R.string.about_no_mail_app))
                    }
                },
            )
        }
    }
}

/**
 * 关于页面的条目行 — 与 [SettingsEntry] 样式保持一致
 */
@Composable
private fun AboutEntry(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * 选项间的分隔线
 */
@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
