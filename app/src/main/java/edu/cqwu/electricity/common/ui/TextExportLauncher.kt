package edu.cqwu.electricity.common.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.util.ToastUtils
import edu.cqwu.electricity.theme.ui.LocalSnackbarController

/** 「把一段文本存成文件」的入口，由 [rememberTextExportLauncher] 创建 */
@Stable
class TextExportLauncher internal constructor(
    private val onLaunch: (fileName: String, text: String, title: String) -> Unit,
) {
    /**
     * 打开系统文件选择器；用户选好位置后写入 [text]，失败弹 Snackbar。
     *
     * @param fileName 选择器里的默认文件名
     * @param text 要写入的纯文本
     * @param title 导出内容的名字，只用于成功/失败提示
     */
    fun launch(fileName: String, text: String, title: String) = onLaunch(fileName, text, title)
}

/**
 * 记录页的导出流程：先记住待写内容，等用户选好文件位置再写入。
 *
 * 电费四个记录页的这段逻辑原本各抄一份（生成文本 → `CreateDocument` → 写入 → Snackbar），
 * 抽出来既省重复，也保证失败提示与编码处理只有一处。文本本身仍由调用方生成。
 */
@Composable
fun rememberTextExportLauncher(): TextExportLauncher {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current

    var pendingText by remember { mutableStateOf("") }
    var pendingTitle by remember { mutableStateOf("") }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingText.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingText.toByteArray(Charsets.UTF_8))
                }
                snackbar.show(resources.getString(R.string.common_export_success, pendingTitle), ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show(resources.getString(R.string.common_export_failed, e.message ?: ""), ToastUtils.Type.ERROR)
            }
            pendingText = ""
            pendingTitle = ""
        }
    }

    return remember(saveFileLauncher) {
        TextExportLauncher { fileName, text, title ->
            pendingText = text
            pendingTitle = title
            saveFileLauncher.launch(fileName)
        }
    }
}
