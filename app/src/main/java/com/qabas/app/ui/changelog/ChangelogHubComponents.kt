package com.qabas.app.ui.changelog

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ChangelogEntity
import com.qabas.app.luxuryCardStyle
import com.qabas.app.ui.theme.*

/**
 * أنواع التحديثات مع أيقوناتها الديناميكية وألوانها النيونية
 */
enum class ChangelogUpdateType(
    val title: String,
    val shortLabel: String,
    val icon: ImageVector
) {
    ALL(
        title = "كافة التحديثات",
        shortLabel = "الكل",
        icon = Icons.Default.AllInclusive,
    ),
    FEATURE(
        title = "ميزات جديدة",
        shortLabel = "ميزة جديدة",
        icon = Icons.Default.AutoAwesome,
    ),
    SECURITY(
        title = "أمان وحماية",
        shortLabel = "أمان",
        icon = Icons.Default.Security,
    ),
    BUG_FIX(
        title = "إصلاح أخطاء",
        shortLabel = "إصلاح",
        icon = Icons.Default.Build
    );

    @Composable
    fun accentColor(): Color = when (this) {
        ALL -> QabasTheme.current.gold
        FEATURE -> QabasTheme.current.primary
        SECURITY -> QabasTheme.current.success
        BUG_FIX -> QabasTheme.current.warning
    }

    @Composable
    fun bgTint(): Color = accentColor().copy(alpha = 0.15f)
}

/**
 * ترتيب وفرز التحديثات
 */
enum class ChangelogSortOrder(
    val title: String,
    val icon: ImageVector
) {
    DATE_DESC("الأحدث تاريخاً / إصداراً", Icons.Default.ArrowDownward),
    DATE_ASC("الأقدم تاريخاً / إصداراً", Icons.Default.ArrowUpward)
}

/**
 * بند تفصيلي داخل التحديث مع تصنيفه وأيقونته
 */
data class ChangelogHighlightItem(
    val rawText: String,
    val cleanText: String,
    val type: ChangelogUpdateType
)

/**
 * تحليل أسطر التحديثات وتعيين الأيقونة والتصنيف المناسب لكل سطر
 */
fun parseHighlightLines(highlights: String): List<ChangelogHighlightItem> {
    return highlights.lines()
        .map { it.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            val lower = line.lowercase()
            val type = when {
                lower.contains("أمان") || lower.contains("حارس") || lower.contains("حماية") ||
                lower.contains("تشفير") || lower.contains("شرع") || lower.contains("خصوصية") ||
                lower.contains("ميثاق") || lower.contains("security") || lower.contains("guard") ||
                lower.contains("auth") || lower.contains("حفظ الأمانات") -> ChangelogUpdateType.SECURITY

                lower.contains("إصلاح") || lower.contains("معالجة") || lower.contains("تسريع") ||
                lower.contains("أداء") || lower.contains("استقرار") || lower.contains("ذاكرة") ||
                lower.contains("تعديل") || lower.contains("حل") || lower.contains("fix") ||
                lower.contains("bug") || lower.contains("crash") || lower.contains("تنظيف") -> ChangelogUpdateType.BUG_FIX

                else -> ChangelogUpdateType.FEATURE
            }
            ChangelogHighlightItem(
                rawText = line,
                cleanText = line,
                type = type
            )
        }
}

/**
 * كشف كافة أنواع التحديثات الموجودة داخل الإصدار
 */
fun ChangelogEntity.detectContainedTypes(): Set<ChangelogUpdateType> {
    val items = parseHighlightLines(highlights)
    val set = items.map { it.type }.toMutableSet()
    val fullText = (title + " " + categoryBadge).lowercase()
    if (fullText.contains("أمان") || fullText.contains("حارس") || fullText.contains("حماية")) {
        set.add(ChangelogUpdateType.SECURITY)
    }
    if (fullText.contains("إصلاح") || fullText.contains("تحسين") || fullText.contains("استقرار")) {
        set.add(ChangelogUpdateType.BUG_FIX)
    }
    if (fullText.contains("إطلاق") || fullText.contains("ميزة") || fullText.contains("جديد") || fullText.contains("رئيسي")) {
        set.add(ChangelogUpdateType.FEATURE)
    }
    if (set.isEmpty()) {
        set.add(ChangelogUpdateType.FEATURE)
    }
    return set
}

/**
 * شريط التحكم والتصفية بنمط السايبرنتك HUD
 */
