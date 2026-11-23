package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpatialServiceStatus(
    val id: String,
    val name: String,
    val category: String,
    val icon: ImageVector,
    val isLive: Boolean,
    val isConfigured: Boolean,
    val latencyMs: Long? = null,
    val fallbackEngine: String,
    val providerBadge: String,
    val accentColor: Color
)

@Composable
fun SpatialServiceDiagnosticsHub(
    onNavigateToKeys: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var isOptimizing by remember { mutableStateOf(false) }
    var scanCompletedMessage by remember { mutableStateOf<String?>(null) }

    var readinessReport by remember {
        mutableStateOf(OperationalReadinessManager.calculateReadiness(context))
    }

    fun buildInitialStatuses(): List<SpatialServiceStatus> = listOf(
        SpatialServiceStatus(
            id = "supabase",
            name = "Supabase Cloud",
            category = "قاعدة البيانات والمصادقة",
            icon = Icons.Default.CloudQueue,
            isLive = SupabaseServices.isSupabaseAvailable,
            isConfigured = KeyVault.supabaseUrl.isNotBlank() && KeyVault.supabaseAnonKey.isNotBlank(),
            fallbackEngine = "التخزين المحلي المشفر",
            providerBadge = "Supabase",
            accentColor = Color(0xFF1E7E34)
        ),
        SpatialServiceStatus(
            id = "gemini",
            name = "Google Gemini",
            category = "السيناريو والتحليل",
            icon = Icons.Default.AutoFixHigh,
            isLive = KeyVault.gemini.isNotBlank() && !KeyVault.gemini.startsWith("your_", ignoreCase = true),
            isConfigured = KeyVault.gemini.isNotBlank(),
            fallbackEngine = "يتطلب مفتاح API",
            providerBadge = "Google AI",
            accentColor = theme.primary
        ),
        SpatialServiceStatus(
            id = "groq",
            name = "Groq LPU",
            category = "المعالجة السريعة",
            icon = Icons.Default.Speed,
            isLive = KeyVault.groq.isNotBlank() && !KeyVault.groq.startsWith("your_", ignoreCase = true),
            isConfigured = KeyVault.groq.isNotBlank(),
            fallbackEngine = "يتطلب مفتاح API",
            providerBadge = "Groq Cloud",
            accentColor = theme.warning
        ),
        SpatialServiceStatus(
            id = "openrouter",
            name = "OpenRouter",
            category = "بوابة النماذج المتعددة",
            icon = Icons.Default.AltRoute,
            isLive = KeyVault.openrouter.isNotBlank() && !KeyVault.openrouter.startsWith("your_", ignoreCase = true),
            isConfigured = KeyVault.openrouter.isNotBlank(),
            fallbackEngine = "يتطلب مفتاح API",
            providerBadge = "OpenRouter",
            accentColor = Color(0xFF5856D6)
        ),
        SpatialServiceStatus(
            id = "azure",
            name = "Azure / Edge-TTS",
            category = "التعليق الصوتي",
            icon = Icons.Default.RecordVoiceOver,
            isLive = KeyVault.azureSpeechKey.isNotBlank() && KeyVault.azureSpeechRegion.isNotBlank(),
            isConfigured = KeyVault.azureSpeechKey.isNotBlank(),
            fallbackEngine = "نطق النظام المحلي متاح",
            providerBadge = "Microsoft / TTS",
            accentColor = theme.success
        ),
        SpatialServiceStatus(
            id = "flux",
            name = "HuggingFace / FLUX",
            category = "الصور والبوسترات",
            icon = Icons.Default.Image,
            isLive = KeyVault.huggingface.isNotBlank() && !KeyVault.huggingface.startsWith("your_", ignoreCase = true),
            isConfigured = KeyVault.huggingface.isNotBlank(),
            fallbackEngine = "يتطلب مفتاح API",
            providerBadge = "HuggingFace",
            accentColor = Color(0xFF7D7AFF)
        ),
        SpatialServiceStatus(
            id = "broll",
            name = "Pexels & Pixabay",
            category = "المشاهد والخلفيات",
            icon = Icons.Default.VideoLibrary,
            isLive = KeyVault.pexels.isNotBlank() || KeyVault.pixabay.isNotBlank(),
            isConfigured = KeyVault.pexels.isNotBlank() || KeyVault.pixabay.isNotBlank(),
            fallbackEngine = "يتطلب مفتاح Pexels/Pixabay",
            providerBadge = "Media API",
            accentColor = theme.primary
        ),
        SpatialServiceStatus(
            id = "higgsfield",
            name = "Higgsfield Video",
            category = "الفيديو السينمائي",
            icon = Icons.Default.MovieFilter,
            isLive = false,
            isConfigured = ServiceAvailabilityRegistry.hasHiggsfieldGateway(),
            fallbackEngine = "مُدار عبر بوابة الخادم",
            providerBadge = "Gateway",
            accentColor = theme.destructive
        ),
        SpatialServiceStatus(
            id = "kokoro",
            name = "المحرك الصوتي المحلي",
            category = "التشغيل بدون إنترنت",
            icon = Icons.Default.GraphicEq,
            isLive = true,
            isConfigured = true,
            fallbackEngine = "Android System Speech",
            providerBadge = "On-Device",
            accentColor = theme.success
        ),
        SpatialServiceStatus(
            id = "github",
            name = "GitHub & xdelta",
            category = "الإصدارات والتحديثات",
            icon = Icons.Default.Code,
            isLive = GitHubAuthManager.isConfigured() || KeyVault.effective("github_token").isNotBlank(),
            isConfigured = GitHubAuthManager.isConfigured() || KeyVault.effective("github_token").isNotBlank(),
            fallbackEngine = "يتطلب رمز GitHub",
            providerBadge = "GitHub",
            accentColor = theme.textSecondary
        )
    )

    var serviceStatuses by remember { mutableStateOf(buildInitialStatuses()) }

    fun runLiveDiagnosticsAndHealing() {
        if (isScanning) return
        isScanning = true
        scanCompletedMessage = null

        scope.launch(Dispatchers.IO) {
            val updated = buildInitialStatuses().map { svc ->
                val start = System.nanoTime()
                val isRealConfigured = when (svc.id) {
                    "gemini" -> KeyVault.gemini.isNotBlank() && !KeyVault.gemini.startsWith("your_", ignoreCase = true)
                    "higgsfield" -> PlatformGatewayClient.providerConfiguredSuspend("higgsfield")
                    "groq" -> KeyVault.groq.isNotBlank() && !KeyVault.groq.startsWith("your_", ignoreCase = true)
                    "azure" -> KeyVault.azureSpeechKey.isNotBlank() && KeyVault.azureSpeechRegion.isNotBlank()
                    "flux" -> KeyVault.huggingface.isNotBlank() && !KeyVault.huggingface.startsWith("your_", ignoreCase = true)
                    "broll" -> KeyVault.pexels.isNotBlank() || KeyVault.pixabay.isNotBlank()
                    "supabase" -> SupabaseConfig.isConfigured
                    "openrouter" -> KeyVault.openrouter.isNotBlank() && !KeyVault.openrouter.startsWith("your_", ignoreCase = true)
                    "kokoro" -> true
                    "github" -> GitHubAuthManager.isConfigured() || KeyVault.effective("github_token").isNotBlank()
                    else -> false
                }

                val checkOk = if (isRealConfigured) {
                    when (svc.id) {
                        "supabase" -> SupabaseServices.isSupabaseAvailable
                        "higgsfield" -> false // Server-managed gateway; readiness depends on live dispatch
                        "kokoro" -> true
                        "github" -> KeyVault.effective("github_token").isNotBlank() || GitHubAuthManager.isConfigured()
                        "azure" -> runCatching {
                            ApiKeyValidator.validateKey(context, "azure", KeyVault.azureSpeechKey, KeyVault.azureSpeechRegion).isValid
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                        "flux" -> runCatching {
                            ApiKeyValidator.validateKey(context, "huggingface", KeyVault.huggingface).isValid
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                        "broll" -> runCatching {
                            val type = if (KeyVault.pexels.isNotBlank()) "pexels" else "pixabay"
                            val key = if (type == "pexels") KeyVault.pexels else KeyVault.pixabay
                            ApiKeyValidator.validateKey(context, type, key).isValid
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                        "gemini" -> runCatching {
                            ApiKeyValidator.validateKey(context, "gemini", KeyVault.gemini).isValid
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                        "groq" -> runCatching {
                            ApiKeyValidator.validateKey(context, "groq", KeyVault.groq).isValid
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                        "openrouter" -> runCatching {
                            ApiKeyValidator.validateKey(context, "openrouter", KeyVault.openrouter).isValid
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                        else -> false
                    }
                } else {
                    false
                }
                val latency = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(12L)

                svc.copy(
                    isLive = checkOk,
                    isConfigured = isRealConfigured,
                    latencyMs = if (isRealConfigured) latency else null
                )
            }

            withContext(Dispatchers.Main) {
                serviceStatuses = updated
                readinessReport = OperationalReadinessManager.calculateReadiness(context)
                isScanning = false
                scanCompletedMessage = "اكتمل الفحص الفعلي: ${updated.count { it.isLive }} خدمات سحابية/محلية متصلة"
            }
        }
    }

    fun optimizeCacheAndRoutes() {
        if (isOptimizing) return
        isOptimizing = true
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("temp_") || file.name.endsWith(".tmp"))) {
                        file.delete()
                    }
                }
                ApiUsageTracker.snapshot(context)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                isOptimizing = false
                Toast.makeText(context, "تم تنظيف الملفات المؤقتة وتحديث الحالة", Toast.LENGTH_SHORT).show()
                runLiveDiagnosticsAndHealing()
            }
        }
    }

    AppleCard(modifier = modifier.testTag("spatial_diagnostics_hub")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "حالة الخدمات",
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "فحص حالة الخدمات",
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = theme.success.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    "${readinessReport.percentage}% جاهزية",
                                    color = theme.success,
                                    fontSize = 10.sp,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "تحقق فوري من اتصال المحركات السحابية والمحلية",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = NotoSansFont
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { optimizeCacheAndRoutes() },
                        modifier = Modifier.size(48.dp).testTag("optimize_cache_btn")
                    ) {
                        if (isOptimizing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.warning, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CleaningServices, contentDescription = "تنظيف الكاش", tint = theme.warning, modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = { runLiveDiagnosticsAndHealing() },
                        modifier = Modifier.size(48.dp).testTag("run_diagnostics_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة الفحص", tint = theme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (scanCompletedMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = theme.success.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = theme.success, modifier = Modifier.size(16.dp))
                        Text(scanCompletedMessage!!, color = theme.success, fontSize = 11.5.sp, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Services Grid Cards
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(serviceStatuses, key = { it.id }) { svc ->
                    val statusColor = when {
                        svc.isLive -> theme.success
                        svc.isConfigured -> theme.warning
                        else -> theme.textSecondary
                    }
                    val statusLabel = when {
                        svc.isLive && svc.id == "kokoro" -> "نشط محلياً"
                        svc.isLive -> "متصل"
                        svc.isConfigured -> "غير متحقق"
                        else -> "غير مفعّل"
                    }

                    Surface(
                        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.55f else 0.45f),
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(184.dp)
                            .then(
                                if (onNavigateToKeys != null) Modifier.clickable(onClick = onNavigateToKeys) else Modifier
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(svc.accentColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(svc.icon, null, tint = svc.accentColor, modifier = Modifier.size(16.dp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        statusLabel,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                svc.name,
                                color = theme.textPrimary,
                                fontSize = 12.5.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                svc.category,
                                color = theme.textSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = NotoSansFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (svc.isLive) (if (svc.latencyMs != null) "${svc.latencyMs} ms" else "جاهز") else svc.fallbackEngine,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontFamily = NotoSansFont,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    svc.providerBadge,
                                    color = theme.textSecondary,
                                    fontSize = 9.5.sp,
                                    fontFamily = NotoSansFont
                                )
                            }
                        }
                    }
                }
            }

            if (onNavigateToKeys != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الميزات التي تفتقد مفتاحاً تبقى معطّلة حتى إضافته.",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = NotoSansFont,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onNavigateToKeys) {
                        Icon(Icons.Default.Key, null, tint = theme.primary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "إدارة المفاتيح",
                            color = theme.primary,
                            fontSize = 12.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
