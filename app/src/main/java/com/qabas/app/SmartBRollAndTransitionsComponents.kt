package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qabas.app.ui.theme.*

/**
 * BRoll Selection Modal for browsing and attaching B-Roll footage to scenes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BRollSelectionModal(
    sceneTitle: String,
    currentMediaUrl: String?,
    onDismiss: () -> Unit,
    onSelectBRoll: (BRollItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("الجميع") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<BRollItem?>(null) }

    val filteredList = remember(selectedCategory, searchQuery) {
        val base = BRollEngine.getBRollForCategory(selectedCategory)
        if (searchQuery.isBlank()) base else {
            base.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.keywords.any { k -> k.contains(searchQuery, ignoreCase = true) } 
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepSlate,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(GoldPrimary.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("محرك B-Roll الذكي 🎬", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("اختر المقطع المرئي الأنسب لمشهد: $sceneTitle", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في مكتبة B-Roll الإسلامية...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(BRollEngine.categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontFamily = NotoSansFont, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = Color.Black,
                            containerColor = CardSurface,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF1E293B),
                            selectedBorderColor = GoldPrimary
                        )
                    )
                }
            }

            // B-Roll Items Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                items(filteredList) { item ->
                    val isChosen = selectedItem?.id == item.id || currentMediaUrl == item.videoUrl
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedItem = item
                                onSelectBRoll(item)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.5.dp, if (isChosen) GoldPrimary else Color(0xFF1E293B))
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                            )
                                        )
                                )
                                Surface(
                                    color = GoldPrimary,
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = item.suggestedTransition,
                                        color = Color.Black,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(item.title, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                Text(item.category, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Live Template Customizer Dialog
 */
@Composable
fun LiveTemplateCustomizerDialog(
    currentTemplateName: String,
    onDismiss: () -> Unit,
    onApplyTemplate: (Template, String, String) -> Unit
) {
    var selectedTemplate by remember { 
        mutableStateOf(AppServices.templates.find { it.name == currentTemplateName } ?: AppServices.templates.first()) 
    }
    var selectedTransition by remember { mutableStateOf("Dissolve") }
    var transitionSpeed by remember { mutableFloatStateOf(0.8f) }
    var brollDensity by remember { mutableFloatStateOf(0.75f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = GoldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تخصيص القالب التفاعلي والانتقالات 🎨", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("اختر القالب البصري المطلوب:", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppServices.templates.forEach { tmpl ->
                        val isSelected = selectedTemplate.id == tmpl.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTemplate = tmpl },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) DeepSlate else CardSurface),
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedTemplate = tmpl },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(tmpl.name, color = if (isSelected) GoldPrimary else Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("الألوان: ${tmpl.colors} | الانتقال: ${tmpl.transitions}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF1E293B))

                Text("تأثير الانتقال بين المشاهد (Transitions):", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BRollEngine.availableTransitions) { trans ->
                        val isChosen = selectedTransition == trans.id
                        FilterChip(
                            selected = isChosen,
                            onClick = { selectedTransition = trans.id },
                            label = { Text(trans.nameAr.split(" ")[0], fontFamily = NotoSansFont, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = Color.Black,
                                containerColor = DeepSlate,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("سرعة الانتقال السينمائي:", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                        Text("${String.format("%.1f", transitionSpeed)} ثانية", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = transitionSpeed,
                        onValueChange = { transitionSpeed = it },
                        valueRange = 0.3f..1.8f,
                        colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("كثافة إدراج B-Roll الذكي:", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                        Text("${(brollDensity * 100).toInt()}%", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = brollDensity,
                        onValueChange = { brollDensity = it },
                        valueRange = 0.25f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplyTemplate(selectedTemplate, selectedTransition, String.format("%.1f", transitionSpeed))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text("تطبيق القالب والانتقالات ✦", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = CardSurface
    )
}

/**
 * Scene BRoll and Transition Badge Component
 */
@Composable
fun SceneBRollAndTransitionBadge(
    sceneIndex: Int,
    transitionType: String,
    mediaUrl: String?,
    onOpenBRollSelector: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MovieFilter, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("الانتقال: ", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                Text(transitionType, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onOpenBRollSelector,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GoldPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (mediaUrl.isNullOrBlank()) "اختيار B-Roll ذكي 🎬" else "تغيير B-Roll 🎬", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
