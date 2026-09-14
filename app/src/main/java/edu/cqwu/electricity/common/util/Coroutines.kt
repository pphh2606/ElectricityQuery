package edu.cqwu.electricity.common.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] 的协程安全版本。
 *
 * 协程取消（[CancellationException]）必须原样抛出：被 [runCatching] 吞掉后，协程会带着"业务失败"
 * 的假象继续执行（页面都已销毁还去更新状态），取消语义也就失效了。业务异常照常收敛为 [Result.failure]。
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
