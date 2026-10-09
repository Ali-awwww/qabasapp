package com.qabas.app

import android.content.Context
import kotlinx.coroutines.*
import java.io.File

// مستويات التدهور المتدرج المعلنة: سينمائي → موحد → مسودة
enum class RenderLevel { CINEMATIC, STANDARD, DRAFT }

object ProductionPowerKit {

    fun decideLevel(isLowEnd: Boolean, sceneCount: Int, quality: String): RenderLevel {
        if (quality.contains("مسودة", ignoreCase = true)) return RenderLevel.DRAFT
        if (isLowEnd || quality.contains("سريع", ignoreCase = true) || quality.contains("Fast", ignoreCase = true)) return RenderLevel.STANDARD
        if (sceneCount >= 8) return RenderLevel.STANDARD
        return RenderLevel.CINEMATIC
    }

    // بصمة المشهد: أي تغيير يعيد الترميز، وغير ذلك يُعاد استخدام المخزن
    fun sceneFingerprint(s: Scene, quality: String): String {
        val raw = listOf(s.title, s.description, s.durationInSeconds.toString(), s.visualEffect, s.tempo, s.transitionType, s.mediaUrl ?: "", quality).joinToString("|")
        return raw.hashCode().toUInt().toString(16)
    }

    fun cachedSceneFile(context: Context, fingerprint: String): File =
        File(File(context.cacheDir, "qabas_engine"), "scene_$fingerprint.mp4")

    // فحص مبكر رخيص: يفشل في ثوانٍ بدل دقائق
    fun preflight(context: Context, inputText: String): String? {
        if (inputText.isBlank()) return "الفكرة فارغة — أدخل نصاً قبل الإنتاج"
        val cacheDir = File(context.cacheDir, "qabas_engine")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val usableMB = try { cacheDir.usableSpace / (1024 * 1024) } catch (_: Exception) { 1000L }
        if (usableMB < 200L) return "مساحة التخزين منخفضة جداً (${usableMB}MB) — حرر مساحة قبل الإنتاج"
        return null
    }

    /**
     * تقدير زمن الكلام العربي (بالثواني) للمشهد لضمان محاذاة الصوت مع الفيديو.
     * يُستخدم لمنع انزياح التوقيت (AV Sync Drift).
     */
    fun estimateArabicSpeechDuration(text: String): Int {
        if (text.isBlank()) return 3
        // تنظيف النص من الرموز والمسافات الزائدة
        val clean = text.trim().replace(Regex("[^\\p{L}\\s]"), "")
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        
        // 2.1 كلمة في الثانية كإيقاع متوسط وقور في قبس (سريع=2.7، بطيء=1.6)
        // +0.8 ثانية كفراغ أولي ونبض وقار في نهاية الجملة
        val rawSeconds = (words / 2.1) + 0.8
        
        return rawSeconds.toInt().coerceIn(3, 20)
    }
}

// جلب تنبؤي: يبدأ أثناء قراءة الفهم، فيخفي زمن الشبكة
object BrollPrefetch {
    private var job: Job? = null
    private val memCache = mutableMapOf<String, String>()

    fun cached(query: String): String? = memCache[query.take(60)]

    fun prefetch(context: Context, inputText: String) {
        if (inputText.length < 4) return
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val words = inputText.split(Regex("\\s+")).filter { it.length > 2 }.take(3)
                words.forEach { w ->
                    if (!isActive) return@forEach
                    if (memCache.containsKey(w)) return@forEach
                    try {
                        val url = withTimeoutOrNull(20_000L) { AppServices.fetchMedia(w, "video") } ?: ""
                        if (url.isNotBlank()) memCache[w] = url
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun cancel() { try { job?.cancel() } catch (_: Exception) {} }
}
