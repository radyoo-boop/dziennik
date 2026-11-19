package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

enum class UploadStatus {
    DRAFT,
    UPLOADING,
    UPLOADED,
    FAILED
}

enum class EventCategory(val labelPl: String) {
    INSPECTION("Inspekcja"),
    INCIDENT("Zdarzenie"),
    FAULT("Usterka"),
    DELIVERY("Dostawa"),
    NOTE("Notatka")
}

@Entity(tableName = "event_reports")
data class EventReportEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventTimestampMillis: Long,
    val category: String = EventCategory.INCIDENT.name,
    val title: String = "",
    val description: String,
    val driveFolderName: String = "",
    val driveFolderId: String? = null,
    val driveFolderWebLink: String? = null,
    val status: UploadStatus = UploadStatus.DRAFT,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val uploadedAtMillis: Long? = null
)

@Entity(
    tableName = "report_photos",
    foreignKeys = [
        ForeignKey(
            entity = EventReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["reportId"])]
)
data class ReportPhotoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val localFilePath: String,
    val displayName: String,
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val driveFileId: String? = null,
    val orderIndex: Int = 0
)

data class EventReportWithPhotos(
    @Embedded val report: EventReportEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reportId"
    )
    val photos: List<ReportPhotoEntity>
) {
    val sortedPhotos: List<ReportPhotoEntity>
        get() = photos.sortedBy { it.orderIndex }
}
