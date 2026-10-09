package com.qabas.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * أنماط المؤثرات الصوتية المتاحة (App Sound Effects Presets)
 */
enum class AppSoundPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val badge: String
) {
    CYBER_TECH(
        id = "cyber_tech",
        title = "نمط الخيال العلمي (Cyber Tech)",
        subtitle = "أصوات ميكانيكية سيبرانية، نبضات رقمية سريعة، واستجابة تفاعلية حادة",
        iconEmoji = "🛰️",
        badge = "سيبراني ✦"
    ),
    SPIRITUAL_ECHO(
        id = "spiritual_echo",
        title = "الصدى الروحاني الفاخر (Spiritual Echo)",
        subtitle = "ترددات رنانة عميقة، نقرات مسبحة خشبية، وأجراس هادئة توحي بالسكينة",
        iconEmoji = "🕌",
        badge = "روحاني ✦"
    ),
    COSMIC_SYNTH(
        id = "cosmic_synth",
        title = "البنفسج الكوني (Cosmic Synth)",
        subtitle = "أصوات كوانتوم ناعمة، انزلاقات أثيرية متوافقة مع الذكاء الاصطناعي",
        iconEmoji = "🌌",
        badge = "كوني ✦"
    ),
    SOFT_MINIMAL(
        id = "soft_minimal",
        title = "النمط الهادئ البسيط (Soft Minimal)",
        subtitle = "نقرات ناعمة خفيفة، لمسات طبيعية، وتجربة استماع مريحة للأذن",
        iconEmoji = "🍃",
        badge = "هادئ"
    ),
    SYSTEM_DEFAULT(
        id = "system_default",
        title = "افتراضي النظام (System Default)",
        subtitle = "استخدام أصوات النقر الافتراضية الخاصة بنظام أندرويد",
        iconEmoji = "📱",
        badge = "افتراضي"
    ),
    OFF(
        id = "off",
        title = "إيقاف المؤثرات (Silent Mode)",
        subtitle = "كتم كافة المؤثرات الصوتية التفاعلية داخل التطبيق",
        iconEmoji = "🔇",
        badge = "صامت"
    );

    companion object {
        fun fromId(id: String?): AppSoundPreset =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: CYBER_TECH
    }
}

/**
 * أنواع المؤثرات الصوتية التفاعلية
 */
enum class SoundType {
    CLICK,
    TOGGLE,
    TAB,
    ACTIVATE,
    SUCCESS,
    DHIKR
}

/**
 * مدير نظام المؤثرات الصوتية الشامل (Global SFX Engine)
 * يدعم التوليد الإجرائي المباشر (Procedural Audio Synthesis) بالإضافة لعينات PCM الأصلية.
 * يضمن سرعة استجابة فائقة (Sub-5ms Latency) وعدم الاعتماد على ملفات خارجية عند الفشل.
 */
object AppSoundManager {
    private const val TAG = "AppSoundManager"
    private const val PREF_SFX_MASTER = "app_sfx_master_enabled"
    private const val PREF_SFX_PRESET = "app_sfx_preset"
    private const val PREF_SFX_VOLUME = "app_sfx_volume"
    private const val PREF_CLICK_ENABLED = "app_sfx_click_enabled"
    private const val PREF_ANIM_ENABLED = "app_sfx_anim_enabled"
    private const val PREF_DHIKR_ENABLED = "app_sfx_dhikr_enabled"
    private const val PREF_HAPTIC_ENABLED = "app_sfx_haptic_enabled"

    private val _masterEnabled = MutableStateFlow(true)
    val masterEnabled: StateFlow<Boolean> = _masterEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow(AppSoundPreset.CYBER_TECH)
    val currentPreset: StateFlow<AppSoundPreset> = _currentPreset.asStateFlow()

