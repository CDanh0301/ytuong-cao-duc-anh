package com.example.test2.data.repository

import com.example.test2.shared.models.Category
import com.example.test2.shared.repository.PersistenceDriver
import com.example.test2.util.AppLogger
import org.json.JSONObject

/**
 * Abstraction for reading lightweight app metadata (user categories, Pro tier)
 * in background workers without manual JSON file parsing inside the Worker.
 */
interface IAppDataSource {
    suspend fun getUserCategories(): List<Category>
    suspend fun isProTier(): Boolean
}

class AndroidFilePersistenceDataSource(
    private val persistenceDriver: PersistenceDriver
) : IAppDataSource {

    override suspend fun getUserCategories(): List<Category> {
        val rawJson = persistenceDriver.loadData() ?: return emptyList()
        if (rawJson.isBlank()) return emptyList()
        return try {
            val root = JSONObject(rawJson)
            val catsArray = root.optJSONArray("categories") ?: return emptyList()
            val catList = mutableListOf<Category>()
            for (i in 0 until catsArray.length()) {
                val obj = catsArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val colorHex = obj.optString("colorHex", "#6366F1")
                val iconName = obj.optString("iconName", "tag")
                val createdAt = obj.optLong("createdAt", 0L)
                if (id.isNotBlank() && name.isNotBlank()) {
                    catList.add(Category(id = id, name = name, colorHex = colorHex, iconName = iconName, createdAt = createdAt))
                }
            }
            catList
        } catch (e: Exception) {
            AppLogger.w("AndroidFilePersistenceDS", "Failed to parse user categories", e)
            emptyList()
        }
    }

    override suspend fun isProTier(): Boolean {
        val rawJson = persistenceDriver.loadData() ?: return true
        if (rawJson.isBlank()) return true
        return try {
            JSONObject(rawJson).optJSONObject("accountTier")?.optBoolean("isPro", true) ?: true
        } catch (e: Exception) {
            AppLogger.w("AndroidFilePersistenceDS", "Failed to parse tier flag", e)
            true
        }
    }
}
