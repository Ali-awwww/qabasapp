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
    private const val CLOUD_PREFS_NAME = "qabas_muezzin_cloud_cache"
    private const val KEY_CUSTOM_MUEZZINS = "custom_muezzin_voices"
    private const val KEY_CLOUD_MUEZZINS = "cloud_muezzin_voices_cache"
    private const val KEY_DEFAULT_VOICE_ID = "default_muezzin_voice_id"

    // Custom audio metadata and the selected default are private to the signed-in account.
    // Audio bytes remain in app-private device storage and are never uploaded.
    private fun customPrefs(context: Context): android.content.SharedPreferences {
        val accountId = Auth.getCurrentUserId()
        val scopedName = "${PREFS_NAME}_${accountId ?: "device"}"
        val scoped = context.getSharedPreferences(scopedName, Context.MODE_PRIVATE)

        // One-time migration of older device-wide settings into the first active account.
        if (accountId != null &&
            !scoped.contains(KEY_CUSTOM_MUEZZINS) &&
            !scoped.contains(KEY_DEFAULT_VOICE_ID)
        ) {
            val legacySources = listOf(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                context.getSharedPreferences("${PREFS_NAME}_device", Context.MODE_PRIVATE)
            )
            val source = legacySources.firstOrNull {
                it.contains(KEY_CUSTOM_MUEZZINS) || it.contains(KEY_DEFAULT_VOICE_ID)
            }
            if (source != null) {
                scoped.edit()
                    .putString(KEY_CUSTOM_MUEZZINS, source.getString(KEY_CUSTOM_MUEZZINS, "[]") ?: "[]")
                    .putString(KEY_DEFAULT_VOICE_ID, source.getString(KEY_DEFAULT_VOICE_ID, "") ?: "")
                    .apply()
                source.edit()
                    .remove(KEY_CUSTOM_MUEZZINS)
                    .remove(KEY_DEFAULT_VOICE_ID)
                    .apply()
            }
        }
        return scoped
    }

    private fun cloudPrefs(context: Context): android.content.SharedPreferences {
        val cloud = context.getSharedPreferences(CLOUD_PREFS_NAME, Context.MODE_PRIVATE)
        if (!cloud.contains(KEY_CLOUD_MUEZZINS)) {
            val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.contains(KEY_CLOUD_MUEZZINS)) {
                cloud.edit()
                    .putString(KEY_CLOUD_MUEZZINS, legacy.getString(KEY_CLOUD_MUEZZINS, "[]") ?: "[]")
                    .apply()
                legacy.edit().remove(KEY_CLOUD_MUEZZINS).apply()
            }
        }
        return cloud
    }

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
        val prefs = cloudPrefs(context)
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
        cloudPrefs(context).edit().putString(KEY_CLOUD_MUEZZINS, arr.toString()).apply()
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
        val prefs = customPrefs(context)
        val jsonStr = prefs.getString(KEY_CUSTOM_MUEZZINS, "[]") ?: "[]"
        return parseVoicesJson(jsonStr)
    }

    /**
     * نسخ ملف صوت من هاتف المستخدم إلى تخزين التطبيق الداخلي (للاستخدام المحلي فقط).
     * يُستخدم للمستخدم العادي بدل الرفع السحابي.
     */
    fun copyLocalAzanFile(context: Context, sourceUri: android.net.Uri, displayName: String): String? {
        return try {
            val extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(sourceUri))
                ?.lowercase(java.util.Locale.ROOT)
                ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
                ?: "mp3"
            val safe = displayName.substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(40)
                .ifBlank { "voice" }
            val fileName = "azan_local_${System.currentTimeMillis()}_$safe.$extension"
            val dest = java.io.File(context.filesDir, "azan_voices").apply { mkdirs() }.resolve(fileName)
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            input.use {
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (!dest.exists() || dest.length() == 0L) {
                dest.delete()
                return null
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
        val current = getCustomVoices(context)
        val target = current.firstOrNull { it.id == voiceId } ?: return false
        val remaining = current.filterNot { it.id == voiceId }
        saveCustomVoices(context, remaining)

        runCatching {
            val uri = android.net.Uri.parse(target.streamUrl)
            if (uri.scheme == "file") java.io.File(uri.path.orEmpty()).takeIf { it.isFile }?.delete()
        }
        val prefs = customPrefs(context)
        if (prefs.getString(KEY_DEFAULT_VOICE_ID, "") == voiceId) {
            prefs.edit().remove(KEY_DEFAULT_VOICE_ID).apply()
        }
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
        customPrefs(context).edit()
            .putString(KEY_CUSTOM_MUEZZINS, arr.toString())
            .apply()
    }

    /**
     * جلب الصوت الافتراضي المحدد
     */
    fun getDefaultVoiceId(context: Context): String {
        val prefs = customPrefs(context)
        val savedId = prefs.getString(KEY_DEFAULT_VOICE_ID, "").orEmpty()
        return savedId.takeIf { id -> getAllVoices(context).any { it.id == id } }.orEmpty()
    }

    /**
     * تعيين صوت المؤذن الافتراضي
     */
    fun setDefaultVoiceId(context: Context, voiceId: String) {
        customPrefs(context).edit()
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
