package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.HudCornerDecor
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

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
    var projectToDelete by remember { mutableStateOf<ProjectService.Project?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") } // الكل, المسودات, المكتملة

    // تصفية المشاريع بحسب البحث والفئة
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
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444))
                    Text(
                        Translator.tr("حذف المشروع النهائي"),
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    Translator.tr("هل أنت متأكد من حذف مشروع: \"${projectToDelete?.title?.cleanTitle()}\"؟ لا يمكن التراجع عن هذا الإجراء."),
                    color = Color.White,
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        projectToDelete?.let { onDeleteProject(it.id) }
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = CutCornerShape(10.dp)
                ) {
                    Text(Translator.tr("حذف نهائي 🗑️"), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(Translator.tr("إلغاء"), color = Color(0xFF94A3B8), fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = CutCornerShape(12.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Spatial HUD Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val primaryColor = GoldPrimary
            val stepX = 40.dp.toPx()
            val stepY = 40.dp.toPx()
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
            containerColor = Color.Transparent,
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
                                .background(
                                    Brush.linearGradient(
                                        listOf(GoldPrimary.copy(alpha = 0.25f), Color(0xFF38BDF8).copy(alpha = 0.15f))
                                    )
                                )
                                .border(1.2.dp, GoldPrimary.copy(alpha = 0.5f), CutCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }

                        Column {
                            Text(
                                Translator.tr("استوديو مشاريعي"),
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (projects.isEmpty()) Translator.tr("لا توجد مشاريع سابقة") else "${projects.size} " + Translator.tr("مشاريع محفوظة ومسودات"),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = NotoSansFont
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Translator.tr("العودة"),
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Surface(
                            color = GoldPrimary.copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "مشروع جديد", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(qabasBackground())
        ) {
            if (projects.isNotEmpty()) {
                // 🔍 شريط البحث والتصفية الهندسي الفاخر
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Translator.tr("بحث في اسم المشروع أو الفكرة..."), color = TextSecondary, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardSurface,
                            unfocusedContainerColor = CardSurface,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = GoldPrimary.copy(alpha = 0.25f)
                        ),
                        shape = CutCornerShape(10.dp),
                        singleLine = true
                    )

                    // 🏷️ شرائح التصفية الذكية
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("الكل", "المسودات", "المكتملة").forEach { filterName ->
                            val isSelected = selectedFilter == filterName
                            Surface(
                                onClick = { selectedFilter = filterName },
                                color = if (isSelected) GoldPrimary else CardSurface,
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else GoldPrimary.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = when (filterName) {
                                        "الكل" -> "الكل 📂 (${projects.size})"
                                        "المسودات" -> "المسودات 📝"
                                        else -> "المكتملة 🎬"
                                    },
                                    color = if (isSelected) DeepSlate else TextPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(GoldPrimary.copy(alpha = 0.25f), Color.Transparent)
                                    )
                                )
                                .border(1.5.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = if (searchQuery.isNotEmpty()) Translator.tr("لم نجد نتائج مطابقة للبحث") else Translator.tr("لا توجد مشاريع سابقة في الاستوديو"),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Text(
                            text = if (searchQuery.isNotEmpty()) Translator.tr("جرب البحث بكلمات أخرى أو تغيير خيارات التصفية") else Translator.tr("ابدأ الآن بتحويل أفكارك النصية والدعوية إلى مقاطع سينمائية احترافية مع الذكاء الاصطناعي."),
                            color = Color(0xFF94A3B8),
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = CutCornerShape(10.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("إنشاء مشروع جديد ✨"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = filteredProjects,
                        key = { proj -> proj.id.ifEmpty { proj.title } }
                    ) { project ->
                        ProjectSpatialCard(
                            project = project,
                            onOpen = { onOpenProject(project) },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * بطاقة مشروع فضائية عالية الدقة بتصميم HUD زجاجي
 */
@Composable
private fun ProjectSpatialCard(
    project: ProjectService.Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanTitle = remember(project.title) { project.title.cleanTitle() }
    val isDraft = remember(project.status) {
        project.status.equals("DRAFT", ignoreCase = true) || project.status.contains("مسودة")
    }

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = CutCornerShape(16.dp),
        backgroundColor = qabasCardSurface().copy(alpha = 0.85f),
        borderColor = GoldPrimary.copy(alpha = 0.35f)
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = GoldPrimary.copy(alpha = 0.1f))
        Column(modifier = Modifier.padding(16.dp)) {
            // الصف العلوي: بوستر المشروع + العنوان + شارة الحالة + زر الحذف
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // بوستر سينمائي مصغر
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CutCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                            )
                        )
                        .border(1.2.dp, GoldPrimary.copy(alpha = 0.45f), CutCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDraft) Icons.Default.MovieCreation else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanTitle,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // شارة حالة المشروع
                        Surface(
                            color = if (isDraft) Color(0xFF0EA5E9).copy(alpha = 0.18f) else EmeraldGreen.copy(alpha = 0.18f),
                            shape = CutCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (isDraft) Color(0xFF0EA5E9).copy(alpha = 0.5f) else EmeraldGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isDraft) "مسودة 📝" else "جاهز للتصدير 🎬",
                                color = if (isDraft) Color(0xFF38BDF8) else EmeraldGreen,
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        // التاريخ
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            val dateStr = if (project.updatedAt > 0) {
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(project.updatedAt))
                            } else {
                                Translator.tr("اليوم")
                            }
                            Text(dateStr, color = Color(0xFF94A3B8), fontSize = 10.5.sp, fontFamily = NotoSansFont)
                        }
                    }
                }

                // زر حذف أنيق بحجم ملموس مريح
                Surface(
                    onClick = onDelete,
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = Translator.tr("حذف"),
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // معاينة الفكرة النصية للمشروع داخل صندوق زجاجي مقعر
            Surface(
                color = Color(0xFF070C15),
                shape = CutCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = project.idea.ifBlank { Translator.tr("مشروع فيديو سينمائي تم إنشاؤه عبر استوديو قبس الذكي.") },
                    color = Color(0xFFCBD5E1),
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // الصف السفلي: زر العمل الرئيسي
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = CutCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DeepSlate,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        Translator.tr("متابعة العمل ⚡"),
                        color = DeepSlate,
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * وظيفة تنظيف العناوين المكررة تلقائياً
 */
private fun String.cleanTitle(): String {
    if (isBlank()) return "مشروع سينمائي بدون عنوان"
    var result = this.trim()
    // إزالة التكرار الملاحظ مثل "الحلقة 4: الحلقة 4: ..."
    val parts = result.split(":")
    if (parts.size >= 2 && parts[0].trim().equals(parts[1].trim(), ignoreCase = true)) {
        result = parts.drop(1).joinToString(":").trim()
    }
    return result.ifBlank { "مشروع قبس السينمائي" }
}
