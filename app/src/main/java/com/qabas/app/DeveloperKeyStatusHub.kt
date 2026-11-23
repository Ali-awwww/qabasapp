package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class SecretKeySpec(
    val envName: String,
    val displayName: String,
    val provider: String,
    val category: String, // "AI", "MEDIA", "BACKEND", "QURAN", "AUDIO"
    val icon: ImageVector,
    val howItWorks: String,
    val whyNeeded: String,
    val serviceType: String,
    val preferenceKey: String,
    val docUrl: String,
    val keyFormatHint: String = ""
)

/**
 * فحص صارم وواقعي: هل المفتاح مدخل حقيقياً أم مجرد قيمة وهمية/افتراضية؟
 */
fun isKeyConfigured(value: String): Boolean {
    val t = value.trim()
    return t.isNotEmpty() &&
            !t.startsWith("your_", ignoreCase = true) &&
            !t.contains("placeholder", ignoreCase = true) &&
            !t.startsWith("default_", ignoreCase = true) &&
            t != "\"\""
}

/**
 * قائمة جميع المفاتيح الـ 10 المعتمدة في بنية التطبيق — مستوى أعلى
 * (لا داخل المركّب) حتى تستطيع تنبيهات المطور فحص المفاتيح المهيأة
 * حياً (ق24): كان الفحص الحي محبوساً داخل DeveloperKeyStatusHub
 * فلم يصل مدخل تنبيه «فشل مفتاح API» أبداً.
 */
