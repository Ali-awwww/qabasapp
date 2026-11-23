package com.qabas.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*

/**
 * 📜 شاشة القواعد والسياسات الموحدة (Qabas Rules & Governance Center)
 * بتصميم: Apple Human Interface Guidelines (Clean Light & Dark)
 * يضمن الشفافية الصارمة، تحريم الكذب وتزييف البيانات، حوكمة الذكاء الاصطناعي، وسلامة المجتمع.
 */
@Composable
fun QabasRulesScreen(
    onBack: () -> Unit
) {
    BackHandler {
        AppSoundManager.playClick()
        onBack()
    }

    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = remember { context.getSharedPreferences("qabas_rules_prefs", Context.MODE_PRIVATE) }

    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var acknowledgedRules by remember {
        mutableStateOf(prefs.getStringSet("acknowledged_rules_set", emptySet()) ?: emptySet())
    }

    val allRules = remember { QabasRulesDatabase.loadAllRules() }

    val filteredRules = remember(selectedCategory, searchQuery) {
        allRules.filter { rule ->
            (selectedCategory == "ALL" || rule.category == selectedCategory) &&
            (searchQuery.isBlank() || rule.title.contains(searchQuery, ignoreCase = true) || rule.description.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            AppleTopBar(
                title = "القواعد والسياسات",
                subtitle = "ميثاق النزاهة الشرعية وحوكمة الذكاء الاصطناعي",
                onBack = onBack
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // شريط البحث
            AppleSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "البحث في القواعد والسياسات...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // شريط تصنيفات Apple
            RulesCategoryTabs(
                selectedCategory = selectedCategory,
                onSelectCategory = { cat ->
                    AppSoundManager.playTab()
                    selectedCategory = cat
                }
            )

            // شريط معلومات الالتزام
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = theme.success,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تم الإقرار: ${acknowledgedRules.size} من ${allRules.size} قاعدة",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = theme.success.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (acknowledgedRules.size == allRules.size) "ملتزم 100% ✅" else "قيد المراجعة",
                        color = theme.success,
                        fontSize = 10.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // قائمة القواعد
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                // بطاقة ميثاق الشفافية والصدق المركزي
                item {
                    TruthAndIntegrityPledgeCard()
                }

                itemsIndexed(filteredRules, key = { _, r -> r.id }) { index, rule ->
                    val isAcknowledged = acknowledgedRules.contains(rule.id)
                    AppleRuleItemCard(
                        rule = rule,
                        index = index + 1,
                        isAcknowledged = isAcknowledged,
                        onAcknowledge = {
                            AppSoundManager.playSuccess()
                            val newSet = acknowledgedRules + rule.id
                            acknowledgedRules = newSet
                            prefs.edit().putStringSet("acknowledged_rules_set", newSet).apply()
                        }
                    )
                }

                // بطاقة الدعم والتبليغ
                item {
                    RulesFooterCard(
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

@Composable
private fun RulesCategoryTabs(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val theme = QabasTheme.current
    val categories = listOf(
        "ALL" to "كافة القواعد",
        "INTEGRITY" to "الصدق والأمانة",
        "AI_GOVERNANCE" to "حوكمة الذكاء",
        "PRIVACY" to "الخصوصية",
        "COMMUNITY" to "آداب المجتمع"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(categories) { (id, title) ->
            val isSelected = selectedCategory == id
            Surface(
                color = if (isSelected) theme.primary else theme.cardSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    0.5.dp,
                    if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)
                ),
                modifier = Modifier.clickable { onSelectCategory(id) }
            ) {
                Text(
                    text = title,
                    color = if (isSelected) theme.onPrimary else theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun TruthAndIntegrityPledgeCard() {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = theme.cardSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ميثاق النزاهة والصدق الشرعي والتقني 🛡️",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = "تحريم قاطع لتزييف البيانات أو تضليل المستخدم",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "«يُحرم تحريماً قاطعاً الكذب، أو التضليل، أو توليد بيانات وهمية غير دقيقة داخل خادم المسلم. كافة التفاعلات، الإحصائيات، الحسابات البرمجية، والمحتوى القرآني والحديثي تخضع لرقابة ومطابقة تامة وصادقة 100% حفاظاً على الأمانة والنزاهة الإلهية».",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AppleRuleItemCard(
    rule: QabasRule,
    index: Int,
    isAcknowledged: Boolean,
    onAcknowledge: () -> Unit
) {
    val theme = QabasTheme.current
    var isExpanded by remember { mutableStateOf(false) }

    val accent = when (rule.category) {
        "INTEGRITY" -> theme.warning
        "AI_GOVERNANCE" -> theme.primary
        "PRIVACY" -> theme.success
        else -> theme.accent
    }

    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        cornerRadius = 14.dp,
        containerColor = theme.cardSurface
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
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#$index",
                            color = accent,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = rule.title,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = theme.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = rule.description,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📌 تفاصيل التنفيذ والالتزام:",
                                color = accent,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = rule.enforcementDetails,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp
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
                            onClick = onAcknowledge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAcknowledged) theme.success else accent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        ) {
                            Icon(
                                imageVector = if (isAcknowledged) Icons.Default.Check else Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAcknowledged) "تم الاطلاع والموافقة ✅" else "أوافق وألتزم 📜",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesFooterCard(onContactSupport: () -> Unit) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = theme.cardSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "هل لديك استفسار أو بلاغ قانوني أو شرعي؟",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "فريق الحوكمة الشرعية والتقنية يراجع كافة البلاغات على مدار الساعة بكل نزاهة وأمانة.",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onContactSupport,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, theme.primary),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text(
                    text = "مراسلة المطور وفريق الحوكمة 💬",
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
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
            title = "حماية البيانات والتشفير (Zero-Knowledge Privacy)",
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
            description = "مجتمع خادم المسلم مجتمع إيماني بنّاء يهدف لنشر الخير، وتبادل الخبرات، وتشجيع المبدعين في خدمة رسالة الإسلام.",
            enforcementDetails = "يُمنع التراشق أو الإساءة في التقييمات، وتخضع التعليقات السحابية لنظام فلترة ذكي لحفظ نقاء المنصة وروحها الأخوية.",
            category = "COMMUNITY"
        )
    )
}
