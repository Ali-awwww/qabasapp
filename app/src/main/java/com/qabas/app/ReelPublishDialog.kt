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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelPublishDialog(
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
    var selectedCategory by remember { mutableStateOf("تلاوة قرآنية") }
    var isFeaturedByDev by remember { mutableStateOf(false) }

    // Video Selection State
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoThumbnailFile by remember { mutableStateOf<File?>(null) }
    var selectedVideoThumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedVideoDurationMs by remember { mutableStateOf(0L) }
    var directVideoUrl by remember { mutableStateOf("") }
    var showDirectUrlInput by remember { mutableStateOf(false) }

    // Spiritual Pledge & Safety Check
    var hasAcceptedPledge by remember { mutableStateOf(false) }
    var spiritualViolationMessage by remember { mutableStateOf<String?>(null) }
    var spiritualViolationAyah by remember { mutableStateOf<String?>(null) }

    // Publishing Progress
    var isPublishing by remember { mutableStateOf(false) }
    var publishProgressText by remember { mutableStateOf("جاري النشر...") }
    var generalErrorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("تلاوة قرآنية", "حديث نبوي", "قصص وعبر", "خواطر إيمانية", "أدعية وأذكار", "فوائد فقهية")

    // Photo/Video Picker Launcher
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

    // Real-time Spiritual Check
    LaunchedEffect(title, description, selectedCategory) {
        if (title.isNotBlank() || !description.isNullOrBlank()) {
            val audit = SpiritualSafetyGuard.performSpiritualAudit(
                title = title,
                description = description,
                tags = listOf(selectedCategory),
                category = selectedCategory
            )
            when (audit) {
                is SpiritualSafetyGuard.ModerationResult.Rejected -> {
                    spiritualViolationMessage = audit.reason
                    spiritualViolationAyah = audit.detailedAyah
                }
                is SpiritualSafetyGuard.ModerationResult.Passed -> {
                    spiritualViolationMessage = null
                    spiritualViolationAyah = null
                }
            }
        } else {
            spiritualViolationMessage = null
            spiritualViolationAyah = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            text = if (isDeveloper) "نشر ريلز إيماني (صلاحيات المطور) 👑" else "نشر ريلز إيماني مبارك 🎬",
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

            Spacer(modifier = Modifier.height(14.dp))

            // Developer / Quota Banner
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

            // 1. Video Selection Section (Zero Manual Database URL!)
            Text(
                text = "1. اختيار مقطع الفيديو 🎬",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedVideoUri != null) {
                // Video Picked Preview Card
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
                                val min = sec / 60
                                val remSec = sec % 60
                                Text(
                                    text = "المدة: %02d:%02d".format(min, remSec),
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
                // Large Primary Gallery Video Picker Button
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
                            text = "اختر مقطع فيديو من هاتفك 📱",
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

                // Collapsible Optional Direct Link for advanced users
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
                        text = if (showDirectUrlInput) "إخفاء خيار الرابط المباشر" else "أو إدخال رابط فيديو مباشر 🔗",
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

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Category Selection
            Text(
                text = "2. التصنيف الإيماني 🕌",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Metadata Fields
            Text(
                text = "3. تفاصيل المقطع ✍️",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = GoldPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Title
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

            // Description
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

            // Developer Pinning Feature
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
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Spiritual Safety Guard & Mandatory Pledge (الرقابة الشرعية القصوى ضد الذنوب والآثام)
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
                            text = if (spiritualViolationMessage != null) "⛔ رصد مخالفة شرعية - يمنع النشر" else "🛡️ الرقابة والضوابط الشرعية الصارمة",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (spiritualViolationMessage != null) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
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
                            text = "منصة قبس منصة إيمانية نقية؛ يُحظر تماماً نشر الموسيقى والمعازف، التبرج، أو أي محتوى يحمل وزراً أو إثماً جارياً.",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mandatory Pledge Checkbox
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
                            text = "أتعهد أمام الله بأن المقطع خالص لوجهه الكريم وخالٍ تماماً من المعازف والموسيقى وأي ذنب جارٍ.",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasAcceptedPledge) GoldPrimary else Color.White.copy(alpha = 0.9f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // General error banner
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

            // Action Buttons
            val canPublish = !isPublishing &&
                spiritualViolationMessage == null &&
                hasAcceptedPledge &&
                title.isNotBlank() &&
                (selectedVideoUri != null || directVideoUrl.isNotBlank()) &&
                (isDeveloper || publishStatus is ReelPublishManager.PublishStatus.Allowed)

            Button(
                onClick = {
                    val validation = ReelPublishManager.validateContent(
                        title = title,
                        description = description,
                        category = selectedCategory,
                        videoUriOrUrl = selectedVideoUri?.toString() ?: directVideoUrl,
                        hasAcceptedPledge = hasAcceptedPledge,
                        isDeveloper = isDeveloper
                    )

                    when (validation) {
                        is ReelPublishManager.ValidationResult.Invalid -> {
                            generalErrorMessage = validation.reason
                        }
                        is ReelPublishManager.ValidationResult.Valid -> {
                            isPublishing = true
                            generalErrorMessage = null
                            scope.launch {
                                val success = ReelPublishManager.publishReelWithUpload(
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
                                    onProgressUpdate = { progressText ->
                                        publishProgressText = progressText
                                    }
                                )

                                isPublishing = false
                                if (success) {
                                    Toast.makeText(context, "تم نشر المقطع الإيماني بنجاح! جزاك الله خيراً 🌿", Toast.LENGTH_LONG).show()
                                    onPublishedSuccess()
                                    onDismiss()
                                } else {
                                    generalErrorMessage = "تعذر إكمال النشر، يرجى التحقق من الاتصال بالإنترنت والمحاولة مجدداً."
                                }
                            }
                        }
                    }
                },
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
                        text = if (isDeveloper) "نشر فوري برتبة المطور 👑" else "نشر المقطع الإيماني المبارك 🚀",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = if (canPublish) DeepSlate else Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
