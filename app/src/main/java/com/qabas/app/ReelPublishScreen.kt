package com.qabas.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReelPublishScreen(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String?,
    onDismiss: () -> Unit,
    onPublishedSuccess: () -> Unit,
    onRequireLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val verifiedRole by SupabaseServices.Auth.verifiedRole.collectAsState()
    val isDeveloper = remember(currentUserEmail, verifiedRole) {
        ReelPublishManager.isDeveloperUser(context, currentUserEmail)
    }

    val publishStatus = remember(currentUserEmail, verifiedRole) {
        ReelPublishManager.checkPublishEligibility(context, currentUserEmail)
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("قرآن كريم") }
    var isFeaturedByDev by remember { mutableStateOf(false) }
    var isSilentSeed by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf("9:16") }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoThumbnailFile by remember { mutableStateOf<File?>(null) }
    var selectedVideoThumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedVideoDurationMs by remember { mutableStateOf(0L) }
    var directVideoUrl by remember { mutableStateOf("") }
    var showDirectUrlInput by remember { mutableStateOf(false) }

    var hasAcceptedPledge by remember { mutableStateOf(false) }
    var spiritualViolationMessage by remember { mutableStateOf<String?>(null) }
    var spiritualViolationAyah by remember { mutableStateOf<String?>(null) }

    var isPublishing by remember { mutableStateOf(false) }
    var publishProgressText by remember { mutableStateOf("جاري النشر...") }
    var generalErrorMessage by remember { mutableStateOf<String?>(null) }
    var uploadSentBytes by remember { mutableStateOf(0L) }
    var uploadTotalBytes by remember { mutableStateOf(0L) }
    var uploadSpeedBps by remember { mutableStateOf(0L) }
    var uploadEtaSeconds by remember { mutableStateOf(0L) }

    // Scheduling & quality options
    var publishAt by remember { mutableStateOf<String?>(null) }
    var publishScheduleLabel by remember { mutableStateOf("الآن (نشر فوري)") }
    var showSchedulePickerDialog by remember { mutableStateOf(false) }
    var expiryAt by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf("high") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Pre-Publish Optimization States
    var isOptimizationExpanded by remember { mutableStateOf(false) }
    var targetResolution by remember { mutableStateOf("1080p") }
    var isSmartCompressEnabled by remember { mutableStateOf(true) }
    var isAudioNormalizeEnabled by remember { mutableStateOf(true) }
    var isSilenceTrimEnabled by remember { mutableStateOf(true) }
    var isSmartThumbEnabled by remember { mutableStateOf(true) }

    val categories = listOf("قرآن كريم", "أحاديث نبوية", "وعظة إيمانية")

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            directVideoUrl = ""
            generalErrorMessage = null
            scope.launch {
                val metadata = SpiritualSafetyGuard.extractVideoMetadata(context, uri)
                selectedVideoDurationMs = metadata.durationMs
                selectedVideoThumbnailFile = metadata.thumbnailFile
                selectedVideoThumbnailBytes = metadata.thumbnailBytes
            }
        }
    }

    LaunchedEffect(title, description, selectedCategory, selectedVideoThumbnailFile) {
        if (title.isNotBlank() || description.isNotBlank()) {
            val textAudit = SpiritualSafetyGuard.performSpiritualAudit(
                title = title,
                description = description,
                tags = listOf(selectedCategory),
                category = selectedCategory
            )
            if (textAudit is SpiritualSafetyGuard.ModerationResult.Rejected) {
                spiritualViolationMessage = textAudit.reason
                spiritualViolationAyah = textAudit.detailedAyah
                return@LaunchedEffect
            }

            val thumb = selectedVideoThumbnailFile
            if (thumb != null && thumb.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(thumb.absolutePath)
                if (bitmap != null) {
                    val visualAudit = SpiritualSafetyGuard.performVisualSpiritualAudit(bitmap, title, description)
                    if (visualAudit is SpiritualSafetyGuard.ModerationResult.Rejected) {
                        spiritualViolationMessage = visualAudit.reason
                        spiritualViolationAyah = visualAudit.detailedAyah
                        return@LaunchedEffect
                    }
                }
            }

            spiritualViolationMessage = null
            spiritualViolationAyah = null
        } else {
            spiritualViolationMessage = null
            spiritualViolationAyah = null
        }
    }

    LaunchedEffect(isPublishing, uploadSentBytes, uploadTotalBytes) {
        if (!isPublishing || uploadTotalBytes <= 0) return@LaunchedEffect
        val prevBytes = uploadSentBytes
        val prevTime = System.currentTimeMillis()
        delay(1000L)
        val deltaBytes = uploadSentBytes - prevBytes
        val deltaTime = System.currentTimeMillis() - prevTime
        if (deltaTime > 0 && deltaBytes > 0) {
            uploadSpeedBps = deltaBytes * 1000 / deltaTime
            val remaining = uploadTotalBytes - uploadSentBytes
            uploadEtaSeconds = if (uploadSpeedBps > 0) remaining / uploadSpeedBps else 0L
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = theme.primary,
        unfocusedBorderColor = theme.separator.copy(alpha = 0.55f),
        focusedTextColor = theme.textPrimary,
        unfocusedTextColor = theme.textPrimary,
        focusedLabelColor = theme.primary,
        unfocusedLabelColor = theme.textSecondary,
        focusedContainerColor = theme.secondarySurface.copy(alpha = 0.5f),
        unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.35f)
    )

    Scaffold(
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppleTopBar(
                title = if (isDeveloper) "نشر ريلز إيماني" else "نشر ريلز إيماني مبارك",
                subtitle = if (isDeveloper) "صلاحيات المطور المعتمدة" else "مشاركة تلاوة أو موعظة لتكون صدقة جارية",
                actions = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = theme.textSecondary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Account eligibility / status card
            if (isDeveloper) {
                AppleCard(containerColor = theme.primary.copy(alpha = if (theme.isDark) 0.14f else 0.08f)) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حساب مطور معتمد",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = theme.primary
                            )
                            Text(
                                text = "نشر فوري بدون قيود أو فترات انتظار مع خيارات الجدولة والتثبيت.",
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }
            } else {
                when (publishStatus) {
                    is ReelPublishManager.PublishStatus.Allowed -> {
                        AppleCard {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "الحصة اليومية: ${ReelPublishManager.DAILY_LIMIT_REGULAR} مقاطع • المتبقي اليوم: ${publishStatus.remainingToday}",
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }
                    }
                    is ReelPublishManager.PublishStatus.DailyLimitExceeded -> {
                        AppleCard(containerColor = theme.destructive.copy(alpha = 0.12f)) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = theme.destructive, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "بلغت الحد الأقصى للنشر اليومي (${publishStatus.maxLimit} مقاطع). تتجدد الحصة غداً بإذن الله.",
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    color = theme.destructive
                                )
                            }
                        }
                    }
                    is ReelPublishManager.PublishStatus.CooldownActive -> {
                        AppleCard(containerColor = theme.warning.copy(alpha = 0.12f)) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = theme.warning, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "فترة تهدئة بين المقاطع: يرجى الانتظار ${publishStatus.remainingMinutes} دقيقة قبل النشر التالي.",
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    color = theme.warning
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            // 2. Video selection
            AppleGroupedSection(header = "1. اختيار مقطع الفيديو") {
                if (selectedVideoUri != null) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp, 96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedVideoThumbnailFile != null && selectedVideoThumbnailFile!!.exists()) {
                                AsyncImage(
                                    model = selectedVideoThumbnailFile,
                                    contentDescription = "غلاف الفيديو",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تم اختيار الفيديو بنجاح",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = theme.success
                                )
                            }
                            if (selectedVideoDurationMs > 0) {
                                val sec = (selectedVideoDurationMs / 1000).toInt()
                                Text(
                                    text = "المدة: ${sec / 60}:${"%02d".format(sec % 60)}",
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 12.sp,
                                    color = theme.textSecondary
                                )
                            }
                            Text(
                                text = "سيتم رفعه وتأمينه داخل المنصة",
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    isAnalyzing = true
                                    scope.launch {
                                        val analysis = ReelPublishManager.analyzeVideoWithAI(context, selectedVideoUri!!)
                                        if (analysis != null) {
                                            title = analysis.first
                                            description = analysis.second
                                            Toast.makeText(context, "تم استخراج العنوان والوصف بنجاح", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "تعذر تحليل المقطع سحابياً حالياً", Toast.LENGTH_SHORT).show()
                                        }
                                        isAnalyzing = false
                                    }
                                },
                                enabled = !isAnalyzing,
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = theme.primary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("اقتراح عنوان ووصف", fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تغيير الفيديو", tint = theme.primary)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = theme.primary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "اختر مقطع فيديو من جهازك",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "يفتح منتقي الوسائط الآمن لاختيار التلاوة أو الموعظة مباشرة",
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    AppleInsetDivider()

                    AppleListRow(
                        title = if (showDirectUrlInput) "إخفاء خيار الرابط المباشر" else "أو إدخال رابط فيديو مباشر",
                        icon = if (showDirectUrlInput) Icons.Default.ExpandLess else Icons.Default.Link,
                        iconBackground = theme.secondarySurface,
                        iconTint = theme.primary,
                        showChevron = false,
                        onClick = { showDirectUrlInput = !showDirectUrlInput }
                    )

                    if (showDirectUrlInput) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            OutlinedTextField(
                                value = directVideoUrl,
                                onValueChange = {
                                    directVideoUrl = it
                                    selectedVideoUri = null
                                    generalErrorMessage = null
                                },
                                label = { Text("رابط الفيديو المباشر (HTTPS / MP4)", fontFamily = CairoFont, fontSize = 12.sp) },
                                placeholder = { Text("https://example.com/video.mp4", fontFamily = RobotoMonoFont, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                        }
                    }
                }
            }

            // 3. Aspect Ratio
            AppleGroupedSection(header = "2. أبعاد العرض (Aspect Ratio)") {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppleSegmentedControl(
                        items = listOf(
                            "9:16" to "طولي 9:16",
                            "1:1" to "مربع 1:1",
                            "16:9" to "عرضي 16:9"
                        ),
                        selectedItem = aspectRatio,
                        onSelect = { aspectRatio = it }
                    )
                }
            }

            // 4. Category
            AppleGroupedSection(header = "3. التصنيف") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) theme.primary else theme.secondarySurface,
                            border = BorderStroke(
                                width = if (isSelected) 0.dp else 0.5.dp,
                                color = theme.separator.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // 5. Details
            AppleGroupedSection(header = "4. تفاصيل المقطع") {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            generalErrorMessage = null
                        },
                        label = { Text("عنوان المقطع *", fontFamily = CairoFont, fontSize = 12.5.sp) },
                        placeholder = { Text("مثال: تلاوة خاشعة من سورة الرحمن", fontFamily = NotoSansFont, fontSize = 12.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            generalErrorMessage = null
                        },
                        label = { Text("الوصف أو نص الآية الكريمة (اختياري)", fontFamily = CairoFont, fontSize = 12.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                }
            }

            // 6. Developer publishing tools (with working Schedule Picker)
            if (isDeveloper) {
                AppleGroupedSection(
                    header = "خيارات المطور والنشر المتقدمة",
                    footer = "يمكنك أدناه الاختيار بين النشر العام للمجتمع أو الحقن الصامت لمشغّل الأثر."
                ) {
                    AppleListRow(
                        title = "تثبيت في صدارة المجتمع",
                        subtitle = "إبراز المقطع وزيادة أولوية ظهوره في الخلاصة",
                        icon = Icons.Default.PushPin,
                        iconBackground = theme.primary,
                        showChevron = false,
                        onClick = { isFeaturedByDev = !isFeaturedByDev },
                        trailingContent = {
                            Switch(
                                checked = isFeaturedByDev,
                                onCheckedChange = { isFeaturedByDev = it }
                            )
                        }
                    )

                    AppleInsetDivider()

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "جودة الفيديو (وضع الأثر)",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = theme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppleSegmentedControl(
                            items = listOf("high" to "جودة عالية", "low" to "حجم خفيف"),
                            selectedItem = quality,
                            onSelect = { quality = it }
                        )
                    }

                    AppleInsetDivider()

                    AppleListRow(
                        title = "جدولة النشر",
                        subtitle = publishAt ?: "يُنشر مباشرة فور إتمام الرفع",
                        trailingText = publishScheduleLabel,
                        icon = Icons.Default.Event,
                        iconBackground = theme.accent,
                        showChevron = true,
                        onClick = { showSchedulePickerDialog = true }
                    )
                }
            }

            // 7. Pre-Publish Optimization
            AppleGroupedSection(header = "تحسين الجودة والبيانات الوصفية") {
                AppleListRow(
                    title = "إعدادات ما قبل النشر",
                    subtitle = "$targetResolution • ${if (isSmartCompressEnabled) "ضغط متوازن" else "بدون ضغط"}",
                    icon = Icons.Default.HighQuality,
                    iconBackground = theme.primary,
                    showChevron = false,
                    onClick = { isOptimizationExpanded = !isOptimizationExpanded },
                    trailingContent = {
                        Icon(
                            imageVector = if (isOptimizationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = theme.textSecondary
                        )
                    }
                )

                AnimatedVisibility(
                    visible = isOptimizationExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppleInsetDivider()
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = "دقة العرض المستهدفة",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = theme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppleSegmentedControl(
                                items = listOf("1080p" to "1080p Full HD", "720p" to "720p HD"),
                                selectedItem = targetResolution,
                                onSelect = { targetResolution = it }
                            )
                        }

                        AppleInsetDivider()
                        AppleListRow(
                            title = "الضغط التلقائي المتوازن",
                            subtitle = "تقليل حجم الملف مع الحفاظ على نقاء الصورة",
                            showChevron = false,
                            onClick = { isSmartCompressEnabled = !isSmartCompressEnabled },
                            trailingContent = {
                                Switch(
                                    checked = isSmartCompressEnabled,
                                    onCheckedChange = { isSmartCompressEnabled = it }
                                )
                            }
                        )

                        AppleInsetDivider()
                        AppleListRow(
                            title = "موازنة حدة الصوت وتقليل الضوضاء",
                            subtitle = "توحيد مستوى صوت التلاوة لمختلف السماعات",
                            showChevron = false,
                            onClick = { isAudioNormalizeEnabled = !isAudioNormalizeEnabled },
                            trailingContent = {
                                Switch(
                                    checked = isAudioNormalizeEnabled,
                                    onCheckedChange = { isAudioNormalizeEnabled = it }
                                )
                            }
                        )

                        AppleInsetDivider()
                        AppleListRow(
                            title = "تقليم السكون في البداية والنهاية",
                            showChevron = false,
                            onClick = { isSilenceTrimEnabled = !isSilenceTrimEnabled },
                            trailingContent = {
                                Switch(
                                    checked = isSilenceTrimEnabled,
                                    onCheckedChange = { isSilenceTrimEnabled = it }
                                )
                            }
                        )

                        AppleInsetDivider()
                        AppleListRow(
                            title = "التقاط غلاف مصغر تلقائي",
                            showChevron = false,
                            onClick = { isSmartThumbEnabled = !isSmartThumbEnabled },
                            trailingContent = {
                                Switch(
                                    checked = isSmartThumbEnabled,
                                    onCheckedChange = { isSmartThumbEnabled = it }
                                )
                            }
                        )

                        AppleInsetDivider()
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            AppleSecondaryButton(
                                text = "إضافة الوسوم الإيمانية إلى الوصف",
                                icon = Icons.Default.AutoAwesome,
                                onClick = {
                                    val smartTags = when (selectedCategory) {
                                        "قرآن كريم" -> "\n#قبس_الإيماني #تلاوات_خاشعة #قرآن_كريم #تلاوة_مباركة"
                                        "أحاديث نبوية" -> "\n#قبس_الإيماني #أحاديث_نبوية #سنة_نبوية #أخلاق_إسلامية"
                                        else -> "\n#قبس_الإيماني #وعظة_إيمانية #ذكر_الله #صدقة_جارية"
                                    }
                                    if (!description.contains("#قبس_الإيماني")) {
                                        description = (description.trim() + " " + smartTags).trim()
                                    }
                                    Toast.makeText(context, "تم إضافة الوسوم الإيمانية إلى الوصف", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // 8. Spiritual moderation & pledge
            val statusTint = if (spiritualViolationMessage != null) theme.destructive else theme.success
            AppleCard(containerColor = statusTint.copy(alpha = if (theme.isDark) 0.14f else 0.08f)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (spiritualViolationMessage != null) Icons.Default.Block else Icons.Default.Verified,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (spiritualViolationMessage != null) "رصد مخالفة شرعية - يمنع النشر" else "الرقابة والضوابط الشرعية",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = statusTint,
                            modifier = Modifier.weight(1f)
                        )
                        com.qabas.app.ui.components.ModelIndicator()
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (spiritualViolationMessage != null) {
                        Text(
                            text = spiritualViolationMessage!!,
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            color = theme.textPrimary,
                            lineHeight = 19.sp
                        )
                        if (!spiritualViolationAyah.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = spiritualViolationAyah!!,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                color = theme.primary,
                                lineHeight = 18.sp
                            )
                        }
                    } else {
                        Text(
                            text = "يُحظر نشر أي محتوى يتضمن الموسيقى أو المعازف أو ما يخالف الضوابط الشرعية.",
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { hasAcceptedPledge = !hasAcceptedPledge }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasAcceptedPledge,
                            onCheckedChange = { hasAcceptedPledge = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = theme.primary,
                                checkmarkColor = theme.onPrimary,
                                uncheckedColor = theme.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "أتعهد أمام الله بأن المقطع خالٍ من الموسيقى والمعازف ومن أي مخالفة شرعية.",
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = if (hasAcceptedPledge) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasAcceptedPledge) theme.primary else theme.textPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (generalErrorMessage != null) {
                AppleCard(containerColor = theme.destructive.copy(alpha = 0.12f)) {
                    Text(
                        text = generalErrorMessage!!,
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        color = theme.destructive,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            val canPublish = !isPublishing &&
                spiritualViolationMessage == null &&
                hasAcceptedPledge &&
                title.isNotBlank() &&
                (selectedVideoUri != null || directVideoUrl.isNotBlank()) &&
                (isDeveloper || publishStatus is ReelPublishManager.PublishStatus.Allowed)

            val doPublishAction: (Boolean) -> Unit = { asSilent ->
                val authState = SupabaseRepository.getInstance().authState.value
                val authId = authState.authenticatedSessionSnapshot()?.userId
                if (authId.isNullOrBlank()) {
                    if (authState is AuthState.Guest || authState is AuthState.Unauthenticated) {
                        Toast.makeText(context, "يجب تسجيل الدخول أولاً لتتمكن من نشر الريلز", Toast.LENGTH_LONG).show()
                        onDismiss()
                        onRequireLogin()
                    } else {
                        Toast.makeText(context, "تعذر قراءة حالة الحساب مؤقتاً. حاول مرة أخرى.", Toast.LENGTH_LONG).show()
                    }
                } else if (authId != currentUserId) {
                    Toast.makeText(context, "تغيرت هوية الحساب؛ أعد فتح شاشة النشر.", Toast.LENGTH_LONG).show()
                    onDismiss()
                } else {
                    val validation = ReelPublishManager.validateContent(
                        title = title,
                        description = description,
                        category = selectedCategory,
                        videoUriOrUrl = selectedVideoUri?.toString() ?: directVideoUrl,
                        hasAcceptedPledge = hasAcceptedPledge,
                        isDeveloper = isDeveloper,
                        durationSeconds = (selectedVideoDurationMs / 1000).toInt()
                    )
                    when (validation) {
                        is ReelPublishManager.ValidationResult.Invalid -> {
                            generalErrorMessage = validation.reason
                        }
                        is ReelPublishManager.ValidationResult.Valid -> {
                            isPublishing = true
                            isSilentSeed = asSilent
                            generalErrorMessage = null
                            uploadSentBytes = 0L
                            uploadTotalBytes = 0L
                            uploadSpeedBps = 0L
                            uploadEtaSeconds = 0L
                            scope.launch {
                                val result = ReelPublishManager.publishReelWithUpload(
                                    context = context,
                                    userId = authId,
                                    userName = currentUserName,
                                    email = currentUserEmail,
                                    title = title.trim(),
                                    description = description.takeIf { it.isNotBlank() },
                                    videoUri = selectedVideoUri,
                                    directUrl = directVideoUrl.takeIf { it.isNotBlank() },
                                    thumbnailBytes = selectedVideoThumbnailBytes,
                                    category = selectedCategory,
                                    isFeaturedByDev = isFeaturedByDev,
                                    isSilentSeed = asSilent,
                                    publishAt = publishAt,
                                    expiryAt = expiryAt,
                                    quality = quality,
                                    aspectRatio = aspectRatio,
                                    onProgressUpdate = { progressText ->
                                        publishProgressText = progressText
                                    },
                                    onBytesProgress = { sent, total, _ ->
                                        uploadSentBytes = sent
                                        uploadTotalBytes = total
                                    }
                                )
                                isPublishing = false
                                when (result) {
                                    is ReelPublishManager.PublishResult.Success -> {
                                        val toastMsg = if (asSilent) {
                                            "تم حقن مقطع الفيديو الصامت للأوفلاين بنجاح!"
                                        } else {
                                            "تم نشر المقطع للمجتمع بنجاح!"
                                        }
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                        onPublishedSuccess()
                                        onDismiss()
                                    }
                                    is ReelPublishManager.PublishResult.Failed -> {
                                        android.util.Log.e("ReelPublish", "Publish failed [${result.stage}]: ${result.devMessage}")
                                        generalErrorMessage = "فشل النشر: ${result.devMessage}"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isDeveloper) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ApplePrimaryButton(
                        text = if (isPublishing && !isSilentSeed) publishProgressText else "نشر ريلز للمجتمع",
                        icon = Icons.Default.CloudUpload,
                        enabled = canPublish,
                        isLoading = isPublishing && !isSilentSeed,
                        onClick = { doPublishAction(false) }
                    )

                    AppleSecondaryButton(
                        text = if (isPublishing && isSilentSeed) publishProgressText else "نشر فيديو صامت لوضع الأثر",
                        icon = Icons.Default.CloudDownload,
                        enabled = canPublish,
                        onClick = { doPublishAction(true) }
                    )
                }
            } else {
                ApplePrimaryButton(
                    text = if (isPublishing) publishProgressText else "نشر المقطع",
                    icon = Icons.Default.CloudUpload,
                    enabled = canPublish,
                    isLoading = isPublishing,
                    onClick = { doPublishAction(false) }
                )
            }

            if (isPublishing && uploadTotalBytes > 0) {
                AppleCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = publishProgressText.ifBlank { "جاري الرفع..." },
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "${(uploadSentBytes.toFloat() / uploadTotalBytes * 100).toInt()}%",
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = theme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (uploadSentBytes.toFloat() / uploadTotalBytes).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = theme.primary,
                            trackColor = theme.separator.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${StorageMetrics.formatBytes(uploadSentBytes)} / ${StorageMetrics.formatBytes(uploadTotalBytes)}",
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
                            )
                            if (uploadSpeedBps > 0) {
                                Text(
                                    text = "${StorageMetrics.formatBytes(uploadSpeedBps)}/s",
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.5.sp,
                                    color = theme.success
                                )
                            }
                        }
                        if (uploadEtaSeconds > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الوقت المتبقي: ${uploadEtaSeconds / 60}:${"%02d".format(uploadEtaSeconds % 60)}",
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.5.sp,
                                color = theme.warning
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showSchedulePickerDialog) {
        val zone = remember { ZoneId.systemDefault() }
        val options = remember {
            val now = Instant.now()
            val tomorrowMorning = ZonedDateTime.of(LocalDate.now(zone).plusDays(1), LocalTime.of(8, 0), zone).toInstant()
            val tomorrowEvening = ZonedDateTime.of(LocalDate.now(zone).plusDays(1), LocalTime.of(19, 0), zone).toInstant()
            listOf(
                null to "الآن (نشر فوري)",
                now.plus(1, ChronoUnit.HOURS).toString() to "بعد ساعة واحدة",
                now.plus(3, ChronoUnit.HOURS).toString() to "بعد 3 ساعات",
                tomorrowMorning.toString() to "غداً صباحاً (08:00)",
                tomorrowEvening.toString() to "غداً مساءً (19:00)"
            )
        }
        AlertDialog(
            onDismissRequest = { showSchedulePickerDialog = false },
            containerColor = theme.cardSurface,
            titleContentColor = theme.textPrimary,
            textContentColor = theme.textSecondary,
            title = {
                Text(
                    text = "جدولة موعد النشر",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (isoValue, label) ->
                        val selected = (publishAt == null && isoValue == null) || publishScheduleLabel == label
                        Surface(
                            onClick = {
                                publishAt = isoValue
                                publishScheduleLabel = label
                                showSchedulePickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                            border = BorderStroke(
                                width = if (selected) 1.dp else 0.5.dp,
                                color = if (selected) theme.primary else theme.separator.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontFamily = CairoFont,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = theme.textPrimary
                                    )
                                    if (isoValue != null) {
                                        Text(
                                            text = isoValue,
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 10.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSchedulePickerDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إغلاق", fontFamily = CairoFont, color = theme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
