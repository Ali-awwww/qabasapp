package com.qabas.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.UUID

@Serializable
data class MishkatReminderContent(
    val id: String = "global",
    @SerialName("quote_text") val quoteText: String,
    val attribution: String,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Content shown at the top of Mishkat. It is read only from the cloud;
 * disabled or unavailable content is never replaced with local placeholder data.
 */
@Composable
fun MishkatReminderCard() {
    var content by remember { mutableStateOf<MishkatReminderContent?>(null) }

    LaunchedEffect(Unit) {
        content = withContext(Dispatchers.IO) {
            SupabaseServices.Database.getMishkatReminderContent()
        }
    }

    var expanded by remember { mutableStateOf(false) }
    val published = content?.takeIf { it.isEnabled } ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A deliberately compact, centered disclosure control keeps the Mishkat header uncluttered.
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 36.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "طي التذكير" else "فتح التذكير",
                tint = Color(0xFFE8D8B5),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "تذكّر",
                color = Color(0xFFE8D8B5),
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = "عرض التذكير",
                tint = Color(0xFFD6BC83),
                modifier = Modifier.size(17.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val reminderShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF041715),
                                Color(0xFF0A2927),
                                Color(0xFF071B25)
                            )
                        ),
                        shape = reminderShape
                    )
                    .border(
                        BorderStroke(1.dp, Color(0xFF36CFC0).copy(alpha = 0.48f)),
                        shape = reminderShape
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF39BFB2).copy(alpha = 0.42f)))
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFE0BD72), modifier = Modifier.size(18.dp).padding(horizontal = 2.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF39BFB2).copy(alpha = 0.42f)))
                    }
                    Text(
                        text = published.quoteText
                            .replace("، ", "،\n")
                            .replace("محطَّ نحس ", "محطَّ نحس\n")
                            .replace("\\\\n", "\n"),
                        color = Color(0xFFF2E8D4),
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 23.sp,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text(
                        text = published.attribution,
                        color = Color(0xFF79D9CB),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    published.videoUrl?.takeIf { it.isNotBlank() }?.let { videoUrl ->
                        MishkatReminderVideoPlayer(
                            videoUrl = videoUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.width(42.dp).height(1.dp).background(Color(0xFFE0BD72).copy(alpha = 0.55f)))
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFE0BD72), modifier = Modifier.size(16.dp).padding(horizontal = 2.dp))
                        Box(Modifier.width(42.dp).height(1.dp).background(Color(0xFFE0BD72).copy(alpha = 0.55f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun MishkatReminderVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }
    val player = remember(videoUrl) {
        var cacheRetained = false
        runCatching {
            VideoCacheService.retainPlayer()
            cacheRetained = true
            ExoPlayerFactory.createPlayer(context, useCache = true, useAudioFocus = true).apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = false
            }
        }.onFailure {
            if (cacheRetained) VideoCacheService.releasePlayer()
        }.getOrNull()
    }

    DisposableEffect(player) {
        onDispose {
            player?.let { activePlayer ->
                runCatching {
                    activePlayer.playWhenReady = false
                    activePlayer.stop()
                    activePlayer.clearMediaItems()
                    activePlayer.release()
                }
                VideoCacheService.releasePlayer()
            }
        }
    }

    if (player == null) {
        Text(
            "تعذّر تهيئة مشغّل الفيديو على هذا الجهاز.",
            color = Color(0xFFE8D8B5),
            fontFamily = CairoFont,
            fontSize = 12.sp
        )
        return
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view ->
                        if (view.player !== player) view.player = player
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { fullscreen = true }) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "ملء الشاشة",
                        tint = Color(0xFFE8D8B5)
                    )
                }
            }
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        }
    }
}

