package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * نوع الوسائط في المتصفح الموحد
 */
enum class CentralMediaType(val label: String, val icon: String) {
    ALL("الكل", "🌟"),
    IMAGE("صور وبوسترات", "🖼️"),
    VIDEO("فيديوهات وريلز", "🎬"),
    AUDIO("صوتيات وتعليق", "🎙️"),
    CLOUD("سحابي Supabase", "☁️"),
    AI_GENERATED("توليد الذكاء", "🤖")
}

/**
 * مصدر الملف
 */
enum class CentralMediaSource(val label: String, val badgeColorHex: String) {
    SUPABASE_CLOUD("Supabase Cloud", "#10B981"),
    AI_FLUX("FLUX AI", "#F59E0B"),
    AI_VOICE("Edge-TTS Voice", "#EC4899"),
    LOCAL_RENDER("رندرة محلية", "#38BDF8"),
    USER_UPLOAD("مرفوع من جهازي", "#8B5CF6")
}

/**
 * نموذج العنصر الموحد في متصفح الوسائط
 */
data class CentralMediaAsset(
    val id: String,
    val title: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val type: CentralMediaType,
    val source: CentralMediaSource,
    val sizeBytes: Long = 0L,
    val formattedSize: String = "0 KB",
    val dateAdded: Long = System.currentTimeMillis(),
    val formattedDate: String = "",
    val bucketName: String? = null,
    val isUploadedToCloud: Boolean = false,
    val mimeType: String = "",
    val promptOrNote: String = ""
) {
    val displayUrl: String
        get() = remoteUrl ?: (if (localPath != null) "file://$localPath" else "")
}

/**
 * 📦 مدير ومستودع الوسائط المركزي (Central Media Manager)
 * يوحّد جميع الملفات المولّدة بالذكاء الاصطناعي والمرفوعة محلياً والسحابية عبر Supabase Storage
 * في مستودع موحد تفاعلي وسريع.
 */
object CentralMediaManager {
    private const val TAG = "CentralMediaManager"

    private val _assets = MutableStateFlow<List<CentralMediaAsset>>(emptyList())
    val assets: StateFlow<List<CentralMediaAsset>> = _assets.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("جاهز")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    /**
     * مسح وتحميل جميع الوسائط (المحلية + السحابية من Supabase Storage)
     */
    suspend fun refreshAllMedia(context: Context) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _cloudSyncStatus.value = "جاري مسح المجلدات المحلية ومزامنة سحابة Supabase..."

        val combinedList = mutableListOf<CentralMediaAsset>()

        // 1) فحص المجلدات المحلية (FLUX AI, Audio, Videos)
        try {
            val localAssets = scanLocalDirectories(context)
            combinedList.addAll(localAssets)
            Log.d(TAG, "Scanned ${localAssets.size} local media assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local assets", e)
        }

