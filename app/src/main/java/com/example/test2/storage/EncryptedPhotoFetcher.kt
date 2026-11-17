package com.example.test2.storage

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.File

/**
 * Custom Coil Fetcher that seamlessly decodes on-device encrypted photos (AES-256-GCM) in memory.
 * Ensures images in internal app storage remain 100% encrypted on disk while rendering smoothly in Compose.
 */
class EncryptedPhotoFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val decryptedStream = LocalCryptoManager.openDecryptedInputStream(file)
        val bufferedSource = decryptedStream.source().buffer()
        val mimeType = when {
            file.name.contains(".png", ignoreCase = true) -> "image/png"
            file.name.contains(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }

        return SourceResult(
            source = ImageSource(source = bufferedSource, context = options.context),
            mimeType = mimeType,
            dataSource = DataSource.DISK
        )
    }

    class FileFactory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (LocalCryptoManager.isEncrypted(data)) {
                return EncryptedPhotoFetcher(data, options)
            }
            return null
        }
    }

    class StringFactory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            val file = File(data)
            if (LocalCryptoManager.isEncrypted(file)) {
                return EncryptedPhotoFetcher(file, options)
            }
            return null
        }
    }

    class UriFactory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val path = data.path ?: return null
            val file = File(path)
            if (LocalCryptoManager.isEncrypted(file)) {
                return EncryptedPhotoFetcher(file, options)
            }
            return null
        }
    }
}
