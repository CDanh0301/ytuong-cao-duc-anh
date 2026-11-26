package com.example.test2.data.repository

import android.content.Context
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.AndroidFilePersistenceDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android-backed repository implementation delegating to the shared [PhotoRepository].
 */
class PhotoRepositoryImpl(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val delegate: PhotoRepository = PhotoRepository(AndroidFilePersistenceDriver(context.applicationContext), scope)
) : IPhotoRepository by delegate
