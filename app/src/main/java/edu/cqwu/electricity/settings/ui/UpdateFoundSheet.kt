package edu.cqwu.electricity.settings.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.update.data.UpdateChannel
import edu.cqwu.electricity.update.data.UpdateDownloadLinks
import edu.cqwu.electricity.update.data.UpdateInfo

@Composable
fun UpdateFoundSheet(
    info: UpdateInfo,
    channel: UpdateChannel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    var showDownloadSourceSheet by remember { mutableStateOf(false) }

    BottomSheetDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.update_available_title),
        icon = Icons.Outlined.SystemUpdate,
        leadingButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
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
        Spacer(modifier = Modifier.height(16.dp))
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

        val downloadLinks = remember(info) {
            info.app.link?.let { UpdateDownloadLinks.create(it) }.orEmpty()
        }
        if (downloadLinks.isNotEmpty()) {
            BottomSheetDialog(
                visible = showDownloadSourceSheet,
                onDismissRequest = { showDownloadSourceSheet = false },
                title = stringResource(R.string.update_download_source_title),
            ) {
                downloadLinks.forEach { link ->
                    BottomSheetItem(
                        icon = Icons.Outlined.FileDownload,
                        title = stringResource(link.labelRes),
                        onClick = {
                            showDownloadSourceSheet = false
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                onDismiss()
                            } catch (e: Exception) {
                                snackbar.show(resources.getString(R.string.common_no_browser))
                            }
                        },
                    )
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