    private val _volume = MutableStateFlow(0.85f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _clickEnabled = MutableStateFlow(true)
    val clickEnabled: StateFlow<Boolean> = _clickEnabled.asStateFlow()

    private val _animEnabled = MutableStateFlow(true)
    val animEnabled: StateFlow<Boolean> = _animEnabled.asStateFlow()

    private val _dhikrEnabled = MutableStateFlow(true)
    val dhikrEnabled: StateFlow<Boolean> = _dhikrEnabled.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val pcmCache = ConcurrentHashMap<String, ByteArray>()
    
    @Volatile
    private var isInitialized = false
    private val audioScope = CoroutineScope(Dispatchers.IO)
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        _masterEnabled.value = prefs.getBoolean(PREF_SFX_MASTER, true)
        val presetId = prefs.getString(PREF_SFX_PRESET, AppSoundPreset.CYBER_TECH.id)
        _currentPreset.value = AppSoundPreset.fromId(presetId)
        _volume.value = prefs.getFloat(PREF_SFX_VOLUME, 0.85f)
        _clickEnabled.value = prefs.getBoolean(PREF_CLICK_ENABLED, true)
        _animEnabled.value = prefs.getBoolean(PREF_ANIM_ENABLED, true)
        _dhikrEnabled.value = prefs.getBoolean(PREF_DHIKR_ENABLED, true)
        _hapticEnabled.value = prefs.getBoolean(PREF_HAPTIC_ENABLED, true)

        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }

        // توليد الأصوات الإجرائية في الذاكرة فوراً لضمان توفرها لحظياً 100%
        populateProceduralAudio()

        isInitialized = true

