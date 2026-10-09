package com.qabas.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🎥 شاشة استوديو الكاميرا السيبرانية الفضائية (Spatial HUD Camera Studio)
 * تدعم تسجيل الفيديو المباشر للريلز والقصص، التقاط الصور الإيمانية، قلب الكاميرا، وتشغيل الفلاش.
 */
@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onVideoCaptured: (String) -> Unit,
    onPhotoCaptured: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler { onBack() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionDeniedView(
            onRequestPermission = {
                permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            },
            onBack = onBack
        )
        return
    }

    // Camera State
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var captureMode by remember { mutableStateOf("VIDEO") } // "VIDEO" or "PHOTO"

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    val isFrontCamera by remember { derivedStateOf { lensFacing == CameraSelector.LENS_FACING_FRONT } }
    var showGridLines by remember { mutableStateOf(true) }

    // Pulse animation for recording HUD
    val infiniteTransition = rememberInfiniteTransition(label = "CameraHudTransition")
    val recPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recPulse"
    )

    // Timer coroutine when recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000L)
                recordingDurationSec++
            }
        } else {
            recordingDurationSec = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview Feed
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.HD)))
                        .setExecutor(executor)
                        .build()

                    videoCapture = VideoCapture.withOutput(recorder)

                    val imgCap = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = imgCap

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    try {
                        cameraProvider.unbindAll()
                        activeCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            videoCapture,
                            imgCap
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Grid Guidelines Overlay
        if (showGridLines) {
            CameraGridOverlay()
        }

        // Top Spatial HUD Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = {
                    if (isRecording) {
                        activeRecording?.stop()
                        activeRecording = null
                        isRecording = false
                    }
                    onBack()
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DeepSlate.copy(alpha = 0.75f))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = GoldPrimary
                )
            }

            // Central Status Pill (REC / STANDBY)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isRecording) Color.Red.copy(alpha = 0.85f)
                        else DeepSlate.copy(alpha = 0.85f)
                    )
                    .border(
                        1.dp,
                        if (isRecording) Color.White.copy(alpha = 0.7f)
                        else GoldPrimary.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.White.copy(alpha = recPulseAlpha) else Color(0xFF10B981))
                    )
                    Text(
                        text = if (isRecording) {
                            String.format(Locale.US, "REC %02d:%02d", recordingDurationSec / 60, recordingDurationSec % 60)
                        } else {
                            "استوديو الكاميرا 4K"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                }
            }

            // Flash & Grid controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Torch toggle
                IconButton(
                    onClick = {
                        isTorchEnabled = !isTorchEnabled
                        activeCamera?.cameraControl?.enableTorch(isTorchEnabled)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isTorchEnabled) GoldPrimary else DeepSlate.copy(alpha = 0.75f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "الفلاش",
                        tint = if (isTorchEnabled) DeepSlate else GoldPrimary
                    )
                }

                // Grid toggle
                IconButton(
                    onClick = { showGridLines = !showGridLines },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (showGridLines) GoldPrimary.copy(alpha = 0.2f) else DeepSlate.copy(alpha = 0.75f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.GridOn,
                        contentDescription = "الشبكة",
                        tint = if (showGridLines) GoldPrimary else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Bottom Controls HUD (Shutter, Switch Camera, Mode Toggle)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode Selector Pill (فيديو ريلز | صورة)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepSlate.copy(alpha = 0.8f))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CameraModeTab(
                    label = "🎥 فيديو ريلز 9:16",
                    isSelected = captureMode == "VIDEO",
                    onClick = { if (!isRecording) captureMode = "VIDEO" }
                )
                CameraModeTab(
                    label = "📸 صورة فوتوغرافية",
                    isSelected = captureMode == "PHOTO",
                    onClick = { if (!isRecording) captureMode = "PHOTO" }
                )
            }

            // Main Trigger Shutter & Side Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flip Camera (Front / Back)
                IconButton(
                    onClick = {
                        if (!isRecording) {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            isTorchEnabled = false
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DeepSlate.copy(alpha = 0.85f))
                        .border(1.2.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "قلب الكاميرا",
                        tint = GoldPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                if (isRecording) listOf(Color.Red, Color(0xFFEF4444), Color.Red)
                                else listOf(GoldPrimary, Color(0xFF00E5FF), GoldPrimary)
                            )
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color.Red
                            else if (captureMode == "PHOTO") Color.White
                            else GoldPrimary
                        )
                        .clickable {
                            AppSoundManager.playClick()
                            if (captureMode == "PHOTO") {
                                // Capture Photo
                                capturePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    onSaved = { path ->
                                        Toast.makeText(context, "تم التقاط الصورة بنجاح 📸", Toast.LENGTH_SHORT).show()
                                        onPhotoCaptured(path)
                                    }
                                )
                            } else {
                                // Toggle Video Recording
                                if (isRecording) {
                                    activeRecording?.stop()
                                    activeRecording = null
                                    isRecording = false
                                } else {
                                    startVideoRecording(
                                        context = context,
                                        videoCapture = videoCapture,
                                        hasAudioPerm = hasAudioPermission,
                                        onRecordingStarted = { rec ->
                                            activeRecording = rec
                                            isRecording = true
                                        },
                                        onRecordingFinalized = { path ->
                                            isRecording = false
                                            activeRecording = null
                                            if (path != null) {
                                                Toast.makeText(context, "تم حفظ الفيديو بنجاح 🎥", Toast.LENGTH_SHORT).show()
                                                onVideoCaptured(path)
                                            }
                                        }
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        // Square stop indicator
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                        )
                    } else if (captureMode == "PHOTO") {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "التقاط صورة",
                            tint = DeepSlate,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "بدء التسجيل",
                            tint = DeepSlate,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Quick Gallery / Done Action
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DeepSlate.copy(alpha = 0.85f))
                        .border(1.2.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "إتمام",
                        tint = GoldPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraModeTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) GoldPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) DeepSlate else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = CairoFont
        )
    }
}