val secretKeySpecs: List<SecretKeySpec> = listOf(
    SecretKeySpec(
        envName = "GEMINI_API_KEY",
        displayName = "Google Gemini 2.5 / Pro",
        provider = "Google AI Studio",
        category = "AI",
        icon = Icons.Default.AutoAwesome,
        howItWorks = "توليد السيناريوهات الدعوية وصياغة خطافات الريلز، والتحليل البصري والتخطيط الإخراجي للمشاهد.",
        whyNeeded = "المحرك الأساسي لكتابة النصوص الإسلامية وفصل المقاطع إلى مشاهد مرئية تفاعلية بذكاء فائق.",
        serviceType = "gemini",
        preferenceKey = "gemini_key",
        docUrl = "https://aistudio.google.com/app/apikey",
        keyFormatHint = "يبدأ بـ AIzaSy..."
    ),
    SecretKeySpec(
        envName = "GROQ_API_KEY",
        displayName = "Groq LPU Ultra-Fast",
        provider = "Groq Cloud",
        category = "AI",
        icon = Icons.Default.Bolt,
        howItWorks = "محرك استدلال فائق السرعة يتخذ قرارات المونتاج اللحظية واقتطاع المشاهد وتوليد العناوين.",
        whyNeeded = "يمنح التطبيق سرعة استجابة خاطفة بدون أي انتظار أثناء تحرير ومراجعة الفيديو.",
        serviceType = "groq",
        preferenceKey = "groq_key",
        docUrl = "https://console.groq.com/keys",
        keyFormatHint = "يبدأ بـ gsk_..."
    ),
    SecretKeySpec(
        envName = "OPENROUTER_API_KEY",
        displayName = "OpenRouter Multi-LLM",
        provider = "OpenRouter",
        category = "AI",
        icon = Icons.Default.AltRoute,
        howItWorks = "بوابة موحدة تتيح الوصول إلى نماذج متعددة بمفتاح واحد، بديل جاهز عند انقطاع مزود آخر.",
        whyNeeded = "يوفر بديلاً جاهزاً عند انقطاع مزود آخر.",
        serviceType = "openrouter",
        preferenceKey = "openrouter_key",
        docUrl = "https://openrouter.ai/keys",
        keyFormatHint = "يبدأ بـ sk-or-v1-..."
    ),
    SecretKeySpec(
        envName = "PIXABAY_API_KEY",
        displayName = "Pixabay Cinematic Assets",
        provider = "Pixabay API",
        category = "MEDIA",
        icon = Icons.Default.Collections,
        howItWorks = "استدعاء لقطات بديلة وخلفيات إسلامية وأنسجة مرئية هادئة مكملة لمشاهد المونتاج.",
        whyNeeded = "مكتبة وسائط ضخمة تثري تنوع المشاهد البصرية في الفيديوهات الطويلة والقصيرة.",
        serviceType = "pixabay",
        preferenceKey = "pixabay_key",
        docUrl = "https://pixabay.com/api/docs/",
        keyFormatHint = "أرقام ومفاتيح من لوحة Pixabay"
    ),
    SecretKeySpec(
        envName = "LEONARDO_API_KEY",
        displayName = "Leonardo.ai",
        provider = "Leonardo.ai",
        category = "MEDIA",
        icon = Icons.Default.Image,
        howItWorks = "توليد صور سينمائية ومشاهد فنية للريلز.",
        whyNeeded = "صور مولّدة بجودة عالية لمشاهد الفيديو.",
        serviceType = "leonardo",
        preferenceKey = "leonardo_api_key",
        docUrl = "https://app.leonardo.ai/api-access",
        keyFormatHint = "مفتاح Leonardo API"
    ),
    SecretKeySpec(
        envName = "QF_CLIENT_ID",
        displayName = "Quran Foundation Client ID",
        provider = "Quran.com / QF",
        category = "QURAN",
        icon = Icons.Default.MenuBook,
        howItWorks = "الربط الرسمي مع واجهات Quran Foundation لتلاوة العفاسي مع توقيت الكلمات آية بآية.",
        whyNeeded = "يتيح تمييز الكلمة الجارية أثناء تلاوة العفاسي — وبلا الزوج تعمل التلاوة من EveryAyah بلا توقيت كلمات.",
        serviceType = "qf",
        preferenceKey = "qf_client_id",
        docUrl = "https://quran.com",
        keyFormatHint = "معرف العميل (Client ID)"
    ),
    SecretKeySpec(
        envName = "QF_CLIENT_SECRET",
        displayName = "Quran Foundation Secret",
        provider = "Quran.com / QF",
        category = "QURAN",
        icon = Icons.Default.VpnKey,
        howItWorks = "التوثيق والتشفير الأمني لجلسات استرجاع توقيتات الكلمات والتلاوات الصوتية آية بآية.",
        whyNeeded = "يتيح مطابقة تلاوة القارئ مع النص القرآني المتزامن كلمة بكلمة في استوديو التجويد.",
        serviceType = "qf",
        preferenceKey = "qf_client_secret",
        docUrl = "https://quran.com",
        keyFormatHint = "المفتاح السري (Client Secret)"
    ),
    SecretKeySpec(
        envName = "SUPABASE_URL",
        displayName = "Supabase Cloud Endpoint",
        provider = "Supabase PostgreSQL",
        category = "BACKEND",
        icon = Icons.Default.CloudQueue,
        howItWorks = "نقطة الاتصال المركزية بقاعدة البيانات ومجلدات التخزين Storage لمزامنة التسجيلات والمشاريع.",
        whyNeeded = "العمود الفقري للسحابة الذي يربط تطبيقك بالسيرفر الحي ويستقبل التسجيلات ومشاريع المستخدمين.",
        serviceType = "supabase",
        preferenceKey = "supabase_url",
        docUrl = "https://supabase.com",
        keyFormatHint = "https://your-id.supabase.co"
    ),
    SecretKeySpec(
        envName = "SUPABASE_ANON_KEY",
        displayName = "Supabase Anon Public Key",
        provider = "Supabase Auth & RLS",
        category = "BACKEND",
        icon = Icons.Default.Security,
        howItWorks = "مفتاح المصادقة العام المشفر الذي يسمح للتطبيق بقراءة وجلب الصوتيات والتسجيلات السحابية بأمان تام.",
        whyNeeded = "يتيح للمستخدمين الاستماع الفوري والتنزيل المباشر للتسجيلات التي يرفعها المطور.",
        serviceType = "supabase",
        preferenceKey = "supabase_anon_key",
        docUrl = "https://supabase.com",
        keyFormatHint = "JWT يبدأ بـ eyJhbGci..."
    ),
    SecretKeySpec(
        envName = "GOOGLE_WEB_CLIENT_ID",
        displayName = "Google Web Client ID (OAuth / Supabase)",
        provider = "Google Cloud Console",
        category = "BACKEND",
        icon = Icons.Default.AccountCircle,
        howItWorks = "معرف العميل لتسجيل الدخول التلقائي بحساب Google وربطه مع Supabase Auth باستخدام Credential Manager.",
        whyNeeded = "يتيح للمستخدمين تسجيل الدخول والمصادقة بزر Google الموحد بنقرة واحدة.",
        serviceType = "google_auth",
        preferenceKey = "google_web_client_id",
        docUrl = "https://supabase.com/dashboard/project/egkgygbvoeevakwkbvfu/auth/providers",
        keyFormatHint = "معرف OAuth Web Client ID"
    )
)

