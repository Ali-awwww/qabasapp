package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    SpiritualReaction("subhanallah", "سبحان الله", "💫"),
    SpiritualReaction("mashallah", "ما شاء الله", "🌟"),
    SpiritualReaction("alhamdulillah", "الحمد لله", "🌿"),
    SpiritualReaction("jazakallah", "أجر لي ولك", "🤍"),
    SpiritualReaction("ameen", "آمين يا رب", "🤲")
)

@Composable
fun SpiritualReactionsBar(
    onReactionTriggered: (SpiritualReaction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(SPIRITUAL_REACTIONS) { reaction ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                modifier = Modifier.clickable {
                    onReactionTriggered(reaction)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(text = reaction.icon, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reaction.text,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
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
    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            key(particle.id) {
                var animProgress by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(particle.id) {
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
                    ) { value, _ ->
                        animProgress = value
                    }
                    onParticleFinished(particle.id)
                }

                val currentY = particle.startY - (animProgress * 400.dp.value)
                val alpha = (1f - animProgress).coerceIn(0f, 1f)
                val scale = 0.8f + (animProgress * 0.4f)

                Box(
                    modifier = Modifier
                        .offset(x = particle.offsetX.dp, y = currentY.dp)
                        .scale(scale)
                        .alpha(alpha)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GoldPrimary.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, Color.White),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = particle.text,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DeepSlate
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+10 حسنات 🌟",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = DeepSlate
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * صندوق التحدي والتدبر التفاعلي (Interactive Quranic Tadabbur Quiz Card)
 */
@Composable
fun InteractiveTadabburCard(
    reelTitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAnswered by remember { mutableStateOf(false) }
    var selectedOptionIndex by remember { mutableIntStateOf(-1) }

    val question = "ما الحكمة الإيمانية الأبرز المستفادة من هذا المقطع المبارك؟"
    val options = listOf(
        "اليقين بوعد الله وحسن التوكل عليه 🌿",
        "لزوم الاستغفار وذكر الله في كل حين 🤍",
        "التدبر في عظمة خلق الله وبديع صنعه 🌌"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "وقفة تدبر ومسار حسنات 💡",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = GoldPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = question,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            options.forEachIndexed { index, opt ->
                val isSelected = selectedOptionIndex == index
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) GoldPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            selectedOptionIndex = index
                            isAnswered = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAnswered && isSelected) "✅" else "🔹",
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = opt,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            if (isAnswered) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "بارك الله فيك! تم تسجيل تأملك وإضافة +20 حسنة إلى رصيدك الإيماني 🌟",
                    fontFamily = CairoFont,
                    fontSize = 10.5.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
