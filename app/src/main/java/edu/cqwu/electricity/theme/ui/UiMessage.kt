package edu.cqwu.electricity.theme.ui

import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * 面向界面的消息：优先显示 [raw] 原样文案（服务器/技术返回，不参与翻译），
 * 否则用 [res]（本地资源，支持格式化参数 [args]）翻译。
 */
data class UiMessage(
    @StringRes val res: Int = 0,
    val args: List<Any> = emptyList(),
    val raw: String? = null,
)

fun UiMessage.resolve(resources: Resources): String = when {
    !raw.isNullOrBlank() -> raw
    res != 0 -> resources.getString(res, *args.toTypedArray())
    else -> ""
}
