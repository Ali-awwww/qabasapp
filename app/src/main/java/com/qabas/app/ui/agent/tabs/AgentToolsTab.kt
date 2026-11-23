package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*


/**
 * ⚙️ بطاقة إعدادات نواة الوكيل: النموذج المستهدف، حد الجولات، والإصلاح الذاتي.
 */
@Composable
internal fun AgentKernelSettingsCard(context: Context) {
    val theme = QabasTheme.current
    val prefs = com.qabas.app.AgentPrefs.instance(context)
    var model by remember { mutableStateOf(AgentPrefs.model(context)) }
    var maxTurns by remember { mutableStateOf(AgentPrefs.maxTurns(context).toFloat()) }
    var autoFix by remember { mutableStateOf(AgentPrefs.isAutoFix(context)) }

    Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.cardSurface.copy(alpha = 0.93f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        PresetPrimary.copy(alpha = 0.6f),
                        theme.primary.copy(alpha = 0.3f),
                        theme.cardBorder.copy(alpha = 0.5f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PresetPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AGENT REASONING PARAMETERS",
                            color = PresetPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Surface(
                        color = PresetPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            "تهيئة النواة ⚙️",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text("نموذج الاستدلال والبرمجة المستهدف:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        null to "⚡ تلقائي الذكي (Auto)",
                        "gemini-2.5-flash" to "💎 Gemini 2.5 Flash (سريع ودقيق)",
                        "llama-3.3-70b-versatile" to "⚡ Groq Llama 3.3 70B (فائق السرعة)",
                        "google/gemma-3-27b-it:free" to "🌊 Gemma 3 (مجاني ومفتوح)"
                    ).forEach { (id, label) ->
                        val isSelected = model == id
                        Surface(
                            color = if (isSelected) theme.success.copy(alpha = 0.18f) else theme.cardSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) theme.success else theme.cardBorder),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    model = id
                                    prefs.edit().putString(AgentPrefs.MODEL, id ?: "").apply()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) theme.success else theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = theme.separator.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الحد الأقصى لجولات التفكير (Max Turns):", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("عدد الجولات المسموح بها للوكيل لإكمال المهمة", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                    Surface(
                        color = theme.success.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, theme.success.copy(alpha = 0.4f))
                    ) {
                        Text(
                            "${maxTurns.toInt()} جولات",
                            color = theme.success,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Slider(
                    value = maxTurns,
                    onValueChange = {
                        maxTurns = it
                        prefs.edit().putInt(AgentPrefs.MAX_TURNS, it.toInt()).apply()
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.success,
                        activeTrackColor = theme.success,
                        inactiveTrackColor = theme.secondarySurface
                    )
                )

                HorizontalDivider(color = theme.separator.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🔧 الإصلاح التلقائي الذكي (Self-Healing)",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (autoFix) theme.success.copy(alpha = 0.12f) else theme.textTertiary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    if (autoFix) "ACTIVE" else "OFF",
                                    color = if (autoFix) theme.success else theme.textTertiary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text("عند حدوث خطأ تجميع، يحلل الوكيل الخطأ ويعيد بناء الكود تلقائياً.", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoFix,
                        onCheckedChange = {
                            autoFix = it
                            prefs.edit().putBoolean(AgentPrefs.AUTO_FIX, it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.success,
                            checkedTrackColor = theme.success.copy(alpha = 0.3f),
                            uncheckedThumbColor = theme.textTertiary,
                            uncheckedTrackColor = theme.secondarySurface
                        )
                    )
                }
            }
        }
}

@Composable
internal fun ToolsTab(context: Context) {
    val theme = QabasTheme.current
    val prefs = com.qabas.app.AgentPrefs.instance(context)
    val dummyCtx = remember { CodeTools.Ctx("-", "-", "-", "-") }
    // (12.6) أدوات الكود (20) وأدوات المحتوى (9) منفصلتان في العرض — كما في
    // CodeTools.kt وAiTools.kt — لا خلط بينهما.
    val codeTools = remember { CodeTools.definitions(context, dummyCtx) }
    val contentTools = remember { AiTools.definitions(context) }
    val allTools = remember { codeTools + contentTools }
    var states by remember { mutableStateOf(allTools.associate { it.name to AgentPrefs.isToolEnabled(context, it.name) }) }
    // (12.16) أداة مدمرة بانتظار تأكيد بشري عبر الحوار أدناه.
    var pendingConfirmTool by remember { mutableStateOf<AiTools.ToolDef?>(null) }

    val arabicNames = mapOf(
        "list_tree" to "📁 تصفح شجرة المستودع",
        "read_file" to "📖 قراءة محتوى ملف",
        "write_file" to "✏️ إنشاء وكتابة ملف جديد",
        "update_file" to "🔄 ترقيع وتحديث ملف موجود",
        "delete_file" to "🗑️ حذف ملف من الفرع",
        "list_branches" to "🌿 قائمة فروع Git",
        "recent_commits" to "📜 سجل الـ Commits الأخيرة",
        "trigger_build" to "🏗️ تشغيل بناء CI/CD في السحابة",
        "build_status" to "📊 متابعة حالة البناء المباشر",
        "create_pr" to "🔀 فتح Pull Request رسمي",
        "list_prs" to "📥 السحوبات المفتوحة",
        "pr_files" to "🔍 فحص ملفات السحب",
        "merge_pr" to "✅ دمج السحب تلقائياً",
        "create_issue" to "🐞 تسجيل مشكلة برمجية (Issue)",
        "list_issues" to "📋 قائمة القضايا المفتوحة",
        "device_status" to "📱 فحص صحة الجهاز",
        "diagnose" to "🩺 التشخيص الهندسي الشامل",
        "generate_script" to "🎬 توليد سيناريو دعوي",
        "generate_image" to "🖼️ توليد صورة",
        "generate_video" to "🎥 توليد فيديو",
        "generate_voice" to "🎙️ توليد تعليق صوتي",
        "publish_to_reels" to "📱 نشر ريلز",
        "search_quran" to "🔍 محرك البحث القرآني",
        "get_tafsir" to "📖 استرجاع التفسير الموثق",
        "search_code_symbols" to "🔍 البحث في أسماء ملفات المشروع",
        "diagnose_build_error" to "🩺 تشخيص أسباب أخطاء التجميع",
        "run_linter_check" to "🧪 فحص توازن الأقواس والسلاسل النصية",
        "generate_unit_test" to "🧪 توليد اختبارات الوحدات الآلية (Unit Tests)",
        "refactor_and_optimize" to "⚡ إرشادات تحسين أداء Compose"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ترسانة الأدوات فقط — إعدادات النواة انتقلت إلى صفحة الإعدادات
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ترسانة الأدوات المتاحة (${allTools.size} أداة):",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "${states.values.count { it }} مفعّلة",
                    color = theme.success,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            ToolsSectionHeader("أدوات الكود — GitHub والبناء (${codeTools.size})")
        }
        items(codeTools) { tool ->
            ToolRow(
                tool = tool,
                arabicName = arabicNames[tool.name] ?: tool.name,
                on = states[tool.name] == true,
                onToggle = {
                    states = states + (tool.name to it)
                    prefs.edit().putBoolean(AgentPrefs.toolKey(tool.name), it).apply()
                },
                onRequestConfirm = { pendingConfirmTool = tool }
            )
        }

        item {
            ToolsSectionHeader("أدوات المحتوى — سكريبت وصور وصوت ونشر (${contentTools.size})")
        }
        items(contentTools) { tool ->
            ToolRow(
                tool = tool,
                arabicName = arabicNames[tool.name] ?: tool.name,
                on = states[tool.name] == true,
                onToggle = {
                    states = states + (tool.name to it)
                    prefs.edit().putBoolean(AgentPrefs.toolKey(tool.name), it).apply()
                },
                onRequestConfirm = { pendingConfirmTool = tool }
            )
        }
    }

    // (12.16) حوار تأكيد حقيقي قبل أي أداة مدمرة: الموافقة لمرة واحدة
    // صالحة 10 دقائق، يستهلكها AgentToolGate عند أول تنفيذ.
    pendingConfirmTool?.let { tool ->
        AlertDialog(
            onDismissRequest = { pendingConfirmTool = null },
            title = { Text("تأكيد أداة مدمرة", fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "الأداة «${tool.name}» مدمرة (${tool.description}).\n" +
                        "التأكيد يسمح بتنفيذ واحد فقط خلال 10 دقائق، ثم يلزم تأكيد جديد. " +
                        "هل أنت متأكد؟",
                    fontFamily = CairoFont
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AgentToolGate.approveDestructive(context, tool.name)
                    pendingConfirmTool = null
                    Toast.makeText(context, "تم التأكيد لمرة واحدة (10 دقائق): ${tool.name}", Toast.LENGTH_SHORT).show()
                }) { Text("تأكيد التنفيذ", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmTool = null }) { Text("إلغاء", fontFamily = CairoFont) }
            }
        )
    }
    }
}

