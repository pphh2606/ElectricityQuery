package edu.cqwu.electricity.common.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * 用系统能力打开链接或自定义 Scheme。
 *
 * 返回 false 表示设备上没有能处理它的 Activity（未安装对应 App、无浏览器等），
 * 由调用方决定提示文案；这里不吞异常后继续，也不做重试。
 */
fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    true
} catch (_: ActivityNotFoundException) {
    false
}
