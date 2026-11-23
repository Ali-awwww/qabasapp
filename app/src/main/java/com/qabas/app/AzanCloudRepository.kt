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
data class AzanVoiceDescriptionUpdate(
    val description: String
)

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

    /**
     * يجلب الأصوات السحابية النشطة. يعيد [Result] (ق50): الفشل كان
     * يُبتلع إلى قائمة فارغة فيُعامل كهو «لا أصوات في الجدول» تماماً،
     * فتُمسح ذاكرة الكاش المحلية بقائمة فارغة عند كل إعادة جلب فاشلة
     * (بعد رفع أو تعديل أو حذف ناجح)، ويُعرض للمستخدم «لا أصوات عامة»
     * عند انقطاع الشبكة. النجاح بقائمة فارغة يعني فعلاً جدولاً فارغاً.
     */
    suspend fun fetchCloudVoices(): Result<List<AzanVoice>> = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client
                ?: return@withContext Result.failure(IllegalStateException("Supabase غير مهيأ"))
            val rows = client.from(TABLE).select {
                filter { eq("is_active", true) }
            }.decodeList<AzanVoiceRow>()
            Result.success(rows.map {
                AzanVoice(
                    id = "cloud_${it.id}",
                    nameAr = it.nameAr,
                    muezzinAr = it.muezzinAr,
                    description = it.description.ifBlank { "أذان سحابي من استوديو خادم المسلم ☁️" },
                    streamUrl = it.audioUrl,
                    isFajrSpecific = it.isFajrSpecific
                )
            })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "fetch failed: ${e.message}")
            Result.failure(e)
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
            // ⚠️ كان Insert لا تُفحص نتيجته — فيفشل صامتاً ويظهر للمستخدم "نجاح" بلا صف فعلي.
            // في supabase-kt 3.x، insert() يرمي استثناءً (وليس يُرجع .error) при فشل RLS
            try {
                client.from(TABLE).insert(row)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "insert failed", e)
                return@withContext Result.failure(
                    IllegalStateException("تعذّر حفظ الصوت في قاعدة البيانات: ${e.message}")
                )
            }
            // تحديث الكاش المحلي فوراً — عند نجاح إعادة الجلب فقط (ق50):
            // فشلها كان يمسح الكاش بقائمة فارغة بعد رفع ناجح.
            fetchCloudVoices().onSuccess {
                MuezzinVoiceManager.cacheCloudVoices(context, it)
            }
            Result.success(publicUrl)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateCloudVoiceDescription(
        context: Context,
        cloudVoice: AzanVoice,
        description: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!AdminGuard.isDashboardAccessAllowed(context)) return@withContext false
            val client = SupabaseServices.client ?: return@withContext false
            val rowId = cloudVoice.id.removePrefix("cloud_")
            client.from(TABLE).update(AzanVoiceDescriptionUpdate(description.trim())) {
                filter { eq("id", rowId) }
            }
            // الكاش يُحدَّث عند نجاح إعادة الجلب فقط (ق50) لا بقائمة فارغة.
            fetchCloudVoices().onSuccess {
                MuezzinVoiceManager.cacheCloudVoices(context, it)
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "description update failed: ${e.message}", e)
            false
        }
    }

    suspend fun deleteCloudVoice(context: Context, cloudVoice: AzanVoice): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!AdminGuard.isDashboardAccessAllowed(context)) return@withContext false
            val client = SupabaseServices.client ?: return@withContext false
            val rowId = cloudVoice.id.removePrefix("cloud_")
            val storagePath = SupabaseServices.Storage.normalizeObjectPath(BUCKET, cloudVoice.streamUrl)
            // Delete the Storage object through the shared service before removing its database row.
            if (storagePath != null) {
                val storageDeleted = SupabaseServices.Storage.deleteFiles(BUCKET, listOf(storagePath))
                if (!storageDeleted) {
                    Log.e(TAG, "Storage deletion failed; keeping database row for voice $rowId")
                    return@withContext false
                }
            }
            client.from(TABLE).delete { filter { eq("id", rowId) } }
            // الكاش يُحدَّث عند نجاح إعادة الجلب فقط (ق50) لا بقائمة فارغة.
            fetchCloudVoices().onSuccess {
                MuezzinVoiceManager.cacheCloudVoices(context, it)
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "delete failed: ${e.message}", e)
            false
        }
    }
}
