package com.qabas.app

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * مكتبة مشاريع الاستوديو: أحدث مشروع + قائمة المشاريع + طابور مهام
 * الفيديو + سجل التوليدات.
 *
 * حدود صريحة:
 * - التكلفة التراكمية من [ProjectService.Project.cost] — وهي 0.0 في كل
 *   مواقع الإنشاء اليوم، فتُعرض «غير مسجلة» بدل رقم مخترع.
 * - التكلفة الفعلية في سجل التوليدات 0 حتى تُقاس من المزود؛ تُعرض
 *   «غير مقاسة بعد» عند الصفر.
 * - طابور المهام وسجل التوليدات يقرآن جدولي `studio_jobs` و`ai_generations`
 *   (الدفعة 4) ويفشلان بلطف إلى فارغ عند غياب الجدولين قبل تطبيق الترحيل.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioLibraryScreen(
    onBack: () -> Unit,
    onOpenProject: (ProjectService.Project) -> Unit = {},
    onRetryJob: (SupabaseServices.StudioJobRow) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<ProjectService.Project>>(emptyList()) }
    var vibeCount by remember { mutableStateOf(0) }
    var jobs by remember { mutableStateOf<List<SupabaseServices.StudioJobRow>>(emptyList()) }
    var generations by remember { mutableStateOf<List<SupabaseServices.AiGenerationRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            loading = true
            // (12.15) مكتبة المحتوى تعرض مشاريع المحتوى فقط — مشاريع
            // فايب كودينج (حالة vibe_coding) تُعرض في قسم فايب كودينج.
            val all = runCatching { ProjectService(context).getLocalProjects() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyList())
            projects = all.filterNot { it.status == VibeCodingProjects.STATUS }
            vibeCount = all.count { it.status == VibeCodingProjects.STATUS }
            jobs = SupabaseServices.Database.getStudioJobs()
            generations = SupabaseServices.Database.getAiGenerations()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مكتبة المشاريع") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) {
                item { CircularProgressIndicator() }
            }

            val latest = projects.firstOrNull()
            if (latest != null) {
                item {
                    Text("أحدث مشروع", style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(latest.title.ifBlank { "مشروع بلا عنوان" }, style = MaterialTheme.typography.titleSmall)
                            Text("الحالة: ${latest.status.ifBlank { "مسودة" }}")
                            Text("آخر مرحلة: ${latest.lastScreen.ifBlank { "غير محددة" }}")
                            Text("التكلفة التراكمية: ${formatProjectCost(latest.cost)}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onOpenProject(latest) }) {
                                Text("تابع من حيث توقفت")
                            }
                        }
                    }
                }
            } else if (!loading) {
                item {
                    Text(
                        if (vibeCount > 0) {
                            "لا مشاريع محتوى بعد — لديك $vibeCount مشروع فايب كودينج في قسم فايب كودينج."
                        } else {
                            "لا مشاريع بعد — اصنع أول مشروع من وكيل الاستوديو."
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("كل المشاريع (${projects.size})", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { reload() }) { Text("تحديث") }
                }
                if (vibeCount > 0) {
                    Text(
                        "مشاريع فايب كودينج ($vibeCount) تُعرض في قسم فايب كودينج لا هنا.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            items(projects, key = { it.id }) { project ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(project.title.ifBlank { "مشروع بلا عنوان" }, style = MaterialTheme.typography.titleSmall)
                        Text("الحالة: ${project.status.ifBlank { "مسودة" }} — آخر مرحلة: ${project.lastScreen.ifBlank { "غير محددة" }}")
                        Text("التكلفة التراكمية: ${formatProjectCost(project.cost)}")
                        OutlinedButton(onClick = { onOpenProject(project) }) { Text("فتح") }
                    }
                }
            }

            item { Text("طابور مهام الفيديو", style = MaterialTheme.typography.titleMedium) }
            if (jobs.isEmpty() && !loading) {
                item { Text("لا مهام فيديو مسجلة بعد — يظهر هنا طابور التوليد بعد تطبيق ترحيل studio_jobs.") }
            }
            items(jobs, key = { it.id }) { job ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(job.prompt.ifBlank { "مهمة فيديو" }, style = MaterialTheme.typography.titleSmall)
                        Text("النوع: ${job.type} — الحالة: ${job.status}")
                        LinearProgressIndicator(
                            progress = { job.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (job.filePath != null) Text("الملف: ${job.filePath}")
                        if (!job.error.isNullOrBlank()) {
                            Text("سبب الفشل: ${job.error}", color = MaterialTheme.colorScheme.error)
                        }
                        if (job.status == "failed") {
                            OutlinedButton(onClick = { onRetryJob(job) }) {
                                Text("إعادة من الوكيل")
                            }
                        }
                    }
                }
            }

            item { Text("سجل التوليدات", style = MaterialTheme.typography.titleMedium) }
            if (generations.isEmpty() && !loading) {
                item { Text("لا توليدات مسجلة بعد.") }
            }
            items(generations, key = { it.id }) { gen ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${gen.type} — ${gen.provider}", style = MaterialTheme.typography.titleSmall)
                        if (!gen.prompt.isNullOrBlank()) Text("الأمر: ${gen.prompt}")
                        if (!gen.filePath.isNullOrBlank()) Text("الملف: ${gen.filePath}")
                        Text(
                            "التكلفة: " + if (gen.costUsd == 0.0) "غير مقاسة بعد"
                            else "${gen.costUsd} دولار"
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/** التكلفة 0.0 تعني «غير مسجلة» — كل مواقع الإنشاء اليوم تتركها صفراً. */
private fun formatProjectCost(cost: Double): String =
    if (cost == 0.0) "غير مسجلة" else "$cost دولار"
