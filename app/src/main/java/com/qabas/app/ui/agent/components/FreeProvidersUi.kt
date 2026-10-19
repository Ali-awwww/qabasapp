package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat


data class FreeProviderSuggestion(
    val id: String,
    val name: String,
    val providerBadge: String,
    val color: Color,
    val recommendedModel: String,
    val modelDescription: String,
    val freePerks: String,
    val keyUrl: String,
    val keyPrefix: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreeProvidersDialog(
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    val suggestions = listOf(
        FreeProviderSuggestion(
            id = "gemini",
            name = "Google AI Studio",
            providerBadge = "أفضل نموذج كود أندرويد مجاني ⚡",
            color = Color(0xFF10B981),
            recommendedModel = "gemini-2.0-flash / gemini-1.5-pro",
            modelDescription = "نموذج ذكي وسريع متخصص في Kotlin و Jetpack Compose واستئصال الأخطاء.",
            freePerks = "مجاني 100% بدون بطاقة ائتمان — 15 طلب/دقيقة و 1,000,000 توكن/دقيقة.",
            keyUrl = "https://aistudio.google.com/app/apikey",
            keyPrefix = "AIzaSy..."
        ),
        FreeProviderSuggestion(
            id = "openrouter",
            name = "OpenRouter AI (Qwen Coder)",
            providerBadge = "أقوى نموذج مفتوح المصدر للكود 🧠",
            color = Color(0xFF38BDF8),
            recommendedModel = "qwen/qwen-2.5-coder-32b-instruct:free",
            modelDescription = "نموذج Qwen 2.5 Coder المصنف كأفضل نموذج مفتوح المصدر لكتابة وبناء الشفرات البرمجية.",
            freePerks = "يوفر أكثر من 10 نماذج برمجية مجانية بالكامل مجاناً.",
            keyUrl = "https://openrouter.ai/keys",
            keyPrefix = "sk-or-v1-..."
        ),
        FreeProviderSuggestion(
            id = "groq",
            name = "Groq Cloud Inference",
            providerBadge = "استجابة فائقة السرعة (300+ Token/s) 🚀",
            color = Color(0xFFF59E0B),
            recommendedModel = "llama-3.3-70b-versatile / qwen-2.5-coder",
            modelDescription = "معالج LPU خارق لتنفيذ المهام البرمجية والتسليم المباشر في أجزاء من الثانية.",
            freePerks = "كوتا مجانية سريعة جداً بحد 30 طلب/دقيقة.",
            keyUrl = "https://console.groq.com/keys",
            keyPrefix = "gsk_..."
        ),
        FreeProviderSuggestion(
            id = "huggingface",
            name = "HuggingFace Hub",
            providerBadge = "مكتبة النماذج البرمجية 🤖",
            color = Color(0xFFFCD34D),
            recommendedModel = "Qwen/Qwen2.5-Coder-32B-Instruct",
            modelDescription = "استدلال مجاني مباشر عبر نماذج HuggingFace المخصصة للتطوير.",
            freePerks = "مفتاح Access Token مجاني بالكامل بدون انتهاء صلاحية.",
            keyUrl = "https://huggingface.co/settings/tokens",
            keyPrefix = "hf_..."
        )
    )

    val filtered = if (selectedFilter == "ALL") suggestions else suggestions.filter { it.id == selectedFilter }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            color = Color(0xFF070D18),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF091528),
                                Color(0xFF050B14),
                                Color(0xFF09121F)
                            )
                        )
                    )
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C192E))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                "🎁 دليل المزودات المجانية ونماذج الكود",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "احصل على مفاتيح API مجانية لتشغيل نماذج البرمجة بدون دفع",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to "الكل (4)",
                        "gemini" to "Google Gemini ⚡",
                        "openrouter" to "OpenRouter (Qwen Coder) 🧠",
                        "groq" to "Groq Fast 🚀",
                        "huggingface" to "HuggingFace 🤖"
                    )
                    filters.forEach { (id, label) ->
                        val isSelected = selectedFilter == id
                        Surface(
                            onClick = { selectedFilter = id },
                            color = if (isSelected) Color(0xFF059669) else Color(0xFF132238),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF34D399) else Color(0xFF1E2D4A))
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF93C5FD),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Providers List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        FreeProviderCard(
                            item = item,
                            onCopyUrl = { url ->
                                clipboard.setText(AnnotatedString(url))
                                Toast.makeText(context, "تم نسخ رابط موقع المفتاح! افتحه في المتصفح للحصول على المفتاح 📋", Toast.LENGTH_SHORT).show()
                            },
                            onSaveKey = { key ->
                                onSaveKey(key)
                                Toast.makeText(context, "تم حفظ وتفعيل المفتاح بنجاح! 🚀", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FreeProviderCard(
    item: FreeProviderSuggestion,
    onCopyUrl: (String) -> Unit,
    onSaveKey: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }

    Surface(
        color = Color(0xFF09182A),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, item.color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Surface(
                    color = item.color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, item.color)
                ) {
                    Text(
                        text = item.providerBadge,
                        color = item.color,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF040A14),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("النموذج البرمجي الموصى به: ", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                        Text(
                            item.recommendedModel,
                            color = GoldPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        item.modelDescription,
                        color = Color(0xFFCBD5E1),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "🎁 الميزات المجانية: ${item.freePerks}",
                        color = Color(0xFF34D399),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onCopyUrl(item.keyUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15263F)),
                    border = BorderStroke(1.dp, item.color.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ رابط المفتاح المجاني 📋", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick In-Card Key Saver
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("الصق مفتاح ${item.name} هنا (${item.keyPrefix})...", color = Color(0xFF64748B), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF030712),
                        unfocusedContainerColor = Color(0xFF030712),
                        focusedBorderColor = item.color,
                        unfocusedBorderColor = Color(0xFF1E2D4A)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            onSaveKey(keyInput)
                            keyInput = ""
                        }
                    },
                    enabled = keyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = item.color, disabledContainerColor = Color(0xFF1E2D4A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("تفعيل وحفظ 💾", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
