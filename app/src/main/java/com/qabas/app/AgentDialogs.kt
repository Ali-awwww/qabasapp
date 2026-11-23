package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * حوارات الوكيل (Agent Dialogs): المفاتيح + المستودع + المنصات — مفصولة عن الشاشة.
 */
/**
 * حوار إدارة وتبديل مستودعات GitHub والتحكم بالصلاحيات
 */
@Composable
fun RepoSetupDialog(
    context: Context,
    activeRepo: RepoVaultEntry?,
    onDismiss: () -> Unit,
    onRepoChanged: (RepoVaultEntry) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var repos by remember {
        mutableStateOf(RepoVaultManager.getRepositories(context))
    }
    var currentActiveId by remember {
        mutableStateOf(RepoVaultManager.getActiveRepoId(context) ?: activeRepo?.id ?: "")
    }
    val currentActiveRepo = remember(repos, currentActiveId) {
        repos.find { it.id == currentActiveId } ?: activeRepo ?: repos.firstOrNull()
    }

    var showAddSection by remember { mutableStateOf(false) }
    var inputRepoUrl by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf("") }
    var inputBranch by remember { mutableStateOf("main") }
    var showTokenText by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun testConnection(entry: RepoVaultEntry) {
        if (entry.owner.isBlank() || entry.repo.isBlank()) {
            testSuccess = false
            testResult = "بيانات المستودع غير مكتملة."
            return
        }
        isTestingConnection = true
        testSuccess = null
        testResult = "جاري فحص الاتصال وقراءة بيانات المستودع..."
        scope.launch(Dispatchers.IO) {
            val client = GitHubRepoClient(entry.owner, entry.repo, entry.token)
            val branches = client.getBranches()
            val authUser = client.getAuthUser()
            withContext(Dispatchers.Main) {
                isTestingConnection = false
                if (branches != null) {
                    testSuccess = true
                    testResult = "الاتصال ناجح! (${branches.size} فروع)${if (!authUser.isNullOrBlank()) " | الحساب: $authUser" else ""}"
                } else {
                    testSuccess = false
                    testResult = if (entry.token.isBlank()) {
                        "المستودع خاص أو غير متاح. يرجى توفير رمز GitHub PAT."
                    } else {
                        "فشل الاتصال: يرجى التحقق من اسم المستودع وصلاحيات الرمز (PAT)."
                    }
                }
            }
        }
    }

    fun handleSwitchRepo(entry: RepoVaultEntry) {
        val target = RepoVaultManager.setActiveRepo(context, entry.id)
        currentActiveId = entry.id
        testResult = null
        testSuccess = null
        target?.let { onRepoChanged(it) }
        Toast.makeText(context, "تم تفعيل المستودع: ${entry.displayName} ⚡", Toast.LENGTH_SHORT).show()
    }

    fun handleAddRepo() {
        val parsed = RepoVaultManager.parseRepoUrl(inputRepoUrl)
        if (parsed == null) {
            Toast.makeText(context, "يرجى كتابة رابط المستودع أو صيغة: owner/repo", Toast.LENGTH_LONG).show()
            return
        }
        val newEntry = RepoVaultEntry(
            id = UUID.randomUUID().toString(),
            owner = parsed.first,
            repo = parsed.second,
            token = inputToken.trim(),
            defaultBranch = inputBranch.trim().ifBlank { "main" },
            customName = parsed.second
        )
        val updated = RepoVaultManager.addOrUpdateRepo(context, newEntry, setAsActive = true)
        repos = updated
        currentActiveId = newEntry.id
        onRepoChanged(newEntry)
        inputRepoUrl = ""
        inputToken = ""
        showAddSection = false
        Toast.makeText(context, "تمت إضافة المستودع وتفعيله 🚀", Toast.LENGTH_SHORT).show()
        testConnection(newEntry)
    }

    fun handleDeleteRepo(id: String) {
        val updated = RepoVaultManager.deleteRepo(context, id)
        repos = updated
        currentActiveId = RepoVaultManager.getActiveRepoId(context) ?: ""
        testResult = null
        testSuccess = null
        Toast.makeText(context, "تم حذف المستودع من القائمة 🗑️", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            backgroundColor = QabasTheme.current.background,
            borderColor = QabasTheme.current.success.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(QabasTheme.current.success.copy(alpha = 0.15f))
                                .border(1.dp, QabasTheme.current.success.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🐙", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "مستودعات GitHub المتصلة",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = QabasTheme.current.textPrimary,
                                fontFamily = CairoFont
                            )
                            Text(
                                text = "التبديل بين المشاريع وطلب صلاحيات البناء",
                                fontSize = 10.sp,
                                color = QabasTheme.current.textSecondary,
                                fontFamily = CairoFont
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = QabasTheme.current.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: One-Click Smart GitHub Scopes Request
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        QabasTheme.current.success.copy(alpha = 0.15f),
                                        QabasTheme.current.success.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .border(1.dp, QabasTheme.current.success.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("⚡", fontSize = 14.sp)
                                Text(
                                    text = "توليد مفتاح الصلاحيات بنقرة واحدة (GitHub PAT)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QabasTheme.current.success,
                                    fontFamily = CairoFont
                                )
                            }
                            Text(
                                text = "يفتح صفحة GitHub مع الصلاحيات المطلوبة للوكيل والبناء (repo, workflow, write:packages, read:user) محددة مسبقاً وتلقائياً دون الحاجة لتحديدها يدوياً.",
                                fontSize = 10.sp,
                                color = QabasTheme.current.textPrimary,
                                lineHeight = 14.sp,
                                fontFamily = CairoFont
                            )
                            Button(
                                onClick = {
                                    val tokenUrl = "https://github.com/settings/tokens/new?description=Qabas+Studio+DevOps+Agent&scopes=repo,workflow,write:packages,read:user"
                                    try {
                                        uriHandler.openUri(tokenUrl)
                                        Toast.makeText(context, "جاري فتح GitHub لإنشاء الرمز بالصلاحيات 🐙", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = QabasTheme.current.success,
                                    contentColor = QabasTheme.current.background
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "فتح وتوليد الصلاحيات على GitHub ↗️",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Active Repo Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(QabasTheme.current.cardSurface)
                            .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // شارة الحالة تقرأ المصدر الوحيد نفسه (الخزانة):
                                // بلا مستودع أو بلا PAT لا تُعرض «نشط».
                                val repoBadgeColor = when {
                                    currentActiveRepo == null -> QabasTheme.current.textSecondary
                                    currentActiveRepo.token.isBlank() -> QabasTheme.current.warning
                                    else -> QabasTheme.current.success
                                }
                                Text(
                                    text = "المستودع النشط حالياً",
                                    fontSize = 10.sp,
                                    color = QabasTheme.current.textSecondary,
                                    fontFamily = CairoFont
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(repoBadgeColor.copy(alpha = 0.15f))
                                        .border(1.dp, repoBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when {
                                            currentActiveRepo == null -> "غير مربوط"
                                            currentActiveRepo.token.isBlank() -> "بلا مفتاح ⚠️"
                                            else -> "نشط ⚡"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = repoBadgeColor,
                                        fontFamily = CairoFont
                                    )
                                }
                            }

                            Text(
                                // بلا مستودع في الخزانة لا يُعرض مستودع افتراضي:
                                // المصدر الوحيد هو RepoVaultManager.getActiveRepo.
                                text = currentActiveRepo?.fullName ?: "لا يوجد مستودع نشط",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = QabasTheme.current.primary,
                                fontFamily = FontFamily.Monospace
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "الفرع: ${currentActiveRepo?.defaultBranch ?: "—"}",
                                    fontSize = 10.sp,
                                    color = QabasTheme.current.textSecondary,
                                    fontFamily = CairoFont
                                )
                                Text(
                                    text = when {
                                        currentActiveRepo == null -> "أضف مستودعاً من الأسفل"
                                        !currentActiveRepo.token.isNullOrBlank() -> "🔒 مفتاح PAT متصل"
                                        else -> "⚠️ بدون مفتاح PAT"
                                    },
                                    fontSize = 10.sp,
                                    color = when {
                                        currentActiveRepo == null -> QabasTheme.current.textSecondary
                                        !currentActiveRepo.token.isNullOrBlank() -> QabasTheme.current.success
                                        else -> QabasTheme.current.warning
                                    },
                                    fontFamily = CairoFont
                                )
                            }

                            // Connection Test Button & Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        currentActiveRepo?.let { testConnection(it) }
                                    },
                                    // بلا مستودع نشط لا معنى لاختبار الاتصال.
                                    enabled = currentActiveRepo != null && !isTestingConnection,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = QabasTheme.current.separator,
                                        contentColor = QabasTheme.current.primary
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = QabasTheme.current.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (isTestingConnection) "جاري الفحص..." else "اختبار الاتصال ⚡",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }

                                if (testResult != null) {
                                    Text(
                                        text = testResult ?: "",
                                        fontSize = 9.sp,
                                        color = when (testSuccess) {
                                            true -> QabasTheme.current.success
                                            false -> QabasTheme.current.destructive
                                            else -> QabasTheme.current.textSecondary
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Saved Repositories List
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المستودعات المحفوظة للتبديل السريع (${repos.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = QabasTheme.current.textPrimary,
                            fontFamily = CairoFont
                        )
                        TextButton(
                            onClick = { showAddSection = !showAddSection },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (showAddSection) Icons.Default.ExpandLess else Icons.Default.Add,
                                contentDescription = null,
                                tint = QabasTheme.current.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showAddSection) "إلغاء" else "إضافة مستودع",
                                fontSize = 10.sp,
                                color = QabasTheme.current.primary,
                                fontFamily = CairoFont
                            )
                        }
                    }

                    // Add Repo Form (Collapsible)
                    AnimatedVisibility(visible = showAddSection) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(QabasTheme.current.secondarySurface)
                                .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "إضافة مستودع جديد للخزانة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QabasTheme.current.primary,
                                    fontFamily = CairoFont
                                )

                                OutlinedTextField(
                                    value = inputRepoUrl,
                                    onValueChange = { inputRepoUrl = it },
                                    label = { Text("رابط المستودع أو owner/repo", fontSize = 10.sp) },
                                    placeholder = { Text("مثال: username/my-repo", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = QabasTheme.current.primary,
                                        unfocusedBorderColor = QabasTheme.current.separator,
                                        focusedLabelColor = QabasTheme.current.primary,
                                        unfocusedLabelColor = QabasTheme.current.textSecondary
                                    )
                                )

                                OutlinedTextField(
                                    value = inputToken,
                                    onValueChange = { inputToken = it },
                                    label = { Text("مفتاح الوصول (GitHub PAT)", fontSize = 10.sp) },
                                    placeholder = { Text("ghp_...", fontSize = 10.sp) },
                                    singleLine = true,
                                    visualTransformation = if (showTokenText) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showTokenText = !showTokenText }) {
                                            Icon(
                                                imageVector = if (showTokenText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = QabasTheme.current.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = QabasTheme.current.primary,
                                        unfocusedBorderColor = QabasTheme.current.separator,
                                        focusedLabelColor = QabasTheme.current.primary,
                                        unfocusedLabelColor = QabasTheme.current.textSecondary
                                    )
                                )

                                OutlinedTextField(
                                    value = inputBranch,
                                    onValueChange = { inputBranch = it },
                                    label = { Text("الفرع الافتراضي", fontSize = 10.sp) },
                                    placeholder = { Text("main", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = QabasTheme.current.primary,
                                        unfocusedBorderColor = QabasTheme.current.separator,
                                        focusedLabelColor = QabasTheme.current.primary,
                                        unfocusedLabelColor = QabasTheme.current.textSecondary
                                    )
                                )

                                Button(
                                    onClick = { handleAddRepo() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = QabasTheme.current.primary,
                                        contentColor = QabasTheme.current.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                ) {
                                    Text(
                                        text = "حفظ وتعيين كمستودع نشط 💾",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }

                    // Repositories List Items
                    if (repos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد مستودعات إضافية محفوظة في الخزانة.",
                                fontSize = 11.sp,
                                color = QabasTheme.current.textTertiary,
                                fontFamily = CairoFont
                            )
                        }
                    } else {
                        repos.forEach { entry ->
                            val isCurrent = entry.id == currentActiveId
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) QabasTheme.current.cardSurface else QabasTheme.current.background)
                                    .border(
                                        1.dp,
                                        if (isCurrent) QabasTheme.current.success.copy(alpha = 0.5f) else QabasTheme.current.separator,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = entry.fullName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) QabasTheme.current.success else QabasTheme.current.textPrimary,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = "● نشط",
                                                    fontSize = 9.sp,
                                                    color = QabasTheme.current.success,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = CairoFont
                                                )
                                            }
                                        }
                                        Text(
                                            text = "فرع: ${entry.defaultBranch} | ${if (entry.token.isNotBlank()) "PAT متوفر" else "بدون PAT"}",
                                            fontSize = 9.sp,
                                            color = QabasTheme.current.textSecondary,
                                            fontFamily = CairoFont
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!isCurrent) {
                                            FilledTonalButton(
                                                onClick = { handleSwitchRepo(entry) },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = QabasTheme.current.separator,
                                                    contentColor = QabasTheme.current.primary
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = "تفعيل",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = CairoFont
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { handleDeleteRepo(entry.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "حذف",
                                                tint = QabasTheme.current.textTertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QabasTheme.current.success,
                        contentColor = QabasTheme.current.background
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = "إغلاق وتأكيد الاختيار ✓",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = CairoFont
                    )
                }
            }
        }
    }
}

/**
 * حوار المتطلبات الموحد (Unified Requirements Dialog)
 * يظهر عندما يحاول المستخدم إرسال رسالة دون توفر مفتاح API أو مستودع GitHub.
 */
@Composable
fun AgentRequirementDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfigComplete: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Auto-detection
    val detectedProvider = remember(apiKey) { UniversalKeyBrain.detect(apiKey) }
    
    // Repository selection
    var showRepoSelector by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            color = QabasTheme.current.cardSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, QabasTheme.current.warning.copy(alpha = 0.6f)) // Golden border
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(QabasTheme.current.warning.copy(alpha = 0.15f))
                        .border(1.dp, QabasTheme.current.warning.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = QabasTheme.current.warning,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "متطلبات العمل النشط 🚀",
                    style = MaterialTheme.typography.titleLarge,
                    color = QabasTheme.current.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "لبدء تنفيذ مهامك البرمجية، يحتاج الوكيل إلى مفتاح ذكاء اصطناعي ومستودع GitHub للعمل عليه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = QabasTheme.current.textSecondary,
                    fontFamily = CairoFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Section 1: AI Engine
                Column(modifier = Modifier.fillMaxWidth()) {
                    RequirementInputSection(
                        title = "1. مفتاح الذكاء الاصطناعي 🧠",
                        description = "سيتم التعرّف على المزود تلقائياً (Gemini, Groq, OpenRouter...)",
                        value = apiKey,
                        onValueChange = { 
                            apiKey = it
                            errorMessage = null
                        },
                        placeholder = "AIza... أو Aq... (Gemini), gsk_... (Groq), sk-or-... (OpenRouter)"
                    )
                    
                    if (apiKey.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = detectedProvider.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, detectedProvider.color.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (detectedProvider != DetectedProvider.NONE) Icons.Default.AutoAwesome else Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = detectedProvider.color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (detectedProvider != DetectedProvider.NONE) 
                                        "المزود المكتشف: ${detectedProvider.displayName}" 
                                        else "لم يتم التعرّف على صيغة المفتاح",
                                    color = detectedProvider.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Section 2: GitHub Integration (PAT only — OAuth flow deleted)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "2. مستودع GitHub 🐙",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = QabasTheme.current.textPrimary,
                        fontFamily = CairoFont
                    )

                    Text(
                        text = "رابط المستودع (مثال: owner/repo) — أدخل رمز PAT أدناه لجلب قائمة مستودعاتك",
                        fontSize = 10.sp,
                        color = QabasTheme.current.textSecondary,
                        fontFamily = CairoFont,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        placeholder = { Text("username/project-name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = QabasTheme.current.textPrimary,
                            unfocusedTextColor = QabasTheme.current.textPrimary,
                            cursorColor = QabasTheme.current.warning,
                            focusedBorderColor = QabasTheme.current.warning,
                            unfocusedBorderColor = QabasTheme.current.separator
                        ),
                        trailingIcon = {
                            if (githubToken.isNotBlank()) {
                                IconButton(
                                    onClick = { 
                                        showRepoSelector = true 
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "جلب المستودعات",
                                        tint = QabasTheme.current.warning
                                    )
                                }
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                RequirementInputSection(
                    title = "رمز GitHub PAT (اختياري)",
                    description = "مطلوب للمستودعات الخاصة ولجلب قائمة المستودعات.",
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    placeholder = "ghp_...",
                    isSecret = true
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = QabasTheme.current.destructive.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, QabasTheme.current.destructive.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = QabasTheme.current.destructive, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "بطاقة تشخيص الخطأ 🔍",
                                    color = QabasTheme.current.textPrimary,
                                    fontSize = 14.sp,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = QabasTheme.current.destructive,
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { errorMessage = null }) {
                                    Text("تجاهل", color = QabasTheme.current.textSecondary, fontSize = 12.sp, fontFamily = CairoFont)
                                }
                                Spacer(Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        errorMessage = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.destructive.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = QabasTheme.current.textPrimary)
                                        Spacer(Modifier.width(6.dp))
                                        Text("إعادة المحاولة", color = QabasTheme.current.textPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, QabasTheme.current.separator)
                    ) {
                        Text("إلغاء", color = QabasTheme.current.textPrimary, fontFamily = CairoFont)
                    }
                    
                    Button(
                        onClick = {
                            if (apiKey.isBlank() || repoUrl.isBlank()) {
                                errorMessage = "يرجى إدخال مفتاح API ورابط المستودع."
                                return@Button
                            }
                            
                            isVerifying = true
                            errorMessage = null
                            
                            scope.launch {
                                try {
                                    // 1. فحص ذكي وحفظ المفتاح
                                    val keyRes = UniversalKeyBrain.saveKeyIfLive(context, apiKey.trim())
                                    if (keyRes !is UniversalKeyBrain.SmartSaveResult.Saved) {
                                        isVerifying = false
                                        errorMessage = when (keyRes) {
                                            is UniversalKeyBrain.SmartSaveResult.Failed -> "فشل فحص المفتاح: ${keyRes.reason}"
                                            is UniversalKeyBrain.SmartSaveResult.Unsupported -> keyRes.reason
                                            else -> "فشل التحقق من المفتاح."
                                        }
                                        return@launch
                                    }
                                    
                                    // 2. فحص GitHub (PAT فقط)
                                    val effectiveToken = githubToken.trim()
                                    val githubHealth = AgentConnectivityEngine.pingGitHubLive(effectiveToken, repoUrl.trim())
                                    if (!githubHealth.isConnected && effectiveToken.isNotBlank()) {
                                        isVerifying = false
                                        errorMessage = "فشل GitHub: ${githubHealth.statusDetail}"
                                        return@launch
                                    }
                                    
                                    // 3. حفظ بيانات المستودع
                                    withContext(Dispatchers.IO) {
                                        val parts = repoUrl.trim().split("/")
                                        if (parts.size >= 2) {
                                            val owner = parts[parts.size - 2]
                                            val repoName = parts[parts.size - 1]
                                            RepoVaultManager.addOrUpdateRepo(
                                                context,
                                                RepoVaultEntry(
                                                    owner = owner,
                                                    repo = repoName,
                                                    token = effectiveToken
                                                )
                                            )
                                        }
                                    }
                                    
                                    isVerifying = false
                                    onConfigComplete()
                                    onDismiss()
                                    
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                    isVerifying = false
                                    errorMessage = "حدث خطأ غير متوقع: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.warning),
                        enabled = !isVerifying
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = QabasTheme.current.cardSurface, strokeWidth = 2.dp)
                        } else {
                            Text("حفظ وتفعيل ⚡", color = QabasTheme.current.cardSurface, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showRepoSelector) {
        // PAT فقط: لا يوجد مسار OAuth بعد الآن. لا يمكن فتح المنتقي بلا PAT،
        // لكن زر الجلب لا يظهر أصلاً إلا عند وجود PAT، فهذا حارس احتياطي فقط.
        val effectiveToken = githubToken

        if (effectiveToken.isBlank()) {
            LaunchedEffect(Unit) {
                showRepoSelector = false
            }
        } else {
            RepositorySelectionDialog(
                token = effectiveToken,
                onRepoSelected = { repo ->
                    repoUrl = repo.fullName
                    showRepoSelector = false
                },
                onDismiss = { showRepoSelector = false }
            )
        }
    }
}

@Composable
fun RepositorySelectionDialog(
    token: String,
    onRepoSelected: (UserRepoSummary) -> Unit,
    onDismiss: () -> Unit
) {
    var repos by remember { mutableStateOf<List<UserRepoSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                repos = GitHubRepoClient.listUserRepos(token)
                isLoading = false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                error = e.localizedMessage
                isLoading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            color = QabasTheme.current.cardSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, QabasTheme.current.warning.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "اختر مستودعاً 🐙",
                        style = MaterialTheme.typography.titleLarge,
                        color = QabasTheme.current.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = QabasTheme.current.textTertiary)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = QabasTheme.current.warning)
                    }
                } else if (error != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = QabasTheme.current.destructive, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "تعذر جلب المستودعات: $error", color = QabasTheme.current.destructive, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else if (repos.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = QabasTheme.current.textTertiary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "لم يتم العثور على مستودعات في هذا الحساب.", color = QabasTheme.current.textTertiary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(repos) { repo ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRepoSelected(repo) },
                                color = QabasTheme.current.separator.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, QabasTheme.current.separator)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (repo.isPrivate) QabasTheme.current.warning.copy(alpha = 0.1f) else QabasTheme.current.success.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                            contentDescription = null,
                                            tint = if (repo.isPrivate) QabasTheme.current.warning else QabasTheme.current.success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repo.fullName,
                                            color = QabasTheme.current.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CairoFont
                                        )
                                        if (repo.description.isNotBlank()) {
                                            Text(
                                                text = repo.description,
                                                color = QabasTheme.current.textSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontFamily = CairoFont
                                            )
                                        }
                                        Text(
                                            text = "تحديث: ${repo.updatedAt.take(10)}",
                                            color = QabasTheme.current.textTertiary,
                                            fontSize = 9.sp,
                                            fontFamily = CairoFont
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = QabasTheme.current.textTertiary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.separator)
                ) {
                    Text("إغلاق", color = QabasTheme.current.textPrimary, fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
private fun RequirementInputSection(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isSecret: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = QabasTheme.current.textPrimary,
            fontSize = 13.sp,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            color = QabasTheme.current.textSecondary,
            fontSize = 11.sp,
            fontFamily = CairoFont,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 12.sp, color = QabasTheme.current.textTertiary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = QabasTheme.current.textPrimary,
                unfocusedTextColor = QabasTheme.current.textPrimary,
                focusedBorderColor = QabasTheme.current.warning,
                unfocusedBorderColor = QabasTheme.current.separator,
                focusedContainerColor = QabasTheme.current.secondarySurface,
                unfocusedContainerColor = QabasTheme.current.secondarySurface
            ),
            singleLine = true
        )
    }
}
