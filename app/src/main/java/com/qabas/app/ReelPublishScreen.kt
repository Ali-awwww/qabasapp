package com.qabas.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReelPublishScreen(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String?,
    onDismiss: () -> Unit,
    onPublishedSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val isDeveloper = remember(currentUserEmail) {
        ReelPublishManager.isDeveloperUser(context, currentUserEmail)
    }

    val publishStatus = remember(currentUserEmail) {
        ReelPublishManager.checkPublishEligibility(context, currentUserEmail)
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("قرآن كريم") }
    var isFeaturedByDev by remember { mutableStateOf(false) }
    var isSilentSeed by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf("9:16") } // "9:16", "1:1", "16:9"

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
    
    // New Advanced States
    var publishAt by remember { mutableStateOf<String?>(null) }
    var expiryAt by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf("high") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Pre-Publish Optimization States (إعدادات تحسين جودة ما قبل النشر)
    var isOptimizationExpanded by remember { mutableStateOf(false) }
    var targetResolution by remember { mutableStateOf("1080p") } // "1080p" or "720p"
    var isSmartCompressEnabled by remember { mutableStateOf(true) }
    var isAudioNormalizeEnabled by remember { mutableStateOf(true) }
    var isSilenceTrimEnabled by remember { mutableStateOf(true) }
    var isSmartThumbEnabled by remember { mutableStateOf(true) }
    var isAiHashtagsEnabled by remember { mutableStateOf(true) }

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
        if (title.isNotBlank() || !description.isNullOrBlank()) {
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

            // إذا تم اختيار فيديو، نقوم بالفحص البصري أيضاً
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

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDeveloper) Icons.Default.VerifiedUser else Icons.Default.VideoCall,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isDeveloper) "نشر ريلز إيماني (صلاحيات المطور)" else "نشر ريلز إيماني مبارك",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "مشاركة تلاوة أو موعظة أو ذكر لتكون صدقة جارية",
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            if (isDeveloper) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "حساب مطور معتمد (Developer Account)",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = GoldPrimary
                            )
                            Text(
                                text = "نشر فوري بدون قيود أو فترات انتظار، مع إمكانية التثبيت في صدارة المنصة.",
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                when (publishStatus) {
                    is ReelPublishManager.PublishStatus.Allowed -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "الحصة اليومية: ${ReelPublishManager.DAILY_LIMIT_REGULAR} مقاطع | المتبقي لك اليوم: ${publishStatus.remainingToday} مقطع",
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    is ReelPublishManager.PublishStatus.DailyLimitExceeded -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "بلغت الحد الأقصى للنشر اليومي (3 مقاطع). سيتم تجديد الحصة غداً بمشيئة الله.",
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                    is ReelPublishManager.PublishStatus.CooldownActive -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "فترة تهدئة بين المقاطع: يرجى الانتظار ${publishStatus.remainingMinutes} دقيقة قبل النشر التالي.",
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "1. اختيار مقطع الفيديو",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedVideoUri != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp, 90.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تم اختيار الفيديو بنجاح",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF10B981)
                                )
                            }
                            if (selectedVideoDurationMs > 0) {
                                val sec = (selectedVideoDurationMs / 1000).toInt()
                                Text(
                                    text = "المدة: ${sec / 60}:${"%02d".format(sec % 60)}",
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = "سيتم رفعه وتأمينه تلقائياً داخل المنصة",
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            
                            // 🤖 زر تحليل الفيديو بالذكاء الاصطناعي
                            Button(
                                onClick = {
                                    isAnalyzing = true
                                    scope.launch {
                                        val analysis = ReelPublishManager.analyzeVideoWithAI(context, selectedVideoUri!!)
                                        if (analysis != null) {
                                            title = analysis.first
                                            description = analysis.second
                                            Toast.makeText(context, "تم توليد العنوان والوصف بذكاء! ✨", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "تعذر تحليل المقطع حالياً", Toast.LENGTH_SHORT).show()
                                        }
                                        isAnalyzing = false
                                    }
                                },
                                enabled = !isAnalyzing,
                                modifier = Modifier.padding(top = 6.dp).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary.copy(alpha = 0.15f))
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = GoldPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحليل وتفريغ AI", fontFamily = CairoFont, fontSize = 10.sp, color = GoldPrimary)
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تغيير", tint = GoldPrimary)
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "اختر مقطع فيديو من هاتفك",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "انقر لفتح معرض الفيديوهات واختيار التلاوة أو المقطع مباشرة",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDirectUrlInput = !showDirectUrlInput }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (showDirectUrlInput) Icons.Default.ExpandLess else Icons.Default.Link,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showDirectUrlInput) "إخفاء خيار الرابط المباشر" else "أو إدخال رابط فيديو مباشر",
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                if (showDirectUrlInput) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = directVideoUrl,
                        onValueChange = {
                            directVideoUrl = it
                            selectedVideoUri = null
                            generalErrorMessage = null
                        },
                        label = { Text("رابط الفيديو المباشر (HTTPS / MP4)", fontFamily = CairoFont, fontSize = 11.5.sp) },
                        placeholder = { Text("https://example.com/video.mp4", fontFamily = RobotoMonoFont, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = GoldPrimary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "2. أبعاد الفيديو (Aspect Ratio)",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("9:16", "طولي", Icons.Default.StayCurrentPortrait),
                    Triple("1:1", "مربع", Icons.Default.CropSquare),
                    Triple("16:9", "عرضي", Icons.Default.CropLandscape)
                ).forEach { (ratio, label, icon) ->
                    val isSelected = aspectRatio == ratio
                    Surface(
                        onClick = { aspectRatio = ratio },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.2.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = label,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "3. التصنيف",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) DeepSlate else Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "4. تفاصيل المقطع",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            generalErrorMessage = null
                        },
                        label = { Text("عنوان المقطع * (مثال: تلاوة خاشعة للشيخ...)", fontFamily = CairoFont, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = GoldPrimary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            generalErrorMessage = null
                        },
                        label = { Text("الوصف أو نص الآية الكريمة (اختياري)", fontFamily = CairoFont, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = GoldPrimary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            if (isDeveloper) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFeaturedByDev = !isFeaturedByDev }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تثبيت المقطع في صدارة المجتمع (Featured Reel)",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "إضافة شارة النجمة الذهبية ومضاعفة أولوية العرض",
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Switch(
                            checked = isFeaturedByDev,
                            onCheckedChange = { isFeaturedByDev = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepSlate,
                                checkedTrackColor = GoldPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("جودة الفيديو (وضع الأثر)", fontFamily = CairoFont, fontSize = 12.sp, color = Color.White)
                        Row {
                            TextButton(onClick = { quality = "low" }, colors = ButtonDefaults.textButtonColors(contentColor = if (quality == "low") GoldPrimary else Color.Gray)) { Text("منخفضة") }
                            TextButton(onClick = { quality = "high" }, colors = ButtonDefaults.textButtonColors(contentColor = if (quality == "high") GoldPrimary else Color.Gray)) { Text("عالية") }
                        }
                    }
                }
                
                // Scheduling Placeholder
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().clickable { /* Show Picker */ }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جدولة النشر: ${publishAt ?: "الآن"}", fontFamily = CairoFont, fontSize = 12.sp, color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "خيارات نشر المطور 🛠️",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GoldPrimary
                            )
                            Text(
                                text = "يمكنك في أسفل الشاشة الاختيار بين النشر العادي للمجتمع أو النشر الفوري للحقن الصامت للأوفلاين عبر الأزرار المخصصة.",
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🚀 Expandable Pre-Publish Optimization Card (كارت إعدادات تحسين جودة ما قبل النشر)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.5.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isOptimizationExpanded = !isOptimizationExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إعدادات تحسين جودة النشر (Pre-Publish) 🚀",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "دقة الفيديو، الضغط الذكي، معالجة الصوت والوسوم",
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        IconButton(onClick = { isOptimizationExpanded = !isOptimizationExpanded }) {
                            Icon(
                                imageVector = if (isOptimizationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isOptimizationExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFF1E293B))

                            // 1. Resolution Selection
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "1. دقة العرض وتنسيق الفيديو (Resolution & Format):",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val is1080 = targetResolution == "1080p"
                                    Surface(
                                        onClick = { targetResolution = "1080p" },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (is1080) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, if (is1080) Color(0xFF38BDF8) else Color(0xFF334155)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "1080p Full HD (فائقة)",
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                color = if (is1080) Color.White else Color(0xFF94A3B8),
                                                fontWeight = if (is1080) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (is1080) Text("✓", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    val is720 = targetResolution == "720p"
                                    Surface(
                                        onClick = { targetResolution = "720p" },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (is720) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, if (is720) Color(0xFF38BDF8) else Color(0xFF334155)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "720p HD (سريعة التنزيل)",
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                color = if (is720) Color.White else Color(0xFF94A3B8),
                                                fontWeight = if (is720) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (is720) Text("✓", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // 2. Smart Compression
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "الضغط التلقائي المتوازن (Smart Compression)",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "تقليل حجم الملف 40% مع الحفاظ التام على جودة الصورة وتلاوة القارئ.",
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Switch(
                                    checked = isSmartCompressEnabled,
                                    onCheckedChange = { isSmartCompressEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0F172A),
                                        checkedTrackColor = Color(0xFF38BDF8)
                                    )
                                )
                            }

                            HorizontalDivider(color = Color(0xFF1E293B))

                            // 3. Audio Normalization & Silence Trimming
                            Text(
                                text = "2. تحسينات الصوت النقي (Audio Optimization):",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF38BDF8)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "موازنة حدة الصوت وتصفية النقاء (Audio Normalization)",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "تعديل مستوى الصوت آلياً ليناسب جميع السماعات مع تقليل الضوضاء.",
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Switch(
                                    checked = isAudioNormalizeEnabled,
                                    onCheckedChange = { isAudioNormalizeEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0F172A),
                                        checkedTrackColor = Color(0xFF38BDF8)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تقليم السكون والتوقفات الصامتة (Silence Trimming)",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "قص أجزاء الصمت غير الضرورية في البداية والنهاية لزيادة التفاعل.",
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Switch(
                                    checked = isSilenceTrimEnabled,
                                    onCheckedChange = { isSilenceTrimEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0F172A),
                                        checkedTrackColor = Color(0xFF38BDF8)
                                    )
                                )
                            }

                            HorizontalDivider(color = Color(0xFF1E293B))

                            // 4. Smart Thumbnail & AI Hashtags
                            Text(
                                text = "3. الغلاف والوسوم الإيمانية الذكية (Metadata & Hashtags):",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF38BDF8)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "توليد غلاف مصغر تلقائي (Smart Thumbnail)",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "التقاط أفضل إطار بؤري من الفيديو ليكون واجهة المقطع.",
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Switch(
                                    checked = isSmartThumbEnabled,
                                    onCheckedChange = { isSmartThumbEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0F172A),
                                        checkedTrackColor = Color(0xFF38BDF8)
                                    )
                                )
                            }

                            // Quick Action: Add Smart Hashtags to Description
                            OutlinedButton(
                                onClick = {
                                    val smartTags = when (selectedCategory) {
                                        "قرآن كريم" -> "\n#قبس_الإيماني #تلاوات_خاشعة #قرآن_كريم #تلاوة_مباركة"
                                        "أحاديث نبوية" -> "\n#قبس_الإيماني #أحاديث_نبويّة #سنة_نبوية #أخلاق_إسلامية"
                                        else -> "\n#قبس_الإيماني #وعظة_إيمانية #ذكر_الله #صدقة_جارية"
                                    }
                                    if (!description.contains("#قبس_الإيماني")) {
                                        description = (description.trim() + " " + smartTags).trim()
                                    }
                                    Toast.makeText(context, "✨ تم إضافة الوسوم الإيمانية الذكية إلى الوصف!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✨ توليد وسوم إيمانية ذكية تلقائياً",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (spiritualViolationMessage != null) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.08f),
                border = BorderStroke(
                    1.5.dp,
                    if (spiritualViolationMessage != null) Color(0xFFEF4444).copy(alpha = 0.6f) else Color(0xFF10B981).copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (spiritualViolationMessage != null) Icons.Default.Block else Icons.Default.Verified,
                            contentDescription = null,
                            tint = if (spiritualViolationMessage != null) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (spiritualViolationMessage != null) "رصد مخالفة شرعية - يمنع النشر" else "الرقابة والضوابط الشرعية",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (spiritualViolationMessage != null) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        com.qabas.app.ui.components.ModelIndicator()
                    }
                    if (spiritualViolationMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = spiritualViolationMessage!!,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            color = Color.White,
                            lineHeight = 17.sp
                        )
                        if (!spiritualViolationAyah.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = spiritualViolationAyah!!,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                color = GoldPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يُحظر نشر أي محتوى يتضمن الموسيقى، المعازف، أو أي وجه من وجوه الذنب والإثم. مِشكاة قبس مخصصة للنقاء الإيماني والشرعي فقط.",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasAcceptedPledge = !hasAcceptedPledge },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasAcceptedPledge,
                            onCheckedChange = { hasAcceptedPledge = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = GoldPrimary,
                                checkmarkColor = DeepSlate,
                                uncheckedColor = Color.White.copy(alpha = 0.6f)
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "أتعهد أمام الله بأن المقطع خالٍ من الموسيقى والمعازف، ومن أي وجه من وجوه الذنب والإثم أو ما يخالف شرع الله.",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasAcceptedPledge) GoldPrimary else Color.White.copy(alpha = 0.9f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (generalErrorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = generalErrorMessage!!,
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val canPublish = !isPublishing &&
                spiritualViolationMessage == null &&
                hasAcceptedPledge &&
                title.isNotBlank() &&
                (selectedVideoUri != null || directVideoUrl.isNotBlank()) &&
                (isDeveloper || publishStatus is ReelPublishManager.PublishStatus.Allowed)

            val doPublishAction: (Boolean) -> Unit = { asSilent ->
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
                                userId = currentUserId,
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
                                    val toastMsg = if (asSilent) "تم حقن مقطع الفيديو الصامت للأوفلاين بنجاح!" else "تم نشر المقطع للمجتمع بنجاح!"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                    onPublishedSuccess()
                                    onDismiss()
                                }
                                is ReelPublishManager.PublishResult.Failed -> {
                                    generalErrorMessage = if (AdminGuard.isDashboardAccessAllowed(context)) {
                                        "فشل النشر [${result.stage}]: ${result.devMessage}"
                                    } else {
                                        "تعذر نشر المقطع — تحقق من الاتصال وحاول مجدداً."
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
                    // Option 1: Normal Reel Publishing to Community Feed
                    Button(
                        onClick = { doPublishAction(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = canPublish,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            disabledContainerColor = Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        if (isPublishing && !isSilentSeed) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(publishProgressText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.5.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🎬 نشر ريلز عادي للمجتمع",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.5.sp
                            )
                        }
                    }

                    // Option 2: Offline Silent Video Injection Publishing
                    Button(
                        onClick = { doPublishAction(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = canPublish,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        if (isPublishing && isSilentSeed) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(publishProgressText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = DeepSlate, fontSize = 12.5.sp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚡ نشر فيديو صامت للحقن (أوفلاين)",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                color = DeepSlate,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = { doPublishAction(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = canPublish,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = publishProgressText,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = if (canPublish) DeepSlate else Color.White.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نشر المقطع",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = if (canPublish) DeepSlate else Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (isPublishing && uploadTotalBytes > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                fontSize = 12.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${(uploadSentBytes.toFloat() / uploadTotalBytes * 100).toInt()}%",
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GoldPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uploadSentBytes.toFloat() / uploadTotalBytes },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = GoldPrimary,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${StorageMetrics.formatBytes(uploadSentBytes)} / ${StorageMetrics.formatBytes(uploadTotalBytes)}",
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            if (uploadSpeedBps > 0) {
                                Text(
                                    text = "${StorageMetrics.formatBytes(uploadSpeedBps)}/s",
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                        if (uploadEtaSeconds > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الوقت المتبقي: ${uploadEtaSeconds / 60}:${"%02d".format(uploadEtaSeconds % 60)}",
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
