package com.qabas.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projects: List<ProjectService.Project>,
    onBack: () -> Unit,
    onOpenProject: (ProjectService.Project) -> Unit,
    onDeleteProject: (String) -> Unit,
    onViewSettings: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    val theme = QabasTheme.current

    var projectToDelete by remember { mutableStateOf<ProjectService.Project?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") }

    val filteredProjects = remember(projects, searchQuery, selectedFilter) {
        projects.filter { proj ->
            val matchesSearch = searchQuery.isBlank() ||
                proj.title.contains(searchQuery, ignoreCase = true) ||
                proj.idea.contains(searchQuery, ignoreCase = true)

            val isDraft = proj.status.equals("DRAFT", ignoreCase = true) || proj.status.contains("مسودة")
            val matchesFilter = when (selectedFilter) {
                "المسودات" -> isDraft
                "المكتملة" -> !isDraft
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = theme.destructive
                    )
                    Text(
                        text = Translator.tr("حذف المشروع النهائي"),
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Text(
                    text = Translator.tr("هل أنت متأكد من حذف مشروع: \"${projectToDelete?.title?.cleanTitle()}\"؟ لا يمكن التراجع عن هذا الإجراء."),
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        projectToDelete?.let { onDeleteProject(it.id) }
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.destructive,
                        contentColor = theme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = Translator.tr("حذف نهائي"),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { projectToDelete = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = Translator.tr("إلغاء"),
                        color = theme.textSecondary,
                        fontFamily = CairoFont
                    )
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.PROJECTS_SCREEN),
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            AppleTopBar(
                title = Translator.tr("استوديو مشاريعي"),
                subtitle = if (projects.isEmpty()) {
                    Translator.tr("لا توجد مشاريع سابقة")
                } else {
                    "${projects.size} " + Translator.tr("مشاريع محفوظة ومسودات")
                },
                onBack = onBack,
                actions = {
                    CloudSyncIndicator(isSyncing = false)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "مشروع جديد",
                            tint = theme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = onViewSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "الإعدادات",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(theme.background)
        ) {
            if (projects.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = Translator.tr("بحث في اسم المشروع أو الفكرة...")
                    )

                    AppleSegmentedControl(
                        items = listOf(
                            "الكل" to "الكل (${projects.size})",
                            "المسودات" to "المسودات",
                            "المكتملة" to "المكتملة"
                        ),
                        selectedItem = selectedFilter,
                        onSelect = { selectedFilter = it }
                    )
                }
            }

            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.widthIn(max = 420.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                Translator.tr("لم نجد نتائج مطابقة للبحث")
                            } else {
                                Translator.tr("لا توجد مشاريع سابقة في الاستوديو")
                            },
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                Translator.tr("جرّب البحث بكلمات أخرى أو تغيير خيارات التصفية")
                            } else {
                                Translator.tr("ابدأ الآن بتحويل أفكارك النصية والدعوية إلى مقاطع احترافية.")
                            },
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        ApplePrimaryButton(
                            text = Translator.tr("إنشاء مشروع جديد"),
                            icon = Icons.Default.Add,
                            onClick = onBack
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredProjects,
                        key = { proj -> proj.id.ifEmpty { proj.title } }
                    ) { project ->
                        ProjectAppleCard(
                            project = project,
                            onOpen = { onOpenProject(project) },
                            onDelete = { projectToDelete = project }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectAppleCard(
    project: ProjectService.Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = QabasTheme.current
    val cleanTitle = remember(project.title) { project.title.cleanTitle() }
    val isDraft = remember(project.status) {
        project.status.equals("DRAFT", ignoreCase = true) || project.status.contains("مسودة")
    }
    val dateStr = remember(project.updatedAt) {
        if (project.updatedAt > 0) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.updatedAt))
        } else {
            Translator.tr("اليوم")
        }
    }
    val isSupabaseActive = SupabaseServices.isSupabaseAvailable

    AppleCard(onClick = onOpen) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDraft) Icons.Default.MovieCreation else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanTitle,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusColor = if (isDraft) theme.primary else theme.success
                        Surface(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isDraft) "مسودة" else "جاهز للتصدير",
                                color = statusColor,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (isSupabaseActive) Icons.Default.CloudDone else Icons.Default.Storage,
                                contentDescription = null,
                                tint = if (isSupabaseActive) theme.success else theme.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isSupabaseActive) "سحابي" else "محلي",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = "• $dateStr",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = NotoSansFont,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = Translator.tr("حذف"),
                        tint = theme.destructive,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = project.idea.ifBlank {
                        Translator.tr("مشروع فيديو تم إنشاؤه عبر استوديو مساعد المسلم.")
                    },
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AppleSecondaryButton(
                    text = Translator.tr("متابعة العمل"),
                    icon = Icons.Default.PlayArrow,
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(0.55f)
                )
            }
        }
    }
}

private fun String.cleanTitle(): String {
    if (isBlank()) return "مشروع سينمائي بدون عنوان"
    var result = this.trim()
    val parts = result.split(":")
    if (parts.size >= 2 && parts[0].trim().equals(parts[1].trim(), ignoreCase = true)) {
        result = parts.drop(1).joinToString(":").trim()
    }
    return result.ifBlank { "مشروع مساعد المسلم السينمائي" }
}
