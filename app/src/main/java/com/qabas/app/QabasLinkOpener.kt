package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 🌐 قاذف ومحرك فتح الروابط الذكي (Universal Resilient Link Opener)
 *
 * مصمم لتجاوز قيود نظام Android الحديثة (Android 11-15+ Package Visibility Restrictions,
 * ActivityNotFoundException, Custom Schemes, Multi-User / App Clones, Missing Browsers):
 * 1. معالجة وتصحيح الرابط (Sanitization & Protocol Verification).
 * 2. محاولة الفتح المباشر عبر التطبيق المخصص (Target App / Browser / Deep Link).
 * 3. حزمة المتصفحات الشائعة (Fallback Browsers) في حال منع النظام للموجه العام.
 * 4. موجه النظام الموحد (System Chooser) بدون قيود الـ Component.
 * 5. الإجراء الاحتياطي الفوري (Fallback): نسخ الرابط التلقائي للحافظة وإشعار المستخدم.
 */
object QabasLinkOpener {

    /** قائمة حزم المتصفحات الرئيسية في بيئات أندرويد المتعددة */
    private val KNOWN_BROWSERS = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.microsoft.emmx", // Edge
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.transsion.phoenix", // Infinix/Tecno Phoenix
        "com.android.browser"
    )

    /** حزم واتساب الرسمية والمعتمدة */
    private val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    /**
     * ينظف الرابط ويتأكد من احتوائه على البروتوكول الصحيح (https://)
     */
    fun sanitizeUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (clean.isBlank()) return ""
        // معالجة روابط chat.whatsapp.com بدون بروتوكول
        if (clean.startsWith("chat.whatsapp.com")) {
            clean = "https://$clean"
        } else if (clean.startsWith("wa.me/")) {
            clean = "https://$clean"
        } else if (!clean.startsWith("http://") && !clean.startsWith("https://") && !clean.startsWith("market://")) {
            clean = "https://$clean"
        }
        return clean
    }

    /**
     * يفتح أي رابط خارجي (موقع، واتساب، ملف شخصي، فيسبوك، يوتيوب، إلخ) بأعلى درجات الموثوقية
     *
     * @param context سياق التطبيق أو النشاط
     * @param rawUrl الرابط المستهدف
     * @param targetDescription وصف الرابط للعرض في الإشعارات (مثل: "مجموعة واتساب", "الموقع الرسمي")
     * @param preferredPackage حزمة مفضلة إن وجدت (اختياري)
     * @return true إذا نجح فتح الرابط، false إذا تم نسخه للحافظة كحل بديل
     */
    fun openUrl(
        context: Context,
        rawUrl: String,
        targetDescription: String = "الرابط",
        preferredPackage: String? = null
    ): Boolean {
        val url = sanitizeUrl(rawUrl)
        if (url.isBlank()) {
            Toast.makeText(context, "الرابط فارغ أو غير متاح ⚠️", Toast.LENGTH_SHORT).show()
            return false
        }

        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            copyToClipboard(context, url, targetDescription)
            Toast.makeText(context, "تعذر معالجة صيغة الرابط، تم نسخه للحافظة 📋", Toast.LENGTH_LONG).show()
            return false
        }

        val pm = context.packageManager

        // أ) إذا كان الرابط رابط واتساب (chat.whatsapp.com أو wa.me)
        if (url.contains("chat.whatsapp.com") || url.contains("wa.me") || preferredPackage in WHATSAPP_PACKAGES) {
            for (pkg in WHATSAPP_PACKAGES) {
                try {
                    val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (waIntent.resolveActivity(pm) != null || isPackageInstalled(context, pkg)) {
                        context.startActivity(waIntent)
                        return true
                    }
                } catch (_: Exception) {
                    // متابعة المحاولة مع الخيارات البديلة
                }
            }
        }

        // ب) إذا تم تحديد حزمة مفضلة
        if (!preferredPackage.isNullOrBlank()) {
            try {
                val pkgIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(preferredPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (pkgIntent.resolveActivity(pm) != null || isPackageInstalled(context, preferredPackage)) {
                    context.startActivity(pkgIntent)
                    return true
                }
            } catch (_: Exception) {
                // استمرار
            }
        }

        // ج) محاولة الفتح العامة القياسية (Standard Intent.ACTION_VIEW)
        try {
            val generalIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(generalIntent)
            return true
        } catch (_: Exception) {
            // فشل الفتح المباشر، الانتقال للمرحلة التالية
        }

        // د) استدعاء موجه التطبيقات لنظام أندرويد (Intent Chooser)
        try {
            val chooserIntent = Intent.createChooser(
                Intent(Intent.ACTION_VIEW, uri),
                "فتح $targetDescription بواسطة..."
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
            return true
        } catch (_: Exception) {
            // فشل الموجه
        }

        // هـ) تجربة المتصفحات الشائعة المثبتة على الجهاز واحداً تلو الآخر
        for (browserPkg in KNOWN_BROWSERS) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(browserPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (browserIntent.resolveActivity(pm) != null || isPackageInstalled(context, browserPkg)) {
                    context.startActivity(browserIntent)
                    return true
                }
            } catch (_: Exception) {
                // محاولة المتصفح التالي
            }
        }

        // و) الحل الاحتياطي الحاسم والشامل: النسخ للحافظة وإعلام المستخدم
        copyToClipboard(context, url, targetDescription)
        Toast.makeText(
            context,
            "تعذر فتح التطبيق مباشرة في نظامك. تم نسخ رابط $targetDescription للحافظة 📋 يمكنك لصقه في المتصفح.",
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    /**
     * فحص وجود حزمة تطبيق معينة
     */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * نسخ النص إلى الحافظة بأمان
     */
    fun copyToClipboard(context: Context, text: String, label: String = "رابط قبس") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {
        }
    }
}
