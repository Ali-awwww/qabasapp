package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 🌐 إعدادات وبوابة الربط بالموقع الرسمي لمنظومة قبس (Qabas Official Web Hub)
 */
object QabasWebConfig {

    /** الرابط الأساسي للموقع الرسمي */
    const val OFFICIAL_WEBSITE_URL = "https://ais-pre-4m7ngsd7prdvbhwsiegpif-753796887195.europe-west2.run.app"
    const val OFFICIAL_WEB_HOST = "ais-pre-4m7ngsd7prdvbhwsiegpif-753796887195.europe-west2.run.app"

    /** روابط الأقسام المباشرة على الموقع */
    const val URL_DOWNLOAD = "$OFFICIAL_WEBSITE_URL/download"
    const val URL_LECTURES = "$OFFICIAL_WEBSITE_URL/lectures"
    const val URL_NEWS = "$OFFICIAL_WEBSITE_URL/news"
    const val URL_REVIEWS = "$OFFICIAL_WEBSITE_URL/reviews"
    const val URL_ADMIN_PORTAL = "$OFFICIAL_WEBSITE_URL/admin"

    /** رابط مجموعة ومجتمع قبس الرسمي (WhatsApp / Telegram) */
    const val DEFAULT_COMMUNITY_GROUP_URL = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2"

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

    fun setOfficialGroupUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_GROUP_URL, url.trim()).apply()
    }

    /**
     * فتح رابط مجموعة قبس الرسمية
     */
    fun openCommunityGroup(context: Context) {
        val groupUrl = getOfficialGroupUrl(context)
        QabasLinkOpener.openUrl(context, groupUrl, "مجموعة قبس على واتساب")
    }

    /**
     * فتح رابط الموقع أو أي مسار فرعي عبر متصفح الهاتف
     */
    fun openWebsite(context: Context, customUrl: String = OFFICIAL_WEBSITE_URL) {
        QabasLinkOpener.openUrl(context, customUrl, "موقع قبس")
    }
}
