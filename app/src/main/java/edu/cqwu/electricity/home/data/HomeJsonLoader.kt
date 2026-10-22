package edu.cqwu.electricity.home.data

import android.content.Context
import com.google.gson.Gson
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.common.settings.AppLanguage
import edu.cqwu.electricity.logging.AppLog
import java.io.IOException

class HomeJsonLoader(private val context: Context) {

    private val gson = Gson()

    /**
     * Invalidates the in-memory cache so the next call to [loadCategories]
     * will re-read the JSON file from assets.
     */
    fun invalidateCache() {
        cachedCategories = null
        cachedLanguageKey = null
        cachedI18n = null
    }

    /**
     * Loads and parses home_apps.json from assets, then merges the localized names
     * from `assets/i18n/home_apps_i18n.json` for the current app language.
     * Filters out categories with empty app lists.
     *
     * 结果连同语言键一起缓存：语言变了必须重新解析合并，否则切换语言后首页仍是旧译文。
     */
    fun loadCategories(): Result<List<HomeCategory>> {
        val languageKey = currentLanguageKey()

        // Return cached data immediately if it matches the current language
        cachedCategories?.let { cached ->
            if (cachedLanguageKey == languageKey) return Result.success(cached)
        }

        return try {
            val jsonString = context.assets.open(HOME_ASSET)
                .bufferedReader()
                .use { it.readText() }
            val response = gson.fromJson(jsonString, HomeResponse::class.java)
            val categories = response.datas
                .filter { it.apps.isNotEmpty() }
                .sortedBy { it.order }

            val localized = mergeWithI18n(categories, languageKey)

            cachedCategories = localized
            cachedLanguageKey = languageKey
            Result.success(localized)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 合并译文。简体中文（[languageKey] 为 null）直接用 JSON 原文；
     * 映射文件缺失、损坏或合并异常时同样整份回退原文，只记一行日志，绝不阻断首页。
     */
    private fun mergeWithI18n(
        categories: List<HomeCategory>,
        languageKey: String?,
    ): List<HomeCategory> {
        if (languageKey == null) return categories

        val i18n = loadI18n() ?: return categories

        val unknownIds = linkedSetOf<String>()
        val merged = try {
            mergeHomeI18n(categories, i18n, languageKey, unknownIds)
        } catch (e: Exception) {
            AppLog.w(TAG, "合并首页译文失败，回退中文: ${e.message}")
            return categories
        }

        // 换新 home_apps.json 后，这行日志直接列出还需要补哪些 appId 的译文
        if (BuildConfig.DEBUG && unknownIds.isNotEmpty()) {
            AppLog.d(TAG, "首页在 $languageKey 下缺少译文并回退中文: ${unknownIds.joinToString(",")}")
        }

        return merged
    }

    /** 读取多语言映射。整份文件与语言无关，解析一次后复用。 */
    private fun loadI18n(): HomeAppI18n? {
        cachedI18n?.let { return it }

        return try {
            val text = context.assets.open(I18N_ASSET).bufferedReader().use { it.readText() }
            gson.fromJson(text, HomeAppI18n::class.java).also { cachedI18n = it }
        } catch (e: Exception) {
            AppLog.w(TAG, "读取首页多语言映射失败，回退中文: ${e.message}")
            null
        }
    }

    /**
     * 当前语言在映射文件里的键；简体中文返回 null，表示直接用 home_apps.json 原文。
     *
     * 同时对 ViewModel 公开：ViewModel 需要用它判断「已加载的数据是否还属于当前语言」。
     * 切语言走的是 Activity.recreate()，ViewModel 会被保留、init 不再执行，只能靠这个比对补救。
     * 具体解析规则见 [AppLanguage.currentI18nKey]，与大厅共用同一份实现。
     */
    fun currentLanguageKey(): String? = AppLanguage.currentI18nKey(context)

    companion object {
        private const val TAG = "HomeJsonLoader"
        private const val HOME_ASSET = "home_apps.json"
        private const val I18N_ASSET = "i18n/home_apps_i18n.json"

        /**
         * 进程级缓存：HomeViewModel 与 AddShortcutScreen 等多处加载同一份首页数据时共享，
         * 避免各自实例重复解析 home_apps.json。
         */
        @Volatile
        private var cachedCategories: List<HomeCategory>? = null

        /** [cachedCategories] 对应的语言键；与当前语言不一致时必须重新解析合并 */
        @Volatile
        private var cachedLanguageKey: String? = null

        /** 多语言映射，内容与语言无关，整份缓存 */
        @Volatile
        private var cachedI18n: HomeAppI18n? = null
    }
}
