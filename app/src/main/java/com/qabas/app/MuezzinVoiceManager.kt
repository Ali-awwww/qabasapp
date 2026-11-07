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
    private const val KEY_CLOUD_MUEZZINS = "cloud_muezzin_voices_cache"
    private const val KEY_DEFAULT_VOICE_ID = "default_muezzin_voice_id"

    /**
     * أصوات المؤذنين الحصرية والمعتمدة حالياً
     */
    // No bundled/sample recordings: the library starts empty until real voices are added.
    val OFFICIAL_DEFAULT_MUEZZINS = emptyList<AzanVoice>()

    /**
     * جلب جميع المؤذنين (المعتمدين + السحابة + المخصص المحلي)
     */
    fun getAllVoices(context: Context): List<AzanVoice> {
        return OFFICIAL_DEFAULT_MUEZZINS + getCloudVoices(context) + getCustomVoices(context)
    }

    fun getCloudVoices(context: Context): List<AzanVoice> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CLOUD_MUEZZINS, "[]") ?: "[]"
        return parseVoicesJson(jsonStr)
    }

    fun cacheCloudVoices(context: Context, voices: List<AzanVoice>) {
        // تُحفظ الأصوات السحابية في كاش محلي للعمل دون إنترنت، دون أي رفع من المستخدم
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
            .edit().putString(KEY_CLOUD_MUEZZINS, arr.toString()).apply()
    }

    private fun parseVoicesJson(jsonStr: String): List<AzanVoice> {
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
     * جلب الأصوات المخصصة المضافة من قبل المستخدم (ملفات محلية فقط — لا تُرفع للسحابة أبداً)
     */
    fun getCustomVoices(context: Context): List<AzanVoice> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_MUEZZINS, "[]") ?: "[]"
        return parseVoicesJson(jsonStr)
    }

    /**
     * نسخ ملف صوت من هاتف المستخدم إلى تخزين التطبيق الداخلي (للاستخدام المحلي فقط).
     * يُستخدم للمستخدم العادي بدل الرفع السحابي.
     */
    fun copyLocalAzanFile(context: Context, sourceUri: android.net.Uri, displayName: String): String? {
        return try {
            val safe = displayName.replace(Regex("[^a-zA-Z0-9_\\-.]"), "_").take(40)
            val fileName = "azan_local_${System.currentTimeMillis()}_$safe.mp3"
            val dest = java.io.File(context.filesDir, "azan_voices").apply { mkdirs() }.resolve(fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            android.net.Uri.fromFile(dest).toString()
        } catch (_: Exception) { null }
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
        val savedId = prefs.getString(KEY_DEFAULT_VOICE_ID, "").orEmpty()
        return savedId.takeIf { id -> getAllVoices(context).any { it.id == id } }.orEmpty()
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
    fun getVoiceById(context: Context, voiceId: String): AzanVoice? {
        return getAllVoices(context).find { it.id == voiceId }
    }
}
