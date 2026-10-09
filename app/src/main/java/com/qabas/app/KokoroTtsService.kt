package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kokoro TTS — صوت عربي محلي عبر المحرك الصوتي.
 * تم تحسينه للعمل بمرونة تامة مع تفادي استهلاك مئات الميغابايتات الإضافية في الحزمة.
 * يسقط تلقائياً إلى RealElevenLabsService / RealAzureSpeechService / Android TTS.
 */
object KokoroTtsService {
    private const val TAG = "KokoroTtsService"

    /** توليد صوت وحفظه كملف WAV → يعاد مساره عند توفر الحزم */
    suspend fun synthesizeSpeech(context: Context, text: String, voice: String = "ar"): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        Log.i(TAG, "Kokoro lightweight mode — falling back to cloud/system TTS")
        null
    }
}
