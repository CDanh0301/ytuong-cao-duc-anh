package com.example.test2.data.repository

import android.content.Context
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.repository.PersistenceDriver
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.AndroidFilePersistenceDriver
import com.example.test2.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android-backed repository implementation delegating to the shared [PhotoRepository].
 */
class PhotoRepositoryImpl(
    persistenceDriver: PersistenceDriver,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val delegate: PhotoRepository = PhotoRepository(
        persistenceDriver = persistenceDriver,
        scope = scope,
        onError = { msg, err -> AppLogger.w("PhotoRepository", msg, err) }
    )
) : IPhotoRepository by delegate {

    constructor(
        context: Context,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ) : this(
        persistenceDriver = AndroidFilePersistenceDriver(context.applicationContext),
        scope = scope
    )
}
