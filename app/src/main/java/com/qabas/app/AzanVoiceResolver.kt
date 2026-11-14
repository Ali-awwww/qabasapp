package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * يضمن وجود مسار صوت قابل للتشغيل وقت الأذان.
 * الأولوية: الاختيار المحفوظ، ثم الصوت الافتراضي للحساب، ثم أول صوت مطور سحابي فعال.
 */
object AzanVoiceResolver {
    private const val TAG = "AzanVoiceResolver"

    suspend fun resolveVoice(context: Context, requestedVoiceId: String): AzanVoice? =
        withContext(Dispatchers.IO) {
            val requested = MuezzinVoiceManager.getVoiceById(context, requestedVoiceId)
                ?.takeIf { hasPotentialSource(it) }
            if (requested != null) return@withContext requested

            val savedDefaultId = MuezzinVoiceManager.getDefaultVoiceId(context)
            MuezzinVoiceManager.getVoiceById(context, savedDefaultId)
                ?.takeIf { hasPotentialSource(it) }
                ?.let {
                return@withContext it
            }

            val cachedCloud = MuezzinVoiceManager.getCloudVoices(context)
                .filter(::hasPotentialSource)
            if (cachedCloud.isNotEmpty()) return@withContext cachedCloud.first()

            // عند الإقلاع البارد قد لا يكون كاش أصوات المطور موجودًا بعد.
            val cloud = AzanCloudRepository.fetchCloudVoices()
            if (cloud.isNotEmpty()) {
                MuezzinVoiceManager.cacheCloudVoices(context, cloud)
                return@withContext cloud.first()
            }

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
