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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun VideoImportSection(
    state: ProjectState,
    onStateChange: (ProjectState) -> Unit,
    onProceed: () -> Unit,
    onChangeSource: () -> Unit,
    onPickVideo: (androidx.activity.result.PickVisualMediaRequest) -> Unit
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
                .border(1.dp, Color(0xFF34D399).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    Translator.tr("المدخل المختار: فيديو من الجهاز 🎬"),
                    color = Color(0xFF34D399),
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
            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF34D399).copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Translator.tr("استيراد فيديو من الجهاز 🎬"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        Translator.tr("إعادة المونتاج، تفريغ الصوت، وتطبيق قوالب قبس"),
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
                        onPickVideo(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                border = BorderStroke(1.5.dp, Color(0xFF34D399).copy(alpha = 0.5f))
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
                        color = Color(0xFF34D399).copy(alpha = 0.18f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(34.dp))
                        }
                    }
                    Text(
                        Translator.tr("اضغط لاختيار فيديو من المعرض"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        Translator.tr("يدعم صيغ MP4 و MOV بدقة عالية"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onPickVideo(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            Translator.tr("فتح معرض الفيديوهات 📂"),
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            // Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF34D399).copy(alpha = 0.15f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.sourceLabel ?: Translator.tr("فيديو مختار"),
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = Translator.tr("جاهز للمونتاج والتعديل ✓"),
                                    color = Color(0xFF34D399),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Action buttons row (Change / Remove)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onPickVideo(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("تغيير الفيديو"), color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
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
                            val desc = state.inputText.ifBlank { "مشروع فيديو مستورد: ${state.sourceLabel ?: "مقطع مرئي"}" }
                            onStateChange(
                                state.copy(
                                    sourceType = "video",
                                    inputText = desc
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
                            Translator.tr("متابعة إلى المونتاج والتعديل 🚀"),
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
