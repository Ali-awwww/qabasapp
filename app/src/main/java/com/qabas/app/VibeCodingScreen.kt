package com.qabas.app

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * قسم «فايب كودينج» الموحد (البند 12).
 *
 * مدخل واحد بدل «الاستوديو» المشتت، والترتيب الملزم داخل القسم:
 * 1 المحادثة، 2 المشاريع، 3 المعاينة، 4 النماذج، 5 الأدوات، 6 الموصلات،
 * 7 التحكم، 8 الروابط.
 *
 * القاعدة: لا يُعاد بناء ما هو موجود — كل تبويب يعرض مكوناً موجوداً كما هو
 * (محادثة AiStudioAgentScreen، وكيل محتوى StudioAgentScreen، …)، وتُضاف
 * التبويبات تباعاً مع كل بند فرعي. بوابة المحتوى (StudioGatewayScreen)
 * تبقى reachable من مسار المحتوى ومن تبويب الروابط، لا من التبويب السفلي.
 */
enum class VibeChatMode { CODING, CONTENT }

enum class VibeTab(val title: String, val icon: ImageVector) {
    CHAT("المحادثة", Icons.Default.Chat),
    PROJECTS("المشاريع", Icons.Default.Folder),
    PREVIEW("المعاينة", Icons.Default.Preview),
    MODELS("النماذج", Icons.Default.Memory),
    TOOLS("الأدوات", Icons.Default.Build),
    CONNECTORS("الموصلات", Icons.Default.Link),
    CONTROL("التحكم", Icons.Default.Tune),
    LINKS("الروابط", Icons.Default.Public)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeCodingScreen(
    onBack: () -> Unit,
    onNavigateToGateway: () -> Unit = {},
    onOpenKeys: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenTeamConfig: () -> Unit = {},
    onOpenProject: (ProjectService.Project) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(VibeTab.CHAT) }
    // التبويبات بالترتيب الملزم: محادثة، مشاريع، معاينة، نماذج، أدوات، موصلات، تحكم، روابط.
    val availableTabs = listOf(
        VibeTab.CHAT, VibeTab.PROJECTS, VibeTab.PREVIEW, VibeTab.MODELS,
        VibeTab.TOOLS, VibeTab.CONNECTORS, VibeTab.CONTROL, VibeTab.LINKS
    )
    val tabIndex = availableTabs.indexOf(selectedTab).coerceAtLeast(0)

    Scaffold(
        containerColor = QabasTheme.current.background,
        bottomBar = bottomBar,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "فايب كودينج",
                            color = QabasTheme.current.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = QabasTheme.current.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = QabasTheme.current.cardSurface
                    )
                )
                ScrollableTabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = QabasTheme.current.cardSurface,
                    contentColor = QabasTheme.current.primary
                ) {
                    availableTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == tabIndex,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    tab.title,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = if (index == tabIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == tabIndex) QabasTheme.current.primary else QabasTheme.current.textSecondary
                                )
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = if (index == tabIndex) QabasTheme.current.primary else QabasTheme.current.textSecondary
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(QabasTheme.current.background)
        ) {
            when (availableTabs[tabIndex]) {
                VibeTab.CHAT -> VibeChatTab(
                    onBack = onBack,
                    onOpenKeys = onOpenKeys,
                    onOpenTeamConfig = onOpenTeamConfig,
                    onOpenProject = onOpenProject
                )
                VibeTab.PROJECTS -> VibeProjectsTab(
                    onOpenLibrary = onOpenLibrary,
                    onOpenChat = { selectedTab = VibeTab.CHAT }
                )
                VibeTab.PREVIEW -> VibePreviewTab()
                VibeTab.MODELS -> VibeModelsTab()
                VibeTab.TOOLS -> VibeToolsTab()
                VibeTab.CONNECTORS -> VibeConnectorsTab(onOpenKeys = onOpenKeys)
                VibeTab.CONTROL -> VibeControlTab()
                VibeTab.LINKS -> VibeLinksTab(
                    onOpenKeys = onOpenKeys,
                    onNavigateToGateway = onNavigateToGateway
                )
            }
        }
    }
}

/**
 * تبويب «المحادثة» (12.2): محادثة واحدة بمسارين.
 * - برمجة: محادثة AiStudioAgentScreen كما هي (handleSendMessage +
 *   handleApprovePlan + حفظ الجلسات + الأوامر السريعة)، تفتح في المحادثة مباشرة.
 * - محتوى: منطق StudioAgentScreen كما هو (قالب الأسئلة الثلاثة + buildPlan +
 *   executePlan) كوضع محتوى داخل المحادثة الموحدة، لا كشاشة مستقلة.
 * - محادثة المخرج DirectorChatScreen بقيت خارج القسم عمداً: هي جزء من تدفق
 *   «المخرج الذكي» (Smart Director) لا من الوكيل، ودمجها كان سيخلط مسار
 *   موجز الفيديو بمساري البرمجة/المحتوى.
 * - المساعد العام AIAssistantScreen بقي خارج القسم كما هو دون أي لمس.
 */
@Composable
private fun VibeChatTab(
    onBack: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenTeamConfig: () -> Unit,
    onOpenProject: (ProjectService.Project) -> Unit
) {
    var mode by remember { mutableStateOf(VibeChatMode.CODING) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VibeModeChip(
                label = "💻 برمجة",
                selected = mode == VibeChatMode.CODING,
                onClick = { mode = VibeChatMode.CODING },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            VibeModeChip(
                label = "🎬 محتوى (ريلز)",
                selected = mode == VibeChatMode.CONTENT,
                onClick = { mode = VibeChatMode.CONTENT },
                modifier = Modifier.weight(1f)
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                VibeChatMode.CODING -> AiStudioAgentScreen(
                    onBack = onBack,
                    onOpenTeamConfig = onOpenTeamConfig,
                    bottomBar = {},
                    startInChat = true
                )
                VibeChatMode.CONTENT -> StudioAgentScreen(
                    onBack = onBack,
                    onOpenCodingAgent = { mode = VibeChatMode.CODING },
                    onOpenKeys = onOpenKeys,
                    onOpenProject = onOpenProject,
                    bottomBar = {},
                    // الشاشة الحاوية لها شريطها وتبويباتها — بلا شريط مكرر.
                    showTopBar = false
                )
            }
        }
    }
}

