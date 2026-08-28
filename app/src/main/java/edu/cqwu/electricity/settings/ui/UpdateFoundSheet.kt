package edu.cqwu.electricity.settings.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.BottomSheetDialog
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.update.data.UpdateChannel
import edu.cqwu.electricity.update.data.UpdateDownloadProbe
import edu.cqwu.electricity.update.data.UpdateDownloadProbeResult
import edu.cqwu.electricity.update.data.UpdateInfo
import edu.cqwu.electricity.update.data.UpdateMirrorSources

@Composable
fun UpdateFoundSheet(
    info: UpdateInfo,
    channel: UpdateChannel,
    isSkipped: Boolean,
    onSkipChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    var showDownloadSourceSheet by remember { mutableStateOf(false) }
    var skipThisVersion by remember(info) { mutableStateOf(isSkipped) }

    fun toggleSkipThisVersion() {
        val newValue = !skipThisVersion
        skipThisVersion = newValue
        onSkipChange(newValue)
    }

    BottomSheetDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.update_available_title),
        icon = Icons.Outlined.SystemUpdate,
        contentModifier = Modifier.height(screenHeight * 0.7f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
        // ── 更新信息与说明（可滚动区）──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        val channelLabel = if (channel == UpdateChannel.CI) {
            stringResource(R.string.update_channel_ci)
        } else {
            stringResource(R.string.update_channel_stable)
        }
        UpdateInfoRow(
            label = stringResource(R.string.update_channel_label),
            value = channelLabel,
        )
        UpdateInfoRow(
            label = stringResource(R.string.update_current_version),
            value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        UpdateInfoRow(
            label = stringResource(R.string.update_new_version),
            value = buildString {
                append("v")
                append(info.app.version ?: info.app.versionCode)
                append(" (")
                append(info.app.versionCode)
                append(")")
            },
        )
        info.app.extra?.packageSize?.let { size ->
            UpdateInfoRow(
                label = stringResource(R.string.update_size),
                value = Formatter.formatShortFileSize(context, size),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.update_changes_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = info.app.note?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.update_no_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }

        // ── 底部固定操作区（原 bottomBar）──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toggleSkipThisVersion() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = skipThisVersion,
                    onCheckedChange = { toggleSkipThisVersion() },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.update_skip_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = { showDownloadSourceSheet = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.update_download))
                }
            }
        }
        }

        val downloadLinks = remember(info) {
            UpdateMirrorSources.downloadLinks(info.app.link.orEmpty())
        }
        if (downloadLinks.isNotEmpty()) {
            BottomSheetDialogV2(
                visible = showDownloadSourceSheet,
                onDismissRequest = { showDownloadSourceSheet = false },
                title = stringResource(R.string.update_download_source_title),
            ) {
                var probeResults by remember(downloadLinks) {
                    mutableStateOf<Map<String, UpdateDownloadProbeResult>>(emptyMap())
                }
                LaunchedEffect(downloadLinks) {
                    probeResults = UpdateDownloadProbe.probe(downloadLinks)
                }
                downloadLinks.forEach { link ->
                    val probe = probeResults[link.url]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDownloadSourceSheet = false
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                    onDismiss()
                                } catch (e: Exception) {
                                    snackbar.show(resources.getString(R.string.common_no_browser))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = link.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        when {
                            probe == null -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            probe.ok -> {
                                Text(
                                    text = stringResource(
                                        R.string.update_download_latency,
                                        probe.latencyMs ?: 0L,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> {
                                Text(
                                    text = stringResource(R.string.update_download_probe_failed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
