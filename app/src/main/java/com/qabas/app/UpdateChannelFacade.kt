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
                SupabaseServices.Auth.verifiedRole.value.lowercase() in setOf("developer", "admin", "owner", "مطور", "مكور")
        }.getOrDefault(false)

    // ─────────────────────────────────────────────────────────────
    // نصوص المستخدم العادي (بدون ذكر المصدر)
    // ─────────────────────────────────────────────────────────────

    /** اسم القناة كما يراه المستخدم العادي */
    const val PUBLIC_CHANNEL_NAME = "قناة التحديث الرسمية"

    /** تسمية القناة حسب صلاحية المستخدم */
    fun channelLabel(context: Context): String =
        if (isDeveloper(context)) "إدارة الإصدار المعتمد" else PUBLIC_CHANNEL_NAME

    /** وصف نقطة الفحص — للمطور يذكر المصدر، وللمستخدم يذكر behavior فقط */
    fun checkSourceLabel(context: Context): String =
        if (isDeveloper(context)) "Supabase — الإصدار المعتمد" else "قناة التحديث الرسمية"

    /** نص زر الفحص (غير أثناء الفحص) */
    fun checkButtonLabel(context: Context): String =
        if (isDeveloper(context)) "فحص الإصدار المعتمد عبر Supabase 🔄"
        else "فحص التحديثات الآن 🔄"

    /** نص زر الفحص (أثناء الفحص) */
    fun checkingButtonLabel(context: Context): String =
        if (isDeveloper(context)) "جاري التحقق من الإصدار المعتمد في Supabase..."
        else "جاري التحقق من التحديثات..."

    /** نص نافذة الفحص (Toast) */
    fun checkingToast(context: Context): String =
        if (isDeveloper(context)) "جاري فحص الإصدار المعتمد في Supabase 🔍"
        else "جاري فحص التحديثات 🔍"

    /** فقرة «عن التحديثات» في مركز التنزيلات */
    fun aboutText(context: Context, currentVersionName: String): String =
        "أنت تستخدم النسخة v$currentVersionName. لا يصل للمستخدمين إلا الإصدار الذي يعتمده المطوّر في Supabase. " +
            "نشر إصدار جديد على GitHub وحده لا يغيّر النسخة التي يتلقاها التطبيق."

    /** سياسة التوزيع التي يطبقها التطبيق */
    fun behaviorText(context: Context): String {
        val interval = UpdateManager.getAutoCheckIntervalHours(context)
        return "• يتحقق التطبيق من الإصدار المعتمد في Supabase كل $interval ساعة (الحد الأدنى ساعتان).\n" +
            "• التحديث الإجباري يبدأ تنزيله في الخلفية مع إشعار بالتقدم.\n" +
            "• التحديث الاختياري يعرض نافذة، ولا يبدأ تنزيله إلا بعد اختيار المستخدم.\n" +
            "• بعد اكتمال التنزيل، يعرض أندرويد خطوة التثبيت التي تتطلب موافقة المستخدم."
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
