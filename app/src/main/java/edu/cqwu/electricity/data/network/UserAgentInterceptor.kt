package edu.cqwu.electricity.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 拦截器，自动将请求的 User-Agent header 替换为 [UserAgentProvider] 中用户选中的值。
 *
 * 使用方式：
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(UserAgentInterceptor)
 *     .build()
 * ```
 */
object UserAgentInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val ua = UserAgentProvider.getActiveUserAgent()
        val newRequest = request.newBuilder()
            .header("User-Agent", ua)
            .build()
        return chain.proceed(newRequest)
    }
}
