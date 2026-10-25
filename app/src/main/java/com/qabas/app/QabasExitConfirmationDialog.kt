package com.qabas.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlin.system.exitProcess

/**
 * 🕌 QabasExitConfirmationDialog — نافذة تأكيد الخروج التفاعلية بتصميم HUD سيبراني وتذكير بذكر الله.
 * توفر تجربة روحانية فاخرة تذكر المستخدم بذكر الله مع عداد تسبيح فوري وتنفيذ مؤكد لإغلاق التطبيق.
 */
@Composable
fun QabasExitConfirmationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // قائمة الأذكار المباركة للخروج
    val dhikrList = remember {
        listOf(
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
            "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ الَّذِي لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ وَأَتُوبُ إِلَيْهِ",
            "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
            "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى نَبِيِّنَا مُحَمَّدٍ وَعَلَى آلِهِ وَصَحْبِهِ أَجْمَعِينَ",
            "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ الْعَلِيِّ الْعَظِيمِ"
        )
    }

    var selectedDhikrIndex by remember { mutableIntStateOf((0 until dhikrList.size).random()) }
    var dhikrCount by remember { mutableIntStateOf(0) }
    var showCopiedToast by remember { mutableStateOf(false) }

    val currentDhikr = dhikrList[selectedDhikrIndex]

    val pulseTransition = rememberInfiniteTransition(label = "exitPulse")
    val pulseGlow by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseGlow"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF090E1D).copy(alpha = 0.95f),
                borderColor = GoldPrimary.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── 1. Header icon and title ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "تأكيد الخروج من قبس ستوديو",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5.sp
                                )
                                Text(
                                    "﴿أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ﴾",
                                    color = GoldPrimary,
                                    fontFamily = AmiriFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Divider(color = Color(0xFF1E293B), thickness = 1.dp)

                    // ── 2. بطاقة تذكير الذكر السيبرانية الروحانية ──
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF030712),
                        border = BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(GoldPrimary.copy(alpha = pulseGlow * 0.6f), Color(0xFF2DD4BF).copy(alpha = pulseGlow * 0.4f))
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        "🌿 ذكر وحسنة قبل المغادرة",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Text(
                                    "تبديل الذكر 🔄",
                                    color = Color(0xFF2DD4BF),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedDhikrIndex = (selectedDhikrIndex + 1) % dhikrList.size
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // نص الذكر المبارك
                            Text(
                                text = "« $currentDhikr »",
                                color = Color.White,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            // شريط التحكم بالذكر (عداد التسبيح + نسخ الذكر)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // زر نسخ الذكر
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentDhikr))
                                        Toast.makeText(context, "تم نسخ الذكر المبارك 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("نسخ الذكر", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                }

                                // عداد التسبيح التفاعلي
                                Button(
                                    onClick = {
                                        dhikrCount++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF).copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (dhikrCount > 0) "سبّحت $dhikrCount مرّة 📿" else "تسبحية سريعة 📿",
                                        color = Color(0xFF2DD4BF),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // ── 3. أزرار إجراء الخروج أو المتابعة ──
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // زر المتابعة والبقاء في التطبيق (الخيار الرئيسي)
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "المتابعة في قبس ستوديو ⚡",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // زر تأكيد الخروج الفعلي وإغلاق النشاط
                        OutlinedButton(
                            onClick = {
                                performAppExit(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AiDanger.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = AiDanger.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AiDanger, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "تأكيد الخروج من التطبيق 🛑",
                                color = AiDanger,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * دالة مساعدة لتنفيذ إغلاق التطبيق الموثق بدون فشل الصلاحيات أو تحويلات ContextWrapper.
 */
fun performAppExit(context: Context) {
    val activity = context.findHostActivity()
    if (activity != null) {
        activity.finishAffinity()
        activity.finish()
    } else {
        // خطوة بديلة وإنقاذية لإغلاق العملية
        exitProcess(0)
    }
}
