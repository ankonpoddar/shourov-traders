package com.example.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GoogleDriveBackupClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val driveApiBaseUrl: String = "https://www.googleapis.com/drive/v3",
    private val driveUploadBaseUrl: String = "https://www.googleapis.com/upload/drive/v3",
) {

    suspend fun findLatestBackupFile(
        accessToken: String,
        fileName: String = DriveBackupContract.BACKUP_FILE_NAME,
    ): Result<DriveBackupFileMetadata?> = withContext(Dispatchers.IO) {
        try {
            val url = "$driveApiBaseUrl/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "name = '${escapeDriveQuery(fileName)}' and trashed = false")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("fields", "files(id,name,modifiedTime,size)")
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("pageSize", "1")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(response.code, bodyString))
                }
                val root = JSONObject(bodyString)
                val files = root.optJSONArray("files")
                if (files == null || files.length() == 0) {
                    Result.success(null)
                } else {
                    Result.success(parseFileMetadata(files.getJSONObject(0)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBackupJson(
        accessToken: String,
        jsonContent: String,
        existingFileId: String? = null,
        fileName: String = DriveBackupContract.BACKUP_FILE_NAME,
    ): Result<DriveBackupFileMetadata> = withContext(Dispatchers.IO) {
        try {
            val resolvedFileId = existingFileId?.takeIf { it.isNotBlank() }
                ?: findLatestBackupFile(accessToken, fileName).getOrElse { return@withContext Result.failure(it) }?.fileId

            if (resolvedFileId != null) {
                val patchResult = updateExistingBackupFile(accessToken, resolvedFileId, jsonContent, fileName)
                if (patchResult.isSuccess) {
                    return@withContext patchResult
                }
                val patchError = patchResult.exceptionOrNull()
                val shouldCreateFresh = patchError is DriveNotFoundException
                if (!shouldCreateFresh) {
                    return@withContext patchResult
                }
            }

            createNewBackupFile(accessToken, jsonContent, fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackupJson(
        accessToken: String,
        fileId: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$driveApiBaseUrl/files/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "media")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(response.code, bodyString))
                }
                Result.success(bodyString)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createNewBackupFile(
        accessToken: String,
        jsonContent: String,
        fileName: String,
    ): Result<DriveBackupFileMetadata> {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(jsonContent.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val url = "$driveUploadBaseUrl/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,modifiedTime,size")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Result.failure(mapHttpError(response.code, bodyString))
            } else {
                Result.success(parseFileMetadata(JSONObject(bodyString), fallbackFileName = fileName))
            }
        }
    }

    private fun updateExistingBackupFile(
        accessToken: String,
        fileId: String,
        jsonContent: String,
        fileName: String,
    ): Result<DriveBackupFileMetadata> {
        val url = "$driveUploadBaseUrl/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", "id,name,modifiedTime,size")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .patch(jsonContent.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    Result.failure(DriveNotFoundException("Backup file $fileId not found on Google Drive."))
                } else {
                    Result.failure(mapHttpError(response.code, bodyString))
                }
            } else {
                Result.success(
                    parseFileMetadata(
                        obj = JSONObject(bodyString),
                        fallbackFileId = fileId,
                        fallbackFileName = fileName,
                    )
                )
            }
        }
    }

    private fun parseFileMetadata(
        obj: JSONObject,
        fallbackFileId: String = "",
        fallbackFileName: String = DriveBackupContract.BACKUP_FILE_NAME,
    ): DriveBackupFileMetadata {
        val sizeStr = obj.optString("size", "0")
        return DriveBackupFileMetadata(
            fileId = obj.optString("id", "").ifBlank { fallbackFileId },
            fileName = obj.optString("name", "").ifBlank { fallbackFileName },
            modifiedTimeIso = obj.optString("modifiedTime", ""),
            sizeBytes = sizeStr.toLongOrNull() ?: obj.optLong("size", 0L),
        )
    }

    private fun escapeDriveQuery(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun mapHttpError(code: Int, body: String): Exception {
        val apiMessage = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val readable = apiMessage.ifBlank { "Google Drive request failed (HTTP $code)" }
        return if (code == 401 || code == 403) {
            DriveAuthException(code, readable)
        } else {
            IOException(readable)
        }
    }

    private class DriveNotFoundException(message: String) : IOException(message)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