/** جلب القيمة الحقيقية للمفتاح من SharedPreferences أولاً ثم من متغيرات البيئة (مستوى أعلى — ق24). */
fun getSecretKeyActualValue(context: Context, spec: SecretKeySpec): String {
    val sp = AgentPrefs.instance(context)
    val fromSp = sp.getString(spec.preferenceKey, "")?.trim() ?: ""
    if (fromSp.isNotBlank()) return fromSp

    return when (spec.envName) {
        // مفاتيح المزودين لا تُقرأ من BuildConfig أبداً (أمان الدفعة 1):
        // المصدر الوحيد هو ما أدخله المستخدم في الواجهة (يُقرأ أعلاه من SharedPreferences).
        "GEMINI_API_KEY", "GROQ_API_KEY", "OPENROUTER_API_KEY", "PIXABAY_API_KEY",
        "LEONARDO_API_KEY" -> ""
        "QF_CLIENT_ID" -> runCatching { BuildConfig.QF_CLIENT_ID }.getOrDefault("")
        "QF_CLIENT_SECRET" -> runCatching { BuildConfig.QF_CLIENT_SECRET }.getOrDefault("")
        "SUPABASE_URL" -> SupabaseConfig.url
        "SUPABASE_ANON_KEY" -> SupabaseConfig.key
        "GOOGLE_WEB_CLIENT_ID" -> GoogleAuthHelper.getWebClientId(context)
        else -> ""
    }.trim()
}

/** (r17) التلميح الذي يحتاجه فحص كل مفتاح: رابط Supabase لمفتاحه،
 * ومعرف QF عند فحص سره (به يصير الفحص طلب توكن حقيقياً لا فحص
 * وصول). (مستوى أعلى — ق24). */
