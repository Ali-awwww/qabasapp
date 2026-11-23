package com.qabas.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun UpdateManagementSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    val verifiedDeveloper = UpdateChannelFacade.isVerifiedDeveloper()

    var isLoading by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<UpdateManager.AdminGitHubRelease>>(emptyList()) }
    var history by remember { mutableStateOf<List<ActiveUpdate>>(emptyList()) }
    var audit by remember { mutableStateOf<List<AppUpdateAudit>>(emptyList()) }
    var activeUpdate by remember { mutableStateOf<ActiveUpdate?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var releaseNotes by remember { mutableStateOf("") }
    var isMandatory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var githubError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val selectedRelease = releases.firstOrNull { it.tagName == selectedTag }
    val latestPublishedCode = history.maxOfOrNull { it.versionCode } ?: 0
    val eligibleReleases = releases.filter { it.versionCode > latestPublishedCode }

    suspend fun refresh() {
        isLoading = true
        error = null
        githubError = null
        try {
            val fetchedHistory = SupabaseServices.Database.getAppUpdateHistoryOrThrow()
            val current = SupabaseServices.Database.getLatestActiveUpdateOrThrow()
            history = fetchedHistory
            activeUpdate = current
            audit = runCatching {
                SupabaseServices.Database.getAppUpdateAuditOrThrow()
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyList())

            try {
                val fetchedReleases = UpdateManager.getAdminGitHubReleases(context).getOrThrow()
                releases = fetchedReleases
                val nextSelection = fetchedReleases.firstOrNull {
                    it.versionCode > (fetchedHistory.maxOfOrNull { row -> row.versionCode } ?: 0)
                }
                selectedTag = nextSelection?.tagName
                releaseNotes = nextSelection?.releaseNotes.orEmpty()
                isMandatory = false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                githubError = e.message ?: "تعذر جلب قائمة إصدارات GitHub"
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل بيانات الإصدارات من Supabase"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(verifiedDeveloper) {
        if (verifiedDeveloper) refresh()
    }

    if (!verifiedDeveloper) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("إدارة الإصدارات متاحة للمطور الموثّق فقط", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("يتم التحقق من الصلاحية على خادم Supabase عند النشر.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("إدارة إصدارات خادم المسلم", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("GitHub للتخزين وSupabase لاعتماد الإصدار وسياسة التحديث", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                }
                IconButton(onClick = { scope.launch { refresh() } }, enabled = !isLoading, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث القائمة", tint = theme.primary)
                }
            }
        }

        item {
            AppleCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الإصدار المعتمد للمستخدمين", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                    if (activeUpdate != null) {
                        val active = activeUpdate!!
                        Text("v${active.versionName} • Build ${active.versionCode}", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        Text(
                            if (active.isMandatory) "تحديث إجباري" else "تحديث اختياري",
                            color = if (active.isMandatory) theme.destructive else theme.success,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("APK: ${active.asset_name ?: active.downloadUrl.substringAfterLast('/')}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                        Text("الحجم: ${formatUpdateBytes(active.fileSizeBytes)}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                        AppleSecondaryButton(
                            text = "إيقاف التحديث مؤقتاً",
                            tint = theme.warning,
                            enabled = !isPublishing,
                            onClick = {
                                scope.launch {
                                    isPublishing = true
                                    error = null
                                    message = null
                                    try {
                                        SupabaseServices.Database.setAppUpdateActive(null, false)
                                        refresh()
                                        message = "تم إيقاف توزيع التحديث مؤقتاً."
                                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                        error = e.message ?: "تعذر إيقاف التحديث"
                                    } finally {
                                        isPublishing = false
                                    }
                                }
                            }
                        )
                    } else {
                        Text("لا يوجد إصدار معتمد حالياً.", color = theme.textSecondary, fontFamily = NotoSansFont)
                        val latest = history.maxByOrNull { it.versionCode }
                        if (latest != null) {
                            AppleSecondaryButton(
                                text = "إعادة تفعيل أحدث إصدار منشور",
                                enabled = !isPublishing,
                                onClick = {
                                    scope.launch {
                                        isPublishing = true
                                        error = null
                                        message = null
                                        try {
                                            SupabaseServices.Database.setAppUpdateActive(latest.id, true)
                                            refresh()
                                            message = "تمت إعادة تفعيل أحدث إصدار منشور."
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                            error = e.message ?: "تعذر تفعيل الإصدار"
                                        } finally {
                                            isPublishing = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = theme.primary) } }
        }

        if (!error.isNullOrBlank()) {
            item { Text("خطأ في بيانات Supabase: $error", color = theme.destructive, fontFamily = NotoSansFont, fontSize = 12.sp) }
        }
        if (!githubError.isNullOrBlank()) {
            item {
                Text(
                    "تعذر تحديث قائمة GitHub: $githubError. بيانات الإصدار المعتمد وسجل Supabase ما زالت معروضة.",
                    color = theme.warning,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }
        }
        if (!message.isNullOrBlank()) {
            item { Text(message!!, color = theme.success, fontFamily = CairoFont, fontSize = 12.sp) }
        }

        item {
            Column {
                Text("إصدارات GitHub التي لم تُنشر بعد", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("لا يصل أي إصدار للمستخدمين بمجرد نشره على GitHub. يجب اختياره واعتماده هنا.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
            }
        }

        if (!isLoading && eligibleReleases.isEmpty()) {
            item {
                Text(
                    if (githubError != null) "تعذر التحقق من إصدارات GitHub حالياً؛ أعد المحاولة لاحقاً."
                    else if (releases.isEmpty()) "لم يتم العثور على إصدارات منشورة تحتوي على APK صالح."
                    else "لا توجد إصدارات أحدث من آخر بناء منشور في Supabase.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }
        }

        items(eligibleReleases, key = { it.tagName }) { release ->
            val selected = release.tagName == selectedTag
            AppleCard(
                containerColor = if (selected) theme.primary.copy(alpha = 0.08f) else theme.cardSurface,
                onClick = {
                    if (!isPublishing) {
                        selectedTag = release.tagName
                        releaseNotes = release.releaseNotes
                        isMandatory = false
                        error = null
                        message = null
                    }
                }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(release.tagName, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        if (selected) {
                            Text("محدد", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Text("Build ${release.versionCode} • ${formatUpdateBytes(release.apkSize)}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                    Text("APK: ${release.assetName}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                    Text(
                        if (release.sha256Hash != null) "SHA-256: متوفر للتحقق من سلامة الملف" else "SHA-256: غير متوفر لهذا الإصدار",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp
                    )
                    Text("تاريخ النشر: ${release.publishedAt.ifBlank { "غير متاح" }}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 10.5.sp)
                }
            }
        }

        if (selectedRelease != null) {
            item {
                AppleCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("اعتماد ${selectedRelease.tagName}", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تحديث إجباري", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                Text("يبدأ التنزيل الخلفي تلقائياً لدى المستخدمين", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                            }
                            Switch(checked = isMandatory, onCheckedChange = { isMandatory = it }, enabled = !isPublishing)
                        }
                        OutlinedTextField(
                            value = releaseNotes,
                            onValueChange = { releaseNotes = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ملاحظات الإصدار", fontFamily = CairoFont) },
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            maxLines = 7
                        )
                        Text("سيتم اعتماد APK: ${selectedRelease.assetName} (${formatUpdateBytes(selectedRelease.apkSize)})", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                        Text(
                            if (selectedRelease.deltaUrl != null) "Delta: ${formatUpdateBytes(selectedRelease.deltaSize)} • أساس Build ${selectedRelease.baseVersionCode}"
                            else "Delta: غير متاح — سيُستخدم APK الكامل",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp
                        )
                        ApplePrimaryButton(
                            text = "اعتماد الإصدار للمستخدمين",
                            isLoading = isPublishing,
                            enabled = !isPublishing && selectedRelease.apkUrl.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isPublishing = true
                                    error = null
                                    message = null
                                    try {
                                        SupabaseServices.Database.publishAppUpdate(
                                            versionName = selectedRelease.versionName,
                                            versionCode = selectedRelease.versionCode,
                                            downloadUrl = selectedRelease.apkUrl,
                                            releaseNotes = UpdateManager.sanitizePublicReleaseNotes(releaseNotes),
                                            fileSizeBytes = selectedRelease.apkSize,
                                            sha256Hash = selectedRelease.sha256Hash,
                                            isMandatory = isMandatory,
                                            githubReleaseUrl = selectedRelease.releaseUrl,
                                            releaseTag = selectedRelease.tagName,
                                            assetName = selectedRelease.assetName,
                                            deltaUrl = selectedRelease.deltaUrl,
                                            deltaSizeBytes = selectedRelease.deltaSize,
                                            deltaSha256Hash = selectedRelease.deltaSha256Hash,
                                            baseVersionCode = selectedRelease.baseVersionCode,
                                            baseSha256Hash = selectedRelease.baseSha256Hash
                                        )
                                        message = "تم اعتماد الإصدار بنجاح."
                                        selectedTag = null
                                        refresh()
                                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                        error = e.message ?: "رفض الخادم اعتماد الإصدار"
                                    } finally {
                                        isPublishing = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("سجل الإصدارات المنشورة", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(history.take(10), key = { it.id }) { release ->
                AppleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("v${release.versionName} • Build ${release.versionCode}", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (release.isActive) "معتمد حالياً" else "منشور سابقاً", color = if (release.isActive) theme.success else theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                        }
                        Text(if (release.isMandatory) "إجباري" else "اختياري", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                }
            }
        }

        if (audit.isNotEmpty()) {
            item {
                HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("سجل عمليات النشر والتحكم", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(audit, key = { "audit-${it.id}" }) { entry ->
                val actionLabel = when (entry.action) {
                    "published" -> "نشر إصدار"
                    "paused" -> "إيقاف التحديث مؤقتاً"
                    "activated" -> "إعادة تفعيل إصدار"
                    else -> entry.action
                }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(actionLabel, color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${entry.actorRole} • حساب ${entry.actorId.take(8)} • ${entry.createdAt} • Build ${entry.newVersionCode ?: entry.oldVersionCode ?: "—"}",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) return "حجم غير معروف"
    val mb = bytes / 1_000_000.0
    return String.format(Locale.US, "%.1f MB", mb)
}