@Composable
fun ChangelogFilterDock(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedType: ChangelogUpdateType,
    onTypeSelect: (ChangelogUpdateType) -> Unit,
    selectedVersion: String?,
    onVersionSelect: (String?) -> Unit,
    availableVersions: List<String>,
    sortOrder: ChangelogSortOrder,
    onToggleSortOrder: () -> Unit,
    typeCounts: Map<ChangelogUpdateType, Int>,
    totalMatchingCount: Int,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.45f, glowElevation = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardSurface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. شريط البحث الفوري (رقم الإصدار، الكلمات الدلالية)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("changelog_search_input"),
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    Text(
                        "بحث برقم الإصدار (مثل v1.3.7) أو الكلمات المفتاحية...",
                        color = theme.textTertiary,
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = PresetPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "مسح",
                                tint = theme.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PresetPrimary,
                    unfocusedBorderColor = theme.secondarySurface,
                    focusedContainerColor = theme.background,
                    unfocusedContainerColor = theme.background,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
                ),
                singleLine = true
            )

            // 2. تصنيف أنواع التحديثات (أزرار التصفية بالأيقونات الديناميكية)
            Text(
                "تصفية حسب نوع التحديث:",
                color = PresetPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChangelogUpdateType.values().forEach { type ->
                    val isSelected = selectedType == type
                    val count = typeCounts[type] ?: 0

                    Surface(
                        onClick = { onTypeSelect(type) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) type.accentColor().copy(alpha = 0.2f) else theme.secondarySurface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) type.accentColor() else theme.separator
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("filter_type_${type.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                tint = if (isSelected) type.accentColor() else theme.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = type.shortLabel,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                            Surface(
                                color = if (isSelected) type.accentColor() else theme.secondarySurface,
                                shape = CircleShape,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = count.toString(),
                                        color = if (isSelected) DeepSlate else theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. شرائح التصفية السريعة حسب رقم الإصدار
            if (availableVersions.isNotEmpty()) {
                Text(
                    "تصفية سريعة برقم الإصدار:",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // خيار الكل
                    val isAllVersionsSelected = selectedVersion == null
                    Surface(
                        onClick = { onVersionSelect(null) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAllVersionsSelected) PresetPrimary.copy(alpha = 0.22f) else theme.background,
                        border = BorderStroke(
                            0.9.dp,
                            if (isAllVersionsSelected) PresetPrimary else theme.secondarySurface
                        ),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "كافة الإصدارات",
                                color = if (isAllVersionsSelected) PresetPrimary else theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                fontWeight = if (isAllVersionsSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // شرائح الإصدارات
                    availableVersions.forEach { ver ->
                        val isVerSelected = selectedVersion == ver
                        Surface(
                            onClick = {
                                onVersionSelect(if (isVerSelected) null else ver)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isVerSelected) theme.primary.copy(alpha = 0.22f) else theme.background,
                            border = BorderStroke(
                                0.9.dp,
                                if (isVerSelected) theme.primary else theme.secondarySurface
                            ),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("version_chip_$ver")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "v$ver",
                                    color = if (isVerSelected) theme.primary else theme.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isVerSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = theme.secondarySurface, thickness = 0.8.dp)

            // 4. سطر التحكم في الترتيب (الفرز) مع بيان عدد النتائج
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // عدد النتائج
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = PresetPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$totalMatchingCount إصدارات مطابقة",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }

                // زر تبديل الفرز حسب التاريخ
                Surface(
                    onClick = onToggleSortOrder,
                    shape = RoundedCornerShape(8.dp),
                    color = theme.secondarySurface,
                    border = BorderStroke(0.8.dp, theme.secondarySurface),
                    modifier = Modifier.testTag("toggle_sort_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = sortOrder.icon,
                            contentDescription = null,
                            tint = PresetPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = sortOrder.title,
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // زر إعادة تعيين الفلاتر إذا تم اختيار فلتر مخصص
            if (searchQuery.isNotBlank() || selectedType != ChangelogUpdateType.ALL || selectedVersion != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onResetFilters,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = theme.destructive,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "إعادة ضبط خيارات التصفية",
                            color = theme.destructive,
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * بطاقة الإصدار المطورة مع الأيقونات الديناميكية لكل نوع وتصنيف تفصيلي
 */
@Composable
fun EnhancedChangelogCardItem(
    item: ChangelogEntity,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(item.isLatest) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val containedTypes = remember(item) { item.detectContainedTypes() }
    val bulletItems = remember(item.highlights) { parseHighlightLines(item.highlights) }

    // تحديد اللون الأبرز للبطاقة (مباشر من السمة — لا remember هنا لأن QabasTheme.current لا يُقرأ داخل لامدا remember)
    val theme = QabasTheme.current
    val dominantColor = when {
        item.isLatest -> theme.gold
        containedTypes.contains(ChangelogUpdateType.SECURITY) -> theme.success
        containedTypes.contains(ChangelogUpdateType.BUG_FIX) -> theme.warning
        else -> theme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(
                shapeRadius = 18.dp,
                borderAlpha = if (item.isLatest) 0.55f else 0.3f,
                glowElevation = if (item.isLatest) 6.dp else 2.dp
            )
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isLatest) theme.cardSurface else theme.background
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // الصف العلوي التفاعلي (رأس البطاقة)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // وسام رقم الإصدار مع تدرج متوهج
                    Surface(
                        color = if (item.isLatest) dominantColor else theme.secondarySurface,
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(
                            1.dp,
                            if (item.isLatest) dominantColor else dominantColor.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "v${item.versionName}",
                            color = if (item.isLatest) DeepSlate else theme.textPrimary,
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.title,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = if (isExpanded) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // تاريخ الإصدار مع أيقونة التقويم
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.releaseDate,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• بناء #${item.versionCode}",
                                color = theme.textTertiary,
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                // الأيقونات الديناميكية للأنواع الموجودة في التحديث + زر التوسيع
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // عرض شارات الأيقونات الديناميكية
                    containedTypes.forEach { type ->
                        Surface(
                            color = type.bgTint(),
                            shape = CircleShape,
                            border = BorderStroke(0.7.dp, type.accentColor().copy(alpha = 0.5f)),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = type.title,
                                    tint = type.accentColor(),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // سهم التوسيع والطي
                    Surface(
                        color = theme.secondarySurface,
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "طي" else "توسيع",
                                tint = PresetPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // القسم الموسع مع البنود والأيقونات الديناميكية لكل بند
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = theme.separator, thickness = 0.8.dp)

                    Spacer(modifier = Modifier.height(10.dp))

                    // شارة الإصدار الرسمية
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = dominantColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, dominantColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = item.categoryBadge,
                                color = dominantColor,
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${bulletItems.size} بنود مطورة",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // قائمة البنود مع الأيقونات الديناميكية لكل سطر
                    bulletItems.forEach { bullet ->
                        Surface(
                            color = theme.background.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, theme.secondarySurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // أيقونة ديناميكية متوهجة لنوع التحديث
                                Surface(
                                    color = bullet.type.bgTint(),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, bullet.type.accentColor().copy(alpha = 0.5f)),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = bullet.type.icon,
                                            contentDescription = bullet.type.shortLabel,
                                            tint = bullet.type.accentColor(),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = bullet.type.accentColor().copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = bullet.type.shortLabel,
                                                color = bullet.type.accentColor(),
                                                fontFamily = CairoFont,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = bullet.cleanText,
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // أزرار المشاركة والنسخ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val textToShare = "🚀 سجل تحديثات تطبيق «خادم المسلم» v${item.versionName}\n${item.title}\nالتاريخ: ${item.releaseDate}\n\nأهم التحديثات:\n${item.highlights}"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                }
                                context.startActivity(Intent.createChooser(intent, "مشاركة سجل الإصدار"))
                            },
                            border = BorderStroke(0.9.dp, dominantColor.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = dominantColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "مشاركة الإصدار",
                                color = dominantColor,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("خادم المسلم v${item.versionName} - ${item.title}\n${item.highlights}"))
                                Toast.makeText(context, "تم نسخ تفاصيل الإصدار v${item.versionName} للحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(0.9.dp, theme.separator),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "نسخ البنود",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * حالة فارغة عند عدم مطابقة أي إصدار للتصفية
 */
@Composable
fun ChangelogEmptyView(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 16.dp, borderAlpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = PresetPrimary.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FilterAltOff,
                        contentDescription = null,
                        tint = PresetPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                "لا توجد إصدارات مطابقة لمعايير التصفية",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )

            Text(
                "جرب البحث برقم إصدار آخر أو اختر نوع تحديث مختلف.",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = DeepSlate,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "إلغاء التصفية وإظهار الكل",
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}
