package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, " +
            "speedBytesPerSec = :speed, status = :status, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long,
        status: DownloadStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET exportedToGallery = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markExported(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
