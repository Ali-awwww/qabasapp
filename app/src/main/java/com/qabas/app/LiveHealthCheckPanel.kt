package com.qabas.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LiveServiceCheck(
    val type: String,
    val name: String,
    val url: String,
    val value: String,
    val hint: String? = null,
    val freeFallback: String,
    var result: KeyValidationResult? = null,
    var latencyMs: Long? = null
)

@Composable
fun LiveHealthCheckPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val services = remember {
        listOf(
            LiveServiceCheck("gemini", "Gemini (ذكاء اصطناعي)", "https://aistudio.google.com/app/apikey", AgentPrefs.getString(context, AgentPrefs.GEMINI_KEY), freeFallback = "المحلل المحلي + استخراج المشاهد البلاغية يعملان بلا مفتاح"),
            LiveServiceCheck("groq", "Groq (نصوص فائقة السرعة)", "https://console.groq.com/keys", AgentPrefs.getString(context, AgentPrefs.GROQ_KEY), freeFallback = "توليد النصوص يقع على المحرك المحلي و Gemini المجاني"),
            LiveServiceCheck("openai", "OpenAI (نماذج GPT)", "https://platform.openai.com/api-keys", AgentPrefs.getString(context, AgentPrefs.OPENAI_KEY), freeFallback = "توليد النصوص يقع على المحرك المحلي و Gemini المجاني"),
            LiveServiceCheck("huggingface", "HuggingFace (صور AI)", "https://huggingface.co/settings/tokens", AgentPrefs.getString(context, AgentPrefs.HUGGINGFACE_KEY), freeFallback = "توليد الصور محلياً عبر LocalImageAnalyzer"),
            LiveServiceCheck("azure", "Azure TTS (نطق)", "https://portal.azure.com/#create/Microsoft.CognitiveServicesSpeechServices", AgentPrefs.getString(context, "azure_speech_key"), AgentPrefs.getString(context, "azure_speech_region"), freeFallback = "النطق المدمج في أندرويد (TextToSpeech) يعمل مجاناً دائماً"),
            LiveServiceCheck("elevenlabs", "ElevenLabs (نطق)", "https://elevenlabs.io/app/settings/api-keys", AgentPrefs.getString(context, "elevenlabs_key"), freeFallback = "النطق المدمج في أندرويد (TextToSpeech) يعمل مجاناً دائماً"),
            LiveServiceCheck("pexels", "Pexels (B-Roll)", "https://www.pexels.com/api/", AgentPrefs.getString(context, "pexels_key"), freeFallback = "كاش B-Roll المحلي + إطار آمن 1080×1920"),
            LiveServiceCheck("pixabay", "Pixabay (B-Roll)", "https://pixabay.com/api/docs/", AgentPrefs.getString(context, "pixabay_key"), freeFallback = "كاش B-Roll المحلي + إطار آمن 1080×1920"),
            LiveServiceCheck("higgsfield", "Higgsfield AI (فيديو سينمائي)", "https://higgsfield.ai", AgentPrefs.getString(context, "higgsfield_api_key"), freeFallback = "قوالب الفيديو ومحركات المونتاج المحلية المدمجة مجاناً")
        )
    }

    var checks by remember { mutableStateOf(services) }
    var running by remember { mutableStateOf(false) }

    fun launchCheck() {
        if (running) return
        val pending = checks.any { it.value.isNotBlank() }
        if (!pending) return
        running = true
        checks = checks.map { it.copy(result = null, latencyMs = null) }
        checks.forEach { svc ->
            val key = svc.value
            if (key.isBlank()) return@forEach
            scope.launch(Dispatchers.IO) {
                val start = System.nanoTime()
                val res = try {
                    ApiKeyValidator.validateKey(context, svc.type, key, svc.hint)
                } catch (e: Exception) {
                    val message = e.message ?: "خطأ غير معروف"
                    KeyValidationResult(
                        isValid = false,
                        summary = "تعذر الاتصال 🌐",
                        explanation = "فشل الوصول لخادم ${svc.name}: $message",
                        suggestedFix = "تحقق من اتصال الإنترنت بالمشروع، ثم أعد المحاولة."
                    )
                }
                val latencyMs = (System.nanoTime() - start) / 1_000_000
                withContext(Dispatchers.Main) {
                    checks = checks.map { if (it.type == svc.type) it.copy(result = res, latencyMs = latencyMs) else it }
                    if (checks.all { it.value.isBlank() || it.result != null }) running = false
                }
            }
        }
    }

    var snapshot by remember { mutableStateOf<Map<String, ApiUsageTracker.ApiStat>?>(null) }
    LaunchedEffect(Unit) {
        snapshot = ApiUsageTracker.snapshot(context)
    }

    val totalCalls = snapshot?.values?.sumOf { it.totalCalls } ?: 0L
    val successCalls = snapshot?.values?.sumOf { it.successCalls } ?: 0L
    val successRate = if (totalCalls > 0) successCalls.toFloat() / totalCalls else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("فحص صحة المفاتيح الحي 🩺", color = GoldPrimary, fontFamily = CairoFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("قياس فوري للاتصال الفعلي وزمن الاستجابة لكل خدمة", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            checks.forEach { svc ->
                val statusColor = when {
                    svc.value.isBlank() -> Color(0xFFEAB308)
                    svc.result == null -> GoldPrimary
                    svc.result!!.isValid -> Color(0xFF10B981)
                    else -> Color(0xFFEF4444)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(svc.name, color = TextPrimary, fontFamily = CairoFont, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    when {
                        svc.value.isBlank() -> Text("لا يوجد مفتاح 🟡", color = statusColor, fontFamily = CairoFont, fontSize = 11.sp)
                        svc.result == null -> CircularProgressIndicator(color = statusColor, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        svc.result!!.isValid -> Text("متصل ✅${svc.latencyMs?.let { " ($it ms)" } ?: ""}", color = statusColor, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        else -> Text("مرفوض 🔴", color = statusColor, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (svc.value.isBlank()) {
                    Text("   🆓 ${svc.freeFallback}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { launchCheck() },
                enabled = !running,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (running) "جاري فحص الاتصال الحي الآن..." else "🔍 فحص الاتصال الحي الآن",
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val issues = checks.filter { it.value.isNotBlank() && it.result != null && !it.result!!.isValid }
            if (issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("خطة الإصلاح المقترحة 🛡️", color = GoldPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                issues.forEach { svc ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• ${svc.name}: ", color = TextPrimary, fontFamily = CairoFont, fontSize = 11.5.sp)
                        Text(if (svc.result!!.suggestedFix.isNotBlank()) svc.result!!.suggestedFix else svc.result!!.explanation, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { openUrl(context, svc.url) }) {
                            Text("الموقع 🔗", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (totalCalls > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Insights, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "📊 إجمالي المكالمات الحقيقية: $totalCalls · نجاح ${(successRate * 100).toInt()}% — التفاصيل في لوحة المطور",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.startActivity(intent)
    }
}