package edu.cqwu.electricity.theme.ui

import android.content.res.Resources
import androidx.annotation.StringRes

data class UiMessage(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
)

fun UiMessage.resolve(resources: Resources): String =
    resources.getString(res, *args.toTypedArray())
