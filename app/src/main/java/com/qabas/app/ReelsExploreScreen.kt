package com.qabas.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*

/**
 * صفحة «استكشف» للريلز (الدفعة 3).
 *
 * - «ريلز اليوم»: المقاطع المميزة من المطوّر (is_featured_by_dev) فقط.
 * - وسوم رائجة: من الوسوم الحقيقية للمقاطع المحمّلة، مرتبة بتكرارها.
 * - صفحات وسم وصوت: النقر على وسم أو صوت يعرض مقاطعه في شبكة.
 * - الشبكة: 3 أعمدة بمصغّرات، وعدد المشاهدات الحقيقي فقط — بلا مدة،
 *   لأن مدة المقطع غير موجودة في قاعدة البيانات.
 * - النقر على مصغّر يقفز للمقطع في الخلاصة عبر onJumpToReel.
 *
 * حدود صريحة: صفحات الصوت تجمّع حسب القارئ (اسم الصانع) لا حسب المقطع
 * الصوتي، لأنه لا عمود audio_track_id في ReelRow.
 */
@Composable
fun ReelsExploreScreen(
    reels: List<SupabaseServices.ReelRow>,
    onBack: () -> Unit,
    onJumpToReel: (String) -> Unit,
    onOpenCreator: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val theme = QabasTheme.current
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedReciter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler {
        when {
            selectedTag != null -> selectedTag = null
            selectedReciter != null -> selectedReciter = null
            else -> onBack()
        }
    }

    val featuredToday = remember(reels) { reels.filter { it.isFeaturedByDev } }
    val trendingTags = remember(reels) {
        reels.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }
    val reciters = remember(reels) {
        reels.groupBy { it.userName.trim().ifBlank { "صانع محتوى" } }
            .entries.sortedByDescending { it.value.size }
            .take(10)
            .map { it.key to it.value.size }
    }
    val visibleReels = remember(reels, selectedTag, selectedReciter, searchQuery) {
        reels.filter { reel ->
            val matchTag = selectedTag == null || reel.tags.contains(selectedTag)
            val matchReciter = selectedReciter == null ||
                reel.userName.trim().ifBlank { "صانع محتوى" } == selectedReciter
            val matchQuery = searchQuery.isBlank() ||
                reel.title.contains(searchQuery, ignoreCase = true) ||
                reel.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                (reel.userName?.contains(searchQuery, ignoreCase = true) == true)
            matchTag && matchReciter && matchQuery
        }
    }

    val pageTitle = when {
        selectedTag != null -> "وسم: $selectedTag"
        selectedReciter != null -> "صوت: $selectedReciter"
        else -> "استكشف الريلز"
    }

    Scaffold(
        modifier = Modifier.testTag("reels_explore_screen"),
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = pageTitle,
                subtitle = when {
                    selectedTag != null || selectedReciter != null ->
                        "${visibleReels.size} مقطع"
                    reels.isEmpty() -> "لا توجد مقاطع محمّلة بعد"
                    else -> "${reels.size} مقطع • ${trendingTags.size} وسم"
                },
                onBack = {
                    when {
                        selectedTag != null -> selectedTag = null
                        selectedReciter != null -> selectedReciter = null
                        else -> onBack()
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في العناوين والوسوم والصنّاع…", fontFamily = CairoFont) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            if (selectedTag == null && selectedReciter == null) {
                if (featuredToday.isNotEmpty()) {
                    item {
                        ExploreSectionTitle(icon = Icons.Default.Star, title = "ريلز اليوم")
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(featuredToday, key = { it.id }) { reel ->
                                ExploreFeaturedCard(
                                    reel = reel,
                                    onClick = { onJumpToReel(reel.id) }
                                )
                            }
                        }
                    }
                }

                if (trendingTags.isNotEmpty()) {
                    item {
                        ExploreSectionTitle(icon = Icons.Default.Tag, title = "وسوم رائجة")
                    }
                    item {
                        FlowRowTags(
                            tags = trendingTags,
                            onTagClick = { selectedTag = it }
                        )
                    }
                }

                if (reciters.isNotEmpty()) {
                    item {
                        ExploreSectionTitle(icon = Icons.Default.MusicNote, title = "أصوات رائجة")
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reciters.forEach { (reciter, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedReciter = reciter }
                                        .background(
                                            color = theme.primary.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = reciter,
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$count مقطع",
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                ExploreSectionTitle(
                    icon = Icons.Default.GridView,
                    title = when {
                        selectedTag != null -> "مقاطع الوسم"
                        selectedReciter != null -> "مقاطع الصوت"
                        else -> "كل المقاطع"
                    }
                )
            }

            if (visibleReels.isEmpty()) {
                item {
                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "لا توجد مقاطع مطابقة هنا",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "جرّب وسماً أو صوتاً آخر، أو عد للخلاصة.",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // شبكة 3 أعمدة كصفوف داخل القائمة نفسها (لا شبكة متداخلة).
                items(visibleReels.chunked(3)) { rowReels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowReels.forEach { reel ->
                            Box(modifier = Modifier.weight(1f)) {
                                ExploreReelThumb(
                                    reel = reel,
                                    onClick = { onJumpToReel(reel.id) },
                                    onCreatorClick = {
                                        onOpenCreator(reel.userId, reel.userName, reel.userAvatar)
                                    }
                                )
                            }
                        }
                        repeat((3 - rowReels.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    val theme = QabasTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun FlowRowTags(tags: List<String>, onTagClick: (String) -> Unit) {
    // صفوف وسوم ملتفّة بدون اعتماديات إضافية.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.chunked(3).forEach { rowTags ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTags.forEach { tag ->
                    AssistChip(
                        onClick = { onTagClick(tag) },
                        label = { Text("#$tag", fontFamily = CairoFont, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreFeaturedCard(
    reel: SupabaseServices.ReelRow,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ExploreThumbImage(
                reel = reel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = reel.title.ifBlank { "مقطع" },
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatExploreCount(reel.viewsCount) + " مشاهدة",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ExploreReelThumb(
    reel: SupabaseServices.ReelRow,
    onClick: () -> Unit,
    onCreatorClick: () -> Unit
) {
    val theme = QabasTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable { onClick() }
    ) {
        ExploreThumbImage(reel = reel, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(6.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "تشغيل",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = reel.title.ifBlank { "مقطع" },
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatExploreCount(reel.viewsCount) + " مشاهدة",
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = CairoFont,
                fontSize = 10.sp
            )
            Text(
                text = reel.userName,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = CairoFont,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onCreatorClick() }
            )
        }
    }
}

@Composable
private fun ExploreThumbImage(reel: SupabaseServices.ReelRow, modifier: Modifier = Modifier) {
    val thumb = reel.thumbnailUrl?.trim()?.takeIf { it.isNotBlank() }
    if (thumb != null) {
        AsyncImage(
            model = thumb,
            contentDescription = reel.title.ifBlank { "مقطع" },
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF0A2927)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = reel.title.ifBlank { "مقطع" },
                color = Color(0xFFE8D8B5),
                fontFamily = CairoFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * تنسيق عدد المشاهدات الحقيقي فقط (من صف المقطع)؛ لا تُعرض مدة لأنها
 * غير موجودة في قاعدة البيانات.
 */
private fun formatExploreCount(count: Int): String {
    val safe = count.coerceAtLeast(0)
    return when {
        safe >= 1_000_000 ->
            "%.1f".format(safe / 1_000_000.0).trimEnd('0').trimEnd('.') + " م"
        safe >= 1_000 ->
            "%.1f".format(safe / 1_000.0).trimEnd('0').trimEnd('.') + " ألف"
        else -> safe.toString()
    }
}
