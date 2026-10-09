package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.qabas.app.ui.components.GlassmorphicSurface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

data class CovenantRule(
    val number: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: String
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var isAgreed by remember { mutableStateOf(true) }

    val covenantRules = remember {
        listOf(
            CovenantRule(
                number = "01",
                title = "إخلاص القصد ونفع الأمة",
                description = "جعل صناعة المحتوى سبيلاً لنشر الفضيلة وتزكية النفوس وبناء الوعي الراشد، بعيداً عن الابتذال والتفاهة.",
                icon = Icons.Default.VolunteerActivism,
                badge = "PURITY"
            ),
            CovenantRule(
                number = "02",
                title = "النقاء الصوتي التام",
                description = "الاعتماد الحصري على التلاوات القرآنية الندية، والأناشيد العذبة بدون معازف، والمؤثرات الصوتية الطبيعية.",
                icon = Icons.Default.GraphicEq,
                badge = "VOICE"
            ),
            CovenantRule(
                number = "03",
                title = "صون الحياء والوقار",
                description = "الالتزام بالحشمة والسمت الإسلامي، واجتناب اللقطات الخادشة، وتجريد الرموز المولدة بالذكاء الاصطناعي.",
                icon = Icons.Default.Security,
                badge = "HAYA"
            ),
            CovenantRule(
                number = "04",
                title = "التوثيق والأمانة",
                description = "عزو الآيات القرآنية بدقة متناهية، والاعتماد الحصري على الأحاديث الصحيحة والحسنة.",
                icon = Icons.Default.MenuBook,
                badge = "TRUST"
            ),
            CovenantRule(
                number = "05",
                title = "الخطاب الرصين",
                description = "ترسيخ قيم الرحمة والتوبة والأمل، واجتناب السباب، والتكفير، والجدل العقيم، وإثارة الشائعات.",
                icon = Icons.Default.Diversity3,
                badge = "UNITY"
            )
        )
    }

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    val infiniteTransition = rememberInfiniteTransition(label = "covenantPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
    ) {
        // 1. Background Grid HUD
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val step = 50.dp.toPx()
            for (i in 0..(w / step).toInt()) {
                drawLine(primaryColor.copy(alpha = 0.02f), Offset(i * step, 0f), Offset(i * step, h), 1f)
            }
            for (i in 0..(h / step).toInt()) {
                drawLine(primaryColor.copy(alpha = 0.02f), Offset(0f, i * step), Offset(w, i * step), 1f)
            }
        }

        // 2. Corner Decors
        HudCornerDecor(modifier = Modifier.fillMaxSize(), color = primaryColor.copy(alpha = 0.2f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Brand Header Badge (Geometric)
            Surface(
                color = primaryColor.copy(alpha = 0.05f),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(primaryColor.copy(alpha = pulseAlpha), CircleShape))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "COVENANT PROTOCOL // QABAS 2.5",
                        color = primaryColor,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ميثاق صُناع الأثر",
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "عهـد وأمانـة لنشـر الـهدى والـنـور برؤية عالمية",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Scrollable Rules Container (Glassmorphic)
            GlassmorphicSurface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)),
                        shape = CutCornerShape(16.dp)
                    ),
                shape = CutCornerShape(16.dp),
                backgroundColor = CardSurface.copy(alpha = 0.6f),
                borderColor = Color.White.copy(alpha = 0.05f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    covenantRules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), CutCornerShape(8.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.05f), CutCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Number Badge (Minimalist)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = rule.number,
                                    color = primaryColor,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                                Box(modifier = Modifier.size(12.dp, 1.dp).background(primaryColor.copy(alpha = 0.4f)))
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rule.title,
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "[ ${rule.badge} ]",
                                        color = accentColor.copy(alpha = 0.8f),
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = rule.description,
                                    color = TextSecondary.copy(alpha = 0.8f),
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pledge Checkbox (Linear Minimalist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (isAgreed) primaryColor.copy(alpha = 0.05f) else Color.Transparent, CutCornerShape(8.dp))
                    .border(1.dp, if (isAgreed) primaryColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f), CutCornerShape(8.dp))
                    .clickable { isAgreed = !isAgreed }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.5.dp, if (isAgreed) primaryColor else Color.Gray, CutCornerShape(4.dp))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAgreed) {
                        Box(modifier = Modifier.fillMaxSize().background(primaryColor, CutCornerShape(2.dp)))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "أقر وأتعهد بالالتزام بهذا الميثاق بروح إبداعية مسؤولة",
                    color = if (isAgreed) Color.White else Color.Gray,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button (Geometric Solid)
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("has_seen_onboarding", true)
                        .putBoolean("has_seen_welcome_rules", true)
                        .putBoolean("has_accepted_covenant", true)
                        .apply()
                    onFinish()
                },
                enabled = isAgreed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = DeepSlate,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.Gray
                ),
                shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "تفعيل بروتوكول الإنتاج",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

