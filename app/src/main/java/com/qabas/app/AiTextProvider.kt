package com.qabas.app

import kotlinx.coroutines.flow.Flow

/**
 * طبقة تجريد لمولّد النص والمحادثة بالذكاء الاصطناعي (المرحلة 2 من الإصلاح).
 *
 * التنفيذ الحالي الوحيد هو [FirebaseAiService] (Firebase AI Logic / Gemini 3.8 Flash).
 * الهدف: فصل بقية الكود عن Firebase تمهيداً لبديل Supabase (دالة Edge جديدة
 * تستدعي Gemini بمفتاح مخزّن في `platform_api_keys` عبر `qabas-key-vault`، أو
 * مفتاح BYOK للمستخدم) دون تغيير أي موقع استدعاء — يكفي تبديل التنفيذ الممرَّر.
 *
 * هذا الملف إضافة فقط: لا يغيّر أي سلوك، ولا يحذف أي تكامل، ولا يضيف اعتماداً.
 * لم يُختبر بالبناء (البناء محظور دون إذن صريح)؛ التحقق هنا قراءة فقط.
 */
interface AiTextProvider {

    /** توليد نص واحد من موجّه، مع تعليمات نظام اختيارية. */
    suspend fun generateText(prompt: String, systemInstruction: String? = null): String

    /** بث النص على دفعات لتقليل زمن أول رمز (TTFT). */
    fun generateContentStream(prompt: String, systemInstruction: String? = null): Flow<String>

    /** محادثة متعددة الأدوار؛ كل زوج (isUser, text). */
    suspend fun chat(
        messages: List<Pair<Boolean, String>>,
        systemInstruction: String? = null
    ): String
}
