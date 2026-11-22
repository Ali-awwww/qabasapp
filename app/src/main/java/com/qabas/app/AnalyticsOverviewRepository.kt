package com.qabas.app

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ============================================================================
// نظرة عامة على النمو للوحة المطور (الدفعة 5 — الجزء أ، إعادة إنشاء)
// ============================================================================
//
// تقرأ مقاييس حقيقية فقط من جدولين:
//   - analytics_events: أحداث التطبيق (user_id / event_name / session_id / created_at)
//   - profiles: عدد المستخدمين وتاريخ إنشائهم (created_at)
// القاعدة الصريحة: أي مقياس بلا مصدر بيانات يظهر «لا توجد بيانات بعد»
// (قيمة null) ولا يُعرض صفر على أنه قياس. لا معدل خالٍ من الأعطال هنا:
// لا مصدر لإجمالي الجلسات، فيبقى «لا بيانات» موثقاً لا بطاقة (قاعدة
// الدفعة 5-ب). كل قراءة تفشل بلطف إلى فارغ عند غياب الجدول
// (على نمط getMishkatDaily / DevWorkQueuesRepository).

private const val TAG = "AnalyticsOverview"

/** صف حدث واحد كما يُقرأ من analytics_events. */
@Serializable
data class AnalyticsEventRow(
    @SerialName("id") val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("event_name") val eventName: String = "",
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** صف ملف واحد كما يُقرأ من profiles (نحتاج created_at فقط). */
@Serializable
data class ProfileCreatedRow(
    @SerialName("id") val id: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

/** عدد المستخدمين المميزين في يوم واحد (لصف الأعمدة). */
data class DayCount(val dayLabel: String, val count: Int)

/** عدد مرات اسم حدث واحد (لأكثر الأحداث). */
data class EventCount(val eventName: String, val count: Int)

/**
 * مقاييس النظرة العامة. كل حقل Int?:
 *   - null = لا بيانات (الجدول غائب أو المصدر بلا قيم قابلة للقياس)
 *   - رقم = قياس حقيقي من الصفوف المقروءة فعلاً
 */
data class OverviewStats(
    val analyticsAvailable: Boolean = false,
    /** مستخدمون مسجَّلون مميزون اليوم (user_id غير فارغ). */
    val dau: Int? = null,
    /** جلسات مجهولة مميزة اليوم (user_id فارغ وsession_id غير فارغ) — تُعرض منفصلة ولا تُدمج مع DAU. */
    val anonSessionsToday: Int? = null,
    /** مستخدمون مسجَّلون مميزون في آخر 30 يوماً. */
    val mau: Int? = null,
    /** مستخدمون جدد في آخر 7 أيام؛ null إن لم يملك أي ملف created_at أصلاً. */
    val newUsers7d: Int? = null,
    /** إجمالي صفوف profiles. */
    val totalUsers: Int? = null,
    /** عدد أحداث اليوم. */
    val eventsToday: Int? = null,
    /** DAU لكل يوم من آخر 7 أيام (فارغة إن غاب الجدول). */
    val dailyDau: List<DayCount> = emptyList(),
    /** أكثر 5 أسماء أحداث في آخر 7 أيام (فارغة إن لم توجد أحداث). */
    val topEvents: List<EventCount> = emptyList()
)

object AnalyticsOverviewRepository {

    // سقف القراءة الواحدة: التطبيق اليوم 3 مستخدمين، فالسقف واسع؛
    // إن تجاوزت الأحداث السقف تُحسب المقاييس من أحدث الصفوف فقط
    // ويُسجَّل ذلك في Logcat (لا رقم مخترع ولا ادعاء اكتمال).
    private const val EVENTS_LIMIT = 10_000L
    private const val PROFILES_LIMIT = 5_000L

    /** يحمّل كل مقاييس النظرة العامة؛ أي فشل -> إحصاءات بلا بيانات. */
    suspend fun loadOverview(): OverviewStats = runCatching {
        val client = SupabaseServices.client ?: return OverviewStats(analyticsAvailable = false)
        val now = Instant.now()
        val todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
        val day7Start = todayStart.minusSeconds(7 * 24 * 3600)
        val day30Start = todayStart.minusSeconds(30 * 24 * 3600)

        val events = client.postgrest.from("analytics_events").select {
            filter { gte("created_at", DateTimeFormatter.ISO_INSTANT.format(day30Start)) }
            limit(EVENTS_LIMIT)
        }.decodeList<AnalyticsEventRow>()
        if (events.size >= EVENTS_LIMIT) {
            Log.w(TAG, "analytics_events hit read limit $EVENTS_LIMIT; metrics cover newest rows only")
        }

        val profiles = client.postgrest.from("profiles").select {
            limit(PROFILES_LIMIT)
        }.decodeList<ProfileCreatedRow>()

        val parsedEvents = events.mapNotNull { row ->
            val at = parseInstant(row.createdAt) ?: return@mapNotNull null
            row to at
        }

        val todayEvents = parsedEvents.filter { (_, at) -> !at.isBefore(todayStart) }
        val dau = todayEvents.mapNotNull { (row, _) -> row.userId?.takeIf { it.isNotBlank() } }.toSet().size
        val anonSessionsToday = todayEvents
            .filter { (row, _) -> row.userId.isNullOrBlank() }
            .mapNotNull { (row, _) -> row.sessionId?.takeIf { it.isNotBlank() } }
            .toSet().size
        val mau = parsedEvents.mapNotNull { (row, _) -> row.userId?.takeIf { it.isNotBlank() } }.toSet().size

        val weekEvents = parsedEvents.filter { (_, at) -> !at.isBefore(day7Start) }
        val topEvents = weekEvents
            .groupingBy { (row, _) -> row.eventName.takeIf { it.isNotBlank() } ?: "غير معروف" }
            .eachCount()
            .entries.sortedByDescending { it.value }.take(5)
            .map { EventCount(it.key, it.value) }

        // عمود DAU لآخر 7 أيام (اليوم أولاً من اليسار): كل يوم مستخدمون مميزون.
        val dailyDau = (6 downTo 0).map { back ->
            val dayStart = todayStart.minusSeconds(back.toLong() * 24 * 3600)
            val dayEnd = dayStart.plusSeconds(24 * 3600)
            val count = parsedEvents
                .filter { (_, at) -> !at.isBefore(dayStart) && at.isBefore(dayEnd) }
                .mapNotNull { (row, _) -> row.userId?.takeIf { it.isNotBlank() } }
                .toSet().size
            val label = LocalDate.ofInstant(dayStart, ZoneOffset.UTC).dayOfMonth.toString()
            DayCount(label, count)
        }

        // المستخدمون الجدد: يُقاس فقط إن وُجد created_at غير فارغ في أي ملف؛
        // كل القيم فارغة (كما فُحص: created_at فارغ في العينة) -> لا بيانات.
        val createdAts = profiles.mapNotNull { parseInstant(it.createdAt) }
        val newUsers7d = if (createdAts.isEmpty()) null
        else createdAts.count { !it.isBefore(day7Start) && !it.isAfter(now) }

        OverviewStats(
            analyticsAvailable = true,
            dau = dau,
            anonSessionsToday = anonSessionsToday,
            mau = mau,
            newUsers7d = newUsers7d,
            totalUsers = profiles.size,
            eventsToday = todayEvents.size,
            dailyDau = dailyDau,
            topEvents = topEvents
        )
    }.onFailure {
        Log.w(TAG, "loadOverview failed (table probably not applied yet): ${it.message}")
    }.getOrDefault(OverviewStats(analyticsAvailable = false))

    private fun parseInstant(raw: String?): Instant? =
        raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
