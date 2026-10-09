package com.qabas.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlin.math.sin

/**
 * 📜 شاشة القواعد والسياسات الموحدة (Qabas Spatial HUD Rules & Governance Center)
 * بتصميم: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * يضمن الشفافية الصارمة، تحريم الكذب وتزييف البيانات، حوكمة الذكاء الاصطناعي، وسلامة المجتمع.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QabasRulesScreen(
    onBack: () -> Unit
) {
    BackHandler {
        AppSoundManager.playClick()
        onBack()
    }

    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var acceptedRulesCount by remember { mutableIntStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "RulesHudTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseGlow"
    )

    val allRules = remember { QabasRulesDatabase.loadAllRules() }

    val filteredRules = remember(selectedCategory, searchQuery) {
        allRules.filter { rule ->
            (selectedCategory == "ALL" || rule.category == selectedCategory) &&
            (searchQuery.isBlank() || rule.title.contains(searchQuery) || rule.description.contains(searchQuery))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .drawBehind {
                // رسم شبكة القيادة السيبرانية الدقيقة (Spatial Cyber Mesh)
                val gridSpacing = 44.dp.toPx()
                val gridColor = Color(0xFF06B6D4).copy(alpha = 0.035f)
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += gridSpacing
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += gridSpacing
                }

                // هالات توهج كوانتية (Cyan Top / Gold Bottom)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.85f, 70.dp.toPx()),
                        radius = 280.dp.toPx()
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height - 100.dp.toPx()),
                        radius = 320.dp.toPx()
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ══════════════════════════════════════════════════════════
            // 1. ترويسة القيادة الفضائية (Spatial HUD Header)
            // ══════════════════════════════════════════════════════════
            SpatialRulesHeader(
                pulseGlow = pulseGlow,
                onBack = {
                    AppSoundManager.playClick()
                    onBack()
                }
            )

            // ══════════════════════════════════════════════════════════
            // 2. شريط التيليمتري النبضي (Telemetry Ribbon)
            // ══════════════════════════════════════════════════════════
            SpatialRulesTelemetryRibbon(pulseGlow = pulseGlow)

            // ══════════════════════════════════════════════════════════
            // 3. شريط الفئات الكوانتية التفاعلية (Category Filter Tabs)
            // ══════════════════════════════════════════════════════════
            SpatialRulesCategoryTabs(
                selectedCategory = selectedCategory,
                onSelectCategory = { cat ->
                    AppSoundManager.playTab()
                    selectedCategory = cat
                }
            )

            // ══════════════════════════════════════════════════════════
            // 4. قائمة القواعد والسياسات الفضائية (Rules List)
            // ══════════════════════════════════════════════════════════
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
            ) {
                // بطاقة ميثاق الشفافية والصدق المركزي
                item {
                    TruthAndIntegrityPledgeCard(pulseGlow = pulseGlow)
                }

                itemsIndexed(filteredRules, key = { _, r -> r.id }) { index, rule ->
                    SpatialRuleItemCard(
                        rule = rule,
                        index = index + 1,
                        pulseGlow = pulseGlow,
                        onAcknowledge = {
                            AppSoundManager.playSuccess()
                            acceptedRulesCount++
                        }
                    )
                }

                // بطاقة الدعم والتبليغ القانوني
                item {
                    SpatialRulesFooterCard(
                        onContactSupport = {
                            AppSoundManager.playClick()
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// المكونات الفرعية الفضائية (Spatial HUD Components)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SpatialRulesHeader(
    pulseGlow: Float,
    onBack: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.82f),
        borderColor = Color(0xFF06B6D4).copy(alpha = 0.32f),
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.5.dp, Color(0xFF06B6D4).copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Gavel,
                    contentDescription = null,
                    tint = Color(0xFF06B6D4),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GOVERNANCE // القواعد والسياسات",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                    ) {
                        Text(
                            "VERIFIED",
                            color = Color(0xFF10B981),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    "ميثاق النزاهة الشرعية، الشفافية، وحوكمة الذكاء الاصطناعي v2.4",
                    color = Color(0xFF94A3B8),
                    fontFamily = CairoFont,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SpatialRulesTelemetryRibbon(pulseGlow: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(width = 36.dp, height = 10.dp)) {
                val midY = size.height / 2f
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, midY)
                path.lineTo(size.width * 0.35f, midY)
                path.lineTo(size.width * 0.5f, midY - 4.dp.toPx() * pulseGlow)
                path.lineTo(size.width * 0.65f, midY + 4.dp.toPx() * pulseGlow)
                path.lineTo(size.width, midY)
                drawPath(
                    path = path,
                    color = Color(0xFF06B6D4),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "SYSTEM_ETHICS: ZERO_TOLERANCE // TRUTH_ENGINE_ACTIVE",
                color = Color(0xFF10B981),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            "COMPLIANCE: 100%",
            color = Color(0xFF64748B),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp
        )
    }
}

@Composable
private fun SpatialRulesCategoryTabs(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf(
        "ALL" to "🌟 كافة القواعد",
        "INTEGRITY" to "🛡️ ميثاق الصدق والأمانة",
        "AI_GOVERNANCE" to "🤖 حوكمة الذكاء الاصطناعي",
        "PRIVACY" to "🔒 الخصوصية والبيانات",
        "COMMUNITY" to "👥 آداب المحتوى والمجتمع"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(categories) { (id, title) ->
            val isSelected = selectedCategory == id
            Surface(
                color = if (isSelected) Color(0xFF06B6D4).copy(alpha = 0.20f) else Color(0xFF0F172A).copy(alpha = 0.75f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF06B6D4) else Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier.clickable { onSelectCategory(id) }
            ) {
                Text(
                    text = title,
                    color = if (isSelected) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                    fontFamily = CairoFont,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * بطاقة ميثاق النزاهة والصدق (Truth & Integrity Pledge)
 */
