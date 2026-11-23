package com.qabas.app

import android.content.Context

/**
 * 🌐 إعدادات وبوابة الربط بالموقع الرسمي لمنظومة خادم المسلم (Qabas Official Web Hub)
 */
object QabasWebConfig {

    /** الرابط الأساسي للموقع الرسمي */
    const val OFFICIAL_WEBSITE_URL = "https://alippappa7-maker.github.io/Website-/"
    const val OFFICIAL_WEB_HOST = "alippappa7-maker.github.io"

    /** مضيف النشر التجريبي القديم (run.app) — محتفظ به كاحتياطي
     * للروابط العميقة فقط، وليس الموقع الرسمي. */
    const val LEGACY_WEB_HOST = "ais-pre-4m7ngsd7prdvbhwsiegpif-753796887195.europe-west2.run.app"

    /** رابط مجموعة ومجتمع خادم المسلم الرسمي (WhatsApp / Telegram) */
    const val DEFAULT_COMMUNITY_GROUP_URL = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=sh&p=a&mlu=4&ilr=4&iam=2"

    private const val PREFS_NAME = "qabas_prefs"
    private const val KEY_CUSTOM_GROUP_URL = "official_community_group_url"

    fun getOfficialGroupUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_GROUP_URL, null)
        // إذا كان المحفوظ قديماً أو فارغاً نعتمد الرابط الرسمي المحدث
        return if (saved.isNullOrBlank() || saved.contains("QabasCommunityGroup")) {
            DEFAULT_COMMUNITY_GROUP_URL
        } else {
            saved
        }
    }

    /**
     * فتح رابط مجموعة خادم المسلم الرسمية
     */
    fun openCommunityGroup(context: Context) {
        val groupUrl = getOfficialGroupUrl(context)
        QabasLinkOpener.openUrl(context, groupUrl, "مجموعة خادم المسلم على واتساب")
    }

    /**
     * فتح رابط الموقع أو أي مسار فرعي عبر متصفح الهاتف
     */
    fun openWebsite(context: Context, customUrl: String = OFFICIAL_WEBSITE_URL) {
        QabasLinkOpener.openUrl(context, customUrl, "موقع خادم المسلم")
    }
}
