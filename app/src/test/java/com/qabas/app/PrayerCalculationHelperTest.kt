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
            defaultMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE
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