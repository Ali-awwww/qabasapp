package com.qabas.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*

/**
 * نافذة تفاعلية راقية لاختيار الدولة والمدينة لتحديد مواقيت الصلاة والقبلة
 */
@Composable
fun PrayerCitySelectionDialog(
    currentSelectedCity: CityLocation,
    onDismiss: () -> Unit,
    onCitySelected: (CityLocation) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryFilter by remember { mutableStateOf("الكل") }

    val allCountries = remember {
        listOf("الكل") + PrayerCalculationHelper.popularCities.map { it.countryAr }.distinct().sorted()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.2.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            color = Color(0xFF0A101D)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A263E), Color(0xFF0F172A))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GoldPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "اختر مدينتك لمواقيت الصلاة",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "تحديد دقيق لمواقيت الأذان وبوصلة القبلة 🕋",
                                    color = qabasTextSecondary(),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                }

                // Search Bar
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث عن مدينتك أو دولتك...", fontFamily = CairoFont, fontSize = 12.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GoldPrimary) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E2D4A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0D1527),
                            unfocusedContainerColor = Color(0xFF0D1527)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Country Filter Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(allCountries) { country ->
                        val isSelected = selectedCountryFilter == country
                        Surface(
                            onClick = { selectedCountryFilter = country },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else Color(0xFF141F33),
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E2D4A))
                        ) {
                            Text(
                                text = country,
                                color = if (isSelected) DeepSlate else Color.White,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // Cities List
                if (filteredCities.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocationOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لم يتم العثور على نتائج", color = Color.White, fontFamily = CairoFont, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCities, key = { it.nameAr + it.countryAr }) { city ->
                            val isCurrent = city.nameAr == currentSelectedCity.nameAr && city.countryAr == currentSelectedCity.countryAr
                            val (qiblaAngle, distanceKm) = remember(city) {
                                PrayerCalculationHelper.calculateQibla(city.latitude, city.longitude)
                            }

                            Surface(
                                onClick = {
                                    PrayerCalculationHelper.saveCity(context, city)
                                    val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
                                    val cityIdx = PrayerCalculationHelper.popularCities.indexOfFirst { it.nameAr == city.nameAr }.coerceAtLeast(0)
                                    prayerPrefs.edit().putInt("selected_city_index", cityIdx).apply()
                                    onCitySelected(city)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF0F1728),
                                border = BorderStroke(1.dp, if (isCurrent) GoldPrimary else Color(0xFF1E2D4A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isCurrent) GoldPrimary else Color(0xFF1E293B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.LocationCity,
                                                contentDescription = null,
                                                tint = if (isCurrent) DeepSlate else GoldPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = city.nameAr,
                                                    color = if (isCurrent) GoldPrimary else Color.White,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${city.nameEn})",
                                                    color = Color(0xFF64748B),
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Text(
                                                text = "${city.countryAr} • 🕋 بعد الكعبة: $distanceKm كم",
                                                color = Color(0xFF94A3B8),
                                                fontFamily = CairoFont,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    if (isCurrent) {
                                        Surface(
                                            color = GoldPrimary,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "مختارة ✓",
                                                color = DeepSlate,
                                                fontSize = 9.sp,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
