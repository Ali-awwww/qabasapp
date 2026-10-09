package com.qabas.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * نموذج بيانات نقطة نهاية واجهة البرمجة (API Endpoint)
 */
data class ApiEndpointInfo(
    val id: String,
    val method: String,
    val path: String,
    val titleAr: String,
    val titleEn: String,
    val description: String,
    val category: String,
    val requestHeaders: Map<String, String>,
    val requestBodySample: String,
    val responseBodySample: String,
    val statusCodes: List<Pair<Int, String>> = listOf(200 to "OK", 400 to "Bad Request", 401 to "Unauthorized", 500 to "Internal Error")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    BackHandler(onBack = onBack)

    val currentApiKey = remember {
        prefs.getString("user_api_key", null) ?: "qbs_live_sec_${System.currentTimeMillis().toString().takeLast(8)}"
    }
    val baseUrl = "http://127.0.0.1:8080/api"

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("نقاط النهاية 📑", "المختبر التفاعلي ⚡", "أمثلة الأكواد 💻", "المصادقة والأمان 🔐")

    // قائمة نقاط النهاية المدعومة في التطبيق
    val endpoints = remember {
        listOf(
            ApiEndpointInfo(
                id = "generate_script",
                method = "POST",
                path = "/generate-script",
                titleAr = "توليد سيناريو دعوي بالذكاء الاصطناعي",
                titleEn = "Generate Script",
                description = "يستقبل الفكرة الأساسية مع النمط السينمائي، ويقوم بتحليلها شرعياً وفنياً لتوليد مشاهد متسلسلة مع أزمنة الإلقاء ونصوص الكابشنز.",
                category = "الاستوديو والإنتاج",
                requestHeaders = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer $currentApiKey"
                ),
                requestBodySample = """
{
  "idea": "فضل الصدقة والإنفاق في الخفاء",
  "styleDescription": "وثائقي ملحمي دافئ",
  "ratio": "9:16",
  "durationSeconds": 30
}
                """.trimIndent(),
                responseBodySample = """
{
  "status": "success",
  "code": 200,
  "executionTimeMs": 320,
  "scenes": [
    {
      "sceneNumber": 1,
      "title": "مقدمة خاشعة عن فضل الصدقة",
      "description": "مشهد طبيعي لأشجار باسقة مع ضوء شمس ذهبي خافت وكلمة بكلمة",
      "duration": 6
    },
    {
      "sceneNumber": 2,
      "title": "استشهاد نبوي شريف",
      "description": "عرض حديث «صدقة السر تطفئ غضب الرب» بخط رقاعي مضيء",
      "duration": 12
    }
  ]
}
                """.trimIndent()
            ),
            ApiEndpointInfo(
                id = "generate_video",
                method = "POST",
                path = "/generate-video",
                titleAr = "معالجة وبناء الفيديو النهائي",
                titleEn = "Render & Assemble Video",
                description = "يقوم بمعالجة مشاهد الفيديو وتركيب المؤثرات البصرية والخطوط الذهبية مع التعليق الصوتي وإنتاج ملف MP4 عالي الجودة.",
                category = "الاستوديو والإنتاج",
                requestHeaders = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer $currentApiKey"
                ),
                requestBodySample = """
{
  "description": "فيديو فضل الصدقة",
  "resolution": "1080x1920",
  "aspectRatio": "9:16",
  "scenesCount": 2,
  "includeSubtitles": true
}
                """.trimIndent(),
                responseBodySample = """
{
  "status": "success",
  "message": "تم بدء مهمة معالجة الفيديو في الخلفية",
  "taskId": "task_vid_89321",
  "estimatedSeconds": 8
}
                """.trimIndent()
            ),
            ApiEndpointInfo(
                id = "generate_tts",
                method = "POST",
                path = "/tts",
                titleAr = "التعليق الصوتي الإسلامي الصافي",
                titleEn = "Islamic Clean Voiceover",
                description = "تحويل النصوص إلى نبرات صوتية وقورة نقية وخالية تماماً من المعازف والموسيقى بنقاء استوديو.",
                category = "الصوتيات والتلاوة",
                requestHeaders = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer $currentApiKey"
                ),
                requestBodySample = """
{
  "text": "بسم الله الرحمن الرحيم، الحمد لله رب العالمين",
  "voiceId": "sheikh_tarteel_clean",
  "speed": 1.0,
  "pitch": 0.0
}
                """.trimIndent(),
                responseBodySample = """
{
  "status": "success",
  "audioUrl": "local://cache/audio/clean_v_8812.wav",
  "durationSeconds": 4.5,
  "isMusicFree": true
}
                """.trimIndent()
            ),
            ApiEndpointInfo(
                id = "quran_ayah",
                method = "GET",
                path = "/quran/ayah?surah=1&ayah=1",
                titleAr = "استرجاع بيانات الآيات والتفاسير",
                titleEn = "Quran Verse & Tafsir",
                description = "جلب نص الآية الكريمة بالرسم العثماني الموثق مع التفسير الميسر وبيانات السورة والترجمة.",
                category = "المعرفة الإسلامية",
                requestHeaders = mapOf(
                    "Authorization" to "Bearer $currentApiKey"
                ),
                requestBodySample = "— (طلب GET لا يتطلب جسم بيانات)",
                responseBodySample = """
{
  "surahNumber": 1,
  "surahName": "الفَاتِحَة",
  "ayahNumber": 1,
  "textUthmani": "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
  "tafsir": "أبدأ قراءتي باسم الله مستعيناً به...",
  "juz": 1
}
                """.trimIndent()
            ),
            ApiEndpointInfo(
                id = "hadith_random",
                method = "GET",
                path = "/hadith/random",
                titleAr = "استرجاع حديث موثق من الصحيحين",
                titleEn = "Authentic Hadith Fetcher",
                description = "استرجاع حديث نبوي شريف صحيح ومحقق من صحيحي البخاري ومسلم لبطاقات النشر السريعة.",
                category = "المعرفة الإسلامية",
                requestHeaders = mapOf(
                    "Authorization" to "Bearer $currentApiKey"
                ),
                requestBodySample = "— (طلب GET لا يتطلب جسم بيانات)",
                responseBodySample = """
{
  "narrator": "عمر بن الخطاب رضي الله عنه",
  "matn": "إنما الأعمال بالنيات، وإنما لكل امرئ ما نوى...",
  "source": "صحيح البخاري • كتاب بدء الوحي",
  "number": 1,
  "isAuthentic": true
}
                """.trimIndent()
            )
        )
    }

    // حالات المختبر التفاعلي (Playground)
    var selectedPlaygroundEndpoint by remember { mutableStateOf(endpoints.first()) }
    var playgroundPayloadInput by remember { mutableStateOf(endpoints.first().requestBodySample) }
    var isSendingRequest by remember { mutableStateOf(false) }
    var responseOutput by remember { mutableStateOf<String?>(null) }
    var responseStatusCode by remember { mutableIntStateOf(200) }
    var responseLatencyMs by remember { mutableLongStateOf(0L) }

    // كود مقتطفات البرمجة
    var selectedSnippetLang by remember { mutableStateOf("cURL") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GoldPrimary.copy(alpha = 0.15f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "توثيق واجهات برمجة قبس",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Qabas Developer RESTful API Gateway",
                                color = qabasTextSecondary(),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareInfo = """
                            ✨ واجهات برمجة تطبيقات قبس (Qabas API)
                            🌐 Base URL: $baseUrl
                            🔑 Authentication: Bearer Token
                            📑 التوثيق الرسمي والمختبر متاح داخل تطبيق قبس.
                        """.trimIndent()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareInfo)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة توثيق واجهات قبس"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة التوثيق", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        bottomBar = bottomBar,
        containerColor = qabasBackground()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // تبويبات التنقل الرئيسية
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF0F1728),
                contentColor = GoldPrimary,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = GoldPrimary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTabIndex == index) GoldPrimary else qabasTextSecondary(),
                                fontFamily = CairoFont,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // تبويب نقاط النهاية (Endpoints List)
                    EndpointsDocList(
                        endpoints = endpoints,
                        baseUrl = baseUrl,
                        apiKey = currentApiKey,
                        onTestEndpoint = { endpoint ->
                            selectedPlaygroundEndpoint = endpoint
                            playgroundPayloadInput = endpoint.requestBodySample
                            selectedTabIndex = 1 // الانتقال للمختبر التفاعلي
                        }
                    )
                }
                1 -> {
                    // تبويب المختبر التفاعلي (Playground)
                    ApiPlaygroundTab(
                        endpoints = endpoints,
                        selectedEndpoint = selectedPlaygroundEndpoint,
                        onSelectEndpoint = { ep ->
                            selectedPlaygroundEndpoint = ep
                            playgroundPayloadInput = ep.requestBodySample
                            responseOutput = null
                        },
                        payloadInput = playgroundPayloadInput,
                        onPayloadChange = { playgroundPayloadInput = it },
                        baseUrl = baseUrl,
                        apiKey = currentApiKey,
                        isSending = isSendingRequest,
                        responseOutput = responseOutput,
                        responseStatus = responseStatusCode,
                        latencyMs = responseLatencyMs,
                        onSendRequest = {
                            coroutineScope.launch {
                                isSendingRequest = true
                                responseOutput = null
                                val startTime = System.currentTimeMillis()
                                delay(350) // محاكاة المعالجة المباشرة
                                responseLatencyMs = System.currentTimeMillis() - startTime
                                isSendingRequest = false
                                responseStatusCode = 200
                                responseOutput = selectedPlaygroundEndpoint.responseBodySample
                                Toast.makeText(context, "تم استلام الرد بنجاح (200 OK) ✓", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                2 -> {
                    // تبويب أمثلة الأكواد البرمجية (Code Snippets)
                    CodeSnippetsTab(
                        endpoints = endpoints,
                        selectedLang = selectedSnippetLang,
                        onLangChange = { selectedSnippetLang = it },
                        baseUrl = baseUrl,
                        apiKey = currentApiKey
                    )
                }
                3 -> {
                    // تبويب المصادقة والأمان (Security & Authentication)
                    SecurityAndAuthTab(
                        apiKey = currentApiKey,
                        baseUrl = baseUrl,
                        onCopyKey = {
                            clipboardManager.setText(AnnotatedString(currentApiKey))
                            Toast.makeText(context, "تم نسخ مفتاح API للحافظة 📋", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

/**
 * تبويب استعراض وتوثيق كافة نقاط النهاية
 */
@Composable
private fun EndpointsDocList(
    endpoints: List<ApiEndpointInfo>,
    baseUrl: String,
    apiKey: String,
    onTestEndpoint: (ApiEndpointInfo) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // بطاقة الهيدر الفاخرة للـ API
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.4f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "بوابة خدمات قبس للمطورين",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "نظام واجهات RESTful معتمد لخدمة التطبيقات والمنصات الدعوية",
                                color = qabasTextSecondary(),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Text(
                                "نشط 🟢",
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color(0xFF090E1A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("Base URL:", color = GoldSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(baseUrl, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(baseUrl))
                                    Toast.makeText(context, "تم نسخ رابط الخادم الأساسي 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }

        // عرض كل نقطة نهاية
        items(endpoints) { endpoint ->
            EndpointCard(endpoint = endpoint, onTest = { onTestEndpoint(endpoint) })
        }
    }
}

@Composable
private fun EndpointCard(
    endpoint: ApiEndpointInfo,
    onTest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val methodColor = if (endpoint.method == "POST") Color(0xFFF59E0B) else Color(0xFF10B981)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 14.dp, borderAlpha = 0.3f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = methodColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, methodColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            endpoint.method,
                            color = methodColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        endpoint.path,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            endpoint.category,
                            color = qabasTextSecondary(),
                            fontFamily = CairoFont,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = endpoint.titleAr,
                color = GoldSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = Color(0xFF1E2D4A), modifier = Modifier.padding(bottom = 8.dp))

                    Text(endpoint.description, color = Color(0xFFCBD5E1), fontFamily = CairoFont, fontSize = 11.sp, lineHeight = 16.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Headers المطلوبة:", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    endpoint.requestHeaders.forEach { (k, v) ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            Text("$k: ", color = qabasTextSecondary(), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            Text(v, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("عينة الطلب (Request Payload):", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFF090E1A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = endpoint.requestBodySample,
                            color = Color(0xFFA5F3FC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("عينة الرد الناجح (Response 200 OK):", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFF090E1A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = endpoint.responseBodySample,
                            color = Color(0xFF6EE7B7),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTest,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تجربة النقطة بالمختبر", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val cUrl = "curl -X ${endpoint.method} \"http://127.0.0.1:8080/api${endpoint.path}\" \\\n  -H \"Content-Type: application/json\" \\\n  -H \"Authorization: Bearer <API_KEY>\""
                                clipboardManager.setText(AnnotatedString(cUrl))
                                Toast.makeText(context, "تم نسخ أمر cURL 📋", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ cURL", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * تبويب المختبر التفاعلي لاختبار الـ API مباشرة
 */
@Composable
private fun ApiPlaygroundTab(
    endpoints: List<ApiEndpointInfo>,
    selectedEndpoint: ApiEndpointInfo,
    onSelectEndpoint: (ApiEndpointInfo) -> Unit,
    payloadInput: String,
    onPayloadChange: (String) -> Unit,
    baseUrl: String,
    apiKey: String,
    isSending: Boolean,
    responseOutput: String?,
    responseStatus: Int,
    latencyMs: Long,
    onSendRequest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // بطاقة اختيار الـ Endpoint
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("اختر نقطة النهاية للاختبار:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(endpoints) { ep ->
                            val isSelected = ep.id == selectedEndpoint.id
                            Surface(
                                color = if (isSelected) GoldPrimary else Color(0xFF151C2C),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF2A3650)),
                                modifier = Modifier.clickable { onSelectEndpoint(ep) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        ep.method,
                                        color = if (isSelected) DeepSlate else (if (ep.method == "POST") Color(0xFFF59E0B) else Color(0xFF10B981)),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        ep.titleAr,
                                        color = if (isSelected) DeepSlate else Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // عنوان المسار الكامل
                    Surface(
                        color = Color(0xFF090E1A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${selectedEndpoint.method} $baseUrl${selectedEndpoint.path}",
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // محرر جسم الطلب Payload
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("جسم الطلب (JSON Request Body):", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        TextButton(
                            onClick = { onPayloadChange(selectedEndpoint.requestBodySample) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("استعادة العينة الأصلية ↺", color = GoldSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = payloadInput,
                        onValueChange = onPayloadChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF090E1A),
                            unfocusedContainerColor = Color(0xFF090E1A),
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E2D4A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 4,
                        maxLines = 8
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onSendRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        enabled = !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DeepSlate, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جاري استدعاء الخادم...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إرسال الطلب الآن ⚡ (Execute Call)", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // نافذة استعراض الرد (Response Inspector)
        if (responseOutput != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 14.dp, borderAlpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Text(
                                        "$responseStatus OK",
                                        color = Color(0xFF10B981),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("الاستجابة: ${latencyMs}ms", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(responseOutput))
                                    Toast.makeText(context, "تم نسخ الرد بالكامل 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = Color(0xFF080C14),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = responseOutput,
                                color = Color(0xFF6EE7B7),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * تبويب أمثلة الأكواد بلغات البرمجة المختلفة
 */
@Composable
private fun CodeSnippetsTab(
    endpoints: List<ApiEndpointInfo>,
    selectedLang: String,
    onLangChange: (String) -> Unit,
    baseUrl: String,
    apiKey: String
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val langs = listOf("cURL", "Kotlin", "Python", "JavaScript")

    val snippetCode = remember(selectedLang) {
        when (selectedLang) {
            "cURL" -> """
# استدعاء توليد سيناريو دعوي بالذكاء الاصطناعي
curl -X POST "$baseUrl/generate-script" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $apiKey" \
  -d '{
    "idea": "فضل الصدقة في الخفاء",
    "styleDescription": "وثائقي ملحمي دافئ",
    "ratio": "9:16",
    "durationSeconds": 30
  }'
            """.trimIndent()
            "Kotlin" -> """
// باستخدام OkHttp في تطبيقات أندرويد
val client = OkHttpClient()
val mediaType = "application/json; charset=utf-8".toMediaType()
val jsonBody = JSONObject().apply {
    put("idea", "فضل الصدقة في الخفاء")
    put("styleDescription", "وثائقي ملحمي دافئ")
    put("durationSeconds", 30)
}
val request = Request.Builder()
    .url("$baseUrl/generate-script")
    .addHeader("Authorization", "Bearer $apiKey")
    .post(jsonBody.toString().toRequestBody(mediaType))
    .build()

val response = client.newCall(request).execute()
val resultJson = response.body?.string()
            """.trimIndent()
            "Python" -> """
import requests

url = "$baseUrl/generate-script"
headers = {
    "Content-Type": "application/json",
    "Authorization": "Bearer $apiKey"
}
payload = {
    "idea": "فضل الصدقة في الخفاء",
    "styleDescription": "وثائقي ملحمي دافئ",
    "durationSeconds": 30
}

response = requests.post(url, json=payload, headers=headers)
print(response.json())
            """.trimIndent()
            "JavaScript" -> """
// Node.js / Browser Fetch
const response = await fetch("$baseUrl/generate-script", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Authorization": "Bearer $apiKey"
  },
  body: JSON.stringify({
    idea: "فضل الصدقة في الخفاء",
    styleDescription: "وثائقي ملحمي دافئ",
    durationSeconds: 30
  })
});

const data = await response.json();
console.log(data);
            """.trimIndent()
            else -> ""
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("اختر لغة البرمجة المفضلة لديك:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        langs.forEach { lang ->
                            val isSelected = lang == selectedLang
                            Surface(
                                color = if (isSelected) GoldPrimary else Color(0xFF151C2C),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF2A3650)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onLangChange(lang) }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        lang,
                                        color = if (isSelected) DeepSlate else Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("كود الاستدعاء المباشر ($selectedLang):", color = GoldSecondary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(snippetCode))
                                Toast.makeText(context, "تم نسخ كود $selectedLang للحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ الكود", color = DeepSlate, fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = Color(0xFF090E1A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = snippetCode,
                            color = Color(0xFFE2E8F0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * تبويب المصادقة والأمان
 */
@Composable
private fun SecurityAndAuthTab(
    apiKey: String,
    baseUrl: String,
    onCopyKey: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مفتاح الوصول المصرح (API Secret Key)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text(
                        "تتطلب جميع استدعاءات واجهات برمجة قبس تمرير مفتاحك في ترويسة الطلب عبر المعيار القياسي Bearer Token لضمان التحقق وحماية حصص الاستهلاك:",
                        color = Color(0xFFCBD5E1),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Surface(
                        color = Color(0xFF090E1A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Authorization: Bearer $apiKey",
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onCopyKey, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2D4A), modifier = Modifier.padding(vertical = 4.dp))

                    Text("قواعد وضوابط الاستخدام الشرعي:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خلو تام من المعازف: ترفض خوادم قبس دمج أي مقاطع موسيقية أو إيقاعات محرمة.", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("دقة النقل الشرعي: يتم تدقيق الآيات بالرسم العثماني والأحاديث من الكتب التسعة المعتمدة.", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("معدل الطلبات المتاح: 100 طلب في الدقيقة لكل حساب صانع محتوى.", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
