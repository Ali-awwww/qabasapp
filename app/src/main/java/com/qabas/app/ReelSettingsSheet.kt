package com.qabas.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelSettingsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val autoplayWifiOnly by ReelPreferencesManager.autoplayWifiOnly.collectAsState()
    val startMuted by ReelPreferencesManager.startMuted.collectAsState()
    val dataSaver by ReelPreferencesManager.dataSaver.collectAsState()
    val hapticFeedback by ReelPreferencesManager.hapticFeedback.collectAsState()
    val autoScrollOnFinish by ReelPreferencesManager.autoScrollOnFinish.collectAsState()
    val playbackSpeed by ReelPreferencesManager.playbackSpeed.collectAsState()
    val showCaptions by ReelPreferencesManager.showCaptions.collectAsState()
    val doubleTapAction by ReelPreferencesManager.doubleTapAction.collectAsState()
    val alAtharEnabled by ReelPreferencesManager.alAtharEnabled.collectAsState()

    var cacheStatus by remember { mutableStateOf<String?>(null) }
    var showAlAtharExplanation by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "إعدادات قسم الريلز",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "REELS SETTINGS // PLAYER & PRIVACY",
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            color = GoldPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))

            // 1. سرعة التشغيل الافتراضية
            SectionHeader(title = "سرعة التشغيل والصوت", icon = Icons.Default.Speed)

            Text(
                text = "السرعة الافتراضية لمقاطع الفيديو:",
                fontFamily = CairoFont,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speeds.forEach { speed ->
                    val isSelected = playbackSpeed == speed
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                ReelPreferencesManager.setPlaybackSpeed(context, speed)
                                ReelPreferencesManager.triggerHaptic(context)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speed}x",
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) DeepSlate else Color.White
                            )
                        }
                    }
                }
            }

            // التبديل: كتم الصوت افتراضياً
            ReelSettingSwitchRow(
                title = "كتم الصوت افتراضياً عند البدء",
                subtitle = "بدء تشغيل المقاطع بدون صوت لتجنب الإحراج",
                checked = startMuted,
                onCheckedChange = {
                    ReelPreferencesManager.setStartMuted(context, it)
                    ReelPreferencesManager.triggerHaptic(context)
                }
            )

            // التبديل: التمرير التلقائي بعد نهاية المقطع
            ReelSettingSwitchRow(
                title = "التمرير التلقائي للمقطع التالي",
                subtitle = "الانتقال للريل التالي تلقائياً عند انتهاء الفيديو الحالي",
                checked = autoScrollOnFinish,
                onCheckedChange = {
                    ReelPreferencesManager.setAutoScrollOnFinish(context, it)
                    ReelPreferencesManager.triggerHaptic(context)
                }
            )

            // التبديل: إظهار النصوص التوضيحية
            ReelSettingSwitchRow(
                title = "عرض العناوين والنصوص الإيمانية",
                subtitle = "إظهار نصوص الآيات والأحاديث المكتوبة فوق الفيديو",
                checked = showCaptions,
                onCheckedChange = {
                    ReelPreferencesManager.setShowCaptions(context, it)
                    ReelPreferencesManager.triggerHaptic(context)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. توفير البيانات والشبكة
            SectionHeader(title = "الشبكة والبيانات", icon = Icons.Default.Wifi)

            ReelSettingSwitchRow(
                title = "وضع توفير باقة الإنترنت (Data Saver)",
                subtitle = "تقليل استهلاك البيانات وجودة البث عند استخدام الشبكة الخلوية",
                checked = dataSaver,
                onCheckedChange = {
                    ReelPreferencesManager.setDataSaver(context, it)
                    ReelPreferencesManager.triggerHaptic(context)
                }
            )

            // ميزة الأثر
            ReelSettingSwitchRow(
                title = "تفعيل ميزة الأثر ✦",
                subtitle = "تحميل المحتوى الإيماني مسبقاً ليعمل بدون إنترنت وفي الخلفية",
                checked = alAtharEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showAlAtharExplanation = true
                    } else {
                        ReelPreferencesManager.setAlAtharEnabled(context, false)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                }
            )

            ReelSettingSwitchRow(
                title = "التشغيل التلقائي عبر Wi-Fi فقط",
                subtitle = "عدم بدء الفيديو تلقائياً على باقات الجوال للحد من الاستهلاك",
                checked = autoplayWifiOnly,
                onCheckedChange = {
                    ReelPreferencesManager.setAutoplayWifiOnly(context, it)
                    ReelPreferencesManager.triggerHaptic(context)
                }
            )

            // مسح كاش الفيديو
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable {
                        val result = ReelPreferencesManager.clearVideoCache(context)
                        cacheStatus = result
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم مسح الذاكرة المؤقتة: $result", Toast.LENGTH_SHORT).show()
                    },
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "مسح الذاكرة المؤقتة للريلز (Cache)",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = Color.White
                            )
                            Text(
                                text = cacheStatus ?: "تحرير المساحة التخزينية المستخدمة بالمشاهدة",
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. الإيماءات والتفاعل
            SectionHeader(title = "الإيماءات والتفاعل", icon = Icons.Default.TouchApp)

            ReelSettingSwitchRow(
                title = "الاهتزاز اللمسي (Haptic Feedback)",
                subtitle = "اهتزاز لطيف عند النقر المزدوج والإعجاب وحفظ المقطع",
                checked = hapticFeedback,
                onCheckedChange = {
                    ReelPreferencesManager.setHapticFeedback(context, it)
                    ReelPreferencesManager.triggerHaptic(context)
                }
            )

            Text(
                text = "وظيفة النقر المزدوج على الشاشة:",
                fontFamily = CairoFont,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            val doubleTapOptions = listOf(
                Triple("LIKE", "إعجاب سريع ❤️", Icons.Default.Favorite),
                Triple("SAVE", "حفظ في المفضلة 🔖", Icons.Default.Bookmark),
                Triple("PAUSE", "إيقاف مؤقت ⏸️", Icons.Default.Pause)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                doubleTapOptions.forEach { (actionKey, label, icon) ->
                    val isSelected = doubleTapAction == actionKey
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                ReelPreferencesManager.setDoubleTapAction(context, actionKey)
                                ReelPreferencesManager.triggerHaptic(context)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GoldPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer info
            Text(
                text = "QABAS REELS ENGINE // LOCAL_PREFS & EDGE_SYNC",
                fontFamily = RobotoMonoFont,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.35f),
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Al-Athar Explanation Dialog
    if (showAlAtharExplanation) {
        AlertDialog(
            onDismissRequest = { showAlAtharExplanation = false },
            containerColor = Color(0xFF0F172A),
            titleContentColor = GoldPrimary,
            textContentColor = Color.White.copy(alpha = 0.85f),
            icon = {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    "ميزة الأثر ✦",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    Text(
                        "عند تفعيل هذه الميزة، سيقوم التطبيق بتحميل مجموعة مختارة من المقاطع الإيمانية والنفحات الإيمانية في الخلفية بشكل صامت.",
                        fontFamily = CairoFont,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "هذا يضمن لك وصولاً سريعاً للمحتوى حتى عند انقطاع الإنترنت، لتكون صدقة جارية وأثراً لا ينقطع.",
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            color = GoldPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ReelPreferencesManager.setAlAtharEnabled(context, true)
                        ReelPreferencesManager.triggerHaptic(context)
                        showAlAtharExplanation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("موافق وتفعيل", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlAtharExplanation = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(16.dp).border(BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)), RoundedCornerShape(24.dp))
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp, top = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            color = GoldPrimary
        )
    }
}

@Composable
fun ReelSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSlate,
                checkedTrackColor = GoldPrimary,
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}