/**
 * تبويب «النماذج» (12.5، المرتبة 4): محتوى مركز النماذج المحلية كما هو
 * (OnDeviceModelsContent) — التنزيل المستأنف/الحذف/التبديل/التصدير/الاستيراد
 * في OnDeviceModelManager، وواجهة LlamaCppService كما هي. حالة المحرك
 * تُعرض بصدق داخل المحتوى نفسه (غير متاح في بناء CI → تفعيل معطّل).
 */
@Composable
private fun VibeModelsTab() {
    val context = LocalContext.current
    OnDeviceModelsContent(
        context = context,
        onDismiss = {},
        showCloseButton = false
    )
}

/**
 * تبويب «الأدوات» (12.6، المرتبة 5): ToolsTab كما هي — 20 أداة كود
 * (CodeTools) تحت «أدوات الكود» و9 أدوات محتوى (AiTools) تحت «أدوات المحتوى».
 * مفاتيح التفعيل تُفرض الآن في مسار التنفيذ عبر AgentToolGate (لا في
 * الواجهة فقط)، والأدوات المدمرة لها حوار تأكيد في التبويب نفسه.
 */
@Composable
private fun VibeToolsTab() {
    val context = LocalContext.current
    ToolsTab(context = context)
}

/**
 * تبويب «الموصلات» (12.7، المرتبة 6): بطاقات تعرض المكونات الموجودة —
 * GitHub (RepoVaultManager + GitHubRepoClient + RepoSetupDialog +
 * pingGitHubLive) وSupabase (KeySyncService + ProjectService وحالتهما)
 * ومزودو النماذج برابط إلى «مفاتيحي». ConnectorsSection.kt لا تُنسخ هنا
 * عمداً: مرصد صيانة للوحة المطور فقط.
 */
