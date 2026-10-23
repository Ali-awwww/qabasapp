package com.qabas.app

import android.content.Context

/**
 * 🎯 UpdateChannelFacade — طبقة عرض مصدر التحديث
 *
 * الهدف:
 * 1. **المستخدم العادي** يرى «قناة التحديث الرسمية» فقط — بلا ذكر GitHub أو المستودع أو
 *    الروابط أو أسماء الأصول. لا يعلم من أين جاء التحديث.
 * 2. **المطور** يرى كل التفاصيل: المستودع الفعلي، نقطة الـ API، الأصول، بصمة التوقيع،
 *    مقارنة الإصدارات، وتوفّر xdelta.
 *
 * ملاحظة أمنية: إخفاء المصدر **واجهة فقط**. القيد الحقيقي موجود في
 * [UpdateManager.getEffectiveRepo] — المستخدم العادي مثبَّت على المستودع الرسمي دائماً،
 * ولا يستطيع توجيه المحدّث إلى مستودع آخر.
 */
object UpdateChannelFacade {

    /** هل هذا المستخدم مطور؟ (المالك / علم محلي / دور في Supabase) */
    fun isDeveloper(context: Context): Boolean =
        runCatching { AdminGuard.isDashboardAccessAllowed(context) }.getOrDefault(false)

    /**
     * صلاحية اللوحة التقنية الداخلية (اسم المستودع، رابط API، بصمة التوقيع).
     * تتحقق من هوية سحابية موثّقة فقط (المالك أو دور developer/owner في Supabase)،
     * ولا تعتمد على العَلَم المحلي is_developer لأنه قابل للتعديل على الجهاز.
     */
    fun isVerifiedDeveloper(): Boolean =
        runCatching {
            AdminGuard.isCloudUserOwner() ||
                SupabaseServices.Auth.verifiedRole.value in setOf("developer", "owner")
        }.getOrDefault(false)

    // ─────────────────────────────────────────────────────────────
    // نصوص المستخدم العادي (بدون ذكر المصدر)
    // ─────────────────────────────────────────────────────────────

    /** اسم القناة كما يراه المستخدم العادي */
    const val PUBLIC_CHANNEL_NAME = "قناة التحديث الرسمية"

    /** تسمية القناة حسب صلاحية المستخدم */
    fun channelLabel(context: Context): String =
        if (isDeveloper(context)) "المستودع الرسمي" else PUBLIC_CHANNEL_NAME

    /** وصف نقطة الفحص — للمطور يذكر المصدر، وللمستخدم يذكر behavior فقط */
    fun checkSourceLabel(context: Context): String =
        if (isDeveloper(context)) "GitHub Releases API" else "قناة التحديث الرسمية"

    /** نص زر الفحص (غير أثناء الفحص) */
    fun checkButtonLabel(context: Context): String =
        if (isDeveloper(context)) "فحص التحديثات الآن عبر GitHub Releases 🔄"
        else "فحص التحديثات الآن 🔄"

    /** نص زر الفحص (أثناء الفحص) */
    fun checkingButtonLabel(context: Context): String =
        if (isDeveloper(context)) "جاري التحقق من GitHub Releases..."
        else "جاري التحقق من التحديثات..."

    /** نص نافذة الفحص (Toast) */
    fun checkingToast(context: Context): String =
        if (isDeveloper(context)) "جاري فحص التحديثات عبر GitHub Releases 🔍"
        else "جاري فحص التحديثات 🔍"

    /** فقرة «عن التحديثات» في مركز التنزيلات */
    fun aboutText(context: Context, currentVersionName: String): String =
        if (isDeveloper(context)) {
            "أنت تستخدم النسخة v$currentVersionName. عند توفر أي إصدار أحدث على GitHub Releases، " +
                "سيبدأ النظام بتنزيله تلقائياً وعرضه مباشرة في مركز التنزيلات."
        } else {
            "أنت تستخدم النسخة v$currentVersionName. عند توفر أي إصدار أحدث، " +
                "سيبدأ النظام بتنزيله تلقائياً وعرضه مباشرة في مركز التنزيلات."
        }

    /** قائمة السلوك التلقائي */
    fun behaviorText(context: Context): String {
        val interval = UpdateManager.getAutoCheckIntervalHours(context)
        return if (isDeveloper(context)) {
            "• يفحص التطبيق تلقائياً مستودع GitHub كل $interval ساعة (الحد الأدنى ساعتان، وقابل للتغيير من مركز التنزيلات).\n" +
                "• يبدأ التنزيل فوراً في الخلفية مع إشعار دائم ونبضات في مركز التنزيلات.\n" +
                "• فور اكتمال التنزيل، يُطلب من نظام أندرويد فتح نافذة التثبيت تلقائياً مع بقاء " +
                "زر التثبيت اليدوي متاحاً هنا في أي وقت."
        } else {
            "• يفحص التطبيق القناة الرسمية تلقائياً كل $interval ساعة (الحد الأدنى ساعتان، وقابل للتغيير من مركز التنزيلات).\n" +
                "• يبدأ التنزيل فوراً في الخلفية مع إشعار دائم ونبضات في مركز التنزيلات.\n" +
                "• فور اكتمال التنزيل، يُطلب من نظام أندرويد فتح نافذة التثبيت تلقائياً مع بقاء " +
                "زر التثبيت اليدوي متاحاً هنا في أي وقت."
        }
    }

    // ─────────────────────────────────────────────────────────────
    // تفاصيل للمطور فقط
    // ─────────────────────────────────────────────────────────────

    /** المستودع الفعلي (للمطور فقط — في الواجهة) */
    fun effectiveRepo(context: Context): String = UpdateManager.getEffectiveRepo(context)

    /** رابط الإصدارات المستخدَم فعلياً */
    fun releasesApiUrl(context: Context): String =
        "https://api.github.com/repos/${effectiveRepo(context)}/releases/latest"

    /** رابط صفحة الإصدارات (يفتح في المتصفح) */
    fun releasesPageUrl(context: Context): String =
        "https://github.com/${effectiveRepo(context)}/releases"

    /**
     * هل نُخفي التفاصيل التقنية عن هذا المستخدم؟
     * نعم لكل من ليس مطوراً.
     */
    fun hidesTechnicalDetails(context: Context): Boolean = !isDeveloper(context)
}
