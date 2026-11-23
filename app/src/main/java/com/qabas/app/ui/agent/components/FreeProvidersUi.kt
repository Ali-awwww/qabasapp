package com.qabas.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*


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
            color = ThemeSuccess,
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
            color = PresetPrimary,
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
            color = ThemeWarning,
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
            color = QabasGold,
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
            color = DeepSlate,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, ThemeSuccess.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ThemeSecondarySurface,
                                DeepSlate,
                                ThemeSecondarySurface
                            )
                        )
                    )
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ThemeSecondarySurface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                "🎁 دليل المزودات المجانية ونماذج الكود",
                                color = TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "احصل على مفاتيح API مجانية لتشغيل نماذج البرمجة بدون دفع",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(ThemeSecondarySurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = ThemeSeparator)

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
                            color = if (isSelected) ThemeSuccess else ThemeSecondarySurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (isSelected) ThemeSuccess else ThemeSeparator)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) TextPrimary else PresetPrimary,
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
        color = CardSurface,
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
                    color = TextPrimary,
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
                color = DeepSlate,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ThemeSeparator),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("النموذج البرمجي الموصى به: ", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(
                            item.recommendedModel,
                            color = PresetPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        item.modelDescription,
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "🎁 الميزات المجانية: ${item.freePerks}",
                        color = ThemeSuccess,
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
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeSecondarySurface),
                    border = BorderStroke(1.dp, item.color.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ رابط المفتاح المجاني 📋", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp)
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
                    placeholder = { Text("الصق مفتاح ${item.name} هنا (${item.keyPrefix})...", color = TextTertiary, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DeepSlate,
                        unfocusedContainerColor = DeepSlate,
                        focusedBorderColor = item.color,
                        unfocusedBorderColor = ThemeSeparator
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
                    colors = ButtonDefaults.buttonColors(containerColor = item.color, disabledContainerColor = ThemeSecondarySurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("تفعيل وحفظ 💾", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
