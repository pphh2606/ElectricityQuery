package edu.cqwu.electricity.hall.data

import android.content.Context
import com.google.gson.Gson
import edu.cqwu.electricity.hall.data.HallItem
import edu.cqwu.electricity.hall.data.HallSearchResponse
import java.io.IOException

/**
 * 从 assets/hall_apps.json 加载办事大厅应用列表
 */
class HallJsonLoader(private val context: Context) {

    private val gson = Gson()
    private var cachedItems: List<HallItem>? = null

    /**
     * 清空内存缓存，下次加载将重新读取文件
     */
    fun invalidateCache() {
        cachedItems = null
    }

    /**
     * 从 assets/hall_apps.json 加载并解析应用列表。
     * 首次加载后会缓存到内存中。
     */
    fun loadItems(): Result<List<HallItem>> {
        cachedItems?.let { return Result.success(it) }

        return try {
            val jsonString = context.assets.open("hall_apps.json")
                .bufferedReader()
                .use { it.readText() }
            val response = gson.fromJson(jsonString, HallSearchResponse::class.java)
            val items = response.searchResult
            cachedItems = items
            Result.success(items)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
