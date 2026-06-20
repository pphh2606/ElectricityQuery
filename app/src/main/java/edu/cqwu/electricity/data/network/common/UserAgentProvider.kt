package edu.cqwu.electricity.data.network.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.ElectricityApp
import edu.cqwu.electricity.data.local.SettingsPreferences
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 浏览器标识（User-Agent）运行时提供者。
 *
 * 组合内置预设 + 用户自定义条目，根据当前选中 ID 返回对应的 UA 字符串。
 * 所有操作通过 [SettingsPreferences] 持久化。
 */
object UserAgentProvider {

    private val gson = Gson()
    private val prefs by lazy { SettingsPreferences(ElectricityApp.instance) }

    // ═══════════════════════════════════════
    //  内置预设
    // ═══════════════════════════════════════

    /** 内置预设列表（不可删除、不可编辑名称） */
    val BUILTIN_PRESETS: List<UserAgentEntry> = listOf(
        UserAgentEntry(
            id = "preset_jr",
            name = "JR校园",
            userAgent = "Mozilla/5.0 (Linux; Android 15; Mi 10 Pro Build/AQ3A.240812.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.260 Mobile Safari/537.36 cpdaily/9.8.1 wisedu/9.8.1",
            isBuiltin = true,
        ),
        UserAgentEntry(
            id = "preset_default",
            name = "默认",
            userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
            isBuiltin = true,
        ),
    )

    // ═══════════════════════════════════════
    //  公开 API
    // ═══════════════════════════════════════

    /**
     * 获取当前激活的 User-Agent 字符串。
     * 如果选中的是内置预设，返回预设值；如果是自定义条目，返回自定义值。
     * 兜底返回"默认"预设。
     */
    fun getActiveUserAgent(): String {
        val selectedId = prefs.getSelectedUaId()
        return getEntryById(selectedId)?.userAgent
            ?: BUILTIN_PRESETS.first { it.id == "preset_default" }.userAgent
    }

    /**
     * 获取完整条目列表（内置预设 + 用户自定义）。
     */
    fun getAllEntries(): List<UserAgentEntry> {
        return BUILTIN_PRESETS + getCustomEntries()
    }

    /**
     * 根据 ID 获取条目。
     */
    fun getEntryById(id: String): UserAgentEntry? {
        return getAllEntries().firstOrNull { it.id == id }
    }

    /**
     * 获取当前选中的条目 ID。
     */
    fun getSelectedId(): String = prefs.getSelectedUaId()

    /**
     * 设置当前选中的条目 ID。
     */
    fun setSelectedId(id: String) {
        prefs.setSelectedUaId(id)
    }

    /**
     * 获取当前选中条目的显示名称。
     */
    fun getSelectedName(): String {
        val id = prefs.getSelectedUaId()
        return getEntryById(id)?.name ?: "默认"
    }

    // ═══════════════════════════════════════
    //  自定义条目 CRUD
    // ═══════════════════════════════════════

    /**
     * 获取用户自定义条目列表。
     */
    fun getCustomEntries(): List<UserAgentEntry> {
        val json = prefs.getCustomUaList()
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<UserAgentEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 保存自定义条目列表。
     */
    private fun saveCustomEntries(entries: List<UserAgentEntry>) {
        prefs.setCustomUaList(gson.toJson(entries))
    }

    /**
     * 添加自定义条目。
     */
    fun addCustomEntry(entry: UserAgentEntry) {
        val entries = getCustomEntries().toMutableList()
        entries.add(entry)
        saveCustomEntries(entries)
    }

    /**
     * 更新自定义条目。
     */
    fun updateCustomEntry(updated: UserAgentEntry) {
        val entries = getCustomEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            entries[index] = updated
            saveCustomEntries(entries)
        }
    }

    /**
     * 删除自定义条目。
     * 如果当前选中项被删除，自动回退到"默认"预设。
     */
    fun removeCustomEntry(id: String) {
        val entries = getCustomEntries().toMutableList()
        entries.removeAll { it.id == id }
        saveCustomEntries(entries)

        // 如果当前选中项被删除，回退到默认
        if (prefs.getSelectedUaId() == id) {
            prefs.setSelectedUaId("preset_default")
        }
    }
}

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

/**
 * 浏览器标识条目数据类。
 *
 * @param id 唯一标识。内置预设以 "preset_" 开头，自定义条目用 UUID。
 * @param name 显示名称。
 * @param userAgent 完整的 User-Agent 字符串。
 * @param note 用户备注（仅自定义条目）。
 * @param isBuiltin 是否为内置预设（不可删除）。
 */
data class UserAgentEntry(
    val id: String,
    val name: String,
    val userAgent: String,
    val note: String = "",
    val isBuiltin: Boolean = false,
)
