package com.qabas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🎙️ مدير أصوات المؤذنين وقاعدة بيانات الأذان الحية
 * (Real Muezzin Voices Manager with Dynamic Custom Muezzins Extension)
 */
object MuezzinVoiceManager {

    private const val PREFS_NAME = "qabas_muezzin_prefs"
    private const val KEY_CUSTOM_MUEZZINS = "custom_muezzin_voices"
    private const val KEY_DEFAULT_VOICE_ID = "default_muezzin_voice_id"

    /**
     * أصوات المؤذنين الحصرية والمعتمدة حالياً
     */
    val OFFICIAL_DEFAULT_MUEZZINS = listOf(
        AzanVoice(
            id = "alshawky_sharjah",
            nameAr = "أذان الشارقة الندي",
            muezzinAr = "الشيخ عبد العزيز الشوكي (مؤذن الشارقة)",
            description = "أذان خاشع رخيم وصوت هادئ يأسر القلوب بصوت مؤذن مساجد الشارقة الشهير الشيخ عبد العزيز الشوكي.",
            streamUrl = "https://ia801503.us.archive.org/15/items/AdhanAbdulazizAlshawky/Adhan_Sharjah_Abdulaziz_Alshawky.mp3"
        ),
        AzanVoice(
            id = "abdulmajeed_salama",
            nameAr = "أذان المؤذن عبد المجيد سلامة",
            muezzinAr = "المؤذن عبد المجيد سلامة",
            description = "أداء شجي مؤثر ونبرة إيمانية ندية تهز الوجدان وترفع همم المؤمنين لإجابة النداء.",
            streamUrl = "https://ia801508.us.archive.org/28/items/AdhanAbdulMajeedSalama/Adhan_Abdul_Majeed_Salama.mp3"
        )
    )

    /**
     * جلب جميع المؤذنين (المعتمدين حصراً + أي مؤذن مخصص أضافه المستخدم لاحقاً)
     */
    fun getAllVoices(context: Context): List<AzanVoice> {
        val customVoices = getCustomVoices(context)
        return OFFICIAL_DEFAULT_MUEZZINS + customVoices
    }

    /**
     * جلب الأصوات المخصصة المضافة من قبل المستخدم
     */
    fun getCustomVoices(context: Context): List<AzanVoice> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_MUEZZINS, "[]") ?: "[]"
        val list = mutableListOf<AzanVoice>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AzanVoice(
                        id = obj.getString("id"),
                        nameAr = obj.getString("nameAr"),
                        muezzinAr = obj.getString("muezzinAr"),
                        description = obj.optString("description", "صوت أذان مخصص"),
                        streamUrl = obj.getString("streamUrl"),
                        isFajrSpecific = obj.optBoolean("isFajrSpecific", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * إضافة مؤذن جديد ديناميكياً
     */
    fun addCustomVoice(context: Context, voice: AzanVoice): Boolean {
        if (voice.id.isBlank() || voice.streamUrl.isBlank() || voice.muezzinAr.isBlank()) return false
        val current = getCustomVoices(context).toMutableList()
        val index = current.indexOfFirst { it.id == voice.id }
        if (index >= 0) {
            current[index] = voice
        } else {
            current.add(voice)
        }

        saveCustomVoices(context, current)
        return true
    }

    /**
     * حذف مؤذن مخصص
     */
    fun deleteCustomVoice(context: Context, voiceId: String): Boolean {
        val current = getCustomVoices(context).filter { it.id != voiceId }
        saveCustomVoices(context, current)
        return true
    }

    private fun saveCustomVoices(context: Context, voices: List<AzanVoice>) {
        val arr = JSONArray()
        for (v in voices) {
            arr.put(JSONObject().apply {
                put("id", v.id)
                put("nameAr", v.nameAr)
                put("muezzinAr", v.muezzinAr)
                put("description", v.description)
                put("streamUrl", v.streamUrl)
                put("isFajrSpecific", v.isFajrSpecific)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_MUEZZINS, arr.toString())
            .apply()
    }

    /**
     * جلب الصوت الافتراضي المحدد
     */
    fun getDefaultVoiceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_VOICE_ID, OFFICIAL_DEFAULT_MUEZZINS.first().id) ?: OFFICIAL_DEFAULT_MUEZZINS.first().id
    }

    /**
     * تعيين صوت المؤذن الافتراضي
     */
    fun setDefaultVoiceId(context: Context, voiceId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_VOICE_ID, voiceId)
            .apply()
    }

    /**
     * جلب كائن المؤذن حسب المعرف
     */
    fun getVoiceById(context: Context, voiceId: String): AzanVoice {
        return getAllVoices(context).find { it.id == voiceId } ?: OFFICIAL_DEFAULT_MUEZZINS.first()
    }
}
