package com.qabas.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * 🕌 QabasExitConfirmationDialog — نافذة تأكيد الخروج الفاخرة المطعمة بنور الذكر والتسبيح وتصميم HUD كوانتي.
 * تذكّر المستخدم بذكر الله وكفارة المجلس، وتتيح له التسبيح السريع مع تنفيذ مؤكد وموثوق لإغلاق التطبيق.
 */
@Composable
fun QabasExitConfirmationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // باقة مباركة من الأذكار النبوية وكفارة المجلس
    val dhikrList = remember {
        listOf(
            DhikrItem(
                text = "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا أَنْتَ ، أَسْتَغْفِرُكَ وَأَتُوبُ إِلَيْكَ",
                title = "دعاء كفارة المجلس",
                fadl = "يَمْحُو مَا كَانَ فِي الْمَجْلِسِ مِنْ لَغْوٍ"
            ),
            DhikrItem(
                text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
                title = "كلمتان خفيفتان على اللسان",
                fadl = "حبيبتانِ إلى الرَّحْمَنِ ثَقِيلَتَانِ فِي الْمِيزَانِ"
            ),
            DhikrItem(
                text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
                title = "خير ما قلت أنا والنبيون",
                fadl = "حِرزٌ من الشيطان ورِفعةٌ للدرجات"
            ),
            DhikrItem(
                text = "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى نَبِيِّنَا مُحَمَّدٍ وَعَلَى آلِهِ وَصَحْبِهِ أَجْمَعِينَ",
                title = "الصلاة على النبي ﷺ",
                fadl = "مَن صلَّى عليَّ صلاةً صلَّى اللَّهُ عليهِ بِها عَشْرًا"
            ),
            DhikrItem(
                text = "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ الَّذِي لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ وَأَتُوبُ إِلَيْهِ",
                title = "سيد الاستغفار والتوبة",
                fadl = "تُغفَرُ الذنوب وتُفرَجُ الهموم"
            ),
            DhikrItem(
                text = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ الْعَلِيِّ الْعَظِيمِ",
                title = "كنز من كنوز الجنة",
                fadl = "براءةٌ من الحول والقوة إلا بالله"
            ),
            DhikrItem(
                text = "سُبْحَانَ اللَّهِ ، وَالْحَمْدُ لِلَّهِ ، وَلَا إِلَهَ إِلَّا اللَّهُ ، وَاللَّهُ أَكْبَرُ",
                title = "الباقيات الصالحات",
                fadl = "أحبُّ الكلامِ إلى اللهِ تعالى"
            ),
            DhikrItem(
                text = "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ ، نِعْمَ الْمَوْلَى وَنِعْمَ النَّصِيرُ",
                title = "تفويض الأمر إلى الله",
                fadl = "قالها إبراهيم عليه السلام حين أُلقي في النار"
            )
        )
    }

    var selectedIndex by remember { mutableIntStateOf((0 until dhikrList.size).random()) }
    var tasbeehCount by remember { mutableIntStateOf(0) }
    var isTappingTasbeeh by remember { mutableStateOf(false) }

    val currentItem = dhikrList[selectedIndex]

    val pulseTransition = rememberInfiniteTransition(label = "qabasExitPulse")
    val borderGlow by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )

    val tasbeehScale by animateFloatAsState(
        targetValue = if (isTappingTasbeeh) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tasbeehBounce"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF020617).copy(alpha = 0.82f))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
                    .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = GoldPrimary.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(26.dp),
                backgroundColor = Color(0xFF090E1D).copy(alpha = 0.96f),
                borderColor = GoldPrimary.copy(alpha = borderGlow * 0.7f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── 1. الرأسية: الشعار والإغلاق والآية الكريمة ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(GoldPrimary.copy(alpha = 0.25f), Color(0xFF0B132B))
                                        )
                                    )
                                    .border(1.5.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "إيقاف تشغيل مصفوفة قبس",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "﴿ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ ﴾",
                                    color = GoldPrimary,
                                    fontFamily = AmiriFont,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إلغاء الخروج",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    // ── 2. بطاقة الذكر النورانية التفاعلية ──
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF030712),
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    GoldPrimary.copy(alpha = borderGlow * 0.7f),
                                    Color(0xFF00E5FF).copy(alpha = 0.4f),
                                    Color(0xFF10B981).copy(alpha = 0.5f)
                                )
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // شريط علوي للذكر
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "✨ ${currentItem.title}",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        selectedIndex = (selectedIndex + 1) % dhikrList.size
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "ذكر آخر 🔄",
                                        color = Color(0xFF00E5FF),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // نص الذكر الشريف مع حركة سلسة عند التبديل
                            AnimatedContent(
                                targetState = currentItem,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                                },
                                label = "dhikrContent"
                            ) { item ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "« ${item.text} »",
                                        color = Color(0xFFF8FAFC),
                                        fontFamily = AmiriFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 26.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                    if (item.fadl.isNotBlank()) {
                                        Text(
                                            text = "فضلُه: ${item.fadl}",
                                            color = Color(0xFF10B981),
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // شريط التفاعل (نسخ الذكر + عداد التسبيح السريع)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // زر نسخ الذكر
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentItem.text))
                                        Toast.makeText(context, "تم نسخ الذكر المبارك 📋 نفعك الله به", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "نسخ الذكر",
                                        color = TextSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }

                                // زر التسبيح التفاعلي اللحظي
                                Button(
                                    onClick = {
                                        tasbeehCount++
                                        isTappingTasbeeh = true
                                        coroutineScope.launch {
                                            delay(120)
                                            isTappingTasbeeh = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .scale(tasbeehScale)
                                ) {
                                    Text(
                                        "📿",
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (tasbeehCount > 0) "سبّحت $tasbeehCount مرّات" else "تسبيحة سريعة 📿",
                                        color = Color(0xFF10B981),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // تنبيه لطيف عن الإغلاق
                    Text(
                        text = "هل ترغب بمغادرة مركز قيادة قبس ستوديو؟ نسأل الله أن يبارك في وقتك وعملك.",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    // ── 3. أزرار التحكم الفعّالة (المتابعة + الإغلاق الفوري) ──
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // زر المتابعة والبقاء في التطبيق (الخيار الأبرز والأساسي)
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dismiss_exit_dialog"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = DeepSlate,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "المتابعة في قبس ستوديو ⚡",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.5.sp
                            )
                        }

                        // زر إغلاق التطبيق الفعلي والمؤكد مع إنهاء المهمة والعملية
                        Button(
                            onClick = {
                                onDismiss()
                                performAppExit(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("exit_app_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626).copy(alpha = 0.22f)
                            ),
                            border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(alpha = 0.75f))
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "إغلاق التطبيق نهائياً 🛑",
                                color = Color(0xFFEF4444),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DhikrItem(
    val text: String,
    val title: String,
    val fadl: String
)

/**
 * تنفيذ إغلاق التطبيق بشكل قاطع ومضمون 100% على جميع إصدارات أندرويد.
 * ينهي النشاط والمهمة من قائمة التطبيقات الحديثة، ويوقف المعالجة النواتية.
 */
fun performAppExit(context: Context) {
    try {
        val activity = context.findHostActivity() ?: (context as? Activity)
        if (activity != null) {
            activity.finishAffinity()
            activity.finishAndRemoveTask()
            activity.finish()
        }
    } catch (_: Throwable) {
        // الاستمرار للإغلاق الجبري
    }

    try {
        android.os.Process.killProcess(android.os.Process.myPid())
    } catch (_: Throwable) {
        try {
            exitProcess(0)
        } catch (_: Throwable) {}
    }
}
