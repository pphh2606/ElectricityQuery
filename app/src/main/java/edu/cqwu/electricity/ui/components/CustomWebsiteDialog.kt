package edu.cqwu.electricity.ui.components

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 自定义网站弹窗，让用户选择本地图标、输入标题和网址。
 *
 * @param onDismiss 关闭弹窗
 * @param onConfirm 确认时的回调 (title, url, iconUri)
 */
@Composable
fun CustomWebsiteDialog(
    visible: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, iconUri: String?) -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // 系统图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        iconUri = uri
    }

    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.custom_website_title),
        icon = Icons.Outlined.Language,
        leadingButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        trailingButton = {
            TextButton(
                onClick = {
                    var hasError = false
                    if (titleInput.isBlank()) {
                        hasError = true
                    }
                    if (!isValidUrl(urlInput)) {
                        hasError = true
                    }
                    if (!hasError) {
                        onConfirm(titleInput.trim(), normalizeUrl(urlInput), iconUri?.toString())
                    }
                }
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    ) {
        // ── 图标选择器 ──
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (iconUri != null) {
                val context = LocalContext.current
                val request = remember(iconUri) {
                    ImageRequest.Builder(context)
                        .data(iconUri)
                        .size(128)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.custom_icon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.custom_website_choose_icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.custom_website_choose_icon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 标题输入 ──
        TextField(
            value = titleInput,
            onValueChange = {
                titleInput = it
                titleError = null
            },
            label = { Text(stringResource(R.string.custom_website_label)) },
            placeholder = { Text(stringResource(R.string.custom_website_label_placeholder)) },
            singleLine = true,
            isError = titleError != null,
            supportingText = titleError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 网址输入 ──
        TextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                urlError = null
            },
            label = { Text(stringResource(R.string.custom_website_url_label)) },
            placeholder = { Text(stringResource(R.string.custom_website_url_placeholder)) },
            singleLine = true,
            isError = urlError != null,
            supportingText = urlError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (titleInput.isBlank()) {
                        titleError = context.getString(R.string.custom_website_title_required)
                    } else if (isValidUrl(urlInput)) {
                        onConfirm(titleInput.trim(), normalizeUrl(urlInput), iconUri?.toString())
                    } else {
                        urlError = context.getString(R.string.custom_website_url_invalid)
                    }
                }
            )
        )
    }
}

private fun isValidUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val normalized = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        "https://$url"
    } else {
        url
    }
    return try {
        val uri = java.net.URI(normalized)
        uri.host != null && uri.host.contains(".")
    } catch (_: Exception) {
        false
    }
}

private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        "https://$trimmed"
    } else {
        trimmed
    }
}