@Composable
private fun TruthAndIntegrityPledgeCard(pulseGlow: Float) {
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF0D2338).copy(alpha = 0.85f),
        borderColor = Color(0xFF06B6D4).copy(alpha = 0.45f),
        borderWidth = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF06B6D4).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "ميثاق النزاهة الإيمانية والتقنية الصارمة 🛡️",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "تحريم مطلق للكذب وتزييف البيانات البرمجية أو النصوص الدينية",
                        color = Color(0xFF22D3EE),
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "«يُحرم تحريماً قاطعاً الكذب، أو التضليل، أو توليد بيانات وهمية غير دقيقة داخل قبس. كافة التفاعلات، الإحصائيات، الحسابات البرمجية، والمحتوى القرآني والحديثي تخضع لرقابة ومطابقة تامة وصادقة 100% حفاظاً على الأمانة والنزاهة الإلهية».",
                color = Color(0xFFE2E8F0),
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * بطاقة القاعدة المنفردة (Spatial Rule Item Card)
 */
@Composable
private fun SpatialRuleItemCard(
    rule: QabasRule,
    index: Int,
    pulseGlow: Float,
    onAcknowledge: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isAcknowledged by remember { mutableStateOf(false) }

    val accent = when (rule.category) {
        "INTEGRITY" -> GoldPrimary
        "AI_GOVERNANCE" -> Color(0xFF06B6D4)
        "PRIVACY" -> Color(0xFF10B981)
        else -> Color(0xFFA855F7)
    }

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.80f),
        borderColor = accent.copy(alpha = 0.35f),
        borderWidth = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.7.dp, accent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "RULE_#$index",
                            color = accent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = rule.title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = rule.description,
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = Color(0xFF070B14).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.6.dp, accent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "📌 تفاصيل التنفيذ والالتزام:",
                            color = accent,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = rule.enforcementDetails,
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isAcknowledged = true
                            onAcknowledge()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAcknowledged) Color(0xFF10B981) else accent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            if (isAcknowledged) Icons.Default.Check else Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = DeepSlate,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isAcknowledged) "تم الاطلاع والموافقة ✅" else "أوافق وألتزم 📜",
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialRulesFooterCard(onContactSupport: () -> Unit) {
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.7f),
        borderColor = Color.White.copy(alpha = 0.12f),
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "هل لديك استفسار أو بلاغ قانوني أو شرعي؟",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "فريق الحوكمة الشرعية والتقنية يراجع كافة البلاغات على مدار الساعة بكل نزاهة وأمانة.",
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onContactSupport,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GoldPrimary),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    "مراسلة المطور وفريق الحوكمة 💬",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// قاعدة بيانات القواعد والسياسات (Rules Model & Data)
// ═══════════════════════════════════════════════════════════════════

