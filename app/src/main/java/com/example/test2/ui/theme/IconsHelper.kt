package com.example.test2.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object IconsHelper {
    val FOLDER_ICONS = listOf(
        "folder" to "Folder",
        "camera" to "Camera",
        "heart" to "Heart",
        "plane" to "Travel",
        "briefcase" to "Work",
        "leaf" to "Nature",
        "user" to "People",
        "utensils" to "Dining",
        "building" to "Places",
        "paw" to "Pets"
    )

    val CATEGORY_ICONS = listOf(
        "tag" to "Tag",
        "leaf" to "Nature",
        "user" to "Portraits",
        "utensils" to "Food",
        "building" to "Architecture",
        "paw" to "Animals",
        "plane" to "Travel",
        "camera" to "Camera"
    )

    fun getIconByName(iconName: String?): ImageVector {
        return when (iconName?.lowercase()) {
            "camera" -> Icons.Default.PhotoCamera
            "heart" -> Icons.Default.Favorite
            "plane" -> Icons.Default.Flight
            "briefcase" -> Icons.Default.Work
            "leaf" -> Icons.Default.Eco
            "user" -> Icons.Default.Person
            "utensils" -> Icons.Default.Restaurant
            "building" -> Icons.Default.Apartment
            "paw" -> Icons.Default.Pets
            "tag" -> Icons.AutoMirrored.Filled.Label
            else -> Icons.Default.Folder
        }
    }
}