@Composable
private fun VibeConnectorsTab(onOpenKeys: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val activeRepo = remember(context, vaultTrigger) { RepoVaultManager.getActiveRepo(context) }
    val savedRepos = remember(context, vaultTrigger) { RepoVaultManager.getRepositories(context) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var ghStatus by remember { mutableStateOf<String?>(null) }
    var ghChecking by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var syncBusy by remember { mutableStateOf(false) }
    var localProjectCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        localProjectCount = runCatching { ProjectService(context).getLocalProjects().size }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "موصلات الوكيل — GitHub وSupabase ومزودو النماذج في مكان واحد.",
                color = QabasTheme.current.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp
            )
        }
        item {
            VibeCard(title = "GitHub — مستودعات الوكيل") {
                if (activeRepo == null) {
                    Text("لا يوجد مستودع نشط — اربط مستودع GitHub ليعمل الوكيل عليه.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                } else {
                    Text("النشط: ${activeRepo.fullName}", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("الفرع الافتراضي: ${activeRepo.defaultBranch} • محفوظة: ${savedRepos.size}", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
                if (ghStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(ghStatus!!, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showRepoDialog = true }) {
                        Text("إعداد المستودع", fontFamily = CairoFont, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val repo = activeRepo
                            if (repo == null) {
                                ghStatus = "لا يوجد مستودع نشط لفحصه."
                            } else {
                                ghChecking = true
                                scope.launch {
                                    val health = AgentConnectivityEngine.pingGitHubLive(repo.token, repo.fullName)
                                    ghStatus = if (health.isConnected) {
                                        "متصل (${health.latencyMs}ms) — ${health.statusDetail}"
                                    } else {
                                        "غير متصل — ${health.statusDetail}"
                                    }
                                    ghChecking = false
                                }
                            }
                        },
                        enabled = !ghChecking,
                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.success, contentColor = QabasTheme.current.onPrimary)
                    ) {
                        Text(if (ghChecking) "يفحص…" else "فحص حي", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            VibeCard(title = "Supabase — الخزنة والمشاريع") {
                Text(
                    "خزنة المفاتيح (KeySyncService عبر qabas-key-vault) ومزامنة المشاريع (ProjectService).",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                )
                Text(
                    "مشاريع محلية في Room: ${localProjectCount?.toString() ?: "—"}",
                    color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp
                )
                if (syncMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(syncMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            syncBusy = true
                            KeySyncService.pushToCloud(context) { ok, msg ->
                                syncBusy = false
                                syncMessage = msg
                            }
                        },
                        enabled = !syncBusy
                    ) { Text("رفع المفاتيح", fontFamily = CairoFont, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            syncBusy = true
                            KeySyncService.pullFromCloud(context) { ok, msg ->
                                syncBusy = false
                                syncMessage = msg
                            }
                        },
                        enabled = !syncBusy
                    ) { Text("تنزيل المفاتيح", fontFamily = CairoFont, fontSize = 12.sp) }
                }
            }
        }
        item {
            VibeCard(title = "مزودو النماذج") {
                Text(
                    "مفاتيح Gemini وGroq وOpenAI وOpenRouter وغيرها تُدار من «مفاتيحي» — " +
                        "فحص حي قبل الحفظ وعرض مقنّع دائماً.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenKeys,
                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                ) {
                    Text("فتح «مفاتيحي»", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRepoDialog) {
        RepoSetupDialog(
            context = context,
            activeRepo = activeRepo,
            onDismiss = { showRepoDialog = false }
        )
    }
}

/** حالة الملف الحالي في المستودع لعرضها جنباً إلى جنب مع المقترح (12.13). */
private sealed interface VibeCurrentFile {
    object Loading : VibeCurrentFile
    data class Ready(val content: String) : VibeCurrentFile
    data class Missing(val reason: String) : VibeCurrentFile
}

/** مخرج HTML كامل؟ (12.13) */
private fun vibeLooksLikeHtmlDocument(text: String): Boolean {
    val lower = text.lowercase()
    return "<html" in lower || "<!doctype html" in lower
}

/** مخرج HTML جزئي (وسوم شائعة بلا هيكل كامل)؟ (12.13) */
private fun vibeLooksLikeHtmlFragment(text: String): Boolean {
    val lower = text.lowercase()
    return listOf(
        "<div", "<p>", "<p ", "<h1", "<h2", "<h3", "<table", "<ul", "<ol",
        "<span", "<button", "<style", "<a "
    ).any { it in lower }
}

/**
 * معاينة HTML في WebView (12.13): JavaScript معطلة — عرض فقط، لا
 * تشغيل. لا يُستخدم لتشغيل تطبيق أندرويد (لا يوجد تشغيل محلي).
 */
@Composable
private fun VibeHtmlPreview(html: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { view -> view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) },
        modifier = Modifier.fillMaxWidth().height(320.dp)
    )
}

/** بطاقة قسم داكنة موحدة لتبويبات فايب كودينج. */
@Composable
private fun VibeCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        color = QabasTheme.current.cardSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            content()
        }
    }
}

/**
 * تبويب «الروابط» (12.8، المرتبة 8): دليل المنصات الست كما هو
 * (CodingPlatformDirectory + CodingPlatformCard) + روابط مزودي «مفاتيحي»
 * الأحد عشر + بطاقة بوابة المحتوى. بلا روابط جديدة من الصفر، وبلا درجات
 * قوة/سرعة غير موثقة (حُذفت من العرض في CodingPlatformCard).
 */
@Composable
private fun VibeLinksTab(
    onOpenKeys: () -> Unit,
    onNavigateToGateway: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "منصات مفاتيح البرمجة — روابط رسمية وخطوات، بلا أرقام قوة أو سرعة غير موثقة.",
                color = QabasTheme.current.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp
            )
        }
        items(CodingPlatformDirectory.platforms, key = { it.id }) { platform ->
            CodingPlatformCard(
                platform = platform,
                onOpenUrl = { openVibeUrl(context, platform.apiKeyUrl) },
                onCopyUrl = {
                    clipboard.setText(AnnotatedString(platform.apiKeyUrl))
                    Toast.makeText(context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                },
                onGoToInput = onOpenKeys
            )
        }
        item {
            VibeCard(title = "روابط مزودي «مفاتيحي»") {
                MY_KEYS_PROVIDER_LINKS.forEach { (name, url) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                        if (url.isNullOrBlank()) {
                            Text("بلا رابط موثق", color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                        } else {
                            OutlinedButton(onClick = { openVibeUrl(context, url) }) {
                                Text("فتح الرابط", fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            VibeCard(title = "بوابة المحتوى") {
                Text(
                    "بوابة الاستوديو (فيديو/صور/صوت) تبقى reachable من هنا ومن مسار المحتوى في المحادثة.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToGateway,
                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                ) {
                    Text("فتح بوابة المحتوى", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun openVibeUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/**
 * تبويب «التحكم» (12.9، المرتبة 7): إعدادات AgentPrefs (حد الجولات/
 * الإصلاح الذاتي/وضع التخطيط/النموذج) عبر AgentKernelSettingsCard كما هي،
 * + مفتاح الوضع المستقل (افتراضياً متوقف — كان true) + حد التكلفة
 * (يُحفظ ويُعرض فقط؛ الإيقاف عند الحد في بند 12.14 اللاحق، فلا إيقاف
 * وهمي هنا) + قائمة سماح المسارات/الفروع التي يفرضها AgentToolGate.
 */
@Composable
private fun VibeControlTab() {
    val context = LocalContext.current
    var isAutonomous by remember { mutableStateOf(AgentPrefs.isAutonomousMode(context)) }
    var isPlanning by remember { mutableStateOf(AgentPrefs.isPlanningMode(context)) }
    var costText by remember { mutableStateOf(AgentPrefs.getCostLimitUsd(context)?.toString() ?: "") }
    var costMessage by remember { mutableStateOf("") }
    // (12.14) لوحة التكلفة والاستهلاك.
    var costSnapshot by remember { mutableStateOf(VibeCostTracker.snapshot(context)) }
    var confirmCostReset by remember { mutableStateOf(false) }
    var costDashboardMessage by remember { mutableStateOf("") }
    var pathText by remember { mutableStateOf(AgentPrefs.allowedPathPrefixes(context).joinToString(", ")) }
    var branchText by remember { mutableStateOf(AgentPrefs.allowedBranches(context).joinToString(", ")) }
    var scopeMessage by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { AgentKernelSettingsCard(context = context) }
        item {
            VibeCard(title = "الاستقلالية والتخطيط") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("النمط المستقل التلقائي", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "متابعة تنفيذ مراحل المهمة دون طلب تأكيد لكل خطوة — متوقف افتراضياً.",
                            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isAutonomous,
                        onCheckedChange = {
                            isAutonomous = it
                            AgentPrefs.putAutonomousMode(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = QabasTheme.current.onPrimary,
                            checkedTrackColor = QabasTheme.current.primary
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("وضع التخطيط", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("عرض بطاقة خطة للاعتماد قبل التنفيذ.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isPlanning,
                        onCheckedChange = {
                            isPlanning = it
                            AgentPrefs.putPlanningMode(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = QabasTheme.current.onPrimary,
                            checkedTrackColor = QabasTheme.current.primary
                        )
                    )
                }
            }
        }
        item {
            VibeCard(title = "حد التكلفة (دولار)") {
                Text(
                    "عند بلوغ الحد يتوقف تنفيذ المهمة برسالة صريحة — يُطبق على " +
                        "الاستهلاك المقاس فقط (نماذج بلا سعر موثق أو رموز تقديرية لا تُحتسب). " +
                        "اتركه فارغاً بلا حد.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("حد التكلفة بالدولار", fontFamily = CairoFont, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val trimmed = costText.trim()
                            if (trimmed.isBlank()) {
                                AgentPrefs.putCostLimitUsd(context, null)
                                costMessage = "أُزيل حد التكلفة (بلا حد)."
                            } else {
                                val value = trimmed.toDoubleOrNull()
                                if (value == null || value < 0) {
                                    costMessage = "أدخل رقماً صحيحاً (0 أو أكثر) أو اتركه فارغاً."
                                } else {
                                    AgentPrefs.putCostLimitUsd(context, value)
                                    costMessage = "تم حفظ حد التكلفة: $value دولار — يُوقف التنفيذ عند بلوغه."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                    ) { Text("حفظ الحد", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    if (costMessage.isNotBlank()) {
                        Text(costMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }
                Text(
                    "الحد الحالي: ${AgentPrefs.getCostLimitUsd(context)?.let { "$it دولار" } ?: "بلا حد"}",
                    color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp
                )
            }
        }
        // (12.14) لوحة التكلفة والاستهلاك — كل الأسعار تقريبية.
        item {
            VibeCard(title = "لوحة التكلفة والاستهلاك (تقريبي)") {
                Text(
                    "كل الأسعار تقريبية — تحقق منها ${VibeModelPrices.VERIFIED_ON} من صفحات التسعير الرسمية للمزودين. " +
                        "«غير مقاسة» تعني بلا سعر موثق، أو رموز تقديرية، أو بلا تفصيل رموز.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "الإنفاق المقاس: ${VibeCostTracker.formatUsd(costSnapshot.spentUsd)} دولار",
                    color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "الاستدعاءات: ${costSnapshot.totalCalls} (مقاسة: ${costSnapshot.measuredCalls} • غير مقاسة: ${costSnapshot.unmeasuredCalls}) • الرموز: ${costSnapshot.totalTokens}",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                val currentLimit = AgentPrefs.getCostLimitUsd(context)
                Text(
                    if (currentLimit == null) "الحد: بلا حد"
                    else "الحد: ${VibeCostTracker.formatUsd(currentLimit)} دولار • المتبقي المقاس: ${VibeCostTracker.formatUsd((currentLimit - costSnapshot.spentUsd).coerceAtLeast(0.0))} دولار",
                    color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp
                )
                if (costSnapshot.unmeasuredByReason.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("غير مقاسة حسب السبب:", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    costSnapshot.unmeasuredByReason.forEach { (reason, count) ->
                        Text("• $reason: $count", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }
                if (costSnapshot.byProvider.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("حسب المزود:", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    costSnapshot.byProvider.entries.sortedByDescending { it.value.costUsd }.forEach { (providerId, agg) ->
                        Text(
                            "• $providerId: ${VibeCostTracker.formatUsd(agg.costUsd)} دولار — ${agg.calls} استدعاء (${agg.tokens} رمزاً، غير مقاسة: ${agg.unmeasuredCalls})",
                            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                        )
                    }
                }
                if (costSnapshot.byModel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("حسب النموذج:", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    costSnapshot.byModel.entries.sortedByDescending { it.value.costUsd }.forEach { (model, agg) ->
                        Text(
                            "• $model: ${VibeCostTracker.formatUsd(agg.costUsd)} دولار — ${agg.calls} استدعاء (${agg.tokens} رمزاً، غير مقاسة: ${agg.unmeasuredCalls})",
                            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                        )
                    }
                }
                if (costSnapshot.byMission.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("حسب المهمة:", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    costSnapshot.byMission.entries.sortedByDescending { it.value.costUsd }.forEach { (mission, agg) ->
                        Text(
                            "• ${mission.take(60)}: ${VibeCostTracker.formatUsd(agg.costUsd)} دولار — ${agg.calls} استدعاء (غير مقاسة: ${agg.unmeasuredCalls})",
                            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                        )
                    }
                }
                if (costSnapshot.totalCalls == 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "لا استدعاءات مسجلة بعد — تُسجَّل تلقائياً عند تنفيذ مهام سحابية.",
                        color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        costSnapshot = VibeCostTracker.snapshot(context)
                        costDashboardMessage = "تم تحديث اللوحة."
                    }) {
                        Text("تحديث", fontFamily = CairoFont, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = {
                        if (confirmCostReset) {
                            VibeCostTracker.reset(context)
                            costSnapshot = VibeCostTracker.snapshot(context)
                            confirmCostReset = false
                            costDashboardMessage = "تمت إعادة تعيين سجل الاستهلاك."
                        } else {
                            confirmCostReset = true
                        }
                    }) {
                        Text(
                            if (confirmCostReset) "تأكيد المسح؟" else "إعادة تعيين",
                            fontFamily = CairoFont, fontSize = 11.sp
                        )
                    }
                }
                if (costDashboardMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(costDashboardMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
                }
            }
        }
        item {
            VibeCard(title = "نطاق صلاحيات الملفات") {
                Text(
                    "قائمة سماح تُفرض في حراس أدوات الكود (AgentToolGate). " +
                        "فارغة = بلا قيد (السلوك الحالي) — أضف بادئات مسارات أو فروعاً لتقييد الوكيل.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("بادئات المسارات المسموحة (مفصولة بفواصل)", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branchText,
                    onValueChange = { branchText = it },
                    label = { Text("الفروع المسموحة (مفصولة بفواصل)", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            AgentPrefs.putAllowedPathPrefixes(
                                context,
                                pathText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            )
                            AgentPrefs.putAllowedBranches(
                                context,
                                branchText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            )
                            scopeMessage = "تم حفظ نطاق الصلاحيات."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                    ) { Text("حفظ النطاق", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    if (scopeMessage.isNotBlank()) {
                        Text(scopeMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * تبويب «المشاريع» (12.10، المرتبة 2): كيان «مشروع فايب كودينج» الرفيع
 * (جلسة + مستودع نشط + ملفات معدلة) على Room الموجود عبر ProjectService،
 * + جلسات المحادثة كاملة (AgentChatSessionManager) + مستودعات GitHub
 * (AgentHubProjectsSection) + بطاقة مكتبة مشاريع المحتوى
 * (StudioLibraryScreen — تُفتح من هنا لا تُعاد كتابتها).
 */
@Composable
private fun VibeProjectsTab(onOpenLibrary: () -> Unit, onOpenChat: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by AgentChatSessionManager.sessions.collectAsState()
    val activeSessionId by AgentChatSessionManager.activeSessionId.collectAsState()
    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val savedRepos = remember(context, vaultTrigger) { RepoVaultManager.getRepositories(context) }
    val activeRepoId = remember(context, vaultTrigger) { RepoVaultManager.getActiveRepoId(context) }
    var vibeProjects by remember { mutableStateOf<List<VibeCodingProject>>(emptyList()) }
    var projectsMessage by remember { mutableStateOf("") }
    var showRepoDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VibeCodingProject?>(null) }

    fun reloadVibeProjects() {
        scope.launch { vibeProjects = VibeCodingProjects.list(context) }
    }

    LaunchedEffect(Unit) {
        AgentChatSessionManager.init(context)
        reloadVibeProjects()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "مشاريع فايب كودينج تربط جلسة محادثة بمستودع وملفات معدلة — " +
                    "والجلسات والمستودعات ومكتبة المحتوى كما هي أدناه.",
                color = QabasTheme.current.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val created = VibeCodingProjects.createFromActiveSession(context)
                            if (created != null) {
                                reloadVibeProjects()
                                projectsMessage = "تم إنشاء مشروع «${created.title}»."
                            } else {
                                projectsMessage = "لا توجد جلسة نشطة أو مهمة أو مستودع لإنشاء مشروع منها."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                ) {
                    Text("إنشاء مشروع من الجلسة النشطة", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { reloadVibeProjects() }) {
                    Text("تحديث", fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
            if (projectsMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(projectsMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
            }
        }
        // (12.15) قوالب مشاريع بادئة حقيقية — كل قالب ينشئ مشروعاً بملفاته الأولية.
        item {
            Text(
                "قوالب مشاريع جاهزة",
                color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
        }
        items(VibeTemplates.ALL, key = { it.id }) { template ->
            VibeTemplateCard(
                template = template,
                onCreated = { created ->
                    reloadVibeProjects()
                    projectsMessage = "تم إنشاء مشروع «${created.title}» من القالب — انسخ أمر البدء أو أرسله من المحادثة."
                    onOpenChat()
                }
            )
        }
        if (vibeProjects.isEmpty()) {
            item {
                Text(
                    "لا مشاريع فايب كودينج بعد — أنشئ واحداً من الجلسة النشطة أعلاه.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                )
            }
        } else {
            items(vibeProjects, key = { it.id }) { vibe ->
                VibeCard(title = vibe.title) {
                    Text("المستودع: ${vibe.repoFullName ?: "بلا مستودع"} • الفرع: ${vibe.branch ?: "—"}", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    Text("الجلسة: ${vibe.sessionId?.take(8) ?: "—"} • ملفات معدلة: ${vibe.modifiedFiles.size} • مطبقة: ${vibe.appliedPaths.size}", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    vibe.modifiedFiles.take(5).forEach { path ->
                        Text("• $path", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                    if (vibe.modifiedFiles.size > 5) {
                        Text("… و${vibe.modifiedFiles.size - 5} ملفات أخرى", color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // (12.12) ذاكرة الوكيل: ملخص + قرارات + ملفات مهمة.
                    VibeMemoryEditor(vibe = vibe, onSaved = { reloadVibeProjects() })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val json = VibeCodingProjects.buildExportJson(context, vibe)
                                VibeCodingProjects.shareExport(context, json, vibe.title)
                            }
                        }) {
                            Text("تصدير ومشاركة", fontFamily = CairoFont, fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { pendingDelete = vibe }) {
                            Text("حذف المشروع", fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("جلسات المحادثة (${sessions.size})", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (sessions.isEmpty()) {
            item {
                Text("لا جلسات محفوظة بعد.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                VibeCard(title = session.title) {
                    Text(
                        "رسائل: ${session.messageCount}" + if (session.id == activeSessionId) " • النشطة" else "",
                        color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                    )
                    if (session.id != activeSessionId) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = {
                            AgentChatSessionManager.switchSession(context, session.id)
                            Toast.makeText(context, "تم التبديل إلى جلسة «${session.title}»", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("تبديل إلى هذه الجلسة", fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("مستودعات GitHub", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                AgentHubProjectsSection(
                    repositories = savedRepos,
                    activeRepositoryId = activeRepoId,
                    onSelectRepository = {
                        RepoVaultManager.setActiveRepo(context, it.id)
                        RepoVaultManager.notifyVaultChanged()
                    },
                    onAddRepository = { showRepoDialog = true }
                )
            }
        }
        item {
            VibeCard(title = "مكتبة مشاريع المحتوى") {
                Text(
                    "مشاريع الفيديو/المحتوى وطابور المهام وسجل التوليدات — كما هي في مكتبة الاستوديو.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenLibrary,
                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                ) {
                    Text("فتح مكتبة المشاريع", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRepoDialog) {
        RepoSetupDialog(
            context = context,
            activeRepo = RepoVaultManager.getActiveRepo(context),
            onDismiss = { showRepoDialog = false }
        )
    }
    pendingDelete?.let { vibe ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف مشروع فايب كودينج؟", fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("سيُحذف مشروع «${vibe.title}» من Room. الجلسة والمستودع لا يُحذفان.", fontFamily = CairoFont) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        VibeCodingProjects.delete(context, vibe.id)
                        reloadVibeProjects()
                    }
                    pendingDelete = null
                }) { Text("حذف", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) { Text("إلغاء", fontFamily = CairoFont) }
            }
        )
    }
}

/**
 * محرر ذاكرة الوكيل لمشروع فايب كودينج (12.12): ملخص الجلسة + قرارات
 * المشروع + ملفات مهمة. تُحفظ على Room الموجود عبر
 * [VibeCodingProjects.updateMemory]، وتُمرَّر سياقاً للنموذج عند تنفيذ
 * مهمة جديدة من جلسة هذا المشروع (انظر executeAgentMissionInner).
 */
@Composable
private fun VibeMemoryEditor(vibe: VibeCodingProject, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summary by remember(vibe.id, vibe.memory.updatedAt) { mutableStateOf(vibe.memory.summary) }
    var decisions by remember(vibe.id, vibe.memory.updatedAt) { mutableStateOf(vibe.memory.decisions) }
    var importantFiles by remember(vibe.id, vibe.memory.updatedAt) { mutableStateOf(vibe.memory.importantFiles) }
    var newDecision by remember(vibe.id) { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "ذاكرة الوكيل — تُمرَّر سياقاً للنموذج عند تنفيذ مهمة جديدة من جلسة هذا المشروع.",
            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
        )
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("ملخص الجلسة", fontFamily = CairoFont, fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Text("قرارات المشروع:", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (decisions.isEmpty()) {
            Text("لا قرارات محفوظة بعد.", color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
        }
        decisions.forEach { decision ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "• $decision",
                    color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { decisions = decisions - decision }) {
                    Text("حذف", fontFamily = CairoFont, fontSize = 11.sp, color = QabasTheme.current.destructive)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newDecision,
                onValueChange = { newDecision = it },
                label = { Text("قرار جديد", fontFamily = CairoFont, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                val trimmed = newDecision.trim()
                if (trimmed.isNotBlank()) {
                    decisions = decisions + trimmed
                    newDecision = ""
                }
            }) { Text("إضافة", fontFamily = CairoFont, fontSize = 11.sp) }
        }
        Text("ملفات مهمة:", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val candidates = (vibe.modifiedFiles + vibe.appliedPaths + importantFiles).distinct()
        if (candidates.isEmpty()) {
            Text(
                "لا ملفات بعد — تظهر هنا ملفات المشروع المعدلة عند وجودها.",
                color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 11.sp
            )
        } else {
            candidates.forEach { path ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = path in importantFiles,
                        onCheckedChange = { checked ->
                            importantFiles = if (checked) importantFiles + path else importantFiles - path
                        }
                    )
                    Text(
                        path,
                        color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                summary = vibe.prompt.take(500)
                importantFiles = vibe.modifiedFiles.take(10)
            }) { Text("ملء تلقائي من المشروع", fontFamily = CairoFont, fontSize = 11.sp) }
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = VibeCodingProjects.updateMemory(
                            context, vibe.id,
                            VibeAgentMemory(
                                summary = summary.trim(),
                                decisions = decisions,
                                importantFiles = importantFiles,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        message = if (ok) "تم حفظ ذاكرة الوكيل." else "تعذر حفظ الذاكرة — حاول مجدداً."
                        saving = false
                        if (ok) onSaved()
                    }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
            ) {
                Text(
                    if (saving) "يُحفظ…" else "حفظ الذاكرة",
                    fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        if (message.isNotBlank()) {
            Text(message, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
        }
    }
}

/**
 * بطاقة قالب بادئ (12.15): إنشاء مشروع من القالب + نسخ أمر البدء.
 * بعد الإنشاء ينتقل المستخدم إلى تبويب المحادثة لإرسال أمر البدء.
 */
@Composable
private fun VibeTemplateCard(template: VibeTemplate, onCreated: (VibeCodingProject) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var creating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    VibeCard(title = template.title) {
        Text(template.description, color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))
        template.files.keys.forEach { path ->
            Text("• $path", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    creating = true
                    scope.launch {
                        val created = VibeCodingProjects.createFromTemplate(context, template)
                        creating = false
                        if (created != null) {
                            onCreated(created)
                        } else {
                            message = "تعذر إنشاء المشروع — حاول مجدداً."
                        }
                    }
                },
                enabled = !creating,
                colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
            ) {
                Text(
                    if (creating) "يُنشأ…" else "إنشاء مشروع من القالب",
                    fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(template.startPrompt))
                Toast.makeText(context, "تم نسخ أمر البدء.", Toast.LENGTH_SHORT).show()
            }) {
                Text("نسخ أمر البدء", fontFamily = CairoFont, fontSize = 11.sp)
            }
        }
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(message, color = QabasTheme.current.destructive, fontFamily = CairoFont, fontSize = 11.sp)
        }
    }
}

/**
 * تبويب «المعاينة» (المرتبة 3، 12.13): معاينة المقترحات جنباً إلى جنب
 * مع الملف الحالي قبل التطبيق + معاينة WebView للمخرجات النصية/HTML
 * المولدة حيث ينطبق + اختبار الكود المولد عبر CI البعيد فقط
 * (trigger_build/build_status/AgentBuildVerifier) مع ربط سجل الفشل
 * بالمهمة. لا تشغيل محلي لتطبيق أندرويد — لا يوجد — ولا يُدَّعى.
 * `generate_unit_test` يولّد نص اختبار فقط ولا يشغّله.
 */
@Composable
private fun VibePreviewTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val activeRepo = remember(context, vaultTrigger) { RepoVaultManager.getActiveRepo(context) }
    var runs by remember { mutableStateOf<List<BuildRunSnapshot>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var triggering by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var showRepoDialog by remember { mutableStateOf(false) }
    var failedLogs by remember { mutableStateOf("") }
    // (12.13) المهمة النشطة ومقترحاتها وحالة الملفات الحالية.
    var mission by remember { mutableStateOf<AgentMissionSession?>(null) }
    var fileStates by remember { mutableStateOf<Map<String, VibeCurrentFile>>(emptyMap()) }
    var applyingDiffPath by remember { mutableStateOf<String?>(null) }
    var previewMessage by remember { mutableStateOf("") }
    // (12.13) اختبار المهمة عبر CI البعيد.
    var verifying by remember { mutableStateOf(false) }
    var verifyJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var verifyMessage by remember { mutableStateOf("") }
    // (12.13) توليد نص اختبار وحدة.
    var testFn by remember { mutableStateOf("") }
    var testSnippet by remember { mutableStateOf("") }

    fun persistMission(updated: AgentMissionSession) {
        mission = updated
        AgentPrefs.saveActiveMission(context, encodeMissionSession(updated))
    }

    fun loadMission() {
        mission = decodeMissionSession(AgentPrefs.loadActiveMission(context))
    }

    fun fetchCurrentFile(path: String) {
        val repo = activeRepo ?: return
        if (fileStates[path] is VibeCurrentFile.Loading) return
        fileStates = fileStates + (path to VibeCurrentFile.Loading)
        scope.launch {
            val content = runCatching {
                val client = GitHubRepoClient(repo.owner, repo.repo, repo.token)
                val res = client.getFileContent(path, repo.defaultBranch)
                res?.content?.takeIf { it.isNotBlank() }?.let {
                    String(
                        android.util.Base64.decode(
                            it.replace("\\s".toRegex(), ""),
                            android.util.Base64.DEFAULT
                        ),
                        Charsets.UTF_8
                    )
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            fileStates = fileStates + (
                path to if (content == null) {
                    VibeCurrentFile.Missing("لا نسخة حالية في المستودع — ملف جديد أو تعذر الجلب.")
                } else {
                    VibeCurrentFile.Ready(content)
                }
            )
        }
    }

    fun refreshRuns() {
        val repo = activeRepo ?: return
        loading = true
        scope.launch {
            runs = runCatching {
                AgentBuildVerifier.fetchRuns(repo.owner, repo.repo, repo.token)
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            if (runs == null) statusMessage = "تعذر جلب حالة البناء — تحقق من الاتصال والصلاحيات."
            loading = false
        }
    }

    LaunchedEffect(activeRepo?.id) { refreshRuns() }
    LaunchedEffect(Unit) { loadMission() }
    LaunchedEffect(mission?.id, activeRepo?.id) {
        mission?.proposedDiffs?.forEach { fetchCurrentFile(it.filePath) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "معاينة المقترحات جنباً إلى جنب مع الملف الحالي قبل التطبيق، " +
                    "ومعاينة مخرجات HTML، واختبار الكود المولد عبر CI البعيد فقط — " +
                    "لا تشغيل محلي لتطبيق أندرويد في التطبيق.",
                color = QabasTheme.current.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp
            )
        }
        // (12.13) معاينة المقترحات جنباً إلى جنب مع الملف الحالي.
        item {
            Text(
                "معاينة المقترحات — قبل التطبيق",
                color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
        run {
            val diffs = mission?.proposedDiffs.orEmpty()
            if (mission == null) {
                item {
                    Text(
                        "لا مهمة نشطة — نفّذ مهمة من تبويب المحادثة أولاً لتظهر مقترحاتها هنا.",
                        color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                    )
                }
            } else if (diffs.isEmpty()) {
                item {
                    Text(
                        "المهمة النشطة بلا مقترحات فروقات بعد.",
                        color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp
                    )
                }
            } else {
                items(diffs, key = { it.filePath }) { diff ->
                    VibeCard(title = "مقترح: ${diff.filePath}") {
                        Text(
                            "إضافات: ${diff.additions} • حذف: ${diff.deletions}",
                            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الملف الحالي", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                when (val state = fileStates[diff.filePath]) {
                                    null -> Text("—", color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                                    VibeCurrentFile.Loading -> Text("يُجلب الملف الحالي…", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                    is VibeCurrentFile.Missing -> Text(state.reason, color = QabasTheme.current.destructive, fontFamily = CairoFont, fontSize = 11.sp)
                                    is VibeCurrentFile.Ready -> Text(
                                        state.content,
                                        color = QabasTheme.current.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                        modifier = Modifier.heightIn(max = 220.dp)
                                            .horizontalScroll(rememberScrollState())
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("المقترح", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    diff.diffText,
                                    color = QabasTheme.current.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                    modifier = Modifier.heightIn(max = 220.dp)
                                        .horizontalScroll(rememberScrollState())
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val repo = activeRepo
                                    if (repo == null || repo.token.isBlank() || repo.defaultBranch.isBlank()) {
                                        previewMessage = "اربط مستودع GitHub أولاً لحفظ المقترح."
                                    } else {
                                        applyingDiffPath = diff.filePath
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val savedPath = AgentPatchKit.saveProposalToRepo(repo, diff)
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                applyingDiffPath = null
                                                val current = mission
                                                if (savedPath != null && current != null) {
                                                    persistMission(current.copy(appliedPaths = current.appliedPaths + savedPath))
                                                    previewMessage = "تم حفظ المقترح في المستودع (مجلد qabas-agent-proposals) — لم يُستبدل الملف الأصلي."
                                                } else {
                                                    previewMessage = "تعذر حفظ المقترح — تأكد من الصلاحيات والفرع."
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = applyingDiffPath == null,
                                colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                            ) {
                                Text(
                                    if (applyingDiffPath == diff.filePath) "يُحفظ…" else "تطبيق المقترح",
                                    fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(onClick = {
                                val current = mission ?: return@OutlinedButton
                                persistMission(
                                    current.copy(
                                        proposedDiffs = current.proposedDiffs.filterNot { it.filePath == diff.filePath }
                                    )
                                )
                                previewMessage = "تم تجاهل المقترح."
                            }) {
                                Text("تجاهل", fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        if (previewMessage.isNotBlank()) {
            item {
                Text(previewMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
            }
        }
        // (12.13) معاينة المخرجات المولدة (HTML في WebView حيث ينطبق).
        item {
            Text(
                "معاينة المخرجات المولدة",
                color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            val output = mission?.steps?.firstOrNull { it.toolName == "code_synthesis" }?.outputSnippet
                ?: mission?.proposedDiffs?.firstOrNull()?.diffText
            if (output.isNullOrBlank()) {
                Text("لا مخرجات مولدة بعد في المهمة النشطة.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            } else if (vibeLooksLikeHtmlDocument(output)) {
                Text("مخرج HTML — يُعرض في WebView (بلا JavaScript).", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                VibeHtmlPreview(html = output)
            } else if (vibeLooksLikeHtmlFragment(output)) {
                Text("مخرج HTML جزئي — يُعرض داخل هيكل بسيط.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                VibeHtmlPreview(html = "<html><head><meta charset=\"utf-8\"></head><body>$output</body></html>")
            } else {
                Text(
                    "المخرج نص/كود — لا HTML فيه، فيُعرض نصاً فقط (لا تشغيل محلي).",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    output.take(4000),
                    color = QabasTheme.current.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    modifier = Modifier.heightIn(max = 220.dp)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                )
            }
        }
        // (12.13) اختبار الكود المولد عبر CI البعيد — مع ربط سجل الفشل بالمهمة.
        item {
            Text(
                "اختبار الكود المولد",
                color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            VibeCard(title = "تشغيل الاختبارات عبر CI البعيد") {
                Text(
                    "الاختبار عبر CI البعيد فقط — لا تشغيل محلي للكود المولد في التطبيق. " +
                        "نتيجة التحقق وسجل الفشل تُحفظ في خطوة داخل المهمة النشطة.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val base = mission
                            if (base == null) {
                                verifyMessage = "لا مهمة نشطة — نفّذ مهمة من تبويب المحادثة أولاً."
                                return@Button
                            }
                            verifying = true
                            verifyMessage = "يُطلق البناء على GitHub Actions ويُراقب حتى الاكتمال…"
                            verifyJob = scope.launch {
                                try {
                                    val updated = runBuildVerification(context, base) { m -> persistMission(m) }
                                    persistMission(updated)
                                    verifyMessage = updated.finalReport.substringAfterLast("\n\n")
                                        .takeIf { it.isNotBlank() }
                                        ?: "انتهى التحقق — راجع خطوة التحقق في المهمة."
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    verifyMessage = "أُلغي التحقق."
                                } finally {
                                    verifying = false
                                }
                            }
                        },
                        enabled = !verifying,
                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                    ) {
                        Text(
                            if (verifying) "يُتحقق…" else "تشغيل الاختبارات عبر CI",
                            fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (verifying) {
                        OutlinedButton(onClick = {
                            verifyJob?.cancel()
                            verifying = false
                            verifyMessage = "أُلغي التحقق."
                        }) {
                            Text("إلغاء", fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }
                }
                if (verifyMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(verifyMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
                }
                mission?.steps?.lastOrNull { it.id == "ci_exec_verify" }?.let { step ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        step.outputSnippet.take(3000),
                        color = QabasTheme.current.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        modifier = Modifier.heightIn(max = 220.dp)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        // (12.13) توليد نص اختبار وحدة فقط — لا يشغّله.
        item {
            VibeCard(title = "توليد نص اختبار وحدة") {
                Text(
                    "يولّد نص اختبار فقط — لا يشغّله. شغّله عبر CI أعلاه بعد إضافة النص للمستودع.",
                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = testFn,
                        onValueChange = { testFn = it },
                        label = { Text("اسم الدالة", fontFamily = CairoFont, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { testSnippet = CodeTools.generateUnitTestSnippet(testFn.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                    ) {
                        Text("توليد", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (testSnippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        testSnippet,
                        color = QabasTheme.current.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        modifier = Modifier.heightIn(max = 200.dp)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        if (activeRepo == null) {
            item {
                VibeCard(title = "لا يوجد مستودع نشط") {
                    Text("اربط مستودع GitHub أولاً لعرض حالة البناء وتشغيله.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showRepoDialog = true }) {
                        Text("إعداد المستودع", fontFamily = CairoFont, fontSize = 12.sp)
                    }
                }
            }
        } else {
            item {
                VibeCard(title = "المستودع النشط: ${activeRepo.fullName}") {
                    Text("الفرع: ${activeRepo.defaultBranch}", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                triggering = true
                                scope.launch {
                                    val ok = AgentBuildVerifier.triggerWorkflow(
                                        activeRepo.owner, activeRepo.repo, activeRepo.token,
                                        activeRepo.defaultBranch
                                    )
                                    statusMessage = if (ok) "أُرسل أمر البناء إلى GitHub Actions." else "تعذر تشغيل البناء — تحقق من الصلاحيات وملف android-ci.yml."
                                    triggering = false
                                    refreshRuns()
                                }
                            },
                            enabled = !triggering,
                            colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary, contentColor = QabasTheme.current.onPrimary)
                        ) {
                            Text(if (triggering) "يُرسل…" else "تشغيل البناء", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { refreshRuns() }, enabled = !loading) {
                            Text(if (loading) "يُحدَّث…" else "تحديث الحالة", fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }
                    if (statusMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(statusMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }
            }
            if (loading && runs == null) {
                item {
                    Text("يُجلب حالة البناء…", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            } else if (runs.isNullOrEmpty()) {
                item {
                    Text("لا تشغيلات بعد — شغّل البناء أولاً.", color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            } else {
                items(runs!!, key = { it.id }) { run ->
                    VibeCard(title = run.name) {
                        Text(AgentBuildVerifier.describe(run), color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "الحالة: ${run.status} • النتيجة: ${run.conclusion ?: "—"} • الكوميت: ${run.headSha.take(7)}",
                            color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openVibeUrl(context, run.htmlUrl) }) {
                                Text("فتح التشغيل", fontFamily = CairoFont, fontSize = 11.sp)
                            }
                            if (run.outcome == BuildRunOutcome.FAILURE) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        failedLogs = AgentBuildVerifier.fetchFailedLogs(
                                            activeRepo.owner, activeRepo.repo, activeRepo.token, run.id
                                        )
                                        if (failedLogs.isBlank()) failedLogs = "تعذر جلب سجل الفشل."
                                    }
                                }) {
                                    Text("سجل الفشل", fontFamily = CairoFont, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                if (failedLogs.isNotBlank()) {
                    item {
                        VibeCard(title = "سجل الفشل") {
                            Text(failedLogs, color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showRepoDialog) {
        RepoSetupDialog(
            context = context,
            activeRepo = activeRepo,
            onDismiss = { showRepoDialog = false }
        )
    }
}

@Composable
private fun VibeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) QabasTheme.current.primary.copy(alpha = 0.18f) else QabasTheme.current.secondarySurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) QabasTheme.current.primary else QabasTheme.current.separator
        ),
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) QabasTheme.current.primary else QabasTheme.current.textSecondary,
                fontFamily = CairoFont,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}
