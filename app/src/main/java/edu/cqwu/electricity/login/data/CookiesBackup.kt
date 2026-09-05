package edu.cqwu.electricity.login.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 账号 Cookie 登录态的 JSON 备份/恢复编解码（明文，不含密码）。
 *
 * 格式：
 * ```json
 * {
 *   "type": "cookies",
 *   "v": 1,
 *   "accounts": [
 *     { "username": "...", "studentId": "...", "lastLoginTime": 0,
 *       "cookies": { "https://host": { "CASTGC": "..." } } }
 *   ]
 * }
 * ```
 *
 * 说明：
 * - 与账号密码隔离：永不导出 [SavedAccount.password]，rememberPassword 恒为 false；
 * - 导出的 cookie 是该账号条目当前保存的全部域名登录态（激活中账号通常最多，登出过的为空）；
 * - 导入由 [AccountSessionStore.importAccounts] 追加（不激活、不动系统 CookieManager）。
 */
object CookiesBackup {

    const val TYPE = "cookies"
    const val FORMAT_VERSION = 1

    /** 生成全部账号的 Cookie 备份 JSON 文本 */
    fun exportJson(): String = encode(AccountSessionStore.getAllAccounts())

    /**
     * 解析备份 JSON 为可导入的账号草稿；格式不合法或类型/版本不符返回 null。
     */
    fun decode(json: String): List<SavedAccount>? {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return null
        }
        if (root.get("type")?.asString != TYPE) return null
        if (root.get("v")?.asInt != FORMAT_VERSION) return null
        val items = root.getAsJsonArray("accounts") ?: return null
        val result = mutableListOf<SavedAccount>()
        for (element in items) {
            try {
                val obj = element.asJsonObject
                val username = obj.get("username")?.asString
                    ?.takeIf { it.isNotBlank() } ?: continue
                val cookies = parseCookies(obj)
                // 密码恒为空、不记住密码：Cookie 备份只还原登录态与账号元信息
                result.add(
                    SavedAccount(
                        id = "", // 导入时由 AccountSessionStore 重新生成
                        username = username,
                        password = null,
                        lastLoginTime = obj.get("lastLoginTime")?.asLong ?: 0L,
                        rememberPassword = false,
                        cookies = cookies,
                        studentId = obj.get("studentId")?.asString,
                    )
                )
            } catch (e: Exception) {
                // 单条异常跳过，不影响其它账号
            }
        }
        return result
    }

    // ═══════════════════════════════════════
    //  内部
    // ═══════════════════════════════════════

    /** 序列化给定账号列表（不含密码与 remember 标记；internal 便于同模块单测） */
    internal fun encode(accounts: List<SavedAccount>): String {
        val root = JsonObject()
        root.addProperty("type", TYPE)
        root.addProperty("v", FORMAT_VERSION)
        root.addProperty("exportedAt", System.currentTimeMillis())
        val arr = JsonArray()
        accounts.forEach { account ->
            val obj = JsonObject()
            obj.addProperty("username", account.username)
            account.studentId?.let { obj.addProperty("studentId", it) }
            obj.addProperty("lastLoginTime", account.lastLoginTime)
            val cookies = JsonObject()
            account.cookies.forEach { (domain, kv) ->
                val inner = JsonObject()
                kv.forEach { (name, value) -> inner.addProperty(name, value) }
                cookies.add(domain, inner)
            }
            obj.add("cookies", cookies)
            arr.add(obj)
        }
        root.add("accounts", arr)
        return root.toString()
    }

    private fun parseCookies(obj: JsonObject): Map<String, Map<String, String>> {
        val outer = obj.getAsJsonObject("cookies") ?: return emptyMap()
        val result = linkedMapOf<String, Map<String, String>>()
        outer.entrySet().forEach { (domain, value) ->
            val innerObj = value.asJsonObject
            val kv = linkedMapOf<String, String>()
            innerObj.entrySet().forEach { (name, v) ->
                v.asString?.let { kv[name] = it }
            }
            result[domain] = kv
        }
        return result
    }
}
