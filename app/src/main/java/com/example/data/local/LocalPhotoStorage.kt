package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StagedPhoto(
    val id: String = UUID.randomUUID().toString(),
    val localFilePath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class LocalPhotoStorage(private val context: Context) {

    private val photosRootDir: File
        get() = File(context.filesDir, "report_photos").apply { mkdirs() }

    private val cameraCacheDir: File
        get() = File(context.cacheDir, "camera_photos").apply { mkdirs() }

    fun createCameraCaptureUri(): Pair<Uri, File> {
        val tempFile = File(cameraCacheDir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return uri to tempFile
    }

    suspend fun stagePhotoFromUri(
        uri: Uri,
        optimizeSize: Boolean
    ): Result<StagedPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            val originalName = resolveDisplayName(uri) ?: "zdjecie_${System.currentTimeMillis()}.jpg"
            val id = UUID.randomUUID().toString()
            val targetFile = File(photosRootDir, "${id}.jpg")

            if (optimizeSize) {
                copyAndOptimizeImage(uri, targetFile)
            } else {
                copyRawImage(uri, targetFile)
            }

            val finalName = if (originalName.endsWith(".jpg", ignoreCase = true) ||
                originalName.endsWith(".jpeg", ignoreCase = true)
            ) {
                originalName
            } else {
                "${originalName.substringBeforeLast('.')}.jpg"
            }

            StagedPhoto(
                id = id,
                localFilePath = targetFile.absolutePath,
                displayName = finalName,
                mimeType = "image/jpeg",
                sizeBytes = targetFile.length()
            )
        }
    }

    suspend fun stagePhotoFromCameraFile(
        cameraFile: File,
        optimizeSize: Boolean
    ): Result<StagedPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.fromFile(cameraFile)
            val id = UUID.randomUUID().toString()
            val targetFile = File(photosRootDir, "${id}.jpg")

            if (optimizeSize) {
                copyAndOptimizeImage(uri, targetFile)
            } else {
                cameraFile.copyTo(targetFile, overwrite = true)
            }
            cameraFile.delete()

            StagedPhoto(
                id = id,
                localFilePath = targetFile.absolutePath,
                displayName = "aparat_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                sizeBytes = targetFile.length()
            )
        }
    }

    fun deleteStagedFile(localFilePath: String) {
        runCatching {
            val file = File(localFilePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun copyRawImage(uri: Uri, targetFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Nie udało się odczytać wybranego pliku.")
    }

    private fun copyAndOptimizeImage(uri: Uri, targetFile: File) {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val maxDim = 2048
        val sampleSize = calculateInSampleSize(
            boundsOptions.outWidth,
            boundsOptions.outHeight,
            maxDim,
            maxDim
        )

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: error("Nie udało się zdekodować zdjęcia.")

        val rotated = rotateBitmapIfNeeded(decoded, orientation)
        val scaled = scaleDownIfNeeded(rotated, maxDim)

        FileOutputStream(targetFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }

        if (scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }
}
