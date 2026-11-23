package com.qabas.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 🗂️ متصفح ومستودع الوسائط المركزي الموحّد (Central Media Browser)
 * يربط بين سحابة Supabase Storage والملفات المحلية المولّدة بالذكاء الاصطناعي (FLUX + Edge-TTS + Videos)
 * في واجهة قيادة زجاجية سيبرانية فائقة السرعة والتنظيم.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CentralMediaBrowserScreen(
    onBack: () -> Unit,
    onSelectAssetForProject: (CentralMediaAsset) -> Unit = {},
    onOpenInPhotoStudio: (String) -> Unit = {},
    onOpenInVideoEditor: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // استهلاك حالة الوسائط المركزية
    val assets by CentralMediaManager.assets.collectAsState()
    val isLoading by CentralMediaManager.isLoading.collectAsState()
    val cloudStatus by CentralMediaManager.cloudSyncStatus.collectAsState()

    // عناصر التحكم في الواجهة
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CentralMediaType.ALL) }
    var selectedAssetForDetail by remember { mutableStateOf<CentralMediaAsset?>(null) }
    var isGridView by remember { mutableStateOf(true) }

    // تشغيل التحديث الأولي عند فتح الشاشة
    LaunchedEffect(Unit) {
        CentralMediaManager.refreshAllMedia(context)
    }

    // لاقط الصور والفيديوهات من المعرض
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val type = if (mimeType.startsWith("video")) CentralMediaType.VIDEO else CentralMediaType.IMAGE
                val imported = CentralMediaManager.importFromUri(context, uri, type)
                if (imported != null) {
                    Toast.makeText(context, "تمت إضافة الملف لمستودع الوسائط ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذّر استيراد الملف — تحقق من الملف وحاول مجدداً", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // لاقط الملفات الصوتية
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = CentralMediaManager.importFromUri(context, uri, CentralMediaType.AUDIO)
                if (imported != null) {
                    Toast.makeText(context, "تم استيراد الملف الصوتي بنجاح 🎙️", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذّر استيراد الملف الصوتي — تحقق من الملف وحاول مجدداً", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // تصفية الوسائط بناءً على البحث والنوع
    val filteredAssets = remember(assets, searchQuery, selectedFilter) {
        assets.filter { asset ->
            val matchesType = when (selectedFilter) {
                CentralMediaType.ALL -> true
                CentralMediaType.IMAGE -> asset.type == CentralMediaType.IMAGE
                CentralMediaType.VIDEO -> asset.type == CentralMediaType.VIDEO
                CentralMediaType.AUDIO -> asset.type == CentralMediaType.AUDIO
                CentralMediaType.CLOUD -> asset.isUploadedToCloud || asset.source == CentralMediaSource.SUPABASE_CLOUD
                CentralMediaType.AI_GENERATED -> asset.source == CentralMediaSource.AI_FLUX || asset.source == CentralMediaSource.AI_VOICE
            }
            val matchesQuery = searchQuery.isBlank() ||
                    asset.title.contains(searchQuery, ignoreCase = true) ||
                    asset.promptOrNote.contains(searchQuery, ignoreCase = true) ||
                    (asset.bucketName ?: "").contains(searchQuery, ignoreCase = true)

            matchesType && matchesQuery
        }
    }

    Scaffold(
        containerColor = theme.background,
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                containerColor = primaryColor,
                contentColor = DeepSlate,
                shape = CutCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "إضافة وسائط", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("رفع وسائط", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(primaryColor.copy(alpha = 0.15f))
                                .border(1.dp, primaryColor.copy(alpha = 0.4f), CutCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PermMedia, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "مستودع الوسائط المركزي",
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "SUPABASE STORAGE & AI NEXUS ✦",
                                color = theme.textSecondary,
                                fontFamily = RobotoMonoFont,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = primaryColor)
                    }
                },
                actions = {
                    // زر استيراد صوت مخصص
                    IconButton(onClick = { audioPickerLauncher.launch("audio/*") }) {
                        Icon(Icons.Default.AudioFile, contentDescription = "استيراد صوت", tint = theme.accent)
                    }

                    // زر التبديل بين Grid و List
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "طريقة العرض",
                            tint = theme.textPrimary
                        )
                    }

                    // زر التحديث المباشر
                    IconButton(
                        onClick = {
                            scope.launch {
                                CentralMediaManager.refreshAllMedia(context)
                                Toast.makeText(context, "تم تحديث مستودع الوسائط 🔄", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primaryColor, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "مزامنة", tint = primaryColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // 1. شريط البحث وحالة المزامنة السحابية
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CutCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = theme.secondarySurface,
                        focusedContainerColor = theme.cardSurface,
                        unfocusedContainerColor = theme.cardSurface,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    ),
                    placeholder = { Text("بحث في الوسائط، البوسترات، والتوليد...", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 11.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح", tint = theme.textTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true
                )

                // مؤشر السحابة
                Surface(
                    color = if (SupabaseServices.isSupabaseAvailable) theme.success.copy(alpha = 0.12f) else theme.secondarySurface,
                    shape = CutCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, if (SupabaseServices.isSupabaseAvailable) theme.success else theme.separator),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (SupabaseServices.isSupabaseAvailable) theme.success else theme.textSecondary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (SupabaseServices.isSupabaseAvailable) "Supabase" else "Local",
                            color = theme.textPrimary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. أشرطة التصفية السريعة (Filter Chips)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CentralMediaType.entries) { type ->
                    val isSelected = selectedFilter == type
                    Surface(
                        onClick = { selectedFilter = type },
                        shape = CutCornerShape(6.dp),
                        color = if (isSelected) primaryColor else theme.cardSurface,
                        border = BorderStroke(0.8.dp, if (isSelected) primaryColor else theme.secondarySurface),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(type.icon, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                type.label,
                                color = if (isSelected) DeepSlate else TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. عداد الملفات والإحصائيات السريعة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "إجمالي العناصر: ${filteredAssets.size} عنصر",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )

                Text(
                    text = cloudStatus,
                    color = primaryColor.copy(alpha = 0.8f),
                    fontFamily = CairoFont,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. شبكة أو قائمة الوسائط
            if (filteredAssets.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.cardSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = theme.textTertiary, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            "لا توجد وسائط مطابقة للبحث أو التصفية",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "قم بتوليد صور FLUX في الاستوديو أو ارفع وسائط من جهازك 📥",
                            color = theme.textTertiary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredAssets, key = { it.id }) { asset ->
                        CentralMediaAssetCard(
                            asset = asset,
                            isGridView = isGridView,
                            primaryColor = primaryColor,
                            onClick = { selectedAssetForDetail = asset }
                        )
                    }
                }
            }
        }
    }

    // نافذة تفاصيل ومعاينة وإدارة العنصر
    selectedAssetForDetail?.let { asset ->
        MediaAssetDetailDialog(
            asset = asset,
            onDismiss = { selectedAssetForDetail = null },
            onUploadToSupabase = {
                scope.launch {
                    val ok = CentralMediaManager.uploadAssetToSupabase(context, asset)
                    if (ok) {
                        Toast.makeText(context, "تم الرفع بنجاح إلى Supabase Storage ☁️✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "تعذر الرفع — تحقق من إعدادات Supabase", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                scope.launch {
                    val ok = CentralMediaManager.deleteAsset(context, asset)
                    selectedAssetForDetail = null
                    Toast.makeText(
                        context,
                        if (ok) "تم حذف الملف بنجاح 🗑️" else "تعذّر حذف الملف — تحقق من الاتصال وحاول مجدداً",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onOpenInPhotoStudio = {
                selectedAssetForDetail = null
                onOpenInPhotoStudio(asset.localPath ?: asset.remoteUrl ?: "")
            },
            onOpenInVideoEditor = {
                selectedAssetForDetail = null
                onOpenInVideoEditor(asset.localPath ?: asset.remoteUrl ?: "")
            },
            onCopyLink = {
                val link = asset.remoteUrl ?: asset.localPath ?: ""
                clipboardManager.setText(AnnotatedString(link))
                Toast.makeText(context, "تم نسخ الرابط للحافظة 📋", Toast.LENGTH_SHORT).show()
            },
            primaryColor = primaryColor,
            accentColor = accentColor
        )
    }
}

/**
 * بطاقة عرض العنصر في الشبكة أو القائمة
 */
@Composable
private fun CentralMediaAssetCard(
    asset: CentralMediaAsset,
    isGridView: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val badgeColor = remember(asset.source) {
        when (asset.source) {
            CentralMediaSource.SUPABASE_CLOUD -> theme.success
            CentralMediaSource.AI_FLUX -> theme.warning
            CentralMediaSource.AI_VOICE -> theme.accent
            CentralMediaSource.LOCAL_RENDER -> theme.primary
            CentralMediaSource.USER_UPLOAD -> theme.accent
        }
    }

    Surface(
        onClick = onClick,
        shape = CutCornerShape(10.dp),
        color = theme.background,
        border = BorderStroke(0.8.dp, theme.secondarySurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isGridView) {
            Column {
                // Media Thumbnail Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(theme.background),
                    contentAlignment = Alignment.Center
                ) {
                    when (asset.type) {
                        CentralMediaType.IMAGE -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(asset.displayUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = asset.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        CentralMediaType.VIDEO -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(asset.displayUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = asset.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(theme.background.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                            }
                        }
                        CentralMediaType.AUDIO -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = theme.accent, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("ملف صوتي", color = theme.textTertiary, fontSize = 10.sp, fontFamily = CairoFont)
                            }
                        }
                        else -> {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Cloud / Local Status Top Badge
                    Surface(
                        color = if (asset.isUploadedToCloud) theme.success.copy(alpha = 0.85f) else theme.background.copy(alpha = 0.7f),
                        shape = CutCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (asset.isUploadedToCloud) Icons.Default.CloudDone else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = theme.textPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                if (asset.isUploadedToCloud) "Supabase" else "Local",
                                color = theme.textPrimary,
                                fontFamily = RobotoMonoFont,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Metadata Footer
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = asset.title,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = CutCornerShape(3.dp),
                            border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = asset.source.label,
                                color = badgeColor,
                                fontFamily = CairoFont,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Text(
                            text = asset.formattedSize,
                            color = theme.textSecondary,
                            fontFamily = RobotoMonoFont,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        } else {
            // List View Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CutCornerShape(6.dp))
                        .background(theme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (asset.type == CentralMediaType.IMAGE || asset.type == CentralMediaType.VIDEO) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(asset.displayUrl).crossfade(true).build(),
                            contentDescription = asset.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = theme.accent, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.title,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${asset.source.label} • ${asset.formattedSize} • ${asset.formattedDate}",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                }

                Surface(
                    color = if (asset.isUploadedToCloud) theme.success.copy(alpha = 0.12f) else theme.secondarySurface,
                    shape = CutCornerShape(4.dp)
                ) {
                    Text(
                        if (asset.isUploadedToCloud) "سحابي ✓" else "محلي",
                        color = if (asset.isUploadedToCloud) theme.success else theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * نافذة حوار تفصيلية لمعاينة الملف والتحكم وإجراء العمليات السحابية والمحلية
 */
@Composable
private fun MediaAssetDetailDialog(
    asset: CentralMediaAsset,
    onDismiss: () -> Unit,
    onUploadToSupabase: () -> Unit,
    onDelete: () -> Unit,
    onOpenInPhotoStudio: () -> Unit,
    onOpenInVideoEditor: () -> Unit,
    onCopyLink: () -> Unit,
    primaryColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            backgroundColor = theme.background.copy(alpha = 0.96f),
            borderColor = primaryColor.copy(alpha = 0.6f),
            shape = CutCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تفاصيل وتحكم بالوسائط",
                        color = primaryColor,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textTertiary)
                    }
                }

                // Media Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(theme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (asset.type == CentralMediaType.IMAGE || asset.type == CentralMediaType.VIDEO) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(asset.displayUrl).crossfade(true).build(),
                            contentDescription = asset.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = theme.accent, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("مسار صوتي نقي", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }
                }

                // Metadata Details Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(6.dp),
                    color = theme.cardSurface,
                    border = BorderStroke(0.6.dp, theme.secondarySurface)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الاسم: ${asset.title}", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("المصدر: ${asset.source.label}", color = theme.primary, fontFamily = CairoFont, fontSize = 11.sp)
                        Text("الحجم: ${asset.formattedSize} • النوع: ${asset.mimeType}", color = theme.textTertiary, fontFamily = RobotoMonoFont, fontSize = 10.sp)
                        if (asset.bucketName != null) {
                            Text("حاوية السحابة (Bucket): ${asset.bucketName}", color = theme.success, fontFamily = RobotoMonoFont, fontSize = 10.sp)
                        }
                    }
                }

                // Action Buttons Grid
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. استخدام في محرر الفيديو
                    if (asset.type == CentralMediaType.VIDEO || asset.type == CentralMediaType.IMAGE) {
                        Button(
                            onClick = onOpenInVideoEditor,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = CutCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فتح في محرر ومونتاج الفيديو 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // 2. استخدام في استوديو الصور
                    if (asset.type == CentralMediaType.IMAGE) {
                        OutlinedButton(
                            onClick = onOpenInPhotoStudio,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = CutCornerShape(6.dp),
                            border = BorderStroke(1.dp, theme.warning),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = theme.warning.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = theme.warning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تعديل في استوديو الصور والبوسترات 🎨", color = theme.warning, fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }

                    // 3. رفع إلى Supabase Storage
                    if (!asset.isUploadedToCloud && asset.localPath != null && SupabaseServices.isSupabaseAvailable) {
                        Button(
                            onClick = onUploadToSupabase,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = CutCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.success)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = theme.textPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("رفع ومزامنة في Supabase Storage ☁️", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // 4. أزرار إضافية (نسخ الرابط + الحذف)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCopyLink,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = CutCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, theme.primary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ الرابط", color = theme.primary, fontFamily = CairoFont, fontSize = 11.sp)
                        }

                        Button(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = CutCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.destructive)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = theme.textPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف الملف", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
