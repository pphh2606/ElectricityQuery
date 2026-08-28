package edu.cqwu.electricity.login.data

import edu.cqwu.electricity.logging.AppLog

/**
 * 退出登录 API 调用点。
 *
 * 目前校园服务尚未提供退出登录接口，此处仅预留接口与调用位置（删除账号时调用），
 * 后续接入真实 API 后在此实现即可，无需改动账号管理逻辑。
 */
object LogoutApi {

    private const val TAG = "LogoutApi"

    /**
     * 调用服务端退出登录接口，使该账号在服务端的会话失效。
     *
     * @param username 要退出登录的登录用户名（学号或登录别名）
     * @param cookies 该账号当前持久化的登录状态（cookie 集合），供退出登录请求携带
     */
    fun logout(username: String, cookies: Map<String, Map<String, String>>) {
        // TODO: 校园服务暂无退出登录 API，后续接入（如 authserver 的 /logout 接口）
        AppLog.d(TAG, "退出登录 API 未接入，跳过服务端登出: $username")
    }
}
