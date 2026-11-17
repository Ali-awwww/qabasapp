package com.qabas.app

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme

/**
 * التفاعلات الروحية الحية ونثر الحسنات (Spiritual Quick Reactions & Hasanat Burst)
 */
data class SpiritualReaction(
    val id: String,
    val text: String,
    val icon: String,
    val rewardHasanat: Int = 10
)

data class FloatingParticle(
    val id: Long,
    val text: String,
    val offsetX: Float,
    val startY: Float
)

val SPIRITUAL_REACTIONS = listOf(
    SpiritualReaction("subhanallah", "سبحان الله", "✦"),
    SpiritualReaction("mashallah", "ما شاء الله", "✧"),
    SpiritualReaction("alhamdulillah", "الحمد لله", "❖"),
    SpiritualReaction("jazakallah", "أجر لي ولك", "♡"),
    SpiritualReaction("ameen", "آمين يا رب", "◈")
)

@Composable
fun SpiritualReactionsBar(
    onReactionTriggered: (SpiritualReaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(SPIRITUAL_REACTIONS) { reaction ->
            Surface(
                onClick = { onReactionTriggered(reaction) },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xB81C1C1E),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.heightIn(min = 38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = reaction.icon,
                        fontSize = 12.sp,
                        color = theme.primary
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = reaction.text,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * طبقة الجزيئات والتفاعلات الطافية (Floating Particles Layer)
 */
@Composable
fun FloatingParticlesLayer(
    particles: List<FloatingParticle>,
    onParticleFinished: (Long) -> Unit
) {
    val theme = QabasTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            key(particle.id) {
                var animProgress by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(particle.id) {
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 1900,
                            easing = LinearOutSlowInEasing
                        )
                    ) { value, _ ->
                        animProgress = value
                    }
                    onParticleFinished(particle.id)
                }

                val currentY = particle.startY - (animProgress * 360.dp.value)
                val alpha = (1f - animProgress).coerceIn(0f, 1f)
                val scale = 0.85f + (animProgress * 0.3f)

                Box(
                    modifier = Modifier
                        .offset(x = particle.offsetX.dp, y = currentY.dp)
                        .scale(scale)
                        .alpha(alpha)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = theme.primary.copy(alpha = 0.94f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.6f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = particle.text,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = theme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+10 حسنات",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = theme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * بطاقة التدبر التفاعلية (Interactive Quranic Tadabbur Quiz Card)
 */
@Composable
fun InteractiveTadabburCard(
    reelTitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    var isAnswered by remember { mutableStateOf(false) }
    var selectedOptionIndex by remember { mutableIntStateOf(-1) }

    val question = "ما الحكمة الإيمانية الأبرز المستفادة من هذا المقطع المبارك؟"
    val options = listOf(
        "اليقين بوعد الله وحسن التوكل عليه",
        "لزوم الاستغفار وذكر الله في كل حين",
        "التدبر في عظمة خلق الله وبديع صنعه"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = theme.cardSurface.copy(alpha = 0.96f),
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.55f)),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "وقفة تدبر",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.primary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = question,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = theme.textPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            options.forEachIndexed { index, opt ->
                val isSelected = selectedOptionIndex == index
                Surface(
                    onClick = {
                        selectedOptionIndex = index
                        isAnswered = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                    border = BorderStroke(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) theme.primary else theme.separator.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAnswered && isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) theme.primary else theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = opt,
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) theme.textPrimary else theme.textSecondary
                        )
                    }
                }
            }

            if (isAnswered) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "بارك الله فيك! تم تسجيل تأملك في هذه الوقفة (+20 حسنة)",
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    color = theme.success,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
