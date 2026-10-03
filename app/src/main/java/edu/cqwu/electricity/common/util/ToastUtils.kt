package edu.cqwu.electricity.common.util

/**
 * 提示类型枚举：SUCCESS / ERROR。
 *
 * 作为 `SnackbarController.show(...)` 的类型参数使用，不同类型对应不同的背景色。
 */
object ToastUtils {

    enum class Type {
        SUCCESS,
        ERROR,

        /** 中性提示（如「允许网站打开「XX」吗？」的询问）：既不代表成功也不代表错误 */
        INFO
    }
}
