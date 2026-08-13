package edu.cqwu.electricity.settings.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalSnackbarController

/**
 * 关于页面
 *
 * 显示应用图标、名称、版本号以及项目主页/开发者联系方式等信息，
 * 与其他设置页面风格一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = currentTopBarColors()
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
                        icon = Icons.Outlined.Code,
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
                                snackbar.show(resources.getString(R.string.common_no_browser))
                            }
                        },
                    )

                    // 构建信息
                    val isCiBuild = BuildConfig.BUILD_SOURCE == "github-actions"
                    AboutEntry(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.about_build_info),
                        subtitle = resources.getString(R.string.about_build_time, BuildConfig.BUILD_TIME) + "\n" +
                            resources.getString(R.string.about_commit_hash, BuildConfig.GIT_COMMIT_HASH) + "\n" +
                            resources.getString(R.string.about_source, if (isCiBuild) resources.getString(R.string.about_source_github) else resources.getString(R.string.about_source_local)),
                        onClick = {
                            if (isCiBuild) {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/pphh2606/ElectricityQuery/commit/${BuildConfig.GIT_COMMIT_HASH}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    snackbar.show(resources.getString(R.string.common_no_browser))
                                }
                            }
                        },
                    )

                    // 开发者
                    AboutEntry(
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.about_developer),
                        subtitle = "pphh2606",
                        onClick = {
                            // 预留：可跳转开发者主页
                        },
                    )

                    // 联系方式（点击弹出底部选择弹窗）
                    AboutEntry(
                        icon = Icons.AutoMirrored.Outlined.Chat,
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
    BottomSheetDialog(
        visible = showContactSheet,
        onDismissRequest = { showContactSheet = false },
        title = stringResource(R.string.about_contact_title),
    ) {
            // QQ
            BottomSheetItem(
                icon = Icons.AutoMirrored.Outlined.Chat,
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
                        snackbar.show(resources.getString(R.string.about_install_qq))
                    }
                },
            )

            // 哔哩哔哩（复制 UID）
            BottomSheetItem(
                icon = Icons.Outlined.VideogameAsset,
                title = stringResource(R.string.about_contact_bilibili),
                onClick = {
                    showContactSheet = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Bilibili UID", "1858606373"))
                    snackbar.show(resources.getString(R.string.common_copied_to_clipboard))
                },
            )

            // 邮箱
            BottomSheetItem(
                icon = Icons.Outlined.Email,
                title = stringResource(R.string.about_contact_email),
                onClick = {
                    showContactSheet = false
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:2606841932@qq.com")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        snackbar.show(resources.getString(R.string.about_no_mail_app))
                    }
                },
            )
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
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}
