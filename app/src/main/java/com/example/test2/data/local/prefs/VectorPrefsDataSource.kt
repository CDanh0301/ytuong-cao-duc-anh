package com.example.test2.data.local.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Local SharedPreferences data source for vector semantic search index versioning.
 */
class VectorPrefsDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getClusterVersion(): Int = prefs.getInt(KEY_CLUSTER_VERSION, 0)

    fun setClusterVersion(version: Int) {
        prefs.edit().putInt(KEY_CLUSTER_VERSION, version).apply()
    }

    companion object {
        private const val PREFS_NAME = "vector_prefs"
        private const val KEY_CLUSTER_VERSION = "vector_cluster_version"
    }
}
