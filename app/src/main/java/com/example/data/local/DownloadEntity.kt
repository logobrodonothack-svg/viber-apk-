package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val sourceUrl: String,
    val resolvedUrl: String,
    val domain: String,
    val mimeType: String,
    val formatLabel: String,
    val qualityLabel: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val localFilePath: String? = null,
    val thumbnailPath: String? = null,
    val posterUrl: String? = null,
    val durationMs: Long = 0L,
    val resolution: String? = null,
    val exportedToGallery: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
