package com.example.data.network

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.util.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadEngine(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun enqueueAndStart(
        candidate: VideoStreamCandidate,
        customTitle: String,
        wifiOnly: Boolean
    ) {
        scope.launch {
            val cleanTitle = customTitle.trim().ifBlank { candidate.title }
            val entity = DownloadEntity(
                title = cleanTitle,
                sourceUrl = candidate.pageUrl,
                resolvedUrl = candidate.streamUrl,
                domain = candidate.domain,
                mimeType = candidate.mimeType,
                formatLabel = candidate.formatLabel,
                qualityLabel = candidate.qualityLabel,
                totalBytes = candidate.contentLengthBytes.coerceAtLeast(0L),
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                status = DownloadStatus.QUEUED,
                posterUrl = candidate.posterUrl
            )
            val id = downloadDao.insert(entity)
            startDownloadJob(id, wifiOnly)
        }
    }

    fun resumeOrRetry(downloadId: Long, wifiOnly: Boolean) {
        scope.launch {
            startDownloadJob(downloadId, wifiOnly)
        }
    }

    fun pauseDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        scope.launch {
            val existing = downloadDao.getById(downloadId) ?: return@launch
            if (existing.status == DownloadStatus.DOWNLOADING || existing.status == DownloadStatus.QUEUED) {
                downloadDao.update(
                    existing.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        scope.launch {
            val existing = downloadDao.getById(downloadId)
            existing?.localFilePath?.let { path ->
                runCatching { File(path).delete() }
                runCatching { File("$path.part").delete() }
            }
            existing?.thumbnailPath?.let { path ->
                runCatching { File(path).delete() }
            }
            downloadDao.deleteById(downloadId)
        }
    }

    private fun startDownloadJob(downloadId: Long, wifiOnly: Boolean) {
        activeJobs[downloadId]?.cancel()
        val job = scope.launch {
            val entity = downloadDao.getById(downloadId) ?: return@launch

            if (wifiOnly && !isOnWifi()) {
                downloadDao.update(
                    entity.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        errorMessage = "Waiting for Wi-Fi (Wi-Fi Only mode enabled)",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@launch
            }

            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "videos").apply { mkdirs() }
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val ext = entity.formatLabel.lowercase().ifBlank { "mp4" }
            val fileName = Formatters.sanitizeFileName("${entity.title}_${entity.id}", ext)
            val targetFile = entity.localFilePath?.let(::File) ?: File(moviesDir, fileName)
            val partFile = File("${targetFile.absolutePath}.part")

            downloadDao.update(
                entity.copy(
                    status = DownloadStatus.DOWNLOADING,
                    errorMessage = null,
                    localFilePath = targetFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
            )

            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val requestBuilder = Request.Builder()
                    .url(entity.resolvedUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("Server responded with HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty video response")
                    val appendMode = response.code == 206 && existingBytes > 0L
                    var downloaded = if (appendMode) existingBytes else 0L
                    val contentLength = body.contentLength()
                    val total = when {
                        appendMode && contentLength > 0L -> existingBytes + contentLength
                        contentLength > 0L -> contentLength
                        entity.totalBytes > 0L -> entity.totalBytes
                        else -> 0L
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            var lastUpdateTime = System.currentTimeMillis()
                            var bytesSinceLastUpdate = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!activeJobs.containsKey(downloadId)) {
                                    throw CancellationException("Download paused")
                                }
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                bytesSinceLastUpdate += bytesRead

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastUpdateTime
                                if (elapsed >= 220L) {
                                    val speed = (bytesSinceLastUpdate * 1000L) / elapsed.coerceAtLeast(1L)
                                    downloadDao.updateProgress(
                                        id = downloadId,
                                        downloadedBytes = downloaded,
                                        totalBytes = total.coerceAtLeast(downloaded),
                                        speed = speed,
                                        status = DownloadStatus.DOWNLOADING,
                                        updatedAt = now
                                    )
                                    lastUpdateTime = now
                                    bytesSinceLastUpdate = 0L
                                }
                            }
                            output.flush()
                        }
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }

                    val finalSize = targetFile.length()
                    val metadata = extractVideoMetadata(targetFile, downloadId)

                    val latest = downloadDao.getById(downloadId) ?: entity
                    downloadDao.update(
                        latest.copy(
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = finalSize,
                            totalBytes = finalSize,
                            speedBytesPerSec = 0L,
                            localFilePath = targetFile.absolutePath,
                            thumbnailPath = metadata.thumbnailPath,
                            durationMs = metadata.durationMs,
                            resolution = metadata.resolution ?: latest.qualityLabel,
                            errorMessage = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val latest = downloadDao.getById(downloadId)
                if (latest != null && latest.status != DownloadStatus.PAUSED) {
                    downloadDao.update(
                        latest.copy(
                            status = DownloadStatus.FAILED,
                            speedBytesPerSec = 0L,
                            errorMessage = e.localizedMessage ?: "Download failed",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } finally {
                activeJobs.remove(downloadId)
            }
        }
        activeJobs[downloadId] = job
    }

    private data class ExtractedVideoMetadata(
        val durationMs: Long = 0L,
        val resolution: String? = null,
        val thumbnailPath: String? = null
    )

    private fun extractVideoMetadata(file: File, downloadId: Long): ExtractedVideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val resolution = if (width != null && height != null && width > 0 && height > 0) {
                "${height}p"
            } else null

            val frameTimeUs = if (durationMs > 2000L) 1_500_000L else 0L
            val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            var thumbPath: String? = null
            if (bitmap != null) {
                val thumbDir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
                val thumbFile = File(thumbDir, "thumb_$downloadId.jpg")
                FileOutputStream(thumbFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                bitmap.recycle()
                thumbPath = thumbFile.absolutePath
            }
            ExtractedVideoMetadata(durationMs, resolution, thumbPath)
        } catch (_: Exception) {
            ExtractedVideoMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