        // 2) جلب الملفات من سحابة Supabase Storage إن وُجدت
        if (SupabaseServices.isSupabaseAvailable) {
            try {
                _cloudSyncStatus.value = "جاري الاستعلام من Supabase Storage..."
                for (bucket in SupabaseServices.Storage.DEFAULT_BUCKETS) {
                    val remoteFiles = SupabaseServices.Storage.listFiles(bucket)
                    for (file in remoteFiles) {
                        val mType = deduceTypeFromFileName(file.name)
                        // الحجم غير معروف من listFiles (يعيد 0 دائماً) فلا
                        // يُدَّعى «0 KB»؛ والتاريخ الحقيقي من createdAt لا «الآن»
                        // (كان كل سحابي يُختم بالآن فيتصدّر الترتيب دائماً).
                        val createdMillis = file.createdAt?.let {
                            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                        } ?: System.currentTimeMillis()
                        val asset = CentralMediaAsset(
                            id = "supabase_${bucket}_${file.name}",
                            title = file.name,
                            localPath = null,
                            remoteUrl = file.publicUrl,
                            type = mType,
                            source = CentralMediaSource.SUPABASE_CLOUD,
                            sizeBytes = file.sizeBytes,
                            formattedSize = if (file.sizeBytes > 0) formatFileSize(file.sizeBytes) else "—",
                            dateAdded = createdMillis,
                            formattedDate = dateFormat.format(Date(createdMillis)),
                            bucketName = bucket,
                            isUploadedToCloud = true,
                            mimeType = deduceMimeType(file.name),
                            promptOrNote = "سحابة Supabase ($bucket)"
                        )
                        combinedList.add(asset)
                    }
                }
                _cloudSyncStatus.value = "تمت المزامنة السحابية بنجاح ✓"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "Supabase storage sync warning", e)
                _cloudSyncStatus.value = "تم المسح المحلي (السحابة غير متصلة)"
            }
        } else {
            _cloudSyncStatus.value = "الوضع المحلي المستقل (Zero-Cloud)"
        }

        // إزالة التكرارات وترتيب الملفات من الأحدث للأقدم
        val distinctSorted = combinedList
            .distinctBy { it.localPath ?: it.remoteUrl ?: it.id }
            .sortedByDescending { it.dateAdded }

        _assets.value = distinctSorted
        _isLoading.value = false
    }

    /**
     * مسح المجلدات المحلية للتطبيق
     */
    private fun scanLocalDirectories(context: Context): List<CentralMediaAsset> {
        val list = mutableListOf<CentralMediaAsset>()

        // 1. صور وبوسترات FLUX
        val fluxDir = File(context.cacheDir, "pollinations_ai")
        if (fluxDir.exists()) {
            fluxDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 500) {
                    list.add(
                        CentralMediaAsset(
                            id = "local_flux_${file.name}",
                            title = "توليد FLUX: ${file.name.take(16)}",
                            localPath = file.absolutePath,
                            remoteUrl = null,
                            type = CentralMediaType.IMAGE,
                            source = CentralMediaSource.AI_FLUX,
                            sizeBytes = file.length(),
                            formattedSize = formatFileSize(file.length()),
                            dateAdded = file.lastModified(),
                            formattedDate = dateFormat.format(Date(file.lastModified())),
                            bucketName = "ai-generated",
                            isUploadedToCloud = false,
                            mimeType = deduceMimeType(file.name),
                            promptOrNote = "صورة مولدة بالذكاء الاصطناعي"
                        )
                    )
                }
            }
        }

        // 2. ملفات الصوت والتسجيلات — لكل مجلد مصدره ووصفه الحقيقيان:
        // edge_tts تعليق Edge-TTS، أما تسجيلات الجهاز فليست «تعليقاً عصبياً».
        // مجلد audio_clones حُذف من المسح: بقايا ميزة الاستنساخ المقتولة (ق19).
        val audioDirs = listOf(
            File(context.cacheDir, "edge_tts") to
                (CentralMediaSource.AI_VOICE to "تعليق صوتي Edge-TTS"),
            File(context.filesDir, "recordings") to
                (CentralMediaSource.USER_UPLOAD to "تسجيل صوتي من الجهاز")
        )
        for ((dir, audioLabel) in audioDirs) {
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 500) {
                        list.add(
                            CentralMediaAsset(
                                id = "local_audio_${file.name}",
                                title = "صوت: ${file.name}",
                                localPath = file.absolutePath,
                                remoteUrl = null,
                                type = CentralMediaType.AUDIO,
                                source = audioLabel.first,
                                sizeBytes = file.length(),
                                formattedSize = formatFileSize(file.length()),
                                dateAdded = file.lastModified(),
                                formattedDate = dateFormat.format(Date(file.lastModified())),
                                bucketName = "audio-tracks",
                                isUploadedToCloud = false,
                                mimeType = deduceMimeType(file.name),
                                promptOrNote = audioLabel.second
                            )
                        )
                    }
                }
            }
        }

        // 3. فيديوهات تم رندرتها
        val videoDirs = listOf(
            File(context.cacheDir, "videos"),
            File(context.cacheDir, "money_printer"),
            File(context.cacheDir, "exports")
        )
        for (dir in videoDirs) {
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".mp4") && file.length() > 1000) {
                        list.add(
                            CentralMediaAsset(
                                id = "local_video_${file.name}",
                                title = "فيديو ريلز: ${file.name}",
                                localPath = file.absolutePath,
                                remoteUrl = null,
                                type = CentralMediaType.VIDEO,
                                source = CentralMediaSource.LOCAL_RENDER,
                                sizeBytes = file.length(),
                                formattedSize = formatFileSize(file.length()),
                                dateAdded = file.lastModified(),
                                formattedDate = dateFormat.format(Date(file.lastModified())),
                                bucketName = "media-assets",
                                isUploadedToCloud = false,
                                mimeType = "video/mp4",
                                promptOrNote = "فيديو ريلز 9:16 مصدّر"
                            )
                        )
                    }
                }
            }
        }

        return list
    }

    /**
     * رفع ملف محلي إلى Supabase Storage بنقرة واحدة
     */
    suspend fun uploadAssetToSupabase(
        context: Context,
        asset: CentralMediaAsset,
        targetBucket: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable) {
            return@withContext false
        }
        val localPath = asset.localPath ?: return@withContext false
        val file = File(localPath)
        if (!file.exists()) return@withContext false

        val userId = SupabaseServices.Auth.getCurrentUserId() ?: return@withContext false
        val bucket = targetBucket ?: asset.bucketName ?: "media-assets"
        val cloudPath = "$userId/${UUID.randomUUID().toString().take(8)}_${file.name}"

        // رفع متدفق من الملف نفسه بنوعه الحقيقي: كان readBytes() يحمل
        // الفيديو كاملاً في الذاكرة (خطر OOM) وبلا content-type.
        return@withContext try {
            val publicUrl = SupabaseServices.Storage.uploadFileWithProgress(
                bucketName = bucket,
                path = cloudPath,
                file = file,
                contentType = asset.mimeType.ifBlank { "application/octet-stream" }
            )

            if (publicUrl != null) {
                // تحديث العنصر في القائمة
                _assets.value = _assets.value.map {
                    if (it.id == asset.id) {
                        it.copy(
                            remoteUrl = publicUrl,
                            bucketName = bucket,
                            isUploadedToCloud = true
                        )
                    } else it
                }
                Log.i(TAG, "Uploaded asset ${asset.title} to Supabase: $publicUrl")
                true
            } else {
                false
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Failed to upload asset to Supabase", e)
            false
        }
    }

    /**
     * استيراد ملف من المعرض أو المستندات وحفظه في المستودع الموحد
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        type: CentralMediaType
    ): CentralMediaAsset? = withContext(Dispatchers.IO) {
        try {
            // الامتداد الحقيقي من اسم المصدر أو نوعه — لا فرض «mp3» على كل
            // صوت (كان ملف wav يُحفظ بامتداد mp3 فيتعذّر تشغيله لاحقاً).
            val sourceName = uri.lastPathSegment?.substringAfterLast('/', "")
            val extFromName = sourceName?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
                ?.lowercase()
            val mime = context.contentResolver.getType(uri).orEmpty()
            val extFromMime = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("quicktime") -> "mov"
                mime.contains("webm") -> "webm"
                mime.contains("wav") || mime.contains("x-wav") -> "wav"
                mime.contains("aac") -> "aac"
                mime.contains("ogg") -> "ogg"
                mime.startsWith("audio/") -> "mp3"
                mime.startsWith("video/") -> "mp4"
                mime.startsWith("image/") -> "jpg"
                else -> null
            }
            val ext = extFromName ?: extFromMime ?: when (type) {
                CentralMediaType.IMAGE -> "jpg"
                CentralMediaType.VIDEO -> "mp4"
                CentralMediaType.AUDIO -> "mp3"
                else -> "bin"
            }
            val targetDir = File(context.filesDir, "user_uploads").apply { mkdirs() }
            val fileName = "upload_${System.currentTimeMillis()}.$ext"
            val targetFile = File(targetDir, fileName)

            // دفق null أو نسخ 0 بايت كان يمرّ صامتاً فيُضاف أصل وهمي فارغ.
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            var copied = 0L
            input.use { inputStream ->
                FileOutputStream(targetFile).use { output ->
                    copied = inputStream.copyTo(output)
                }
            }
            if (copied <= 0L || !targetFile.exists() || targetFile.length() <= 0L) {
                runCatching { targetFile.delete() }
                Log.w(TAG, "Import from $uri copied $copied bytes; discarding empty file")
                return@withContext null
            }

            val newAsset = CentralMediaAsset(
                id = "user_upload_${targetFile.name}",
                title = "مرفوع: ${targetFile.name}",
                localPath = targetFile.absolutePath,
                remoteUrl = null,
                type = type,
                source = CentralMediaSource.USER_UPLOAD,
                sizeBytes = targetFile.length(),
                formattedSize = formatFileSize(targetFile.length()),
                dateAdded = targetFile.lastModified(),
                formattedDate = dateFormat.format(Date(targetFile.lastModified())),
                bucketName = "user-uploads",
                isUploadedToCloud = false,
                mimeType = deduceMimeType(targetFile.name),
                promptOrNote = "تم الاستيراد من الهاتف"
            )

            _assets.value = listOf(newAsset) + _assets.value
            newAsset
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file from URI", e)
            null
        }
    }

    /**
     * حذف ملف من الذاكرة المحلية أو من سحابة Supabase.
     * يُرجع نجاحاً حقيقياً فقط: كان يُرجع true دائماً (حتى عند فشل الحذف)
     * فتُحذف البطاقة من القائمة ويبقى الملف — والواجهة تشهد «نجاحاً».
     * عند الفشل يبقى العنصر في القائمة لإعادة المحاولة.
     */
    suspend fun deleteAsset(
        context: Context,
        asset: CentralMediaAsset
    ): Boolean = withContext(Dispatchers.IO) {
        var ok = true

        // 1. حذف محلي
        if (asset.localPath != null) {
            try {
                val f = File(asset.localPath)
                if (f.exists() && !f.delete()) ok = false
            } catch (_: Exception) {
                ok = false
            }
        }

        // 2. حذف سحابي إن كان مخزناً في Supabase
        if (asset.bucketName != null && asset.remoteUrl != null && SupabaseServices.isSupabaseAvailable) {
            try {
                // استخراج المسار الحقيقي للملف السحابي من الرابط
                // (ق171: الرابط العام مرمّز — حذف بمسار مرمّز يفشل على
                // أسماء فيها مسافات أو عربية؛ ويُقطع الاستعلام `?token=`
                // إن كان الرابط موقّعاً). معرّف الاحتياط ليس رابطاً
                // فلا يُفك ترميزه.
                val rawUrl = asset.remoteUrl
                val bucketPrefix = "/storage/v1/object/public/${asset.bucketName}/"
                val cloudPath = if (rawUrl.contains(bucketPrefix)) {
                    SupabaseServices.Storage.decodeObjectPath(rawUrl.substringAfter(bucketPrefix).substringBefore("?"))
                } else if (rawUrl.contains("/${asset.bucketName}/")) {
                    SupabaseServices.Storage.decodeObjectPath(rawUrl.substringAfter("/${asset.bucketName}/").substringBefore("?"))
                } else {
                    asset.id.removePrefix("supabase_${asset.bucketName}_")
                }
                if (!SupabaseServices.Storage.deleteFiles(asset.bucketName, listOf(cloudPath))) ok = false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                ok = false
            }
        }

        // إزالة العنصر من القائمة النشطة عند النجاح فقط
        if (ok) {
            _assets.value = _assets.value.filterNot { it.id == asset.id }
        }
        ok
    }

    // --- توابع مساعدة ---

    private fun deduceTypeFromFileName(name: String): CentralMediaType {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> CentralMediaType.IMAGE
            lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm") -> CentralMediaType.VIDEO
            lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".m4a") -> CentralMediaType.AUDIO
            else -> CentralMediaType.IMAGE
        }
    }

    private fun deduceMimeType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".ogg") -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
