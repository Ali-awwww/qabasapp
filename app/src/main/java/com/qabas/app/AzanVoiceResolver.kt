package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * يضمن وجود مسار صوت قابل للتشغيل وقت الأذان.
 * الأولوية: الاختيار المحفوظ، ثم الصوت الافتراضي للحساب، ثم أول صوت مطور سحابي فعال،
 * ثم أول صوت منزّل / مسجّل من المستخدم — وبعدها حالة صادقة فارغة (null) لا معرّف متدلٍّ.
 */
object AzanVoiceResolver {
    private const val TAG = "AzanVoiceResolver"

    /**
     * يحلّ صوت الأذان للصلاة المعطاة. صوت «الفجر فقط» لا يُشغَّل لغير
     * الفجر أبداً: تسجيله يحمل «الصلاة خير من النوم» فتشغيله ظهراً أذان
     * خاطئ. prayerKey=null (معاينة يدوية بلا صلاة) يسمح بكل الأصوات.
     */
    suspend fun resolveVoice(
        context: Context,
        requestedVoiceId: String,
        prayerKey: String? = null
    ): AzanVoice? =
        withContext(Dispatchers.IO) {
            val suitable = { voice: AzanVoice ->
                !voice.isFajrSpecific || prayerKey == null ||
                    prayerKey == PrayerAlarmManager.PRAYER_FAJR
            }
            val requested = MuezzinVoiceManager.getVoiceById(context, requestedVoiceId)
                ?.takeIf { hasPotentialSource(it) && suitable(it) }
            if (requested != null) return@withContext requested

            val savedDefaultId = MuezzinVoiceManager.getDefaultVoiceId(context)
            MuezzinVoiceManager.getVoiceById(context, savedDefaultId)
                ?.takeIf { hasPotentialSource(it) && suitable(it) }
                ?.let {
                return@withContext it
            }

            val cachedCloud = MuezzinVoiceManager.getCloudVoices(context)
                .filter { hasPotentialSource(it) && suitable(it) }
            if (cachedCloud.isNotEmpty()) return@withContext cachedCloud.first()

            // عند الإقلاع البارد قد لا يكون كاش أصوات المطور موجودًا بعد.
            // الفشل يعيد قائمة فارغة (ق50) فيُتخطى كأن لم يكن — لا مسح كاش هنا.
            val cloud = AzanCloudRepository.fetchCloudVoices().getOrDefault(emptyList())
            if (cloud.isNotEmpty()) {
                MuezzinVoiceManager.cacheCloudVoices(context, cloud)
                cloud.firstOrNull { hasPotentialSource(it) && suitable(it) }
                    ?.let { return@withContext it }
            }

            // الملاذ الأخير قبل الحالة الصادقة الفارغة: صوت منزّل أو مسجّل
            // من المستخدم له مصدر تشغيل حقيقي.
            val localVoice = MuezzinVoiceManager.getCustomVoices(context)
                .firstOrNull { hasPotentialSource(it) && suitable(it) }
                ?: MuezzinVoiceManager.getPersonalCloudVoices(context)
                    .firstOrNull { hasPotentialSource(it) && suitable(it) }
            if (localVoice != null) return@withContext localVoice

            Log.e(TAG, "No requested, default, cached, or cloud adhan voice is available")
            null
        }

    private fun hasPotentialSource(voice: AzanVoice): Boolean {
        val source = voice.streamUrl.trim()
        if (source.startsWith("file://") || source.startsWith("/")) {
            val path = source.removePrefix("file://")
            val file = java.io.File(path)
            return file.isFile && file.length() > 0L
        }
        return source.startsWith("http")
    }

    /**
     * ينزّل صوت المطور إلى كاش دائم عند الحاجة، ويعيد مسارًا محليًا صالحًا.
     * إذا تعذر التنزيل يعيد الرابط السحابي كخطة تشغيل أخيرة عند توفر الإنترنت.
     */
    suspend fun ensurePlayableSource(context: Context, voice: AzanVoice): String? =
        withContext(Dispatchers.IO) {
            val source = voice.streamUrl.trim()
            if (source.startsWith("file://") || source.startsWith("/")) {
                val path = source.removePrefix("file://")
                return@withContext path.takeIf { java.io.File(it).isFile && java.io.File(it).length() > 0L }
            }

            if (voice.id.startsWith("cloud_") && source.startsWith("http")) {
                DownloadsHubManager.init(context)
                val local = DownloadsHubManager.downloadMedia(
                    context = context,
                    mediaId = voice.id,
                    title = voice.nameAr,
                    fileUrl = source,
                    extension = "mp3"
                )
                if (!local.isNullOrBlank()) return@withContext local
            }

            source.takeIf { it.startsWith("http") }
        }
}
