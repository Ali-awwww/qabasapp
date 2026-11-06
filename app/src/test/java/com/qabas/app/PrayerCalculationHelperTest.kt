package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrayerCalculationHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("qabas_prayer_location_private", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `gps city round trips with precise coordinates in local storage`() {
        // Ankara is a real city outside the built-in popular-city list.
        val ankara = CityLocation(
            nameAr = "أنقرة",
            nameEn = "Ankara",
            countryAr = "تركيا",
            latitude = 39.9334,
            longitude = 32.8597,
            timeZoneOffsetHours = 3.0,
            defaultMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            timeZoneId = "Europe/Istanbul"
        )

        assertTrue(PrayerCalculationHelper.saveCity(context, ankara))

        assertEquals(ankara, PrayerCalculationHelper.getSavedCity(context))
        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        val localLocationPrefs = context.getSharedPreferences("qabas_prayer_location_private", Context.MODE_PRIVATE)
        val nearestCityIndex = appPrefs.getInt("selected_city_index", -1)

        assertTrue(nearestCityIndex in PrayerCalculationHelper.popularCities.indices)
        assertEquals(nearestCityIndex, prayerPrefs.getInt("selected_city_index", -1))
        assertTrue(localLocationPrefs.getBoolean("custom_city_enabled", false))
        assertEquals(ankara.latitude.toString(), localLocationPrefs.getString("custom_city.latitude", null))
        assertEquals(ankara.longitude.toString(), localLocationPrefs.getString("custom_city.longitude", null))
        assertEquals(ankara.timeZoneId, localLocationPrefs.getString("custom_city.timezone_id", null))
    }

    @Test
    fun `popular cities have valid timezone identifiers`() {
        val availableIds = TimeZone.getAvailableIDs().toSet()
        assertTrue(PrayerCalculationHelper.popularCities.all { city ->
            val zoneId = city.timeZoneId
            !zoneId.isNullOrBlank() && zoneId in availableIds
        })
    }

    @Test
    fun `countdown uses selected city timezone instead of source calendar timezone`() {
        val instantMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JANUARY, 15, 2, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val newYork = CityLocation(
            nameAr = "نيويورك",
            nameEn = "New York",
            countryAr = "الولايات المتحدة",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZoneOffsetHours = -5.0,
            defaultMethod = CalculationMethod.ISNA,
            timeZoneId = "America/New_York"
        )
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = instantMillis
        }
        val istanbulCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul")).apply {
            timeInMillis = instantMillis
        }

        assertEquals(
            PrayerCalculationHelper.calculatePrayerTimes(utcCalendar, newYork),
            PrayerCalculationHelper.calculatePrayerTimes(istanbulCalendar, newYork)
        )
    }

    @Test
    fun `named city timezone applies daylight saving to prayer clock times`() {
        val instantMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JULY, 15, 16, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val newYork = CityLocation(
            nameAr = "نيويورك",
            nameEn = "New York",
            countryAr = "الولايات المتحدة",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZoneOffsetHours = -5.0,
            defaultMethod = CalculationMethod.ISNA,
            timeZoneId = "America/New_York"
        )
        val fixedOffsetCity = newYork.copy(timeZoneId = null)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = instantMillis
        }
        val namedZoneDhuhr = PrayerCalculationHelper.calculatePrayerTimes(calendar, newYork).dhuhr
        val fixedOffsetDhuhr = PrayerCalculationHelper.calculatePrayerTimes(calendar, fixedOffsetCity).dhuhr

        assertEquals(60, kotlin.math.abs(minuteOfDay(namedZoneDhuhr) - minuteOfDay(fixedOffsetDhuhr)))
    }

    @Test
    fun `DST transition uses offset at each prayer instant`() {
        val instantMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.MARCH, 29, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tromso = CityLocation(
            nameAr = "ترومسو",
            nameEn = "Tromso",
            countryAr = "النرويج",
            latitude = 69.6492,
            longitude = 18.9553,
            timeZoneOffsetHours = 1.0,
            defaultMethod = CalculationMethod.ISNA,
            timeZoneId = "Europe/Oslo"
        )
        val fixedCet = tromso.copy(timeZoneId = null)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = instantMillis
        }
        val namedZoneTimes = PrayerCalculationHelper.calculatePrayerTimes(calendar, tromso)
        val fixedCetTimes = PrayerCalculationHelper.calculatePrayerTimes(calendar, fixedCet)

        assertEquals(fixedCetTimes.fajr, namedZoneTimes.fajr)
        assertEquals(60, kotlin.math.abs(minuteOfDay(namedZoneTimes.dhuhr) - minuteOfDay(fixedCetTimes.dhuhr)))
    }

    @Test
    fun `alarm wall clock is converted to selected city absolute time`() {
        val referenceMillis = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul")).apply {
            set(2026, Calendar.JANUARY, 15, 2, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val newYork = CityLocation(
            nameAr = "نيويورك",
            nameEn = "New York",
            countryAr = "الولايات المتحدة",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZoneOffsetHours = -5.0,
            defaultMethod = CalculationMethod.ISNA,
            timeZoneId = "America/New_York"
        )

        val todayAlarm = PrayerCalculationHelper.calendarForPrayerTime("05:00", referenceMillis, newYork)
        val tomorrowAlarm = PrayerCalculationHelper.calendarForPrayerTime(
            "05:00",
            referenceMillis,
            newYork,
            dayOffset = 1
        )
        val deviceCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul")).apply {
            timeInMillis = tomorrowAlarm.timeInMillis
        }

        assertEquals(14, todayAlarm.get(Calendar.DAY_OF_MONTH))
        assertEquals(5, todayAlarm.get(Calendar.HOUR_OF_DAY))
        assertTrue(todayAlarm.timeInMillis < referenceMillis)
        assertEquals(15, tomorrowAlarm.get(Calendar.DAY_OF_MONTH))
        assertEquals(5, tomorrowAlarm.get(Calendar.HOUR_OF_DAY))
        assertTrue(tomorrowAlarm.timeInMillis > referenceMillis)
        assertEquals(13, deviceCalendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `countdown after isha uses tomorrow calculated fajr`() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply {
            set(2026, Calendar.JANUARY, 14, 22, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val newYork = CityLocation(
            nameAr = "نيويورك",
            nameEn = "New York",
            countryAr = "الولايات المتحدة",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZoneOffsetHours = -5.0,
            defaultMethod = CalculationMethod.ISNA,
            timeZoneId = "America/New_York"
        )
        val today = PrayerCalculationHelper.calculatePrayerTimes(calendar, newYork)
        val tomorrowCalendar = (calendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrow = PrayerCalculationHelper.calculatePrayerTimes(
            tomorrowCalendar,
            newYork,
            includeNextDayFajr = false
        )

        assertEquals("الفجر", today.nextPrayerName)
        assertEquals(tomorrow.fajr, today.nextPrayerTimeFormatted)
        assertTrue(today.millisUntilNextPrayer > 0L)
    }

    private fun minuteOfDay(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
    @Test
    fun `choosing a built in city clears local custom coordinates and syncs indexes`() {
        val ankara = CityLocation(
            nameAr = "أنقرة",
            nameEn = "Ankara",
            countryAr = "تركيا",
            latitude = 39.9334,
            longitude = 32.8597,
            timeZoneOffsetHours = 3.0,
            defaultMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE
        )
        PrayerCalculationHelper.saveCity(context, ankara)

        val istanbul = PrayerCalculationHelper.popularCities.first { it.nameAr == "إسطنبول" }
        PrayerCalculationHelper.saveCity(context, istanbul)

        val expectedIndex = PrayerCalculationHelper.popularCities.indexOf(istanbul)
        assertEquals(istanbul, PrayerCalculationHelper.getSavedCity(context))
        assertEquals(expectedIndex, context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getInt("selected_city_index", -1))
        assertEquals(expectedIndex, context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
            .getInt("selected_city_index", -1))
        assertFalse(context.getSharedPreferences("qabas_prayer_location_private", Context.MODE_PRIVATE)
            .getBoolean("custom_city_enabled", true))
    }

    @Test
    fun `synced nearest city index is used when precise location data is not on device`() {
        val ankara = CityLocation(
            nameAr = "أنقرة",
            nameEn = "Ankara",
            countryAr = "تركيا",
            latitude = 39.9334,
            longitude = 32.8597,
            timeZoneOffsetHours = 3.0,
            defaultMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE
        )
        PrayerCalculationHelper.saveCity(context, ankara)

        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val nearestCityIndex = appPrefs.getInt("selected_city_index", -1)
        context.getSharedPreferences("qabas_prayer_location_private", Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertEquals(PrayerCalculationHelper.popularCities[nearestCityIndex], PrayerCalculationHelper.getSavedCity(context))
    }
}