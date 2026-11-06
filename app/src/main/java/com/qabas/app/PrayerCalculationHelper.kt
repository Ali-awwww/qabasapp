package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Calendar
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone
import kotlin.math.*

/**
 * Calculation methods for prayer times
 */
enum class CalculationMethod(val displayName: String, val fajrAngle: Double, val ishaAngle: Double, val ishaIntervalMinutes: Int = 0) {
    UMM_AL_QURA("أم القرى - مكة المكرمة", 18.5, 0.0, 90),
    EGYPTIAN("الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    MUSLIM_WORLD_LEAGUE("رابطة العالم الإسلامي", 18.0, 17.0),
    ISNA("أمريكا الشمالية (ISNA)", 15.0, 15.0),
    KARACHI("جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0),
    DUBAI("دبي - الأوقاف والشؤون الإسلامية", 18.2, 18.2)
}

data class CityLocation(
    val nameAr: String,
    val nameEn: String,
    val countryAr: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneOffsetHours: Double,
    val defaultMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val timeZoneId: String? = null
)

data class CalculatedPrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val midnight: String,
    val qiyamStart: String,
    val nextPrayerName: String,
    val nextPrayerTimeFormatted: String,
    val millisUntilNextPrayer: Long,
    val progressToNextPrayer: Float,
    val qiblaDirectionDeg: Float,
    val distanceToMakkahKm: Int
)

object PrayerCalculationHelper {

