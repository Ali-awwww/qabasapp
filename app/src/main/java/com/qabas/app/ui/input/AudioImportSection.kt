package com.qabas.app.ui.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ProjectState
import com.qabas.app.Translator
import com.qabas.app.ui.theme.*

@Composable
fun AudioImportSection(
    state: ProjectState,
    onStateChange: (ProjectState) -> Unit,
    onProceed: () -> Unit,
    onChangeSource: () -> Unit,
    onPickAudio: (String) -> Unit,
    onOpenAudioLibrary: () -> Unit,
    audioUsageMode: String,
    onAudioUsageModeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Indicator / Switcher Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151B2B), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    Translator.tr("المدخل المختار: ملف صوتي 🎙️"),
                    color = Color(0xFF38BDF8),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            TextButton(
                onClick = {
                    onChangeSource()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    Translator.tr("تغيير ↩️"),
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }
        }

        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Translator.tr("استيراد ملف صوتي 🎙️"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        Translator.tr("تفريغ، تعليق صوتي، أو مسار صوتي أساسي للمشروع"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (state.sourceUris.isEmpty()) {
            // Picker Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPickAudio("audio/*")
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(34.dp))
                        }
                    }
                    Text(
                        Translator.tr("اضغط لاختيار ملف صوتي من الجهاز"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        Translator.tr("يدعم صيغ MP3 و WAV و M4A و AAC و OGG"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onPickAudio("audio/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                Translator.tr("من الجهاز 📂"),
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenAudioLibrary,
                            border = BorderStroke(1.dp, GoldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                Translator.tr("مكتبة قبس 🎙️"),
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            // File Info & Usage Choice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.sourceLabel ?: Translator.tr("ملف صوتي مختار"),
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = Translator.tr("ملف صوتي مستورد ✓"),
                                    color = Color(0xFF38BDF8),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Usage Mode Choice
                    Text(
                        Translator.tr("اختر كيفية توظيف هذا الصوت:"),
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // Option A: Voiceover
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAudioUsageModeChange("voiceover") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (audioUsageMode == "voiceover") Color(0xFF1A2742) else Color(0xFF0F172A)
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (audioUsageMode == "voiceover") Color(0xFF38BDF8) else Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = audioUsageMode == "voiceover",
                                onClick = { onAudioUsageModeChange("voiceover") },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF38BDF8),
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    Translator.tr("استخدام كتعليق صوتي (Voiceover) 🎙️"),
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    Translator.tr("اعتماد هذا الصوت كإلقاء صوتي أساسي وتوليد مشاهد ملائمة لكلماته"),
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Option B: Primary Material
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAudioUsageModeChange("primary") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (audioUsageMode == "primary") Color(0xFF1A2742) else Color(0xFF0F172A)
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (audioUsageMode == "primary") Color(0xFF38BDF8) else Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = audioUsageMode == "primary",
                                onClick = { onAudioUsageModeChange("primary") },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF38BDF8),
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    Translator.tr("استخدامه كمادة أساسية للمشروع 🎵"),
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    Translator.tr("اعتماد الصوت كمسار أساسي للمشروع مع بناء المشاهد والسكريبت وفقاً له"),
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Action buttons row (Change / Remove)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onPickAudio("audio/*")
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("تغيير الملف"), color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onStateChange(
                                    state.copy(
                                        sourceUris = emptyList(),
                                        sourceLabel = null
                                    )
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("إزالة 🗑️"), color = Color(0xFFF87171), fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Big Proceed Button
                    Button(
                        onClick = {
                            val cleanName = state.sourceLabel?.substringBefore(" (") ?: "ملف صوتي"
                            val desc = if (audioUsageMode == "voiceover") {
                                state.inputText.ifBlank { "مشروع بتعليق صوتي: $cleanName" }
                            } else {
                                state.inputText.ifBlank { "مشروع صوتي أساسي: $cleanName" }
                            }
                            onStateChange(
                                state.copy(
                                    sourceType = "audio",
                                    inputText = desc,
                                    voiceOver = if (audioUsageMode == "voiceover") "صوت مخصص: $cleanName" else state.voiceOver
                                )
                            )
                            onProceed()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            Translator.tr("متابعة إلى المسار الذكي 🚀"),
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Back Button
        TextButton(
            onClick = {
                onChangeSource()
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(Translator.tr("تغيير نوع المدخل والعودة"), color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
        }
    }
}
