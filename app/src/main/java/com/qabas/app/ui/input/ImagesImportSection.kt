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
fun ImagesImportSection(
    state: ProjectState,
    onStateChange: (ProjectState) -> Unit,
    onProceed: () -> Unit,
    onChangeSource: () -> Unit,
    onPickImages: (androidx.activity.result.PickVisualMediaRequest) -> Unit
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
                .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    Translator.tr("المدخل المختار: ألبوم صور 🖼️"),
                    color = Color(0xFFFBBF24),
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
            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFBBF24).copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Translator.tr("استيراد ألبوم صور 🖼️"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        Translator.tr("تحويل الصور والتصاميم إلى ريلز متحرك مع مؤثرات بصرية"),
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
                        onPickImages(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                border = BorderStroke(1.5.dp, Color(0xFFFBBF24).copy(alpha = 0.5f))
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
                        color = Color(0xFFFBBF24).copy(alpha = 0.18f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(34.dp))
                        }
                    }
                    Text(
                        Translator.tr("اضغط لاختيار صور من المعرض"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        Translator.tr("يمكنك اختيار حتى 15 صورة (JPEG, PNG, WEBP) لتوليد المشاهد"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onPickImages(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translator.tr("فتح المعرض واختيار الصور"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // Thumbnails Preview & Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFBBF24).copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${state.sourceUris.size} " + Translator.tr("صور مختارة"),
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = Translator.tr("جاهزة للتحويل إلى مشاهد ريلز ✓"),
                                    color = Color(0xFF34D399),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onPickImages(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .background(Color(0xFF1E293B), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = Translator.tr("إضافة صور"), tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                        }
                    }

                    // Image Thumbnails Carousel
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(state.sourceUris) { index, uriString ->
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = uriString,
                                    contentDescription = "صورة ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Badge number
                                Surface(
                                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = NotoSansFont,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = {
                                        val updated = state.sourceUris.filterIndexed { i, _ -> i != index }
                                        onStateChange(
                                            state.copy(
                                                sourceUris = updated,
                                                sourceLabel = if (updated.isNotEmpty()) "${updated.size} صور مختارة" else null
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = Translator.tr("حذف"), tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        item {
                            // Add More Box in Carousel
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0B0F19))
                                    .clickable {
                                        onPickImages(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(Translator.tr("إضافة"), color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                                }
                            }
                        }
                    }

                    // Script / Topic hint input
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = { onStateChange(state.copy(inputText = it)) },
                        placeholder = {
                            Text(
                                Translator.tr("اكتب فكرة أو موضوع الريلز (اختياري)..."),
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontFamily = NotoSansFont
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0B0F19),
                            unfocusedContainerColor = Color(0xFF0B0F19),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        maxLines = 2
                    )

                    // Action buttons row (Add More / Clear)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onPickImages(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("إضافة صور"), color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
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
                            Text(Translator.tr("مسح الكل 🗑️"), color = Color(0xFFF87171), fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Big Proceed Button
                    Button(
                        onClick = {
                            val desc = state.inputText.ifBlank { "مشروع ريلز متحرك من ${state.sourceUris.size} صور" }
                            onStateChange(
                                state.copy(
                                    sourceType = "images",
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