/** عنوان قسم داخل قائمة الأدوات. */
@Composable
private fun ToolsSectionHeader(title: String) {
    val theme = QabasTheme.current
    Text(
        title,
        color = theme.primary,
        fontFamily = CairoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
}

/** صف أداة واحدة: الاسم والوصف ومفتاح التفعيل، وزر تأكيد للمدمرة. */
@Composable
private fun ToolRow(
    tool: AiTools.ToolDef,
    arabicName: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestConfirm: () -> Unit
) {
    val theme = QabasTheme.current
    Surface(
        color = theme.cardSurface.copy(alpha = 0.87f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (on) theme.success.copy(alpha = 0.4f) else theme.cardBorder.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) theme.success.copy(alpha = 0.12f) else theme.secondarySurface.copy(alpha = 0.3f))
                        .border(1.dp, if (on) theme.success.copy(alpha = 0.4f) else theme.cardBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (tool.name.contains("file")) Icons.Default.Description
                        else if (tool.name.contains("pr") || tool.name.contains("branch")) Icons.Default.AltRoute
                        else if (tool.name.contains("build")) Icons.Default.Build
                        else if (tool.name.contains("test")) Icons.Default.Science
                        else Icons.Default.Code,
                        contentDescription = null,
                        tint = if (on) theme.success else theme.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // المعرّف على سطر مستقل كامل العرض: كان في صف مع الاسم
                    // العربي فيضغط فينكمش ويتكسر عمودياً حرفاً حرفاً.
                    Text(
                        arabicName,
                        color = if (on) theme.textPrimary else theme.textTertiary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tool.name,
                        color = theme.textTertiary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    Text(
                        tool.description,
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }

                Switch(
                    checked = on,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.success,
                        checkedTrackColor = theme.success.copy(alpha = 0.3f),
                        uncheckedThumbColor = theme.textTertiary,
                        uncheckedTrackColor = theme.secondarySurface
                    )
                )
            }
            // (12.16) الأدوات المدمرة لا تُنفذ في حلقة LLM إلا بموافقة بشرية
            // مسبقة من هنا — زر التأكيد يفتح حواراً حقيقياً (أعلاه).
            if (AgentToolGate.requiresConfirmation(tool.name)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "أداة مدمرة — تتطلب تأكيداً قبل كل تنفيذ",
                        color = theme.warning,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRequestConfirm) {
                        Text("تأكيد تنفيذ واحد", fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
