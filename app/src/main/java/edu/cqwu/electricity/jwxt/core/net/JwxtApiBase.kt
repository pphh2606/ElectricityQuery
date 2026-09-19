package edu.cqwu.electricity.jwxt.core.net

/**
 * 各功能 Api 的公共基类。
 *
 * 只做一件事：把 [JwxtHttpClient] 的能力以 `protected` 形式交给子类，让子类的接口方法能直接写
 * `request(...)` / `gson` / `checkBusinessCode(...)`，不必每个类都重复转发一遍。
 */
abstract class JwxtApiBase(
    protected val http: JwxtHttpClient = JwxtHttpClient(),
) {

    /** JSON 解析器（含课表详情的 null 安全反序列化器），由 [JwxtHttpClient] 统一持有 */
    protected val gson get() = http.gson

    /** 发一次请求（含 401 换票据重试与错误归类） */
    protected suspend fun <T> request(
        path: String,
        isPost: Boolean,
        jsonBody: String? = null,
        parse: (String) -> T,
    ): Result<T> = http.request(path, isPost, jsonBody, parse)

    /** 业务码校验：401 清 token 并抛会话过期，其余非 200 视为业务失败 */
    protected fun checkBusinessCode(code: Int, msg: String, path: String) =
        http.checkBusinessCode(code, msg, path)
}
