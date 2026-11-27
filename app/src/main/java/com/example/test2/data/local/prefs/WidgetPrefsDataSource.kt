package com.example.test2.data.local.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Local SharedPreferences data source for Home Screen Widget cached state.
 */
class WidgetPrefsDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCachedIsPro(): Boolean = prefs.contains(KEY_IS_PRO)

    fun getCachedIsPro(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun setCachedIsPro(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    companion object {
        private const val PREFS_NAME = "photo_search_widget_prefs"
        private const val KEY_IS_PRO = "cached_is_pro"
    }
}
