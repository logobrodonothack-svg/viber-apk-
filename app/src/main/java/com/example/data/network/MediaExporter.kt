package com.example.data.network

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.DownloadEntity
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaExporter {
    suspend fun saveToGallery(context: Context, item: DownloadEntity): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = item.localFilePath ?: error("Local file missing")
                val sourceFile = File(path)
                if (!sourceFile.exists()) error("Video file not found on disk")

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    error("Use Share to export on Android 9 or older")
                }

                val ext = item.formatLabel.lowercase().ifBlank { "mp4" }
                val displayName = Formatters.sanitizeFileName(item.title, ext)

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, item.mimeType.ifBlank { "video/mp4" })
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/ClipVault"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)
                    ?: error("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Failed to open gallery output stream")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                "Saved to Movies/ClipVault"
            }
        }

    fun shareVideo(context: Context, item: DownloadEntity): Boolean {
        val file = item.localFilePath?.let(::File) ?: return false
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "video/mp4" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${item.title}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return true
    }

    fun openExternalPlayer(context: Context, item: DownloadEntity): Boolean {
        val file = item.localFilePath?.let(::File) ?: return false
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifBlank { "video/mp4" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(viewIntent)
            true
        }.getOrDefault(false)
    }
}
