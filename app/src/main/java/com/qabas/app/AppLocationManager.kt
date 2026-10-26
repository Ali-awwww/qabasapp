package com.qabas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

/**
 * 🛰️ مدير الموقع الجغرافي والربط الذكي بمواقيت الصلاة والقبلة
 */
object AppLocationManager {

    private const val TAG = "AppLocationManager"
    private const val PREFS_NAME = "qabas_prayer_prefs"

    private val _currentLocationState = MutableStateFlow<LocationResult>(LocationResult.Idle)
    val currentLocationState: StateFlow<LocationResult> = _currentLocationState.asStateFlow()

    sealed class LocationResult {
        object Idle : LocationResult()
        object Loading : LocationResult()
        data class Success(val city: CityLocation, val isAutoDetected: Boolean = true) : LocationResult()
        data class PermissionDenied(val message: String) : LocationResult()
        data class Error(val message: String) : LocationResult()
    }

    /**
     * فحص صلاحيات الموقع الجغرافي
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * تحديد الموقع الجغرافي تلقائياً وتحديث المدينة والقبلة والمواقيت
     */
    suspend fun detectCurrentLocation(
        context: Context,
        forceRefresh: Boolean = false
    ): CityLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            _currentLocationState.value = LocationResult.PermissionDenied("صلاحية الوصول للموقع الجغرافي غير ممنوحة")
            return@withContext null
        }

        _currentLocationState.value = LocationResult.Loading

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            _currentLocationState.value = LocationResult.Error("خدمات الموقع الجغرافي غير متوفرة على هذا الجهاز")
            return@withContext null
        }

        var location: Location? = null

        // 1. فحص آخر موقع معروف من جميع المزودات المتاحة
        runCatching {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (provider in providers) {
                if (runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)) {
                    val lastKnown = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                    if (lastKnown != null) {
                        if (location == null || lastKnown.time > location!!.time) {
                            location = lastKnown
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Failed to get last known location: ${it.message}") }

        // 2. إذا كان الموقع الم المأخوذ قديماً جداً أو غير موجود، نطلب تحديثاً سريعاً
        if (location == null || forceRefresh) {
            val freshLocation = getSingleLocationUpdate(context, locationManager)
            if (freshLocation != null) {
                location = freshLocation
            }
        }

        val finalLoc = location
        if (finalLoc == null) {
            _currentLocationState.value = LocationResult.Error("تعذر الحصول على الموقع الجغرافي عبر GPS أو الشبكة")
            return@withContext null
        }

        // 3. مطابقة الإحداثيات وتحويلها إلى مدينة
        val detectedCity = resolveCityFromCoordinates(context, finalLoc.latitude, finalLoc.longitude)

        // 4. حفظ وتعميم المدينة في النظام
        PrayerCalculationHelper.saveCity(context, detectedCity)
        saveAutoDetectedLocationPrefs(context, detectedCity, finalLoc.latitude, finalLoc.longitude)

        _currentLocationState.value = LocationResult.Success(detectedCity, isAutoDetected = true)
        return@withContext detectedCity
    }

    /**
     * طلب تحديث آمن وسريع للموقع
     */
    private suspend fun getSingleLocationUpdate(
        context: Context,
        locationManager: LocationManager
    ): Location? = withContext(Dispatchers.IO) {
        kotlin.coroutines.suspendCoroutine { cont ->
            var resumed = false
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (!resumed) {
                        resumed = true
                        runCatching { locationManager.removeUpdates(this) }
                        cont.resumeWith(Result.success(loc))
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
                    // إلغاء الانتظار بعد 5 ثوانٍ لتجنب تجميد الواجهة
                    android.os.Handler(context.mainLooper).postDelayed({
                        if (!resumed) {
                            resumed = true
                            runCatching { locationManager.removeUpdates(listener) }
                            cont.resumeWith(Result.success(null))
                        }
                    }, 5000L)
                } else {
                    cont.resumeWith(Result.success(null))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting location update: ${e.message}")
                if (!resumed) {
                    resumed = true
                    cont.resumeWith(Result.success(null))
                }
            }
        }
    }

    /**
     * مطابقة الإحداثيات مع المدن المعتمدة أو تعيين اسم المدينة عبر Geocoder
     */
    fun resolveCityFromCoordinates(
        context: Context,
        lat: Double,
        lng: Double
    ): CityLocation {
        // أقرب مدينة بالمسافة
        var closestCity = PrayerCalculationHelper.popularCities[0]
        var minDistanceKm = Double.MAX_VALUE

        for (city in PrayerCalculationHelper.popularCities) {
            val d = distanceBetween(lat, lng, city.latitude, city.longitude)
            if (d < minDistanceKm) {
                minDistanceKm = d
                closestCity = city
            }
        }

        // محاولة الحصول على اسم المدينة الحقيقي من النظام
        var geocodedNameAr: String? = null
        var geocodedCountryAr: String? = null

        runCatching {
            val geocoder = Geocoder(context, Locale("ar"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        geocodedNameAr = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                        geocodedCountryAr = addr.countryName
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    geocodedNameAr = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    geocodedCountryAr = addr.countryName
                }
            }
        }.onFailure { Log.w(TAG, "Geocoder lookup failed: ${it.message}") }

        val timeZoneOffsetHours = (TimeZone.getDefault().rawOffset / (1000.0 * 60.0 * 60.0))
        val resolvedName = geocodedNameAr?.takeIf { it.isNotBlank() } ?: closestCity.nameAr
        val resolvedCountry = geocodedCountryAr?.takeIf { it.isNotBlank() } ?: closestCity.countryAr

        return if (minDistanceKm < 40.0) {
            // ضمن نطاق مدينة معروفة
            closestCity.copy(
                latitude = lat,
                longitude = lng,
                timeZoneOffsetHours = timeZoneOffsetHours
            )
        } else {
            // موقع مخصص بالمدينة الحالية
            CityLocation(
                nameAr = resolvedName,
                nameEn = resolvedName,
                countryAr = resolvedCountry,
                latitude = lat,
                longitude = lng,
                timeZoneOffsetHours = timeZoneOffsetHours,
                defaultMethod = closestCity.defaultMethod
            )
        }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun saveAutoDetectedLocationPrefs(
        context: Context,
        city: CityLocation,
        lat: Double,
        lng: Double
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_auto_location_enabled", true)
            putString("detected_city_name", city.nameAr)
            putString("detected_country_name", city.countryAr)
            putFloat("detected_lat", lat.toFloat())
            putFloat("detected_lng", lng.toFloat())
            putLong("location_last_updated_time", System.currentTimeMillis())
            apply()
        }

        val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        qabasPrefs.edit().apply {
            putString("selected_prayer_city_name", city.nameAr)
            putString("detected_user_location", "${city.nameAr}، ${city.countryAr}")
            apply()
        }
    }
}
