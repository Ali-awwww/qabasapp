package com.qabas.app

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * Detects the current city for prayer-time calculations.
 * Coordinates are processed and stored locally; they are not uploaded.
 */
object PrayerLocationProvider {

    suspend fun detectCurrentCity(context: Context, hasFinePermission: Boolean): CityLocation? =
        withContext(Dispatchers.IO) {
            val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val cancellation = CancellationTokenSource()
            val priority = if (hasFinePermission) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY

            val location: Location = try {
                withTimeoutOrNull(25_000L) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        client.getCurrentLocation(priority, cancellation.token)
                            .addOnSuccessListener { result ->
                                if (continuation.isActive) continuation.resume(result)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                            .addOnCanceledListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        continuation.invokeOnCancellation { cancellation.cancel() }
                    }
                }
            } catch (_: SecurityException) {
                null
            } finally {
                cancellation.cancel()
            } ?: return@withContext null

            if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
                return@withContext null
            }

            val address = runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("ar")).getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
            }.getOrNull()

            val cityName = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "موقعي الحالي"
            val countryName = address?.countryName ?: address?.countryCode ?: "غير معروف"
            val method = when (address?.countryCode?.uppercase(Locale.ROOT)) {
                "SA" -> CalculationMethod.UMM_AL_QURA
                "EG" -> CalculationMethod.EGYPTIAN
                "US", "CA" -> CalculationMethod.ISNA
                "PK", "AF" -> CalculationMethod.KARACHI
                "AE" -> CalculationMethod.DUBAI
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
            val geocodedCityNameEn = address?.locality ?: address?.subAdminArea ?: "Current location"
            val matchingPopularCity = PrayerCalculationHelper.popularCities.firstOrNull {
                it.nameEn.equals(geocodedCityNameEn, ignoreCase = true) || it.nameAr == cityName
            }
            val cityTimeZone = matchingPopularCity?.let { PrayerCalculationHelper.timeZoneFor(it) }
                ?: TimeZone.getDefault()
            val offset = cityTimeZone.getOffset(System.currentTimeMillis()) / 3_600_000.0

            CityLocation(
                nameAr = cityName,
                nameEn = geocodedCityNameEn,
                countryAr = countryName,
                latitude = location.latitude,
                longitude = location.longitude,
                timeZoneOffsetHours = offset,
                defaultMethod = method,
                timeZoneId = cityTimeZone.id
            )
        }
}
