package com.qabas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun UpdateManagementSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verifiedDeveloper = UpdateChannelFacade.isVerifiedDeveloper()

    var isLoading by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<UpdateManager.AdminGitHubRelease>>(emptyList()) }
    var history by remember { mutableStateOf<List<ActiveUpdate>>(emptyList()) }
    var activeUpdate by remember { mutableStateOf<ActiveUpdate?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var releaseNotes by remember { mutableStateOf("") }
    var isMandatory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val selectedRelease = releases.firstOrNull { it.tagName == selectedTag }
    val latestPublishedCode = history.maxOfOrNull { it.versionCode } ?: 0
    val eligibleReleases = releases.filter { it.versionCode > latestPublishedCode }

    suspend fun refresh() {
        isLoading = true
        error = null
        try {
            val fetchedReleases = UpdateManager.getAdminGitHubReleases(context).getOrThrow()
            val fetchedHistory = SupabaseServices.Database.getAppUpdateHistoryOrThrow()
            val current = SupabaseServices.Database.getLatestActiveUpdateOrThrow()
            releases = fetchedReleases
            history = fetchedHistory
            activeUpdate = current
            val nextSelection = fetchedReleases.firstOrNull { it.versionCode > (fetchedHistory.maxOfOrNull { row -> row.versionCode } ?: 0) }
            selectedTag = nextSelection?.tagName
            releaseNotes = nextSelection?.releaseNotes.orEmpty()
            isMandatory = false
        } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل بيانات الإصدارات"
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
            Text("إدارة الإصدارات متاحة للمطور الموثّق فقط", color = Color.White, fontWeight = FontWeight.Bold)
            Text("يتم التحقق من الصلاحية مرة أخرى على خادم Supabase عند النشر.", color = Color.LightGray, fontSize = 12.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("إدارة إصدارات قبس", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("GitHub للتخزين وSupabase لاعتماد الإصدار وسياسة التحديث", color = Color.LightGray, fontSize = 11.sp)
                }
                OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !isLoading) {
                    Text("تحديث القائمة")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الإصدار الذي يصل للمستخدمين", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                    if (activeUpdate != null) {
                        val active = activeUpdate!!
                        Text("v${active.versionName}  •  Build ${active.versionCode}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (active.isMandatory) "تحديث إجباري" else "تحديث اختياري", color = if (active.isMandatory) Color(0xFFF87171) else Color(0xFF34D399), fontSize = 12.sp)
                        Text("APK: ${active.asset_name ?: active.downloadUrl.substringAfterLast('/')}", color = Color.LightGray, fontSize = 11.sp)
                        Text("الحجم: ${formatUpdateBytes(active.fileSizeBytes)}", color = Color.LightGray, fontSize = 11.sp)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isPublishing = true
                                    error = null
                                    message = null
                                    try {
                                        SupabaseServices.Database.setAppUpdateActive(null, false)
                                        refresh()
                                        message = "تم إيقاف توزيع التحديث مؤقتاً. لم يتم حذف الإصدار أو التراجع عنه."
                                    } catch (e: Exception) {
                                        error = e.message ?: "تعذر إيقاف التحديث"
                                    } finally {
                                        isPublishing = false
                                    }
                                }
                            },
                            enabled = !isPublishing
                        ) { Text("إيقاف التحديث مؤقتاً") }
                    } else {
                        Text("لا يوجد إصدار معتمد حالياً.", color = Color.LightGray)
                        val latest = history.maxByOrNull { it.versionCode }
                        if (latest != null) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isPublishing = true
                                        error = null
                                        message = null
                                        try {
                                            SupabaseServices.Database.setAppUpdateActive(latest.id, true)
                                            refresh()
                                            message = "تمت إعادة تفعيل أحدث إصدار منشور."
                                        } catch (e: Exception) {
                                            error = e.message ?: "تعذر تفعيل الإصدار"
                                        } finally {
                                            isPublishing = false
                                        }
                                    }
                                },
                                enabled = !isPublishing
                            ) { Text("إعادة تفعيل أحدث إصدار منشور") }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        }

        if (!error.isNullOrBlank()) {
            item { Text("خطأ: $error", color = Color(0xFFF87171), fontSize = 12.sp) }
        }
        if (!message.isNullOrBlank()) {
            item { Text(message!!, color = Color(0xFF34D399), fontSize = 12.sp) }
        }

        item {
            Text("إصدارات GitHub التي لم تُنشر بعد", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("لا يصل أي إصدار للمستخدمين بمجرد نشره على GitHub. يجب اختياره واعتماده هنا.", color = Color.LightGray, fontSize = 11.sp)
        }

        if (!isLoading && eligibleReleases.isEmpty()) {
            item {
                Text(
                    if (releases.isEmpty()) "لم يتم العثور على إصدارات منشورة تحتوي على APK صالح."
                    else "لا توجد إصدارات أحدث من آخر بناء تم نشره في Supabase.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        items(eligibleReleases, key = { it.tagName }) { release ->
            val selected = release.tagName == selectedTag
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF1E293B) else Color(0xFF111827)),
                border = BorderStroke(1.dp, if (selected) Color(0xFFFBBF24) else Color(0xFF334155)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(release.tagName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Build ${release.versionCode}  •  ${formatUpdateBytes(release.apkSize)}", color = Color.LightGray, fontSize = 11.sp)
                    Text("APK: ${release.assetName}", color = Color.LightGray, fontSize = 11.sp)
                    Text("تاريخ النشر: ${release.publishedAt.ifBlank { "غير متاح" }}", color = Color.LightGray, fontSize = 10.sp)
                    OutlinedButton(
                        onClick = {
                            selectedTag = release.tagName
                            releaseNotes = release.releaseNotes
                            isMandatory = false
                            error = null
                            message = null
                        },
                        enabled = !isPublishing
                    ) { Text(if (selected) "الإصدار المحدد" else "اختيار هذا الإصدار") }
                }
            }
        }

        if (selectedRelease != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("اعتماد ${selectedRelease.tagName}", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تحديث إجباري", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("يبدأ التنزيل الخلفي تلقائياً لدى المستخدمين", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Switch(checked = isMandatory, onCheckedChange = { isMandatory = it }, enabled = !isPublishing)
                        }
                        OutlinedTextField(
                            value = releaseNotes,
                            onValueChange = { releaseNotes = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ملاحظات الإصدار") },
                            minLines = 3,
                            maxLines = 7
                        )
                        Text("سيتم اعتماد APK: ${selectedRelease.assetName} (${formatUpdateBytes(selectedRelease.apkSize)})", color = Color.LightGray, fontSize = 11.sp)
                        Button(
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
                                            releaseNotes = releaseNotes,
                                            fileSizeBytes = selectedRelease.apkSize,
                                            sha256Hash = selectedRelease.sha256Hash,
                                            isMandatory = isMandatory,
                                            githubReleaseUrl = selectedRelease.releaseUrl,
                                            releaseTag = selectedRelease.tagName,
                                            assetName = selectedRelease.assetName
                                        )
                                        message = "تم اعتماد الإصدار بنجاح. سيحصل المستخدمون عليه عند فحص التحديثات."
                                        selectedTag = null
                                        refresh()
                                    } catch (e: Exception) {
                                        error = e.message ?: "رفض الخادم اعتماد الإصدار"
                                    } finally {
                                        isPublishing = false
                                    }
                                }
                            },
                            enabled = !isPublishing && !selectedRelease.apkUrl.isBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isPublishing) CircularProgressIndicator()
                            else Text("اعتماد الإصدار للمستخدمين", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Divider(color = Color(0xFF334155))
                Text("سجل الإصدارات المنشورة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(history.take(10), key = { it.id }) { release ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("v${release.versionName} • Build ${release.versionCode}", color = Color.White, fontSize = 12.sp)
                        Text(if (release.isActive) "معتمد حالياً" else "منشور سابقاً", color = if (release.isActive) Color(0xFF34D399) else Color.LightGray, fontSize = 10.sp)
                    }
                    Text(if (release.isMandatory) "إجباري" else "اختياري", color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) return "حجم غير معروف"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
