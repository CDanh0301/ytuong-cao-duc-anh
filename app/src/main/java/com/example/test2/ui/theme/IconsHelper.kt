package com.example.test2.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WorkOutline
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
            "camera" -> Icons.Outlined.PhotoCamera
            "heart" -> Icons.Outlined.FavoriteBorder
            "plane" -> Icons.Outlined.Flight
            "briefcase" -> Icons.Outlined.WorkOutline
            "leaf" -> Icons.Outlined.Eco
            "user" -> Icons.Outlined.Person
            "utensils" -> Icons.Outlined.Restaurant
            "building" -> Icons.Outlined.Apartment
            "paw" -> Icons.Outlined.Pets
            "tag" -> Icons.AutoMirrored.Outlined.Label
            else -> Icons.Outlined.Folder
        }
    }
}
