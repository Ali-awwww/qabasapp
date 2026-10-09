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
 * درع الحماية والرقابة الشرعية القصوى لقسم الريلز في تطبيق قبس (Spiritual Safety Guard)
 *
 * يوفر حماية صارمة ورقابة شرعية تامة ضد:
 * 1. الموسيقى والمعازف والغناء والمؤثرات الطربية بكل أصنافها.
 * 2. التبرج والسفور والمحتوى الخادش للحياء والآداب.
 * 3. الشركيات، السحر، التنجيم، الأبراج والبدع.
 * 4. القمار، المراهنات، الربا والمكاسب المحرمة.
 * 5. السب والشتم وإثارة الفتن والتحريف والاستهزاء بالدين.
 * 6. أي ذنب أو إثم جارٍ يحمل صاحبه وزراً.
 */
object SpiritualSafetyGuard {

    private const val TAG = "SpiritualSafetyGuard"

    // 1. قائمة الكلمات المحظورة قطيعاً (الموسيقى والمعازف والغناء والرقص)
    private val MUSIC_AND_ENTERTAINMENT_BANNED = listOf(
        "موسيقى", "موسيقي", "موسيقى صاخبة", "موسيقى هادئة", "موسيقى حزينة",
        "معازف", "عزف", "غناء", "اغاني", "أغاني", "اغنية", "أغنية", "طرب",
        "بيانو", "جيتار", "غيتار", "عود", "كمنجة", "كمان", "ناي طربي",
        "طبول", "طبلة", "دفوف طربية", "دي جي", "ديجي", "ريمكس", "remix", "music",
        "song", "dance", "رقص", "دلع", "ردح", "تيك توك ترند", "شيلات طربية",
        "شيلة غرامية", "مهرجانات", "اغاني شعبية", "راب", "بوب", "جاز", "روك",
        "concert", "حفلة غنائية", "سهرة طربية", "كليب غنائي", "دويتو غنائي"
    )

    // 2. قائمة المحظورات الأخلاقية والتبرج والسفور والمحتوى الخادش
    private val INDECENCY_BANNED = listOf(
        "تعري", "إباحي", "اباحي", "مخل", "فاضح", "تبرج", "سفور", "غزل فاحش",
        "علاقات محرمة", "تعارف", "شات صوتي", "dating", "sexy", "hot", "porn",
        "xxx", "سهرة خاصة", "بنات للتعارف", "ترقيم", "فضيحة", "فديحة", "عري",
        "ملابس فاضحة", "إغراء", "شهوة", "رومانسية محرمة", "قبلات", "عناق"
    )

    // 3. الشركيات والسحر والتنجيم والأبراج
    private val SHIRK_AND_SORCERY_BANNED = listOf(
        "سحر", "شعوذة", "تنجيم", "أبراج", "ابراج", "قراءة الفنجان", "قراءة الكف",
        "حظك اليوم", "جلب الحبيب", "فك السحر بالدجل", "تمائم شركية", "شياطين",
        "كهانة", "عراف", "روحانيات شركية", "استحضار جن", "زار", "تاروت", "tarot",
        "horoscope", "astrology", "برجك اليوم", "طاقة الكون الشركية"
    )

    // 4. القمار والمراهنات والربا والمكاسب المحرمة
    private val GAMBLING_AND_HARAM_WEALTH = listOf(
        "قمار", "مراهنات", "يانصيب", "ربا", "فوركس وهمي", "1xbet", "bet", "casino",
        "كازينو", "بوكر", "ربح سريع مضلل", "تعدين مشبوه", "هكر", "اختراق", "hack",
        "سرقة حسابات", "موقع مراهنات", "توقعات مباريات قمار", "روليت", "بلاك جاك"
    )

    // 5. السب والشتم وإثارة الفتن والتكفير والاستهزاء
    private val PROFANITY_AND_FITNAH = listOf(
        "سب", "شتم", "لعن", "قذف", "كفر", "استهزاء بالدين", "تحريف القرآن",
        "فتنة طائفية", "سب الصحابة", "سب أمهات المؤمنين", "تكفير المسلمين",
        "تحريض على القتل", "إرهاب", "عنف دموي", "سخرية من الشريعة", "إلحاد"
    )

