@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronDown
import androidx.compose.material.icons.filled.ChevronUp
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17251F)),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ChevronUp else Icons.Default.ChevronDown,
                    contentDescription = if (expanded) "طي التذكير" else "فتح التذكير",
                    tint = Color(0xFFE8D8B5)
                )
                Text(
                    text = "تذكّر",
                    color = Color(0xFFE8D8B5),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = "عرض التذكير",
                        tint = Color(0xFFD6BC83)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = published.quoteText.replace("، ", "،\n"),
                        color = Color(0xFFF4EAD7),
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 23.sp,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text(
                        text = published.attribution,
                        color = Color(0xFFD6BC83),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (!published.videoUrl.isNullOrBlank()) {
                        MishkatReminderVideoPlayer(
                            videoUrl = published.videoUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
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
        runCatching {
            ExoPlayerFactory.createPlayer(context, useCache = true, useAudioFocus = true).apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = false
            }
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
                        val mimeType = context.contentResolver.getType(uri)?.lowercase()
                        val extension = when (mimeType) {
                            "video/mp4" -> "mp4"
                            "video/quicktime" -> "mov"
                            "video/webm" -> "webm"
                            else -> throw IOException("صيغة الفيديو غير مدعومة. استخدم MP4 أو MOV أو WEBM.")
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
                            val path = "mishkat-reminder/$userId/${UUID.randomUUID()}.$extension"
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نشر التذكير للمستخدمين", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            Text("عند الإيقاف يبقى المحتوى محفوظاً في السحابة ولا يظهر في المشكاة.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            enabled = !isSaving && !isUploading
                        )
                    }
                    if (!videoUrl.isNullOrBlank() && videoUrl.startsWith("https://", ignoreCase = true)) {
                        Text(
                            "تم تعيين مصدر فيديو. سيظهر بعد حفظ التغييرات ونشر الميزة.",
                            color = Color(0xFF86D6A6),
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
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
                                    errorMessage = "أدخل العبارة واسم صاحبها قبل الحفظ."
                                    return@launch
                                }
                                if (cleanVideoUrl != null && !cleanVideoUrl.startsWith("https://", ignoreCase = true)) {
                                    errorMessage = "يجب أن يبدأ رابط الفيديو بـ HTTPS."
                                    return@launch
                                }
                                if (isEnabled && cleanVideoUrl == null) {
                                    errorMessage = "أضف فيديو قبل تفعيل ظهور التذكير للمستخدمين."
                                    return@launch
                                }
                                isSaving = true
                                val saved = withContext(Dispatchers.IO) {
                                    SupabaseServices.Database.saveMishkatReminderContent(
                                        quoteText = cleanQuote,
                                        attribution = cleanAttribution,
                                        videoUrl = cleanVideoUrl,
                                        isEnabled = isEnabled
                                    )
                                }
                                isSaving = false
                                if (saved) {
                                    quoteText = cleanQuote
                                    attribution = cleanAttribution
                                    videoUrl = cleanVideoUrl.orEmpty()
                                    Toast.makeText(context, "حُفظت إعدادات تذكّر في السحابة.", Toast.LENGTH_LONG).show()
                                } else {
                                    errorMessage = "لم يؤكد Supabase حفظ التغييرات. تحقق من صلاحية المطوّر والاتصال ثم أعد المحاولة."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        enabled = !isSaving && !isUploading && quoteText.isNotBlank() && attribution.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ الحفظ...")
                        } else {
                            Text("حفظ ونشر التغييرات")
                        }
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
