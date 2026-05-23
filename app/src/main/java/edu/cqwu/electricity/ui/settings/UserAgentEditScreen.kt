package edu.cqwu.electricity.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.network.UserAgentEntry
import edu.cqwu.electricity.data.network.UserAgentProvider
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import java.util.UUID

/**
 * 编辑/添加浏览器标识页。
 *
 * 两种模式：
 * - 添加模式：entryId = "new"，所有字段为空
 * - 编辑模式：entryId 为已有条目的 ID，填充已有数据
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgentEditScreen(
    entryId: String,
    onBack: () -> Unit,
) {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val isNew = entryId == "new"

    // 编辑模式下加载已有条目
    val existingEntry = remember(entryId) {
        if (!isNew) UserAgentProvider.getEntryById(entryId) else null
    }

    var userAgent by remember { mutableStateOf(existingEntry?.userAgent ?: "") }
    var note by remember { mutableStateOf(existingEntry?.note ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = userAgent.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNew) "添加浏览器标识" else "编辑浏览器标识",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    // 编辑模式下且非内置预设时显示删除按钮
                    if (!isNew && existingEntry != null && !existingEntry.isBuiltin) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 用 Column + weight 让 Button 贴着底部
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // 可滚动的输入区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ── 备注输入框 ──
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("添加备注") },
                        placeholder = { Text("例如：学校平板") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── 浏览器标识输入框 ──
                    TextField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        label = { Text("输入浏览器标识") },
                        placeholder = { Text("Mozilla/5.0 ...") },
                        minLines = 4,
                        maxLines = 8,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── 保存按钮（右下角） ──
                Button(
                    onClick = {
                        if (!canSave) return@Button
                        if (isNew) {
                            // 添加模式
                            val newEntry = UserAgentEntry(
                                id = UUID.randomUUID().toString(),
                                name = note.ifBlank { "自定义标识" },
                                userAgent = userAgent.trim(),
                                note = note,
                                isBuiltin = false,
                            )
                            UserAgentProvider.addCustomEntry(newEntry)
                            // 自动选中新添加的条目
                            UserAgentProvider.setSelectedId(newEntry.id)
                        } else if (existingEntry != null) {
                            // 编辑模式
                            val updated = existingEntry.copy(
                                name = note.ifBlank { existingEntry.name },
                                userAgent = userAgent.trim(),
                                note = note,
                            )
                            UserAgentProvider.updateCustomEntry(updated)
                        }
                        onBack()
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 16.dp),
                    enabled = canSave,
                ) {
                    Text("保存")
                }
            }
        }
    }

    // ── 删除确认对话框 ──
    if (showDeleteDialog && existingEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "删除浏览器标识") },
            text = {
                Text(text = "确定要删除\"${existingEntry.name}\"吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    UserAgentProvider.removeCustomEntry(existingEntry.id)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
