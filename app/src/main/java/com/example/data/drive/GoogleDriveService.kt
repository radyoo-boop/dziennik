package com.example.data.drive

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class DriveFileDto(
    val id: String,
    val name: String,
    val webViewLink: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateDriveFileMetadata(
    val name: String,
    val mimeType: String? = null,
    val parents: List<String>? = null
)

class DriveUnauthorizedException(message: String) : IOException(message)
class DriveApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

class GoogleDriveService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
) {
    private val fileAdapter = moshi.adapter(DriveFileDto::class.java)
    private val fileListAdapter = moshi.adapter(DriveFileListDto::class.java)
    private val metadataAdapter = moshi.adapter(CreateDriveFileMetadata::class.java)

    suspend fun findOrCreateRootFolder(
        accessToken: String,
        folderName: String
    ): DriveFileDto = withContext(Dispatchers.IO) {
        val escapedName = folderName.replace("'", "\\'")
        val query = "name = '$escapedName' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name,webViewLink)")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val listResponse = executeAndParse(request) { body ->
            fileListAdapter.fromJson(body) ?: DriveFileListDto()
        }

        listResponse.files.firstOrNull()?.let { return@withContext it }

        createFolder(
            accessToken = accessToken,
            folderName = folderName,
            parentId = null
        )
    }

    suspend fun createFolder(
        accessToken: String,
        folderName: String,
        parentId: String?
    ): DriveFileDto = withContext(Dispatchers.IO) {
        val metadata = CreateDriveFileMetadata(
            name = folderName,
            mimeType = FOLDER_MIME_TYPE,
            parents = parentId?.let { listOf(it) }
        )
        val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,name,webViewLink")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(metadataAdapter.toJson(metadata).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeAndParse(request) { body ->
            fileAdapter.fromJson(body)
                ?: throw DriveApiException("Nieprawidłowa odpowiedź serwera podczas tworzenia folderu.")
        }
    }

    suspend fun uploadTextFile(
        accessToken: String,
        parentId: String,
        fileName: String,
        content: String,
        mimeType: String = "text/plain; charset=UTF-8"
    ): DriveFileDto = withContext(Dispatchers.IO) {
        val metadataJson = metadataAdapter.toJson(
            CreateDriveFileMetadata(
                name = fileName,
                parents = listOf(parentId)
            )
        )

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(content.toRequestBody(mimeType.toMediaType()))
            .build()

        val url = DRIVE_UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,webViewLink")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        executeAndParse(request) { body ->
            fileAdapter.fromJson(body)
                ?: throw DriveApiException("Nie udało się odczytać odpowiedzi po wysłaniu pliku $fileName.")
        }
    }

    suspend fun uploadBinaryFileResumable(
        accessToken: String,
        parentId: String,
        fileName: String,
        file: File,
        mimeType: String
    ): DriveFileDto = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw DriveApiException("Brak pliku zdjęcia na urządzeniu: $fileName")
        }

        val metadataJson = metadataAdapter.toJson(
            CreateDriveFileMetadata(
                name = fileName,
                parents = listOf(parentId)
            )
        )

        val initUrl = DRIVE_UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", "id,name,webViewLink")
            .build()

        val fileLength = file.length()
        val initRequest = Request.Builder()
            .url(initUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Type", mimeType)
            .header("X-Upload-Content-Length", fileLength.toString())
            .post(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val uploadSessionUrl = client.newCall(initRequest).execute().use { response ->
            checkHttpError(response.code, response.body?.string().orEmpty())
            response.header("Location")
                ?: throw DriveApiException("Dysk Google nie zwrócił adresu sesji wysyłania dla $fileName.")
        }

        val streamingBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()
            override fun contentLength() = fileLength
            override fun writeTo(sink: BufferedSink) {
                file.source().use { source ->
                    sink.writeAll(source)
                }
            }
        }

        val uploadRequest = Request.Builder()
            .url(uploadSessionUrl)
            .put(streamingBody)
            .build()

        executeAndParse(uploadRequest) { body ->
            fileAdapter.fromJson(body)
                ?: throw DriveApiException("Nie udało się odczytać potwierdzenia wysłania zdjęcia $fileName.")
        }
    }

    private inline fun <T> executeAndParse(
        request: Request,
        parser: (String) -> T
    ): T {
        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                checkHttpError(response.code, rawBody)
                return parser(rawBody)
            }
        } catch (e: DriveUnauthorizedException) {
            throw e
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(
                "Błąd połączenia sieciowego z Dyskiem Google: ${e.localizedMessage ?: "sprawdź internet"}",
                e
            )
        }
    }

    private fun checkHttpError(statusCode: Int, rawBody: String) {
        if (statusCode in 200..299) return
        if (statusCode == 401) {
            throw DriveUnauthorizedException("Sesja Google Drive wygasła. Wymagane odświeżenie tokenu.")
        }
        val details = rawBody.take(180).ifBlank { "kod HTTP $statusCode" }
        throw DriveApiException("Dysk Google odrzucił żądanie ($statusCode): $details")
    }

    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}
