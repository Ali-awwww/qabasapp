package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import com.qabas.app.ui.agent.AgentDesign
import com.qabas.app.ui.agent.AgentNeonButton
import com.qabas.app.ui.agent.AgentNeonCard
import com.qabas.app.ui.agent.AgentSectionHeader
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.HudCornerDecor
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


enum class DashboardSection {
    MAIN,
    AGENT_BUILD_HUB,
    CLOUD_API_HUB,
    HEALTH_DIAGNOSTICS_HUB,
    BUG_REPORTS,
    USERS_COMMUNITY_HUB,
    BUSINESS_REVENUE_HUB,
    SYSTEM_IDENTITY_HUB,
    AI_MODEL_SWITCHER,
    LIVE_KEY_STATUS_HUB, REQUESTS, SYSTEM_CONTROLS, NOTIFICATIONS, USERS, STATS,
    ACCOUNT_SETTINGS, MONEY_CENTER, DEV_STUDIO_SIGNATURE,
    STYLE_BRAIN,
    API_KEYS, AUDIT_LOG, BACKUP, PRODUCTION_PIPELINE, BUILD_CENTER, HEALTH_CENTER, AGENT_ROOM, CONNECTORS,
    CRASH_LOGS, DEV_CHAT
}

data class DevUser(
    val id: String,
    val name: String,
    val email: String,
    val type: String, // "مطور", "خاص", "Freemium"
    val projectCount: Int,
    val regDate: String,
    val isSuspended: Boolean = false,
    val customId: String? = null,
    val accountNumber: String? = null
)

