package edu.cqwu.electricity.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Locale-aware ContextWrapper for in-app language switching.
 *
 * Overrides the resource configuration so that all XML string resources
 * are resolved against the user-selected language, independently of the
 * device's system language.
 *
 * ## Usage
 *
 * In [Activity.attachBaseContext]:
 * ```kotlin
 * override fun attachBaseContext(newBase: Context) {
 *     val language = SettingsPreferences(newBase).getAppLanguage()
 *     super.attachBaseContext(LocaleContextWrapper.wrap(newBase, language))
 * }
 * ```
 *
 * After the user selects a different language, call [Activity.recreate]
 * to restart the Activity with the new configuration.
 */
class LocaleContextWrapper(base: Context) : ContextWrapper(base) {

    companion object {
        /**
         * Wrap the given [context] so that all resource lookups use [language].
         *
         * @param context  The base application context.
         * @param language An IETF BCP 47 language tag, e.g. "zh" or "en".
         * @return A [ContextWrapper] whose resources are localised to [language].
         */
        fun wrap(context: Context, language: String): ContextWrapper {
            val locale = Locale.forLanguageTag(language)
            Locale.setDefault(locale)

            val config = Configuration(context.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }

            return LocaleContextWrapper(context.createConfigurationContext(config))
        }
    }
}