@Composable
private fun CameraGridOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.fillMaxHeight().width(0.6.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.fillMaxHeight().width(0.6.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CameraPermissionDeniedView(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.15f))
                    .border(1.5.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                "يلزم إذن الكاميرا للتصوير",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CairoFont
            )
            Text(
                "استوديو قبس يحتاج للوصول إلى الكاميرا لتسجيل الريلز الإيمانية والتقاط الصور للمصغرات.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontFamily = NotoSansFont
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text("منح إذن الكاميرا ⚡", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
            }
            TextButton(onClick = onBack) {
                Text("العودة للخلف", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

// Helpers for Video and Photo Capture
@SuppressLint("MissingPermission")
private fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    hasAudioPerm: Boolean,
    onRecordingStarted: (Recording) -> Unit,
    onRecordingFinalized: (String?) -> Unit
) {
    if (videoCapture == null) {
        Toast.makeText(context, "الكاميرا غير جاهزة بعد", Toast.LENGTH_SHORT).show()
        return
    }
    val videosDir = File(context.cacheDir, "videos").apply { mkdirs() }
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val videoFile = File(videosDir, "reel_rec_${timeStamp}.mp4")

    val outputOptions = FileOutputOptions.Builder(videoFile).build()
    val executor = ContextCompat.getMainExecutor(context)

    try {
        val pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
        if (hasAudioPerm) {
            pendingRecording.withAudioEnabled()
        }

        val recording = pendingRecording.start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    // Recording started
                }
                is VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        onRecordingFinalized(videoFile.absolutePath)
                    } else {
                        Toast.makeText(context, "خطأ في تسجيل الفيديو: ${event.error}", Toast.LENGTH_SHORT).show()
                        onRecordingFinalized(null)
                    }
                }
            }
        }
        onRecordingStarted(recording)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل بدء التسجيل: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onSaved: (String) -> Unit
) {
    if (imageCapture == null) {
        Toast.makeText(context, "الكاميرا غير جاهزة للالتقاط", Toast.LENGTH_SHORT).show()
        return
    }
    val photosDir = File(context.cacheDir, "photos").apply { mkdirs() }
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFile = File(photosDir, "snap_${timeStamp}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(photoFile.absolutePath)
            }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "فشل التقاط الصورة: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
