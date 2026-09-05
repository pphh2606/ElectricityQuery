package edu.cqwu.electricity.settings.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 应用设置项的 JSON 备份/恢复编解码。
 *
 * 格式：
 * ```json
 * { "v": 1, "settings": [ { "k": "night_mode", "t": "string", "v": "dark" }, ... ] }
 * ```
 * 每项带类型标签（bool/int/long/float/string/stringset），导入时可精确还原类型写回。
 *
 * 注意：Android 13+ 的应用语言由系统 LocaleManager 管理，不在此文件中，
 * 因此语言不参与备份（[app_language] 键也会被排除）。
 */
object SettingsBackup {

    const val FORMAT_VERSION = 1

    /** 不参与备份的键：应用语言（13+ 走 LocaleManager；低版本残留键也跳过） */
    private val EXCLUDED_KEYS = setOf("app_language")

    /** 设置文件名，与 [SettingsPreferences] 保持一致 */
    private const val PREF_NAME = "settings_preferences"

    // ═══════════════════════════════════════
    //  纯逻辑（可 JVM 单测）
    // ═══════════════════════════════════════

    /**
     * 把设置键值序列化为备份 JSON。
     *
     * @param entries SharedPreferences.all 的原样键值（值是 Boolean/Int/Long/Float/String/Set<String>）
     */
    fun encode(entries: Map<String, *>): String {
        val root = JsonObject()
        root.addProperty("v", FORMAT_VERSION)
        val items = JsonArray()
        for ((key, value) in entries) {
            if (key in EXCLUDED_KEYS) continue
            val v = value ?: continue
            val item = JsonObject()
            item.addProperty("k", key)
            when (v) {
                is Boolean -> {
                    item.addProperty("t", "bool")
                    item.addProperty("v", v)
                }
                is Int -> {
                    item.addProperty("t", "int")
                    item.addProperty("v", v)
                }
                is Long -> {
                    item.addProperty("t", "long")
                    item.addProperty("v", v)
                }
                is Float -> {
                    item.addProperty("t", "float")
                    item.addProperty("v", v)
                }
                is Double -> {
                    item.addProperty("t", "float")
                    item.addProperty("v", v)
                }
                is String -> {
                    item.addProperty("t", "string")
                    item.addProperty("v", v)
                }
                is Set<*> -> {
                    val arr = JsonArray()
                    v.filterIsInstance<String>().forEach(arr::add)
                    item.addProperty("t", "stringset")
                    item.add("v", arr)
                }
                else -> continue // 未知类型跳过
            }
            items.add(item)
        }
        root.add("settings", items)
        return root.toString()
    }

    /**
     * 解析备份 JSON 为类型化键值；格式不合法或版本不支持返回 null。
     */
    fun decode(json: String): Map<String, Any>? {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return null
        }
        if (root.get("v")?.asInt != FORMAT_VERSION) return null
        val items = root.getAsJsonArray("settings") ?: return null
        val result = linkedMapOf<String, Any>()
        for (element in items) {
            try {
                val item = element.asJsonObject
                val key = item.get("k")?.asString
                    ?.takeIf { it.isNotBlank() && it !in EXCLUDED_KEYS } ?: continue
                val type = item.get("t")?.asString ?: continue
                val raw = item.get("v") ?: continue
                when (type) {
                    "bool" -> result[key] = raw.asBoolean
                    "int" -> result[key] = raw.asInt
                    "long" -> result[key] = raw.asLong
                    "float" -> result[key] = raw.asFloat
                    "string" -> result[key] = raw.asString
                    "stringset" -> {
                        val arr = raw.asJsonArray
                        val values = buildList {
                            arr.forEach { el -> el.asString?.takeIf { it.isNotEmpty() }?.let(::add) }
                        }
                        result[key] = values.toSet()
                    }
                }
            } catch (e: Exception) {
                // 单条格式异常时跳过，不影响其它条目
            }
        }
        return result
    }

    // ═══════════════════════════════════════
    //  设备读写
    // ═══════════════════════════════════════

    /** 获取设置 SharedPreferences（与 [SettingsPreferences] 同名） */
    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 生成当前设置的备份 JSON 文本 */
    fun exportJson(context: Context): String =
        encode(prefs(context).all)

    /**
     * 校验并把备份写回设置存储。
     *
     * @return true 表示校验通过并已写回；false 表示内容非法/版本不支持（未写任何内容）
     */
    fun importJson(context: Context, json: String): Boolean {
        val entries = decode(json) ?: return false
        val editor = prefs(context).edit()
        entries.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
            }
        }
        editor.apply()
        return true
    }
}
