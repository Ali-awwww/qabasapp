package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.RobotoMonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelSettingsSheet(
    onDismiss: () -> Unit,
    onHiddenReelsCleared: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

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
    var hiddenReelCount by remember { mutableIntStateOf(ReelPreferencesManager.getHiddenReelIds(context).size) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "إعدادات قسم الريلز",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "التشغيل والشبكة والتفاعل",
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = theme.textSecondary
                    )
                }
            }

            // 1. Playback & Audio
            AppleGroupedSection(header = "التشغيل والصوت") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "السرعة الافتراضية لمقاطع الفيديو",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    AppleSegmentedControl(
                        items = speeds.map { it to "${it}x" },
                        selectedItem = playbackSpeed,
                        onSelect = { speed ->
                            ReelPreferencesManager.setPlaybackSpeed(context, speed)
                            ReelPreferencesManager.triggerHaptic(context)
                        }
                    )
                }
                AppleInsetDivider()
                ReelSettingSwitchRow(
                    title = "كتم الصوت افتراضياً عند البدء",
                    subtitle = "بدء تشغيل المقاطع بدون صوت لتجنب الإحراج",
                    checked = startMuted,
                    onCheckedChange = {
                        ReelPreferencesManager.setStartMuted(context, it)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                )
                AppleInsetDivider()
                ReelSettingSwitchRow(
                    title = "التمرير التلقائي للمقطع التالي",
                    subtitle = "الانتقال للريل التالي تلقائياً عند انتهاء الفيديو الحالي",
                    checked = autoScrollOnFinish,
                    onCheckedChange = {
                        ReelPreferencesManager.setAutoScrollOnFinish(context, it)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                )
                AppleInsetDivider()
                ReelSettingSwitchRow(
                    title = "عرض العناوين والنصوص الإيمانية",
                    subtitle = "إظهار نصوص الآيات والأحاديث المكتوبة فوق الفيديو",
                    checked = showCaptions,
                    onCheckedChange = {
                        ReelPreferencesManager.setShowCaptions(context, it)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                )
            }

            // 2. Network & Offline
            AppleGroupedSection(header = "الشبكة والتخزين") {
                ReelSettingSwitchRow(
                    title = "وضع توفير البيانات (Data Saver)",
                    subtitle = "تقليل استهلاك البيانات وجودة البث عند استخدام الشبكة الخلوية",
                    checked = dataSaver,
                    onCheckedChange = {
                        ReelPreferencesManager.setDataSaver(context, it)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                )
                AppleInsetDivider()
                ReelSettingSwitchRow(
                    title = "تفعيل ميزة الأثر",
                    subtitle = "تحميل المقاطع الإيمانية مسبقاً ليعمل بدون إنترنت",
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
                AppleInsetDivider()
                ReelSettingSwitchRow(
                    title = "التشغيل التلقائي عبر Wi-Fi فقط",
                    subtitle = "عدم بدء الفيديو تلقائياً على باقات الجوال للحد من الاستهلاك",
                    checked = autoplayWifiOnly,
                    onCheckedChange = {
                        ReelPreferencesManager.setAutoplayWifiOnly(context, it)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                )
                AppleInsetDivider()
                AppleListRow(
                    title = "مسح الذاكرة المؤقتة للريلز",
                    subtitle = cacheStatus ?: "تحرير المساحة التخزينية المستخدمة بالمشاهدة",
                    icon = Icons.Default.CleaningServices,
                    iconBackground = theme.warning,
                    onClick = {
                        val result = ReelPreferencesManager.clearVideoCache(context)
                        cacheStatus = result
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم مسح الذاكرة المؤقتة: $result", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 3. Gestures & Privacy
            AppleGroupedSection(header = "الإيماءات والخصوصية") {
                ReelSettingSwitchRow(
                    title = "الاهتزاز اللمسي",
                    subtitle = "اهتزاز لطيف عند النقر المزدوج والإعجاب وحفظ المقطع",
                    checked = hapticFeedback,
                    onCheckedChange = {
                        ReelPreferencesManager.setHapticFeedback(context, it)
                        ReelPreferencesManager.triggerHaptic(context)
                    }
                )
                AppleInsetDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "وظيفة النقر المزدوج على الشاشة",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val doubleTapOptions = listOf(
                        "LIKE" to "إعجاب",
                        "SAVE" to "حفظ",
                        "PAUSE" to "إيقاف مؤقت"
                    )
                    AppleSegmentedControl(
                        items = doubleTapOptions,
                        selectedItem = doubleTapAction,
                        onSelect = { actionKey ->
                            ReelPreferencesManager.setDoubleTapAction(context, actionKey)
                            ReelPreferencesManager.triggerHaptic(context)
                        }
                    )
                }
                AppleInsetDivider()
                AppleListRow(
                    title = "إدارة المحتوى المخفي",
                    subtitle = if (hiddenReelCount == 0) "لا توجد مقاطع مخفية" else "$hiddenReelCount مقطع مخفي — اضغط للاستعادة",
                    icon = Icons.Default.VisibilityOff,
                    iconBackground = theme.primary,
                    trailingText = if (hiddenReelCount > 0) "استعادة" else null,
                    enabled = hiddenReelCount > 0,
                    onClick = if (hiddenReelCount > 0) {
                        {
                            ReelPreferencesManager.clearHiddenReels(context)
                            hiddenReelCount = 0
                            onHiddenReelsCleared()
                            Toast.makeText(context, "تمت استعادة المقاطع المخفية إلى الخلاصة", Toast.LENGTH_SHORT).show()
                        }
                    } else null
                )
            }
        }
    }

    if (showAlAtharExplanation) {
        AlertDialog(
            onDismissRequest = { showAlAtharExplanation = false },
            containerColor = theme.cardSurface,
            titleContentColor = theme.textPrimary,
            textContentColor = theme.textSecondary,
            icon = {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    "ميزة الأثر",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    Text(
                        "عند تفعيل هذه الميزة، سيقوم التطبيق بتحميل مجموعة مختارة من المقاطع الإيمانية في الخلفية بشكل صامت.",
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppleCard(containerColor = theme.primary.copy(alpha = 0.08f)) {
                        Text(
                            "هذا يضمن لك وصولاً سريعاً للمحتوى حتى عند انقطاع الإنترنت، لتكون صدقة جارية وأثراً لا ينقطع.",
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            color = theme.primary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("موافق وتفعيل", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlAtharExplanation = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp)
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
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                color = theme.textSecondary,
                lineHeight = 17.sp
            )
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

