package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Calendar
import java.util.Locale
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
    val defaultMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA
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
        CityLocation("مكة المكرمة", "Makkah", "السعودية", 21.4225, 39.8262, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("المدينة المنورة", "Madinah", "السعودية", 24.4672, 39.6111, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("الرياض", "Riyadh", "السعودية", 24.7136, 46.6753, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("جدة", "Jeddah", "السعودية", 21.5433, 39.1728, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("القاهرة", "Cairo", "مصر", 30.0444, 31.2357, 2.0, CalculationMethod.EGYPTIAN),
        CityLocation("الإسكندرية", "Alexandria", "مصر", 31.2001, 29.9187, 2.0, CalculationMethod.EGYPTIAN),
        CityLocation("القدس الشريف", "Jerusalem", "فلسطين", 31.7683, 35.2137, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("غزة", "Gaza", "فلسطين", 31.5017, 34.4668, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("دمشق", "Damascus", "سوريا", 33.5138, 36.2765, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("بغداد", "Baghdad", "العراق", 33.3152, 44.3661, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("أبوظبي", "Abu Dhabi", "الإمارات", 24.4539, 54.3773, 4.0, CalculationMethod.DUBAI),
        CityLocation("دبي", "Dubai", "الإمارات", 25.2048, 55.2708, 4.0, CalculationMethod.DUBAI),
        CityLocation("الكويت", "Kuwait", "الكويت", 29.3759, 47.9774, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("الدوحة", "Doha", "قطر", 25.2854, 51.5310, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("المنامة", "Manama", "البحرين", 26.2285, 50.5860, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("مسقط", "Muscat", "عُمان", 23.5880, 58.3829, 4.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("صنعاء", "Sanaa", "اليمن", 15.3694, 44.1910, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("عدن", "Aden", "اليمن", 12.7855, 45.0187, 3.0, CalculationMethod.UMM_AL_QURA),
        CityLocation("عمّان", "Amman", "الأردن", 31.9454, 35.9284, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("بيروت", "Beirut", "لبنان", 33.8938, 35.5018, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("الرباط", "Rabat", "المغرب", 34.0209, -6.8416, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("الدار البيضاء", "Casablanca", "المغرب", 33.5731, -7.5898, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("الجزائر", "Algiers", "الجزائر", 36.7538, 3.0588, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("تونس", "Tunis", "تونس", 36.8065, 10.1815, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("طرابلس", "Tripoli", "ليبيا", 32.8872, 13.1913, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("الخرطوم", "Khartoum", "السودان", 15.5007, 32.5599, 2.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("نواكشوط", "Nouakchott", "موريتانيا", 18.0735, -15.9582, 0.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("مقديشو", "Mogadishu", "الصومال", 2.0469, 45.3182, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("إسطنبول", "Istanbul", "تركيا", 41.0082, 28.9784, 3.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("جاكرتا", "Jakarta", "إندونيسيا", -6.2088, 106.8456, 7.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("كوالالمبور", "Kuala Lumpur", "ماليزيا", 3.1390, 101.6869, 8.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("لندن", "London", "المملكة المتحدة", 51.5074, -0.1278, 0.0, CalculationMethod.ISNA),
        CityLocation("باريس", "Paris", "فرنسا", 48.8566, 2.3522, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("برلين", "Berlin", "ألمانيا", 52.5200, 13.4050, 1.0, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        CityLocation("نيويورك", "New York", "الولايات المتحدة", 40.7128, -74.0060, -5.0, CalculationMethod.ISNA),
        CityLocation("تورونتو", "Toronto", "كندا", 43.6532, -79.3832, -5.0, CalculationMethod.ISNA),
        CityLocation("سيدني", "Sydney", "أستراليا", -33.8688, 151.2093, 10.0, CalculationMethod.MUSLIM_WORLD_LEAGUE)
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

    fun getSavedCity(context: Context): CityLocation {
        val cityPrefs = context.getSharedPreferences(CITY_PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = cityPrefs.getString(KEY_SELECTED_CITY_NAME, "مكة المكرمة") ?: "مكة المكرمة"
        val privatePrefs = context.getSharedPreferences(PRIVATE_LOCATION_PREFS_NAME, Context.MODE_PRIVATE)

        if (privatePrefs.getBoolean(KEY_CUSTOM_CITY_ENABLED, false)) {
            val localCustomName = privatePrefs.getString("$CUSTOM_CITY_PREFIX.name_ar", null)
            if (localCustomName == savedName) {
                readStoredCity(privatePrefs, CUSTOM_CITY_PREFIX)?.let { return it }
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
            val editor = privatePrefs.edit()
            writeStoredCity(editor, LAST_SCHEDULED_CITY_PREFIX, city)
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

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || timezoneOffset !in -14.0..14.0) {
            return null
        }

        return CityLocation(nameAr, nameEn, countryAr, latitude, longitude, timezoneOffset, method)
    }

    private fun writeStoredCity(editor: SharedPreferences.Editor, prefix: String, city: CityLocation) {
        editor.putString("$prefix.name_ar", city.nameAr)
            .putString("$prefix.name_en", city.nameEn)
            .putString("$prefix.country_ar", city.countryAr)
            .putString("$prefix.latitude", city.latitude.toString())
            .putString("$prefix.longitude", city.longitude.toString())
            .putString("$prefix.timezone_offset", city.timeZoneOffsetHours.toString())
            .putString("$prefix.method", city.defaultMethod.name)
    }

    private fun clearStoredCity(editor: SharedPreferences.Editor, prefix: String) {
        listOf("name_ar", "name_en", "country_ar", "latitude", "longitude", "timezone_offset", "method")
            .forEach { editor.remove("$prefix.$it") }
    }

    private fun nearestPopularCityIndex(city: CityLocation): Int =
        popularCities.indices.minByOrNull { distanceKm(city, popularCities[it]) } ?: 0

    private fun isMateriallyDifferent(reference: CityLocation, candidate: CityLocation): Boolean {
        if (reference.nameAr != candidate.nameAr ||
            reference.countryAr != candidate.countryAr ||
            reference.defaultMethod != candidate.defaultMethod
        ) return true

        if (abs(reference.timeZoneOffsetHours - candidate.timeZoneOffsetHours) >= RESCHEDULE_TIMEZONE_DELTA_HOURS) {
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

    /**
     * Main calculation function using standard Solar Ephemeris math
     */
    fun calculatePrayerTimes(
        calendar: Calendar = Calendar.getInstance(),
        location: CityLocation,
        method: CalculationMethod = location.defaultMethod
    ): CalculatedPrayerTimes {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

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

        // Solar Noon (Dhuhr)
        val dhuhrBase = 12.0 + location.timeZoneOffsetHours - location.longitude / 15.0 - eqT

        // Sun altitude helpers
        fun sunAngleTime(angle: Double, isMorning: Boolean): Double {
            val sinVal = (-sin(Math.toRadians(angle)) - sin(Math.toRadians(location.latitude)) * sin(Math.toRadians(declination))) /
                    (cos(Math.toRadians(location.latitude)) * cos(Math.toRadians(declination)))
            if (sinVal < -1.0 || sinVal > 1.0) return dhuhrBase // Polar fallback
            val diff = Math.toDegrees(acos(sinVal)) / 15.0
            return if (isMorning) dhuhrBase - diff else dhuhrBase + diff
        }

        // Asr calculation (Shafi'i: shadow = 1)
        fun asrTime(shadowFactor: Double = 1.0): Double {
            val angle = -Math.toDegrees(atan(1.0 / (shadowFactor + tan(Math.toRadians(abs(location.latitude - declination))))))
            val sinVal = (-sin(Math.toRadians(angle)) - sin(Math.toRadians(location.latitude)) * sin(Math.toRadians(declination))) /
                    (cos(Math.toRadians(location.latitude)) * cos(Math.toRadians(declination)))
            val clamped = sinVal.coerceIn(-1.0, 1.0)
            val diff = Math.toDegrees(acos(clamped)) / 15.0
            return dhuhrBase + diff
        }

        val sunriseHour = sunAngleTime(0.833, true)
        val sunsetHour = sunAngleTime(0.833, false)
        val fajrHour = sunAngleTime(method.fajrAngle, true)
        val asrHour = asrTime(1.0)
        val maghribHour = sunsetHour + (2.0 / 60.0) // 2 min buffer

        val ishaHour = if (method.ishaIntervalMinutes > 0) {
            maghribHour + (method.ishaIntervalMinutes / 60.0)
        } else {
            sunAngleTime(method.ishaAngle, false)
        }

        // Night calculations (Midnight & Last Third)
        val nextFajrHour = fajrHour + 24.0
        val nightDuration = nextFajrHour - sunsetHour
        val midnightHour = sunsetHour + (nightDuration / 2.0)
        val qiyamHour = sunsetHour + (nightDuration * (2.0 / 3.0))

        val fajrStr = formatHourMinutes(fajrHour)
        val sunriseStr = formatHourMinutes(sunriseHour)
        val dhuhrStr = formatHourMinutes(dhuhrBase)
        val asrStr = formatHourMinutes(asrHour)
        val maghribStr = formatHourMinutes(maghribHour)
        val ishaStr = formatHourMinutes(ishaHour)
        val midnightStr = formatHourMinutes(midnightHour % 24.0)
        val qiyamStr = formatHourMinutes(qiyamHour % 24.0)

        // Calculate Next Prayer & Countdown
        val nowHours = calendar.get(Calendar.HOUR_OF_DAY) + (calendar.get(Calendar.MINUTE) / 60.0) + (calendar.get(Calendar.SECOND) / 3600.0)

        val prayerSchedule = listOf(
            Triple("الفجر", fajrHour, fajrStr),
            Triple("الشروق", sunriseHour, sunriseStr),
            Triple("الظهر", dhuhrBase, dhuhrStr),
            Triple("العصر", asrHour, asrStr),
            Triple("المغرب", maghribHour, maghribStr),
            Triple("العشاء", ishaHour, ishaStr)
        )

        var nextName = "الفجر"
        var nextTimeStr = fajrStr
        var nextTargetHours = fajrHour + 24.0
        var prevTargetHours = ishaHour

        for (i in prayerSchedule.indices) {
            val (_, h, s) = prayerSchedule[i]
            if (nowHours < h) {
                nextName = prayerSchedule[i].first
                nextTimeStr = s
                nextTargetHours = h
                prevTargetHours = if (i > 0) prayerSchedule[i - 1].second else ishaHour - 24.0
                break
            }
        }

        val diffHours = if (nextTargetHours >= nowHours) nextTargetHours - nowHours else (nextTargetHours + 24.0) - nowHours
        val millisUntilNext = (diffHours * 3600.0 * 1000.0).toLong()

        val totalIntervalHours = max(0.5, nextTargetHours - prevTargetHours)
        val progress = ((totalIntervalHours - diffHours) / totalIntervalHours).toFloat().coerceIn(0f, 1f)

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