    val popularCities = listOf(
        CityLocation("مكة المكرمة", "Makkah", "السعودية", 21.4225, 39.8262, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Riyadh"),
        CityLocation("المدينة المنورة", "Madinah", "السعودية", 24.4672, 39.6111, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Riyadh"),
        CityLocation("الرياض", "Riyadh", "السعودية", 24.7136, 46.6753, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Riyadh"),
        CityLocation("جدة", "Jeddah", "السعودية", 21.5433, 39.1728, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Riyadh"),
        CityLocation("القاهرة", "Cairo", "مصر", 30.0444, 31.2357, 2.0, CalculationMethod.EGYPTIAN, "Africa/Cairo"),
        CityLocation("الإسكندرية", "Alexandria", "مصر", 31.2001, 29.9187, 2.0, CalculationMethod.EGYPTIAN, "Africa/Cairo"),
        CityLocation("القدس الشريف", "Jerusalem", "فلسطين", 31.7683, 35.2137, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Jerusalem"),
        CityLocation("غزة", "Gaza", "فلسطين", 31.5017, 34.4668, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Gaza"),
        CityLocation("دمشق", "Damascus", "سوريا", 33.5138, 36.2765, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Damascus"),
        CityLocation("بغداد", "Baghdad", "العراق", 33.3152, 44.3661, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Baghdad"),
        CityLocation("أبوظبي", "Abu Dhabi", "الإمارات", 24.4539, 54.3773, 4.0, CalculationMethod.DUBAI, "Asia/Dubai"),
        CityLocation("دبي", "Dubai", "الإمارات", 25.2048, 55.2708, 4.0, CalculationMethod.DUBAI, "Asia/Dubai"),
        CityLocation("الكويت", "Kuwait", "الكويت", 29.3759, 47.9774, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Kuwait"),
        CityLocation("الدوحة", "Doha", "قطر", 25.2854, 51.5310, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Qatar"),
        CityLocation("المنامة", "Manama", "البحرين", 26.2285, 50.5860, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Bahrain"),
        CityLocation("مسقط", "Muscat", "عُمان", 23.5880, 58.3829, 4.0, CalculationMethod.UMM_AL_QURA, "Asia/Muscat"),
        CityLocation("صنعاء", "Sanaa", "اليمن", 15.3694, 44.1910, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Aden"),
        CityLocation("عدن", "Aden", "اليمن", 12.7855, 45.0187, 3.0, CalculationMethod.UMM_AL_QURA, "Asia/Aden"),
        CityLocation("عمّان", "Amman", "الأردن", 31.9454, 35.9284, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Amman"),
        CityLocation("بيروت", "Beirut", "لبنان", 33.8938, 35.5018, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Beirut"),
        CityLocation("الرباط", "Rabat", "المغرب", 34.0209, -6.8416, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Casablanca"),
        CityLocation("الدار البيضاء", "Casablanca", "المغرب", 33.5731, -7.5898, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Casablanca"),
        CityLocation("الجزائر", "Algiers", "الجزائر", 36.7538, 3.0588, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Algiers"),
        CityLocation("تونس", "Tunis", "تونس", 36.8065, 10.1815, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Tunis"),
        CityLocation("طرابلس", "Tripoli", "ليبيا", 32.8872, 13.1913, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Tripoli"),
        CityLocation("الخرطوم", "Khartoum", "السودان", 15.5007, 32.5599, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Khartoum"),
        CityLocation("نواكشوط", "Nouakchott", "موريتانيا", 18.0735, -15.9582, 0.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Nouakchott"),
        CityLocation("مقديشو", "Mogadishu", "الصومال", 2.0469, 45.3182, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Mogadishu"),
        CityLocation("إسطنبول", "Istanbul", "تركيا", 41.0082, 28.9784, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Europe/Istanbul"),
        CityLocation("جاكرتا", "Jakarta", "إندونيسيا", -6.2088, 106.8456, 7.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Jakarta"),
        CityLocation("كوالالمبور", "Kuala Lumpur", "ماليزيا", 3.1390, 101.6869, 8.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Kuala_Lumpur"),
        CityLocation("لندن", "London", "المملكة المتحدة", 51.5074, -0.1278, 0.0, CalculationMethod.ISNA, "Europe/London"),
        CityLocation("باريس", "Paris", "فرنسا", 48.8566, 2.3522, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Europe/Paris"),
        CityLocation("برلين", "Berlin", "ألمانيا", 52.5200, 13.4050, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Europe/Berlin"),
        CityLocation("نيويورك", "New York", "الولايات المتحدة", 40.7128, -74.0060, -5.0, CalculationMethod.ISNA, "America/New_York"),
        CityLocation("تورونتو", "Toronto", "كندا", 43.6532, -79.3832, -5.0, CalculationMethod.ISNA, "America/Toronto"),
        CityLocation("سيدني", "Sydney", "أستراليا", -33.8688, 151.2093, 10.0, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Australia/Sydney")
    )

    private const val TAG = "PrayerCalculationHelper"
    private const val CITY_PREFS_NAME = "qabas_prefs"
    private const val PRAYER_PREFS_NAME = "qabas_prayer_prefs"
    // Precise GPS coordinates stay in a device-local store and are never cloud-synced.
    private const val PRIVATE_LOCATION_PREFS_NAME = "qabas_prayer_location_private"
    private const val KEY_SELECTED_CITY_NAME = "selected_prayer_city_name"
    private const val KEY_SYNCED_CITY_INDEX = "selected_city_index"
    private const val KEY_LOCAL_CITY_INDEX = "selected_city_index"
    private const val KEY_CUSTOM_CITY_ENABLED = "custom_city_enabled"
    private const val CUSTOM_CITY_PREFIX = "custom_city"
    private const val LAST_SCHEDULED_CITY_PREFIX = "last_scheduled_city"
    private const val RESCHEDULE_DISTANCE_KM = 5.0
    private const val RESCHEDULE_TIMEZONE_DELTA_HOURS = 0.25
    private val timeZoneCache = mutableMapOf<String, TimeZone>()
    private val utcTimeZone = TimeZone.getTimeZone("UTC")
    private val tomorrowFajrCache = mutableMapOf<TomorrowFajrCacheKey, String>()

    /** Resolve the selected city's civil time zone, falling back to its saved fixed offset. */
    fun timeZoneFor(location: CityLocation): TimeZone {
        val zoneId = location.timeZoneId?.trim()?.takeIf { it.isNotEmpty() }
        val offsetMillis = (location.timeZoneOffsetHours * 3_600_000.0).roundToInt()
        val cacheKey = "${zoneId.orEmpty()}|$offsetMillis"

        return synchronized(timeZoneCache) {
            timeZoneCache.getOrPut(cacheKey) {
                if (zoneId != null) {
                    val resolved = TimeZone.getTimeZone(zoneId)
                    if (resolved.id != "GMT" || zoneId == "GMT" || zoneId.startsWith("GMT")) {
                        resolved
                    } else {
                        SimpleTimeZone(offsetMillis, "Qabas-${location.nameEn}")
                    }
                } else {
                    SimpleTimeZone(offsetMillis, "Qabas-${location.nameEn}")
                }
            }
        }
    }

    /** Build an alarm instant for a wall-clock prayer time on the selected city's date. */
    fun calendarForPrayerTime(
        timeString: String,
        referenceMillis: Long,
        location: CityLocation,
        dayOffset: Int = 0
    ): Calendar {
        val parts = timeString.trim().split(":")
        require(parts.size == 2) { "Invalid prayer time: $timeString" }
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 }
            ?: throw IllegalArgumentException("Invalid prayer hour: $timeString")
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 }
            ?: throw IllegalArgumentException("Invalid prayer minute: $timeString")

        return Calendar.getInstance(timeZoneFor(location)).apply {
            timeInMillis = referenceMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun getSavedCity(context: Context): CityLocation {
        val cityPrefs = context.getSharedPreferences(CITY_PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = cityPrefs.getString(KEY_SELECTED_CITY_NAME, "مكة المكرمة") ?: "مكة المكرمة"
        val privatePrefs = context.getSharedPreferences(PRIVATE_LOCATION_PREFS_NAME, Context.MODE_PRIVATE)

        if (privatePrefs.getBoolean(KEY_CUSTOM_CITY_ENABLED, false)) {
            val localCustomName = privatePrefs.getString("$CUSTOM_CITY_PREFIX.name_ar", null)
            if (localCustomName == savedName) {
                readStoredCity(privatePrefs, CUSTOM_CITY_PREFIX)?.let { stored ->
                    if (stored.timeZoneId != null) return stored

                    // Migrate legacy GPS locations to a named zone when possible; otherwise use the device zone.
                    val matchingCity = popularCities.firstOrNull {
                        it.nameEn.equals(stored.nameEn, ignoreCase = true) || it.nameAr == stored.nameAr
                    }
                    val migrated = stored.copy(
                        timeZoneOffsetHours = matchingCity?.timeZoneOffsetHours ?: stored.timeZoneOffsetHours,
                        timeZoneId = matchingCity?.timeZoneId ?: TimeZone.getDefault().id
                    )
                    privatePrefs.edit().also {
                        writeStoredCity(it, CUSTOM_CITY_PREFIX, migrated)
                    }.apply()
                    return migrated
                }
            }
            // A cloud-synced city name changed, or local coordinates are incomplete; discard stale data.
            val editor = privatePrefs.edit().putBoolean(KEY_CUSTOM_CITY_ENABLED, false)
            clearStoredCity(editor, CUSTOM_CITY_PREFIX)
            editor.apply()
        }

        popularCities.find { it.nameAr == savedName || it.nameEn.equals(savedName, ignoreCase = true) }?.let {
            return it
        }

        // On another device, precise coordinates are intentionally absent; use the synced nearest-city index.
        val fallbackIndex = cityPrefs.getInt(KEY_SYNCED_CITY_INDEX, 0).coerceIn(0, popularCities.lastIndex)
        return popularCities[fallbackIndex]
    }

    /**
     * Saves the selected city in the shared app/prayer preferences.
     * For a GPS-derived city, detailed coordinates remain device-local. The returned flag tells
     * callers whether prayer alarms need a refresh relative to the last scheduled location.
     */
    fun saveCity(context: Context, city: CityLocation): Boolean {
        val cityPrefs = context.getSharedPreferences(CITY_PREFS_NAME, Context.MODE_PRIVATE)
        val prayerPrefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        val privatePrefs = context.getSharedPreferences(PRIVATE_LOCATION_PREFS_NAME, Context.MODE_PRIVATE)

        val lastScheduledCity = readStoredCity(privatePrefs, LAST_SCHEDULED_CITY_PREFIX)
        val shouldReschedule = lastScheduledCity == null || isMateriallyDifferent(lastScheduledCity, city)
        val knownCityIndex = popularCities.indexOf(city)
        val syncedCityIndex = if (knownCityIndex >= 0) knownCityIndex else nearestPopularCityIndex(city)

        if (cityPrefs.getString(KEY_SELECTED_CITY_NAME, null) != city.nameAr ||
            cityPrefs.getInt(KEY_SYNCED_CITY_INDEX, -1) != syncedCityIndex
        ) {
            cityPrefs.edit()
                .putString(KEY_SELECTED_CITY_NAME, city.nameAr)
                .putInt(KEY_SYNCED_CITY_INDEX, syncedCityIndex)
                .apply()
        }
        if (prayerPrefs.getInt(KEY_LOCAL_CITY_INDEX, -1) != syncedCityIndex) {
            prayerPrefs.edit().putInt(KEY_LOCAL_CITY_INDEX, syncedCityIndex).apply()
        }

        val privateEditor = privatePrefs.edit()
        if (knownCityIndex >= 0) {
            privateEditor.putBoolean(KEY_CUSTOM_CITY_ENABLED, false)
            clearStoredCity(privateEditor, CUSTOM_CITY_PREFIX)
        } else {
            privateEditor.putBoolean(KEY_CUSTOM_CITY_ENABLED, true)
            writeStoredCity(privateEditor, CUSTOM_CITY_PREFIX, city)
        }
        privateEditor.apply()
        return shouldReschedule
    }

    /**
     * Persist the city and refresh alarm times only when it materially differs from the last
     * scheduled location. This prevents GPS updates from re-creating alarms every few minutes.
     */
    fun updateSelectedCity(context: Context, city: CityLocation): Boolean {
        if (!saveCity(context, city)) return false

        return runCatching {
            PrayerAlarmManager.scheduleAllPrayers(context)
            val privatePrefs = context.getSharedPreferences(PRIVATE_LOCATION_PREFS_NAME, Context.MODE_PRIVATE)
            val scheduledCity = city.copy(
                timeZoneOffsetHours = timeZoneFor(city).getOffset(System.currentTimeMillis()) / 3_600_000.0
            )
            val editor = privatePrefs.edit()
            writeStoredCity(editor, LAST_SCHEDULED_CITY_PREFIX, scheduledCity)
            editor.apply()
            true
        }.onFailure {
            Log.w(TAG, "Could not refresh prayer alarms after a city change", it)
        }.getOrDefault(false)
    }

    private fun readStoredCity(prefs: SharedPreferences, prefix: String): CityLocation? {
        val nameAr = prefs.getString("$prefix.name_ar", null)?.takeIf { it.isNotBlank() } ?: return null
        val nameEn = prefs.getString("$prefix.name_en", null)?.takeIf { it.isNotBlank() } ?: return null
        val countryAr = prefs.getString("$prefix.country_ar", null)?.takeIf { it.isNotBlank() } ?: return null
        val latitude = prefs.getString("$prefix.latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString("$prefix.longitude", null)?.toDoubleOrNull() ?: return null
        val timezoneOffset = prefs.getString("$prefix.timezone_offset", null)?.toDoubleOrNull() ?: return null
        val methodName = prefs.getString("$prefix.method", null) ?: return null
        val method = runCatching { CalculationMethod.valueOf(methodName) }.getOrNull() ?: return null
        val timeZoneId = prefs.getString("$prefix.timezone_id", null)?.takeIf { it.isNotBlank() }

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || timezoneOffset !in -14.0..14.0) {
            return null
        }

        return CityLocation(nameAr, nameEn, countryAr, latitude, longitude, timezoneOffset, method, timeZoneId)
    }

    private fun writeStoredCity(editor: SharedPreferences.Editor, prefix: String, city: CityLocation) {
        editor.putString("$prefix.name_ar", city.nameAr)
            .putString("$prefix.name_en", city.nameEn)
            .putString("$prefix.country_ar", city.countryAr)
            .putString("$prefix.latitude", city.latitude.toString())
            .putString("$prefix.longitude", city.longitude.toString())
            .putString("$prefix.timezone_offset", city.timeZoneOffsetHours.toString())
            .putString("$prefix.timezone_id", city.timeZoneId)
            .putString("$prefix.method", city.defaultMethod.name)
    }

    private fun clearStoredCity(editor: SharedPreferences.Editor, prefix: String) {
        listOf("name_ar", "name_en", "country_ar", "latitude", "longitude", "timezone_offset", "timezone_id", "method")
            .forEach { editor.remove("$prefix.$it") }
    }

    private fun nearestPopularCityIndex(city: CityLocation): Int =
        popularCities.indices.minByOrNull { distanceKm(city, popularCities[it]) } ?: 0

    private fun isMateriallyDifferent(reference: CityLocation, candidate: CityLocation): Boolean {
        if (reference.nameAr != candidate.nameAr ||
            reference.countryAr != candidate.countryAr ||
            reference.defaultMethod != candidate.defaultMethod ||
            reference.timeZoneId != candidate.timeZoneId
        ) return true

        val candidateOffsetHours = timeZoneFor(candidate).getOffset(System.currentTimeMillis()) / 3_600_000.0
        if (abs(reference.timeZoneOffsetHours - candidateOffsetHours) >= RESCHEDULE_TIMEZONE_DELTA_HOURS) {
            return true
        }
        return distanceKm(reference, candidate) >= RESCHEDULE_DISTANCE_KM
    }

    private fun distanceKm(first: CityLocation, second: CityLocation): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val deltaLatitude = Math.toRadians(second.latitude - first.latitude)
        val deltaLongitude = Math.toRadians(second.longitude - first.longitude)
        val haversine = sin(deltaLatitude / 2).pow(2) +
            cos(firstLatitude) * cos(secondLatitude) * sin(deltaLongitude / 2).pow(2)
        val centralAngle = 2 * atan2(
            sqrt(haversine.coerceIn(0.0, 1.0)),
            sqrt((1.0 - haversine).coerceIn(0.0, 1.0))
        )
        return 6_371.0 * centralAngle
    }

    private const val KAABA_LAT = 21.422487
    private const val KAABA_LON = 39.826206

    /**
     * Calculate Qibla angle from North in degrees (0..360) and distance to Makkah
     */
    fun calculateQibla(latitude: Double, longitude: Double): Pair<Float, Int> {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(KAABA_LAT)
        val lon2 = Math.toRadians(KAABA_LON)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var qiblaAngle = Math.toDegrees(atan2(y, x))
        if (qiblaAngle < 0) qiblaAngle += 360.0

        // Distance in KM using Haversine formula
        val r = 6371.0 // Radius of earth in km
        val dLat = lat2 - lat1
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = (r * c).roundToInt()

        return Pair(qiblaAngle.toFloat(), distance)
    }

    private data class TomorrowFajrCacheKey(
        val year: Int,
        val month: Int,
        val day: Int,
        val latitude: Double,
        val longitude: Double,
        val timeZoneId: String?,
        val timeZoneOffsetHours: Double,
        val method: CalculationMethod
    )

    private data class PrayerEvent(
        val name: String,
        val formattedTime: String,
        val millis: Long
    )

    private fun localEventMillis(dayStart: Calendar, decimalHour: Double, extraDayOffset: Int = 0): Long {
        val totalMinutes = (decimalHour * 60.0).roundToInt()
        val dayOffset = Math.floorDiv(totalMinutes, 1440) + extraDayOffset
        val minuteOfDay = Math.floorMod(totalMinutes, 1440)

        return (dayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun tomorrowFajrFor(
        cityCalendar: Calendar,
        location: CityLocation,
        method: CalculationMethod
    ): String {
        val key = TomorrowFajrCacheKey(
            year = cityCalendar.get(Calendar.YEAR),
            month = cityCalendar.get(Calendar.MONTH) + 1,
            day = cityCalendar.get(Calendar.DAY_OF_MONTH),
            latitude = location.latitude,
            longitude = location.longitude,
            timeZoneId = location.timeZoneId,
            timeZoneOffsetHours = location.timeZoneOffsetHours,
            method = method
        )
        synchronized(tomorrowFajrCache) {
            tomorrowFajrCache[key]?.let { return it }
        }

        val tomorrowCalendar = (cityCalendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val fajr = calculatePrayerTimes(
            tomorrowCalendar,
            location,
            method,
            includeNextDayFajr = false
        ).fajr

        synchronized(tomorrowFajrCache) {
            if (tomorrowFajrCache.size >= 64) tomorrowFajrCache.clear()
            tomorrowFajrCache[key] = fajr
        }
        return fajr
    }

    /**
     * Main calculation function using standard Solar Ephemeris math
     */
    fun calculatePrayerTimes(
        calendar: Calendar = Calendar.getInstance(),
        location: CityLocation,
        method: CalculationMethod = location.defaultMethod,
        includeNextDayFajr: Boolean = true
    ): CalculatedPrayerTimes {
        val cityTimeZone = timeZoneFor(location)
        val cityCalendar = Calendar.getInstance(cityTimeZone).apply {
            timeInMillis = calendar.timeInMillis
        }
        val year = cityCalendar.get(Calendar.YEAR)
        val month = cityCalendar.get(Calendar.MONTH) + 1
        val day = cityCalendar.get(Calendar.DAY_OF_MONTH)
        val utcDayStartMillis = Calendar.getInstance(utcTimeZone).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun localHourForUtcMillis(utcMillis: Long): Double {
            val localWallClockMillis = utcMillis + cityTimeZone.getOffset(utcMillis).toLong()
            return (localWallClockMillis - utcDayStartMillis).toDouble() / 3_600_000.0
        }

        fun localHourForUtcHour(utcHour: Double): Double {
            val utcMillis = utcDayStartMillis + (utcHour * 3_600_000.0).roundToLong()
            return localHourForUtcMillis(utcMillis)
        }

        // Julian Day
        val jd = julianDay(year, month, day) - location.longitude / (15.0 * 24.0)

        // Sun calculations
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g)))

        val e = 23.439 - 0.00000036 * d
        val declination = Math.toDegrees(asin(sin(Math.toRadians(e)) * sin(Math.toRadians(l))))
        val ra = Math.toDegrees(atan2(cos(Math.toRadians(e)) * sin(Math.toRadians(l)), cos(Math.toRadians(l)))) / 15.0
        val rightAscension = fixHour(ra)

        // Equation of Time
        val eqT = q / 15.0 - rightAscension

        // Solar noon in UTC; each event is converted with the city's offset at its actual instant.
        val utcNoonBase = 12.0 - location.longitude / 15.0 - eqT
        val dhuhrBase = localHourForUtcHour(utcNoonBase)

        // Sun altitude helpers
        fun sunAngleUtcTime(angle: Double, isMorning: Boolean): Double {
            val sinVal = (-sin(Math.toRadians(angle)) - sin(Math.toRadians(location.latitude)) * sin(Math.toRadians(declination))) /
                    (cos(Math.toRadians(location.latitude)) * cos(Math.toRadians(declination)))
            if (sinVal < -1.0 || sinVal > 1.0) return utcNoonBase // Polar fallback
            val diff = Math.toDegrees(acos(sinVal)) / 15.0
            return if (isMorning) utcNoonBase - diff else utcNoonBase + diff
        }

        // Asr calculation (Shafi'i: shadow = 1)
        fun asrUtcTime(shadowFactor: Double = 1.0): Double {
            val angle = -Math.toDegrees(atan(1.0 / (shadowFactor + tan(Math.toRadians(abs(location.latitude - declination))))))
            val sinVal = (-sin(Math.toRadians(angle)) - sin(Math.toRadians(location.latitude)) * sin(Math.toRadians(declination))) /
                    (cos(Math.toRadians(location.latitude)) * cos(Math.toRadians(declination)))
            val clamped = sinVal.coerceIn(-1.0, 1.0)
            val diff = Math.toDegrees(acos(clamped)) / 15.0
            return utcNoonBase + diff
        }

        val sunriseUtcHour = sunAngleUtcTime(0.833, true)
        val sunsetUtcHour = sunAngleUtcTime(0.833, false)
        val fajrUtcHour = sunAngleUtcTime(method.fajrAngle, true)
        val asrUtcHour = asrUtcTime(1.0)
        val maghribUtcHour = sunsetUtcHour + (2.0 / 60.0) // 2 min buffer
        val ishaUtcHour = if (method.ishaIntervalMinutes > 0) {
            maghribUtcHour + (method.ishaIntervalMinutes / 60.0)
        } else {
            sunAngleUtcTime(method.ishaAngle, false)
        }

        val sunriseHour = localHourForUtcHour(sunriseUtcHour)
        val sunsetHour = localHourForUtcHour(sunsetUtcHour)
        val fajrHour = localHourForUtcHour(fajrUtcHour)
        val asrHour = localHourForUtcHour(asrUtcHour)
        val maghribHour = localHourForUtcHour(maghribUtcHour)
        val ishaHour = localHourForUtcHour(ishaUtcHour)

        // Night calculations use elapsed UTC duration, then convert the midpoint and last-third instant to local clock time.
        val sunsetUtcMillis = utcDayStartMillis + (sunsetUtcHour * 3_600_000.0).roundToLong()
        val nextFajrUtcMillis = utcDayStartMillis + ((fajrUtcHour + 24.0) * 3_600_000.0).roundToLong()
        val nightDurationMillis = (nextFajrUtcMillis - sunsetUtcMillis).coerceAtLeast(30 * 60 * 1000L)
        val midnightHour = localHourForUtcMillis(sunsetUtcMillis + nightDurationMillis / 2)
        val qiyamHour = localHourForUtcMillis(sunsetUtcMillis + (nightDurationMillis * 2 / 3))

        val fajrStr = formatHourMinutes(fajrHour)
        val sunriseStr = formatHourMinutes(sunriseHour)
        val dhuhrStr = formatHourMinutes(dhuhrBase)
        val asrStr = formatHourMinutes(asrHour)
        val maghribStr = formatHourMinutes(maghribHour)
        val ishaStr = formatHourMinutes(ishaHour)
        val midnightStr = formatHourMinutes(midnightHour)
        val qiyamStr = formatHourMinutes(qiyamHour)

        // Calculate the next event using absolute instants in the selected city's time zone.
        val nowMillis = cityCalendar.timeInMillis
        val dayStart = (cityCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val prayerSchedule = listOf(
            Triple("الفجر", fajrHour, fajrStr),
            Triple("الشروق", sunriseHour, sunriseStr),
            Triple("الظهر", dhuhrBase, dhuhrStr),
            Triple("العصر", asrHour, asrStr),
            Triple("المغرب", maghribHour, maghribStr),
            Triple("العشاء", ishaHour, ishaStr)
        ).map { (name, hour, time) ->
            PrayerEvent(name, time, localEventMillis(dayStart, hour))
        }.sortedBy { it.millis }

        val nextIndex = prayerSchedule.indexOfFirst { it.millis > nowMillis }
        val tomorrowFajrTime = if (nextIndex < 0 && includeNextDayFajr) {
            tomorrowFajrFor(cityCalendar, location, method)
        } else {
            fajrStr
        }
        val nextEventMillis = if (nextIndex >= 0) {
            prayerSchedule[nextIndex].millis
        } else if (includeNextDayFajr) {
            calendarForPrayerTime(tomorrowFajrTime, nowMillis, location, dayOffset = 1).timeInMillis
        } else {
            localEventMillis(dayStart, fajrHour, extraDayOffset = 1)
        }
        val nextName = if (nextIndex >= 0) prayerSchedule[nextIndex].name else "الفجر"
        val nextTimeStr = if (nextIndex >= 0) prayerSchedule[nextIndex].formattedTime else tomorrowFajrTime
        val previousEventMillis = when {
            nextIndex > 0 -> prayerSchedule[nextIndex - 1].millis
            nextIndex == 0 -> localEventMillis(dayStart, ishaHour, extraDayOffset = -1)
            else -> prayerSchedule.last().millis
        }

        val millisUntilNext = (nextEventMillis - nowMillis).coerceAtLeast(0L)
        val totalIntervalMillis = (nextEventMillis - previousEventMillis).coerceAtLeast(30 * 60 * 1000L)
        val progress = ((totalIntervalMillis - millisUntilNext).toDouble() / totalIntervalMillis.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)

        val (qiblaDeg, distKm) = calculateQibla(location.latitude, location.longitude)

        return CalculatedPrayerTimes(
            fajr = fajrStr,
            sunrise = sunriseStr,
            dhuhr = dhuhrStr,
            asr = asrStr,
            maghrib = maghribStr,
            isha = ishaStr,
            midnight = midnightStr,
            qiyamStart = qiyamStr,
            nextPrayerName = nextName,
            nextPrayerTimeFormatted = nextTimeStr,
            millisUntilNextPrayer = millisUntilNext,
            progressToNextPrayer = progress,
            qiblaDirectionDeg = qiblaDeg,
            distanceToMakkahKm = distKm
        )
    }

    fun formatCountdown(millis: Long): String {
        val totalSecs = max(0, millis / 1000)
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatHourMinutes(decimalHour: Double): String {
        val normalized = (decimalHour % 24.0 + 24.0) % 24.0
        val h = normalized.toInt()
        val m = ((normalized - h) * 60.0).roundToInt()
        val finalH = if (m == 60) (h + 1) % 24 else h
        val finalM = if (m == 60) 0 else m
        return String.format(Locale.US, "%02d:%02d", finalH, finalM)
    }

    private fun fixAngle(a: Double): Double = (a % 360.0 + 360.0) % 360.0
    private fun fixHour(h: Double): Double = (h % 24.0 + 24.0) % 24.0

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }
}
