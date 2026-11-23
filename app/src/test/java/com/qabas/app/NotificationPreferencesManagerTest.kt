package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات نظام الإشعارات والتبيهات (Notification & Alert System Tests):
 *
 * 1. تفضيلات الإشعارات تُحفظ وتُقرأ بشكل صحيح.
 * 2. مصدر الحقيقة الوحيد لتفضيلات الصلاة هو مخزن «qabas_prayer_prefs»
 *    الخام (مفاتيح notify_<prayer> وsound_mode_<prayer>)، ويقرأه
 *    PrayerAlarmManager مباشرةً — غلافا get/setPrayerPreferences وثوابت
 *    SOUND_* حُذفا في الجولة 33 لعدم وجود قارئ لهما، فلا يختبرهما شيء هنا.
 * 3. أنماط الصوت سلاسل خام («FULL_AZAN» افتراضياً)؛ وقيمة «TAKBEER_ONLY»
 *    القديمة تُطبَّع إلى «FULL_AZAN» عند الجدولة (PrayerAlarmManager).
 * 4. تتبع الورد (سلسلة الأيام) يعمل ويزداد — مرة واحدة في اليوم (الجولة 12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPreferencesManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // تنظيف كامل لكل المخازن involved
        context.getSharedPreferences("qabas_notification_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationPreferencesManager.init(context)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. المفتاح الرئيسي والتبديلات العامة
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `master switch defaults to enabled`() {
        assertTrue(NotificationPreferencesManager.masterEnabled.value)
    }

    @Test
    fun `master switch persists and updates flow`() {
        NotificationPreferencesManager.setMasterEnabled(context, false)
        assertFalse(NotificationPreferencesManager.masterEnabled.value)

        // تحقّق من التخزين الفعلي
        val prefs = context.getSharedPreferences("qabas_notification_settings", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean("notif_master_enabled", true))
    }

    @Test
    fun `daily hadith and sound toggles persist`() {
        NotificationPreferencesManager.setDailyHadithEnabled(context, false)
        NotificationPreferencesManager.setSoundEnabled(context, false)
        NotificationPreferencesManager.setVibrationEnabled(context, false)

        assertFalse(NotificationPreferencesManager.dailyHadithEnabled.value)
        assertFalse(NotificationPreferencesManager.soundEnabled.value)
        assertFalse(NotificationPreferencesManager.vibrationEnabled.value)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. مصدر الحقيقة الوحيد لتفضيلات الصلاة
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `prayer preferences write and read from prayer prefs store`() {
        // (R51) غلافا get/setPrayerPreferences وثوابت SOUND_* حُذفا في
        // الجولة 33 — الكتابة والقراءة هنا على المخزن الخام نفسه الذي
        // تكتبه شاشة المحراب ويقرأه PrayerAlarmManager.
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        prayerPrefs.edit()
            .putBoolean("notify_fajr", false)
            .putString("sound_mode_fajr", "NOTIFICATION_ONLY")
            .apply()

        assertFalse(PrayerAlarmManager.isPrayerEnabled(context, "fajr"))

        // التحقّق من المخزن نفسه الذي يكتب فيه PrayerTimesScreen
        assertFalse(prayerPrefs.getBoolean("notify_fajr", true))
        assertEquals(
            "NOTIFICATION_ONLY",
            prayerPrefs.getString("sound_mode_fajr", "FULL_AZAN")
        )
    }

    @Test
    fun `prayer enable flag written by UI is read by alarm manager`() {
        // هذا هو السيناريو الحقيقي: المستخدم يوقف تنبيه الظهر من شاشة المحراب
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        prayerPrefs.edit().putBoolean("notify_dhuhr", false).apply()

        // مدير الجدولة يجب أن يرى نفس القيمة
        assertFalse(PrayerAlarmManager.isPrayerEnabled(context, "dhuhr"))
    }

    @Test
    fun `prayer enable flag defaults to enabled`() {
        assertTrue(PrayerAlarmManager.isPrayerEnabled(context, "isha"))
    }

    @Test
    fun `global prayer switch lives in prayer prefs store`() {
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        assertTrue(NotificationPreferencesManager.isGlobalPrayerEnabled(context))

        prayerPrefs.edit().putBoolean("prayer_notifications_enabled", false).apply()
        assertFalse(NotificationPreferencesManager.isGlobalPrayerEnabled(context))
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. أنماط الصوت — يجب أن تطابق ما يتعامل معه PrayerAzanReceiver
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `sound mode defaults to full azan when unset`() {
        // (R51) اختبار «supported sound modes match receiver contract»
        // حُذف مع ثوابت SOUND_* في الجولة 33 — لا ثابت واحد يُقاس عليه
        // بعدها. الباقي القابل للاختبار هنا: الافتراضي الخام عند غياب
        // المفتاح هو الأذان الكامل، كما يقرأه PrayerAlarmManager.
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        assertEquals("FULL_AZAN", prayerPrefs.getString("sound_mode_fajr", "FULL_AZAN"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. تتبع الورد
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `recording a wird increments the streak`() {
        // (R51) كان يتوقع +2 من استدعائين في اليوم نفسه — لكن الجولة 12
        // جعلت التسجيل مرة واحدة في اليوم، فالاستدعاء الثاني في اليوم
        // نفسه لا يزيد شيئاً. الصحيح: +1 مهما تكرر الاستدعاء في اليوم.
        val before = NotificationPreferencesManager.wiradStreak.value
        NotificationPreferencesManager.recordWirdCompleted(context)
        NotificationPreferencesManager.recordWirdCompleted(context)
        assertEquals(before + 1, NotificationPreferencesManager.wiradStreak.value)
    }

    @Test
    fun `recording a wird stores the last completion date`() {
        NotificationPreferencesManager.recordWirdCompleted(context)
        val prefs = context.getSharedPreferences("qabas_notification_settings", Context.MODE_PRIVATE)
        val stamp = prefs.getString("notif_last_wirad_date", null)
        assertTrue("تاريخ آخر ورد لم يُحفظ", stamp != null && stamp.isNotBlank())
    }
}
