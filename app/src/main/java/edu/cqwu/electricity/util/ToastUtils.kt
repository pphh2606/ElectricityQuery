package edu.cqwu.electricity.util

/**
 * Toast 类型枚举
 *
 * 为 Compose 版 [edu.cqwu.electricity.ui.components.ToastOverlay] 提供 SUCCESS/ERROR 类型区分，
 * 每个类型对应不同的背景颜色。
 */
object ToastUtils {

    enum class Type {
        SUCCESS,
        ERROR
    }
}
