package com.musync.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musync.app.data.local.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownload(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun getDownloadFlow(id: String): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatusAndProgress(id: String, status: String, progress: Float, errorMessage: String? = null)

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, fileSizeBytes = :fileSize WHERE id = :id")
    suspend fun markCompleted(id: String, fileSize: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()

    @Query("SELECT SUM(fileSizeBytes) FROM downloads WHERE status = 'COMPLETED'")
    fun getTotalStorageUsedFlow(): Flow<Long?>

    @Query("SELECT SUM(fileSizeBytes) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getTotalStorageUsed(): Long?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun getCompletedCountFlow(): Flow<Int>
}