        // تحميل عينات PCM الإضافية من الموارد إذا كانت متوفرة
        audioScope.launch {
            loadPcmSafely(appContext, "cyber_click", R.raw.sfx_cyber_click)
            loadPcmSafely(appContext, "cyber_toggle", R.raw.sfx_cyber_toggle)
            loadPcmSafely(appContext, "cyber_activate", R.raw.sfx_cyber_activate)

            loadPcmSafely(appContext, "luxury_click", R.raw.sfx_luxury_click)
            loadPcmSafely(appContext, "luxury_toggle", R.raw.sfx_luxury_toggle)
            loadPcmSafely(appContext, "luxury_activate", R.raw.sfx_luxury_activate)

            loadPcmSafely(appContext, "cosmic_click", R.raw.sfx_cosmic_click)
            loadPcmSafely(appContext, "cosmic_toggle", R.raw.sfx_cosmic_toggle)
            loadPcmSafely(appContext, "cosmic_activate", R.raw.sfx_cosmic_activate)
            
            Log.d(TAG, "Sound bank loaded (${pcmCache.size} sound signatures)")
        }
    }

    private fun populateProceduralAudio() {
        // أصوات الخيال العلمي السيبراني
        pcmCache["cyber_click"] = generateCyberClick()
        pcmCache["cyber_toggle"] = generateCyberToggle()
        pcmCache["cyber_activate"] = generateCyberActivate()
        pcmCache["cyber_dhikr"] = generateDhikrBead(40, 1500.0, 480.0)

        // أصوات الصدى الروحاني
        pcmCache["luxury_click"] = generateSpiritualClick()
        pcmCache["luxury_toggle"] = generateSpiritualToggle()
        pcmCache["luxury_activate"] = generateSpiritualActivate()
        pcmCache["luxury_dhikr"] = generateSpiritualDhikr()

        // أصوات البنفسج الكوني
        pcmCache["cosmic_click"] = generateCosmicClick()
        pcmCache["cosmic_toggle"] = generateCosmicToggle()
        pcmCache["cosmic_activate"] = generateCosmicActivate()
        pcmCache["cosmic_dhikr"] = generateDhikrBead(35, 1200.0, 600.0)

        // أصوات النمط الهادئ البسيط
        pcmCache["minimal_click"] = generateSoftMinimalClick()
        pcmCache["minimal_toggle"] = generateTone(20, 600.0, 800.0, 6.0, 20000.0, false)
        pcmCache["minimal_activate"] = generateTone(100, 440.0, 660.0, 3.0, 22000.0, false)
        pcmCache["minimal_dhikr"] = generateSoftMinimalClick()

        // أصوات النجاح والتنقل
        pcmCache["success_chime"] = generateSuccess()
        pcmCache["nav_whoosh"] = generateSweep(70, 300.0, 750.0, 1.0, 0.1)
    }

    private fun loadPcmSafely(context: Context, key: String, resId: Int) {
        try {
            context.resources.openRawResource(resId).use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > 44) {
                    val rawPcm = bytes.copyOfRange(44, bytes.size)
                    pcmCache[key] = rawPcm
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Notice: using procedural synthesis for $key: ${e.message}")
        }
    }

    // ── دوال التحكم والضبط (Settings Controls) ──

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        _masterEnabled.value = enabled
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SFX_MASTER, enabled).apply()
    }

    fun setPreset(context: Context, preset: AppSoundPreset) {
        _currentPreset.value = preset
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putString(PREF_SFX_PRESET, preset.id).apply()
    }

    fun setVolume(context: Context, vol: Float) {
        _volume.value = vol.coerceIn(0f, 1f)
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putFloat(PREF_SFX_VOLUME, _volume.value).apply()
    }

    fun setClickEnabled(context: Context, enabled: Boolean) {
        _clickEnabled.value = enabled
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_CLICK_ENABLED, enabled).apply()
    }

    fun setAnimEnabled(context: Context, enabled: Boolean) {
        _animEnabled.value = enabled
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_ANIM_ENABLED, enabled).apply()
    }

    fun setDhikrEnabled(context: Context, enabled: Boolean) {
        _dhikrEnabled.value = enabled
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_DHIKR_ENABLED, enabled).apply()
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        _hapticEnabled.value = enabled
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_HAPTIC_ENABLED, enabled).apply()
    }

    // ── تشغيل الاهتزاز التكتيكي الخفيف (Haptic Pulse) ──
    private fun triggerHaptic(durationMs: Long = 12) {
        if (!_hapticEnabled.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Throwable) {}
    }

    // ── تشغيل الصوت عبر محرك AudioTrack المباشر ──
    private fun playSoundByKey(key: String, view: View? = null, withHaptic: Boolean = true) {
        if (!_masterEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return

        if (withHaptic) {
            triggerHaptic(12)
        }

        val pcm = pcmCache[key]
        if (pcm != null && pcm.isNotEmpty()) {
            val vol = _volume.value
            audioScope.launch {
                try {
                    val sampleRate = 44100
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(pcm.size)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    track.setVolume(vol)
                    track.write(pcm, 0, pcm.size)
                    track.play()

                    val durationMs = (pcm.size.toDouble() / (sampleRate * 2) * 1000).toLong() + 40
                    delay(durationMs)
                    try {
                        track.stop()
                        track.release()
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {
                    view?.playSoundEffect(SoundEffectConstants.CLICK)
                }
            }
        } else {
            view?.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    // ── الدوال العامة لتشغيل المؤثرات في كافة أنحاء التطبيق ──

    /**
     * تشغيل صوت النقر التفاعلي على الأزرار والبطاقات والعناصر
     */
    fun playClick(view: View? = null) {
        if (!_masterEnabled.value || !_clickEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return

        when (_currentPreset.value) {
            AppSoundPreset.CYBER_TECH -> playSoundByKey("cyber_click", view)
            AppSoundPreset.SPIRITUAL_ECHO -> playSoundByKey("luxury_click", view)
            AppSoundPreset.COSMIC_SYNTH -> playSoundByKey("cosmic_click", view)
            AppSoundPreset.SOFT_MINIMAL -> playSoundByKey("minimal_click", view)
            AppSoundPreset.SYSTEM_DEFAULT -> {
                triggerHaptic(10)
                view?.playSoundEffect(SoundEffectConstants.CLICK)
            }
            AppSoundPreset.OFF -> { /* silent */ }
        }
    }

    /**
     * تشغيل صوت التبديل والمفاتيح (Switches & Toggles)
     */
    fun playToggle(view: View? = null) {
        if (!_masterEnabled.value || !_clickEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return

        when (_currentPreset.value) {
            AppSoundPreset.CYBER_TECH -> playSoundByKey("cyber_toggle", view)
            AppSoundPreset.SPIRITUAL_ECHO -> playSoundByKey("luxury_toggle", view)
            AppSoundPreset.COSMIC_SYNTH -> playSoundByKey("cosmic_toggle", view)
            AppSoundPreset.SOFT_MINIMAL -> playSoundByKey("minimal_toggle", view)
            AppSoundPreset.SYSTEM_DEFAULT -> {
                triggerHaptic(14)
                view?.playSoundEffect(SoundEffectConstants.CLICK)
            }
            AppSoundPreset.OFF -> { /* silent */ }
        }
    }

    /**
     * تشغيل صوت التبديل بين التبويبات والشاشات الرئيسية (Tabs & BottomNav)
     */
    fun playTab(view: View? = null) {
        if (!_masterEnabled.value || !_clickEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return

        when (_currentPreset.value) {
            AppSoundPreset.CYBER_TECH -> playSoundByKey("cyber_toggle", view)
            AppSoundPreset.SPIRITUAL_ECHO -> playSoundByKey("luxury_click", view)
            AppSoundPreset.COSMIC_SYNTH -> playSoundByKey("cosmic_toggle", view)
            AppSoundPreset.SOFT_MINIMAL -> playSoundByKey("minimal_click", view)
            AppSoundPreset.SYSTEM_DEFAULT -> {
                triggerHaptic(12)
                view?.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
            }
            AppSoundPreset.OFF -> { /* silent */ }
        }
    }

    /**
     * تشغيل صوت التفعيل / التحويل الكامل / تغيير الثيم
     */
    fun playActivate(view: View? = null) {
        if (!_masterEnabled.value || !_animEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return

        when (_currentPreset.value) {
            AppSoundPreset.CYBER_TECH -> playSoundByKey("cyber_activate", view)
            AppSoundPreset.SPIRITUAL_ECHO -> playSoundByKey("luxury_activate", view)
            AppSoundPreset.COSMIC_SYNTH -> playSoundByKey("cosmic_activate", view)
            AppSoundPreset.SOFT_MINIMAL -> playSoundByKey("minimal_activate", view)
            AppSoundPreset.SYSTEM_DEFAULT -> {
                triggerHaptic(20)
                view?.playSoundEffect(SoundEffectConstants.CLICK)
            }
            AppSoundPreset.OFF -> { /* silent */ }
        }
    }

    /**
     * تشغيل صوت نقرة السبحة أو الأذكار (Acoustic Dhikr / Tasbeeh Bead)
     */
    fun playDhikr(view: View? = null) {
        if (!_masterEnabled.value || !_dhikrEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return

        triggerHaptic(15)
        when (_currentPreset.value) {
            AppSoundPreset.SPIRITUAL_ECHO -> playSoundByKey("luxury_dhikr", view, withHaptic = false)
            AppSoundPreset.CYBER_TECH -> playSoundByKey("cyber_dhikr", view, withHaptic = false)
            AppSoundPreset.COSMIC_SYNTH -> playSoundByKey("cosmic_dhikr", view, withHaptic = false)
            AppSoundPreset.SOFT_MINIMAL -> playSoundByKey("minimal_dhikr", view, withHaptic = false)
            AppSoundPreset.SYSTEM_DEFAULT -> view?.playSoundEffect(SoundEffectConstants.CLICK)
            AppSoundPreset.OFF -> { /* silent */ }
        }
    }

    /**
     * تشغيل صوت انتقال الشاشات وحركات الأنميشن
     */
    fun playAnimationSound() {
        if (!_masterEnabled.value || !_animEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return
        playSoundByKey("nav_whoosh", withHaptic = false)
    }

    /**
     * تشغيل صوت نجاح العملية أو اكتمال إنجاز
     */
    fun playSuccess(view: View? = null) {
        if (!_masterEnabled.value || _currentPreset.value == AppSoundPreset.OFF) return
        triggerHaptic(25)
        playSoundByKey("success_chime", view, withHaptic = false)
    }

    /**
     * تشغيل صوت تجريبي لنمط محدد (للمعاينة المباشرة من شاشة الإعدادات)
     */
    fun playDemoSound(preset: AppSoundPreset) {
        when (preset) {
            AppSoundPreset.CYBER_TECH -> playSoundByKey("cyber_activate")
            AppSoundPreset.SPIRITUAL_ECHO -> playSoundByKey("luxury_activate")
            AppSoundPreset.COSMIC_SYNTH -> playSoundByKey("cosmic_activate")
            AppSoundPreset.SOFT_MINIMAL -> playSoundByKey("minimal_activate")
            AppSoundPreset.SYSTEM_DEFAULT -> playSoundByKey("cyber_click")
            AppSoundPreset.OFF -> { /* silent */ }
        }
    }

    // ── محركات التوليد الصوتي الإجرائي النقي (Procedural Waveform Synthesis) ──

    private fun generateCyberClick(): ByteArray {
        return generateTone(
            durationMs = 20,
            startFreq = 2200.0,
            endFreq = 750.0,
            decayRate = 8.0,
            amplitude = 26000.0,
            addClick = true
        )
    }

    private fun generateCyberToggle(): ByteArray {
        return generateDualBurst(
            freq1 = 980.0, dur1Ms = 15,
            freq2 = 1760.0, dur2Ms = 25,
            decayRate = 6.0
        )
    }

    private fun generateCyberActivate(): ByteArray {
        return generateSweep(
            durationMs = 120,
            startFreq = 420.0,
            endFreq = 1540.0,
            harmonicRatio = 2.0,
            harmonicAmp = 0.25
        )
    }

    private fun generateSpiritualClick(): ByteArray {
        return generateTone(
            durationMs = 30,
            startFreq = 528.0,
            endFreq = 480.0,
            decayRate = 4.5,
            amplitude = 22000.0,
            addClick = false
        )
    }

    private fun generateSpiritualToggle(): ByteArray {
        return generateDualBurst(
            freq1 = 528.0, dur1Ms = 25,
            freq2 = 792.0, dur2Ms = 35,
            decayRate = 4.0
        )
    }

    private fun generateSpiritualActivate(): ByteArray {
        return generateHarmonicBell(
            durationMs = 220,
            fundamental = 660.0,
            harmonics = doubleArrayOf(1.0, 0.45, 0.25, 0.12),
            harmonicMultipliers = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        )
    }

    private fun generateSpiritualDhikr(): ByteArray {
        return generateDhikrBead(
            durationMs = 45,
            clickFreq = 1400.0,
            bodyFreq = 432.0
        )
    }

    private fun generateCosmicClick(): ByteArray {
        return generateTone(
            durationMs = 22,
            startFreq = 1500.0,
            endFreq = 1100.0,
            decayRate = 6.0,
            amplitude = 24000.0,
            addClick = false
        )
    }

    private fun generateCosmicToggle(): ByteArray {
        return generateDualBurst(
            freq1 = 660.0, dur1Ms = 20,
            freq2 = 1100.0, dur2Ms = 35,
            decayRate = 5.0
        )
    }

    private fun generateCosmicActivate(): ByteArray {
        return generateSweep(
            durationMs = 160,
            startFreq = 432.0,
            endFreq = 1080.0,
            harmonicRatio = 1.5,
            harmonicAmp = 0.3
        )
    }

    private fun generateSoftMinimalClick(): ByteArray {
        return generateTone(
            durationMs = 14,
            startFreq = 750.0,
            endFreq = 500.0,
            decayRate = 9.0,
            amplitude = 20000.0,
            addClick = false
        )
    }

    private fun generateSuccess(): ByteArray {
        return generateTriad(
            f1 = 523.25, // C5
            f2 = 659.25, // E5
            f3 = 783.99, // G5
            noteDurMs = 45,
            sustainDurMs = 90
        )
    }

    // ── خوارزميات التوليد الصوتي بالرياضيات (PCM Math Functions) ──

    private fun generateTone(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        decayRate: Double,
        amplitude: Double,
        addClick: Boolean
    ): ByteArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
        val bytes = ByteArray(numSamples * 2)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2.0 * Math.PI * freq / sampleRate
            
            var sample = sin(phase) * exp(-progress * decayRate) * amplitude
            if (addClick && i < 15) {
                sample += (if (i % 2 == 0) 1.0 else -1.0) * amplitude * 0.4
            }
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (intVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateDualBurst(
        freq1: Double, dur1Ms: Int,
        freq2: Double, dur2Ms: Int,
        decayRate: Double
    ): ByteArray {
        val sampleRate = 44100
        val n1 = (sampleRate * dur1Ms / 1000)
        val n2 = (sampleRate * dur2Ms / 1000)
        val total = n1 + n2
        val bytes = ByteArray(total * 2)
        var phase = 0.0

        for (i in 0 until n1) {
            phase += 2.0 * Math.PI * freq1 / sampleRate
            val progress = i.toDouble() / n1
            val sample = sin(phase) * exp(-progress * decayRate) * 25000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (intVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        phase = 0.0
        for (i in 0 until n2) {
            phase += 2.0 * Math.PI * freq2 / sampleRate
            val progress = i.toDouble() / n2
            val sample = sin(phase) * exp(-progress * decayRate) * 27000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            val idx = (n1 + i) * 2
            bytes[idx] = (intVal and 0xFF).toByte()
            bytes[idx + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateSweep(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        harmonicRatio: Double,
        harmonicAmp: Double
    ): ByteArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
        val bytes = ByteArray(numSamples * 2)
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * (progress * progress)
            phase1 += 2.0 * Math.PI * freq / sampleRate
            phase2 += 2.0 * Math.PI * (freq * harmonicRatio) / sampleRate

            val envelope = sin(progress * Math.PI) // Smooth bell envelope
            val sample = (sin(phase1) + sin(phase2) * harmonicAmp) * envelope * 24000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (intVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateHarmonicBell(
        durationMs: Int,
        fundamental: Double,
        harmonics: DoubleArray,
        harmonicMultipliers: DoubleArray
    ): ByteArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
        val bytes = ByteArray(numSamples * 2)
        val phases = DoubleArray(harmonics.size)

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            var combined = 0.0
            for (h in harmonics.indices) {
                val freq = fundamental * harmonicMultipliers[h]
                phases[h] += 2.0 * Math.PI * freq / sampleRate
                combined += sin(phases[h]) * harmonics[h]
            }
            val envelope = exp(-progress * 4.0)
            val sample = combined * envelope * 22000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (intVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateDhikrBead(
        durationMs: Int,
        clickFreq: Double,
        bodyFreq: Double
    ): ByteArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
        val bytes = ByteArray(numSamples * 2)
        var clickPhase = 0.0
        var bodyPhase = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            clickPhase += 2.0 * Math.PI * clickFreq / sampleRate
            bodyPhase += 2.0 * Math.PI * bodyFreq / sampleRate

            val clickEnv = exp(-progress * 25.0)
            val bodyEnv = exp(-progress * 6.0)
            val sample = (sin(clickPhase) * 0.7 * clickEnv + sin(bodyPhase) * 0.5 * bodyEnv) * 28000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (intVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateTriad(
        f1: Double,
        f2: Double,
        f3: Double,
        noteDurMs: Int,
        sustainDurMs: Int
    ): ByteArray {
        val sampleRate = 44100
        val n1 = (sampleRate * noteDurMs / 1000)
        val n2 = (sampleRate * noteDurMs / 1000)
        val n3 = (sampleRate * sustainDurMs / 1000)
        val total = n1 + n2 + n3
        val bytes = ByteArray(total * 2)

        var phase = 0.0
        // النوتة الأولى
        for (i in 0 until n1) {
            phase += 2.0 * Math.PI * f1 / sampleRate
            val sample = sin(phase) * exp(-(i.toDouble() / n1) * 3.0) * 22000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (intVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        // النوتة الثانية
        phase = 0.0
        for (i in 0 until n2) {
            phase += 2.0 * Math.PI * f2 / sampleRate
            val sample = sin(phase) * exp(-(i.toDouble() / n2) * 3.0) * 24000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            val idx = (n1 + i) * 2
            bytes[idx] = (intVal and 0xFF).toByte()
            bytes[idx + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        // النوتة الثالثة الرنانة
        phase = 0.0
        for (i in 0 until n3) {
            phase += 2.0 * Math.PI * f3 / sampleRate
            val sample = sin(phase) * exp(-(i.toDouble() / n3) * 2.5) * 26000.0
            val intVal = sample.toInt().coerceIn(-32768, 32767)
            val idx = (n1 + n2 + i) * 2
            bytes[idx] = (intVal and 0xFF).toByte()
            bytes[idx + 1] = ((intVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}

/**
 * Modifier Extension لربط المؤثرات الصوتية تلقائياً بأي عنصر في واجهة Compose
 */
fun Modifier.soundClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    soundType: SoundType = SoundType.CLICK,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this.clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(),
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        when (soundType) {
            SoundType.CLICK -> AppSoundManager.playClick()
            SoundType.TOGGLE -> AppSoundManager.playToggle()
            SoundType.TAB -> AppSoundManager.playTab()
            SoundType.ACTIVATE -> AppSoundManager.playActivate()
            SoundType.SUCCESS -> AppSoundManager.playSuccess()
            SoundType.DHIKR -> AppSoundManager.playDhikr()
        }
        onClick()
    }
}
