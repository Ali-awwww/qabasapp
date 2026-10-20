package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AzanVoiceRow(
    val id: String,
    @SerialName("name_ar") val nameAr: String,
    @SerialName("muezzin_ar") val muezzinAr: String,
    val description: String = "",
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("is_fajr_specific") val isFajrSpecific: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true
)

/**
 * مستودع أصوات الأذان السحابية (Supabase):
 * - القراءة: متاحة للجميع (المطور + المستخدم).
 * - الرفع/الحذف: للمطور فقط (يُفحص عبر AdminGuard قبل النداء).
 */
object AzanCloudRepository {
    private const val TAG = "AzanCloud"
    const val BUCKET = "azan-voices"
    const val TABLE = "azan_voices"

    suspend fun fetchCloudVoices(): List<AzanVoice> = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client ?: return@withContext emptyList()
            val rows = client.from(TABLE).select {
                filter { eq("is_active", true) }
            }.decodeList<AzanVoiceRow>()
            rows.map {
                AzanVoice(
                    id = "cloud_${it.id}",
                    nameAr = it.nameAr,
                    muezzinAr = it.muezzinAr,
                    description = it.description.ifBlank { "أذان سحابي من استوديو قبس ☁️" },
                    streamUrl = it.audioUrl,
                    isFajrSpecific = it.isFajrSpecific
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * يرفع ملف صوت الأذان إلى Supabase Storage ثم يحفظ صفاً في جدول azan_voices.
     * لا يُستدعى إلا من حساب المطور (AdminGuard). يبث تقدم الرفع عبر onProgress لعرض العدّاد.
     */
    suspend fun uploadAzanVoice(
        context: Context,
        fileUri: Uri,
        nameAr: String,
        muezzinAr: String,
        description: String,
        isFajrSpecific: Boolean,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!AdminGuard.isDashboardAccessAllowed(context)) {
                return@withContext Result.failure(SecurityException("الرفع السحابي مخصص للمطور فقط"))
            }
            val client = SupabaseServices.client
                ?: return@withContext Result.failure(IllegalStateException("Supabase غير مهيأ"))
            // ننسخ إلى ملف مؤقت لرفعه بتدفق مع عدّاد تقدم (بلا تحميل كامل في الذاكرة)
            val tmp = java.io.File(context.cacheDir, "azan_upload_${UUID.randomUUID()}.mp3")
            val totalBytes = try {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                tmp.length()
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("تعذر قراءة الملف"))
            }
            if (totalBytes <= 0) return@withContext Result.failure(IllegalStateException("الملف فارغ"))
            if (totalBytes > 25 * 1024 * 1024) {
                tmp.delete()
                return@withContext Result.failure(IllegalStateException("حجم الملف يتجاوز 25MB"))
            }
            val path = "azan_${UUID.randomUUID()}.mp3"
            val publicUrl = SupabaseServices.Storage.uploadFileWithProgress(
                bucketName = BUCKET, path = path, file = tmp,
                contentType = "audio/mpeg", upsert = false,
                onProgress = onProgress
            )
            tmp.delete()
            if (publicUrl == null) {
                return@withContext Result.failure(IllegalStateException("فشل رفع الملف للسحابة"))
            }
            val row = AzanVoiceRow(
                id = UUID.randomUUID().toString(),
                nameAr = nameAr.trim(),
                muezzinAr = muezzinAr.trim(),
                description = description.trim(),
                audioUrl = publicUrl,
                isFajrSpecific = isFajrSpecific
            )
            // ⚠️ كان Insert لا تُفحص نتيجته — فيفشل صامتاً ويظهر للمستخدم "نجاح" بلا صف فعلي
            val insertError = client.from(TABLE).insert(row).error
            if (insertError != null) {
                Log.e(TAG, "insert failed: ${insertError.message} / ${insertError.code}")
                return@withContext Result.failure(
                    IllegalStateException("تعذّر حفظ الصوت في قاعدة البيانات: ${insertError.message}")
                )
            }
            // تحديث الكاش المحلي فوراً
            MuezzinVoiceManager.cacheCloudVoices(
                context,
                fetchCloudVoices()
            )
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudVoice(context: Context, cloudVoice: AzanVoice): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!AdminGuard.isDashboardAccessAllowed(context)) return@withContext false
            val client = SupabaseServices.client ?: return@withContext false
            val rowId = cloudVoice.id.removePrefix("cloud_")
            client.from(TABLE).delete { filter { eq("id", rowId) } }
            MuezzinVoiceManager.cacheCloudVoices(context, fetchCloudVoices())
            true
        } catch (e: Exception) {
            Log.e(TAG, "delete failed: ${e.message}")
            false
        }
    }
}
