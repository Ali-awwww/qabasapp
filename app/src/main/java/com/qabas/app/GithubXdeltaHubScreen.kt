package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة الاتصال المباشر بمستودع GitHub وفحص إصدارات وتحديثات xdelta التفاضلية
 * بنمط Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubXdeltaHubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // بيانات المستودع — القيم الرسمية ثابتة للجميع، والتوكن والمستودع
    // المخصص للمطور فقط (AdminGuard). لا يُضمَّن أي سر في الكود.
    val isDevMode = remember { AdminGuard.isDashboardAccessAllowed(context) }
    var ownerInput by remember {
        mutableStateOf(prefs.getString("build_center_owner", "Ali-awwww")?.ifBlank { "Ali-awwww" } ?: "Ali-awwww")
    }
    var repoInput by remember {
        mutableStateOf(prefs.getString("build_center_repo", "qabasapp")?.ifBlank { "qabasapp" } ?: "qabasapp")
    }
    var tokenInput by remember {
        mutableStateOf(prefs.getString("build_center_token", "") ?: "")
    }
    var showToken by remember { mutableStateOf(false) }
    var isEditingRepo by remember { mutableStateOf(false) }

    // تطهير لمرة واحدة: إسقاط أي توكن مسرّب قديم مخزن على الجهاز
    LaunchedEffect(Unit) {
        val stored = prefs.getString("build_center_token", "")
        if (!stored.isNullOrBlank() && UpdateManager.isRetiredToken(stored)) {
            prefs.edit().remove("build_center_token").apply()
            tokenInput = ""
        }
        if (!isDevMode) {
            ownerInput = "Ali-awwww"
            repoInput = "qabasapp"
            tokenInput = ""
        }
    }

    // حالة الفحص الحي
    var isChecking by remember { mutableStateOf(false) }
    var inspectionResult by remember { mutableStateOf<UpdateManager.GitHubReleaseInspection?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // حالة التحميل والتثبيت
    val downloadProgress by UpdateManager.downloadProgress.collectAsState()
    val downloadResult by UpdateManager.downloadResult.collectAsState()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadPhaseName by remember { mutableStateOf("") }

    // معمارية الجهاز الحالية
    val deviceAbi = remember {
        val abis = Build.SUPPORTED_ABIS
        when {
            abis.any { it == "arm64-v8a" } -> "arm64-v8a"
            abis.any { it == "armeabi-v7a" } -> "armeabi-v7a"
            abis.any { it == "x86_64" } -> "x86_64"
            else -> abis.firstOrNull() ?: "arm64-v8a"
        }
    }

    // الاستماع لتقدم التحميل
    LaunchedEffect(downloadProgress) {
        val p = downloadProgress
        if (p != null) {
            isDownloading = true
            downloadPhaseName = when (p.phase) {
                UpdateManager.Phase.DOWNLOADING_DELTA -> "جاري تنزيل ملف الـ Delta التفاضلي..."
                UpdateManager.Phase.APPLYING_PATCH -> "جاري تطبيق الباتش وتوليد الـ APK محلياً..."
                UpdateManager.Phase.DOWNLOADING_FULL -> "جاري تنزيل الـ APK الكامل..."
            }
        }
    }

    LaunchedEffect(downloadResult) {
        val res = downloadResult
        if (res != null) {
            isDownloading = false
            Toast.makeText(context, res.third, Toast.LENGTH_LONG).show()
        }
    }

    // دالة الفحص المباشر — غير المطور: القناة الرسمية فقط وبدون توكن
    fun checkReleases() {
        isChecking = true
        errorMessage = null
        coroutineScope.launch {
            val dev = AdminGuard.isDashboardAccessAllowed(context)
            val targetRepo = if (dev) "${ownerInput.trim()}/${repoInput.trim()}"
                else "Ali-awwww/qabasapp"
            val res = UpdateManager.inspectGitHubRelease(
                context = context,
                customRepo = targetRepo,
                customToken = if (dev) tokenInput.trim().ifBlank { null } else null
            )
            isChecking = false
            res.fold(
                onSuccess = { inspection ->
                    inspectionResult = inspection
                    if (inspection.isNewerThanCurrent) {
                        Toast.makeText(context, "تم العثور على تحديث جديد: ${inspection.tagName} 🚀", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "أنت على أحدث إصدار متطابق مع المستودع ✓", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "تعذر جلب بيانات الإصدار من المستودع"
                }
            )
        }
    }

    // فحص تلقائي أولي عند الدخول
    LaunchedEffect(Unit) {
        checkReleases()
    }

    fun saveRepoCredentials() {
        // كتابة بيانات المستودع والمفتاح حصرية للمطور
        if (!AdminGuard.isDashboardAccessAllowed(context)) {
            Toast.makeText(context, "إعدادات المستودع للمطور فقط 🔒", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit()
            .putString("build_center_owner", ownerInput.trim())
            .putString("build_center_repo", repoInput.trim())
            .putString("build_center_token", tokenInput.trim())
            .apply()

        // مزامنة مع خزانة المستودعات
        runCatching {
            RepoVaultManager.addOrUpdateRepo(
                context = context,
                entry = RepoVaultEntry(
                    id = "repo_ali_awwww_qabasapp",
                    owner = ownerInput.trim(),
                    repo = repoInput.trim(),
                    token = tokenInput.trim(),
                    customName = "مستودع قبس الرسمي (${ownerInput.trim()}/${repoInput.trim()})",
                    description = "مستودع GitHub المباشر لتحديثات xdelta وبناء الـ APK"
                ),
                setAsActive = true
            )
        }
        isEditingRepo = false
        Toast.makeText(context, "تم حفظ بيانات الاتصال بنجاح ✓", Toast.LENGTH_SHORT).show()
        checkReleases()
    }

    fun resetToOfficialDefaults() {
        ownerInput = "Ali-awwww"
        repoInput = "qabasapp"
        tokenInput = ""
        saveRepoCredentials()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Text(
                            text = "تحديثات xdelta و GitHub",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/${ownerInput.trim()}/${repoInput.trim()}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = "فتح في المتصفح",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF070B14)
                )
            )
        },
        containerColor = Color(0xFF070B14)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1️⃣ بطاقة هوية المستودع والاتصال المباشر (Spatial HUD Connectivity Banner)
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0D1527).copy(alpha = 0.85f),
                borderColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "الاتصال المباشر بـ GitHub",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "قناة تحديثات xdelta الذكية",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // شارة الحالة
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Text(
                                    text = if (isDevMode && tokenInput.isNotBlank()) "متصل ومفوض ✓" else "القناة الرسمية ✓",
                                    color = Color(0xFF10B981),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // رابط المستودع
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString("https://github.com/${ownerInput.trim()}/${repoInput.trim()}"))
                                Toast.makeText(context, "تم نسخ رابط المستودع 📋", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF090E1A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "https://github.com/${ownerInput.trim()}/${repoInput.trim()}",
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                        }
                    }

                    // قسم المطور فقط: التوكن والتحكم بالمستودع (مخفي عن المستخدمين)
                    if (isDevMode) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // عرض التوكن
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF090E1A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (tokenInput.isBlank()) "بدون توكن (قناة عامة)" else if (showToken) tokenInput else "ghp_" + "•".repeat(24) + tokenInput.takeLast(6),
                                    color = Color(0xFF94A3B8),
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { showToken = !showToken }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "إظهار",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // أزرار التحكم بالمستودع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditingRepo = !isEditingRepo },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEditingRepo) "إلغاء التعديل" else "تعديل المستودع", color = Color(0xFFCBD5E1), fontFamily = CairoFont, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { resetToOfficialDefaults() },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الافتراضي المعتمد", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }

                    // نموذج التعديل في حال تم تفعيله
                    AnimatedVisibility(visible = isEditingRepo) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ownerInput,
                                onValueChange = { ownerInput = it },
                                label = { Text("مالك المستودع (Owner)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = repoInput,
                                onValueChange = { repoInput = it },
                                label = { Text("اسم المستودع (Repo)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it },
                                label = { Text("توكن GitHub (PAT)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = { saveRepoCredentials() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("حفظ وتحديث الاتصال فورياً", color = Color(0xFF050811), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    } // end if (isDevMode) — قسم المطور
                }
            }

            // 2️⃣ قياسات محرك الـ xdelta والعتاد (Spatial Telemetry & Hardware Spec)
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0D1527).copy(alpha = 0.65f),
                borderColor = Color(0xFF334155),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "محرك xdelta3 التفاضلي ومعمارية الجهاز",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("معمارية معالج جهازك:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.5.sp)
                        Surface(
                            color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = deviceAbi,
                                color = Color(0xFF00E5FF),
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ثنائي xdelta3 المطلوب:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.5.sp)
                        Text("xdelta3-$deviceAbi (Static ELF)", color = Color.White, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("متوسط توفير البيانات:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.5.sp)
                        Text("85% - 95% مقارنة بـ APK كامل ⚡", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("النسخة المثبتة الحالية:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.5.sp)
                        Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Color.White, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                    }
                }
            }

            // 3️⃣ زر فحص أحدث إصدارات xdelta المباشر (Primary Scan Action)
            Button(
                onClick = { checkReleases() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isChecking && !isDownloading
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF050811),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "جاري فحص وتدقيق المستودع...",
                        color = Color(0xFF050811),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF050811),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "فحص أحدث إصدارات xdelta من المستودع ⚡",
                        color = Color(0xFF050811),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // رسالة الخطأ إن وجدت
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF4444),
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // 4️⃣ نتائج فحص الإصدار من GitHub (Inspection Results Card)
            inspectionResult?.let { info ->
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF0D1527).copy(alpha = 0.9f),
                    borderColor = if (info.isNewerThanCurrent) Color(0xFF10B981) else Color(0xFF00E5FF).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "إصدار المستودع: ${info.tagName}",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (info.publishedAt.isNotBlank()) {
                                    Text(
                                        text = "نُشر في: ${info.publishedAt.take(10)}",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            // شارة الحالة
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (info.isNewerThanCurrent) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (info.isNewerThanCurrent) Color(0xFF10B981) else Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (info.isNewerThanCurrent) "تحديث جديد متاح 🚀" else "الإصدار الأخير ✓",
                                    color = if (info.isNewerThanCurrent) Color(0xFF10B981) else Color(0xFF38BDF8),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // أصول الإصدار (Assets Analysis HUD)
                        Text(
                            text = "الأصول المكتشفة في الإصدار (Release Assets):",
                            color = Color(0xFFE2E8F0),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. ملف الـ Delta
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF090E1A),
                            border = BorderStroke(1.dp, if (info.deltaAsset != null) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = if (info.deltaAsset != null) Color(0xFF10B981) else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = info.deltaAsset ?: "ملف .delta (تفاضلي)",
                                        color = if (info.deltaAsset != null) Color.White else Color(0xFF64748B),
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = if (info.deltaAsset != null) "${UpdateManager.formatSize(info.deltaSize)} ⚡" else "غير متوفر",
                                    color = if (info.deltaAsset != null) Color(0xFF10B981) else Color(0xFF64748B),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. ملف الـ APK الكامل
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF090E1A),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = info.apkAsset ?: "ملف APK كامل",
                                        color = Color.White,
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = if (info.apkAsset != null) UpdateManager.formatSize(info.apkSize) else "غير متوفر",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 3. ثنائيات xdelta3
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF090E1A),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "ثنائيات xdelta3 المرفقة",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = if (info.xdeltaBinaries.isNotEmpty()) "${info.xdeltaBinaries.size} معماريات جاهزة ✓" else "مدمج تلقائياً",
                                    color = Color(0xFF00E5FF),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // ملاحظات الإصدار إن وجدت
                        if (info.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ملاحظات وتغييرات الإصدار:",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF090E1A),
                                border = BorderStroke(1.dp, Color(0xFF1E293B))
                            ) {
                                Text(
                                    text = info.releaseNotes,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // أزرار التثبيت الفوري
                        if (info.apkUrl != null) {
                            val updateInfoObj = remember(info) {
                                UpdateManager.UpdateInfo(
                                    versionName = info.tagName.removePrefix("v").substringBefore("+"),
                                    versionCode = 999999, // forcing execution
                                    deltaUrl = info.deltaUrl,
                                    apkUrl = info.apkUrl,
                                    releaseNotes = info.releaseNotes,
                                    deltaSize = info.deltaSize,
                                    apkSize = info.apkSize,
                                    xdeltaUrls = info.xdeltaUrls
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (info.deltaUrl != null && info.deltaSize > 0) {
                                    Button(
                                        onClick = {
                                            isDownloading = true
                                            UpdateDownloadService.start(context, updateInfoObj, forceFull = false)
                                            Toast.makeText(context, "بدأ تنزيل التحديث التفاضلي الصغير ⚡", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.2f).height(44.dp),
                                        enabled = !isDownloading
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "تثبيت xdelta (${UpdateManager.formatSize(info.deltaSize)}) ⚡",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        isDownloading = true
                                        UpdateDownloadService.start(context, updateInfoObj, forceFull = true)
                                        Toast.makeText(context, "بدأ تنزيل ملف الـ APK الكامل 📦", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF475569)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    enabled = !isDownloading
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "APK كامل 📦",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5️⃣ شريط التقدم اللحظي أثناء التنزيل والتثبيت
            AnimatedVisibility(visible = isDownloading) {
                downloadProgress?.let { prog ->
                    GlassmorphicSurface(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF0D1527),
                        borderColor = Color(0xFF00E5FF),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = downloadPhaseName,
                                    color = Color(0xFF00E5FF),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${prog.percent}%",
                                    color = Color.White,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { prog.percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF00E5FF),
                                trackColor = Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${UpdateManager.formatSize(prog.bytesDownloaded)} / ${UpdateManager.formatSize(prog.totalBytes)}",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.sp
                                )
                                if (prog.speedBytesPerSec > 0) {
                                    Text(
                                        text = UpdateManager.formatSpeed(prog.speedBytesPerSec),
                                        color = Color(0xFF10B981),
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    UpdateDownloadService.cancel(context)
                                    isDownloading = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("إلغاء التحديث", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
