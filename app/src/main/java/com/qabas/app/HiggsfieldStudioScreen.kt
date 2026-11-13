package com.qabas.app

import android.media.MediaController
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.qabasPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class HiggsfieldMode(val label: String) {
    TEXT_TO_VIDEO("نص إلى فيديو"),
    IMAGE_TO_VIDEO("تحريك صورة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiggsfieldStudioScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = qabasPrimary()

    var mode by rememberSaveable { mutableStateOf(HiggsfieldMode.TEXT_TO_VIDEO.name) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var durationSeconds by rememberSaveable { mutableIntStateOf(5) }
    var cameraMotion by rememberSaveable { mutableStateOf("cinematic") }
    var selectedImage by remember { mutableStateOf<File?>(null) }
    var isPreparingImage by remember { mutableStateOf(false) }
    var isConfigured by remember { mutableStateOf<Boolean?>(null) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showChargeConfirmation by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressMessage by remember { mutableStateOf("") }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var resultMessage by remember { mutableStateOf("") }

    val activeMode = HiggsfieldMode.valueOf(mode)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isPreparingImage = true
                selectedImage = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = File(context.cacheDir, "higgsfield_source_${System.currentTimeMillis()}.img")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            output.outputStream().use { target -> input.copyTo(target) }
                        } ?: return@runCatching null
                        output.takeIf { it.exists() && it.length() > 0L }
                    }.getOrNull()
                }
                isPreparingImage = false
            }
        }
    }

    LaunchedEffect(Unit) {
        isConfigured = runCatching { HiggsfieldVideoService.isConfigured(context) }.getOrDefault(false)
    }

    val submitGeneration: () -> Unit = {
        scope.launch {
            isGenerating = true
            progress = 0f
            progressMessage = "تهيئة الطلب..."
            resultFile = null
            resultMessage = ""
            try {
                val onProgress: (Float, String) -> Unit = { value, message ->
                    scope.launch {
                        progress = value.coerceIn(0f, 1f)
                        progressMessage = message
                    }
                }
                val generated = when (activeMode) {
                    HiggsfieldMode.TEXT_TO_VIDEO -> HiggsfieldVideoService.generateTextToVideo(
                        context = context,
                        prompt = prompt,
                        cameraMotion = cameraMotion,
                        durationSeconds = durationSeconds,
                        onProgress = onProgress
                    )
                    HiggsfieldMode.IMAGE_TO_VIDEO -> {
                        val image = selectedImage
                        if (image == null) null else HiggsfieldVideoService.animateImage(
                            context = context,
                            imageFile = image,
                            prompt = prompt,
                            cameraMotion = cameraMotion,
                            durationSeconds = durationSeconds,
                            onProgress = onProgress
                        )
                    }
                }
                resultFile = generated
                resultMessage = if (generated != null) {
                    "اكتمل الفيديو وحُفظ مؤقتًا داخل التطبيق."
                } else {
                    "تعذر إكمال الطلب. تحقق من حالة الربط والمفتاح ثم حاول لاحقًا."
                }
            } catch (_: Exception) {
                resultMessage = "تعذر إكمال الطلب. تحقق من الاتصال ببوابة قبس ومفتاح Higgsfield."
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF050811),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("استوديو Higgsfield", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Kling 4.0 عبر بوابة قبس", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(Icons.Default.VpnKey, contentDescription = "إعداد مفتاح API", tint = primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF030712))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0B132B),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (isConfigured) {
                                        true -> Color(0xFF10B981)
                                        false -> Color(0xFFF59E0B)
                                        null -> Color.Gray
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = when (isConfigured) {
                                true -> "المفتاح مسجل على الخادم؛ لم تُختبر صلاحيته لدى Higgsfield بعد."
                                false -> "لا يظهر مفتاح مسجل أو تعذر الوصول إلى بوابة قبس."
                                null -> "جارٍ فحص حالة الربط..."
                            },
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "الربط هنا بمفتاح API محفوظ على خادم قبس، وليس تسجيل دخول OAuth إلى موقع Higgsfield.",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                    if (isConfigured != true) {
                        OutlinedButton(onClick = { showKeyDialog = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                            Spacer(Modifier.width(6.dp))
                            Text("ربط مفتاح API", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HiggsfieldMode.entries.forEach { option ->
                    val selected = activeMode == option
                    Surface(
                        onClick = { mode = option.name },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) primary else Color(0xFF111827),
                        border = BorderStroke(1.dp, if (selected) primary else Color(0xFF334155))
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (option == HiggsfieldMode.TEXT_TO_VIDEO) Icons.Default.MovieFilter else Icons.Default.Image,
                                contentDescription = null,
                                tint = if (selected) Color(0xFF050811) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(option.label, color = if (selected) Color(0xFF050811) else Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (activeMode == HiggsfieldMode.IMAGE_TO_VIDEO) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !isPreparingImage && !isGenerating,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (isPreparingImage) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Image, contentDescription = null, tint = primary)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedImage?.name ?: "اختر صورة للتحريك", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("وصف المشهد والحركة", fontFamily = CairoFont) },
                placeholder = { Text("اكتب وصفًا واضحًا لما تريد ظهوره في الفيديو", fontFamily = CairoFont) },
                minLines = 3,
                maxLines = 6,
                enabled = !isGenerating,
                shape = RoundedCornerShape(12.dp)
            )

            Text("المدة", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { seconds ->
                    val selected = durationSeconds == seconds
                    Surface(
                        onClick = { durationSeconds = seconds },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) primary.copy(alpha = 0.2f) else Color(0xFF111827),
                        border = BorderStroke(1.dp, if (selected) primary else Color(0xFF334155))
                    ) {
                        Text(
                            "${seconds}s",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (selected) primary else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Text("حركة الكاميرا", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("cinematic" to "سينمائي", "orbit" to "مداري", "drone" to "درون", "push_in" to "تقريب").forEach { (value, label) ->
                    val selected = cameraMotion == value
                    Surface(
                        onClick = { cameraMotion = value },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) primary.copy(alpha = 0.18f) else Color(0xFF111827),
                        border = BorderStroke(1.dp, if (selected) primary else Color(0xFF334155))
                    ) {
                        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp), color = if (selected) primary else Color.White, fontFamily = CairoFont, fontSize = 10.sp)
                    }
                }
            }

            Text(
                "تنبيه: قد يستهلك إرسال الطلب رصيدًا من حساب Higgsfield. لا يعرض التطبيق هنا سعرًا مضمونًا؛ راجع حساب المزود قبل المتابعة.",
                color = Color(0xFFFBBF24),
                fontFamily = CairoFont,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )

            Button(
                onClick = { showChargeConfirmation = true },
                enabled = !isGenerating && !isPreparingImage && isConfigured == true && prompt.isNotBlank() &&
                    (activeMode == HiggsfieldMode.TEXT_TO_VIDEO || selectedImage != null),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF050811), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("جارٍ تنفيذ الطلب...", color = Color(0xFF050811), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color(0xFF050811))
                    Spacer(Modifier.width(8.dp))
                    Text("متابعة إلى التأكيد", color = Color(0xFF050811), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }

            if (isGenerating) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = primary)
                Text(progressMessage, color = Color(0xFFCBD5E1), fontFamily = CairoFont, fontSize = 11.sp)
            }

            if (resultMessage.isNotBlank()) {
                Text(resultMessage, color = if (resultFile != null) Color(0xFF10B981) else Color(0xFFF87171), fontFamily = CairoFont, fontSize = 11.sp)
            }

            resultFile?.takeIf { it.exists() }?.let { videoFile ->
                key(videoFile.absolutePath) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        factory = { viewContext ->
                            VideoView(viewContext).apply {
                                setVideoURI(Uri.fromFile(videoFile))
                                setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                            }
                        }
                    )
                }
            }
        }
    }

    if (showChargeConfirmation) {
        AlertDialog(
            onDismissRequest = { showChargeConfirmation = false },
            title = { Text("تأكيد إرسال طلب Higgsfield", fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيُرسل وصفك${if (activeMode == HiggsfieldMode.IMAGE_TO_VIDEO) " والصورة المحددة" else ""} إلى Kling 4.0 عبر بوابة قبس. قد يستهلك الطلب رصيدًا من حساب Higgsfield. هل تريد المتابعة؟",
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showChargeConfirmation = false
                    submitGeneration()
                }) { Text("أرسل الطلب", color = primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showChargeConfirmation = false }) { Text("إلغاء", fontFamily = CairoFont) }
            },
            containerColor = Color(0xFF0B132B),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1)
        )
    }

    if (showKeyDialog) {
        ApiKeyManagerDialog(onDismiss = { showKeyDialog = false })
    }
}
