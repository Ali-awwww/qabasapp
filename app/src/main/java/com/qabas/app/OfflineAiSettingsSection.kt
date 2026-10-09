package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun OfflineAiSettingsSection() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("offline_ai_mode", false)) }
    var isModelReady by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val modelFile = File(context.filesDir, "models/llama/gemma-3-1b-it-q4_k_m.gguf")
        isModelReady = modelFile.exists() && modelFile.length() > 100_000_000
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🧠 الذكاء الاصطناعي المحلي (Offline)",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "يعمل بدون إنترنت وبدون مفتاح API - خصوصية تامة",
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isOfflineMode) "✓ الوضع المحلي مفعل" else "الوضع السحابي",
                        color = if (isOfflineMode) GoldPrimary else TextSecondary,
                        fontFamily = CairoFont
                    )
                    if (isOfflineMode && isModelReady) {
                        Text(
                            "يعمل بدون إنترنت",
                            color = androidx.compose.ui.graphics.Color.Green,
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = isOfflineMode,
                    onCheckedChange = { enabled ->
                        if (enabled && !isModelReady) {
                            statusMessage = "يجب تنزيل الموديل أولاً"
                            return@Switch
                        }
                        isOfflineMode = enabled
                        prefs.edit().putBoolean("offline_ai_mode", enabled).apply()
                        statusMessage = if (enabled) "تم التفعيل" else "تم العودة للوضع السحابي"
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldPrimary,
                        checkedTrackColor = GoldPrimary.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    statusMessage,
                    color = GoldPrimary,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            when {
                isDownloading -> {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = GoldPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "جاري التنزيل... ${(downloadProgress * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                !isModelReady -> {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    isDownloading = true
                                    downloadModel(context) { progress ->
                                        downloadProgress = progress
                                    }
                                    isModelReady = true
                                    statusMessage = "✓ الموديل جاهز"
                                } catch (e: Exception) {
                                    statusMessage = "خطأ: ${e.message}"
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تنزيل الموديل (600MB)", color = DeepSlate)
                    }
                    Text(
                        "يتطلب اتصال إنترنت للتنزيل الأولي فقط",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                isModelReady && !isOfflineMode -> {
                    OutlinedButton(
                        onClick = {
                            prefs.edit().putBoolean("offline_ai_mode", true).apply()
                            isOfflineMode = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تفعيل الوضع المحلي")
                    }
                }
            }
        }
    }
}

private suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit) {
    withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models/llama")
        if (!modelDir.exists()) modelDir.mkdirs()
        
        val modelFile = File(modelDir, "gemma-3-1b-it-q4_k_m.gguf")
        
        val url = "https://huggingface.co/google/gemma-3-1b-it-q4-gguf/resolve/main/gemma-3-1b-it-q4_k_m.gguf"
        
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        val request = okhttp3.Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("فشل التنزيل: ${response.code}")
            
            val body = response.body ?: throw Exception("لا يوجد محتوى")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            body.byteStream().use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }
        }
    }
}
