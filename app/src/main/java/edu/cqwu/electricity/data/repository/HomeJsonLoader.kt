package edu.cqwu.electricity.data.repository

import android.content.Context
import com.google.gson.Gson
import edu.cqwu.electricity.data.model.HomeCategory
import edu.cqwu.electricity.data.model.HomeResponse
import java.io.IOException

class HomeJsonLoader(private val context: Context) {

    private val gson = Gson()
    private var cachedCategories: List<HomeCategory>? = null

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
}
