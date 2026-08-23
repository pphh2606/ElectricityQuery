package edu.cqwu.electricity.home.data

import android.content.Context
import com.google.gson.Gson
import java.io.IOException

class HomeJsonLoader(private val context: Context) {

    private val gson = Gson()

    /**
     * Invalidates the in-memory cache so the next call to [loadCategories]
     * will re-read the JSON file from assets.
     */
    fun invalidateCache() {
        cachedCategories = null
    }

    /**
     * Loads and parses home_apps.json from assets.
     * Filters out categories with empty app lists.
     *
     * Results are cached in memory after first load,
     * subsequent calls return immediately with cached data.
     */
    fun loadCategories(): Result<List<HomeCategory>> {
        // Return cached data immediately if available
        cachedCategories?.let { return Result.success(it) }

        return try {
            val jsonString = context.assets.open("home_apps.json")
                .bufferedReader()
                .use { it.readText() }
            val response = gson.fromJson(jsonString, HomeResponse::class.java)
            val categories = response.datas
                .filter { it.apps.isNotEmpty() }
                .sortedBy { it.order }
            // Cache for subsequent calls
            cachedCategories = categories
            Result.success(categories)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * 进程级缓存：HomeViewModel 与 AddShortcutScreen 等多处加载同一份首页数据时共享，
         * 避免各自实例重复解析 home_apps.json。
         */
        @Volatile
        private var cachedCategories: List<HomeCategory>? = null
    }
}