/** أدوات تنسيق البيانات الحقيقية لشاشة المطور. */
object DevDashboardFormatters {
    private val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())

    /**
     * ينسّق تاريخ التسجيل من حقل `createdAt` الموجود في Firestore (epoch millis)
     * أو Supabase (نص ISO). يعود بنص "غير معروف" عند غياب القيمة.
     */
    fun formatRegDate(createdAt: Any?): String {
        val mills = when (createdAt) {
            is Number -> createdAt.toLong()
            is String -> parseCreatedAtString(createdAt)
            else -> 0L
        }
        if (mills <= 0L) return "غير معروف"
        return runCatching { dateFormat.format(java.util.Date(mills)) }.getOrDefault("غير معروف")
    }

    /** يجرّب نصوص ISO للتواريخ مع خيار المللي، ثم الرقم الخام (epoch millis). */
    private fun parseCreatedAtString(raw: String): Long {
        val isoFormats = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        )
        for (fmt in isoFormats) {
            val parsed = runCatching { fmt.parse(raw)?.time }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return runCatching { raw.toLong() }.getOrDefault(0L)
    }

    /** تنسيق زمن الاستجابة الحقيقي بالثواني أو المللي ثانية. */
    fun formatLatency(ms: Long): String = when {
        ms >= 1000 -> String.format(java.util.Locale.getDefault(), "%.1fs", ms / 1000.0)
        ms > 0 -> "${ms}ms"
        else -> "--"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperDashboardScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit = {}, onNavigateTo: (AppState) -> Unit = {}) {
    val coroutineScope = rememberCoroutineScope()
    var currentSection by remember { mutableStateOf(DashboardSection.MAIN) }

    // قفزة قادمة من شاشة المحادثة (شريط الإجراءات الذكي): انتقال مباشر للقسم المطلوب
    val gateContext = LocalContext.current
    LaunchedEffect(Unit) {
        val target = gateContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString("dev_dash_target", null)
        if (target != null) {
            runCatching { currentSection = DashboardSection.valueOf(target) }
            gateContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                .edit().remove("dev_dash_target").apply()
        }
    }
    // حارس الوصول: اللوحة للمالك فقط (لا يُمنح علم is_developer افتراضياً لأجهزة جديدة)
    var accessAllowed by remember { mutableStateOf(AdminGuard.isDashboardAccessAllowed(gateContext)) }
    
    // تأكيد إضافي عبر بيانات Supabase Auth الوصفية (Metadata)
    LaunchedEffect(Unit) {
        if (SupabaseServices.isSupabaseAvailable) {
            val isAuthAdmin = AdminGuard.isAuthAdmin()
            if (isAuthAdmin) {
                accessAllowed = true
            }
        }
    }

    if (!accessAllowed) {
        Column(
            modifier = Modifier.fillMaxSize().background(DeepSlate).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("لوحة تحكم المطور محمية 🔒", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "الوصول مخصص لحساب المالك فقط. سجّل الدخول ببريد المالك، أو استخدم جهازاً يحمل علم is_developer المكتوب سابقاً.",
                color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                Text("رجوع", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val handleBack = {
        if (currentSection == DashboardSection.MAIN) {
            onBack()
        } else {
            currentSection = DashboardSection.MAIN
        }
    }

    androidx.activity.compose.BackHandler(onBack = handleBack)

    Box(modifier = Modifier.fillMaxSize()) {
        // Spatial HUD Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val primaryColor = GoldPrimary
            val stepX = 50.dp.toPx()
            val stepY = 50.dp.toPx()
            val w = size.width
            val h = size.height

            for (i in 0..(w / stepX).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, h),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            for (j in 0..(h / stepY).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(0f, j * stepY),
                    end = Offset(w, j * stepY),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = DeepSlate
        ) { padding ->
        val context = LocalContext.current
        var requests by remember { mutableStateOf(AppRequestService.getRequests(context, isDeveloper = true)) }

        // قائمة المستخدمين الحقيقية — تشمل المسجلين محلياً وسحابياً وتتزامن حياً
        val devUsers = remember { mutableStateListOf<DevUser>() }
        val localAccounts by LocalAccountRegistry.accountsFlow.collectAsState()

        fun mergeUsers(localList: List<LocalAccountRegistry.AccountRecord>, cloudList: List<DevUser> = emptyList()) {
            val seenIds = mutableSetOf<String>()
            val seenEmails = mutableSetOf<String>()
            val merged = mutableListOf<DevUser>()

            // 1. إضافة الحسابات المحلية أولاً للسرعة وظهور العداد فورياً
            localList.forEach { acc ->
                val lowerEmail = acc.email.lowercase(java.util.Locale.ROOT)
                if (lowerEmail.isNotBlank() && seenEmails.add(lowerEmail)) {
                    seenIds.add(acc.id)
                    merged.add(acc.toDevUser())
                }
            }

            // 2. دمج الحسابات السحابية
            cloudList.forEach { u ->
                val lowerEmail = u.email.lowercase(java.util.Locale.ROOT)
                if (lowerEmail.isNotBlank() && lowerEmail !in seenEmails && seenIds.add(u.id)) {
                    seenEmails.add(lowerEmail)
                    merged.add(u)
                }
            }

            devUsers.clear()
            devUsers.addAll(merged)
        }

        // تحديث حي فوري عند تغير الحسابات المسجلة محلياً في الهاتف
        LaunchedEffect(localAccounts) {
            mergeUsers(localAccounts, devUsers.toList())
        }

        LaunchedEffect(Unit) {
            val initialLocal = LocalAccountRegistry.getAllAccounts(context)
            mergeUsers(initialLocal)

            // 3. جلب المستخدمين من Supabase (الخادم السحابي الأساسي) ودمجهم
            if (SupabaseServices.isSupabaseAvailable) {
                try {
                    val supabaseUsers = SupabaseServices.Database.getAllUsers()
                    val cloudList = mutableListOf<DevUser>()

                    supabaseUsers.forEach { row ->
                        // تجاهل الصفوف اليتيمة بلا بريد (صفوف وهمية من جلسات قديمة) — لا تحتسب كمستخدم
                        val cleanRowEmail = row.email?.trim().orEmpty()
                        if (cleanRowEmail.isBlank() || !cleanRowEmail.contains("@")) return@forEach
                        val targetUid = row.externalId ?: row.id
                        val rowEmail = cleanRowEmail.lowercase(java.util.Locale.ROOT)
                        val projectCount = runCatching {
                            SupabaseServices.Database.getUserProjects(targetUid).size
                        }.getOrDefault(0)
                        val roleDisplay = when (row.type.lowercase(java.util.Locale.ROOT)) {
                            "developer", "مطور" -> "مطور"
                            "special", "خاص" -> "خاص"
                            else -> if (row.type.isNotBlank()) row.type else "مجاني"
                        }
                        val cloudUser = DevUser(
                            id = targetUid,
                            name = row.name?.ifBlank { null } ?: cleanRowEmail.substringBefore('@'),
                            email = cleanRowEmail,
                            type = roleDisplay,
                            projectCount = projectCount,
                            regDate = DevDashboardFormatters.formatRegDate(row.createdAt),
                            isSuspended = row.strikes >= 5 || row.status == "suspended",
                            customId = row.customId ?: row.email?.substringBefore('@')
                        )
                        cloudList.add(cloudUser)

                        // توثيق الحساب السحابي محلياً أيضاً دون تكرار
                        LocalAccountRegistry.registerOrUpdateAccount(
                            context = context,
                            account = LocalAccountRegistry.AccountRecord(
                                id = cloudUser.id,
                                name = cloudUser.name,
                                email = cloudUser.email,
                                customId = cloudUser.customId ?: "",
                                type = cloudUser.type,
                                status = if (cloudUser.isSuspended) "suspended" else "active",
                                projectCount = cloudUser.projectCount
                            )
                        )
                    }

                    // دمج البيانات السحابية مع المحلية الحالية
                    mergeUsers(LocalAccountRegistry.getAllAccounts(context), cloudList)
                } catch (e: Exception) {
                    android.util.Log.w("DeveloperDashboard", "Supabase users fetch failed: ${e.message}")
                }
            }
        }

        val devLogs = SystemLogsManager.logs

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentSection) {
                DashboardSection.MAIN -> {
                    DashboardMainGrid(
                        onBack = handleBack,
                        onAgentBuildHub = { currentSection = DashboardSection.AGENT_BUILD_HUB },
                        onCloudApiHub = { currentSection = DashboardSection.CLOUD_API_HUB },
                        onHealthDiagnosticsHub = { currentSection = DashboardSection.HEALTH_DIAGNOSTICS_HUB },
                        onUsersCommunityHub = { currentSection = DashboardSection.USERS_COMMUNITY_HUB },
                        onBusinessRevenueHub = { 
                            requests = AppRequestService.getRequests(context, isDeveloper = true)
                            currentSection = DashboardSection.BUSINESS_REVENUE_HUB 
                        },
                        onSystemIdentityHub = { currentSection = DashboardSection.SYSTEM_IDENTITY_HUB },
                        onAiModelSwitcher = { currentSection = DashboardSection.AI_MODEL_SWITCHER },
                        onBugReports = { currentSection = DashboardSection.BUG_REPORTS },
                        onDevChat = { currentSection = DashboardSection.DEV_CHAT },
                        userCount = devUsers.size
                    )
                }

                // 1. غرفة الوكيل ومركز البناء
                DashboardSection.AGENT_BUILD_HUB, DashboardSection.AGENT_ROOM, DashboardSection.BUILD_CENTER -> {
                    val initialTab = if (currentSection == DashboardSection.BUILD_CENTER) 1 else 0
                    AgentBuildHubScreen(
                        initialTab = initialTab,
                        context = context,
                        onBack = { currentSection = DashboardSection.MAIN },
                        onNavigateSection = { currentSection = it },
                        onNavigateTo = onNavigateTo
                    )
                }

                // 2. مركز المفاتيح والربط السحابي
                DashboardSection.CLOUD_API_HUB, DashboardSection.API_KEYS, DashboardSection.LIVE_KEY_STATUS_HUB, DashboardSection.CONNECTORS, DashboardSection.AI_MODEL_SWITCHER -> {
                    val initialTab = when (currentSection) {
                        DashboardSection.LIVE_KEY_STATUS_HUB -> 1
                        DashboardSection.CONNECTORS -> 2
                        DashboardSection.AI_MODEL_SWITCHER -> 3
                        else -> 0
                    }
                    CloudApiHubScreen(
                        initialTab = initialTab,
                        context = context,
                        onBack = { currentSection = DashboardSection.MAIN },
                        onNavigateSection = { currentSection = it }
                    )
                }

                // 3. مركز الصحة والتشخيص الشامل
                DashboardSection.HEALTH_DIAGNOSTICS_HUB, DashboardSection.HEALTH_CENTER, DashboardSection.CRASH_LOGS, DashboardSection.BUG_REPORTS, DashboardSection.PRODUCTION_PIPELINE, DashboardSection.AUDIT_LOG -> {
                    val initialTab = when (currentSection) {
                        DashboardSection.CRASH_LOGS -> 2
                        DashboardSection.BUG_REPORTS -> 3
                        DashboardSection.PRODUCTION_PIPELINE -> 4
                        DashboardSection.AUDIT_LOG -> 5
                        else -> 0
                    }
                    HealthDiagnosticsHubScreen(
                        initialTab = initialTab,
                        context = context,
                        devLogs = devLogs,
                        onBack = { currentSection = DashboardSection.MAIN },
                        onNavigateSection = { currentSection = it }
                    )
                }

                // 4. إدارة المستخدمين والمجتمع
                DashboardSection.USERS_COMMUNITY_HUB, DashboardSection.USERS, DashboardSection.NOTIFICATIONS, DashboardSection.DEV_CHAT -> {
                    val initialTab = when (currentSection) {
                        DashboardSection.NOTIFICATIONS -> 1
                        DashboardSection.DEV_CHAT -> 2
                        else -> 0
                    }
                    UsersCommunityHubScreen(
                        initialTab = initialTab,
                        context = context,
                        devUsers = devUsers,
                        onBack = { currentSection = DashboardSection.MAIN },
                        onNavigateSection = { currentSection = it }
                    )
                }

                // 5. مركز الأعمال والإيرادات
                DashboardSection.BUSINESS_REVENUE_HUB, DashboardSection.MONEY_CENTER, DashboardSection.REQUESTS, DashboardSection.STATS -> {
                    val initialTab = when (currentSection) {
                        DashboardSection.REQUESTS -> 1
                        DashboardSection.STATS -> 2
                        else -> 0
                    }
                    BusinessRevenueHubScreen(
                        initialTab = initialTab,
                        context = context,
                        requests = requests,
                        userCount = devUsers.size,
                        onOpenChat = onOpenChat,
                        onRefreshRequests = { requests = AppRequestService.getRequests(context, isDeveloper = true) },
                        onBack = { currentSection = DashboardSection.MAIN },
                        onNavigateSection = { currentSection = it }
                    )
                }

                // 6. نظام المطور والهوية الفنية
                DashboardSection.SYSTEM_IDENTITY_HUB, DashboardSection.SYSTEM_CONTROLS, DashboardSection.DEV_STUDIO_SIGNATURE, DashboardSection.ACCOUNT_SETTINGS, DashboardSection.STYLE_BRAIN, DashboardSection.BACKUP -> {
                    val initialTab = when (currentSection) {
                        DashboardSection.DEV_STUDIO_SIGNATURE -> 1
                        DashboardSection.ACCOUNT_SETTINGS -> 3
                        DashboardSection.STYLE_BRAIN -> 4
                        DashboardSection.BACKUP -> 5
                        else -> 0
                    }
                    SystemIdentityHubScreen(
                        initialTab = initialTab,
                        context = context,
                        onBack = { currentSection = DashboardSection.MAIN },
                        onNavigateSection = { currentSection = it }
                    )
                }
            }
        }
    }
}
}

// =========================================================================
// 🏛️ شاشات مراكز القيادة الستة المدمجة (6 Unified Developer Command Hubs)
// =========================================================================

@Composable
fun SpatialHubNavigationStrip(
    currentHub: DashboardSection,
    onSelectHub: (DashboardSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hub_strip_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val hubs = listOf(
        Triple(DashboardSection.AGENT_BUILD_HUB, "🤖 الوكيل والبناء", Color(0xFF8B5CF6)),
        Triple(DashboardSection.CLOUD_API_HUB, "⚡ المفاتيح والسحابة", GoldPrimary),
        Triple(DashboardSection.HEALTH_DIAGNOSTICS_HUB, "🩺 التشخيص والصحة", Color(0xFF0EA5E9)),
        Triple(DashboardSection.USERS_COMMUNITY_HUB, "👥 المستخدمون", Color(0xFF10B981)),
        Triple(DashboardSection.BUSINESS_REVENUE_HUB, "💰 الأعمال والطلبات", Color(0xFFF59E0B)),
        Triple(DashboardSection.SYSTEM_IDENTITY_HUB, "⚙️ النظام والهوية", Color(0xFFEC4899))
    )
    Surface(
        color = Color(0xFF070B14).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(0xFF1E293B).copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            hubs.forEach { (section, label, color) ->
                val isSelected = currentHub == section
                Surface(
                    color = if (isSelected) color.copy(alpha = 0.22f) else CardSurface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        if (isSelected) 1.2.dp else 1.dp,
                        if (isSelected) color.copy(alpha = 0.7f + (0.3f * pulseGlow)) else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.clickable { onSelectHub(section) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevHubScaffold(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    currentHub: DashboardSection? = null,
    onNavigateSection: ((DashboardSection) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.18f))
                                .border(1.2.dp, accentColor.copy(alpha = 0.45f), CutCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(title, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                            Text(subtitle, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                actions = {
                    if (currentHub != null && onNavigateSection != null) {
                        IconButton(onClick = { onNavigateSection(DashboardSection.MAIN) }) {
                            Icon(Icons.Default.Dashboard, contentDescription = "الرئيسية", tint = GoldPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepSlate)
        ) {
            // Spatial Navigation Quick-Bar across all 6 hubs
            if (currentHub != null && onNavigateSection != null) {
                SpatialHubNavigationStrip(
                    currentHub = currentHub,
                    onSelectHub = onNavigateSection
                )
            }

            Surface(
                color = CardSurface,
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = GoldPrimary,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = accentColor,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { onTabSelected(index) },
                            text = {
                                Text(
                                    text = tabTitle,
                                    color = if (isSelected) accentColor else TextSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
fun AgentBuildHubScreen(
    initialTab: Int = 0,
    context: Context,
    onBack: () -> Unit,
    onNavigateSection: (DashboardSection) -> Unit,
    onNavigateTo: (AppState) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("🤖 مركز الوكيل (شاشة موحدة)", "🏗️ مركز البناء و APK (Build Center)")

    // رجوع ذكي: إذا كان في تبويب فرعي، العودة للتبويب الرئيسي أولاً
    androidx.activity.compose.BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val activeRepo = remember(vaultTrigger) { RepoVaultManager.getActiveRepo(context) }
    val (engineProvider, _) = remember(vaultTrigger) { UniversalKeyBrain.getActiveEngine() }

    DevHubScaffold(
        title = "غرفة الوكيل ومركز البناء",
        subtitle = "التطوير الذكي المباشر والأتمتة وبناء حزم APK",
        icon = Icons.Default.SmartToy,
        accentColor = Color(0xFF8B5CF6),
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        currentHub = DashboardSection.AGENT_BUILD_HUB,
        onNavigateSection = onNavigateSection
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ══════════════════════════════════════════════════════════════
            // 🌌 شريط التحكم والجاهزية السيبراني الموحد (SPATIAL MISSION CONTROL HUD)
            // ══════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color(0xEE060C18),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00F0FF).copy(alpha = 0.5f),
                            Color(0xFF8B5CF6).copy(alpha = 0.35f),
                            Color(0xFF10B981).copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    HudCornerDecor(modifier = Modifier.matchParentSize(), color = Color(0xFF00F0FF).copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // مؤشر المحرك الذكي النشط
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (engineProvider != DetectedProvider.NONE) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (engineProvider != DetectedProvider.NONE) "محرك: ${engineProvider.displayName}" else "المحرك: غير متصل",
                                color = if (engineProvider != DetectedProvider.NONE) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }

                        // مؤشر المستودع النشط
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                activeRepo?.let { "${it.owner}/${it.repo}" } ?: "لم يُحدد مستودع",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }

                        // أزرار التنقل السريعة بين الأدوات
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (selectedTab == 0) Color(0xFF0F3A2A) else Color(0xFF131F33),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, if (selectedTab == 0) Color(0xFF10B981) else Color(0xFF334155)),
                                modifier = Modifier.clickable { onNavigateTo(AppState.AI_STUDIO_AGENT) }
                            ) {
                                Text(
                                    "⚡ الوكيل ↗",
                                    color = if (selectedTab == 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                color = if (selectedTab == 1) Color(0xFF311042) else Color(0xFF131F33),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, if (selectedTab == 1) Color(0xFFA855F7) else Color(0xFF334155)),
                                modifier = Modifier.clickable { selectedTab = 1 }
                            ) {
                                Text(
                                    "🏗️ البناء",
                                    color = if (selectedTab == 1) Color(0xFFC084FC) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Surface(
                                color = Color(0xFF0C243B),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF00E5FF)),
                                modifier = Modifier.clickable { onNavigateTo(AppState.AI_STUDIO_AGENT) }
                            ) {
                                Text(
                                    "🛸 AI Studio",
                                    color = Color(0xFF00E5FF),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> AgentRoomRedirectCard(
                        onOpenAgentScreen = { onNavigateTo(AppState.AI_STUDIO_AGENT) },
                        onJumpKeys = { onNavigateSection(DashboardSection.CLOUD_API_HUB) }
                    )
                    1 -> BuildCenterSection(
                        context = context,
                        onNavigateTo = onNavigateTo,
                        onJumpAgentRoom = { onNavigateTo(AppState.AI_STUDIO_AGENT) }
                    )
                }
            }
        }
    }
}

/**
 * بطاقة التحويل الموحدة: لوحة المطور لم تعد تستضيف نسخة ثانية من غرفة الوكيل.
 * كل منطق الوكيل يعيش في شاشة واحدة هي AiStudioAgentScreen.
 */
@Composable
fun AgentRoomRedirectCard(
    onOpenAgentScreen: () -> Unit,
    onJumpKeys: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AgentDesign.Backdrop)
            .padding(AgentDesign.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(AgentDesign.SpaceMd)
    ) {
        AgentNeonCard(accent = AgentDesign.Primary) {
            AgentSectionHeader(index = "00", title = "مركز الوكيل الذكي", accent = AgentDesign.Primary)
            Spacer(Modifier.height(AgentDesign.SpaceMd))
            Text(
                "تم توحيد غرفة الوكيل في شاشة واحدة: المهمة، الطابور الحي، الأدوات والجاهزية، والسجل — بتبويبات هندسية واحدة.",
                color = AgentDesign.TextSecondary,
                fontFamily = CairoFont,
                fontSize = AgentDesign.FontXs
            )
            Spacer(Modifier.height(AgentDesign.SpaceLg))
            Row(horizontalArrangement = Arrangement.spacedBy(AgentDesign.SpaceSm)) {
                AgentNeonButton(
                    text = "فتح مركز الوكيل",
                    onClick = onOpenAgentScreen,
                    accent = AgentDesign.Primary,
                    icon = Icons.Default.Bolt
                )
                AgentNeonButton(
                    text = "مفاتيح API",
                    onClick = onJumpKeys,
                    accent = AgentDesign.Secondary,
                    icon = Icons.Default.VpnKey
                )
            }
        }

        AgentNeonCard(accent = AgentDesign.Secondary) {
            AgentSectionHeader(index = "01", title = "لماذا شاشة واحدة؟", accent = AgentDesign.Secondary)
            Spacer(Modifier.height(AgentDesign.SpaceSm))
            Text(
                "نسخة واحدة من الحالة (سياق التنفيذ، الفريق، النموذج، السجل) تعني سلوكاً متسقاً وسجلاً حقيقياً لكل تشغيل، بدل نسختين متشابهتين تتباعدان مع الوقت.",
                color = AgentDesign.TextSecondary,
                fontFamily = CairoFont,
                fontSize = AgentDesign.FontXs
            )
            Spacer(Modifier.height(AgentDesign.SpaceSm))
            Text(
                "مركز البناء و APK يبقى هنا في التبويب الثاني، ويفتح مركز الوكيل عند الحاجة للتشغيل.",
                color = AgentDesign.TextSecondary,
                fontFamily = CairoFont,
                fontSize = AgentDesign.FontXs
            )
        }
    }
}

@Composable
fun CloudApiHubScreen(
    initialTab: Int = 0,
    context: Context,
    onBack: () -> Unit,
    onNavigateSection: (DashboardSection) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("🔑 مفاتيح API", "⚡ المراقبة الحية (14)", "🌐 مرصد نقاط السحابة والصيانة (13)", "🚀 تبديل النماذج والسرعة", "🛰️ تيليمتري الدلتا (Firestore)")

    DevHubScaffold(
        title = "مركز المفاتيح والربط السحابي",
        subtitle = "إدارة المفاتيح، فحص الاستجابة، وتكامل الخدمات السحابية ومراقبة التحديثات",
        icon = Icons.Default.VpnKey,
        accentColor = GoldPrimary,
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        currentHub = DashboardSection.CLOUD_API_HUB,
        onNavigateSection = onNavigateSection
    ) {
        when (selectedTab) {
            0 -> ApiKeysScreen(onBack = onBack)
            1 -> DeveloperKeyStatusHub(onBack = onBack)
            2 -> ConnectorsSection(
                onJumpBuildCenter = { onNavigateSection(DashboardSection.AGENT_BUILD_HUB) },
                onJumpKeys = { selectedTab = 0 },
                onJumpHealth = { onNavigateSection(DashboardSection.HEALTH_DIAGNOSTICS_HUB) },
                onJumpDeltaMonitoring = { selectedTab = 4 }
            )
            3 -> AiModelLatencySwitcherScreen(onBack = onBack)
            4 -> DeltaMonitoringDashboard(
                onClose = { selectedTab = 2 }
            )
        }
    }
}

@Composable
fun HealthDiagnosticsHubScreen(
    initialTab: Int = 0,
    context: Context,
    devLogs: SnapshotStateList<SystemLogEntry>,
    onBack: () -> Unit,
    onNavigateSection: (DashboardSection) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("🩺 صحة الأنظمة", "🛡️ التشخيص الفضائي (HUD)", "🐛 ملتقط الانهيارات (Crash Guard)", "🐞 بلاغات الأخطاء", "🚀 خط الإنتاج (Pipeline)", "📜 سجل التدقيق الأمني")

    DevHubScaffold(
        title = "مركز الصحة والتشخيص الشامل",
        subtitle = "فحص الذاكرة والشبكة وسجلات الانهيارات والعمليات والملاحظات",
        icon = Icons.Default.MedicalServices,
        accentColor = Color(0xFF0EA5E9),
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        currentHub = DashboardSection.HEALTH_DIAGNOSTICS_HUB,
        onNavigateSection = onNavigateSection
    ) {
        when (selectedTab) {
            0 -> HealthCenterSection(devLogs = devLogs)
            1 -> SpatialServiceDiagnosticsHub(
                onNavigateToKeys = { onNavigateSection(DashboardSection.CLOUD_API_HUB) }
            )
            2 -> CrashLogsHubScreen(onBack = { selectedTab = 0 })
            3 -> BugReportsSection()
            4 -> ProductionPipelineSection()
            5 -> AuditLogSection(context = context)
        }
    }
}

@Composable
fun UsersCommunityHubScreen(
    initialTab: Int = 0,
    context: Context,
    devUsers: SnapshotStateList<DevUser>,
    onBack: () -> Unit,
    onNavigateSection: (DashboardSection) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("👥 المستخدمون والرتب", "📢 إرسال الإشعارات الجماعية", "💬 محادثات المستخدمين")

    DevHubScaffold(
        title = "إدارة المستخدمين والمجتمع",
        subtitle = "إدارة الحسابات، الصلاحيات، والتواصل المباشر مع المستخدمين",
        icon = Icons.Default.Group,
        accentColor = Color(0xFF10B981),
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        currentHub = DashboardSection.USERS_COMMUNITY_HUB,
        onNavigateSection = onNavigateSection
    ) {
        when (selectedTab) {
            0 -> EnhancedUsersSection(context = context, devUsers = devUsers)
            1 -> NotificationsSection(context = context)
            2 -> DeveloperChatSection(context = context)
        }
    }
}

@Composable
private fun DeveloperChatSection(context: Context) {
    val scope = rememberCoroutineScope()
    var threads by remember { mutableStateOf<Map<String, List<SupabaseServices.ChatMessageRow>>>(emptyMap()) }
    var selectedEmail by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 0=الكل 1=عاجل 2=أخطاء 3=بلاغات ريلز
    var filterMode by remember { mutableIntStateOf(0) }
    // الملف الشخصي للمرسل (يُفتح بالضغط على صورته كواتساب)
    var profileEmail by remember { mutableStateOf<String?>(null) }
    var profileUser by remember { mutableStateOf<SupabaseServices.UserRow?>(null) }
    var profileAvatar by remember { mutableStateOf<String?>(null) }
    var profileLoading by remember { mutableStateOf(false) }

    fun openSenderProfile(email: String) {
        profileEmail = email
        profileUser = null
        profileAvatar = null
        profileLoading = true
        scope.launch {
            val user = SupabaseServices.Database.getUserByEmail(email)
            profileUser = user
            val uid = user?.id?.takeIf { it.isNotBlank() }
                ?: threads[email]?.firstOrNull()?.userId?.takeIf { it?.isNotBlank() == true }
            if (!uid.isNullOrBlank()) {
                profileAvatar = runCatching { SupabaseServices.Profiles.getUserProfile(uid)?.avatarUrl }.getOrNull()
            }
            profileLoading = false
        }
    }

    fun msgTime(m: SupabaseServices.ChatMessageRow): String = m.createdAt.orEmpty()
    fun isUrgentMsg(m: SupabaseServices.ChatMessageRow): Boolean {
        val t = m.message
        return t.contains("[URGENT]") || t.contains("عاجل") || t.contains("🚨") || t.contains("بلاغ عاجل")
    }
    fun isBugMsg(m: SupabaseServices.ChatMessageRow): Boolean {
        val t = m.message
        return t.contains("[TELEMETRY]") || t.contains("خلل") || t.contains("خطأ") ||
            t.contains("مشكلة") || t.contains("تهنيج") || t.contains("تعذر") || t.contains("فشل")
    }
    fun isReelReport(m: SupabaseServices.ChatMessageRow): Boolean = m.message.contains("معرّف المقطع:")
    fun isSeedOnly(msgs: List<SupabaseServices.ChatMessageRow>): Boolean {
        if (msgs.isEmpty()) return true
        return msgs.all { it.id.startsWith("welcome_") }
    }
    fun shortTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        // ISO "2026-10-16T14:05:42" -> "14:05"
        return if (iso.length >= 16) iso.substring(11, 16) else iso.take(5)
    }
    fun unreadCount(msgs: List<SupabaseServices.ChatMessageRow>): Int {
        val lastDevIdx = msgs.indexOfLast { it.sender == "developer" }
        return if (lastDevIdx == -1) msgs.count { it.sender != "developer" }
        else msgs.drop(lastDevIdx + 1).count { it.sender != "developer" }
    }

    suspend fun refreshThreads() {
        if (isRefreshing) return
        isRefreshing = true
        try {
            val cloudThreads = SupabaseServices.Database.getAllChatThreads()
            val localThreads = DevChatRepository.getAllThreads(context)
            val merged = mutableMapOf<String, MutableList<SupabaseServices.ChatMessageRow>>()

            cloudThreads.forEach { (email, msgs) ->
                merged[email.trim().lowercase()] = msgs.toMutableList()
            }
            localThreads.forEach { (email, msgs) ->
                val key = email.trim().lowercase()
                val existing = merged[key] ?: mutableListOf()
                val existingIds = existing.map { it.id }.toSet()
                msgs.forEach { m ->
                    if (m.id !in existingIds) {
                        existing.add(
                            SupabaseServices.ChatMessageRow(
                                id = m.id,
                                userId = m.userId,
                                userEmail = key,
                                sender = m.sender,
                                message = m.message,
                                createdAt = java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss",
                                    java.util.Locale.US
                                ).format(java.util.Date(m.createdAt))
                            )
                        )
                    }
                }
                merged[key] = existing
            }
            // فرز الرسائل زمنياً داخل كل محادثة
            merged.forEach { (k, v) -> merged[k] = v.sortedBy { msgTime(it) }.toMutableList() }
            threads = merged
            if (selectedEmail == null || selectedEmail !in merged.keys) {
                selectedEmail = merged.keys.firstOrNull()
            }
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshThreads()
        while (isActive) {
            delay(8000)
            refreshThreads()
        }
    }

    // ترتيب المحادثات: الأحدث أولاً، والترحيبية فقط في الأسفل
    val orderedEmails = remember(threads) {
        threads.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<SupabaseServices.ChatMessageRow>>> { !isSeedOnly(it.value) }
                .thenByDescending { it.value.lastOrNull()?.let { m -> msgTime(m) }.orEmpty() }
        ).map { it.key }
    }
    val visibleEmails = orderedEmails.filter { email ->
        val msgs = threads[email].orEmpty()
        val q = searchQuery.trim().lowercase()
        val matchSearch = q.isBlank() || email.contains(q) || msgs.any { it.message.lowercase().contains(q) }
        if (!matchSearch) return@filter false
        when (filterMode) {
            1 -> msgs.any { isUrgentMsg(it) }
            2 -> msgs.any { isBugMsg(it) }
            3 -> msgs.any { isReelReport(it) }
            else -> true
        }
    }

    val selectedMessages = selectedEmail?.let { threads[it].orEmpty() }.orEmpty()
    val selectedUserId = selectedMessages.firstOrNull()?.userId
    val chatFilterLabel = when (filterMode) {
        1 -> "العاجل"
        2 -> "الأخطاء"
        3 -> "بلاغات الريلز"
        else -> "كل المحادثات"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "محادثات المستخدمين",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    "الأحدث أولاً • $chatFilterLabel (${visibleEmails.size})",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        AppSoundManager.playClick()
                        refreshThreads()
                        Toast.makeText(context, "تم تحديث المحادثات 🔄", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = GoldPrimary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // بحث + فلاتر
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("بحث بالبريد أو نص الرسالة...", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("الكل 💬", "عاجل 🚨", "أخطاء 🐞", "بلاغات 🎬").forEachIndexed { idx, label ->
                FilterChip(
                    selected = filterMode == idx,
                    onClick = { filterMode = idx },
                    label = { Text(label, fontFamily = CairoFont, fontSize = 10.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (threads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isRefreshing) "جاري تحميل المحادثات..." else "لا توجد رسائل مستخدمين حتى الآن.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 13.sp
                )
            }
            return@Column
        }

        if (visibleEmails.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا نتائج مطابقة للبحث أو الفلتر.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // قائمة المحادثات بنمط واتساب
            LazyColumn(
                modifier = Modifier.widthIn(min = 135.dp, max = 190.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleEmails) { email ->
                    val thread = threads[email].orEmpty()
                    val last = thread.lastOrNull()
                    val unread = unreadCount(thread)
                    val urgent = thread.any { isUrgentMsg(it) }
                    val bug = thread.any { isBugMsg(it) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEmail = email },
                        shape = RoundedCornerShape(12.dp),
                        color = if (email == selectedEmail) GoldPrimary.copy(alpha = 0.18f) else CardSurface,
                        border = BorderStroke(
                            1.dp,
                            when {
                                urgent -> Color(0xFFEF4444)
                                email == selectedEmail -> GoldPrimary
                                else -> Color.White.copy(alpha = 0.08f)
                            }
                        )
                    ) {
                        Row(modifier = Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (urgent) Color(0xFFEF4444).copy(alpha = 0.25f)
                                        else GoldPrimary.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .clickable { openSenderProfile(email) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    email.firstOrNull()?.uppercase() ?: "?",
                                    color = if (urgent) Color(0xFFEF4444) else GoldPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        email,
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (unread > 0) {
                                        Surface(color = Color(0xFF25D366), shape = CircleShape) {
                                            Text(
                                                "$unread",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (urgent) Text("🚨 ", fontSize = 9.sp)
                                    else if (bug) Text("🐞 ", fontSize = 9.sp)
                                    Text(
                                        last?.message.orEmpty().replace("\n", " "),
                                        color = TextSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    shortTime(last?.createdAt),
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        enabled = selectedEmail != null,
                        onClick = { selectedEmail?.let { openSenderProfile(it) } }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            selectedEmail?.firstOrNull()?.uppercase() ?: "?",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedEmail ?: "اختر مستخدمًا",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${selectedMessages.size} رسالة • اضغط لعرض الملف",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true
                ) {
                    items(selectedMessages.reversed()) { msg ->
                        val mine = msg.sender == "developer"
                        val urgent = isUrgentMsg(msg)
                        val bug = isBugMsg(msg)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    mine -> GoldPrimary
                                    urgent -> Color(0xFF7F1D1D)
                                    else -> Color.White.copy(alpha = 0.08f)
                                },
                                border = if (urgent && !mine) BorderStroke(1.dp, Color(0xFFEF4444)) else null,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    if (urgent || bug || isReelReport(msg)) {
                                        Text(
                                            if (urgent) "🚨 عاجل" else if (isReelReport(msg)) "🎬 بلاغ مقطع" else "🐞 خطأ مبلغ",
                                            color = if (mine) DeepSlate else Color(0xFFFCA5A5),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CairoFont
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        msg.message,
                                        color = if (mine) DeepSlate else Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        shortTime(msg.createdAt),
                                        color = (if (mine) DeepSlate else Color.White).copy(alpha = 0.6f),
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (!mine && msg.message.contains("معرّف المقطع:")) {
                                        val reelId = msg.message.substringAfter("معرّف المقطع:").substringBefore("\n").substringBefore(" ").trim()
                                        if (reelId.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val ok = SupabaseServices.Profiles.deleteReel(reelId)
                                                        if (ok) {
                                                            Toast.makeText(context, "تم حذف المقطع المبلغ عنه [$reelId] بنجاح 🗑️", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "فشل حذف المقطع، قد يكون محذوفاً بالفعل", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.DeleteForever, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("حذف هذا المقطع فوراً 🗑️", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= 1000) input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("اكتب رد المطور...", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    IconButton(
                        enabled = !isSending && input.isNotBlank() && selectedEmail != null,
                        onClick = {
                            val email = selectedEmail ?: return@IconButton
                            val targetId = selectedUserId
                            val text = input.trim()
                            if (text.isBlank()) return@IconButton
                            isSending = true
                            scope.launch {
                                DevChatRepository.sendMessage(
                                    context = context,
                                    userEmail = email,
                                    text = text,
                                    sender = "developer"
                                )
                                if (!targetId.isNullOrBlank()) {
                                    SupabaseServices.Database.sendChatMessage(
                                        userEmail = email,
                                        message = text,
                                        sender = "developer",
                                        targetUserId = targetId
                                    )
                                }
                                isSending = false
                                input = ""
                                AppSoundManager.playSuccess()
                                refreshThreads()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال الرد", tint = GoldPrimary)
                    }
                }
            }
        }
    }

    // ── نافذة الملف الشخصي للمرسل (كواتساب: ضغط على الصورة) ──
    if (profileEmail != null) {
        val email = profileEmail!!
        val msgCount = threads[email]?.size ?: 0
        AlertDialog(
            onDismissRequest = { profileEmail = null },
            confirmButton = {
                TextButton(onClick = {
                    profileEmail = null
                    selectedEmail = email
                }) { Text("فتح المحادثة 💬", fontFamily = CairoFont) }
            },
            dismissButton = {
                TextButton(onClick = { profileEmail = null }) { Text("إغلاق", fontFamily = CairoFont) }
            },
            title = { Text("الملف الشخصي للمرسل", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!profileAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = profileAvatar,
                            contentDescription = "صورة المرسل",
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(72.dp).background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(email.firstOrNull()?.uppercase() ?: "?", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (profileLoading) {
                        CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(22.dp))
                        Text("جاري جلب البيانات...", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    } else {
                        val u = profileUser
                        Text(
                            u?.name?.takeIf { it.isNotBlank() } ?: "بدون اسم مسجل",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(email, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (u == null) {
                            Text("لا يوجد ملف مسجل لهذا البريد في قاعدة البيانات.", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                        } else {
                            SenderProfileRow("رقم الحساب", u.accountNumber ?: "—")
                            SenderProfileRow("المعرف المخصص", u.customId ?: "—")
                            SenderProfileRow("نوع الاشتراك", u.type)
                            SenderProfileRow("الحالة", u.status)
                            SenderProfileRow("الإنذارات", "${u.strikes}")
                            SenderProfileRow("عضو منذ", u.createdAt?.take(10) ?: "—")
                            SenderProfileRow("رسائل المحادثة", "$msgCount")
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SenderProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(label, color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
    }
}

@Composable
fun BusinessRevenueHubScreen(
    initialTab: Int = 0,
    context: Context,
    requests: List<AppRequestService.AppRequest>,
    userCount: Int,
    onOpenChat: (String) -> Unit,
    onRefreshRequests: () -> Unit,
    onBack: () -> Unit,
    onNavigateSection: (DashboardSection) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("💰 مركز المال والاشتراكات", "📋 طلبات التطبيقات والعملاء", "📊 مركز الإحصائيات")

    DevHubScaffold(
        title = "مركز الأعمال والإيرادات",
        subtitle = "الاشتراكات، طلبات العملاء، الفواتير، وقمع التحويل",
        icon = Icons.Default.MonetizationOn,
        accentColor = Color(0xFFF59E0B),
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        currentHub = DashboardSection.BUSINESS_REVENUE_HUB,
        onNavigateSection = onNavigateSection
    ) {
        when (selectedTab) {
            0 -> MoneyCenterSection(context = context)
            1 -> RequestsSection(
                context = context,
                requests = requests,
                onOpenChat = onOpenChat,
                onRefresh = onRefreshRequests
            )
            2 -> StatsSection(requests = requests, userCount = userCount)
        }
    }
}

@Composable
fun SystemIdentityHubScreen(
    initialTab: Int = 0,
    context: Context,
    onBack: () -> Unit,
    onNavigateSection: (DashboardSection) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf(
        "⚙️ التحكم بالنظام",
        "✍️ توقيع واستوديو المطور",
        "💬 مجتمع ودعم السحابة",
        "👤 إعدادات الحساب",
        "🧠 عقل الأساليب",
        "💾 النسخ الاحتياطي"
    )

    DevHubScaffold(
        title = "نظام المطور والهوية الفنية",
        subtitle = "إعدادات المنصة، الهوية الفنية، أنماط الذكاء، والنسخ الاحتياطي",
        icon = Icons.Default.AdminPanelSettings,
        accentColor = Color(0xFFEC4899),
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        currentHub = DashboardSection.SYSTEM_IDENTITY_HUB,
        onNavigateSection = onNavigateSection
    ) {
        when (selectedTab) {
            0 -> SystemControlsSection(context = context)
            1 -> DevStudioSignatureSection(context = context)
            2 -> Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                SupportConfigDevSection(context = context)
            }
            3 -> Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                AccountSettingsSection(context = context)
            }
            4 -> Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                StyleBrainSection(context = context)
            }
            5 -> Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                DashboardBackupSection()
            }
        }
    }
}

@Composable
fun DashboardMainGrid(
    onBack: () -> Unit = {},
    onAgentBuildHub: () -> Unit = {},
    onCloudApiHub: () -> Unit = {},
    onHealthDiagnosticsHub: () -> Unit = {},
    onUsersCommunityHub: () -> Unit = {},
    onBusinessRevenueHub: () -> Unit = {},
    onSystemIdentityHub: () -> Unit = {},
    onBugReports: () -> Unit = onHealthDiagnosticsHub,
    onDevChat: () -> Unit = onUsersCommunityHub,
    // توافقية كاملة مع الروابط السابقة
    onLiveKeyHub: () -> Unit = onCloudApiHub,
    onAiModelSwitcher: () -> Unit = onCloudApiHub,
    onRequestSection: () -> Unit = onBusinessRevenueHub,
    onSystemControls: () -> Unit = onSystemIdentityHub,
    onNotifications: () -> Unit = onUsersCommunityHub,
    onUsers: () -> Unit = onUsersCommunityHub,
    onStats: () -> Unit = onBusinessRevenueHub,
    onHealthCenter: () -> Unit = onHealthDiagnosticsHub,
    onAgentRoom: () -> Unit = onAgentBuildHub,
    onConnectors: () -> Unit = onCloudApiHub,
    onAccountSettings: () -> Unit = onSystemIdentityHub,
    onMoneyCenter: () -> Unit = onBusinessRevenueHub,
    onDevStudioSignature: () -> Unit = onSystemIdentityHub,
    onStyleBrain: () -> Unit = onSystemIdentityHub,
    onApiKeys: () -> Unit = onCloudApiHub,
    onAuditLog: () -> Unit = onHealthDiagnosticsHub,
    onBackup: () -> Unit = onSystemIdentityHub,
    onProductionPipeline: () -> Unit = onHealthDiagnosticsHub,
    onBuildCenter: () -> Unit = onAgentBuildHub,
    userCount: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val gridContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGridOnline by remember { mutableStateOf(NetworkUtils.isNetworkAvailable(gridContext)) }
    var isSupabaseOnline by remember { mutableStateOf(SupabaseServices.isSupabaseAvailable) }
    var diagnosticSummary by remember { mutableStateOf("جاري الفحص...") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var cloudLatency by remember { mutableLongStateOf(-1L) }
    var isCheckingCloud by remember { mutableStateOf(false) }

    fun refreshCloudPing() {
        if (!isSupabaseOnline) return
        isCheckingCloud = true
        coroutineScope.launch {
            val t0 = System.currentTimeMillis()
            val ok = SupabaseServices.Database.ping()
            val t1 = System.currentTimeMillis()
            cloudLatency = if (ok) (t1 - t0) else -1L
            isCheckingCloud = false
        }
    }

    LaunchedEffect(Unit) {
        val reports = AppSelfDoctor.runFullDiagnosis(gridContext)
        val criticalCount = reports.count { it.severity == "حرج" }
        val improvementCount = reports.count { it.severity == "تحسين" }
        diagnosticSummary = if (criticalCount > 0) {
            "$criticalCount حرجة"
        } else if (improvementCount > 0) {
            "مستقر ($improvementCount)"
        } else {
            "100% مستقر"
        }
        refreshCloudPing()

        while (true) {
            isGridOnline = NetworkUtils.isNetworkAvailable(gridContext)
            isSupabaseOnline = SupabaseServices.isSupabaseAvailable
            kotlinx.coroutines.delay(4000)
        }
    }

    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Green = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFEC4899)
    val Gold = GoldPrimary

    data class CommandHubCardData(
        val id: Int,
        val title: String,
        val subtitle: String,
        val badge: String,
        val techTag: String,
        val icon: ImageVector,
        val accent: Color,
        val onClick: () -> Unit
    )

    val allHubs = listOf(
        CommandHubCardData(
            id = 1,
            title = "غرفة الوكيل ومركز البناء",
            subtitle = "وكيل ReAct البرمجي • إدارة Git • بناء وتجميع APK وتتبع المهام",
            badge = "تطوير وبرمجة ⚡",
            techTag = "// REACT AGENT + GIT PIPELINE",
            icon = Icons.Default.SmartToy,
            accent = Violet,
            onClick = onAgentBuildHub
        ),
        CommandHubCardData(
            id = 2,
            title = "مركز المفاتيح والربط السحابي",
            subtitle = "مفاتيح API • المراقبة الحية (14) • موصلات Supabase و GitHub",
            badge = "سحابة وشبكة 🌐",
            techTag = "// SUPABASE REALTIME + 14 KEYS",
            icon = Icons.Default.VpnKey,
            accent = Gold,
            onClick = onCloudApiHub
        ),
        CommandHubCardData(
            id = 3,
            title = "مركز الصحة والتشخيص الشامل",
            subtitle = "صحة الأنظمة والذاكرة • ملتقط الانهيارات • بلاغات الأعطال والملاحظات",
            badge = "فحص وتشخيص 🛡️",
            techTag = "// SELF-DOCTOR + CRASH RECORDER",
            icon = Icons.Default.MedicalServices,
            accent = Cyan,
            onClick = onHealthDiagnosticsHub
        ),
        CommandHubCardData(
            id = 4,
            title = "إدارة المستخدمين والمجتمع",
            subtitle = "المستخدمون والرتب • تجميد الحسابات • إرسال الإشعارات الجماعية",
            badge = "$userCount مستخدم 👥",
            techTag = "// SUPABASE AUTH + BROADCASTS",
            icon = Icons.Default.Group,
            accent = Green,
            onClick = onUsersCommunityHub
        ),
        CommandHubCardData(
            id = 5,
            title = "مركز الأعمال والإيرادات",
            subtitle = "الاشتراكات والإيرادات • طلبات التطبيقات • قمع التحويل والإحصائيات",
            badge = "مال ومشاريع 📈",
            techTag = "// REVENUE METRICS + DEV REQUESTS",
            icon = Icons.Default.MonetizationOn,
            accent = Amber,
            onClick = onBusinessRevenueHub
        ),
        CommandHubCardData(
            id = 6,
            title = "نظام المطور والهوية الفنية",
            subtitle = "وضع الصيانة • توقيع المطور الفني • عقل الأساليب • النسخ الاحتياطي",
            badge = "تحكم وهوية 🔒",
            techTag = "// MAINTENANCE + STYLE BRAIN",
            icon = Icons.Default.AdminPanelSettings,
            accent = Rose,
            onClick = onSystemIdentityHub
        )
    )

    val filteredHubs = remember(selectedFilterIndex) {
        if (selectedFilterIndex == 0) allHubs else allHubs.filter { it.id == selectedFilterIndex }
    }

    val filterCategories = listOf(
        "الكل // ALL",
        "🤖 الوكيل والبناء",
        "⚡ المفاتيح والسحابة",
        "🩺 الصحة والتشخيص",
        "👥 المستخدمون",
        "💰 الأعمال والطلبات",
        "⚙️ نظام المطور"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050811))
    ) {
        // ── 1. Cybernetic Spatial HUD Header (Edge-to-Edge with statusBarsPadding) ──
        Surface(
            color = Color(0xFF090E1A).copy(alpha = 0.95f),
            border = BorderStroke(0.8.dp, Color(0xFF1E293B).copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Button Pill
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Center Cyber Title & Monospace HUD Tag
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "لوحة تحكم المطور",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = pulseAlpha))
                            )
                        }
                        Text(
                            text = "// SPATIAL HUD TECH • v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFF22D3EE),
                            fontFamily = RobotoMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Live Telemetry Mini Capsule
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (cloudLatency > 0) "${cloudLatency}ms" else "ONLINE",
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal Category Segmented Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterCategories.forEachIndexed { index, title ->
                        val isSelected = selectedFilterIndex == index
                        val accentColor = when (index) {
                            1 -> Violet
                            2 -> Gold
                            3 -> Cyan
                            4 -> Green
                            5 -> Amber
                            6 -> Rose
                            else -> GoldPrimary
                        }
                        Surface(
                            color = if (isSelected) accentColor.copy(alpha = 0.18f) else Color(0xFF0F172A).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accentColor else Color(0xFF1E293B).copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedFilterIndex = index }
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) accentColor else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Scrollable Body Content (High Information Density HUD) ──
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 28.dp)
        ) {
            // ── Spatial HUD Telemetry Glass Box ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF090E1A).copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                GoldPrimary.copy(alpha = 0.45f),
                                Cyan.copy(alpha = 0.35f),
                                Violet.copy(alpha = 0.4f)
                            )
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF0B1120),
                                        Color(0xFF060913)
                                    )
                                )
                            )
                            .padding(12.dp)
                    ) {
                        // 4-Quadrant Matrix
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Supabase Cloud Matrix
                            CyberTelemetryPill(
                                modifier = Modifier.weight(1f),
                                title = "Supabase Cloud",
                                value = if (isCheckingCloud) "فحص..." else if (isSupabaseOnline) "مستقر ✓" else "غير متاح ✗",
                                subValue = if (cloudLatency > 0) "${cloudLatency}ms latency" else "Sync Active",
                                accent = if (isSupabaseOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                icon = Icons.Default.CloudQueue,
                                onClick = { refreshCloudPing() }
                            )

                            // 2. Self-Doctor Health Matrix
                            CyberTelemetryPill(
                                modifier = Modifier.weight(1f),
                                title = "صحة النظام",
                                value = diagnosticSummary,
                                subValue = "AppSelfDoctor",
                                accent = Cyan,
                                icon = Icons.Default.Shield,
                                onClick = {
                                    coroutineScope.launch {
                                        diagnosticSummary = "فحص..."
                                        val reports = AppSelfDoctor.runFullDiagnosis(gridContext)
                                        val crit = reports.count { it.severity == "حرج" }
                                        val imp = reports.count { it.severity == "تحسين" }
                                        diagnosticSummary = if (crit > 0) "$crit حرجة" else if (imp > 0) "مستقر ($imp)" else "100% مستقر"
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 3. User Community Metric
                            CyberTelemetryPill(
                                modifier = Modifier.weight(1f),
                                title = "صناع المحتوى",
                                value = "$userCount مستخدم",
                                subValue = "قاعدة Supabase",
                                accent = Green,
                                icon = Icons.Default.People,
                                onClick = onUsersCommunityHub
                            )

                            // 4. Memory & Free RAM
                            val freeRamMb = remember { (Runtime.getRuntime().freeMemory() / (1024 * 1024)).toInt() }
                            CyberTelemetryPill(
                                modifier = Modifier.weight(1f),
                                title = "الذاكرة المتاحة",
                                value = "${freeRamMb} MB",
                                subValue = "JVM Heap RAM",
                                accent = Violet,
                                icon = Icons.Default.Memory,
                                onClick = onHealthDiagnosticsHub
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 5. Firebase Firestore Matrix
                            val isFirebaseActive = remember { FirebaseManager.isConfigured(gridContext) }
                            CyberTelemetryPill(
                                modifier = Modifier.weight(1f),
                                title = "Firebase Firestore",
                                value = if (isFirebaseActive) "نشط سحابياً 🔥" else "غير متاح ✗",
                                subValue = if (isFirebaseActive) "Firestore NoSQL + Auth" else "Offline Only",
                                accent = if (isFirebaseActive) Color(0xFFFFA000) else Color(0xFF94A3B8),
                                icon = Icons.Default.CloudDone,
                                onClick = {
                                    Toast.makeText(gridContext, if (isFirebaseActive) "Firebase Firestore متصل ومربوط بقاعدة البيانات المخصصة بنجاح ✅" else "Firebase غير مهيأ", Toast.LENGTH_SHORT).show()
                                }
                            )

                            // 6. Dual Cloud Sync Status
                            CyberTelemetryPill(
                                modifier = Modifier.weight(1f),
                                title = "الازدواج السحابي",
                                value = if (isFirebaseActive && isSupabaseOnline) "تزامن هجين ⚡" else "سحابة مفردة",
                                subValue = "Firebase + Supabase",
                                accent = GoldPrimary,
                                icon = Icons.Default.Sync,
                                onClick = {
                                    Toast.makeText(gridContext, "يعمل التطبيق بنظام الازدواج السحابي المزدوج لحفظ المشاريع ومزامنة المستخدمين دون فقدان أي بيانات", Toast.LENGTH_LONG).show()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick HUD Action Strip
                        Surface(
                            color = Color(0xFF070B14).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF1E293B).copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Ping Keys
                                CyberMicroAction(
                                    modifier = Modifier.weight(1f),
                                    title = "14 مفتاح",
                                    icon = Icons.Default.Sensors,
                                    accent = GoldPrimary,
                                    onClick = onLiveKeyHub
                                )

                                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.08f)))

                                // 2. Model Latency
                                CyberMicroAction(
                                    modifier = Modifier.weight(1f),
                                    title = "سباق النماذج",
                                    icon = Icons.Default.Bolt,
                                    accent = Color(0xFF10B981),
                                    onClick = onAiModelSwitcher
                                )

                                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.08f)))

                                // 3. Auto-Heal & Purge Cache
                                CyberMicroAction(
                                    modifier = Modifier.weight(1f),
                                    title = "تفريغ الكاش",
                                    icon = Icons.Default.AutoFixHigh,
                                    accent = Cyan,
                                    onClick = {
                                        coroutineScope.launch {
                                            val freedMb = runCatching {
                                                var totalBytes = 0L
                                                gridContext.cacheDir?.listFiles()?.forEach { file ->
                                                    totalBytes += file.length()
                                                    file.deleteRecursively()
                                                }
                                                totalBytes / (1024 * 1024)
                                            }.getOrDefault(0L)
                                            Toast.makeText(gridContext, "تم تفريغ $freedMb MB وتحسين الأداء ⚡", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.08f)))

                                // 4. Copy Diagnostics Bundle
                                CyberMicroAction(
                                    modifier = Modifier.weight(1f),
                                    title = "نسخ التقرير",
                                    icon = Icons.Default.ContentCopy,
                                    accent = Violet,
                                    onClick = {
                                        coroutineScope.launch {
                                            val reports = AppSelfDoctor.runFullDiagnosis(gridContext)
                                            val bundle = buildString {
                                                append("🛸 تقرير تشخيص قبس الفضائي الشامل (HUD Telemetry)\n")
                                                append("• وقت التقرير: ${java.util.Date()}\n")
                                                append("• الذاكرة الحرة: ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB\n")
                                                append("• حالة الشبكة: $isGridOnline\n")
                                                append("• سحابة Supabase: $isSupabaseOnline\n")
                                                append("• التقارير النشطة:\n")
                                                reports.forEach { r -> append("  - [${r.severity}] ${r.title}: ${r.message}\n") }
                                            }
                                            val cm = gridContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Qabas Diagnostic Bundle", bundle))
                                            Toast.makeText(gridContext, "تم نسخ حزمة التشخيص إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Live Feedback & Direct Developer Radar (البلاغات والرسائل الحية) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val reportsList by BugReportRepository.reports.collectAsState()
                    val openReports = reportsList.count { it.status == "open" }
                    val totalReports = reportsList.size

                    // 1. زر البلاغات المباشر مع عداد حي
                    Surface(
                        onClick = onBugReports,
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E1420),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("بلاغات الأعطال", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                Text(
                                    if (openReports > 0) "$openReports جديد 🚨" else "الكل مستقر ($totalReports)",
                                    color = if (openReports > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 2. زر محادثات المستخدمين المباشر
                    Surface(
                        onClick = onDevChat,
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF102027),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Forum, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("محادثات المستخدمين", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                Text("فتح قناة DevChat 💬", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontSize = 10.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // Group Title
            item {
                DashGroupHeader(
                    title = "مراكز القيادة والإدارة الرئيسية 🏛️",
                    count = filteredHubs.size
                )
            }

            // ── Command Hub Cards ──
            items(filteredHubs) { hub ->
                CyberCommandHubCard(
                    title = hub.title,
                    subtitle = hub.subtitle,
                    badge = hub.badge,
                    techTag = hub.techTag,
                    icon = hub.icon,
                    accent = hub.accent,
                    onClick = hub.onClick
                )
            }
        }
    }
}

@Composable
private fun CyberTelemetryPill(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subValue: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontFamily = CairoFont,
                    maxLines = 1
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subValue,
                    color = accent.copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CyberMicroAction(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 9.sp,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CyberCommandHubCard(
    title: String,
    subtitle: String,
    badge: String,
    techTag: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090E1A).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color(0xFF0A0F1D).copy(alpha = 0.6f)
                        )
                    )
                )
        ) {
            // Spatial Accent Indicator Notch
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(40.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(accent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // HUD Leading Icon Box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Center Content Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            color = accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, accent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = badge,
                                color = accent,
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = techTag,
                        color = accent.copy(alpha = 0.7f),
                        fontFamily = RobotoMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Directional Action Indicator
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "دخول",
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashGroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GoldPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            color = GoldPrimary.copy(alpha = 0.15f),
            shape = CircleShape,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
        ) {
            Text(
                text = "$count",
                color = GoldPrimary,
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.12f),
            thickness = 1.dp
        )
    }
}

@Composable
fun OperationalHubCard(isInternetOnline: Boolean, isSupabaseConfigured: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    var cloudLatency by remember { mutableLongStateOf(-1L) }
    var cloudStatus by remember { mutableStateOf(if (isSupabaseConfigured) "جاري الفحص..." else "غير مهيأ") }
    var isChecking by remember { mutableStateOf(false) }

    fun checkCloud() {
        if (!isSupabaseConfigured) return
        isChecking = true
        coroutineScope.launch {
            val t0 = System.currentTimeMillis()
            val ok = SupabaseServices.Database.ping()
            val t1 = System.currentTimeMillis()
            cloudLatency = if (ok) (t1 - t0) else -1L
            cloudStatus = if (ok) "مستقر ✓" else "فشل الاتصال ✗"
            isChecking = false
        }
    }

    LaunchedEffect(isInternetOnline, isSupabaseConfigured) {
        if (isInternetOnline && isSupabaseConfigured) {
            checkCloud()
        }
    }

    Surface(
        color = Color(0xFF070B14).copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isInternetOnline) GoldPrimary.copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Internet Part
            DigitalSpeedCounter()

            // Separator
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.1f)))

            // Cloud Part
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (cloudLatency > 0) Color(0xFF10B981).copy(alpha = 0.05f) else Color.Transparent)
                    .clickable(enabled = !isChecking && isInternetOnline) { checkCloud() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Supabase Cloud",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = NotoSansFont
                    )
                    Text(
                        text = if (isChecking) "فحص..." else cloudStatus,
                        color = when {
                            !isSupabaseConfigured -> Color(0xFF94A3B8)
                            cloudLatency > 0 -> Color(0xFF10B981)
                            else -> Color(0xFFEF4444)
                        },
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (cloudLatency > 0) {
                    Text(
                        text = "${cloudLatency}ms",
                        color = Color(0xFF10B981).copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isSupabaseConfigured) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = null, 
                        tint = GoldPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InsightMetricsRow(
    diagnosticSummary: String,
    isGridOnline: Boolean,
    userCount: Int,
    onRefreshHealth: () -> Unit
) {
    val context = LocalContext.current
    var connectionType by remember { mutableStateOf("متصلة") }
    
    LaunchedEffect(isGridOnline) {
        connectionType = if (!isGridOnline) "منقطعة"
        else {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(activeNetwork)
            when {
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "4G/5G"
                else -> "متصلة"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // App Health
        MetricItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.MedicalServices,
            title = "صحة النظام",
            value = if (diagnosticSummary.contains("حرجة")) "تنبيه ⚠️" else "مستقرة ✓",
            subtitle = diagnosticSummary,
            accent = if (diagnosticSummary.contains("حرجة")) Color(0xFFEF4444) else GoldSecondary,
            clickable = true,
            onClick = onRefreshHealth
        )

        Box(Modifier.width(1.dp).height(36.dp).align(Alignment.CenterVertically).background(Color.White.copy(alpha = 0.08f)))

        // Network
        MetricItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CloudSync,
            title = "حالة الشبكة",
            value = if (isGridOnline) "متصل 🌐" else "منقطع ✗",
            subtitle = connectionType,
            accent = if (isGridOnline) Color(0xFF10B981) else Color(0xFFEF4444)
        )

        Box(Modifier.width(1.dp).height(36.dp).align(Alignment.CenterVertically).background(Color.White.copy(alpha = 0.08f)))

        // Users
        MetricItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Group,
            title = "المستخدمين",
            value = "$userCount",
            subtitle = "حساب مسجل",
            accent = Color(0xFF38BDF8)
        )
    }
}

@Composable
private fun MetricItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String = "",
    accent: Color,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontFamily = CairoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = Color.White,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = accent.copy(alpha = 0.85f),
                fontFamily = NotoSansFont,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickMetric(icon: ImageVector, title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2, lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            title, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RequestsSection(
    context: Context,
    requests: List<AppRequestService.AppRequest>,
    onOpenChat: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var statusTab by remember { mutableStateOf("all") }
    var paidFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val pendingCount = requests.count { it.status == "pending" }
    val progressCount = requests.count { it.status == "in_progress" }
    val doneCount = requests.count { it.status == "completed" }

    val visible = requests.filter { req ->
        (statusTab == "all" || req.status == statusTab) &&
            (paidFilter == "all" ||
                (paidFilter == "paid" && req.isPaid) ||
                (paidFilter == "free" && !req.isPaid)) &&
            (query.isBlank() ||
                req.title.contains(query, ignoreCase = true) ||
                req.userEmail.contains(query, ignoreCase = true) ||
                req.goal.contains(query, ignoreCase = true))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("تأكيد تصفير الطلبات؟", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("سيُحفظ نسخة احتياطية تلقائياً قبل المسح (${requests.size} طلب). هل أنت متأكد؟", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        // نسخة احتياطية تلقائية قبل المسح
                        val prefs = context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                        val raw = prefs.getString("requests", "[]") ?: "[]"
                        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).edit()
                            .putString("requests_backup_${System.currentTimeMillis()}", raw)
                            .apply()
                        prefs.edit().clear().apply()
                        showClearConfirm = false
                        onRefresh()
                        AuditLogger.log(context, "requests_cleared", "تصفير ${requests.size} طلب (مع نسخة احتياطية)")
                        Toast.makeText(context, "تم التصفير مع حفظ نسخة احتياطية ✅", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("نعم، صفّر", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("إلغاء", color = GoldPrimary, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface
        )
    }

    Column {
        // ── شريط الحالة: كانبان مصغر ──
        CenterTabRow(
            tabs = listOf(
                "الكل (${requests.size})",
                "🆕 جديد ($pendingCount)",
                "⚙️ قيد التنفيذ ($progressCount)",
                "✅ مكتمل ($doneCount)"
            ),
            selected = listOf("all", "pending", "in_progress", "completed").indexOf(statusTab),
            onSelect = {
                statusTab = listOf("all", "pending", "in_progress", "completed")[it]
            }
        )
        Spacer(modifier = Modifier.height(10.dp))

        // ── بحث + فلتر الدفع + تصفير آمن ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("بحث بالعنوان أو البريد…", fontSize = 12.sp, fontFamily = NotoSansFont) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            IconButton(onClick = { showClearConfirm = true }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "تصفير آمن", tint = Color(0xFFEF4444))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CenterTabRow(
            tabs = listOf("الكل", "💰 مدفوع", "🆓 مجاني"),
            selected = listOf("all", "paid", "free").indexOf(paidFilter),
            onSelect = { paidFilter = listOf("all", "paid", "free")[it] }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(CardSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (requests.isEmpty()) "لا توجد طلبات تطبيقات حالياً (السجل فارغ)" else "لا نتائج مطابقة للفلتر الحالي",
                        color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("عند إرسال المستخدمين لطلبات جيدة ستظهر هنا فوراً", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.id }) { req ->
                    RequestCard(
                        req = req,
                        context = context,
                        onOpenChat = onOpenChat,
                        onRefresh = onRefresh
                    )
                }
            }
        }
    }
}

/** بطاقة طلب غنية: حدّ ملوّن حسب الحالة + وقت نسبي + شريط تقدم + إجراءات سريعة. */
@Composable
private fun RequestCard(
    req: AppRequestService.AppRequest,
    context: Context,
    onOpenChat: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val statusColor = when (req.status) {
        "in_progress" -> Color(0xFF22D3EE)
        "completed" -> Color(0xFF10B981)
        else -> Color(0xFFE8C547)
    }
    val statusLabel = when (req.status) {
        "in_progress" -> "⚙️ قيد التنفيذ"
        "completed" -> "✅ مكتمل"
        else -> "🆕 جديد"
    }
    var expanded by remember(req.id) { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    val linkedRepo = remember(req.id) {
        context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
            .getString("repo_${req.id}", null)
    }

    fun applyChange(status: String? = null, isPaid: Boolean? = null, label: String) {
        AppRequestService.updateRequestProgressAndPayment(
            context, req.id, status = status, isPaid = isPaid,
            progress = if (status == "completed") 100 else if (status == "in_progress") req.progress.coerceAtLeast(10) else req.progress
        )
        // إكمال الطلب يفك الربط النشط تلقائياً
        if (status == "completed" && AppRequestService.getActiveBuildRequest(context)?.id == req.id) {
            AppRequestService.clearActiveBuildRequest(context)
        }
        AuditLogger.log(context, "request_status", "$label: ${req.title} (${req.userEmail})")
        onRefresh()
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    fun linkToBuild() {
        AppRequestService.updateRequestProgressAndPayment(
            context, req.id, status = "in_progress",
            progress = req.progress.coerceAtLeast(10)
        )
        AppRequestService.setActiveBuildRequest(context, req.id)
        AuditLogger.log(context, "request_build", "ربط بالبناء: ${req.title}")
        onRefresh()
        Toast.makeText(context, "🛠️ رُبط بمركز البناء — افتح «مركز البناء» من اللوحة", Toast.LENGTH_LONG).show()
    }

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("طلب آخر قيد البناء!", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = { Text("يوجد طلب نشط مربوط بمركز البناء. الربط الجديد سيستبدله — أكمل الطلب الحالي أولاً أو تابع.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { showReplaceDialog = false; linkToBuild() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) { Text("استبدل", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = false }) {
                    Text("إلغاء", color = GoldPrimary, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, statusColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    req.title, color = GoldPrimary, fontFamily = TajawalFont,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f)
                )
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        statusLabel, color = statusColor, fontSize = 10.sp,
                        fontFamily = CairoFont, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("👤 ${req.userEmail}", color = Color.LightGray, fontSize = 11.sp, fontFamily = NotoSansFont)
                Text("🕐 ${relativeTime(req.timestamp)}", color = Color.Gray, fontSize = 11.sp, fontFamily = NotoSansFont)
            }
            if (linkedRepo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = Color(0xFF8B5CF6).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "📦 $linkedRepo",
                        color = Color(0xFF8B5CF6), fontSize = 10.sp,
                        fontFamily = NotoSansFont, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }
            // حالة التفاوض على السعر
            val priceLabel = when (req.priceStatus) {
                "offered" -> "📋 عرض مرسل (${req.cost}$) — بانتظار العميل" to Color(0xFFF59E0B)
                "accepted" -> "✅ السعر مقبول (${req.cost}$)" to Color(0xFF10B981)
                "rejected" -> "❌ العميل رفض — اعرض جديداً" to Color(0xFFEF4444)
                else -> null
            }
            if (priceLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = priceLabel.second.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        priceLabel.first,
                        color = priceLabel.second, fontSize = 10.sp,
                        fontFamily = CairoFont, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                req.goal, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded }
            )
            if (req.progress > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.weight(1f).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth((req.progress.coerceIn(0, 100)) / 100f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(statusColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${req.progress}٪", color = statusColor, fontSize = 11.sp, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // إجراءات سريعة حسب الحالة
                if (req.status == "pending") {
                    Button(
                        onClick = { applyChange(status = "in_progress", label = "بدأ التنفيذ ⚙️") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE), contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("ابدأ ⚙️", fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                }
                if (req.status == "in_progress") {
                    Button(
                        onClick = { applyChange(status = "completed", label = "اكتمل ✅") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("أكمل ✅", fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                }
                if (!req.isPaid) {
                    OutlinedButton(
                        onClick = { applyChange(isPaid = true, label = "تمييز كمدفوع 💰") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                    ) { Text("مدفوع 💰", fontSize = 11.sp, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                } else {
                    Text("مدفوع (${req.cost}$)", color = GoldPrimary, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
                // الجسر: قبول ← ربط بمركز البناء (مع حارس الاستبدال)
                Button(
                    onClick = {
                        val active = AppRequestService.getActiveBuildRequest(context)
                        if (active != null && active.id != req.id) showReplaceDialog = true
                        else linkToBuild()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("🛠️ بناء", fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { onOpenChat(req.id) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldPrimary.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("محادثة", fontSize = 11.sp, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** وقت نسبي عربي: منذ 5 دقائق / ساعتين / 3 أيام… */
private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ $minutes د"
        hours < 24 -> "منذ $hours س"
        days == 1L -> "أمس"
        days < 30 -> "منذ $days يوم"
        else -> "منذ ${days / 30} شهر"
    }
}

@Composable
fun SystemControlsSection(context: Context) {
    val initial = AppRemoteConfig.current(context)
    var isMaintenanceMode by remember { mutableStateOf(initial.maintenanceMode) }
    var acceptNewRequests by remember { mutableStateOf(initial.acceptRequests) }
    var autoAiReply by remember { mutableStateOf(initial.autoAiReply) }
    var customMessage by remember { mutableStateOf(initial.maintenanceMessage) }
    var syncState by remember { mutableStateOf(
        if (initial.lastSyncMs > 0) "آخر مزامنة سحابية: ${AuditLogger.formatTime(initial.lastSyncMs)}" else "لم تتم المزامنة مع السحابة بعد"
    ) }
    val controllerScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun saveConfig(
        maintenance: Boolean = isMaintenanceMode,
        accept: Boolean = acceptNewRequests,
        aiReply: Boolean = autoAiReply,
        message: String = customMessage
    ) {
        val next = AppRemoteConfig.ConfigData(
            maintenanceMode = maintenance,
            acceptRequests = accept,
            autoAiReply = aiReply,
            maintenanceMessage = message,
            lastSyncMs = System.currentTimeMillis()
        )
        controllerScope.launch {
            val pushed = AppRemoteConfig.pushToCloud(context, next)
            syncState = if (pushed) {
                "تم الحفظ والمزامنة سحابياً بنجاح ☁️ (${AuditLogger.formatTime(System.currentTimeMillis())})"
            } else {
                "تم الحفظ محلياً فقط 💾"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("التحكم في خيارات النظام المباشرة", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("وضع الصيانة (Maintenance Mode)", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("إيقاف الوصول المؤقت للتطبيق للعملاء عند تحديث الخوادم.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
                Switch(
                    checked = isMaintenanceMode,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        isMaintenanceMode = it
                        saveConfig(maintenance = it)
                        Toast.makeText(context, if (it) "تم تفعيل وضع الصيانة ⚠️" else "تم إيقاف وضع الصيانة وإتاحة التطبيق ✅", Toast.LENGTH_SHORT).show()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
                )
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("استقبال طلبات جديدة", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("السماح للمستخدمين بإرسال مشاريع جديدة.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
                Switch(
                    checked = acceptNewRequests,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        acceptNewRequests = it
                        saveConfig(accept = it)
                        Toast.makeText(context, if (it) "تم تفعيل استقبال الطلبات الجديدة ✅" else "تم إيقاف استقبال الطلبات مؤقتاً 🛑", Toast.LENGTH_SHORT).show()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
                )
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الرد الآلي (AI Auto-Reply)", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("تفعيل الاستجابة التلقائية للذكاء الاصطناعي مع المستخدمين.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
                Switch(
                    checked = autoAiReply,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        autoAiReply = it
                        saveConfig(aiReply = it)
                        Toast.makeText(context, if (it) "تم تفعيل الرد الآلي للذكاء الاصطناعي 🤖" else "تم تعطيل الرد الآلي ⏸️", Toast.LENGTH_SHORT).show()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
                )
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رسالة توقف الخدمة المخصصة (تظهر للمستخدمين)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("سيتم إظهار هذا النص للمستخدمين عند تفعيل وضع الصيانة أو إيقاف الخدمة.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)

                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2
                )

                Text("نماذج جاهزة للرسائل:", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "تحديث الخوادم" to "الخدمة متوقفة مؤقتاً لتحديث الخوادم وتطوير الأداء. سنعود خلال دقائق!",
                        "تجاوز الحد اليومي" to "تم الوصول للحد اليومي لاستهلاك السيرفرات المجانية. يرجى المحاولة غداً أو ترقية الحساب.",
                        "صيانة مجدولة" to "تخضع المنصة لصيانة مجدولة لتحسين جودة الذكاء الاصطناعي. شكراً لترقبكم!"
                    )
                    presets.forEach { (title, msg) ->
                        Button(
                            onClick = {
                                AppSoundManager.playToggle()
                                customMessage = msg
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(title, color = GoldPrimary, fontSize = 12.sp, fontFamily = NotoSansFont)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        AppSoundManager.playSuccess()
                        saveConfig(message = customMessage)
                        Toast.makeText(context, "تم حفظ رسالة الصيانة سحابياً بنجاح ✦", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ نص الرسالة سحابياً", color = Color.Black, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    syncState,
                    color = GoldSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ── القواعد المشروطة وتجارب A/B ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("القواعد المشروطة وتجارب A/B", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("تجاوز قيمة مفتاح لشريحة فقط (إصدار/نوع مستخدم/نسبة طرح) — تُقيَّم الأعلى أولوية أولاً.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            ConfigOverridesEditor(context = context)
        }

        // ── إعدادات الدعم ومجتمع واتساب السحابية ──
        SupportConfigDevSection(context = context)
    }
}

@Composable
fun NotificationsSection(context: Context) {
    var notifTitle by remember { mutableStateOf("") }
    var notifMessage by remember { mutableStateOf("") }
    var sentNotifications by remember { mutableStateOf(AppNotificationService.getNotifications(context)) }
    var isDailyHadithEnabled by remember { mutableStateOf(AppNotificationService.isDailyHadithEnabled(context)) }
    val notificationScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Daily Hadith Auto Notification Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("الحديث النبوي اليومي التلقائي 📖", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "نشر حديث يومي صحيح من الكتب المعتمدة (البخاري ومسلم) بدون الحاجة لدخول التطبيق.",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = isDailyHadithEnabled,
                        onCheckedChange = { checked ->
                            isDailyHadithEnabled = checked
                            AppNotificationService.setDailyHadithEnabled(context, checked)
                            Toast.makeText(
                                context,
                                if (checked) "تم تفعيل إشعار الحديث النبوي اليومي الساعة 8:00 صباحاً 🟢" else "تم إيقاف الإشعارات اليومية التلقائية 🔴",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSlate,
                            checkedTrackColor = GoldPrimary,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF151B2B)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        notificationScope.launch {
                            val sentHadith = AppNotificationService.triggerDailyHadithNow(context)
                            sentNotifications = AppNotificationService.getNotifications(context)
                            Toast.makeText(context, "تم إرسال حديث اليوم كـ إشعار بنجاح! ✨", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال حديث نبوي صحيح الآن للمستخدمين 🚀", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("إرسال إشعار عام للمستخدمين", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notifTitle,
                    onValueChange = { notifTitle = it },
                    label = { Text("عنوان الإشعار", color = Color.Gray, fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notifMessage,
                    onValueChange = { notifMessage = it },
                    label = { Text("نص الإشعار", color = Color.Gray, fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (notifTitle.isNotBlank() && notifMessage.isNotBlank()) {
                            val title = notifTitle
                            val message = notifMessage
                            notificationScope.launch {
                                val id = RemoteNotificationsManager.sendBroadcast(context, title, message)
                                val toast = if (id != null) "تم إرسال الإشعار بنجاح ✅" else "فشل الإرسال: ${RemoteNotificationsManager.lastSendResult.value}"
                                Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
                                sentNotifications = AppNotificationService.getNotifications(context)
                            }
                            notifTitle = ""
                            notifMessage = ""
                        } else {
                            Toast.makeText(context, "الرجاء تعبئة العنوان والنص", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال الإشعار الآن", color = Color.Black, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // Display sent notifications log
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("الإشعارات المرسلة سابقاً (${sentNotifications.size})", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (sentNotifications.isNotEmpty()) {
                TextButton(onClick = {
                    AppNotificationService.clearNotifications(context)
                    sentNotifications = emptyList()
                    Toast.makeText(context, "تم مسح جميع الإشعارات", Toast.LENGTH_SHORT).show()
                }) {
                    Text("مسح الكل", color = Color.Red, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        }

        Text("البث يعمل عبر Supabase Realtime — يصل كل الأجهزة المتصلة فوراً.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)

        if (sentNotifications.isEmpty()) {
            Text("لا توجد إشعارات مرسلة حالياً", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(200.dp)) {
                items(sentNotifications) { notif ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(notif.title, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            Text(notif.message, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigOverridesEditor(context: Context) {
    var rules by remember { mutableStateOf<List<AppRemoteConfig.OverrideRule>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // حقول قاعدة جديدة
    var selKey by remember { mutableStateOf(AppRemoteConfig.KEY_MAINTENANCE) }
    var selValue by remember { mutableStateOf("true") }
    var selUserType by remember { mutableStateOf("all") }
    var selPercent by remember { mutableStateOf(100) }
    var selMinVersion by remember { mutableStateOf("0") }

    fun reload() {
        scope.launch {
            loading = true
            AppRemoteConfig.invalidateOverrides()
            rules = AppRemoteConfig.fetchOverrides(context)
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val keyLabels = mapOf(
        AppRemoteConfig.KEY_MAINTENANCE to "وضع الصيانة",
        AppRemoteConfig.KEY_ACCEPT_REQUESTS to "استقبال الطلبات",
        AppRemoteConfig.KEY_AUTO_AI_REPLY to "الرد الآلي",
        AppRemoteConfig.KEY_MAINTENANCE_MESSAGE to "رسالة الصيانة"
    )
    val typeLabels = mapOf("all" to "الكل", "free" to "المجانيون", "premium" to "المميزون", "developers" to "المطورون")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // اختيار المفتاح
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            keyLabels.forEach { (key, label) ->
                FilterChip(selected = selKey == key, onClick = { selKey = key }, label = { Text(label, fontFamily = CairoFont, fontSize = 11.sp) })
            }
        }
        // القيمة
        if (selKey == AppRemoteConfig.KEY_MAINTENANCE_MESSAGE) {
            OutlinedTextField(
                value = selValue, onValueChange = { selValue = it },
                label = { Text("نص الرسالة البديلة", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 2
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("true" to "تفعيل", "false" to "تعطيل").forEach { (v, label) ->
                    FilterChip(selected = selValue == v, onClick = { selValue = v }, label = { Text(label, fontFamily = CairoFont, fontSize = 11.sp) })
                }
            }
        }
        // الشريحة
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            typeLabels.forEach { (t, label) ->
                FilterChip(selected = selUserType == t, onClick = { selUserType = t }, label = { Text(label, fontFamily = CairoFont, fontSize = 11.sp) })
            }
        }
        // النسبة + أدنى إصدار
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الطرح: $selPercent٪", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.width(90.dp))
            Slider(value = selPercent.toFloat(), onValueChange = { selPercent = it.toInt() }, valueRange = 0f..100f, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = selMinVersion, onValueChange = { selMinVersion = it.filter { c -> c.isDigit() } },
            label = { Text("أدنى versionCode (0 = الكل)", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Button(
            onClick = {
                scope.launch {
                    val ok = AppRemoteConfig.saveOverride(
                        context,
                        AppRemoteConfig.OverrideRule(
                            key = selKey, value = selValue, userType = selUserType,
                            minVersion = selMinVersion.toIntOrNull() ?: 0,
                            percent = selPercent, priority = (rules.maxOfOrNull { it.priority } ?: 0) + 1
                        )
                    )
                    Toast.makeText(context, if (ok) "حُفظت القاعدة ✅" else "فشل الحفظ", Toast.LENGTH_SHORT).show()
                    if (ok) reload()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("إضافة القاعدة", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
        }

        if (loading) {
            CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(24.dp))
        } else if (rules.isEmpty()) {
            Text("لا قواعد بعد — القيم العامة هي الفعالة.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
        } else {
            rules.forEach { rule ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${keyLabels[rule.key] ?: rule.key} = ${rule.value}",
                                color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                            Text(
                                "${typeLabels[rule.userType] ?: rule.userType} • ${rule.percent}٪ • v${rule.minVersion}+",
                                color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val ok = AppRemoteConfig.deleteOverride(rule.id)
                                Toast.makeText(context, if (ok) "حُذفت القاعدة" else "فشل الحذف", Toast.LENGTH_SHORT).show()
                                if (ok) reload()
                            }
                        }) {
                            Text("حذف", color = Color.Red, fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsSection(requests: List<AppRequestService.AppRequest>, userCount: Int) {
    val context = LocalContext.current
    val totalRevenue = requests.filter { it.isPaid }.sumOf { it.cost }
    val activeProjects = requests.count { it.status == "in_progress" || it.status == "pending" }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("🔑 الاستهلاك", "📈 التفاعل والمبيعات", "☁️ السحابة")

    Column(modifier = Modifier.fillMaxSize()) {
        CenterTabRow(
            tabs = tabs,
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        Text("اللوحة المرئية لاستهلاك المفاتيح الحقيقي", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiConsumptionChart(context = context)
                    }
                    item {
                        Text("مؤشرات الأداء وصحة النظام وقواعد البيانات (Health & Room DB)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        SystemPerformanceHealthKpiGrid(context = context)
                    }
                    item {
                        Text("إحصائيات النظام والشبكة الحقيقية", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "حالة الخدمة",
                                value = if (NetworkUtils.isNetworkAvailable(context)) "100% متصل 🟢" else "غير متصل 🔴",
                                icon = Icons.Default.CloudDone,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "الحسابات والمستخدمين",
                                value = "$userCount حقيقي",
                                icon = Icons.Default.Group,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                1 -> {
                    item {
                        Text("رسم بياني لتفاعل وإنتاجية صناع المحتوى (Engagement Trends)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        WeeklyEngagementTrendsChart(context = context)
                    }
                    item {
                        Text("المشاريع والمبيعات الحقيقية", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title = "الأرباح الإجمالية",
                                value = "${totalRevenue}$",
                                icon = Icons.Default.AttachMoney,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "المشاريع النشطة",
                                value = "$activeProjects",
                                icon = Icons.Default.Build,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Text("قمع التحويل والاحتفاظ (حقيقي من الطلبات)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        RequestFunnelCard(requests = requests)
                    }
                }
                else -> {
                    item {
                        Text("مراقبة تحليلات فايربيس المباشرة (Firebase Analytics Live)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LiveFirebaseAnalyticsMonitorCard(context = context)
                    }
                    item {
                        Text("سجلات حارس المحتوى السحابية المباشرة (Cloud Guard Activity)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LiveCloudGuardLogsCard(context = context)
                    }
                    item {
                        Text("المساحة السحابية والسيرفر (Firebase & Cloud Storage)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        FirebaseStorageCapacityCard(context = context)
                    }
                }
            }
        }
    }
}

@Composable
fun RequestFunnelCard(requests: List<AppRequestService.AppRequest>) {
    val total = requests.size
    val started = requests.count { it.status == "in_progress" || it.status == "completed" }
    val completed = requests.count { it.status == "completed" }
    val paid = requests.count { it.isPaid }
    val stages = listOf(
        "إجمالي الطلبات" to total,
        "بدأ التنفيذ" to started,
        "اكتمل" to completed,
        "مدفوع" to paid
    )
    // الاحتفاظ: طالبون بأكثر من طلب + نشاط 7/30 يوم
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val byUser = requests.groupBy { it.userEmail }
    val repeatPct = if (byUser.isNotEmpty()) byUser.count { it.value.size > 1 } * 100.0 / byUser.size else 0.0
    val active7 = byUser.count { (_, list) -> list.any { now - it.timestamp < 7 * dayMs } }
    val active30 = byUser.count { (_, list) -> list.any { now - it.timestamp < 30 * dayMs } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stages.forEachIndexed { i, (label, count) ->
                val pct = if (total > 0) count * 100.0 / total else 0.0
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("$count • ${"%.0f".format(pct)}٪", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth((pct / 100).toFloat().coerceIn(0f, 1f))
                                .background(
                                    when (i) {
                                        0 -> Color(0xFF38BDF8)
                                        1 -> Color(0xFF8B5CF6)
                                        2 -> Color(0xFF10B981)
                                        else -> GoldPrimary
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF1E293B))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FunnelMiniStat("${"%.0f".format(repeatPct)}٪", "طلب متكرر")
                FunnelMiniStat("$active7", "نشط 7 أيام")
                FunnelMiniStat("$active30", "نشط 30 يوم")
            }
        }
    }
}

@Composable
private fun FunnelMiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
    }
}

@Composable
fun LiveFirebaseAnalyticsMonitorCard(context: Context) {
    val analyticsService = remember { AppServices.getAnalyticsService(context) }
    var testEventSentCount by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFF5D76E).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("مراقبة Firebase Analytics اللحظية", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("تتبع تفاعل المستخدمين وسلوك صناع المحتوى", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }

                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Text(
                        "متصل ونشط 🟢",
                        color = Color(0xFF10B981),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Screen Views Breakdown
            Text("أكثر الشاشات والاستوديوهات زيارة وتفاعلاً:", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // لا توجد بيانات زيارة حقيقية لكل شاشة حتى الآن — أرقام صادقة بدلاً من قيم مختلقة
            val screensStats = listOf(
                Pair("استوديو الإنتاج (Studio Pro)", Color(0xFFEAB308)),
                Pair("المُلقن الذكي (Teleprompter)", Color(0xFF3B82F6)),
                Pair("استوديو بطاقات الأحاديث", Color(0xFF10B981)),
                Pair("محرك السلاسل الدعوية الآلي", Color(0xFFA855F7)),
                Pair("المساعد الدعوي الذكي", Color(0xFFEC4899))
            )

            Text("لا تتوفر بيانات زيارة لكل شاشة بعد — سيتم ربطها بالتتبع الفعلي لاحقاً.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 11.sp)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                screensStats.forEach { (screenName, barColor) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(screenName, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier
                                .weight(2f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = barColor,
                            trackColor = Color(0xFF0B0F19)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("--", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.width(55.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Insights, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (testEventSentCount == 0) "الأحداث تُسجل فوراً في السحابة" else "تم إرسال $testEventSentCount حدث تجريبي ✦",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = {
                        testEventSentCount++
                        analyticsService.logEvent(
                            "dev_analytics_heartbeat",
                            mapOf(
                                "timestamp" to System.currentTimeMillis(),
                                "trigger" to "developer_dashboard",
                                "test_count" to testEventSentCount
                            )
                        )
                        Toast.makeText(context, "تم إرسال حدث التحليلات #$testEventSentCount إلى Firebase بنجاح 🚀", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إرسال حدث تجريبي", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LiveCloudGuardLogsCard(context: Context) {
    val logsFlow = remember { CloudServices.Database.observeGuardLogs() }
    val cloudLogs by logsFlow.collectAsState(initial = emptyList())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("سجلات التدقيق الشرعي السحابي", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (cloudLogs.isEmpty()) "في انتظار عمليات الفحص السحابية..." else "${cloudLogs.size} عملية فحص مسجلة",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = if (CloudServices.isFirebaseInitialized) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (CloudServices.isFirebaseInitialized) Color(0xFF10B981) else Color(0xFFEF4444))
                ) {
                    Text(
                        if (CloudServices.isFirebaseInitialized) "Firestore 🟢" else "محلي فقط 🟡",
                        color = if (CloudServices.isFirebaseInitialized) Color(0xFF10B981) else Color(0xFFF59E0B),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (cloudLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا توجد سجلات تدقيق متاحة حتى الآن.",
                        color = Color.Gray,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cloudLogs.take(5).forEach { logMap ->
                        val verdict = logMap["verdict"] as? String ?: "APPROPRIATE"
                        val score = (logMap["score"] as? Number)?.toInt() ?: 90
                        val reason = logMap["reason"] as? String ?: "فحص محتوى"
                        val textSnippet = logMap["textSnippet"] as? String ?: ""

                        val badgeColor = when (verdict) {
                            "REJECTED" -> Color(0xFFEF4444)
                            "NEEDS_REVIEW" -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }

                        Surface(
                            color = Color(0xFF0B0F19),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = badgeColor.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                when (verdict) {
                                                    "REJECTED" -> "مرفوض ❌"
                                                    "NEEDS_REVIEW" -> "مراجعة ⚠️"
                                                    else -> "مناسب ✅"
                                                },
                                                color = badgeColor,
                                                fontFamily = CairoFont,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$score%",
                                            color = badgeColor,
                                            fontFamily = RobotoMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = reason,
                                        color = Color.White,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    if (textSnippet.isNotBlank()) {
                                        Text(
                                            text = textSnippet,
                                            color = TextSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyEngagementTrendsChart(context: Context) {
    var selectedMetric by remember { mutableIntStateOf(0) } // 0: إنتاج الفيديو, 1: نصوص الذكاء الاصطناعي, 2: بطاقات الأحاديث

    val daysOfWeek = listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")

    // عدّ حقيقي من قاعدة البيانات أثناء الافتتاح — لا أرقام مزيفة
    var videoData by remember { mutableStateOf(IntArray(7)) }
    var scriptData by remember { mutableStateOf(IntArray(7)) }
    var cardsData by remember { mutableStateOf(IntArray(7)) }

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStarts = LongArray(7)
        val dayEnds = LongArray(7)
        for (i in 6 downTo 0) {
            val startCal = today.clone() as java.util.Calendar
            startCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            dayStarts[6 - i] = startCal.timeInMillis
            dayEnds[6 - i] = startCal.timeInMillis + 86_400_000L
        }
        val v = IntArray(7)
        val s = IntArray(7)
        val c = IntArray(7)
        for (i in 0..6) {
            v[i] = db.projectDao().countProjectsBetween(dayStarts[i], dayEnds[i])
            s[i] = db.reelScriptDao().countScriptsBetween(dayStarts[i], dayEnds[i])
            c[i] = db.hadithCardDao().countCardsBetween(dayStarts[i], dayEnds[i])
        }
        videoData = v
        scriptData = s
        cardsData = c
    }

    val currentData = when (selectedMetric) {
        0 -> videoData
        1 -> scriptData
        else -> cardsData
    }
    val maxVal = (currentData.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("معدل الإنتاج الأسبوعي", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("إجمالي الأسبوع: ${currentData.sum()}", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metric Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("فيديوهات 🎬", "نصوص AI ✍️", "أحاديث 🎴").forEachIndexed { idx, label ->
                    val isSelected = selectedMetric == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) GoldPrimary else Color.Transparent)
                            .clickable { selectedMetric = idx }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (isSelected) DeepSlate else Color.Gray,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width / (currentData.size * 2)
                    val maxBarHeight = size.height - 20.dp.toPx()

                    // Draw grid lines
                    val steps = 3
                    for (i in 0..steps) {
                        val y = (maxBarHeight / steps) * i
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }

                    // Draw bars
                    currentData.forEachIndexed { index, value ->
                        val x = (index * 2 * barWidth) + (barWidth / 2)
                        val heightRatio = value / maxVal
                        val barHeight = maxBarHeight * heightRatio
                        val yOffset = maxBarHeight - barHeight

                        // Background pillar
                        drawRoundRect(
                            color = Color(0xFF0B0F19),
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(barWidth, maxBarHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                        )

                        // Active gradient bar
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(GoldPrimary, GoldPrimary.copy(alpha = 0.4f))
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(x, yOffset),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysOfWeek.forEachIndexed { idx, day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(day, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        Text("${currentData[idx]}", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemPerformanceHealthKpiGrid(context: Context) {
    val db = remember { AppDatabase.getDatabase(context) }
    var stats by remember { mutableStateOf<Map<String, ApiUsageTracker.ApiStat>>(emptyMap()) }
    LaunchedEffect(Unit) {
        stats = ApiUsageTracker.snapshot(context)
    }

    val totalCalls = stats.values.sumOf { it.totalCalls }
    val totalSuccess = stats.values.sumOf { it.successCalls }
    val overallSuccessRate = if (totalCalls > 0) (totalSuccess * 100) / totalCalls else 0
    val hasMeasurements = totalCalls > 0

    val usedMemoryMb = remember {
        val rt = Runtime.getRuntime()
        (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
    }
    val roomVersion = remember {
        runCatching { db.openHelper.writableDatabase.version }.getOrDefault(4)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مؤشرات زمن الاستجابة والاستقرار الحقيقية", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Surface(
                    color = if (hasMeasurements) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF475569).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (hasMeasurements) "مستقر $overallSuccessRate% ⚡" else "لا قياسات بعد",
                        color = if (hasMeasurements) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = NotoSansFont,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiMetricBox(
                    title = "Gemini",
                    value = DevDashboardFormatters.formatLatency(stats["Gemini"]?.avgLatencyMs ?: 0L),
                    subtitle = "متوسط زمن التوليد",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricBox(
                    title = "Groq",
                    value = DevDashboardFormatters.formatLatency(stats["Groq"]?.avgLatencyMs ?: 0L),
                    subtitle = "متوسط زمن الاستدعاء",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricBox(
                    title = "ElevenLabs",
                    value = DevDashboardFormatters.formatLatency(stats["ElevenLabs"]?.avgLatencyMs ?: 0L),
                    subtitle = "متوسط زمن الصوت",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiMetricBox(
                    title = "نسبة نجاح الاستدعاءات",
                    value = if (hasMeasurements) "$overallSuccessRate%" else "--",
                    subtitle = "$totalCalls استدعاء مسجّل",
                    isPositive = true,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricBox(
                    title = "قاعدة بيانات Room",
                    value = "v$roomVersion",
                    subtitle = "الإصدار الفعلي على الجهاز",
                    isPositive = true,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricBox(
                    title = "استهلاك الذاكرة",
                    value = "$usedMemoryMb MB",
                    subtitle = "الذاكرة المخصصة الفعلية",
                    isPositive = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun KpiMetricBox(
    title: String,
    value: String,
    subtitle: String,
    isPositive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0B0F19),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF151B2B))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value,
                color = if (isPositive) Color(0xFF10B981) else GoldPrimary,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
        }
    }
}

@Composable
fun FirebaseStorageCapacityCard(context: Context) {
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    val isFirebaseActive = CloudServices.isFirebaseInitialized

    // Calculate real disk & cache usage
    val cacheBytes = remember {
        try {
            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }
    val cacheMB = (cacheBytes / (1024.0 * 1024.0)).coerceAtLeast(0.5)

    // Real local data & database usage (no fabricated numbers)
    val dataBytes = remember {
        try {
            (context.filesDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L) +
                (context.getDatabasePath("qabas.db").length())
        } catch (e: Exception) {
            0L
        }
    }
    val dataMB = dataBytes / (1024.0 * 1024.0)

    // Firebase Free Tier Specs
    val maxFreeStorageMB = 1024.0 // 1 GB free
    val totalUsedMB = (cacheMB + dataMB).coerceAtMost(maxFreeStorageMB)
    val remainingMB = maxFreeStorageMB - totalUsedMB
    val remainingPercentage = ((remainingMB / maxFreeStorageMB) * 100).toInt()
    val usedPercentageRatio = (totalUsedMB / maxFreeStorageMB).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isFirebaseActive) GoldPrimary else Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("سعة تخزين السيرفر و Firebase", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (isFirebaseActive) "سحابة Firebase Firestore نشطة 🔥" else "وضع التخزين المحلي المؤقت 💾",
                            color = if (isFirebaseActive) Color(0xFF10B981) else Color.Gray,
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    color = if (remainingPercentage > 20) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (remainingPercentage > 20) Color(0xFF10B981) else Color(0xFFEF4444))
                ) {
                    Text(
                        "متبقي $remainingPercentage%",
                        color = if (remainingPercentage > 20) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capacity Progress Bar
            LinearProgressIndicator(
                progress = { usedPercentageRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = when {
                    usedPercentageRatio < 0.7f -> Color(0xFF10B981)
                    usedPercentageRatio < 0.9f -> GoldPrimary
                    else -> Color(0xFFEF4444)
                },
                trackColor = Color(0xFF0B0F19)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Real stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("المساحة المستهلكة", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    Text(String.format("%.1f MB", totalUsedMB), color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المساحة المتبقية مجاناً", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    Text(String.format("%.1f MB", remainingMB), color = Color(0xFF10B981), fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("حد الباقة المجانية", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    Text("1,024 MB (1 GB)", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حجم ملفات الذاكرة المؤقتة (Cache):", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
                Text(String.format("%.1f MB", cacheMB), color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "ملاحظة صادقة: استهلاك Firebase السحابي لا يُقاس محلياً؛ الأرقام المعروضة قياس فعلي لملفات التطبيق وقاعدة البيانات والكاش على هذا الجهاز.",
                color = Color(0xFF64748B),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ApiConsumptionChart(context: Context) {
    var stats by remember { mutableStateOf<Map<String, ApiUsageTracker.ApiStat>>(emptyMap()) }
    LaunchedEffect(Unit) {
        stats = ApiUsageTracker.snapshot(context)
    }

    val apis = ApiUsageTracker.SUPPORTED
    val totalCalls = stats.values.sumOf { it.totalCalls }
    val maxCalls = stats.values.maxOfOrNull { it.totalCalls } ?: 0

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استهلاك مفاتيح API الحقيقي", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Surface(
                    color = if (totalCalls > 0) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF475569).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (totalCalls > 0) Color(0xFF10B981) else Color(0xFF64748B))
                ) {
                    Text(
                        if (totalCalls > 0) "$totalCalls استدعاء مسجّل" else "لا قياسات بعد",
                        color = if (totalCalls > 0) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontFamily = CairoFont,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("أعداد الاستدعاءات الفعلية المسجّلة من نقاط HTTP الحقيقية في التطبيق.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // Bar Chart
            Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width / (apis.size * 2)
                    val maxBarHeight = size.height - 30.dp.toPx()

                    val steps = 4
                    for (i in 0..steps) {
                        val y = (maxBarHeight / steps) * i
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    apis.forEachIndexed { index, name ->
                        val calls = stats[name]?.totalCalls ?: 0L
                        val fraction = if (maxCalls > 0) calls.toFloat() / maxCalls else 0f
                        val x = (index * 2 * barWidth) + barWidth / 2
                        val targetHeight = maxBarHeight * fraction
                        val yOffset = maxBarHeight - targetHeight

                        drawRoundRect(
                            color = Color(0xFF0B0F19),
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(barWidth, maxBarHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                        )
                        val barColor = if (calls > 0) Color(0xFF10B981) else Color(0xFF334155)
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(barColor.copy(alpha = 0.8f), barColor.copy(alpha = 0.3f))),
                            topLeft = androidx.compose.ui.geometry.Offset(x, yOffset),
                            size = androidx.compose.ui.geometry.Size(barWidth, targetHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                apis.forEach { name ->
                    val calls = stats[name]?.totalCalls ?: 0L
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(name.replace("HuggingFace", "HF AI"), color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (calls > 0) "$calls استدعاء" else "0",
                            color = if (calls > 0) Color(0xFF10B981) else Color(0xFF64748B),
                            fontFamily = CairoFont,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun LogsSection(devLogs: androidx.compose.runtime.snapshots.SnapshotStateList<SystemLogEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("سجلات النظام الحية (${devLogs.size})", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = { devLogs.clear() }) {
                Text("مسح السجلات", color = Color.Red, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CardSurface, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            if (devLogs.isEmpty()) {
                Text("لا توجد سجلات حالياً (النظام يعمل بصفاء تام)", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 14.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(devLogs) { log ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("[${log.level}]", color = log.color, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(log.message, color = Color.LightGray, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(log.time, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

data class CrashLogFile(
    val fileName: String,
    val timestampMs: Long,
    val thread: String,
    val exceptionLine: String,
    val fullStack: String
)

fun loadCrashLogs(context: Context): List<CrashLogFile> {
    return try {
        val dir = File(context.filesDir, "crash_logs")
        if (!dir.exists()) return emptyList()
        dir.listFiles()
            ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val content = runCatching { file.readText() }.getOrElse { "" }
                val thread = content.lineSequence()
                    .firstOrNull { it.startsWith("Thread:") }
                    ?.removePrefix("Thread:")
                    ?.trim() ?: "غير معروف"
                // استخراج أول سطر "Caused by:" أو أول سطر Exception/Error بعد رأس الملف.
                // السابق كان يلتقط سطراً داخل stack بدلاً من رأس الاستثناء.
                val exceptionLine = run {
                    val lines = content.lineSequence().toList()
                    val causedBy = lines.firstOrNull { it.trim().startsWith("Caused by:") }?.trim()
                    if (causedBy != null) {
                        causedBy
                    } else {
                        val headerEnd = lines.indexOfFirst { it.startsWith("Thread:") }.let { if (it == -1) 0 else it + 1 }
                        lines.drop(headerEnd)
                            .firstOrNull { l ->
                                val t = l.trim()
                                t.startsWith("java.") || t.startsWith("kotlin.") ||
                                t.startsWith("android.") || t.startsWith("androidx.") ||
                                (t.contains("Exception:") || t.contains("Error:"))
                            }
                            ?.trim().orEmpty()
                    }
                }
                CrashLogFile(
                    fileName = file.name,
                    timestampMs = file.lastModified(),
                    thread = thread,
                    exceptionLine = exceptionLine,
                    fullStack = content
                )
            }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * يلتقط أول إطار فعلي من تطبيق `com.qabas.app` داخل الـ stack (تجاهل أطر Android/Kotlin/Java).
 * يعود بنص مثل "QuranDataProvider.kt:loadTafsir" أو "غير معروف" لو لم يُعثر.
 */
fun firstAppFrame(fullStack: String): String {
    return fullStack.lineSequence()
        .firstOrNull { line ->
            val t = line.trim()
            t.startsWith("at com.qabas.app.") && "(:\\d+)" in t
        }
        ?.trim()
        ?.removePrefix("at ")
        ?.take(160)
        ?: "غير معروف"
}

/**
 * نوع مبسّط من CrashLogFile يُجمّع حسب عائلة الخطأ (NullPointerException, OOM, ...).
 * مفيد لتجميع "أكثر 5 أخطاء تكراراً" في الواجهة.
 */
data class CrashGroup(val type: String, val count: Int, val latestMs: Long, val sample: CrashLogFile)

fun groupCrashesByType(crashes: List<CrashLogFile>): List<CrashGroup> {
    fun family(raw: String): String {
        val s = raw.trim()
        // أول كلمة/معرّف Java بين كلمات مفصولة بـ : أو قبلها
        val regex = Regex("(java|kotlin|androidx|android|com)\\.[\\w.$]+(?:Exception|Error)")
        val m = regex.find(s)
        if (m != null) {
            val simple = m.value.substringAfterLast('.')
            // Inner-class exceptions: java.net.Foo$BarException -> BarException
            return if (simple.contains('$')) simple.substringAfterLast('$') else simple
        }
        // fallback: اقطع قبل أول ":" أو "("
        val cutAt = listOf(':', '(').firstOrNull { s.contains(it) } ?: return s
        return s.substringBefore(cutAt).trim().take(40)
    }
    return crashes
        .groupBy { family(it.exceptionLine.ifBlank { "غير مصنّف" }) }
        .map { (type, list) ->
            CrashGroup(
                type = type,
                count = list.size,
                latestMs = list.maxOf { it.timestampMs },
                sample = list.maxByOrNull { it.timestampMs } ?: list.first()
            )
        }
        .sortedWith(compareByDescending<CrashGroup> { it.count }.thenByDescending { it.latestMs })
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "📋 تم نسخ السجل إلى الحافظة", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "مشاركة سجل الانهيار").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(context, "تعذّر فتح المشاركة — استخدم زر النسخ بدلاً من ذلك.", Toast.LENGTH_LONG).show()
    }
}

/** يصدر كل ملفات crash_logs إلى crash_logs_export.zip في Downloads. */
fun exportCrashesZip(context: Context): File? {
    return runCatching {
        val dir = File(context.filesDir, "crash_logs")
        val files = dir.listFiles()?.filter { it.name.startsWith("crash_") } ?: return@runCatching null
        if (files.isEmpty()) return@runCatching null
        @Suppress("DEPRECATION")
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        downloads.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val out = File(downloads, "qabas_crash_logs_$stamp.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            files.sortedByDescending { it.lastModified() }.forEach { f ->
                zos.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        out
    }.getOrNull()
}

/**
 * يحاكي انهياراً اختبارياً بأمان عبر نفس مسار الإنتاج (بلا قتل العملية).
 */
fun simulateCrashForTesting(context: android.content.Context) {
    QabasCrashGuard.simulateCrash(context)
}

class CrashDiagnosis(
    val why: String,
    val how: String,
    val fix: String
)

fun diagnoseCrash(crash: CrashLogFile): CrashDiagnosis {
    val raw = crash.fullStack
    val full = raw.lowercase()
    val exc = crash.exceptionLine.lowercase()
    val topFrame = firstAppFrame(raw)

    // ─── قاعدة روابط قبس الشائعة ───
    fun codeHint(): String = when {
        "qurandataprovider" in full -> "في QuranDataProvider.kt — قسم تحميل القرآن/التفسير"
        "qabasbrainviewmodel" in full -> "في QabasBrainViewModel.kt — منطق الذكاء المركزي"
        "supabaseservices" in full || "cloudservices" in full -> "في طبقة Supabase/Cloud — المزامنة السحابية"
        "brollengine" in full -> "في BRollEngine.kt — محرك B-Roll/الخلفيات البرمجية"
        "appselfdoctor" in full -> "في AppSelfDoctor.kt — فحوصات الصحة الذاتية"
        "qabascrashguard" in full -> "في QabasCrashGuard.kt — حارس الانهيارات نفسه"
        "mainactivity" in full -> "في MainActivity.kt — الجذر/التنقل"
        "developerdashboardscreen" in full -> "في DeveloperDashboardScreen.kt — لوحة المطور"
        "realservices" in full -> "في RealServices.kt — خدمات Gemini/AI الحقيقية"
        "socialaccountmanager" in full -> "في SocialAccountManager.kt — حسابات التواصل"
        "settingsscreen" in full -> "في SettingsScreen.kt — الإعدادات"
        else -> ""
    }

    val locationHint = if (topFrame != "غير معروف") "الموقع: $topFrame" else ""

    fun diag(why: String, how: String, fix: String): CrashDiagnosis =
        CrashDiagnosis(why = why, how = how, fix = fix)

    return when {
        // ─── انهيارات الذاكرة ───
        "outofmemoryerror" in exc || ("outofmemory" in exc && "error" in exc) || "could not allocate" in full ->
            diag(
                why = "نفاد الذاكرة (OutOfMemoryError) أثناء معالجة بيانات ضخمة دفعة واحدة. $locationHint",
                how = "حدث غالباً في استخراج الإطارات، تحميل الأصول، أو دمج الفيديو. ${codeHint()} تأكد من أن المعالجة تستخدم streaming بدل listInMemory.",
                fix = "أغلق التطبيقات الخلفية، جرّب فكرة أقصر، وإن تكرر حدّث الحارس على نقاط التحويل لـ flow/chunked بدل listOf."
            )

        // ─── Coroutines ───
        "cancellationexception" in exc ->
            diag(
                why = "إلغاء متوقّع من Coroutines (لن يحتاج إصلاحاً عادةً). $locationHint",
                how = "حدث عند إغلاق شاشة/إلغاء scope بشكل طبيعي؛ Kotlin يلقيها لإيقاف الـ job.",
                fix = "تجاهلها — ليست انهياراً فعلياً. لو ظهرت بكثرة في main thread فتحقق من Scope lifetime."
            )

        // ─── FFmpeg ───
        "arthenica" in full || "ffmpegkit" in full || ("ffmpeg" in full && "rc=" in full) ->
            diag(
                why = "فشل FFmpegKit أثناء ترميز/دمج الفيديو. $locationHint",
                how = "الأسباب: صيغة غير مدعومة، نقص مساحة، أو خطأ في وسائط الإدخال. ${codeHint()}",
                fix = "تحقق من المساحة الحرة، استخدم فيديو أقصر، أعد المحاولة؛ إن تكرر أرسل السجل الكامل من لوحة المطور."
            )

        // ─── NullPointerException ───
        "nullpointerexception" in exc || "nullpointer" in exc || "kotlinnullpointer" in exc ->
            diag(
                why = "مرجع Null في مسار غير محصّن. $locationHint",
                how = "استدعت دالة كائناً لم يُملأ (Lazy init فشل، أو nullable لم يُتحقق منه). ${codeHint()}",
                fix = "أعد المحاولة لإعادة إنشاء الكائنات؛ إن تكرر أضف ?. أو ?: في النقطة المحددة وأرسل السباك للمطور."
            )

        // ─── IllegalStateException ───
        "illegalstateexception" in exc ->
            diag(
                why = "حالة كائن غير صحيحة (مثلاً: استدعاء دالة قبل التهيئة). $locationHint",
                how = "${codeHint()} استدعاء على كائن لم يكتمل إنشاؤه أو تم إغلاقه.",
                fix = "تحقق من ترتيب التهيئة؛ إن تكرر شارك السباك — غالباً يحتاج StateFlow بدل lateinit."
            )

        // ─── IllegalArgumentException ───
        "illegalargumentexception" in exc ->
            diag(
                why = "معامل غير صالح مرّ لدالة. $locationHint",
                how = "${codeHint()} قيمة خارج النطاق المتوقع (URL خاطئ، index سالب، URI فارغ).",
                fix = "أعد المحاولة بمدخل مختلف؛ إن تكرر شارك السباك لإضافة requireNotNull/require() حارس."
            )

        // ─── NoSuchMethod / NoClassDefFoundError ───
        "nosuchmethoderror" in exc || "nosuchmethod" in full ->
            diag(
                why = "استدعاء دالة غير موجودة في وقت التشغيل (عدم تطابق إصدارات). $locationHint",
                how = "تعارض بين ProGuard/R8 أو بين مكتبات بنسختين مختلفتين (مثلاً Coil + Compose).",
                fix = "نظّف caches Gradle (.gradle/caches)، أعد البناء بـ clean. إن تكرر أضف keep rules في proguard-rules.pro."
            )
        "noclassdeffounderror" in exc || "classnotfound" in exc ->
            diag(
                why = "كلاس غير موجود وقت التشغيل. $locationHint",
                how = "تعارض حزم أو dexing (multi-dex لم يُفعّل، أو proguard حذف كلاساً مطلوباً).",
                fix = "نظّف caches وأعد البناء؛ إن تكرر فعّل multiDexEnabled وأضف قواعد ProGuard للكلاسات المعنية."
            )

        // ─── StackOverflowError ───
        "stackoverflowerror" in exc ->
            diag(
                why = "تجاوز عمق الـ Stack (استدعاء ذاتي لا نهائي). $locationHint",
                how = "${codeHint()} تكرار لا نهائي — غالباً JSON يحتوي على self-reference أو render متكرر.",
                fix = "أعد المحاولة بمدخل أقصر/أبسط؛ إن تكرر أضف depth-limit أو tailrec."
            )

        // ─── SQLite ───
        "sqlite" in exc || "sqlexception" in exc ->
            diag(
                why = "فشل قاعدة بيانات SQLite المحلية. $locationHint",
                how = "تلف ملف DB أو قفل من عملية أخرى أو امتلاء التخزين.",
                fix = "احذف cache التطبيق من الإعدادات، أعد التشغيل؛ إن تكرر شارك السجل."
            )

        // ─── JSON ───
        "jsonsyntax" in full || "jsonparse" in full || "jsonexception" in exc || "org.json" in full ->
            diag(
                why = "استجابة JSON تالفة من خدمة AI/سحابية. $locationHint",
                how = "Gemini/Supabase أحياناً يخرجان نصاً غير JSON أو باقتطاع. ${codeHint()}",
                fix = "أعد المحاولة؛ إن تكرر أرسل الخام للمطور لتحصين parser."
            )

        // ─── Compose ───
        "compose" in full && ("key" in full || "compositionlocal" in full || "infinite" in full) ->
            diag(
                why = "خطأ في شجرة Compose. $locationHint",
                how = "${codeHint()} مفتاح مكرّر أو CompositionLocal مفقود أو recurse في layout.",
                fix = "أعد فتح الشاشة المعنية؛ إن تكرر شارك السباك."
            )

        // ─── Coil / Image ───
        "coil" in full || "imagedecoder" in full ->
            diag(
                why = "فشل تحميل/فك ترميز صورة. $locationHint",
                how = "صورة تالفة، صيغة غير مدعومة، أو امتلاء الذاكرة المؤقتة.",
                fix = "أعد المحاولة بعد مسح cache الصور؛ إن تكرر استبدلها بصيغة JPG."
            )

        // ─── Gemini / AI ───
        "gemini" in exc || "com.google.ai" in full || "generativelanguage" in full || "api key" in full ->
            diag(
                why = "مفتاح Gemini مفقود/منتهٍ/مرفوض. $locationHint",
                how = "حدث أثناء تحليل الفكرة، توليد السكربت، أو تطبيق الأسلوب. ${codeHint()}",
                fix = "افتح الإعدادات وتحقق من المفتاح (يبدأ بـ AIzaSy)، أو أضف مفتاحاً جديداً من aistudio.google.com."
            )

        // ─── شبكة ───
        "socket" in exc || "connectexception" in exc || "unknownhost" in exc || "timeout" in exc || "ssl" in exc ->
            diag(
                why = "انقطاع الشبكة أو حجب الاتصال. $locationHint",
                how = "حدث أثناء جلب وسائط، استدعاء AI، أو مزامنة سحابية. ${codeHint()}",
                fix = "تحقق من الإنترنت؛ التطبيق يعمل بدون مفاتيح عبر الخلفيات البرمجية والنطق المدمج."
            )

        // ─── Pexels / Pixabay ───
        "pexels" in exc || "pixabay" in exc ->
            diag(
                why = "فشل جلب B-Roll من Pexels/Pixabay. $locationHint",
                how = "مفتاح غير صالح أو رفض عن بُعد. ${codeHint()}",
                fix = "تحقق من مفاتيح Pexels/Pixabay؛ التطبيق يتحول تلقائياً للخلفيات البرمجية."
            )

        // ─── TTS ───
        "texttospeech" in exc || "azurespeech" in exc || "elevenlabs" in exc ->
            diag(
                why = "فشل محرك توليد الصوت. $locationHint",
                how = "${codeHint()} نقص مفتاح، انقطاع خدمة، أو عدم تثبيت أصوات محلية.",
                fix = "سيتحول التطبيق للنطق المدمج تلقائياً؛ تأكد من تثبيت أصوات عربية على جهازك."
            )

        // ─── Security ───
        "securityexception" in exc || "permission denial" in exc ->
            diag(
                why = "رفض إذن من نظام Android. $locationHint",
                how = "${codeHint()} طلب إذن (كاميرا/تخزين/مايك) قبل منحه فعلياً.",
                fix = "منح الإذن من إعدادات الجهاز → التطبيقات → قبس → الأذونات."
            )

        // ─── ClassCast ───
        "classcastexception" in exc ->
            diag(
                why = "تحويل نوع غير صالح. $locationHint",
                how = "كائن نوعه الفعلي لا يطابق النوع المُفترض — غالباً نتيجة دالة ترجع Any? بدون فحص.",
                fix = "أعد المحاولة؛ إن تكرر شارك السباك — غالباً يحتاج safe cast أو instanceof."
            )

        // ─── ConcurrentModification ───
        "concurrentmodification" in exc ->
            diag(
                why = "تعديل قائمة أثناء التكرار عليها. $locationHint",
                how = "${codeHint()} for-loop على List تتم كتابتها من coroutine آخر في نفس الوقت.",
                fix = "أعد المحاولة؛ إن تكرر استبدل بـ CopyOnWriteArrayList أو synchronized."
            )

        // ─── Supabase / Cloud ───
        "supabase" in full || "retrofit" in full || "okhttp" in full ->
            diag(
                why = "خطأ في طبقة Supabase/Retrofit السحابية. $locationHint",
                how = "${codeHint()} مزامنة المستخدمين/المشاريع/الإحصائيات.",
                fix = "تحقق من الاتصال؛ التطبيق يتدهور للوضع المحلي بأمان عند غياب الخادم."
            )

        // ─── default ───
        else ->
            diag(
                why = "استثناء غير مصنَّف بعد. $locationHint",
                how = "${codeHint()} إذا ظهر متكرراً أضف قاعدة جديدة في diagnoseCrash (DeveloperDashboardScreen.kt).",
                fix = "انسخ السباك وأرسله للمطور لتوسيع التشخيص."
            )
    }
}

/** خيط الفلتر النشط: الكل / main / worker / coroutine / غير معروف */
private enum class CrashThreadFilter(val label: String, val match: (String) -> Boolean) {
    ALL("الكل", { true }),
    MAIN("main", { it.equals("main", ignoreCase = true) }),
    WORKER("worker", { it.contains("worker", ignoreCase = true) || it.startsWith("DefaultDispatcher") || it.contains("pool") }),
    COROUTINE("coroutine", { it.contains("DefaultDispatcher", ignoreCase = true) || it.contains("coroutine", ignoreCase = true) }),
    UNKNOWN("غير معروف", { it.equals("غير معروف", ignoreCase = true) || it.isBlank() })
}

@Composable
fun CrashLogsSection(context: Context) {
    CrashLogsHubScreen(onBack = null)
}

private enum class CrashViewMode { LIST, GROUPS }

@Composable
private fun SummaryStrip(total: Int, types: Int, lastMs: Long) {
    val lastText = if (lastMs > 0L) {
        val mins = (System.currentTimeMillis() - lastMs) / 60_000
        when {
            mins < 1 -> "الآن"
            mins < 60 -> "قبل ${mins} د"
            mins < 1440 -> "قبل ${mins / 60} س"
            else -> "قبل ${mins / 1440} يوم"
        }
    } else "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1629), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatPill("إجمالي", total.toString(), GoldPrimary)
        StatPill("أنواع", types.toString(), Color(0xFF22D3EE))
        StatPill("آخر انهيار", lastText, Color(0xFFF87171))
    }
}

@Composable
private fun ReleaseHealthStrip(context: Context) {
    val (crashes, launches) = remember { CrashBreadcrumbs.releaseHealth(context) }
    val rate = if (launches > 0) crashes * 100.0 / launches else 0.0
    val tint = when {
        launches == 0 -> TextSecondary
        rate < 1.0 -> Color(0xFF10B981)
        rate < 5.0 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("صحة الإصدار الحالي", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    if (launches == 0) "لا بيانات إقلاع بعد" else "$crashes انهيار / $launches إقلاع",
                    color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp
                )
            }
            Text(
                if (launches == 0) "—" else "${"%.1f".format(rate)}٪",
                color = tint, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.sp)
    }
}

@Composable
private fun CrashCard(crash: CrashLogFile, onOpenStack: () -> Unit) {
    val diag = remember(crash.fileName) { diagnoseCrash(crash) }
    val stamp = remember(crash.timestampMs) {
        runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(crash.timestampMs))
        }.getOrElse { crash.fileName }
    }
    val topFrame = remember(crash.fullStack) { firstAppFrame(crash.fullStack) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1629), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF232D47), RoundedCornerShape(10.dp))
            .clickable { onOpenStack() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = crash.exceptionLine.ifBlank { "غير مصنَّف" }.take(140),
                    color = Color(0xFFF87171),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text("📍 $topFrame", color = Color(0xFF22D3EE), fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(stamp, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DiagnosisRow("لماذا ‽", diag.why, Color(0xFFFB7185))
            DiagnosisRow("الحل ✓", diag.fix, Color(0xFF34D399))
        }
        Text(
            "🛈 اضغط لفتح السباك الكامل • نسخ • مشاركة",
            color = Color(0xFF22D3EE),
            fontFamily = NotoSansFont,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CrashGroupCard(group: CrashGroup, onOpenSample: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1629), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF232D47), RoundedCornerShape(10.dp))
            .clickable { onOpenSample() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📦 ${group.type}", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Surface(color = Color(0xFFF87171), shape = RoundedCornerShape(6.dp)) {
                Text("× ${group.count}", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Text("آخر ظهور: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(group.latestMs))}", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.sp)
        Text("📍 ${firstAppFrame(group.sample.fullStack)}", color = Color(0xFF22D3EE), fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
 private fun CrashStackDialog(crash: CrashLogFile, onDismiss: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit) {
    var aiAnalysis by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    val aiScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("سباك الانهيار", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(crash.fileName, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "📍 ${firstAppFrame(crash.fullStack)}",
                    color = Color(0xFF22D3EE),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF050B17), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = crash.fullStack.ifBlank { "(ملف فارغ)" },
                        color = Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                // ── تحليل الذكاء الاصطناعي لسبب العطل ──
                if (aiBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("يحلل النموذج السباك...", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                } else if (aiAnalysis != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(aiAnalysis!!, color = Color.White, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            aiBusy = true
                            aiScope.launch {
                                val stack = crash.fullStack.take(4000)
                                val answer = AppServices.chatWithAssistant(
                                    listOf(
                                        Pair(
                                            true,
                                            "أنت خبير أعطال أندرويد (Kotlin). حلل هذا السباك باختصار بالعربية: السطر المسبب، لماذا حدث، والإصلاح المقترح بسطرين لكل نقطة.\n\n$stack"
                                        )
                                    ),
                                    "أنت محلل أعطال. تجيب بالعربية بثلاث نقاط فقط: السبب/لماذا/الإصلاح."
                                )
                                aiAnalysis = answer.take(1200)
                                aiBusy = false
                            }
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تحليل بالذكاء الاصطناعي", color = Color(0xFF8B5CF6), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy) { Text("📋 نسخ", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp) }
                TextButton(onClick = onShare) { Text("📤 مشاركة", color = Color(0xFF22D3EE), fontFamily = NotoSansFont, fontSize = 12.sp) }
                TextButton(onClick = onDismiss) { Text("إغلاق", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp) }
            }
        },
        containerColor = Color(0xFF0F1629),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun DiagnosisRow(label: String, text: String, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = accent, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Text(text, color = Color.LightGray, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun DevUserItemRow(user: DevUser, onTypeChange: (String) -> Unit, onSuspendToggle: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name, 
                        color = if (user.isSuspended) Color.Red else Color.White, 
                        fontFamily = CairoFont, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 15.sp,
                        textDecoration = if (user.isSuspended) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                    if (user.isSuspended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = Color.Red.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)) {
                            Text("موقوف", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontFamily = NotoSansFont)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val badgeColor = when(user.type) {
                        "خاص" -> GoldPrimary
                        "مطور" -> GoldPrimary
                        else -> Color.Gray
                    }
                    val badgeTextColor = if (user.type == "Freemium") Color.White else DeepSlate
                    
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = user.type,
                            color = badgeTextColor,
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(user.email, color = Color.Gray, fontSize = 12.sp, fontFamily = NotoSansFont)
            }
            
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات الحساب", tint = Color.Gray)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DeepSlate)
                ) {
                    DropdownMenuItem(
                        text = { Text("مستخدم عادي (Freemium)", color = Color.White, fontFamily = CairoFont) },
                        onClick = { onTypeChange("Freemium"); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("حساب خاص (أقارب)", color = GoldPrimary, fontFamily = CairoFont) },
                        onClick = { onTypeChange("خاص"); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("مطور نظام", color = GoldPrimary, fontFamily = CairoFont) },
                        onClick = { onTypeChange("مطور"); expanded = false }
                    )
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = { Text(if (user.isSuspended) "إلغاء الإيقاف" else "إيقاف الحساب", color = Color.Red, fontFamily = CairoFont) },
                        onClick = { onSuspendToggle(); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
fun PromoCodesSection(context: Context) {
    var promoCode by remember { mutableStateOf("") }
    var durationDays by remember { mutableStateOf("") }
    var giftCode by remember { mutableStateOf("") }
    var pointsAmount by remember { mutableStateOf("") }
    val giftManager = remember { GiftManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var customPromos by remember { mutableStateOf(giftManager.getCustomPromoCodes()) }
    var customGifts by remember { mutableStateOf(giftManager.getCustomGiftCards()) }

    // تحديث الكاش من Firestore كلما فُتحت هذه الشاشة، ضماناً لرؤية أحدث الأكواد.
    LaunchedEffect(Unit) {
        runCatching { giftManager.loadValidCodesFromCloud() }
            .onFailure { android.util.Log.e("PromoCodesSection", "loadValidCodesFromCloud failed: ${it.message}", it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            // Header
            Text(
                "نظام المكافآت والأكواد",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                "قم بتوليد أكواد هدايا (للنقاط) أو أكواد ترقية (لقبس برو) لتوزيعها على المستخدمين.",
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 14.sp
            )
        }

        // Section 1: Promo Codes (Pro)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("توليد كود ترقية (قبس برو)", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = promoCode,
                        onValueChange = { promoCode = it.uppercase() },
                        label = { Text("رمز الكود (مثال: NEW-YEAR-PRO)", color = Color.Gray, fontFamily = CairoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationDays,
                        onValueChange = { durationDays = it },
                        label = { Text("المدة بالأيام (مثال: 30)", color = Color.Gray, fontFamily = CairoFont) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (promoCode.isNotBlank() && durationDays.toIntOrNull() != null) {
                                coroutineScope.launch {
                                    val saved = giftManager.addCustomPromoCode(promoCode, durationDays.toInt())
                                    customPromos = giftManager.getCustomPromoCodes()
                                    if (saved) {
                                        Toast.makeText(context, "تم إضافة كود الترقية بنجاح!", Toast.LENGTH_SHORT).show()
                                        promoCode = ""
                                        durationDays = ""
                                    } else {
                                        Toast.makeText(context, "فشل حفظ الكود في السحابة. تحقق من الاتصال وحاول مجدداً.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "تأكد من إدخال البيانات بشكل صحيح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("توليد كود الترقية", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Gift Cards (Points)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("توليد بطاقة هدايا (نقاط ذهبية)", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = giftCode,
                        onValueChange = { giftCode = it.uppercase() },
                        label = { Text("رمز البطاقة (مثال: WELCOME-1000)", color = Color.Gray, fontFamily = CairoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pointsAmount,
                        onValueChange = { pointsAmount = it },
                        label = { Text("عدد النقاط (مثال: 500)", color = Color.Gray, fontFamily = CairoFont) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (giftCode.isNotBlank() && pointsAmount.toIntOrNull() != null) {
                                coroutineScope.launch {
                                    val saved = giftManager.addCustomGiftCard(giftCode, pointsAmount.toInt())
                                    customGifts = giftManager.getCustomGiftCards()
                                    if (saved) {
                                        Toast.makeText(context, "تم إضافة بطاقة الهدايا بنجاح!", Toast.LENGTH_SHORT).show()
                                        giftCode = ""
                                        pointsAmount = ""
                                    } else {
                                        Toast.makeText(context, "فشل حفظ البطاقة في السحابة. تحقق من الاتصال وحاول مجدداً.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "تأكد من إدخال البيانات بشكل صحيح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("توليد بطاقة النقاط", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        item {
            Text("الأكواد المخصصة النشطة", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (customPromos.isEmpty() && customGifts.isEmpty()) {
                Text("لا توجد أكواد مخصصة بعد.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 14.sp)
            }
        }

        items(customPromos.toList()) { (code, duration) ->
            ActiveCodeItem(code = code, type = "ترقية برو (${duration / (24 * 60 * 60 * 1000L)} أيام)", icon = Icons.Default.WorkspacePremium)
        }

        items(customGifts.toList()) { (code, points) ->
            ActiveCodeItem(code = code, type = "رصيد نقاط ($points نقطة)", icon = Icons.Default.Toll)
        }
    }
}

@Composable
fun ActiveCodeItem(code: String, type: String, icon: ImageVector) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(code, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(type, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Qabas Code", code)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "تم نسخ الكود: $code", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الكود", tint = GoldPrimary)
                }
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Text("فعال", color = Color(0xFF10B981), fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevStudioSignatureSection(context: Context) {
    val scope = rememberCoroutineScope()
    val accountService = remember { AppServices.getAccountService(context) }
    var isWatermarkEnabled by remember { mutableStateOf(accountService.isDevWatermarkEnabled) }
    var watermarkText by remember { mutableStateOf(accountService.devWatermarkText) }
    var watermarkStyle by remember { mutableStateOf(accountService.devWatermarkStyle) }
    var isExclusiveStudioEnabled by remember { mutableStateOf(accountService.isDevExclusiveStudioEnabled) }

    var developerTopic by remember { mutableStateOf("") }
    var isGeneratingScript by remember { mutableStateOf(false) }
    var generatedDevScript by remember { mutableStateOf("") }

    var showDevSeriesDialog by remember { mutableStateOf(false) }
    var showDevSeoDialog by remember { mutableStateOf(false) }

    if (showDevSeriesDialog) {
        AutoSeriesGeneratorDialog(
            context = context,
            initialSeriesTitle = "سلسلة عظماء التاريخ الإسلامي",
            onDismiss = { showDevSeriesDialog = false },
            onExportSeriesToProjects = {
                showDevSeriesDialog = false
            }
        )
    }

    if (showDevSeoDialog) {
        ViralSeoHashtagsDialog(
            context = context,
            initialTopicOrScript = if (developerTopic.isNotBlank()) developerTopic else "سلسلة قصص ورسائل إيمانية",
            onDismiss = { showDevSeoDialog = false },
            onApplySeoData = { _, _ ->
                showDevSeoDialog = false
            }
        )
    }

    val styles = listOf(
        "ختم ذهبي سفلي (Golden Master Bar)",
        "ختم زاوية علوي شفاف (Top Corner Seal)",
        "توقيع سينمائي كامل (Full Cinematic Signature)"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(DeepSlate, Color(0xFF151B2B))))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(GoldPrimary.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, GoldPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("استوديو وتوقيع المطور ✦", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("صناعة محتوى خاص بتميز فريد وطباعة الختم الذهبي على الفيديوهات", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Watermark & Signature Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("توقيع المطور على الفيديوهات (Video Watermark)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل طباعة التوقيع الذهبي", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("طباعة توقيع المطور تلقائياً في أسفل الفيديو عند التصدير.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isWatermarkEnabled,
                            onCheckedChange = { 
                                isWatermarkEnabled = it
                                accountService.isDevWatermarkEnabled = it
                                Toast.makeText(context, if (it) "تم تفعيل توقيع المطور ✦" else "تم إيقاف التوقيع", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
                        )
                    }

                    if (isWatermarkEnabled) {
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { 
                                watermarkText = it
                                accountService.devWatermarkText = it
                            },
                            label = { Text("نص التوقيع والختم المخصص", color = Color.Gray, fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text("نمط وموقع التوقيع:", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            styles.forEach { style ->
                                val isSelected = watermarkStyle == style
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { 
                                        watermarkStyle = style
                                        accountService.devWatermarkStyle = style
                                    },
                                    label = { Text(style.split(" ")[0], fontFamily = NotoSansFont, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = Color.Black,
                                        containerColor = DeepSlate,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Live Preview Box
                        Text("معاينة توقيع المطور على الفيديو:", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF151B2B), DeepSlate)))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text("مُعاينة شاشة الفيديو (1080x1920)", color = Color.White.copy(alpha = 0.3f), fontFamily = CairoFont, modifier = Modifier.align(Alignment.Center))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DeepSlate.copy(alpha = 0.9f))
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(watermarkText, color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Exclusive Developer Studio & Master Content Engine
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("استوديو صناعة محتوى المطور الحصري 🎬", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("أداة مخصصة لإنشاء مقاطع وفيديوهات ذات طابع إخراجي رفيع استناداً لقواعد بيانات المعرفة الإسلامية وشؤون جميع المستخدمين.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("محرك التوجيه الفائق (Master Prompting)", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("توليد نصوص بأعلى معايير التوثيق الشرعي والأسلوب الوثائقي السينمائي.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isExclusiveStudioEnabled,
                            onCheckedChange = { 
                                isExclusiveStudioEnabled = it
                                accountService.isDevExclusiveStudioEnabled = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
                        )
                    }

                    OutlinedTextField(
                        value = developerTopic,
                        onValueChange = { developerTopic = it },
                        label = { Text("موضوع فيديو المطور (مثال: بشائر الأمة، رد الشبهات، رسالة للمجتمع)", color = Color.Gray, fontFamily = CairoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { showDevSeriesDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF151B2B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مولد السلاسل والروابط التلقائية (Auto Series Engine) 🚀", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = { showDevSeoDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF151B2B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مُحلل الكلمات والوسوم الفيروسية (SEO Predictor) 📈", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (developerTopic.isBlank()) {
                                Toast.makeText(context, "يرجى كتابة موضوع الفيديو أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGeneratingScript = true
                            scope.launch {
                                val prompt = "بصفتك المطور الرئيسي لتطبيق قبس، اكتب سيناريو فيديو وثائقي فاخر بتوقيع المطور عن: $developerTopic. اذكر أدلة شرعية موثقة وتراكيب سينمائية ممتازة."
                                val res = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                isGeneratingScript = false
                                generatedDevScript = res
                            }
                        },
                        enabled = !isGeneratingScript,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        if (isGeneratingScript) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري توليد سيناريو المطور...", color = Color.Black, fontFamily = CairoFont)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("توليد سيناريو المطور الفائق 🚀", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (generatedDevScript.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepSlate),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("السيناريو المُولد بتوقيع المطور الذهبي:", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(generatedDevScript, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("ملاحظة: عند تصدير هذا الفيديو سيتم إضافة ختم المطور: $watermarkText تلقائياً.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportConfigDevSection(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(SupportConfigManager.current(context)) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cfg = SupportConfigManager.refreshFromCloud(context)
    }

    var whatsappUrl by remember(cfg) { mutableStateOf(cfg.whatsappGroupUrl) }
    var whatsappName by remember(cfg) { mutableStateOf(cfg.whatsappGroupName) }
    var paypalUrl by remember(cfg) { mutableStateOf(cfg.paypalUrl) }
    var usdtAddress by remember(cfg) { mutableStateOf(cfg.usdtTronAddress) }
    var bankName by remember(cfg) { mutableStateOf(cfg.bankName) }
    var bankIban by remember(cfg) { mutableStateOf(cfg.bankIban) }
    var hadithQuote by remember(cfg) { mutableStateOf(cfg.hadithQuote) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("إعدادات الدعم ومجتمع واتساب السحابية", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("تعديل روابط ومحافظ الدعم ومجموعة واتساب لجميع المستخدمين.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Surface(
                color = Color(0xFF25D366).copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("WhatsApp + Cloud", color = Color(0xFF25D366), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رابط مجموعة واتساب الرسمية:", color = Color(0xFF25D366), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = whatsappUrl,
                    onValueChange = { whatsappUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF25D366),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Text("رابط PayPal / بطاقة الدفع:", color = Color(0xFF0070BA), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = paypalUrl,
                    onValueChange = { paypalUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Text("عنوان محفظة USDT (Tron TRC20):", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = usdtAddress,
                    onValueChange = { usdtAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Text("رقم الآيبان (IBAN):", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = bankIban,
                    onValueChange = { bankIban = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Text("الحديث الشريف / الآية التحفيزية:", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                OutlinedTextField(
                    value = hadithQuote,
                    onValueChange = { hadithQuote = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        isSaving = true
                        coroutineScope.launch {
                            val next = cfg.copy(
                                whatsappGroupUrl = whatsappUrl.trim(),
                                whatsappGroupName = whatsappName.trim(),
                                paypalUrl = paypalUrl.trim(),
                                usdtTronAddress = usdtAddress.trim(),
                                bankName = bankName.trim(),
                                bankIban = bankIban.trim(),
                                hadithQuote = hadithQuote.trim()
                            )
                            val ok = SupportConfigManager.saveAndPublish(context, next, AdminGuard.currentIdentity(context))
                            cfg = next
                            isSaving = false
                            android.widget.Toast.makeText(
                                context,
                                if (ok) "تم نشر بيانات الدعم ومجموعة واتساب لجميع المستخدمين سحابياً! 🚀" else "تم الحفظ محلياً",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("حفظ ونشر التعديلات سحابياً للجميع", color = Color.Black, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

