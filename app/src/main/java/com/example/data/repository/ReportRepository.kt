package com.example.data.repository

import com.example.data.drive.DriveUnauthorizedException
import com.example.data.drive.GoogleDriveService
import com.example.data.local.EventCategory
import com.example.data.local.EventReportDao
import com.example.data.local.EventReportEntity
import com.example.data.local.EventReportWithPhotos
import com.example.data.local.LocalPhotoStorage
import com.example.data.local.ReportPhotoEntity
import com.example.data.local.StagedPhoto
import com.example.data.local.UploadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UploadProgress(
    val stepLabel: String,
    val completedSteps: Int,
    val totalSteps: Int
) {
    val fraction: Float
        get() = if (totalSteps <= 0) 0f else (completedSteps.toFloat() / totalSteps).coerceIn(0f, 1f)
}

class ReportRepository(
    private val dao: EventReportDao,
    private val driveService: GoogleDriveService,
    private val photoStorage: LocalPhotoStorage
) {
    val allReports: Flow<List<EventReportWithPhotos>> = dao.observeAllReportsWithPhotos()

    suspend fun createLocalReport(
        eventTimestampMillis: Long,
        category: EventCategory,
        title: String,
        description: String,
        stagedPhotos: List<StagedPhoto>
    ): EventReportWithPhotos {
        val reportId = UUID.randomUUID().toString()
        val report = EventReportEntity(
            id = reportId,
            eventTimestampMillis = eventTimestampMillis,
            category = category.name,
            title = title.trim(),
            description = description.trim(),
            status = UploadStatus.DRAFT,
            createdAtMillis = System.currentTimeMillis()
        )
        val photoEntities = stagedPhotos.mapIndexed { index, staged ->
            ReportPhotoEntity(
                id = staged.id,
                reportId = reportId,
                localFilePath = staged.localFilePath,
                displayName = staged.displayName,
                mimeType = staged.mimeType,
                sizeBytes = staged.sizeBytes,
                orderIndex = index
            )
        }
        dao.insertReportWithPhotos(report, photoEntities)
        return EventReportWithPhotos(report, photoEntities)
    }

    suspend fun uploadReportToDrive(
        reportId: String,
        accessToken: String,
        accountEmail: String?,
        rootFolderName: String,
        onProgress: (UploadProgress) -> Unit
    ): Result<String?> {
        val bundle = dao.getReportWithPhotos(reportId)
            ?: return Result.failure(IllegalStateException("Nie znaleziono raportu."))
        val report = bundle.report
        val photos = bundle.sortedPhotos

        // Total steps: 1 (root folder) + 1 (report folder) + 1 (report files) + photos.size
        val totalSteps = 3 + photos.size

        dao.updateStatus(reportId, UploadStatus.UPLOADING, null)

        return try {
            onProgress(UploadProgress("Przygotowanie folderu „$rootFolderName”...", 0, totalSteps))
            val rootFolder = driveService.findOrCreateRootFolder(accessToken, rootFolderName)

            val folderTitle = buildReportFolderName(report)
            onProgress(UploadProgress("Tworzenie folderu raportu...", 1, totalSteps))
            val reportFolder = driveService.createFolder(
                accessToken = accessToken,
                folderName = folderTitle,
                parentId = rootFolder.id
            )

            onProgress(UploadProgress("Wysyłanie formularza zdarzenia...", 2, totalSteps))
            val txtContent = buildReadableReportText(report, photos, accountEmail)
            driveService.uploadTextFile(
                accessToken = accessToken,
                parentId = reportFolder.id,
                fileName = "raport.txt",
                content = txtContent,
                mimeType = "text/plain; charset=UTF-8"
            )

            val jsonContent = buildJsonReport(report, photos, accountEmail)
            driveService.uploadTextFile(
                accessToken = accessToken,
                parentId = reportFolder.id,
                fileName = "raport.json",
                content = jsonContent,
                mimeType = "application/json; charset=UTF-8"
            )

            photos.forEachIndexed { index, photo ->
                val step = 3 + index
                onProgress(
                    UploadProgress(
                        stepLabel = "Wysyłanie zdjęcia ${index + 1} z ${photos.size}...",
                        completedSteps = step,
                        totalSteps = totalSteps
                    )
                )
                val targetName = String.format(Locale.US, "zdjecie_%02d.jpg", index + 1)
                val uploadedFile = driveService.uploadBinaryFileResumable(
                    accessToken = accessToken,
                    parentId = reportFolder.id,
                    fileName = targetName,
                    file = File(photo.localFilePath),
                    mimeType = photo.mimeType
                )
                dao.updatePhotoDriveFileId(photo.id, uploadedFile.id)
            }

            onProgress(UploadProgress("Zakończono wysyłanie!", totalSteps, totalSteps))
            dao.markUploaded(
                reportId = reportId,
                status = UploadStatus.UPLOADED,
                folderName = reportFolder.name,
                folderId = reportFolder.id,
                webLink = reportFolder.webViewLink,
                uploadedAtMillis = System.currentTimeMillis()
            )
            Result.success(reportFolder.webViewLink)
        } catch (e: DriveUnauthorizedException) {
            dao.updateStatus(reportId, UploadStatus.FAILED, e.message)
            throw e
        } catch (e: CancellationException) {
            dao.updateStatus(reportId, UploadStatus.FAILED, "Wysyłanie zostało przerwane.")
            throw e
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Nie udało się wysłać raportu na Dysk Google."
            dao.updateStatus(reportId, UploadStatus.FAILED, errorMsg)
            Result.failure(e)
        }
    }

    suspend fun deleteReport(bundle: EventReportWithPhotos) {
        bundle.photos.forEach { photo ->
            photoStorage.deleteStagedFile(photo.localFilePath)
        }
        dao.deleteReportById(bundle.report.id)
    }

    private fun buildReportFolderName(report: EventReportEntity): String {
        val datePrefix = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            .format(Date(report.eventTimestampMillis))
        val categoryLabel = enumValues<EventCategory>()
            .firstOrNull { it.name == report.category }?.labelPl ?: "Zdarzenie"
        val slugSource = report.title.ifBlank { report.description }
        val sanitizedSlug = slugSource
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(28)
            .trim('_')
        return if (sanitizedSlug.isNotBlank()) {
            "${datePrefix}_${categoryLabel}_$sanitizedSlug"
        } else {
            "${datePrefix}_$categoryLabel"
        }
    }

    private fun buildReadableReportText(
        report: EventReportEntity,
        photos: List<ReportPhotoEntity>,
        accountEmail: String?
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("pl", "PL"))
        val eventDateStr = dateFormat.format(Date(report.eventTimestampMillis))
        val createdDateStr = dateFormat.format(Date(report.createdAtMillis))
        val categoryLabel = enumValues<EventCategory>()
            .firstOrNull { it.name == report.category }?.labelPl ?: report.category

        return buildString {
            appendLine("==========================================")
            appendLine("RAPORT ZDARZENIA — DZIENNIK ZDARZEŃ")
            appendLine("==========================================")
            appendLine("ID raportu:       ${report.id}")
            appendLine("Data zdarzenia:   $eventDateStr")
            appendLine("Kategoria:        $categoryLabel")
            if (report.title.isNotBlank()) {
                appendLine("Tytuł:            ${report.title}")
            }
            if (!accountEmail.isNullOrBlank()) {
                appendLine("Autor (konto):    $accountEmail")
            }
            appendLine("Utworzono:        $createdDateStr")
            appendLine("Liczba zdjęć:     ${photos.size}")
            appendLine()
            appendLine("--- OPIS ZDARZENIA ---")
            appendLine(report.description)
            appendLine()
            if (photos.isNotEmpty()) {
                appendLine("--- ZAŁĄCZONE ZDJĘCIA ---")
                photos.forEachIndexed { idx, photo ->
                    val targetName = String.format(Locale.US, "zdjecie_%02d.jpg", idx + 1)
                    appendLine("${idx + 1}. $targetName (oryginał: ${photo.displayName})")
                }
            }
        }
    }

    private fun buildJsonReport(
        report: EventReportEntity,
        photos: List<ReportPhotoEntity>,
        accountEmail: String?
    ): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val json = JSONObject().apply {
            put("id", report.id)
            put("eventTimestampIso", isoFormat.format(Date(report.eventTimestampMillis)))
            put("eventTimestampMillis", report.eventTimestampMillis)
            put("category", report.category)
            put("title", report.title)
            put("description", report.description)
            put("authorEmail", accountEmail ?: "")
            put("createdAtIso", isoFormat.format(Date(report.createdAtMillis)))
            put("photoCount", photos.size)
            put(
                "photos",
                JSONArray().apply {
                    photos.forEachIndexed { idx, photo ->
                        put(
                            JSONObject().apply {
                                put("driveFileName", String.format(Locale.US, "zdjecie_%02d.jpg", idx + 1))
                                put("originalName", photo.displayName)
                                put("mimeType", photo.mimeType)
                                put("sizeBytes", photo.sizeBytes)
                            }
                        )
                    }
                }
            )
        }
        return json.toString(2)
    }
}
