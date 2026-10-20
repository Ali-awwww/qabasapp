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
 * 2. مفتاح التفضيلات لكل صلاة واحد ومتوافق بين الواجهة ومدير الجدولة.
 * 3. أنماط الصوت المدعومة مطابقة تماماً لما يتعامل معه PrayerAzanReceiver.
 * 4. تتبع الورد (سلسلة الأيام) يعمل ويزداد.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
        NotificationPreferencesManager.setPrayerPreferences(
            context = context,
            prayerKey = "fajr",
            enabled = false,
            soundMode = NotificationPreferencesManager.SOUND_TAKBEER_ONLY,
            voiceId = "makkah_mulla"
        )

        val (enabled, soundMode) =
            NotificationPreferencesManager.getPrayerPreferences(context, "fajr")
        assertFalse(enabled)
        assertEquals(NotificationPreferencesManager.SOUND_TAKBEER_ONLY, soundMode)

        // التحقّق من المخزن نفسه الذي يكتب فيه PrayerTimesScreen
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        assertFalse(prayerPrefs.getBoolean("notify_fajr", true))
        assertEquals(
            NotificationPreferencesManager.SOUND_TAKBEER_ONLY,
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
        // وواجهة التفضيلات أيضاً
        val (enabled, _) = NotificationPreferencesManager.getPrayerPreferences(context, "dhuhr")
        assertFalse(enabled)
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
    fun `supported sound modes match receiver contract`() {
        val supported = setOf(
            NotificationPreferencesManager.SOUND_FULL_AZAN,
            NotificationPreferencesManager.SOUND_TAKBEER_ONLY,
            NotificationPreferencesManager.SOUND_NOTIFICATION_ONLY,
            NotificationPreferencesManager.SOUND_SILENT
        )

        // القيم التي يفرّق بينها PrayerAzanReceiver فعلياً
        val receiverSupported = setOf("FULL_AZAN", "TAKBEER_ONLY", "NOTIFICATION_ONLY", "SILENT")
        assertEquals(receiverSupported, supported)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. تتبع الورد
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `recording a wird increments the streak`() {
        val before = NotificationPreferencesManager.wiradStreak.value
        NotificationPreferencesManager.recordWirdCompleted(context)
        NotificationPreferencesManager.recordWirdCompleted(context)
        assertEquals(before + 2, NotificationPreferencesManager.wiradStreak.value)
    }

    @Test
    fun `recording a wird stores the last completion date`() {
        NotificationPreferencesManager.recordWirdCompleted(context)
        val prefs = context.getSharedPreferences("qabas_notification_settings", Context.MODE_PRIVATE)
        val stamp = prefs.getString("notif_last_wirad_date", null)
        assertTrue("تاريخ آخر ورد لم يُحفظ", stamp != null && stamp.isNotBlank())
    }
}
