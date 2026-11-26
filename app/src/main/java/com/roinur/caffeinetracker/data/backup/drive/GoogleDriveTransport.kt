package com.roinur.caffeinetracker.data.backup.drive

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class DriveHttpException(val code: Int) : IOException(when (code) {
    401 -> "Google-behörigheten har gått ut. Anslut Google Drive igen."
    403 -> "Google Drive nekade åtkomst. Kontrollera Drive API, OAuth-registrering, behörigheter och lagringsutrymme."
    429 -> "Google Drive begränsar tillfälligt anrop. Backupen försöker igen senare."
    else -> "Google Drive-anropet misslyckades (HTTP $code)."
})

/** Dedicated client: no logged tokens, bounded response reads, no redirects. */
internal class GoogleDriveTransport(private val token: String, private val channel: String) : DriveBackupTransport {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val TEXT = "application/json; charset=utf-8".toMediaType()
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private fun properties(
        verified: Boolean? = null,
        parentId: String = "",
        writerId: String = ""
    ) = JSONObject()
        .put("caffeine_kind", "full_backup_v1")
        .put("caffeine_channel", channel)
        .apply {
            verified?.let { put("caffeine_verified", it.toString()) }
            if (parentId.isNotBlank()) put("caffeine_parent", parentId)
            if (writerId.isNotBlank()) put("caffeine_writer", writerId)
        }

    private fun markerQuery() = "appProperties has { key='caffeine_kind' and value='full_backup_v1' } and " +
        "appProperties has { key='caffeine_channel' and value='${channel.replace("'", "\\'")}' }"

    private fun request(method: String, path: String, query: Map<String, String> = emptyMap(), body: RequestBody? = null): String {
        val url = "https://www.googleapis.com/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").method(method, body).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DriveHttpException(response.code)
            val responseBody = response.body ?: return@use ""
            require(responseBody.contentLength() <= CaffeineCloudBackup.MAX_BYTES) { "Svaret från Drive är för stort." }
            responseBody.source().use { source ->
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, 8192)
                    if (read < 0) break
                    require(buffer.size <= CaffeineCloudBackup.MAX_BYTES) { "Svaret från Drive är för stort." }
                }
                buffer.readUtf8()
            }
        }
    }

    private fun list(query: String): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        var page = ""
        do {
            val result = JSONObject(request("GET", "drive/v3/files", buildMap {
                put("q", "trashed=false and ($query)")
                put("fields", "nextPageToken,files(id,name,createdTime,appProperties)")
                put("orderBy", "createdTime desc")
                put("pageSize", "100")
                if (page.isNotBlank()) put("pageToken", page)
            }))
            val files = result.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) rows += files.getJSONObject(index)
            page = result.optString("nextPageToken")
            check(rows.size <= 2000) { "För många molnbackuper. Granska Drive-mappen manuellt." }
        } while (page.isNotBlank())
        return rows
    }

    override fun listVerified(): List<DriveBackupFile> = list(
        "${markerQuery()} and mimeType!='application/vnd.google-apps.folder' and " +
            "appProperties has { key='caffeine_verified' and value='true' }"
    ).map {
        val props = it.optJSONObject("appProperties")
        DriveBackupFile(
            id = it.getString("id"),
            name = it.getString("name"),
            createdAt = it.getString("createdTime"),
            parentId = props?.optString("caffeine_parent").orEmpty(),
            writerId = props?.optString("caffeine_writer").orEmpty()
        )
    }

    private fun folder(): String {
        list("${markerQuery()} and mimeType='application/vnd.google-apps.folder'").firstOrNull()?.let { return it.getString("id") }
        val metadata = JSONObject()
            .put("name", "Caffeine Tracker Backups")
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("appProperties", properties())
        return JSONObject(request("POST", "drive/v3/files", mapOf("fields" to "id"), metadata.toString().toRequestBody(JSON))).getString("id")
    }

    override fun uploadPending(text: String, parentId: String, writerId: String): DriveBackupFile =
        uploadPendingBody(text.toRequestBody(TEXT), parentId, writerId)

    override fun uploadPending(file: File, parentId: String, writerId: String): DriveBackupFile =
        uploadPendingBody(file.asRequestBody(TEXT), parentId, writerId)

    private fun uploadPendingBody(payload: RequestBody, parentId: String, writerId: String): DriveBackupFile {
        val name = "Caffeine-Tracker-${Instant.now().toString().replace(':', '-')}-${UUID.randomUUID().toString().take(8)}.json"
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray(listOf(folder())))
            .put("appProperties", properties(verified = false, parentId = parentId, writerId = writerId))
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(JSON))
            .addPart(payload)
            .build()
        val result = JSONObject(request("POST", "upload/drive/v3/files", mapOf("uploadType" to "multipart", "fields" to "id,name,createdTime"), body))
        return DriveBackupFile(
            id = result.getString("id"),
            name = result.getString("name"),
            createdAt = result.getString("createdTime"),
            parentId = parentId,
            writerId = writerId
        )
    }

    override fun download(id: String): String = request("GET", "drive/v3/files/$id", mapOf("alt" to "media"))

    override fun downloadChecksum(id: String): DriveDownloadChecksum {
        val url = "https://www.googleapis.com/drive/v3/files/$id".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DriveHttpException(response.code)
            val body = requireNotNull(response.body) { "Drive returnerade inget innehåll." }
            require(body.contentLength() <= CaffeineCloudBackup.MAX_BYTES) { "Svaret från Drive är för stort." }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= CaffeineCloudBackup.MAX_BYTES) { "Svaret från Drive är för stort." }
                    digest.update(buffer, 0, count)
                }
            }
            DriveDownloadChecksum(total, digest.digest().joinToString("") { "%02x".format(it) })
        }
    }

    override fun markVerified(id: String, parentId: String, writerId: String) {
        request(
            "PATCH",
            "drive/v3/files/$id",
            mapOf("fields" to "id"),
            JSONObject().put("appProperties", properties(verified = true, parentId = parentId, writerId = writerId))
                .toString().toRequestBody(JSON)
        )
    }

    override fun delete(id: String) {
        request(
            "PATCH",
            "drive/v3/files/$id",
            mapOf("fields" to "id"),
            JSONObject().put("trashed", true).toString().toRequestBody(JSON)
        )
    }
}
