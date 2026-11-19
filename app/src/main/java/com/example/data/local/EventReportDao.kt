package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventReportDao {
    @Transaction
    @Query("SELECT * FROM event_reports ORDER BY createdAtMillis DESC")
    fun observeAllReportsWithPhotos(): Flow<List<EventReportWithPhotos>>

    @Transaction
    @Query("SELECT * FROM event_reports WHERE id = :reportId LIMIT 1")
    suspend fun getReportWithPhotos(reportId: String): EventReportWithPhotos?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: EventReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<ReportPhotoEntity>)

    @Transaction
    suspend fun insertReportWithPhotos(report: EventReportEntity, photos: List<ReportPhotoEntity>) {
        insertReport(report)
        if (photos.isNotEmpty()) {
            insertPhotos(photos)
        }
    }

    @Query(
        "UPDATE event_reports SET status = :status, errorMessage = :errorMessage WHERE id = :reportId"
    )
    suspend fun updateStatus(reportId: String, status: UploadStatus, errorMessage: String? = null)

    @Query(
        """
        UPDATE event_reports
        SET status = :status,
            driveFolderName = :folderName,
            driveFolderId = :folderId,
            driveFolderWebLink = :webLink,
            errorMessage = NULL,
            uploadedAtMillis = :uploadedAtMillis
        WHERE id = :reportId
        """
    )
    suspend fun markUploaded(
        reportId: String,
        status: UploadStatus,
        folderName: String,
        folderId: String,
        webLink: String?,
        uploadedAtMillis: Long
    )

    @Query("UPDATE report_photos SET driveFileId = :driveFileId WHERE id = :photoId")
    suspend fun updatePhotoDriveFileId(photoId: String, driveFileId: String)

    @Query("DELETE FROM event_reports WHERE id = :reportId")
    suspend fun deleteReportById(reportId: String)
}
