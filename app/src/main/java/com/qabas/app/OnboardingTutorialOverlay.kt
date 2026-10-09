package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*

data class TutorialStep(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val badgeText: String,
    val actionText: String,
    val tipText: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingTutorialOverlay(
    onDismiss: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToCreate: () -> Unit
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            TutorialStep(
                stepNumber = 1,
                title = "1. كِتابة الفكرة بكلماتك البسيطة",
                subtitle = "صناعة فيديو فوري دون الحاجة لأي ضبط تقني",
                description = "ادخل موضوع الفيديو الدعوي الذي تريد صناعته أو اختر أحد القوالب الجاهزة. يعمل التطبيق فوراً وبشكل مؤتمت للجميع دون أي تعقيد.",
                icon = Icons.Default.EditNote,
                badgeText = "تشغيل فوري جاهز",
                actionText = "✨ ابدأ بكتابة فكرتك الآن",
                tipText = "💡 للمطورين والمتقدمين: يمكنك تخصيص مفاتيحك الخاصة من قسم الإعدادات لو أردت كوتا منفصلة."
            ),
            TutorialStep(
                stepNumber = 2,
                title = "2. المعالجة والإخراج بالذكاء الاصطناعي",
                subtitle = "الذكاء الاصطناعي يخرج لك المشاهد والسيناريو",
                description = "يقوم محرك قبس بتحويل كلمتك إلى مقاطع درامية مرتبة، مع تحديد الخطاف الجاذب (Hook) والوصف البصري والتعليق الصوتي.",
                icon = Icons.Default.AutoAwesome,
                badgeText = "إخراج سينمائي ذكي",
                actionText = "🎬 مراجعة المشاهد والتأثيرات",
                tipText = "💡 يمكنك تعديل مدة كل مشهد أو تغيير نبرة التعليق الصوتي بنقرة واحدة قبل التصدير."
            ),
            TutorialStep(
                stepNumber = 3,
                title = "3. المونتاج والتصدير النهائي",
                subtitle = "دمج المشاهد والختم الذهبي بنقرة واحدة",
                description = "يقوم محرك المونتاج المحلي بتركيب التعليق الصوتي والمؤثرات البصرية وإضافة ختم قبس الذهبي، وتصدير الفيديو لجهازك.",
                icon = Icons.Default.MovieFilter,
                badgeText = "فيديو جاهز 100% للنشر",
                actionText = "🚀 انطلق واصنع أول فيديو!",
                tipText = "💡 يمكنك مشاركة الفيديو المصدّر مباشرة على منصات التواصل الاجتماعي فور انتهائه."
            )
        )
    }

    val currentStep = steps[currentStepIndex]

    // Pulsing Glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    fun markCompleted() {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_completed_guided_tutorial", true).apply()
    }

    Dialog(
        onDismissRequest = {
            markCompleted()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF151B2B),
                                DeepSlate,
                                Color(0xFF090D16)
                            )
                        )
                    )
                    .border(BorderStroke(1.5.dp, GoldPrimary), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar with Step Counter & Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, GoldPrimary)
                    ) {
                        Text(
                            text = "الدليل الإرشادي السريع • خطوة ${currentStep.stepNumber} من ${steps.size}",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    TextButton(
                        onClick = {
                            markCompleted()
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = "تخطي الدليل",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, _ ->
                        val active = index == currentStepIndex
                        val color = if (active) GoldPrimary else Color.Gray.copy(alpha = 0.3f)
                        val width = if (active) 24.dp else 8.dp
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Icon Spotlight with Pulsing Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(haloScale)
                            .background(GoldPrimary.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(GoldPrimary.copy(alpha = 0.25f), CircleShape)
                            .border(1.5.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentStep.icon,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Transition Animation
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "stepAnimation"
                ) { step ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = step.title,
                            color = GoldPrimary,
                            fontSize = 20.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = step.subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            color = Color(0xFF0B0F19),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = step.description,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontFamily = CairoFont,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = step.tipText,
                                        color = GoldPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = CairoFont,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Direct Action Button
                Button(
                    onClick = {
                        if (currentStepIndex == 0) {
                            markCompleted()
                            onDismiss()
                            onNavigateToCreate()
                        } else if (currentStepIndex < steps.size - 1) {
                            currentStepIndex++
                        } else {
                            markCompleted()
                            onDismiss()
                            onNavigateToCreate()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentStep.actionText,
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = DeepSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Controls (Back / Next Step)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        TextButton(
                            onClick = { currentStepIndex-- }
                        ) {
                            Text(
                                text = "← الخطوة السابقة",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (currentStepIndex < steps.size - 1) {
                        TextButton(
                            onClick = { currentStepIndex++ }
                        ) {
                            Text(
                                text = "الخطوة التالية →",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
