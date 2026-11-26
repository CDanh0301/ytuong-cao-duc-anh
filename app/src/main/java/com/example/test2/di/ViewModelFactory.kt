package com.example.test2.di

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.test2.presentation.categories.CategoriesViewModel
import com.example.test2.presentation.gallery.GalleryViewModel
import com.example.test2.presentation.housekeeping.HousekeepingViewModel
import com.example.test2.presentation.vault.VaultViewModel
import com.example.test2.ui.PhotoAppViewModel

/**
 * ViewModelProvider.Factory providing manual DI across [PhotoAppViewModel] and all feature ViewModels
 * backed by a shared [AppContainer].
 */
class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    val container: AppContainer by lazy {
        AppModule.createContainer(application, hasStoragePermission(application))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            PhotoAppViewModel::class.java -> PhotoAppViewModel(application, container)
            GalleryViewModel::class.java -> GalleryViewModel(application, container)
            VaultViewModel::class.java -> VaultViewModel(application, container)
            CategoriesViewModel::class.java -> CategoriesViewModel(container)
            HousekeepingViewModel::class.java -> HousekeepingViewModel(container)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }

    companion object {
        fun hasStoragePermission(application: Application): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(application, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
