package com.qabas.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.ApplePrimaryButton
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
    val theme = QabasTheme.current
    val context = LocalContext.current
    var isAgreed by remember { mutableStateOf(false) }

    val covenantRules = remember {
        listOf(
            CovenantRule(
                number = "01",
                title = "إخلاص القصد ونفع الأمة",
                description = "جعل صناعة المحتوى سبيلاً لنشر الفضيلة وتزكية النفوس وبناء الوعي الراشد، بعيداً عن الابتذال والتفاهة.",
                icon = Icons.Default.VolunteerActivism,
                badge = "الإخلاص"
            ),
            CovenantRule(
                number = "02",
                title = "النقاء الصوتي التام",
                description = "الاعتماد الحصري على التلاوات القرآنية الندية، والأناشيد العذبة بدون معازف، والمؤثرات الصوتية الطبيعية.",
                icon = Icons.Default.GraphicEq,
                badge = "النقاء"
            ),
            CovenantRule(
                number = "03",
                title = "صون الحياء والوقار",
                description = "الالتزام بالحشمة والسمت الإسلامي، واجتناب اللقطات الخادشة، وتجريد الرموز المولدة بالذكاء الاصطناعي.",
                icon = Icons.Default.Security,
                badge = "الوقار"
            ),
            CovenantRule(
                number = "04",
                title = "التوثيق والأمانة",
                description = "عزو الآيات القرآنية بدقة متناهية، والاعتماد الحصري على الأحاديث الصحيحة والحسنة.",
                icon = Icons.Default.MenuBook,
                badge = "الأمانة"
            ),
            CovenantRule(
                number = "05",
                title = "الخطاب الرصين",
                description = "ترسيخ قيم الرحمة والتوبة والأمل، واجتناب السباب، والتكفير، والجدل العقيم، وإثارة الشائعات.",
                icon = Icons.Default.Diversity3,
                badge = "الحكمة"
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                color = theme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ميثاق قبس الأخلاقي",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ميثاق صُناع الأثر",
                color = theme.textPrimary,
                fontSize = 26.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "عهد وأمانة لنشر الهدى والنور برؤية راشدة",
                color = theme.textSecondary,
                fontSize = 13.sp,
                fontFamily = NotoSansFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            AppleCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                cornerRadius = 18.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    covenantRules.forEachIndexed { idx, rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(rule.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${rule.number}. ${rule.title}",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Surface(
                                        color = theme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = rule.badge,
                                            color = theme.primary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = rule.description,
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                        if (idx < covenantRules.lastIndex) {
                            AppleInsetDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppleCard(cornerRadius = 14.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = isAgreed, role = Role.Checkbox, onValueChange = { isAgreed = it })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAgreed,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = theme.primary,
                            uncheckedColor = theme.textSecondary,
                            checkmarkColor = theme.onPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "أقر وأتعهد بالالتزام بهذا الميثاق بروح إبداعية مسؤولة",
                        color = if (isAgreed) theme.textPrimary else theme.textSecondary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            ApplePrimaryButton(
                text = "موافقة والبدء الآن",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                enabled = isAgreed,
                onClick = {
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("has_seen_onboarding", true)
                        .putBoolean("has_seen_welcome_rules", true)
                        .putBoolean("has_accepted_covenant", true)
                        .apply()
                    onFinish()
                }
            )

            if (!isAgreed) {
                Text(
                    text = "حدد مربع الإقرار للمتابعة",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
