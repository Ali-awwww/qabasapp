package com.qabas.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * درع الحماية والرقابة الشرعية لقسم الريلز في تطبيق قبس (Spiritual Safety Guard)
 *
 * يمنع نشر المحتوى الذي يحتوي على موسيقى أو معازف أو أي من وجوه الذنب والإثم.
 */
object SpiritualSafetyGuard {

    private const val TAG = "SpiritualSafetyGuard"

    private val MUSIC_AND_ENTERTAINMENT_BANNED = listOf(
        "موسيقى", "موسيقي", "موسيقى صاخبة", "موسيقى هادئة", "موسيقى حزينة",
        "معازف", "عزف", "غناء", "اغاني", "أغاني", "اغنية", "أغنية", "طرب",
        "بيانو", "جيتار", "غيتار", "عود", "كمنجة", "كمان", "ناي طربي",
        "طبول", "طبلة", "دفوف طربية", "دي جي", "ديجي", "ريمكس", "remix", "music",
        "song", "dance", "رقص", "دلع", "ردح", "تيك توك ترند", "شيلات طربية",
        "شيلة غرامية", "مهرجانات", "اغاني شعبية", "راب", "بوب", "جاز", "روك",
        "concert", "حفلة غنائية", "سهرة طربية", "كليب غنائي", "دويتو غنائي",
        "تبرج", "عري", "سفور", "فسق", "فجور", "ذنب", "إثم", "اثم", "محرمات",
        "انحلال", "رذيلة", "إباحية", "اباحية", "sex", "porn", "naked", "haram"
    )

    sealed class ModerationResult {
        object Passed : ModerationResult()
        data class Rejected(val reason: String, val detailedAyah: String = "") : ModerationResult()
    }

    /**
     * الفحص الشرعي لنصوص المقطع — يمنع الموسيقى والمعازف فقط
     */
    fun performSpiritualAudit(
        title: String,
        description: String?,
        tags: List<String>,
        category: String
    ): ModerationResult {
        val combinedText = buildString {
            append(title)
            append(" ")
            if (!description.isNullOrBlank()) append(description)
            append(" ")
            append(tags.joinToString(" "))
            append(" ")
            append(category)
        }.lowercase()

        for (banned in MUSIC_AND_ENTERTAINMENT_BANNED) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد محتوى قد يتضمن مخالفة شرعية أو وجهاً من وجوه الذنب («$banned»). منصة قبس مخصصة للمحتوى القرآني والإيماني النقي وخالية تماماً من المعازف والآثام.",
                    detailedAyah = "قال الله تعالى: ﴿وَمِنَ النَّاسِ مَن يَشْتَرِي لَهْوَ الْحَدِيثِ لِيُضِلَّ عَن سَبِيلِ اللَّهِ بِغَيْرِ عِلْمٍ وَيَتَّخِذَهَا هُزُوًا ۚ أُولَٰئِكَ لَهُمْ عَذَابٌ مُّهِينٌ﴾ [لقمان: 6]"
                )
            }
        }

        return ModerationResult.Passed
    }

    private fun containsWord(source: String, keyword: String): Boolean {
        if (source.contains(keyword, ignoreCase = true)) return true
        val normalizedSource = source.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي")
        val normalizedKeyword = keyword.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي")
        return normalizedSource.contains(normalizedKeyword, ignoreCase = true)
    }

    /**
     * استخراج صورة مصغرة (Thumbnail) ومعلومات الفيديو من ملف محلي
     */
    suspend fun extractVideoMetadata(
        context: Context,
        videoUri: Uri
    ): VideoMetadataInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            // استخراج فريم بعد ثانية واحدة كصورة مصغرة
            val frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)

            // حفظ الفريم كملف صورة محلي
            var thumbnailFile: File? = null
            var thumbnailBytes: ByteArray? = null
            if (frame != null) {
                val stream = ByteArrayOutputStream()
                frame.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                thumbnailBytes = stream.toByteArray()

                val thumbCacheDir = File(context.cacheDir, "reel_thumbs")
                if (!thumbCacheDir.exists()) thumbCacheDir.mkdirs()
                val targetFile = File(thumbCacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(targetFile).use { it.write(thumbnailBytes) }
                thumbnailFile = targetFile
            }

            VideoMetadataInfo(
                durationMs = durationMs,
                width = width,
                height = height,
                thumbnailFile = thumbnailFile,
                thumbnailBytes = thumbnailBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video metadata: ${e.message}")
            VideoMetadataInfo(0L, 0, 0, null, null)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    data class VideoMetadataInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val thumbnailFile: File?,
        val thumbnailBytes: ByteArray?
    )

    /**
     * تم حذف المقاطع التجريبية والوهمية استجابة للتوجيهات الشرعية ونقاء بيئة البيانات الحقيقية
     */
    val AUTHENTIC_STARTER_REELS: List<SupabaseServices.ReelRow> = emptyList()
}