    // 6. المخدرات والمسكرات والمعاصي الظاهرة
    private val SUBSTANCES_AND_SINS = listOf(
        "خمر", "نبيذ", "بيرة", "مخدرات", "حشيش", "كبتاجون", "شيشة", "دخان",
        "شرب الخمر", "سكران", "عصيان", "معصية جهاراً", "إيذاء النفس", "انتحار"
    )

    sealed class ModerationResult {
        object Passed : ModerationResult()
        data class Rejected(val reason: String, val detailedAyah: String = "") : ModerationResult()
    }

    /**
     * الفحص الشرعي الدقيق والشامل لنصوص المقطع (العنوان، الوصف، الوسوم، اسم المنشد/القارئ)
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

        // 1. فحص الموسيقى والمعازف
        for (banned in MUSIC_AND_ENTERTAINMENT_BANNED) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد محتوى يحتوي على موسيقى أو معازف أو غناء («$banned»). منصة قبس مخصصة للمحتوى القرآني والإيماني النقي وخالية تماماً من المعازف.",
                    detailedAyah = "قال الله تعالى: ﴿وَمِنَ النَّاسِ مَن يَشْتَرِي لَهْوَ الْحَدِيثِ لِيُضِلَّ عَن سَبِيلِ اللَّهِ بِغَيْرِ عِلْمٍ وَيَتَّخِذَهَا هُزُوًا ۚ أُولَٰئِكَ لَهُمْ عَذَابٌ مُّهِينٌ﴾ [لقمان: 6]"
                )
            }
        }

        // 2. فحص المحتوى الخادش للحياء والتبرج
        for (banned in INDECENCY_BANNED) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد ألفاظ أو محتوى مخالف للحشمة والآداب الشرعية («$banned»).",
                    detailedAyah = "قال الله تعالى: ﴿إِنَّ الَّذِينَ يُحِبُّونَ أَن تَشِيعَ الْفَاحِشَةُ فِي الَّذِينَ آمَنُوا لَهُمْ عَذَابٌ أَلِيمٌ فِي الدُّنْيَا وَالْآخِرَةِ﴾ [النور: 19]"
                )
            }
        }

        // 3. فحص الشركيات والسحر والتنجيم
        for (banned in SHIRK_AND_SORCERY_BANNED) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد محتوى يتضمن سحراً أو تنجيماً أو بدعاً شركية («$banned»).",
                    detailedAyah = "قال النبي ﷺ: «من أتى عرافاً أو كاهناً فصدقه بما يقول فقد كفر بما أنزل على محمد» [رواه أحمد]"
                )
            }
        }

        // 4. فحص القمار والمكاسب المحرمة
        for (banned in GAMBLING_AND_HARAM_WEALTH) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد ترويج للقمار أو المراهنات أو المكاسب غير المشروعة («$banned»).",
                    detailedAyah = "قال الله تعالى: ﴿يَا أَيُّهَا الَّذِينَ آمَنُوا إِنَّمَا الْخَمْرُ وَالْمَيْسِرُ وَالْأَنصَابُ وَالْأَزْلَامُ رِجْسٌ مِّنْ عَمَلِ الشَّيْطَانِ فَاجْتَنِبُوهُ لَعَلَّكُمْ تُفْلِحُونَ﴾ [المائدة: 90]"
                )
            }
        }

        // 5. فحص السب وإثارة الفتن
        for (banned in PROFANITY_AND_FITNAH) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد ألفاظ سب أو طعن أو إثارة للفتن المنهي عنها شرعاً («$banned»).",
                    detailedAyah = "قال النبي ﷺ: «ليس المؤمن بالطعان ولا اللعان ولا الفاحش ولا البذيء» [رواه الترمذي]"
                )
            }
        }

        // 6. فحص المسكرات والمعاصي
        for (banned in SUBSTANCES_AND_SINS) {
            if (containsWord(combinedText, banned)) {
                return ModerationResult.Rejected(
                    reason = "تم رصد محتوى يروج للمسكرات أو المعاصي الظاهرة («$banned»).",
                    detailedAyah = "قال الله تعالى: ﴿وَلَا تَعَاوَنُوا عَلَى الْإِثْمِ وَالْعُدْوَانِ ۚ وَاتَّقُوا اللَّهَ ۖ إِنَّ اللَّهَ شَدِيدُ الْعِقَابِ﴾ [المائدة: 2]"
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