data class QabasRule(
    val id: String,
    val title: String,
    val description: String,
    val enforcementDetails: String,
    val category: String
)

object QabasRulesDatabase {
    fun loadAllRules(): List<QabasRule> = listOf(
        QabasRule(
            id = "rule_truth_integrity",
            title = "تحريم الكذب وتزييف البيانات (Truth & Honesty Guarantee)",
            description = "يُمنع منعاً باتاً تزييف البيانات البرمجية، إيهام المستخدم بإنجاز لم يتحقق، أو إنشاء بيانات وهمية مضللة أثناء المعالجة.",
            enforcementDetails = "تلتزم المنظومة بالصدق التام، وعرض أخطاء المعالجة بشفافية، وعدم تزييف نتائج الذكاء الاصطناعي، ومحو أي بيانات اختبارية تجريبية لحفظ سلامة بيئة النظام.",
            category = "INTEGRITY"
        ),
        QabasRule(
            id = "rule_quran_accuracy",
            title = "الأمانة الشرعية والتدقيق القرآني المصحفي",
            description = "وجوب مطابقة كافة الآيات القرآنية والأحاديث النبوية برسم المصحف العثماني وقواعد التجويد المعتمدة.",
            enforcementDetails = "تمر النصوص الدينية عبر محرك ContentGuard للفحص الآلي ضد مصحف مجمع الملك فهد، ويُحظر أي تحريف أو تصرف في نصوص الوحيين.",
            category = "INTEGRITY"
        ),
        QabasRule(
            id = "rule_ai_governance",
            title = "حوكمة الذكاء الاصطناعي وضوابط المحتوى الهادف",
            description = "استخدام نماذج الذكاء الاصطناعي مقيد بصناعة المحتوى الإيماني، الثقافي، والتعليمي الهادف فقط.",
            enforcementDetails = "يُحظر استخدام الاستوديو لتوليد محتوى مسيء، هابط، يروج للمحرمات، أو يحرض على العنف والكراهية. أي انتهاك يؤدي لتجميد الحساب الفوري.",
            category = "AI_GOVERNANCE"
        ),
        QabasRule(
            id = "rule_cloud_security",
            title = "حماية البيانات والتشفير الكوانتي (Zero-Knowledge Privacy)",
            description = "كافة مشاريع المستخدم، تسجيلاته الصوتية، ومحادثاته مع المطور مشفرة سحابياً ومحمية بسياسات RLS الصارمة.",
            enforcementDetails = "لا يحق لأي طرف ثالث الاطلاع على وسائطك، وتُدار الجلسات عبر مفاتيح JWT مشفرة ببروتوكول AES-256 مع إمكانية حذف البيانات نهائياً بطلب المستخدم.",
            category = "PRIVACY"
        ),
        QabasRule(
            id = "rule_bug_transparency",
            title = "الإصلاح الفوري للعيوب والالتزام بالإتقان (Quality Assurance)",
            description = "في حال رصد أي عيب برمجي أو قصور في تجربة الاستخدام، يلتزم فريق التطوير بمعالجته فوراً حتى تمام 100%.",
            enforcementDetails = "يتم تسجيل الانهيارات عبر CrashGuard، وتوجيه بلاغات المستخدمين لقناة الفحص الفوري (DevChat) لضمان بيئة مستقرة وعالية الجودة.",
            category = "COMMUNITY"
        ),
        QabasRule(
            id = "rule_community_ethics",
            title = "آداب المجتمع والتعاون الإيجابي",
            description = "مجتمع قبس مجتمع إيماني بنّاء يهدف لنشر الخير، وتبادل الخبرات، وتشجيع المبدعين في خدمة رسالة الإسلام.",
            enforcementDetails = "يُمنع التراشق أو الإساءة في التقييمات، وتخضع التعليقات السحابية لنظام فلترة ذكي لحفظ نقاء المنصة وروحها الأخوية.",
            category = "COMMUNITY"
        )
    )
}