fun secretKeyHintFor(context: Context, spec: SecretKeySpec): String? = when (spec.envName) {
    "SUPABASE_ANON_KEY" -> getSecretKeyActualValue(context, secretKeySpecs.first { it.envName == "SUPABASE_URL" })
    // ق94: مرآة السابقة — فحص الرابط يحتاج مفتاح Anon معه (كان
    // بلا تلميح فيُفحص الرابط بنفسه كمفتاح فيرد 401 دائماً).
    "SUPABASE_URL" -> getSecretKeyActualValue(context, secretKeySpecs.first { it.envName == "SUPABASE_ANON_KEY" })
    "QF_CLIENT_SECRET" -> getSecretKeyActualValue(context, secretKeySpecs.first { it.envName == "QF_CLIENT_ID" })
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperKeyStatusHub(
    onBack: () -> Unit,
    showTopBar: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val theme = QabasTheme.current

    // قائمة جميع المفاتيح الـ 10 المعتمدة في بنية التطبيق
    // قائمة جميع المفاتيح الـ 10 المعتمدة في بنية التطبيق (مستوى أعلى — انظر secretKeySpecs)
    val secretSpecs = remember { secretKeySpecs }

    // جلب القيمة الحقيقية للمفتاح — يفوَّض إلى getSecretKeyActualValue (مستوى أعلى، ق24)
    fun getKeyActualValue(spec: SecretKeySpec): String = getSecretKeyActualValue(context, spec)

    // حالات الفحص والحوارات
    var validationStatuses by remember { mutableStateOf<Map<String, KeyValidationResult>>(emptyMap()) }
    var isCheckingAll by remember { mutableStateOf(false) }
    var testingKeyEnv by remember { mutableStateOf<String?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var showArchitectureGuide by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var activeEditSpec by remember { mutableStateOf<SecretKeySpec?>(null) }
    var visibleKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    /** (r17) التلميح الذي يحتاجه فحص كل مفتاح: رابط Supabase لمفتاحه،
     * ومعرف QF عند فحص سره (به يصير الفحص طلب توكن حقيقياً لا فحص
     * وصول). تُستخدم في الفحص الجماعي وفي حوار التعديل معاً — السابق
     * كان يمرر التلميح في الفحص الجماعي فقط فكان فحص الحوار أضعف. */
    fun hintFor(spec: SecretKeySpec): String? = secretKeyHintFor(context, spec)

    suspend fun testSingleKey(spec: SecretKeySpec): KeyValidationResult {
        val rawValue = getKeyActualValue(spec)
        return ApiKeyValidator.validateKey(context, spec.serviceType, rawValue, hintFor(spec))
    }

    fun testAllKeys() {
        if (isCheckingAll) return
        isCheckingAll = true
        coroutineScope.launch {
            try {
                val results = mutableMapOf<String, KeyValidationResult>()
                secretSpecs.map { spec ->
                    async(Dispatchers.IO) {
                        val res = testSingleKey(spec)
                        spec.envName to res
                    }
                }.awaitAll().forEach { (k, v) -> results[k] = v }

                validationStatuses = results
                Toast.makeText(context, "اكتمل الفحص الحي الصادق لجميع المفاتيح ⚡", Toast.LENGTH_SHORT).show()
            } finally {
                // ق94: بلا finally كان أي رمي (حتى الإلغاء) يترك زر
                // الفحص معلقاً على «جاري فحص...» بلا نتيجة.
                isCheckingAll = false
            }
        }
    }

    // إحصاءات صادقة تماماً (بلا أي تزييف أو افتراضات وهمية)
    val configuredKeysList = remember(reloadTrigger, secretSpecs) {
        secretSpecs.filter { isKeyConfigured(getKeyActualValue(it)) }
    }
    val configuredCount = configuredKeysList.size
    val totalKeys = secretSpecs.size
    val connectedCount = validationStatuses.values.count { it.isValid }

    // ق94: متوسط زمن الفحوصات الجارية في هذه الجلسة فقط — كان
    // يُحسب من لقطة ApiUsageTracker (قيم قديمة عابرة للجلسات ومن
    // أي استدعاء لا من الفحص) فيعرض رقماً لا علاقة له بالفحص الجاري.
    val displayAverageLatency = remember(validationStatuses) {
        val latencies = validationStatuses.values.map { it.latencyMs }.filter { it > 0 }
        if (latencies.isNotEmpty()) "${latencies.average().toLong()}ms" else "—"
    }

    val filteredSpecs = remember(selectedCategoryFilter, secretSpecs, reloadTrigger) {
        if (selectedCategoryFilter == "ALL") secretSpecs
        else secretSpecs.filter { it.category == selectedCategoryFilter }
    }

    // محتوى الشاشة الأساسي
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── لوحة مراقبة التكامل السحابي الحية (Firebase & Supabase) ──
            item {
                CloudIntegrationMonitorPanel()
            }

            // بطاقة التلميتر الحية الصادقة والموجزة (Dark Luxury Glassmorphic Board)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.9f)),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(PresetPrimary.copy(alpha = 0.4f), PresetAccent.copy(alpha = 0.2f), PresetPrimary.copy(alpha = 0.4f))
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(PresetPrimary.copy(alpha = 0.12f))
                                        .border(1.dp, PresetPrimary.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Sensors, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "لوحة المراقبة والتحكم بالمفاتيح",
                                        color = PresetPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "بيانات تشغيل حقيقية ومباشرة بدون محاكاة وهمية",
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // أزرار التحكم السريعة للوحة المفاتيح
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { showBackupDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = "نسخ احتياطي واستيراد", tint = PresetPrimary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { showResetAllDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = "إعادة ضبط الكل", tint = theme.destructive.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { showArchitectureGuide = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "دليل المعمارية", tint = PresetPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // شبكة المؤشرات الأربعة الحقيقية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryMetricCard(
                                title = "مُهيأة في النظام",
                                value = "$configuredCount / $totalKeys",
                                color = if (configuredCount > 0) PresetPrimary else theme.textSecondary,
                                icon = Icons.Default.VpnKey,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryMetricCard(
                                title = "اتصال سليم وفعال",
                                // ق94: قبل أي فحص كان «0 / 10» أحمر كأن كل
                                // المفاتيح معطوبة؛ والمقام بعد فحص جزئي
                                // يجب أن يكون المفحوص لا الكل.
                                value = if (validationStatuses.isEmpty()) "لم يُفحص"
                                else "$connectedCount / ${validationStatuses.size}",
                                color = when {
                                    validationStatuses.isEmpty() -> theme.textSecondary
                                    connectedCount == validationStatuses.size -> theme.success
                                    connectedCount > 0 -> theme.warning
                                    else -> theme.destructive
                                },
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryMetricCard(
                                title = "متوسط زمن الاستجابة",
                                value = displayAverageLatency,
                                color = theme.primary,
                                icon = Icons.Default.Speed,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // زر فحص حي لجميع المفاتيح بالتوازي
                        Button(
                            onClick = { testAllKeys() },
                            enabled = !isCheckingAll,
                            colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            if (isCheckingAll) {
                                CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري فحص المفاتيح عبر الخوادم الحقيقية...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فحص حي شامل لجميع المفاتيح الآن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // فلاتر الأقسام (Category Filter Chips)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterList = listOf(
                        "ALL" to "الكل ($totalKeys)",
                        "AI" to "الذكاء الاصطناعي (AI)",
                        "MEDIA" to "الوسائط والفيديو",
                        "BACKEND" to "السحابة وقواعد البيانات",
                        "QURAN" to "منظومة القرآن",
                        "AUDIO" to "الصوتيات"
                    )
                    items(filterList) { (key, label) ->
                        val isSelected = selectedCategoryFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PresetPrimary else theme.cardSurface)
                                .border(1.dp, if (isSelected) PresetPrimary else theme.separator, RoundedCornerShape(16.dp))
                                .clickable { selectedCategoryFilter = key }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (isSelected) DeepSlate else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // بطاقات المفاتيح التفصيلية المصممة بعناية فائقة
            items(filteredSpecs, key = { it.envName }) { spec ->
                val rawVal = getKeyActualValue(spec)
                val isConfigured = isKeyConfigured(rawVal)
                val validation = validationStatuses[spec.envName]
                val isTestingThis = testingKeyEnv == spec.envName
                val isKeyVisible = spec.envName in visibleKeys

                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.background.copy(alpha = 0.95f)),
                    border = BorderStroke(
                        1.dp,
                        when {
                            validation?.isValid == true -> theme.success.copy(alpha = 0.5f)
                            validation != null && !validation.isValid -> theme.destructive.copy(alpha = 0.5f)
                            isConfigured -> PresetPrimary.copy(alpha = 0.35f)
                            else -> theme.separator
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // السطر الأول: الأيقونة، الاسم بالكامل دون أي ضغط، وشارة الحالة
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // الأيقونة
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PresetPrimary.copy(alpha = 0.12f))
                                    .border(1.dp, PresetPrimary.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(spec.icon, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // العنوان وبيئة المتغير
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.displayName,
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${spec.envName} • ${spec.provider}",
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // شارة الحالة الأنيقة والمضغوطة (لا تسبب أي ضغط أو التفاف رأسي)
                            val statusBadgeText = when {
                                isTestingThis -> "يفحص..."
                                validation?.isValid == true -> "متصل ✅"
                                validation != null && !validation.isValid -> "خطأ 🔴"
                                // ق94: «جاهز» كان يدّعي الجاهزية لمفتاح
                                // لم يُفحص بعد — الصادق «مدخل» فقط.
                                isConfigured -> "مدخل ⏳"
                                else -> "غير مهيأ ⚪"
                            }
                            val badgeColor = when {
                                validation?.isValid == true -> theme.success
                                validation != null && !validation.isValid -> theme.destructive
                                isConfigured -> theme.gold
                                else -> theme.textSecondary
                            }
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = statusBadgeText,
                                        color = badgeColor,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // صندوق الوظيفة والأهمية المعمارية
                        Surface(
                            color = theme.background.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, theme.separator),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("التوظيف: ", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text(spec.howItWorks, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("الأهمية: ", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text(spec.whyNeeded, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                        }

                        // نتيجة الفحص الحي الصادقة لكامل عرض البطاقة (تمنع التكسير النصي تماماً)
                        if (validation != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = if (validation.isValid) theme.success.copy(alpha = 0.12f) else theme.destructive.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (validation.isValid) theme.success.copy(alpha = 0.35f) else theme.destructive.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (validation.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (validation.isValid) theme.success else theme.destructive,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = validation.summary,
                                            color = if (validation.isValid) theme.success else theme.destructive,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (validation.explanation.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = validation.explanation,
                                            color = TextPrimary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    if (!validation.isValid && validation.suggestedFix.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "الحل المقترح: ${validation.suggestedFix}",
                                            color = PresetPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // عرض القيمة الحالية بشكل صادق تماماً
                        Surface(
                            color = theme.background,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, theme.separator),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("القيمة: ", color = TextSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                                    if (isConfigured) {
                                        val displayVal = if (isKeyVisible) rawVal
                                        else if (rawVal.length > 10) "${rawVal.take(5)}••••••••${rawVal.takeLast(4)}"
                                        else "••••••••"
                                        Text(
                                            text = displayVal,
                                            color = PresetPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "لم يتم إدخال المفتاح في النظام (غير مهيأ)",
                                            color = theme.textSecondary,
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (isConfigured) {
                                    IconButton(
                                        onClick = {
                                            visibleKeys = if (isKeyVisible) visibleKeys - spec.envName else visibleKeys + spec.envName
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "إظهار/إخفاء",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // شريط أزرار التحكم الكاملة (ما طلبه المستخدم تحديداً: خيارات تحكم مرنة وشاملة)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // 1. زر إدخال وتعديل المفتاح (يفتح حوار الإدخال والحفظ الفوري)
                                Button(
                                    onClick = { activeEditSpec = spec },
                                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConfigured) "تعديل" else "إدخال",
                                        color = PresetPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 2. زر فحص حي فوري
                                Button(
                                    onClick = {
                                        testingKeyEnv = spec.envName
                                        coroutineScope.launch {
                                            try {
                                                val res = testSingleKey(spec)
                                                validationStatuses = validationStatuses + (spec.envName to res)
                                                if (res.isValid) {
                                                    Toast.makeText(context, "${spec.displayName}: متصل وسليم بنجاح ✅", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "${spec.displayName}: ${res.summary}", Toast.LENGTH_LONG).show()
                                                }
                                            } finally {
                                                // ق94: بلا finally كان أي رمي يترك
                                                // البطاقة على «يفحص...» والزر معطلاً.
                                                testingKeyEnv = null
                                            }
                                        }
                                    },
                                    enabled = !isTestingThis,
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.secondarySurface),
                                    border = BorderStroke(1.dp, theme.separator),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (isTestingThis) {
                                        CircularProgressIndicator(color = PresetPrimary, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = theme.primary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("فحص حي", color = theme.primary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // أزرار سريعة: نسخ وتوثيق ومسح
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (isConfigured) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(rawVal))
                                            Toast.makeText(context, "تم نسخ المفتاح إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            AgentPrefs.instance(context)
                                                .edit()
                                                .remove(spec.preferenceKey)
                                                .apply()
                                            reloadTrigger++
                                            validationStatuses = validationStatuses - spec.envName
                                            Toast.makeText(context, "تم مسح ${spec.displayName} 🗑️", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "مسح المفتاح", tint = theme.destructive.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spec.docUrl))
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Toast.makeText(context, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "فتح رابط الوثائق", tint = PresetPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ق94: قسم «الصوتيات» بلا أي مفتاح في القائمة — كان المرشح
            // يعرض قائمة فارغة بلا أي تفسير.
            if (filteredSpecs.isEmpty()) {
                item {
                    Text(
                        text = "لا مفاتيح في هذا القسم بعد — الصوتيات (Kokoro وEdge TTS) تعمل بلا مفتاح API.",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    // إذا كانت الشاشة تعمل بمفردها يتم عرض Scaffold مع TopAppBar، وإلا تُعرض مباشرة بدون تكرار الشريط
    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "مراقبة المفاتيح الحية ($totalKeys) ⚡",
                            color = PresetPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont,
                            fontSize = 16.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة", tint = PresetPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { testAllKeys() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة فحص الكل", tint = PresetPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                )
            },
            containerColor = DeepSlate,
            content = content
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSlate)
        ) {
            content(PaddingValues(0.dp))
        }
    }

    // حوار إدخال وتعديل المفتاح الصادق والفعال (Edit Key Dialog)
    activeEditSpec?.let { spec ->
        var inputVal by remember { mutableStateOf(getKeyActualValue(spec).let { if (isKeyConfigured(it)) it else "" }) }
        var isTestingInDialog by remember { mutableStateOf(false) }
        var dialogTestResult by remember { mutableStateOf<KeyValidationResult?>(null) }

        // (r17) الكتابة الوحيدة من هذا الحوار — لا تُستدعى إلا بعد
        // فحص حي ناجح لنفس القيمة المعروضة (dialogTestResult تُصفَّر
        // عند كل تعديل للإدخال، فلا يمر فحص قديم).
        fun saveDialogKey(trimmed: String) {
            AgentPrefs.instance(context)
                .edit()
                .putString(spec.preferenceKey, trimmed)
                .apply()
            reloadTrigger++
            dialogTestResult?.let { res ->
                validationStatuses = validationStatuses + (spec.envName to res)
            }
            Toast.makeText(context, "تم حفظ ${spec.displayName} بنجاح ✅", Toast.LENGTH_SHORT).show()
            activeEditSpec = null
        }

        AlertDialog(
            onDismissRequest = { activeEditSpec = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(spec.icon, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ضبط: ${spec.displayName}",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "أدخل المفتاح الحقيقي من لوحة تحكم ${spec.provider}:",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )

                    if (spec.keyFormatHint.isNotBlank()) {
                        Surface(
                            color = theme.cardSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, theme.secondarySurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "تلميح الصيغة: ${spec.keyFormatHint}",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = {
                            inputVal = it
                            dialogTestResult = null
                        },
                        placeholder = { Text("الصق المفتاح هنا...", color = TextSecondary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PresetPrimary,
                            unfocusedBorderColor = theme.separator,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            cursorColor = PresetPrimary
                        ),
                        singleLine = spec.serviceType != "firebase",
                        maxLines = if (spec.serviceType == "firebase") 5 else 1,
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) {
                                    inputVal = clip.trim()
                                    dialogTestResult = null
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "لصق", tint = PresetPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    // نتيجة الفحص داخل الحوار
                    dialogTestResult?.let { res ->
                        Surface(
                            color = if (res.isValid) theme.success.copy(alpha = 0.15f) else theme.destructive.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (res.isValid) theme.success else theme.destructive),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = res.summary,
                                    color = if (res.isValid) theme.success else theme.destructive,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                if (res.explanation.isNotBlank()) {
                                    Text(
                                        text = res.explanation,
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // أزرار إضافية داخل الحوار
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // زر فحص قبل الحفظ
                        OutlinedButton(
                            onClick = {
                                if (inputVal.isBlank()) {
                                    Toast.makeText(context, "يرجى كتابة أو لصق المفتاح أولاً", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTestingInDialog = true
                                coroutineScope.launch {
                                    val res = ApiKeyValidator.validateKey(context, spec.serviceType, inputVal, hintFor(spec))
                                    dialogTestResult = res
                                    isTestingInDialog = false
                                }
                            },
                            enabled = !isTestingInDialog,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            if (isTestingInDialog) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فحص فوري", fontSize = 10.sp, fontFamily = CairoFont)
                            }
                        }

                        // زر مسح المفتاح المخزن
                        if (isKeyConfigured(getKeyActualValue(spec))) {
                            TextButton(
                                onClick = {
                                    AgentPrefs.instance(context)
                                        .edit()
                                        .remove(spec.preferenceKey)
                                        .apply()
                                    reloadTrigger++
                                    validationStatuses = validationStatuses - spec.envName
                                    activeEditSpec = null
                                    Toast.makeText(context, "تم مسح المفتاح وإعادة تعيينه 🗑️", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("مسح المفتاح", color = theme.destructive, fontSize = 10.sp, fontFamily = CairoFont)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputVal.trim()
                        if (trimmed.isBlank()) {
                            // ق94: كان الإغلاق صامتاً فيظن المستخدم أنه
                            // حفظ فراغاً — يُبقى الحوار مفتوحاً مع تنبيه.
                            Toast.makeText(context, "يرجى كتابة أو لصق المفتاح أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // (r17) الحفظ بعد فحص حي فقط: زر «حفظ» السابق كان
                        // يكتب أي نص ويقول «تم الحفظ بنجاح» دون فحص، وزر
                        // «فحص فوري» اختياري لا يمنع الحفظ. إن كان الفحص
                        // الناجح معروضاً لنفس القيمة يُحفظ فوراً، وإلا
                        // يُفحص الآن ويُحفظ فقط عند النجاح.
                        if (isTestingInDialog) return@Button
                        if (dialogTestResult?.isValid == true) {
                            saveDialogKey(trimmed)
                            return@Button
                        }
                        isTestingInDialog = true
                        coroutineScope.launch {
                            val res = ApiKeyValidator.validateKey(context, spec.serviceType, trimmed, hintFor(spec))
                            dialogTestResult = res
                            isTestingInDialog = false
                            if (res.isValid) {
                                saveDialogKey(trimmed)
                            } else {
                                Toast.makeText(context, "لم يُحفظ: ${res.summary}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ المفتاح", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeEditSpec = null }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                }
            },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // دليل المعمارية وتوظيف المفاتيح الـ 10
    if (showArchitectureGuide) {
        AlertDialog(
            onDismissRequest = { showArchitectureGuide = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = PresetPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خريطة التوظيف المعماري للمفاتيح الـ 10", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "تتوزع المفاتيح الـ 10 في تطبيق خادم المسلم ضمن طبقات معمارية متكاملة:",
                        fontFamily = NotoSansFont,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    ArchitectureLayerItem(
                        layerName = "1. طبقة العقول والذكاء الاصطناعي (AI Brains)",
                        description = "تشمل Gemini, Groq, OpenRouter لتوليد السيناريوهات والتحليل الديني وقص الفيديوهات فائقة السرعة.",
                        color = PresetPrimary
                    )

                    ArchitectureLayerItem(
                        layerName = "2. طبقة الصوتيات وتلاوة القرآن (Audio & Voice)",
                        description = "تشمل Kokoro وEdge TTS والأصوات المحلية، و Quran Foundation لنصوص وتلاوات القرآن المعتمدة رسمياً آية بآية.",
                        color = theme.success
                    )

                    ArchitectureLayerItem(
                        layerName = "3. طبقة الوسائط والفيديو الواقعي (Media B-Roll)",
                        description = "تشمل Pixabay وLeonardo لتوفير لقطات سينمائية 4K طبيعية وإسلامية خالية من الحقوق وصور مولّدة.",
                        color = theme.primary
                    )

                    ArchitectureLayerItem(
                        layerName = "4. طبقة السحابة وقواعد البيانات (Backend Cloud)",
                        description = "تشمل Supabase لتخزين الصوتيات والمشاريع، و Google Services للإشعارات والتحليلات.",
                        color = theme.accent
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showArchitectureGuide = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إغلاق", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار النسخ الاحتياطي والاستيراد للمفاتيح
    if (showBackupDialog) {
        var importJsonInput by remember { mutableStateOf("") }
        // (r17) المعاينة مقنّعة — السابق كان يعرض كل المفاتيح كاملة
        // بنص صريح داخل الحوار. النسخة الكاملة تُنسخ فقط بزر صريح
        // من المستخدم، مع تنبيه مكتوب أسفل المعاينة.
        val (currentKeysJson, currentKeysJsonFull) = remember(reloadTrigger) {
            val sp = AgentPrefs.instance(context)
            val masked = org.json.JSONObject()
            val full = org.json.JSONObject()
            secretSpecs.forEach { spec ->
                val v = sp.getString(spec.preferenceKey, "")?.trim() ?: ""
                if (isKeyConfigured(v)) {
                    masked.put(spec.preferenceKey, AgentPrefs.maskSecret(v))
                    full.put(spec.preferenceKey, v)
                }
            }
            masked.toString(2) to full.toString(2)
        }

        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = PresetPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نسخ احتياطي واستيراد المفاتيح", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. تصدير المفاتيح الحالية المهيأة في النظام:", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = theme.background,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, theme.secondarySurface),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp)
                    ) {
                        Text(
                            text = if (currentKeysJson.length > 2) currentKeysJson else "{ /* لا توجد مفاتيح مهيأة حالياً */ }",
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(currentKeysJsonFull))
                            Toast.makeText(context, "تم نسخ حزمة المفاتيح إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondarySurface),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ حزمة المفاتيح كـ JSON", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                    Text(
                        "المعاينة أعلاه مقنّعة. النسخة المنسوخة تحمل المفاتيح كاملة بنص صريح — لا تشاركها مع أحد واحذفها بعد النقل.",
                        color = theme.warning,
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2. استيراد مفاتيح جديدة (الصق كود JSON هنا):", color = theme.primary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("{\n  \"gemini_key\": \"...\"\n}", color = TextSecondary, fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.separator,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = importJsonInput.trim()
                        if (trimmed.isBlank()) {
                            showBackupDialog = false
                            return@Button
                        }
                        // (r17) الاستيراد السابق كان يكتب أي مفتاح JSON في
                        // التفضيلات دون قائمة سماح (حتى مفاتيح إعدادات
                        // الوكيل مثل agent_allowed_path_prefixes) ودون أي
                        // فحص حي. الآن: قائمة سماح بمعرفات المفاتيح فقط،
                        // وفحص حي لكل قيمة، ويُحفظ الصالح فقط.
                        try {
                            val obj = org.json.JSONObject(trimmed)
                            val allowed = secretSpecs.associateBy { it.preferenceKey }
                            val targets = mutableListOf<Pair<SecretKeySpec, String>>()
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val v = obj.optString(k).trim()
                                if (v.isNotBlank()) {
                                    allowed[k]?.let { targets.add(it to v) }
                                }
                            }
                            if (targets.isEmpty()) {
                                Toast.makeText(context, "لم يتم العثور على مفاتيح معروفة في النص", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            coroutineScope.launch {
                                var saved = 0
                                var skipped = 0
                                for ((spec, value) in targets) {
                                    val res = ApiKeyValidator.validateKey(context, spec.serviceType, value, hintFor(spec))
                                    if (res.isValid) {
                                        AgentPrefs.instance(context).edit()
                                            .putString(spec.preferenceKey, value).apply()
                                        validationStatuses = validationStatuses + (spec.envName to res)
                                        saved++
                                    } else skipped++
                                }
                                reloadTrigger++
                                Toast.makeText(
                                    context,
                                    "تم استيراد $saved مفتاحاً بعد فحصها حياً" +
                                        if (skipped > 0) "؛ وتُخطّي $skipped مفتاحاً غير صالح" else "",
                                    Toast.LENGTH_LONG
                                ).show()
                                showBackupDialog = false
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "تنسيق JSON غير صالح", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تطبيق والاستيراد", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("إغلاق", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار تأكيد مسح جميع المفاتيح
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = theme.destructive)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعادة تعيين جميع المفاتيح", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
            },
            text = {
                Text(
                    "هل أنت متأكد من مسح جميع المفاتيح الـ 10 المخزنة في النظام؟\nسيتم حذفها من الذاكرة المحلية وإعادة التعيين إلى الحالة الافتراضية.",
                    fontFamily = NotoSansFont,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val editor = AgentPrefs.instance(context).edit()
                        secretSpecs.forEach { spec ->
                            editor.remove(spec.preferenceKey)
                        }
                        editor.apply()
                        reloadTrigger++
                        validationStatuses = emptyMap()
                        showResetAllDialog = false
                        Toast.makeText(context, "تمت إعادة ضبط جميع المفاتيح بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("مسح الكل الآن", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun TelemetryMetricCard(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        color = theme.background.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}

@Composable
fun ArchitectureLayerItem(
    layerName: String,
    description: String,
    color: Color
) {
    val theme = QabasTheme.current
    Surface(
        color = theme.secondarySurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(layerName, color = color, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
