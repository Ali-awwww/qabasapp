package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 1. قسم تخصيص القراءة والمصحف الشريف وصحيح الحديث
 */
@Composable
fun ReadingCustomizationSection() {
    val context = LocalContext.current
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
        // Live Real-Time Preview Card
        Surface(
            color = currentPalette.cardBackground,
            shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
            border = BorderStroke(1.dp, currentPalette.accentColor.copy(alpha = 0.5f)),
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
                        "معاينة حية للنص القرآني والحديث:",
                        color = currentPalette.accentColor,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${quranFontSize.toInt()} pt • ${currentPalette.name}",
                        color = currentPalette.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }

                // Sample Ayah / Hadith text
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
                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حجم خط النصوص والآيات", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("${quranFontSize.toInt()} نقطة", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = quranFontSize,
                onValueChange = { UserPreferencesManager.setQuranFontSize(context, it) },
                valueRange = 14f..30f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = GoldPrimary,
                    activeTrackColor = GoldPrimary,
                    inactiveTrackColor = Color(0xFF1E293B)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("أصغر (14)", color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                Text("متوسط (20)", color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                Text("كبير جداً (30)", color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Font Family Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("نوع الخط المفضل للقراءة:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "amiri" to "الأميري 📜",
                    "cairo" to "كايرو ✒️",
                    "tajawal" to "تجوال 📖",
                    "naskh" to "النسخ 🪶"
                ).forEach { (key, label) ->
                    val isSelected = readingFontFamily == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.CutCornerShape(6.dp))
                            .background(if (isSelected) GoldPrimary else Color(0xFF151B2B))
                            .border(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B), androidx.compose.foundation.shape.CutCornerShape(6.dp))
                            .clickable { UserPreferencesManager.setReadingFontFamily(context, key) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) DeepSlate else TextPrimary,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Reading Atmosphere / Background Theme
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("نمط ورائحة الورق للقراءة:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { UserPreferencesManager.setReadingTheme(context, themeKey) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                        border = BorderStroke(1.5.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                        shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(bgCol)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                            )
                            Text(
                                text = title,
                                color = if (isSelected) GoldPrimary else TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Show/Hide Tashkeel Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("إظهار التشكيل والحركات الكاملة", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("تفعيل علامات الإعراب وضبط أحرف القرآن والأحاديث", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
            }
            Switch(
                checked = showTashkeel,
                onCheckedChange = { UserPreferencesManager.setShowTashkeel(context, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GoldPrimary,
                    checkedTrackColor = GoldPrimary.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}

/**
 * 2. قسم إعدادات الأذان والمواقيت والأذكار الإيمانية
 */
@Composable
fun SpiritualPreferencesSection() {
    val context = LocalContext.current
    val calculationMethod by UserPreferencesManager.calculationMethod.collectAsState()
    val athanVoice by UserPreferencesManager.athanVoice.collectAsState()
    val morningAthkar by UserPreferencesManager.morningAthkar.collectAsState()
    val eveningAthkar by UserPreferencesManager.eveningAthkar.collectAsState()
    val qiyamReminder by UserPreferencesManager.qiyamReminder.collectAsState()
    val kahfFridayReminder by UserPreferencesManager.kahfFridayReminder.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Calculation Method Chips
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("طريقة حساب مواقيت الصلاة:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        label = { Text(label, fontFamily = CairoFont, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF151B2B),
                            labelColor = TextSecondary,
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = DeepSlate
                        ),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                        shape = androidx.compose.foundation.shape.CutCornerShape(6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Athan Voice Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("صوت الأذان المفضل:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    listOf(
                        "makkah" to "الحرم المكي 🕋",
                        "madinah" to "الحرم المدني 🕌",
                        "aqsa" to "المسجد الأقصى 🇵🇸",
                        "alafasy" to "مشاري العفاسي 🎙️",
                        "takbeerat" to "تكبيرات مختصرة 🔊",
                        "silent" to "تنبيه صامت / اهتزاز 📳"
                    )
                ) { (key, label) ->
                    val isSelected = athanVoice == key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            UserPreferencesManager.setAthanVoice(context, key)
                            Toast.makeText(context, "تم اختيار: $label", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(label, fontFamily = CairoFont, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF151B2B),
                            labelColor = TextSecondary,
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = DeepSlate
                        ),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                        shape = androidx.compose.foundation.shape.CutCornerShape(6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Athkar Reminders Switches
        Text("تنبيهات الزاد والأذكار الدورية:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)

        // Morning Athkar
        SettingToggleRow(
            title = "تذكير أذكار الصباح",
            subtitle = "تنبيه يومي مبارك بعد صلاة الفجر للاستفتاح بالأذكار",
            checked = morningAthkar,
            onCheckedChange = { UserPreferencesManager.setMorningAthkar(context, it) }
        )

        // Evening Athkar
        SettingToggleRow(
            title = "تذكير أذكار المساء",
            subtitle = "تنبيه يومي بعد صلاة العصر لحفظ النفس والتحصين",
            checked = eveningAthkar,
            onCheckedChange = { UserPreferencesManager.setEveningAthkar(context, it) }
        )

        // Qiyam Reminder
        SettingToggleRow(
            title = "تذكير قيام الليل والوتر",
            subtitle = "تنبيه في الثلث الأخير من الليل قبل الفجر",
            checked = qiyamReminder,
            onCheckedChange = { UserPreferencesManager.setQiyamReminder(context, it) }
        )

        // Kahf Friday Reminder
        SettingToggleRow(
            title = "تذكير سورة الكهف والصلاة على النبي ﷺ",
            subtitle = "تنبيه أسبوعي صباح كل يوم جمعة",
            checked = kahfFridayReminder,
            onCheckedChange = { UserPreferencesManager.setKahfFridayReminder(context, it) }
        )
    }
}

/**
 * 3. قسم إعدادات المونتاج والعلامة المائية وصناعة المحتوى
 */
@Composable
fun StudioWatermarkPreferencesSection() {
    val context = LocalContext.current
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
        // Watermark Switch & Input
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
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = GoldPrimary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = androidx.compose.foundation.shape.CutCornerShape(8.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Watermark Position
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("موضع العلامة المائية في الفيديو:", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(androidx.compose.foundation.shape.CutCornerShape(4.dp))
                                .background(if (isSelected) GoldPrimary else Color(0xFF151B2B))
                                .border(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B), androidx.compose.foundation.shape.CutCornerShape(4.dp))
                                .clickable { UserPreferencesManager.setWatermarkPosition(context, posKey) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = posLabel,
                                color = if (isSelected) DeepSlate else TextPrimary,
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Default Aspect Ratio Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("نسبة الأبعاد الافتراضية للمقاطع:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "9:16" to ("9:16 عمودي" to "ريلز، تيك توك، ستوري"),
                    "1:1" to ("1:1 مربع" to "إنستغرام وفيسبوك"),
                    "16:9" to ("16:9 أفقي" to "يوتيوب وتلفاز")
                ).forEach { (ratioKey, info) ->
                    val (title, sub) = info
                    val isSelected = defaultAspectRatio == ratioKey
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { UserPreferencesManager.setDefaultAspectRatio(context, ratioKey) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                        border = BorderStroke(1.5.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                        shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
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
                                color = if (isSelected) GoldPrimary else TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = sub,
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Auto-save to gallery
        SettingToggleRow(
            title = "حفظ الفيديو تلقائياً في ألبوم الصور",
            subtitle = "تصدير نسخة فورية إلى مجلد Movies/QabasStudio عند اكتمال الرندر",
            checked = autoSaveToGallery,
            onCheckedChange = { UserPreferencesManager.setAutoSaveToGallery(context, it) }
        )
    }
}

/**
 * 4. قسم التخزين، توفير البيانات، والنسخ الاحتياطي
 */
@Composable
fun StorageAndBackupSection(
    cacheSizeBytes: Long?,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dataSaverMode by UserPreferencesManager.dataSaverMode.collectAsState()
    val cacheLimitMb by UserPreferencesManager.cacheLimitMb.collectAsState()
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

    if (showSupabaseConfigDialog) {
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
            color = Color(0xFF151B2B),
            shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = true)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مزامنة الإعدادات بالسحابة (Supabase)", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        Surface(
                            color = if (isSupabaseAvailable) Color(0xFF065F46) else Color(0xFF374151),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isSupabaseAvailable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isSupabaseAvailable) "Supabase ☁️" else "محلي 💾",
                                    color = Color.White,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        val isFirebaseActive = remember { FirebaseManager.isConfigured(context) }
                        Surface(
                            color = if (isFirebaseActive) Color(0xFF78350F) else Color(0xFF374151),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isFirebaseActive) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color(0xFFFFA000),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isFirebaseActive) "Firebase 🔥" else "Firebase غير متصل",
                                    color = Color.White,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Text(
                    "تتيح مزامنة الإعدادات حفظ تفضيلات القراءة، المواقيت والعلامة المائية سحابياً عبر Supabase، مع التبديل التلقائي للتخزين المحلي عند انقطاع الاتصال.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )

                SettingToggleRow(
                    title = "تفعيل المزامنة السحابية التلقائية",
                    subtitle = "رفع وتحديث التغييرات في الخلفية عند توفر الإنترنت",
                    checked = cloudSyncEnabled,
                    onCheckedChange = { UserPreferencesManager.setCloudSyncEnabled(context, it) }
                )

                if (lastSyncTime > 0L) {
                    val formattedTime = remember(lastSyncTime) {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                    }
                    Text(
                        "آخر مزامنة سحابية ناجحة: $formattedTime",
                        color = GoldSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val ok = UserPreferencesManager.syncToSupabase(context)
                                if (ok) {
                                    Toast.makeText(context, "تم رفع وتحديث الإعدادات بالسحابة بنجاح ☁️", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "تعذر المزامنة بالسحابة — تم الاعتماد على التخزين المحلي 💾", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مزامنة الآن ☁️", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val ok = UserPreferencesManager.fetchAndApplyFromSupabase(context)
                                if (ok) {
                                    Toast.makeText(context, "تم جلب وتطبيق تفضيلاتك من السحابة ✦", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "لم يتم العثور على إعدادات سحابية — تم استخدام التخزين المحلي 💾", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("جلب من السحابة 📥", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }

                FilledTonalButton(
                    onClick = { showSupabaseConfigDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = GoldPrimary.copy(alpha = 0.12f),
                        contentColor = GoldPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تهيئة وفحص اتصال Supabase وأكواد SQL ⚡", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Data Saver Mode
        SettingToggleRow(
            title = "وضع توفير بيانات الهاتف (Data Saver)",
            subtitle = "تقليل استهلاك الإنترنت عند الاتصال بالبيانات الخلوية وعدم تحميل 4K إلا عبر Wi-Fi",
            checked = dataSaverMode,
            onCheckedChange = { UserPreferencesManager.setDataSaverMode(context, it) }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        // Cache Management
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذاكرة التخزين المؤقت (Cache):", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val sizeText = cacheSizeBytes?.let { UpdateManager.formatSize(it) } ?: "0 B"
                Text(sizeText, color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "تتضمن الملفات المؤقتة للصور والصوتيات لتحميلها بسرعة دون استهلاك إنترنت متكرر.",
                color = TextSecondary,
                fontFamily = CairoFont,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onClearCache,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                shape = androidx.compose.foundation.shape.CutCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تنظيف الملفات المؤقتة وتسريع التطبيق 🧹", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        // Backup & Restore (النسخ الاحتياطي ونقل المحفوظات)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("النسخ الاحتياطي ونقل المحفوظات:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "احفظ نسخة احتياطية من أحاديثك ومقاطعك المفضلة وإعداداتك واستعدها عند تغيير هاتفك.",
                color = TextSecondary,
                fontFamily = CairoFont,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            val json = UserPreferencesManager.exportBackupJson(context)
                            isExporting = false
                            if (json != null) {
                                showBackupDialog = json
                            } else {
                                Toast.makeText(context, "فشل إنشاء النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير النسخة 📤", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعادة النسخة 📥", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        }
    }

    // Export Backup Dialog
    if (showBackupDialog != null) {
        val backupJson = showBackupDialog!!
        AlertDialog(
            onDismissRequest = { showBackupDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تم إنشاء النسخة الاحتياطية بنجاح ✦", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "تم تجميع تفضيلاتك والمحفوظات في ملف JSON آمن. يمكنك نسخه أو مشاركته لحفظه بأمان:",
                        fontFamily = CairoFont,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = Color(0xFF0B0F19),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                    ) {
                        Text(
                            text = backupJson.take(300) + "...\n[كامل البيانات مشفرة وآمنة]",
                            color = GoldSecondary,
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
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, backupJson)
                            putExtra(Intent.EXTRA_TITLE, "Qabas_Studio_Backup.json")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "مشاركة وحفظ النسخة الاحتياطية")
                        context.startActivity(shareIntent)
                        showBackupDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة وحفظ الملف", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(backupJson))
                        Toast.makeText(context, "تم نسخ محتوى النسخة للحافظة 📋", Toast.LENGTH_SHORT).show()
                        showBackupDialog = null
                    }
                ) {
                    Text("نسخ للحافظة", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF151B2B),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Import Backup Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استعادة النسخة الاحتياطية", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "الصق محتوى ملف النسخة الاحتياطية (JSON) لاسترجاع كافة التفضيلات والمحفوظات فوراً:",
                        fontFamily = CairoFont,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("الصق بيانات النسخة هنا...", fontFamily = NotoSansFont, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
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
                                Toast.makeText(context, "تمت استعادة النسخة الاحتياطية بنجاح ✦", Toast.LENGTH_LONG).show()
                                showImportDialog = false
                            } else {
                                Toast.makeText(context, "فشل استعادة النسخة — تأكد من صحة البيانات", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("استعادة الآن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF151B2B),
            shape = RoundedCornerShape(16.dp)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldPrimary,
                checkedTrackColor = GoldPrimary.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}