/** لوحة المطوّر لإدارة النص والفيديو والنشر السحابي لميزة تذكّر. */
@Composable
fun MishkatReminderManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var adminChecked by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var quoteText by remember { mutableStateOf("") }
    var attribution by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isAdmin = AdminGuard.isAuthAdmin()
        adminChecked = true
        if (isAdmin) {
            try {
                val remote = withContext(Dispatchers.IO) {
                    SupabaseServices.Database.getMishkatReminderContent()
                }
                if (remote != null) {
                    quoteText = remote.quoteText
                    attribution = remote.attribution
                    videoUrl = remote.videoUrl.orEmpty()
                    isEnabled = remote.isEnabled
                } else {
                    errorMessage = "لم يُعثر على إعدادات الميزة في السحابة."
                }
            } catch (error: Exception) {
                errorMessage = "تعذّر تحميل إعدادات الميزة من السحابة."
            }
        }
        isLoading = false
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploading = true
                uploadProgress = 0
                errorMessage = null
                try {
                    val uploadedUrl = withContext(Dispatchers.IO) {
                        val mimeType = when (context.contentResolver.getType(uri)?.lowercase()) {
                            "video/mp4" -> "video/mp4"
                            "video/quicktime" -> "video/quicktime"
                            "video/webm" -> "video/webm"
                            else -> throw IOException("صيغة الفيديو غير مدعومة. استخدم MP4 أو MOV أو WEBM.")
                        }
                        val extension = when (mimeType) {
                            "video/mp4" -> "mp4"
                            "video/quicktime" -> "mov"
                            else -> "webm"
                        }
                        val temporaryFile = File(
                            context.cacheDir,
                            "mishkat_reminder_${UUID.randomUUID()}.$extension"
                        )
                        try {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: throw IOException("تعذّر قراءة ملف الفيديو المحدد.")
                            input.use { source ->
                                temporaryFile.outputStream().use { target ->
                                    source.copyTo(target)
                                }
                            }
                            if (temporaryFile.length() <= 0L) {
                                throw IOException("ملف الفيديو فارغ.")
                            }
                            if (temporaryFile.length() > 100L * 1024L * 1024L) {
                                throw IOException("حجم الفيديو يتجاوز الحد المسموح (100 MB).")
                            }
                            val userId = SupabaseServices.Auth.getCurrentUserId()
                                ?: throw IOException("انتهت جلسة المطوّر. سجّل الدخول ثم أعد المحاولة.")
                            val path = "$userId/mishkat-reminder/${UUID.randomUUID()}.$extension"
                            SupabaseServices.Storage.uploadFileWithProgress(
                                bucketName = "media-assets",
                                path = path,
                                file = temporaryFile,
                                contentType = mimeType,
                                upsert = false,
                                onProgress = { sent, total ->
                                    if (total > 0L) {
                                        val percent = ((sent * 100L) / total).toInt().coerceIn(0, 100)
                                        scope.launch(Dispatchers.Main.immediate) {
                                            uploadProgress = percent
                                        }
                                    }
                                }
                            ) ?: throw IOException("فشل رفع الفيديو إلى التخزين السحابي.")
                        } finally {
                            temporaryFile.delete()
                        }
                    }
                    videoUrl = uploadedUrl
                    Toast.makeText(context, "تم رفع الفيديو إلى التخزين السحابي.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    errorMessage = error.message ?: "تعذّر رفع الفيديو."
                } finally {
                    isUploading = false
                }
            }
        }
    }

    DevHubScaffold(
        title = "إدارة تذكّر",
        subtitle = "محتوى المشكاة • إعدادات سحابية",
        icon = Icons.Default.Lightbulb,
        accentColor = GoldPrimary,
        tabs = listOf("المحتوى والنشر"),
        selectedTab = 0,
        onTabSelected = {},
        onBack = onBack
    ) {
        when {
            !adminChecked || isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            }
            !isAdmin -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(42.dp))
                    Text("هذه الإدارة مخصصة للمطوّر المعتمد.", color = Color.White, fontFamily = CairoFont)
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "محتوى التذكير",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    OutlinedTextField(
                        value = quoteText,
                        onValueChange = { quoteText = it },
                        label = { Text("العبارة") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        enabled = !isSaving && !isUploading
                    )
                    OutlinedTextField(
                        value = attribution,
                        onValueChange = { attribution = it },
                        label = { Text("نسبة العبارة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSaving && !isUploading
                    )
                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text("رابط فيديو مباشر (اختياري)") },
                        supportingText = {
                            Text("استخدم رابط HTTPS مباشر لملف فيديو، أو ارفع ملفاً من جهازك. روابط صفحات YouTube لا تعمل كمصدر مباشر للمشغّل.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                        enabled = !isSaving && !isUploading
                    )
                    OutlinedButton(
                        onClick = { videoPicker.launch(arrayOf("video/mp4", "video/quicktime", "video/webm")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !isSaving && !isUploading
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isUploading) "جارٍ رفع الفيديو..." else "اختيار فيديو ورفعه إلى Supabase")
                    }
                    if (isUploading) {
                        LinearProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = GoldPrimary
                        )
                        Text("تقدم الرفع: $uploadProgress%", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                    Text(
                        if (isEnabled) "الحالة الحالية: منشور للمستخدمين" else "الحالة الحالية: مسودة غير ظاهرة في المشكاة",
                        color = if (isEnabled) Color(0xFF86D6A6) else TextSecondary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (videoUrl.isNotBlank() && videoUrl.startsWith("https://", ignoreCase = true)) {
                        Text(
                            "معاينة الفيديو قبل النشر",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        MishkatReminderVideoPlayer(
                            videoUrl = videoUrl.trim(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    errorMessage?.let {
                        Text(it, color = Color(0xFFFF8A80), fontFamily = CairoFont, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                errorMessage = null
                                val cleanQuote = quoteText.trim()
                                val cleanAttribution = attribution.trim()
                                val cleanVideoUrl = videoUrl.trim().takeIf { it.isNotBlank() }
                                if (cleanQuote.isBlank() || cleanAttribution.isBlank()) {
                                    errorMessage = "أدخل العبارة واسم صاحبها قبل النشر."
                                    return@launch
                                }
                                if (cleanVideoUrl == null || !cleanVideoUrl.startsWith("https://", ignoreCase = true)) {
                                    errorMessage = "ارفع فيديو أو أدخل رابط فيديو مباشر صالحاً قبل النشر."
                                    return@launch
                                }
                                isSaving = true
                                val saved = withContext(Dispatchers.IO) {
                                    SupabaseServices.Database.saveMishkatReminderContent(
                                        quoteText = cleanQuote,
                                        attribution = cleanAttribution,
                                        videoUrl = cleanVideoUrl,
                                        isEnabled = true
                                    )
                                }
                                isSaving = false
                                if (saved) {
                                    quoteText = cleanQuote
                                    attribution = cleanAttribution
                                    videoUrl = cleanVideoUrl
                                    isEnabled = true
                                    Toast.makeText(context, "تم نشر التذكير في المشكاة بنجاح.", Toast.LENGTH_LONG).show()
                                } else {
                                    errorMessage = "تعذّر تأكيد النشر من Supabase. لم نعرض المحتوى على أنه منشور."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        enabled = !isSaving && !isUploading && quoteText.isNotBlank() && attribution.isNotBlank() &&
                            videoUrl.trim().startsWith("https://", ignoreCase = true)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ النشر...")
                        } else {
                            Text(if (isEnabled) "تحديث المنشور" else "نشر التذكير الآن")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                errorMessage = null
                                val cleanQuote = quoteText.trim()
                                val cleanAttribution = attribution.trim()
                                val cleanVideoUrl = videoUrl.trim().takeIf { it.isNotBlank() }
                                if (cleanQuote.isBlank() || cleanAttribution.isBlank()) {
                                    errorMessage = "أدخل العبارة واسم صاحبها قبل حفظ المسودة."
                                    return@launch
                                }
                                isSaving = true
                                val saved = withContext(Dispatchers.IO) {
                                    SupabaseServices.Database.saveMishkatReminderContent(
                                        quoteText = cleanQuote,
                                        attribution = cleanAttribution,
                                        videoUrl = cleanVideoUrl,
                                        isEnabled = false
                                    )
                                }
                                isSaving = false
                                if (saved) {
                                    quoteText = cleanQuote
                                    attribution = cleanAttribution
                                    videoUrl = cleanVideoUrl.orEmpty()
                                    isEnabled = false
                                    Toast.makeText(context, "تم حفظ التذكير كمسودة دون إظهاره للمستخدمين.", Toast.LENGTH_LONG).show()
                                } else {
                                    errorMessage = "تعذّر حفظ المسودة في Supabase."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !isSaving && !isUploading && quoteText.isNotBlank() && attribution.isNotBlank()
                    ) {
                        Text("حفظ كمسودة دون نشر")
                    }
                    Text(
                        "التخزين السحابي: Supabase • bucket: media-assets • الحد الأقصى للملف: 100 MB",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
