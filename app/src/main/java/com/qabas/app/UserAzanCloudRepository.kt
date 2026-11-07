package com.qabas.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Serializable
data class UserAzanVoiceRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * Personal adhan uploads. Rows and Storage objects are private and owner-RLS protected;
 * they are never inserted into the public studio table/bucket.
 */
object UserAzanCloudRepository {
    const val TABLE = "user_azan_voices"
    const val BUCKET = "user-azan-private"
    private const val MAX_BYTES = 25L * 1024L * 1024L
    private const val TAG = "UserAzanCloud"

    fun getDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun getMimeType(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri)?.lowercase() ?: "audio/mpeg"

    fun titleFromFileName(displayName: String): String =
        displayName.substringBeforeLast('.', displayName).trim().take(120)

    suspend fun upload(
        context: Context,
        uri: Uri,
        title: String,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<AzanVoice> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        var storagePath: String? = null
        try {
            val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(SecurityException("سجّل الدخول لحفظ الأذان في حسابك"))
            val client = SupabaseServices.client
                ?: return@withContext Result.failure(IllegalStateException("Supabase غير مهيأ"))
            val safeTitle = title.trim().take(120)
            if (safeTitle.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("عنوان الأذان مطلوب"))
            }
            val mime = getMimeType(context, uri)
            if (!mime.startsWith("audio/")) {
                return@withContext Result.failure(IllegalArgumentException("الملف المحدد ليس ملفاً صوتياً"))
            }

            tempFile = File(context.cacheDir, "user_azan_upload_${UUID.randomUUID()}.bin")
            val source = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("تعذر قراءة الملف المحدد"))
            source.use { input ->
                tempFile!!.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            tempFile!!.delete()
                            return@withContext Result.failure(IllegalArgumentException("الملف يتجاوز الحد 25MB"))
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            val file = tempFile!!
            if (file.length() <= 0L) {
                file.delete()
                return@withContext Result.failure(IllegalArgumentException("الملف الصوتي فارغ"))
            }

            val extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime)
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
                ?: "audio"
            val voiceId = UUID.randomUUID().toString()
            storagePath = "$userId/$voiceId.$extension"

            val uploaded = SupabaseServices.Storage.uploadPrivateFileWithProgress(
                bucketName = BUCKET,
                path = storagePath!!,
                file = file,
                contentType = mime,
                onProgress = onProgress
            )
            if (!uploaded) {
                return@withContext Result.failure(IllegalStateException("فشل رفع الملف الخاص إلى السحابة"))
            }

            val row = UserAzanVoiceRow(
                id = voiceId,
                userId = userId,
                title = safeTitle,
                storagePath = storagePath!!,
                contentType = mime,
                sizeBytes = file.length()
            )
            try {
                client.from(TABLE).insert(row)
            } catch (dbError: Exception) {
                // Roll back the object if the owner-scoped metadata insert did not succeed.
                runCatching { SupabaseServices.Storage.deleteFiles(BUCKET, listOf(storagePath!!)) }
                Log.e(TAG, "metadata insert failed", dbError)
                return@withContext Result.failure(
                    IllegalStateException("تعذر حفظ بيانات الأذان في حسابك: ${dbError.message}")
                )
            }

            Result.success(
                AzanVoice(
                    id = "usercloud_$voiceId",
                    nameAr = safeTitle,
                    muezzinAr = "أذان شخصي",
                    description = "محفوظ في سحابتك الشخصية",
                    streamUrl = storagePath!!,
                    isFajrSpecific = false
                )
            )
        } catch (error: Exception) {
            Log.e(TAG, "personal upload failed", error)
            Result.failure(error)
        } finally {
            tempFile?.delete()
        }
    }

    suspend fun fetchMyVoices(): Result<List<AzanVoice>> = withContext(Dispatchers.IO) {
        try {
            val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.success(emptyList())
            val client = SupabaseServices.client
                ?: return@withContext Result.failure(IllegalStateException("Supabase غير مهيأ"))
            val voices = client.from(TABLE).select {
                filter { eq("user_id", userId) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<UserAzanVoiceRow>().map { row ->
                AzanVoice(
                    id = "usercloud_${row.id}",
                    nameAr = row.title,
                    muezzinAr = "أذان شخصي",
                    description = "محفوظ في سحابتك الشخصية",
                    streamUrl = row.storagePath,
                    isFajrSpecific = false
                )
            }
            Result.success(voices)
        } catch (error: Exception) {
            Log.e(TAG, "fetch personal voices failed: ${error.message}", error)
            Result.failure(error)
        }
    }

    suspend fun resolveStreamUrl(voiceId: String): String? = withContext(Dispatchers.IO) {
        try {
            val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: return@withContext null
            val rowId = voiceId.removePrefix("usercloud_")
            val client = SupabaseServices.client ?: return@withContext null
            val row = client.from(TABLE).select {
                filter {
                    eq("id", rowId)
                    eq("user_id", userId)
                }
                limit(1)
            }.decodeSingleOrNull<UserAzanVoiceRow>() ?: return@withContext null
            SupabaseServices.Storage.createSignedUrl(BUCKET, row.storagePath, kotlin.time.Duration.parse("1h"))
        } catch (error: Exception) {
            Log.e(TAG, "resolve private voice URL failed: ${error.message}", error)
            null
        }
    }

    suspend fun deleteMyVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: return@withContext false
            val rowId = voiceId.removePrefix("usercloud_")
            val client = SupabaseServices.client ?: return@withContext false
            val row = client.from(TABLE).select {
                filter {
                    eq("id", rowId)
                    eq("user_id", userId)
                }
                limit(1)
            }.decodeSingleOrNull<UserAzanVoiceRow>() ?: return@withContext false

            if (!SupabaseServices.Storage.deleteFiles(BUCKET, listOf(row.storagePath))) return@withContext false
            client.from(TABLE).delete {
                filter {
                    eq("id", rowId)
                    eq("user_id", userId)
                }
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "delete personal voice failed: ${error.message}", error)
            false
        }
    }

    suspend fun getDefaultVoiceId(): String? {
        val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: return null
        val settings = SupabaseServices.UserSettings.getUserSettings(userId) ?: return null
        return runCatching {
            JSONObject(settings.preferencesJson).optString("azan_default_voice_id").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun saveDefaultVoiceId(voiceId: String): Boolean {
        val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: return false
        val settings = SupabaseServices.UserSettings.getUserSettings(userId)
        val json = runCatching {
            JSONObject(settings?.preferencesJson?.takeIf { it.isNotBlank() } ?: "{}")
                .put("azan_default_voice_id", voiceId)
                .toString()
        }.getOrElse {
            JSONObject().put("azan_default_voice_id", voiceId).toString()
        }
        return SupabaseServices.UserSettings.saveUserSettings(userId, json)
    }

    suspend fun updateTitle(voiceId: String, title: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = Auth.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: return@withContext false
            val safeTitle = title.trim().take(120).takeIf { it.isNotBlank() } ?: return@withContext false
            val client = SupabaseServices.client ?: return@withContext false
            client.from(TABLE).update(mapOf("title" to safeTitle)) {
                filter {
                    eq("id", voiceId.removePrefix("usercloud_"))
                    eq("user_id", userId)
                }
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "update personal title failed: ${error.message}", error)
            false
        }
    }
}
