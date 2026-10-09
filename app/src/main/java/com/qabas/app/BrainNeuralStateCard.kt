package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlin.math.sin

/**
 * مؤشر الحالة العصبية والبصرية للعقل الإخراجي (Brain Neural State Card)
 * يوضح حالة العقل الحالية (استرجاع الذاكرة، التحليل، معالجة النمط، التفكير بـ Gemini، وجاهزية الإنتاج)
 * باستخدام رسوم نيون متحركة حية (Procedural Lottie-grade Neural Animation) ومؤشرات تفاعلية فاخرة.
 */
@Composable
fun BrainNeuralStateCard(
    uiState: BrainUiState,
    onSyncMemory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "brain_pulse")
    
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val isProcessing = uiState.status != BrainStatus.IDLE && uiState.status != BrainStatus.SUCCESS

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isProcessing) 14.dp else 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = GoldPrimary.copy(alpha = if (isProcessing) 0.25f else 0.10f),
                spotColor = GoldPrimary.copy(alpha = if (isProcessing) 0.35f else 0.15f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isProcessing) 1.5.dp else 1.dp,
            brush = if (isProcessing) {
                Brush.horizontalGradient(
                    listOf(
                        GoldPrimary.copy(alpha = pulseGlow),
                        Color(0xFF38BDF8),
                        GoldSecondary.copy(alpha = pulseGlow)
                    )
                )
            } else {
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1E293B),
                        GoldPrimary.copy(alpha = 0.5f),
                        Color(0xFF1E293B)
                    )
                )
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF111827),
                            DeepSlate
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // الشريط العلوي: الهوية وشارة الحالة والمزامنة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.12f))
                            .border(1.dp, GoldPrimary.copy(alpha = pulseGlow), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieBrainVisualizer(
                            status = uiState.status,
                            size = 38.dp
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "عقل قبس الإخراجي (StyleBrain)",
                                color = TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "${uiState.brainStrengthScore}/100",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = getStatusSubheading(uiState.status),
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }
                }

                // زر مزامنة سحابة Supabase
                IconButton(
                    onClick = onSyncMemory,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Memory",
                        tint = if (isProcessing) GoldPrimary else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // لوحة الرسوم العصبية التفاعلية (Procedural Neural Waveform)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B1120))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val midY = height / 2f

                    // رسم خلفية شبكية خافتة
                    val gridSpacing = 20.dp.toPx()
                    var x = 0f
                    while (x < width) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.3f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1f
                        )
                        x += gridSpacing
                    }

                    // رسم موجة النشاط العصبي
                    val path = Path()
                    val waveColor = if (isProcessing) GoldPrimary else Color(0xFF38BDF8)
                    val frequency = if (isProcessing) 0.035f else 0.018f
                    val amplitude = if (isProcessing) (height * 0.32f) * pulseGlow else height * 0.18f

                    for (px in 0..width.toInt() step 3) {
                        val currentX = px.toFloat()
                        val currentY = midY + sin((currentX * frequency) + wavePhase) * amplitude
                        if (px == 0) {
                            path.moveTo(currentX, currentY)
                        } else {
                            path.lineTo(currentX, currentY)
                        }
                    }

                    drawPath(
                        path = path,
                        color = waveColor,
                        style = Stroke(width = if (isProcessing) 3.dp.toPx() else 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // رسم عُقد عصبية متوهجة (Neural Nodes)
                    val nodeCount = 5
                    val step = width / (nodeCount + 1)
                    for (i in 1..nodeCount) {
                        val nodeX = step * i
                        val nodeY = midY + sin((nodeX * frequency) + wavePhase) * amplitude
                        drawCircle(
                            color = GoldPrimary.copy(alpha = pulseGlow),
                            radius = if (isProcessing) 5.dp.toPx() else 3.5.dp.toPx(),
                            center = Offset(nodeX, nodeY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(nodeX, nodeY)
                        )
                    }
                }

                // نص الحالة المباشر في منتصف شاشة الموجات
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xFF0B1120).copy(alpha = 0.90f), RoundedCornerShape(8.dp))
                        .border(0.8.dp, if (isProcessing) GoldPrimary.copy(alpha = 0.6f) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LottieBrainVisualizer(
                        status = uiState.status,
                        size = 18.dp
                    )

                    Text(
                        text = uiState.statusMessage,
                        color = if (isProcessing) GoldPrimary else TextPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // مؤشرات تقدم المراحل العصبية (Neural Pipeline Steps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuralStepBadge(
                    title = "ذاكرة Supabase",
                    isActive = uiState.status == BrainStatus.FETCHING_MEMORY,
                    isDone = uiState.activeStyleObjectsCount > 0
                )
                NeuralStepBadge(
                    title = "تحليل الفكرة",
                    isActive = uiState.status == BrainStatus.ANALYZING_IDEA,
                    isDone = uiState.status.ordinal > BrainStatus.ANALYZING_IDEA.ordinal
                )
                NeuralStepBadge(
                    title = "مطابقة النمط",
                    isActive = uiState.status == BrainStatus.MATCHING_STYLE,
                    isDone = uiState.status.ordinal > BrainStatus.MATCHING_STYLE.ordinal
                )
                NeuralStepBadge(
                    title = "ذكاء Gemini",
                    isActive = uiState.status == BrainStatus.GEMINI_REASONING,
                    isDone = uiState.status.ordinal > BrainStatus.GEMINI_REASONING.ordinal
                )
                NeuralStepBadge(
                    title = "إخراج FFmpeg",
                    isActive = uiState.status == BrainStatus.SYNTHESIZING_DIRECTIVES,
                    isDone = uiState.status == BrainStatus.SUCCESS
                )
            }

            // الشريط السفلي: إحصائيات الذاكرة السحابية
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151B2B), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Styles",
                        tint = if (SupabaseServices.isSupabaseAvailable) Color(0xFF10B981) else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (SupabaseServices.isSupabaseAvailable) 
                            "ذاكرة Supabase: ${uiState.activeStyleObjectsCount} كائن نمط نشط"
                        else 
                            "الذاكرة المحلية: ${uiState.activeStyleObjectsCount} نمط نشط",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "نقاء الهوية: 98%",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun NeuralStepBadge(
    title: String,
    isActive: Boolean,
    isDone: Boolean
) {
    val bgColor = when {
        isActive -> GoldPrimary.copy(alpha = 0.20f)
        isDone -> Color(0xFF10B981).copy(alpha = 0.15f)
        else -> Color(0xFF1E293B).copy(alpha = 0.40f)
    }

    val textColor = when {
        isActive -> GoldPrimary
        isDone -> Color(0xFF34D399)
        else -> TextSecondary.copy(alpha = 0.6f)
    }

    val borderColor = when {
        isActive -> GoldPrimary
        isDone -> Color(0xFF10B981)
        else -> Color.Transparent
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isDone) {
                Icon(Icons.Default.Check, contentDescription = null, tint = textColor, modifier = Modifier.size(10.dp))
            }
            Text(
                text = title,
                color = textColor,
                fontSize = 9.5.sp,
                fontFamily = CairoFont,
                fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun getStatusSubheading(status: BrainStatus): String {
    return when (status) {
        BrainStatus.IDLE -> "جاهز لمعالجة الأفكار والأنماط الإخراجية"
        BrainStatus.FETCHING_MEMORY -> "استرجاع الأنماط المرجعية من سحابة Supabase"
        BrainStatus.ANALYZING_IDEA -> "تحليل الفكرة واستخراج الدلالات الإيمانية"
        BrainStatus.MATCHING_STYLE -> "مطابقة الأسلوب الفني وصهر موازين الهوية"
        BrainStatus.GEMINI_REASONING -> "استدلال Gemini الذكي الموجه بالذاكرة"
        BrainStatus.SYNTHESIZING_DIRECTIVES -> "توليد مصفوفة فلاتر FFmpeg والتيبوغرافيا"
        BrainStatus.SUCCESS -> "اكتمل القرار الإخراجي بنجاح تام"
        BrainStatus.ERROR -> "تفعيل التدهور الأنيق لضمان استمرار الإنتاج"
    }
}
