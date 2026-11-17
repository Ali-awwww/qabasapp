package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.ApplePrimaryButton
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
    val theme = QabasTheme.current
    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            TutorialStep(
                stepNumber = 1,
                title = "1. كتابة الفكرة بكلماتك البسيطة",
                subtitle = "صناعة فيديو فوري دون الحاجة لأي ضبط تقني",
                description = "أدخل موضوع الفيديو الدعوي الذي تريد صناعته أو اختر أحد القوالب الجاهزة. يعمل التطبيق بسلاسة وتنظيم واضح دون تعقيد.",
                icon = Icons.Default.EditNote,
                badgeText = "تشغيل جاهز",
                actionText = "ابدأ بكتابة فكرتك الآن",
                tipText = "يمكنك تخصيص مفاتيحك الخاصة من قسم الإعدادات في أي وقت عند الحاجة."
            ),
            TutorialStep(
                stepNumber = 2,
                title = "2. المعالجة والإخراج بالذكاء الاصطناعي",
                subtitle = "الذكاء الاصطناعي يخرج لك المشاهد والسيناريو",
                description = "يقوم محرك قبس بتحويل كلمتك إلى مقاطع مرتبة، مع تحديد الخطاف الجاذب والوصف البصري والتعليق الصوتي.",
                icon = Icons.Default.AutoAwesome,
                badgeText = "إخراج ذكي",
                actionText = "متابعة للمرحلة التالية",
                tipText = "يمكنك تعديل مدة كل مشهد أو تغيير نبرة التعليق الصوتي قبل التصدير."
            ),
            TutorialStep(
                stepNumber = 3,
                title = "3. المونتاج والتصدير النهائي",
                subtitle = "دمج المشاهد والختم الذهبي بنقرة واحدة",
                description = "يقوم محرك المونتاج بتركيب التعليق الصوتي والمؤثرات البصرية وتصدير الفيديو لجهازك بجودة عالية.",
                icon = Icons.Default.MovieFilter,
                badgeText = "جاهز للنشر",
                actionText = "انطلق واصنع أول فيديو",
                tipText = "يمكنك مشاركة الفيديو المصدّر مباشرة على منصات التواصل الاجتماعي فور انتهائه."
            )
        )
    }

    val currentStep = steps[currentStepIndex]

    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
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
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 480.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = theme.cardSurface,
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = theme.primary.copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "الدليل السريع • ${currentStep.stepNumber} من ${steps.size}",
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        TextButton(
                            onClick = {
                                markCompleted()
                                onDismiss()
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "تخطي",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { index, _ ->
                            val active = index == currentStepIndex
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (active) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (active) theme.primary else theme.separator)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(78.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .scale(haloScale)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.12f))
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentStep.icon,
                                contentDescription = null,
                                tint = theme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "stepAnimation"
                    ) { step ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = step.title,
                                color = theme.textPrimary,
                                fontSize = 19.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = step.subtitle,
                                color = theme.textSecondary,
                                fontSize = 12.5.sp,
                                fontFamily = NotoSansFont,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                color = theme.secondarySurface.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = step.description,
                                        color = theme.textPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = NotoSansFont,
                                        lineHeight = 20.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = theme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = step.tipText,
                                            color = theme.primary,
                                            fontSize = 11.5.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    ApplePrimaryButton(
                        text = currentStep.actionText,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = {
                            if (currentStepIndex == 0 || currentStepIndex == steps.size - 1) {
                                markCompleted()
                                onDismiss()
                                onNavigateToCreate()
                            } else {
                                currentStepIndex++
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            TextButton(
                                onClick = { currentStepIndex-- },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "الخطوة السابقة",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (currentStepIndex < steps.size - 1) {
                            TextButton(
                                onClick = { currentStepIndex++ },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "الخطوة التالية",
                                    color = theme.primary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
