package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * مستودع المستودعات ومحور البناء (Spatial Repo Vault & CI/CD Hub):
 * - خزانة المستودعات المتعددة: ربط عبر الرابط والمفتاح (Direct Link & Key) أو عبر رابط تفويض GitHub بموافقة المستخدم.
 * - غرفة الوكيل هي غرفة التنفيذ التي ترتبط بالمستودع النشط المختار من هذا القسم.
 * - تشغيلات البناء (CI/CD Workflows) • الإصدارات (تنزيل وتثبيت APK) • الفروع • سجل التعديلات • محرر الأكواد.
 * - تصميم Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech).
 */
@Composable
fun BuildCenterSection(
    context: Context,
    onNavigateTo: (AppState) -> Unit = {},
    onJumpAgentRoom: () -> Unit = {}
) {
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // إدارة خزانة المستودعات
    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    var savedRepos by remember(vaultTrigger) { mutableStateOf(RepoVaultManager.getRepositories(context)) }
    var activeRepoId by remember(vaultTrigger) { mutableStateOf(RepoVaultManager.getActiveRepoId(context)) }
    val activeRepoEntry = remember(savedRepos, activeRepoId) {
        savedRepos.find { it.id == activeRepoId } ?: savedRepos.firstOrNull()
    }

    var owner by remember(activeRepoEntry) {
        mutableStateOf(activeRepoEntry?.owner ?: (prefs.getString("build_center_owner", "aliwalead2007-ship-it") ?: "aliwalead2007-ship-it"))
    }
    var repo by remember(activeRepoEntry) {
        mutableStateOf(activeRepoEntry?.repo ?: (prefs.getString("build_center_repo", "QabasStudio-") ?: "QabasStudio-"))
    }
    var token by remember(activeRepoEntry) {
        mutableStateOf(if (!activeRepoEntry?.token.isNullOrBlank()) activeRepoEntry!!.token else (prefs.getString("build_center_token", "") ?: ""))
    }

    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اضغط «تحديث» لجلب بيانات المستودع") }
    // 0: الخزانة (Vault) • 1: البناء (CI/CD) • 2: الإصدارات • 3: الفروع • 4: السجل • 5: المحرر
    var tab by remember { mutableStateOf(0) }

    var repoInfo by remember { mutableStateOf<RepoInfo?>(null) }
    var runs by remember { mutableStateOf(listOf<BuildRun>()) }
    var releases by remember { mutableStateOf(listOf<ReleaseInfo>()) }
    var branches by remember { mutableStateOf(listOf<BranchInfo>()) }
    var commits by remember { mutableStateOf(listOf<CommitInfo>()) }
    var installingTag by remember { mutableStateOf<String?>(null) }
    var installingProgress by remember { mutableStateOf(0) }
    var lastRefresh by remember { mutableStateOf<String?>(null) }

    // حالات ربط مستودع مباشر برابط ومفتاح
    var directUrlInput by remember { mutableStateOf("") }
    var directTokenInput by remember { mutableStateOf("") }
    var directCustomNameInput by remember { mutableStateOf("") }
    var directIsPrivate by remember { mutableStateOf(true) }
    var isValidatingDirect by remember { mutableStateOf(false) }
    var directValidationMessage by remember { mutableStateOf<String?>(null) }
    var isDirectFormExpanded by remember { mutableStateOf(true) }

    // حالات استكشاف وتفويض GitHub
    var showExploreUserReposDialog by remember { mutableStateOf(false) }
    var userReposList by remember { mutableStateOf(listOf<UserRepoSummary>()) }
    var isLoadingUserRepos by remember { mutableStateOf(false) }
    var userReposFilter by remember { mutableStateOf("") }

    // حالات إنشاء مستودع جديد على GitHub
    var showCreateRepoForm by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoDescription by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(true) }
    var creatingRepo by remember { mutableStateOf(false) }

    fun saveSettings() {
        prefs.edit()
            .putString("build_center_owner", owner.trim())
            .putString("build_center_repo", repo.trim())
            .putString("build_center_token", token.trim())
            .apply()
    }

    suspend fun apiGet(path: String, auth: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url("https://api.github.com$path")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
            if (auth && token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            val response = client.newCall(builder.build()).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) { status = "خطأ GitHub: ${response.code}" }
                return@withContext null
            }
            response.body?.string()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { status = "تعذر الاتصال: ${e.message}" }
            null
        }
    }

    suspend fun apiPost(path: String, body: String?, expectCode: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val rb = (body ?: "").toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api.github.com$path")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer $token")
                .post(rb)
                .build()
            client.newCall(req).execute().use { it.code == expectCode }
        }.getOrDefault(false)
    }

    fun refreshAll() {
        if (owner.isBlank() || repo.isBlank()) {
            Toast.makeText(context, "أدخل المالك والمستودع أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        saveSettings()
        busy = true
        status = "جاري جلب بيانات المستودع..."
        scope.launch {
            val o = owner.trim()
            val r = repo.trim()
            val authed = token.isNotBlank()

            // معلومات المستودع
            apiGet("/repos/$o/$r", auth = authed)?.let { body ->
                val j = JSONObject(body)
                repoInfo = RepoInfo(
                    fullName = j.optString("full_name", "$o/$r"),
                    description = j.optString("description", ""),
                    isPrivate = j.optBoolean("private", false),
                    defaultBranch = j.optString("default_branch", "main"),
                    stars = j.optInt("stargazers_count", 0),
                    openIssues = j.optInt("open_issues_count", 0)
                )
            }
            // التشغيلات
            apiGet("/repos/$o/$r/actions/runs?per_page=8", auth = authed)?.let { body ->
                val arr = JSONObject(body).optJSONArray("workflow_runs")
                runs = buildList {
                    for (i in 0 until (arr?.length() ?: 0)) {
                        val run = arr!!.getJSONObject(i)
                        add(
                            BuildRun(
                                id = run.optLong("id", 0),
                                name = run.optString("display_title", run.optString("name", "بناء")),
                                status = run.optString("status", ""),
                                conclusion = run.optString("conclusion", ""),
                                branch = run.optString("head_branch", ""),
                                created = run.optString("created_at", "").take(16).replace("T", " ")
                            )
                        )
                    }
                }
            }
            // الإصدارات
            apiGet("/repos/$o/$r/releases?per_page=10", auth = authed)?.let { body ->
                val arr = JSONArray(body)
                releases = buildList {
                    for (i in 0 until arr.length()) {
                        val rel = arr.getJSONObject(i)
                        val assets = rel.optJSONArray("assets")
                        val apk = (0 until (assets?.length() ?: 0))
                            .map { assets!!.getJSONObject(it) }
                            .firstOrNull { it.optString("name", "").endsWith(".apk") }
                        add(
                            ReleaseInfo(
                                tag = rel.optString("tag_name", ""),
                                name = rel.optString("name", "").ifBlank { rel.optString("tag_name", "") },
                                notes = rel.optString("body", ""),
                                published = rel.optString("published_at", "").take(10),
                                apkUrl = apk?.optString("browser_download_url"),
                                apkAssetId = apk?.optLong("id", 0) ?: 0,
                                apkSize = apk?.optLong("size", 0) ?: 0,
                                isDraft = rel.optBoolean("draft", false),
                                isPrerelease = rel.optBoolean("prerelease", false)
                            )
                        )
                    }
                }
            }
            // الفروع
            apiGet("/repos/$o/$r/branches?per_page=20", auth = authed)?.let { body ->
                val arr = JSONArray(body)
                branches = buildList {
                    for (i in 0 until arr.length()) {
                        val b = arr.getJSONObject(i)
                        add(
                            BranchInfo(
                                name = b.optString("name", ""),
                                protected = b.optBoolean("protected", false),
                                sha = b.optJSONObject("commit")?.optString("sha", "")?.take(7) ?: ""
                            )
                        )
                    }
                }
            }
            // سجل الـ commits
            apiGet("/repos/$o/$r/commits?per_page=12&sha=main", auth = authed)?.let { body ->
                val arr = JSONArray(body)
                commits = buildList {
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        val inner = c.optJSONObject("commit")
                        add(
                            CommitInfo(
                                sha = c.optString("sha", "").take(7),
                                message = inner?.optString("message", "")?.lineSequence()?.firstOrNull() ?: "",
                                author = inner?.optJSONObject("author")?.optString("name", "") ?: "",
                                date = inner?.optJSONObject("author")?.optString("date", "")?.take(10) ?: ""
                            )
                        )
                    }
                }
            }
            status = "تم التحديث — $o/$r"
            lastRefresh = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            busy = false
        }
    }

    fun switchActiveRepo(id: String) {
        val target = RepoVaultManager.setActiveRepo(context, id)
        activeRepoId = id
        savedRepos = RepoVaultManager.getRepositories(context)
        if (target != null) {
            owner = target.owner
            repo = target.repo
            if (target.token.isNotBlank()) token = target.token
            Toast.makeText(context, "تم تفعيل «${target.fullName}» كمستودع نشط لغرفة الوكيل ومركز البناء 🚀", Toast.LENGTH_SHORT).show()
            refreshAll()
        }
    }

    fun openGitHubTokenUrl() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RepoVaultManager.GITHUB_TOKEN_GENERATION_URL))
            context.startActivity(intent)
            Toast.makeText(context, "جاري فتح GitHub لإنشاء رمز بالأذونات الجاهزة... اضغط Generate Token ثم انسخه 🔑", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح المتصفح: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadUserRepos() {
        val curToken = token.ifBlank { prefs.getString("build_center_token", "") ?: "" }
        if (curToken.isBlank()) {
            Toast.makeText(context, "أدخل رمز PAT أولاً لاستكشاف مستودعات حسابك", Toast.LENGTH_LONG).show()
            return
        }
        isLoadingUserRepos = true
        showExploreUserReposDialog = true
        scope.launch {
            val list = GitHubRepoClient.listUserRepos(curToken.trim())
            withContext(Dispatchers.Main) {
                isLoadingUserRepos = false
                userReposList = list
                if (list.isEmpty()) {
                    Toast.makeText(context, "لم يتم العثور على مستودعات أو الرمز غير صالح", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun connectDirectRepo() {
        val parsed = RepoVaultManager.parseRepoUrl(directUrlInput)
        if (parsed == null) {
            directValidationMessage = "يرجى إدخال رابط مستودع صالح مثل: https://github.com/owner/repo أو owner/repo"
            return
        }
        val (o, r) = parsed
        val effectiveToken = directTokenInput.trim().ifBlank { token.trim() }

        isValidatingDirect = true
        directValidationMessage = null
        scope.launch {
            val testClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            val reqBuilder = Request.Builder()
                .url("https://api.github.com/repos/$o/$r")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
            if (effectiveToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $effectiveToken")
            }
            try {
                val res = testClient.newCall(reqBuilder.build()).execute()
                withContext(Dispatchers.Main) {
                    isValidatingDirect = false
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val j = JSONObject(body)
                        val isPriv = j.optBoolean("private", directIsPrivate)
                        val branch = j.optString("default_branch", "main")
                        val desc = j.optString("description", "")
                        val entry = RepoVaultEntry(
                            owner = o,
                            repo = r,
                            token = effectiveToken,
                            customName = directCustomNameInput.trim().ifBlank { r },
                            isPrivate = isPriv,
                            defaultBranch = branch,
                            description = desc
                        )
                        RepoVaultManager.addOrUpdateRepo(context, entry, setAsActive = true)
                        savedRepos = RepoVaultManager.getRepositories(context)
                        activeRepoId = entry.id
                        owner = o
                        repo = r
                        token = effectiveToken
                        saveSettings()
                        directUrlInput = ""
                        directTokenInput = ""
                        directCustomNameInput = ""
                        isDirectFormExpanded = false
                        Toast.makeText(context, "تم التحقق وربط «$o/$r» بالخزانة وتفعيله لغرفة الوكيل 🎯", Toast.LENGTH_LONG).show()
                        refreshAll()
                    } else if (res.code == 404) {
                        directValidationMessage = "المستودع غير موجود أو خاص ويحتاج رمز PAT بصلاحية repo."
                    } else if (res.code == 401) {
                        directValidationMessage = "رمز PAT غير صالح أو منتهي الصلاحية."
                    } else {
                        directValidationMessage = "خطأ من GitHub: كود الاستجابة ${res.code}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isValidatingDirect = false
                    directValidationMessage = "فشل الاتصال: ${e.message}"
                }
            }
        }
    }

    fun triggerBuild() {
        if (token.isBlank()) {
            Toast.makeText(context, "أدخل رمز PAT بصلاحية actions:write أولاً", Toast.LENGTH_LONG).show()
            return
        }
        saveSettings()
        busy = true
        status = "جاري إرسال أمر البناء..."
        scope.launch {
            val ok = apiPost(
                "/repos/${owner.trim()}/${repo.trim()}/actions/workflows/android-ci.yml/dispatches",
                JSONObject().put("ref", "main").toString(), 204
            )
            status = if (ok) "أُرسل أمر البناء — حدّث بعد دقيقة لرؤية التشغيل" else "فشل إرسال الأمر (تحقق من الرمز والصلاحيات)"
            busy = false
            if (ok) {
                AuditLogger.log(context, "build_trigger", "تشغيل بناء APK من غرفة تحكم GitHub")
                AppRequestService.getActiveBuildRequest(context)?.let { req ->
                    val earlyPrefs = context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                    if (req.progress < 80) {
                        AppRequestService.updateRequestProgressAndPayment(context, req.id, progress = 80)
                    }
                    if (!earlyPrefs.getBoolean("silent_${req.id}", false)) {
                        AppRequestService.sendMessage(
                            context,
                            AppRequestService.ChatMessage(
                                requestId = req.id, senderEmail = "dev", isDeveloper = true,
                                message = "بدأ بناء نسختك الأولى 🏗️ — ستصلك فور جاهزيتها للاختبار."
                            )
                        )
                    }
                }
                kotlinx.coroutines.delay(15000)
                refreshAll()
            }
        }
    }

    fun rerunRun(id: Long) {
        if (token.isBlank()) {
            Toast.makeText(context, "إعادة التشغيل تحتاج رمز PAT", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        scope.launch {
            val ok = apiPost("/repos/${owner.trim()}/${repo.trim()}/actions/runs/$id/rerun", "", 201)
            Toast.makeText(context, if (ok) "أُعيد التشغيل" else "فشلت إعادة التشغيل", Toast.LENGTH_SHORT).show()
            if (ok) AuditLogger.log(context, "build_rerun", "إعادة تشغيل البناء رقم $id")
            busy = false
            if (ok) { kotlinx.coroutines.delay(8000); refreshAll() }
        }
    }

    fun cancelRun(id: Long) {
        if (token.isBlank()) {
            Toast.makeText(context, "الإلغاء يحتاج رمز PAT", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        scope.launch {
            val ok = apiPost("/repos/${owner.trim()}/${repo.trim()}/actions/runs/$id/cancel", "", 202)
            Toast.makeText(context, if (ok) "أُلغي التشغيل" else "فشل الإلغاء", Toast.LENGTH_SHORT).show()
            if (ok) AuditLogger.log(context, "build_cancel", "إلغاء البناء رقم $id")
            busy = false
            if (ok) refreshAll()
        }
    }

    fun installRelease(rel: ReleaseInfo) {
        if (rel.apkUrl == null) {
            Toast.makeText(context, "لا يوجد APK في هذا الإصدار", Toast.LENGTH_SHORT).show()
            return
        }
        installingTag = rel.tag
        val hasToken = token.isNotBlank()
        val downloadUrl = if (hasToken && rel.apkAssetId > 0) {
            "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/releases/assets/${rel.apkAssetId}"
        } else {
            rel.apkUrl!!
        }
        val info = UpdateManager.UpdateInfo(
            versionName = rel.tag.removePrefix("v"),
            versionCode = parseTagCode(rel.tag),
            deltaUrl = null,
            apkUrl = downloadUrl,
            releaseNotes = rel.notes,
            deltaSize = 0,
            apkSize = rel.apkSize
        )
        UpdateManager.downloadAndInstall(
            context, info,
            onProgress = { installingProgress = it.percent },
            onDone = { ok, msg ->
                installingTag = null
                installingProgress = 0
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (ok) AuditLogger.log(context, "release_install", "تثبيت ${rel.tag} من غرفة GitHub")
            },
            authToken = token.takeIf { hasToken }
        )
    }

    // واجهة المستخدم الرئيسية بتصميم Spatial HUD
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ══════════════════════════════════════════════════════════════
        // 🌌 لوحة بطل الخزانة (SPATIAL REPO VAULT // ACTIVE TARGET)
        // ══════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF070D1B))
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF10B981).copy(alpha = 0.85f),
                                Color(0xFF00F0FF).copy(alpha = 0.6f),
                                Color(0xFF1E2D4A)
                            )
                        )
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // شريط الترويسة الفضائي وزر غرفة الوكيل
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            "SPATIAL REPO VAULT // ACTIVE TARGET",
                            color = Color(0xFF00F0FF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Button(
                        onClick = onJumpAgentRoom,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1E38)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("غرفة الوكيل (التنفيذ) 🤖", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // معلومات المستودع النشط
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Icon(
                            Icons.Default.Source,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.padding(12.dp).size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            repoInfo?.fullName ?: "${owner.trim()}/${repo.trim()}",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            activeRepoEntry?.displayName?.takeIf { it != repo }?.let { "«$it» • " }.orEmpty() +
                                (repoInfo?.description?.take(70) ?: status),
                            color = Color(0xFF94A3B8),
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // شارات القياس وحالة المستودع
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BuildPill(
                        text = when {
                            repoInfo == null -> "غير متصل"
                            repoInfo!!.isPrivate -> "خاص 🔒"
                            else -> "عام 🌍"
                        },
                        tint = if (repoInfo?.isPrivate == true) Color(0xFFF59E0B) else Color(0xFF10B981)
                    )
                    BuildPill(
                        text = "الفرع: ${repoInfo?.defaultBranch ?: "main"}",
                        tint = Color(0xFF38BDF8)
                    )
                    BuildPill(
                        text = if (token.isNotBlank()) "الرمز محفوظ 🔑" else "بدون رمز PAT",
                        tint = if (token.isNotBlank()) Color(0xFF8B5CF6) else Color(0xFFEF4444)
                    )
                    if (lastRefresh != null) {
                        BuildPill(text = "تحديث: $lastRefresh", tint = Color(0xFF64748B))
                    }
                }

                // إحصاءات سريعة هندسية
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D1527))
                        .border(BorderStroke(1.dp, Color(0xFF1E2D4A)), RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BuildStat(value = "${savedRepos.size}", label = "مستودعات بالخزانة")
                    BuildStat(value = "${repoInfo?.stars ?: 0}", label = "نجوم")
                    BuildStat(value = "${releases.size}", label = "إصدارات")
                    BuildStat(value = "${runs.count { it.conclusion == "success" }}", label = "بناء ناجح")
                }

                // أزرار العمليات السريعة للهيدر (48dp Touch Target)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = ::refreshAll,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("تحديث", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { tab = 0 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1.1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("الخزانة (${savedRepos.size})", color = Color(0xFF00F0FF), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }

                    Button(
                        onClick = ::triggerBuild,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D4A)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("بناء ⚡", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
                    }
                }

                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 🌉 الجسر: طلب العميل النشط قيد البناء (إن وجد)
        // ══════════════════════════════════════════════════════════════
        val activeReq = remember { mutableStateOf(AppRequestService.getActiveBuildRequest(context)) }
        var scaffolding by remember { mutableStateOf(false) }
        var scaffoldMsg by remember { mutableStateOf<String?>(null) }
        activeReq.value?.let { req ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF121B2F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🛠️ قيد البناء الآن للطلب",
                                color = Color(0xFFF59E0B),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "${req.title} — ${req.userEmail}",
                                color = Color.White,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { tab = 5 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("المحرر ✏️", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                AppRequestService.clearActiveBuildRequest(context)
                                activeReq.value = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("إنهاء", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }

                    // أزرار خطة الطلب والتوليد والتسليم
                    val reqPrefs = context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                    var prNumber by remember(req.id) { mutableStateOf(reqPrefs.getInt("pr_${req.id}", -1)) }
                    Button(
                        onClick = {
                            val orKey = KeyVault.openrouter
                            if (orKey.isBlank()) {
                                Toast.makeText(context, "أدخل مفتاح OpenRouter أولاً", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (token.isBlank()) {
                                Toast.makeText(context, "أدخل رمز PAT للحفظ في المستودع", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            scaffolding = true
                            scaffoldMsg = null
                            scope.launch {
                                val live = AppRequestService.getActiveBuildRequest(context) ?: req
                                val plan = live.generatedPrompts?.takeIf { it.isNotBlank() } ?: LocalPromptPlanner.generatePlan(live)
                                val files = OpenRouterService.generateStarterFiles(live, plan, orKey)
                                var created = 0
                                var pr: Int? = null
                                if (files.isNotEmpty()) {
                                    val rc = GitHubRepoClient(owner.trim(), repo.trim(), token.trim())
                                    val baseBranch = rc.getDefaultBranch()
                                    val branch = "dev-" + GitHubRepoClient.sanitizeRepoName(live.title).take(30)
                                    val base = rc.getBranchSha(baseBranch)
                                    val branchReady = base != null && (rc.createBranch(branch, base) || rc.getBranchSha(branch) != null)
                                    if (branchReady) {
                                        for ((path, code) in files) {
                                            if (path == ".github/workflows/android-ci.yml") continue
                                            if (rc.createFile(path, branch, "ملفات البداية: $path", code)) created++
                                        }
                                        for ((path, code) in ClientRepoTemplates.all()) {
                                            if (rc.createFile(path, branch, "قوالب GitHub: $path", code)) created++
                                        }
                                        if (created >= 5) {
                                            pr = rc.createPullRequest(branch, "هيكل البداية: ${live.title}", "توليد تلقائي للطلب. راجع ثم ادمج.", baseBranch)
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    scaffolding = false
                                    if (created > 0) {
                                        if (pr != null && pr > 0) {
                                            reqPrefs.edit().putInt("pr_${live.id}", pr).apply()
                                            prNumber = pr
                                        }
                                        AppRequestService.updateRequestProgressAndPayment(context, live.id, progress = 30)
                                        scaffoldMsg = "وُلّدت $created ملفات بنجاح ✅"
                                    } else {
                                        scaffoldMsg = "تعذر التوليد — تحقق من المفاتيح والصلاحيات"
                                    }
                                    Toast.makeText(context, scaffoldMsg, Toast.LENGTH_LONG).show()
                                    refreshAll()
                                }
                            }
                        },
                        enabled = !scaffolding && !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (scaffolding) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OpenRouter يكتب الملفات…", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Text("🚀 توليد هيكل قابل للبناء بالذكاء الاصطناعي", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    scaffoldMsg?.let { Text(it, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 11.sp) }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 🗂️ شريط التبويبات الفضائي (HUD Spatial Tabs)
        // ══════════════════════════════════════════════════════════════
        val tabTitles = listOf("خزانة المستودعات", "تشغيلات البناء", "الإصدارات", "الفروع", "السجل", "المحرر")
        val tabIcons = listOf(
            Icons.Default.FolderSpecial,
            Icons.Default.PlayArrow,
            Icons.Default.Download,
            Icons.Default.AccountTree,
            Icons.Default.History,
            Icons.Default.Code
        )

        Surface(
            color = Color(0xFF0B1220),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.2.dp, Color(0xFF1E2D4A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF00F0FF),
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    if (tab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[tab]),
                            color = Color(0xFF00F0FF),
                            height = 3.dp
                        )
                    }
                },
                divider = {}
            ) {
                tabTitles.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    tabIcons[i],
                                    contentDescription = null,
                                    tint = if (tab == i) Color(0xFF00F0FF) else Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    title,
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium,
                                    color = if (tab == i) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 🔄 محتوى التبويبات بحسب الاختيار
        // ══════════════════════════════════════════════════════════════
        when (tab) {
            // ──────────────────────────────────────────────────────────
            // 🗄️ تبويب 0: خزانة المستودعات (Repository Vault) — جوهر الطلب
            // ──────────────────────────────────────────────────────────
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 1) بطاقة ربط مستودع مباشر برابط ومفتاح (Direct Link & Key)
                    Surface(
                        color = Color(0xFF0B1324),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "ربط مستودع برابط ومفتاح (Direct Link & Key) 🔗",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                TextButton(
                                    onClick = { isDirectFormExpanded = !isDirectFormExpanded },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (isDirectFormExpanded) "إغلاق ▲" else "فتح النموذج ▼",
                                        color = Color(0xFF00F0FF),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                "اربط أي مستودع GitHub مباشرة عبر إدخال رابطه ومفتاح الوصول (PAT). سيتم فحصه فوراً وحفظه بالخزانة.",
                                color = Color(0xFF94A3B8),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )

                            if (isDirectFormExpanded) {
                                OutlinedTextField(
                                    value = directUrlInput,
                                    onValueChange = { directUrlInput = it; directValidationMessage = null },
                                    label = { Text("رابط المستودع (URL أو owner/repo)", fontSize = 11.sp) },
                                    placeholder = { Text("مثال: https://github.com/myuser/my-repo", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = directTokenInput,
                                    onValueChange = { directTokenInput = it },
                                    label = { Text("مفتاح الوصول / رمز PAT الخاص بهذا المستودع", fontSize = 11.sp) },
                                    placeholder = { Text("ghp_... (اتركه فارغاً لاستخدام الرمز العام المحفوظ)", fontSize = 10.sp) },
                                    singleLine = true,
                                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showToken = !showToken }) {
                                            Icon(
                                                if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = directCustomNameInput,
                                    onValueChange = { directCustomNameInput = it },
                                    label = { Text("اسم مخصص للتمييز في الخزانة (اختياري)", fontSize = 11.sp) },
                                    placeholder = { Text("مثال: تطبيق المتجر الرئيسي", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = directIsPrivate,
                                        onCheckedChange = { directIsPrivate = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF10B981),
                                            checkedTrackColor = Color(0xFF0F3A2A)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (directIsPrivate) "مستودع خاص 🔒" else "مستودع عام 🌍",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp
                                    )
                                }

                                directValidationMessage?.let { msg ->
                                    Surface(
                                        color = Color(0xFF381515),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            msg,
                                            color = Color(0xFFFCA5A5),
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = ::connectDirectRepo,
                                    enabled = !isValidatingDirect,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    if (isValidatingDirect) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري التحقق والربط من GitHub...", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("فحص الاتصال وحفظ بالخزانة ⚡", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 2) بطاقة تفويض GitHub بموافقة المستخدم (OAuth / Token Generator & Account Explorer)
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "رابط تفويض GitHub بموافقة المستخدم 🌐",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Text(
                                "افتح رابط التفويض في GitHub بموافقتك للحصول على الرمز الجاهز بالأذونات الدقيقة المطلوبة (repo للكود، workflow للبناء، read:user للمصادقة).",
                                color = Color(0xFFCBD5E1),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = ::openGitHubTokenUrl,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("فتح رابط التفويض ↗️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = ::loadUserRepos,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("استكشاف مستودعاتي 🔍", color = Color(0xFFC084FC), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 3) بطاقة إنشاء مستودع جديد على GitHub (New Repo Provisioning)
                    Surface(
                        color = Color(0xFF0F1A2C),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddBox, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "إنشاء مستودع جديد على GitHub 📦",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                TextButton(
                                    onClick = { showCreateRepoForm = !showCreateRepoForm },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (showCreateRepoForm) "إخفاء ▲" else "إنشاء جديد ▼",
                                        color = Color(0xFF38BDF8),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (showCreateRepoForm) {
                                OutlinedTextField(
                                    value = newRepoName,
                                    onValueChange = { newRepoName = it },
                                    label = { Text("اسم المستودع بالإنجليزية", fontSize = 11.sp) },
                                    placeholder = { Text("مثال: my-android-project", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = newRepoDescription,
                                    onValueChange = { newRepoDescription = it },
                                    label = { Text("الوصف (اختياري)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = newRepoPrivate,
                                        onCheckedChange = { newRepoPrivate = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF38BDF8),
                                            checkedTrackColor = Color(0xFF0C4A6E)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (newRepoPrivate) "خاص 🔒" else "عام 🌍",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (token.isBlank()) {
                                            Toast.makeText(context, "أدخل رمز PAT أولاً (يحتاج صلاحية repo)", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                        val clean = GitHubRepoClient.sanitizeRepoName(newRepoName)
                                        if (clean.isBlank()) {
                                            Toast.makeText(context, "أدخل اسماً صالحاً للمستودع", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        creatingRepo = true
                                        scope.launch {
                                            val fullName = GitHubRepoClient.createUserRepo(
                                                token.trim(), clean, newRepoDescription, newRepoPrivate
                                            )
                                            withContext(Dispatchers.Main) {
                                                creatingRepo = false
                                                if (fullName != null) {
                                                    val parts = fullName.split("/")
                                                    val newEntry = RepoVaultEntry(
                                                        owner = parts[0],
                                                        repo = parts.getOrNull(1) ?: clean,
                                                        token = token.trim(),
                                                        customName = clean,
                                                        isPrivate = newRepoPrivate,
                                                        description = newRepoDescription
                                                    )
                                                    RepoVaultManager.addOrUpdateRepo(context, newEntry, setAsActive = true)
                                                    savedRepos = RepoVaultManager.getRepositories(context)
                                                    activeRepoId = newEntry.id
                                                    owner = parts[0]
                                                    repo = parts.getOrNull(1) ?: clean
                                                    saveSettings()
                                                    newRepoName = ""
                                                    newRepoDescription = ""
                                                    showCreateRepoForm = false
                                                    Toast.makeText(context, "أُنشئ المستودع بنجاح ✅ $fullName وأصبح النشط للتنفيذ", Toast.LENGTH_LONG).show()
                                                    refreshAll()
                                                } else {
                                                    Toast.makeText(context, "تعذر الإنشاء — تحقق من الرمز والاسم", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !creatingRepo && !busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    if (creatingRepo) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري إنشاء المستودع في GitHub…", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    } else {
                                        Text("إنشاء المستودع وتفعيله 📦", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 4) قائمة مستودعات الخزانة المحفوظة (Saved Repositories in Vault)
                    Text(
                        "مستودعات الخزانة المحفوظة (${savedRepos.size}) 🗄️",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    savedRepos.forEach { repoEntry ->
                        val isActive = repoEntry.id == activeRepoId || repoEntry.fullName.equals("$owner/$repo", ignoreCase = true)
                        Surface(
                            color = if (isActive) Color(0xFF091626) else Color(0xFF0B111F),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                if (isActive) 1.5.dp else 1.dp,
                                if (isActive) Color(0xFF10B981) else Color(0xFF1E2D4A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = (if (isActive) Color(0xFF10B981) else Color(0xFF38BDF8)).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                if (isActive) Icons.Default.CheckCircle else Icons.Default.Source,
                                                contentDescription = null,
                                                tint = if (isActive) Color(0xFF10B981) else Color(0xFF38BDF8),
                                                modifier = Modifier.padding(8.dp).size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                repoEntry.displayName,
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                repoEntry.fullName,
                                                color = Color(0xFF94A3B8),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isActive) {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                "النشط للتنفيذ 🎯",
                                                color = Color(0xFF10B981),
                                                fontFamily = CairoFont,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                if (repoEntry.description.isNotBlank()) {
                                    Text(
                                        repoEntry.description,
                                        color = Color(0xFFCBD5E1),
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BuildPill(text = if (repoEntry.isPrivate) "خاص 🔒" else "عام 🌍", tint = if (repoEntry.isPrivate) Color(0xFFF59E0B) else Color(0xFF10B981))
                                    BuildPill(text = "الفرع: ${repoEntry.defaultBranch}", tint = Color(0xFF38BDF8))
                                    if (repoEntry.token.isNotBlank()) {
                                        BuildPill(text = "رمز خاص 🔑", tint = Color(0xFF8B5CF6))
                                    }
                                }

                                // أزرار الإجراءات على هذا المستودع
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isActive) {
                                        Button(
                                            onClick = { switchActiveRepo(repoEntry.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("تفعيل للتنفيذ 🎯", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = onJumpAgentRoom,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F2538)),
                                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("غرفة الوكيل 🤖", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            switchActiveRepo(repoEntry.id)
                                            tab = 1
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Text("العمليات 📊", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                                    }

                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoEntry.githubUrl))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "فتح في GitHub", tint = Color(0xFF38BDF8))
                                    }

                                    if (savedRepos.size > 1) {
                                        IconButton(
                                            onClick = {
                                                savedRepos = RepoVaultManager.deleteRepo(context, repoEntry.id)
                                                if (activeRepoId == repoEntry.id) {
                                                    savedRepos.firstOrNull()?.let { switchActiveRepo(it.id) }
                                                }
                                                Toast.makeText(context, "تم حذف المستودع من الخزانة", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────
            // 🏗️ تبويب 1: تشغيلات البناء (CI/CD Workflows)
            // ──────────────────────────────────────────────────────────
            1 -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "تشغيلات البناء لمستودع: $owner/$repo",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = ::triggerBuild,
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("تشغيل البناء ⚡", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    if (runs.isEmpty()) {
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "لا توجد تشغيلات بناء مسجلة لهذا المستودع بعد.\nاضغط «تشغيل البناء ⚡» أو «تحديث 🔄» لجلب البيانات.",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }

                    runs.forEach { run ->
                        val tint = when {
                            run.conclusion == "success" -> Color(0xFF10B981)
                            run.conclusion == "failure" -> Color(0xFFEF4444)
                            run.status == "in_progress" || run.status == "queued" -> Color(0xFFF59E0B)
                            else -> Color(0xFF94A3B8)
                        }
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(run.name, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                                        Text("${run.branch} • ${run.created}", color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 11.sp)
                                    }
                                    BuildPill(
                                        text = when {
                                            run.conclusion == "success" -> "ناجح ✅"
                                            run.conclusion == "failure" -> "فاشل ❌"
                                            run.status == "in_progress" -> "يعمل... ⏳"
                                            run.status == "queued" -> "بالانتظار ⏸️"
                                            else -> run.status
                                        },
                                        tint = tint
                                    )
                                }
                                if (run.status == "in_progress" || run.status == "queued" || run.conclusion == "failure") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (run.conclusion == "failure") {
                                            Button(
                                                onClick = { rerunRun(run.id) },
                                                enabled = !busy,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("إعادة التشغيل", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontSize = 11.sp)
                                            }
                                        }
                                        if (run.status == "in_progress" || run.status == "queued") {
                                            Button(
                                                onClick = { cancelRun(run.id) },
                                                enabled = !busy,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1515)),
                                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("إلغاء", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────
            // 🚀 تبويب 2: الإصدارات وتنزيل وتثبيت APK
            // ──────────────────────────────────────────────────────────
            2 -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (releases.isEmpty()) {
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "لا توجد إصدارات APK بعد لهذا المستودع.\nعند اكتمال بناء Android CI سيظهر الإصدار هنا تلقائياً.",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }

                    releases.forEachIndexed { index, rel ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (index == 0) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF38BDF8).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(rel.name, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        if (index == 0 && !rel.isDraft) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            BuildPill(text = "الأحدث 🌟", tint = Color(0xFF10B981))
                                        }
                                    }
                                    if (rel.isDraft) BuildPill(text = "مسودة", tint = Color(0xFFF59E0B))
                                    else if (rel.isPrerelease) BuildPill(text = "تجريبي", tint = Color(0xFF38BDF8))
                                }

                                Text(
                                    "${rel.tag} • ${rel.published}" + if (rel.apkSize > 0) " • ${UpdateManager.formatSize(rel.apkSize)}" else "",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )

                                if (rel.notes.isNotBlank()) {
                                    Text(rel.notes.take(200), color = Color(0xFFCBD5E1), fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 3)
                                }

                                if (rel.apkUrl != null) {
                                    if (installingTag == rel.tag) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            LinearProgressIndicator(
                                                progress = { (installingProgress / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                                color = Color(0xFF10B981),
                                                trackColor = Color(0xFF1E293B)
                                            )
                                            Text("جاري التنزيل... $installingProgress٪", color = Color(0xFF10B981), fontFamily = NotoSansFont, fontSize = 11.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = { installRelease(rel) },
                                            enabled = installingTag == null,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تنزيل وتثبيت APK (${rel.tag}) 📲", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────
            // 🌿 تبويب 3: الفروع (Branches)
            // ──────────────────────────────────────────────────────────
            3 -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (branches.isEmpty()) {
                        Text("لا فروع بعد — اضغط «تحديث».", color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    branches.forEach { branch ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(branch.name, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Text(branch.sha, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                }
                                if (branch.protected) BuildPill(text = "محمي 🔒", tint = Color(0xFFF59E0B))
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────
            // 📜 تبويب 4: سجل التعديلات (Commits)
            // ──────────────────────────────────────────────────────────
            4 -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (commits.isEmpty()) {
                        Text("لا سجل بعد — اضغط «تحديث».", color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    commits.forEachIndexed { index, commit ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (index == 0) Color(0xFF10B981) else Color(0xFF38BDF8), CircleShape)
                                )
                                if (index < commits.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(30.dp)
                                            .background(Color(0xFF1E293B))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(commit.message, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${commit.sha} • ${commit.author} • ${commit.date}",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────
            // 💻 تبويب 5: المحرر واستكشاف الأكواد (Code Explorer)
            // ──────────────────────────────────────────────────────────
            5 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(580.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                ) {
                    AiEditorSection(
                        context = context,
                        owner = owner.trim(),
                        repo = repo.trim(),
                        token = token.trim(),
                        onFileCommitted = { refreshAll() },
                        onRequestBuild = { tab = 1; triggerBuild() },
                        onNavigateTo = onNavigateTo
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 🔍 نافذة حوار استكشاف مستودعات حساب GitHub واستيرادها بنقرة
    // ══════════════════════════════════════════════════════════════
    if (showExploreUserReposDialog) {
        AlertDialog(
            onDismissRequest = { showExploreUserReposDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF00F0FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استكشاف مستودعات حسابك في GitHub 🔍", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = userReposFilter,
                        onValueChange = { userReposFilter = it },
                        placeholder = { Text("بحث في المستودعات...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isLoadingUserRepos) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF00F0FF))
                        }
                    } else {
                        val filtered = userReposList.filter {
                            userReposFilter.isBlank() || it.fullName.contains(userReposFilter, ignoreCase = true) || it.name.contains(userReposFilter, ignoreCase = true)
                        }

                        if (filtered.isEmpty()) {
                            Text("لا توجد مستودعات مطابقة.", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered) { uRepo ->
                                    val isAlreadyInVault = savedRepos.any { it.fullName.equals(uRepo.fullName, ignoreCase = true) }
                                    Surface(
                                        color = Color(0xFF0A101D),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isAlreadyInVault) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF1E2D4A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(uRepo.name, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(uRepo.fullName, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                                if (uRepo.description.isNotBlank()) {
                                                    Text(uRepo.description, color = Color(0xFF64748B), fontFamily = NotoSansFont, fontSize = 9.5.sp, maxLines = 1)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val entry = RepoVaultEntry(
                                                        owner = uRepo.owner,
                                                        repo = uRepo.name,
                                                        token = token.trim(),
                                                        customName = uRepo.name,
                                                        isPrivate = uRepo.isPrivate,
                                                        defaultBranch = uRepo.defaultBranch,
                                                        description = uRepo.description
                                                    )
                                                    RepoVaultManager.addOrUpdateRepo(context, entry, setAsActive = true)
                                                    savedRepos = RepoVaultManager.getRepositories(context)
                                                    activeRepoId = entry.id
                                                    owner = uRepo.owner
                                                    repo = uRepo.name
                                                    saveSettings()
                                                    Toast.makeText(context, "تمت إضافة «${uRepo.fullName}» للخزانة وتفعيله 🎯", Toast.LENGTH_SHORT).show()
                                                    showExploreUserReposDialog = false
                                                    refreshAll()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = if (isAlreadyInVault) Color(0xFF0F2C20) else Color(0xFF1E2D4A)),
                                                border = BorderStroke(1.dp, if (isAlreadyInVault) Color(0xFF10B981) else Color(0xFF00F0FF)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(
                                                    if (isAlreadyInVault) "تفعيل 🎯" else "استيراد ➕",
                                                    color = if (isAlreadyInVault) Color(0xFF10B981) else Color(0xFF00F0FF),
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExploreUserReposDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                ) {
                    Text("إغلاق", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun BuildPill(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            color = tint,
            fontFamily = CairoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BuildStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 10.sp)
    }
}

private fun parseTagCode(tag: String): Int {
    val buildPart = tag.substringAfter("+", "")
    if (buildPart.isNotEmpty()) {
        return buildPart.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
    val parts = tag.removePrefix("v").trim().split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}

private data class RepoInfo(
    val fullName: String,
    val description: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val stars: Int,
    val openIssues: Int
)

private data class BuildRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String,
    val branch: String,
    val created: String
)

private data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val published: String,
    val apkUrl: String?,
    val apkAssetId: Long,
    val apkSize: Long,
    val isDraft: Boolean,
    val isPrerelease: Boolean
)

private data class BranchInfo(
    val name: String,
    val protected: Boolean,
    val sha: String
)

private data class CommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String
)
