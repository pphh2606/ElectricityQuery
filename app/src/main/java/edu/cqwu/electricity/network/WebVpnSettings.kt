package edu.cqwu.electricity.network

/**
 * WebVPN 全局开关的运行状态。
 *
 * 由 [edu.cqwu.electricity.app.ElectricityApp] 从 SharedPreferences 加载，
 * 设置页修改后同步更新，OkHttp 拦截器每次请求读取该值。
 */
object WebVpnSettings {
    @Volatile
    var enabled: Boolean = false
}
