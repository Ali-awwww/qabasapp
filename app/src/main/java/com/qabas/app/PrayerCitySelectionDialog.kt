package com.qabas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.theme.*

/**
 * نافذة تفاعلية نظيفة وفق معايير Apple HIG لاختيار الدولة والمدينة لمواقيت الصلاة والقبلة
 */
@Composable
fun PrayerCitySelectionDialog(
    currentSelectedCity: CityLocation,
    onDismiss: () -> Unit,
    onCitySelected: (CityLocation) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryFilter by remember { mutableStateOf("الكل") }

    val allCountries = remember(currentSelectedCity.countryAr) {
        listOf("الكل") + (PrayerCalculationHelper.popularCities.map { it.countryAr } + currentSelectedCity.countryAr)
            .distinct()
            .sorted()
    }

    val filteredCities = remember(searchQuery, selectedCountryFilter) {
        PrayerCalculationHelper.popularCities.filter { city ->
            val matchesSearch = searchQuery.isBlank() ||
                city.nameAr.contains(searchQuery.trim(), ignoreCase = true) ||
                city.nameEn.contains(searchQuery.trim(), ignoreCase = true) ||
                city.countryAr.contains(searchQuery.trim(), ignoreCase = true)

            val matchesCountry = selectedCountryFilter == "الكل" || city.countryAr == selectedCountryFilter
            matchesSearch && matchesCountry
        }
    }

    val isCurrentCityCustom = PrayerCalculationHelper.popularCities.none { it == currentSelectedCity }
    val currentCityMatchesSearch = searchQuery.isBlank() ||
        currentSelectedCity.nameAr.contains(searchQuery.trim(), ignoreCase = true) ||
        currentSelectedCity.nameEn.contains(searchQuery.trim(), ignoreCase = true) ||
        currentSelectedCity.countryAr.contains(searchQuery.trim(), ignoreCase = true)
    val currentCityMatchesCountry = selectedCountryFilter == "الكل" ||
        currentSelectedCity.countryAr == selectedCountryFilter
    val showCurrentLocation = isCurrentCityCustom && currentCityMatchesSearch && currentCityMatchesCountry

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
                .fillMaxHeight(0.84f),
            shape = RoundedCornerShape(22.dp),
            color = theme.background,
            shadowElevation = if (theme.isDark) 0.dp else 12.dp,
            border = BorderStroke(0.75.dp, theme.separator.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = theme.cardSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "اختر مدينتك لمواقيت الصلاة",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp
                                    )
                                    Text(
                                        "تحديد دقيق لمواقيت الأذان وبوصلة القبلة",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = theme.textSecondary
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))
                    }
                }

                // Search Bar
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "ابحث عن مدينتك أو دولتك..."
                    )
                }

                // Country Filter Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(allCountries) { country ->
                        val isSelected = selectedCountryFilter == country
                        Surface(
                            onClick = { selectedCountryFilter = country },
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) theme.primary else theme.cardSurface,
                            border = BorderStroke(
                                0.75.dp,
                                if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = country,
                                    color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Cities List
                if (filteredCities.isEmpty() && !showCurrentLocation) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "لم يتم العثور على نتائج",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showCurrentLocation) {
                            item(key = "current-location") {
                                CitySelectionRowCard(
                                    title = "الموقع الحالي (${currentSelectedCity.nameAr})",
                                    subtitle = currentSelectedCity.countryAr,
                                    isSelected = true,
                                    badgeText = "مختارة",
                                    onClick = {
                                        PrayerCalculationHelper.updateSelectedCity(context, currentSelectedCity)
                                        onCitySelected(currentSelectedCity)
                                    }
                                )
                            }
                        }
                        items(filteredCities, key = { it.nameAr + it.countryAr }) { city ->
                            val isCurrent = city == currentSelectedCity
                            val (_, distanceKm) = remember(city) {
                                PrayerCalculationHelper.calculateQibla(city.latitude, city.longitude)
                            }
                            CitySelectionRowCard(
                                title = city.nameAr,
                                secondaryTitle = "(${city.nameEn})",
                                subtitle = "${city.countryAr} • المسافة للكعبة: $distanceKm كم",
                                isSelected = isCurrent,
                                badgeText = if (isCurrent) "مختارة" else null,
                                onClick = {
                                    PrayerCalculationHelper.updateSelectedCity(context, city)
                                    onCitySelected(city)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CitySelectionRowCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    secondaryTitle: String? = null,
    badgeText: String? = null
) {
    val theme = QabasTheme.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) theme.primary.copy(alpha = if (theme.isDark) 0.16f else 0.08f) else theme.cardSurface,
        border = BorderStroke(
            width = if (isSelected) 1.2.dp else 0.5.dp,
            color = if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) theme.primary else theme.secondarySurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationCity,
                        contentDescription = null,
                        tint = if (isSelected) theme.onPrimary else theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = if (isSelected) theme.primary else theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (!secondaryTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = secondaryTitle,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
            }

            if (!badgeText.isNullOrBlank()) {
                Surface(
                    color = theme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = theme.onPrimary,
                        fontSize = 10.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
