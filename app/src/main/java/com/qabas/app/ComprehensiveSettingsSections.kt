package com.qabas.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1. قسم تخصيص القراءة والمصحف الشريف وصحيح الحديث
 */
@Composable
fun ReadingCustomizationSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val quranFontSize by UserPreferencesManager.quranFontSize.collectAsState()
    val readingFontFamily by UserPreferencesManager.readingFontFamily.collectAsState()
    val readingTheme by UserPreferencesManager.readingTheme.collectAsState()
    val showTashkeel by UserPreferencesManager.showTashkeel.collectAsState()

    val currentPalette = remember(readingTheme) {
        UserPreferencesManager.getResolvedReadingThemePalette(readingTheme)
    }
    val currentFont = remember(readingFontFamily) {
        UserPreferencesManager.getResolvedFontFamily(readingFontFamily)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Real-Time Preview Card (Preserves exact Mushaf swatch contrast)
        Surface(
            color = currentPalette.cardBackground,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, currentPalette.accentColor.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "معاينة حية للنص القرآني والحديث",
                        color = currentPalette.accentColor,
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${quranFontSize.toInt()} pt • ${currentPalette.name}",
                        color = currentPalette.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp
                    )
                }

                val sampleTextWithTashkeel = "﴿ إِنَّ هَٰذَا الْقُرْآنَ يَهْدِي لِلَّتِي هِيَ أَقْوَمُ وَيُبَشِّرُ الْمُؤْمِنِينَ ﴾\n«خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ»"
                val sampleTextWithoutTashkeel = "﴿ إن هذا القرآن يهدي للتي هي أقوم ويبشر المؤمنين ﴾\n«خيركم من تعلم القرآن وعلمه»"

                Text(
                    text = if (showTashkeel) sampleTextWithTashkeel else sampleTextWithoutTashkeel,
                    color = currentPalette.textColor,
                    fontFamily = currentFont,
                    fontSize = quranFontSize.sp,
                    lineHeight = (quranFontSize * 1.55f).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }

        // Font Size Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حجم خط النصوص والآيات", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
                Text("${quranFontSize.toInt()} نقطة", color = theme.primary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = quranFontSize,
                onValueChange = { UserPreferencesManager.setQuranFontSize(context, it) },
                valueRange = 14f..30f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = theme.primary,
                    activeTrackColor = theme.primary,
                    inactiveTrackColor = theme.secondarySurface
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("أصغر (14)", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = CairoFont)
                Text("متوسط (20)", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = CairoFont)
                Text("كبير جداً (30)", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = CairoFont)
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        // Font Family Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("نوع الخط المفضل للقراءة", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "amiri" to "الأميري",
                    "cairo" to "كايرو",
                    "tajawal" to "تجوال",
                    "naskh" to "النسخ"
                ).forEach { (key, label) ->
                    val isSelected = readingFontFamily == key
                    Surface(
                        onClick = { UserPreferencesManager.setReadingFontFamily(context, key) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) theme.primary else theme.secondarySurface.copy(alpha = 0.7f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            Text(
                                text = label,
                                color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        // Reading Atmosphere / Background Theme
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("نمط ولون الورق للقراءة", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "dark_luxury" to ("ملكي داكن" to Color(0xFF0B0F19)),
                    "sepia_warm" to ("ورق دافئ" to Color(0xFFFBF0D9)),
                    "oled_black" to ("أسود مطبق" to Color(0xFF000000)),
                    "light_clean" to ("أبيض ناصع" to Color(0xFFF8FAFC))
                ).forEach { (themeKey, pair) ->
                    val (title, bgCol) = pair
                    val isSelected = readingTheme == themeKey
                    Surface(
                        onClick = { UserPreferencesManager.setReadingTheme(context, themeKey) },
                        modifier = Modifier.weight(1f),
                        color = if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.secondarySurface.copy(alpha = 0.55f),
                        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(bgCol)
                                    .border(1.dp, theme.separator, CircleShape)
                            )
                            Text(
                                text = title,
                                color = if (isSelected) theme.primary else theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        // Show/Hide Tashkeel Toggle
        SettingToggleRow(
            title = "إظهار التشكيل والحركات الكاملة",
            subtitle = "تفعيل علامات الإعراب وضبط أحرف القرآن والأحاديث",
            checked = showTashkeel,
            onCheckedChange = { UserPreferencesManager.setShowTashkeel(context, it) }
        )
    }
}

/**
 * 2. قسم إعدادات الأذان والمواقيت والأذكار الإيمانية
 */
@Composable
fun SpiritualPreferencesSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val calculationMethod by UserPreferencesManager.calculationMethod.collectAsState()
    val backgroundScope = rememberCoroutineScope()
    var adhanBackground by remember { mutableStateOf(AdhanBackgroundPreferences.get(context)) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) backgroundScope.launch(Dispatchers.IO) {
            val saved = AdhanBackgroundPreferences.saveMedia(context, AdhanBackgroundPreferences.TYPE_IMAGE, uri)
            withContext(Dispatchers.Main) {
                if (saved) {
                    adhanBackground = AdhanBackgroundPreferences.get(context)
                    Toast.makeText(context, "تم حفظ صورة خلفية شاشة الأذان", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) backgroundScope.launch(Dispatchers.IO) {
            val saved = AdhanBackgroundPreferences.saveMedia(context, AdhanBackgroundPreferences.TYPE_VIDEO, uri)
            withContext(Dispatchers.Main) {
                if (saved) {
                    adhanBackground = AdhanBackgroundPreferences.get(context)
                    Toast.makeText(context, "تم حفظ فيديو خلفية شاشة الأذان", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "تعذر حفظ الفيديو. الحد الأقصى 30 ثانية و50 م.ب", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Calculation Method Chips
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("طريقة حساب مواقيت الصلاة", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        "MAKKAH" to "أم القرى (مكة المكرمة)",
                        "MWL" to "رابطة العالم الإسلامي",
                        "EGYPT" to "الهيئة المصرية العامة",
                        "KARACHI" to "جامعة العلوم بكراتشي",
                        "ISNA" to "أمريكا الشمالية (ISNA)"
                    )
                ) { (key, label) ->
                    val isSelected = calculationMethod == key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            UserPreferencesManager.setCalculationMethod(context, key)
                            Toast.makeText(context, "تم ضبط الحساب على: $label", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(label, fontFamily = CairoFont, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = theme.secondarySurface.copy(alpha = 0.65f),
                            labelColor = theme.textSecondary,
                            selectedContainerColor = theme.primary,
                            selectedLabelColor = theme.onPrimary
                        ),
                        border = BorderStroke(0.5.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        // مكتبة الصوت الموحدة
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("صوت الأذان", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "اختيار المؤذن والتنزيل والتشغيل يتم من مكتبة أصوات الأذان الموحدة.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            AppleSecondaryButton(
                text = "فتح مكتبة أصوات الأذان",
                icon = Icons.Default.VolumeUp,
                onClick = { AppNavigator.navigateTo(AppState.PRAYER_TIMES) }
            )
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("خلفية شاشة إشعار الأذان", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
            Text("تظهر عند دخول وقت الأذان، ومستقلة عن إشعار صلاة مباشر.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("night_blue" to "كحلي", "black_gold" to "ذهبي", "green_night" to "أخضر", "sky_night" to "سماوي")) { (preset, label) ->
                    val selected = adhanBackground.type == AdhanBackgroundPreferences.TYPE_PRESET && adhanBackground.value == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            AdhanBackgroundPreferences.savePreset(context, preset)
                            adhanBackground = AdhanBackgroundPreferences.get(context)
                        },
                        label = { Text(label, fontFamily = CairoFont, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = theme.secondarySurface.copy(alpha = 0.65f),
                            labelColor = theme.textSecondary,
                            selectedContainerColor = theme.primary,
                            selectedLabelColor = theme.onPrimary
                        ),
                        border = BorderStroke(0.5.dp, if (selected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AppleSecondaryButton(
                    text = "صورة",
                    icon = Icons.Default.Image,
                    tint = theme.primary,
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f)
                )
                AppleSecondaryButton(
                    text = "فيديو",
                    icon = Icons.Default.Videocam,
                    tint = theme.success,
                    onClick = { videoPicker.launch("video/*") },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "المحدد حاليًا: ${if (adhanBackground.type == AdhanBackgroundPreferences.TYPE_IMAGE) "صورة" else if (adhanBackground.type == AdhanBackgroundPreferences.TYPE_VIDEO) "فيديو" else "لون جاهز"}",
                color = theme.primary,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * 3. قسم إعدادات المونتاج والعلامة المائية وصناعة المحتوى
 */
@Composable
fun StudioWatermarkPreferencesSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val watermarkEnabled by UserPreferencesManager.watermarkEnabled.collectAsState()
    val watermarkText by UserPreferencesManager.watermarkText.collectAsState()
    val watermarkPosition by UserPreferencesManager.watermarkPosition.collectAsState()
    val defaultAspectRatio by UserPreferencesManager.defaultAspectRatio.collectAsState()
    val autoSaveToGallery by UserPreferencesManager.autoSaveToGallery.collectAsState()

    var editingWatermarkText by remember(watermarkText) { mutableStateOf(watermarkText) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingToggleRow(
            title = "تضمين علامتك المائية / اسم قناتك",
            subtitle = "إضافة توقيعك الشخصي على مقاطع الريلز والفيديوهات المنتجة",
            checked = watermarkEnabled,
            onCheckedChange = { UserPreferencesManager.setWatermarkEnabled(context, it) }
        )

        if (watermarkEnabled) {
            OutlinedTextField(
                value = editingWatermarkText,
                onValueChange = {
                    editingWatermarkText = it
                    UserPreferencesManager.setWatermarkText(context, it)
                },
                label = { Text("نص العلامة المائية (مثال: @qabas_channel)", fontFamily = CairoFont, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = theme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.separator,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("موضع العلامة المائية في الفيديو", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "BOTTOM_RIGHT" to "أسفل اليمين",
                        "BOTTOM_LEFT" to "أسفل اليسار",
                        "TOP_RIGHT" to "أعلى اليمين",
                        "TOP_LEFT" to "أعلى اليسار"
                    ).forEach { (posKey, posLabel) ->
                        val isSelected = watermarkPosition == posKey
                        Surface(
                            onClick = { UserPreferencesManager.setWatermarkPosition(context, posKey) },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) theme.primary else theme.secondarySurface.copy(alpha = 0.7f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                                Text(
                                    text = posLabel,
                                    color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("نسبة الأبعاد الافتراضية للمقاطع", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "9:16" to ("9:16 عمودي" to "ريلز وتيك توك"),
                    "1:1" to ("1:1 مربع" to "إنستغرام"),
                    "16:9" to ("16:9 أفقي" to "يوتيوب")
                ).forEach { (ratioKey, info) ->
                    val (title, sub) = info
                    val isSelected = defaultAspectRatio == ratioKey
                    Surface(
                        onClick = { UserPreferencesManager.setDefaultAspectRatio(context, ratioKey) },
                        modifier = Modifier.weight(1f),
                        color = if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.55f),
                        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) theme.primary else theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = sub,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        SettingToggleRow(
            title = "حفظ الفيديو تلقائياً في ألبوم الصور",
            subtitle = "تصدير نسخة فورية إلى مجلد Movies/QabasStudio عند اكتمال الرندر",
            checked = autoSaveToGallery,
            onCheckedChange = { UserPreferencesManager.setAutoSaveToGallery(context, it) }
        )
    }
}

/**
 * 4. قسم التخزين، توفير البيانات، والنسخ الاحتياطي (Supabase Exclusively)
 */
@Composable
fun StorageAndBackupSection(
    cacheSizeBytes: Long?,
    onClearCache: () -> Unit,
    isDeveloper: Boolean = false
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    val dataSaverMode by UserPreferencesManager.dataSaverMode.collectAsState()
    val cloudSyncEnabled by UserPreferencesManager.cloudSyncEnabled.collectAsState()
    val lastSyncTime by UserPreferencesManager.lastSyncTime.collectAsState()
    val isSyncing by UserPreferencesManager.isSyncing.collectAsState()
    val isSupabaseAvailable = SupabaseServices.isSupabaseAvailable
    val clipboardManager = LocalClipboardManager.current

    var isExporting by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf<String?>(null) }
    var importJsonInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSupabaseConfigDialog by remember { mutableStateOf(false) }

    if (showSupabaseConfigDialog && isDeveloper) {
        com.qabas.app.ui.dialogs.SupabaseCloudConfigDialog(
            onDismiss = { showSupabaseConfigDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Supabase Cloud Settings Sync Block
        Surface(
            color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.55f else 0.45f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مزامنة الإعدادات عبر Supabase", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }

                    Surface(
                        color = (if (isSupabaseAvailable) theme.success else theme.textSecondary).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSupabaseAvailable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isSupabaseAvailable) theme.success else theme.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isSupabaseAvailable) "متصل" else "محلي",
                                color = if (isSupabaseAvailable) theme.success else theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Text(
                    "تتيح مزامنة الإعدادات حفظ تفضيلات القراءة، المواقيت والعلامة المائية سحابياً عبر Supabase، مع الاعتماد على التخزين المحلي عند عدم الاتصال.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )

                SettingToggleRow(
                    title = "تفعيل المزامنة السحابية التلقائية",
                    subtitle = "رفع وتحديث التغييرات عند توفر الاتصال",
                    checked = cloudSyncEnabled,
                    onCheckedChange = { UserPreferencesManager.setCloudSyncEnabled(context, it) }
                )

                if (lastSyncTime > 0L) {
                    val formattedTime = remember(lastSyncTime) {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                    }
                    Text(
                        "آخر مزامنة سحابية ناجحة: $formattedTime",
                        color = theme.primary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ApplePrimaryButton(
                        text = "مزامنة الآن",
                        icon = Icons.Default.CloudUpload,
                        isLoading = isSyncing,
                        enabled = !isSyncing && isSupabaseAvailable,
                        onClick = {
                            coroutineScope.launch {
                                val ok = UserPreferencesManager.syncToSupabase(context)
                                Toast.makeText(
                                    context,
                                    if (ok) "تم رفع وتحديث الإعدادات بالسحابة بنجاح" else "تعذرت المزامنة بالسحابة — تم الحفظ محلياً",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    AppleSecondaryButton(
                        text = "جلب من السحابة",
                        icon = Icons.Default.CloudDownload,
                        enabled = !isSyncing && isSupabaseAvailable,
                        onClick = {
                            coroutineScope.launch {
                                val ok = UserPreferencesManager.fetchAndApplyFromSupabase(context)
                                Toast.makeText(
                                    context,
                                    if (ok) "تم جلب وتطبيق تفضيلاتك من السحابة" else "لم يتم العثور على إعدادات سحابية",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isDeveloper) {
                    AppleSecondaryButton(
                        text = "تهيئة وفحص اتصال Supabase",
                        icon = Icons.Default.Settings,
                        onClick = { showSupabaseConfigDialog = true }
                    )
                }
            }
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        SettingToggleRow(
            title = "وضع توفير بيانات الهاتف (Data Saver)",
            subtitle = "تقليل استهلاك الإنترنت عند الاتصال بالبيانات الخلوية وعدم تحميل 4K إلا عبر Wi-Fi",
            checked = dataSaverMode,
            onCheckedChange = { UserPreferencesManager.setDataSaverMode(context, it) }
        )

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        // Cache Management
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذاكرة التخزين المؤقت (Cache)", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
                val sizeText = cacheSizeBytes?.let { UpdateManager.formatSize(it) } ?: "0 B"
                Text(sizeText, color = theme.primary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "تتضمن الملفات المؤقتة للصور والصوتيات لتحميلها بسرعة دون استهلاك إنترنت متكرر.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            AppleSecondaryButton(
                text = "تنظيف الملفات المؤقتة",
                icon = Icons.Default.DeleteSweep,
                onClick = onClearCache
            )
        }

        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

        // Backup & Restore
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("النسخ الاحتياطي ونقل المحفوظات", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "احفظ نسخة احتياطية من أحاديثك ومقاطعك المفضلة وإعداداتك واستعدها عند تغيير هاتفك.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApplePrimaryButton(
                    text = "تصدير النسخة",
                    icon = Icons.Default.CloudDownload,
                    isLoading = isExporting,
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            val json = UserPreferencesManager.exportBackupJson(context)
                            isExporting = false
                            if (json != null) showBackupDialog = json
                            else Toast.makeText(context, "فشل إنشاء النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                AppleSecondaryButton(
                    text = "استعادة النسخة",
                    icon = Icons.Default.CloudUpload,
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showBackupDialog != null) {
        val backupJson = showBackupDialog!!
        AlertDialog(
            onDismissRequest = { showBackupDialog = null },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تم إنشاء النسخة الاحتياطية بنجاح", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "تم تجميع تفضيلاتك والمحفوظات في ملف JSON. يمكنك نسخه أو مشاركته لحفظه:",
                        fontFamily = NotoSansFont,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                    ) {
                        Text(
                            text = backupJson.take(300) + "...\n[كامل البيانات جاهزة للحفظ]",
                            color = theme.primary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, backupJson)
                            putExtra(Intent.EXTRA_TITLE, "Qabas_Studio_Backup.json")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة وحفظ النسخة الاحتياطية"))
                        showBackupDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة وحفظ الملف", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(backupJson))
                        Toast.makeText(context, "تم نسخ محتوى النسخة للحافظة", Toast.LENGTH_SHORT).show()
                        showBackupDialog = null
                    }
                ) {
                    Text("نسخ للحافظة", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استعادة النسخة الاحتياطية", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "الصق محتوى ملف النسخة الاحتياطية (JSON) لاسترجاع كافة التفضيلات والمحفوظات:",
                        fontFamily = NotoSansFont,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("الصق بيانات النسخة هنا...", fontFamily = NotoSansFont, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.separator,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.isBlank()) {
                            Toast.makeText(context, "يرجى لصق كود النسخة أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val ok = UserPreferencesManager.importBackupJson(context, importJsonInput)
                            if (ok) {
                                Toast.makeText(context, "تمت استعادة النسخة الاحتياطية بنجاح", Toast.LENGTH_LONG).show()
                                showImportDialog = false
                            } else {
                                Toast.makeText(context, "فشل استعادة النسخة — تأكد من صحة البيانات", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("استعادة الآن", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text(subtitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.onPrimary,
                checkedTrackColor = theme.success,
                uncheckedThumbColor = theme.textSecondary,
                uncheckedTrackColor = theme.secondarySurface
            )
        )
    }
}

/**
 * 5. قسم إعدادات سهولة الوصول (Accessibility)
 */
@Composable
fun AccessibilitySettingsSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val highContrast by UserPreferencesManager.highContrastMode.collectAsState()
    val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()
    val globalTextScale by UserPreferencesManager.globalTextScale.collectAsState()

    AppleCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "تحسين الرؤية والتفاعل مع النظام",
                color = theme.primary,
                fontFamily = CairoFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            SettingToggleRow(
                title = "وضع التباين العالي (High Contrast)",
                subtitle = "زيادة وضوح النصوص والألوان لتسهيل القراءة في الإضاءة القوية",
                checked = highContrast,
                onCheckedChange = { UserPreferencesManager.setHighContrastMode(context, it) }
            )

            HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

            SettingToggleRow(
                title = "تقليل الحركة (Reduced Motion)",
                subtitle = "تعطيل انتقالات التنقل المتحركة بين الشاشات لتقليل الحركة",
                checked = reducedMotion,
                onCheckedChange = { UserPreferencesManager.setReducedMotion(context, it) }
            )

            HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFormat, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حجم خط واجهة التطبيق", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${(globalTextScale * 100).toInt()}%", color = theme.primary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = globalTextScale,
                    onValueChange = { UserPreferencesManager.setGlobalTextScale(context, it) },
                    valueRange = 0.8f..1.4f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primary,
                        activeTrackColor = theme.primary,
                        inactiveTrackColor = theme.secondarySurface
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("صغير", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = CairoFont)
                    Text("افتراضي", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = CairoFont)
                    Text("كبير", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = CairoFont)
                }
            }
        }
    }
}
