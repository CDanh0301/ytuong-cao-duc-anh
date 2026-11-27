package com.example.test2.data.local.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Local SharedPreferences data source for periodic Aging Clutter Monitor metadata.
 */
class AgingPrefsDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordCheck(checkedAtMillis: Long, clutterCount: Int, clutterBytes: Long) {
        prefs.edit().apply {
            putLong(KEY_LAST_CHECKED, checkedAtMillis)
            putInt(KEY_LAST_CLUTTER_COUNT, clutterCount)
            putLong(KEY_LAST_CLUTTER_BYTES, clutterBytes)
            apply()
        }
    }

    fun getLastNotifiedMillis(): Long = prefs.getLong(KEY_LAST_NOTIFIED, 0L)

    fun setLastNotifiedMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_NOTIFIED, timestampMillis).apply()
    }

    fun getLastCheckedInfo(): Triple<Long, Int, Long> {
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        val clutterCount = prefs.getInt(KEY_LAST_CLUTTER_COUNT, 0)
        val clutterBytes = prefs.getLong(KEY_LAST_CLUTTER_BYTES, 0L)
        return Triple(lastChecked, clutterCount, clutterBytes)
    }

    companion object {
        private const val PREFS_NAME = "aging_monitor_prefs"
        private const val KEY_LAST_NOTIFIED = "key_last_notified"
        private const val KEY_LAST_CHECKED = "key_last_checked"
        private const val KEY_LAST_CLUTTER_COUNT = "key_last_clutter_count"
        private const val KEY_LAST_CLUTTER_BYTES = "key_last_clutter_bytes"
    }
}
