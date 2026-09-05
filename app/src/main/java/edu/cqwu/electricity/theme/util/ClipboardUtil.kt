package edu.cqwu.electricity.theme.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.SnackbarController

/**
 * 将文本复制到系统剪贴板并显示成功提示。
 *
 * @param context 用于获取剪贴板服务
 * @param text 待复制内容
 * @param label 剪贴板条目描述（如导出标题）
 * @param snackbar 提示控制器（弹出"已复制到剪贴板"）
 */
fun copyToClipboard(
    context: Context,
    text: String,
    label: String,
    snackbar: SnackbarController
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    snackbar.show(context.getString(R.string.common_copied_to_clipboard), ToastUtils.Type.SUCCESS)
}
