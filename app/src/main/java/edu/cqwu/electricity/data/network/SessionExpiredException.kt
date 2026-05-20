package edu.cqwu.electricity.data.network

/**
 * Session 过期异常。
 * 当 API 请求响应被重定向到 CAS 登录页时抛出此异常。
 * UI 层捕获后应提示用户重新登录。
 */
class SessionExpiredException(message: String) : Exception(message)
