package com.qabas.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme

/**
 * Apple HIG Clean Top Navigation Bar (Inline or Large Title)
 */
@Composable
fun AppleTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String = "apple_back_button",
    largeTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(backTestTag)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = theme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (!largeTitle) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions
            )
        }

        if (largeTitle) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = theme.separator.copy(alpha = 0.45f)
        )
    }
}

/**
 * Apple HIG Inset Grouped Card / Section
 */
@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    containerColor: Color = QabasTheme.current.cardSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val border = if (theme.isDark) {
        BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.7f))
    } else {
        BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.35f))
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            shadowElevation = if (theme.isDark) 0.dp else 1.5.dp,
            border = border
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            shadowElevation = if (theme.isDark) 0.dp else 1.5.dp,
            border = border
        ) {
            Column(content = content)
        }
    }
}

/**
 * Apple HIG Grouped Section with optional uppercase/caption header & footer
 */
@Composable
fun AppleGroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        AppleCard(content = content)
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun AppleInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        thickness = 0.5.dp,
        color = QabasTheme.current.separator.copy(alpha = 0.5f)
    )
}

/**
 * Apple iOS Settings-style List Row with 29dp rounded square icon badge
 */
@Composable
fun AppleListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.White,
    iconBackground: Color = QabasTheme.current.primary,
    trailingText: String? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val theme = QabasTheme.current
    val rowAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(iconBackground.copy(alpha = rowAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = (if (destructive) theme.destructive else theme.textPrimary).copy(alpha = rowAlpha),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = theme.textSecondary.copy(alpha = rowAlpha),
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        } else {
            if (!trailingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailingText,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showChevron && onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = theme.textSecondary.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Apple HIG Segmented Control (UISegmentedControl style)
 */
@Composable
fun <T> AppleSegmentedControl(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.9f else 0.75f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items.forEach { (item, label) ->
                val selected = item == selectedItem
                val bg by animateColorAsState(
                    targetValue = if (selected) theme.cardSurface else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "seg_bg"
                )
                Surface(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = bg,
                    shadowElevation = if (selected && !theme.isDark) 1.5.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) theme.textPrimary else theme.textSecondary,
                            fontFamily = CairoFont,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Apple iOS Search Bar
 */
@Composable
fun AppleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "بحث...",
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.9f else 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "بحث",
                tint = theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = theme.textPrimary,
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(theme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "مسح",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ApplePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isLoading: Boolean = false
) {
    val theme = QabasTheme.current
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.primary,
            contentColor = theme.onPrimary,
            disabledContainerColor = theme.secondarySurface,
            disabledContentColor = theme.textSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = theme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun AppleSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = QabasTheme.current.primary
) {
    val theme = QabasTheme.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = if (theme.isDark) 0.16f else 0.10f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) tint else theme.textSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Unified Banner displayed cleanly whenever a cloud/external service is disabled until an API Key or login is provided.
 */
@Composable
fun AppleKeyRequiredBanner(
    serviceName: String,
    reason: String,
    onConfigureClick: (() -> Unit)? = null,
    actionLabel: String = "إعداد المفتاح",
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = theme.warning.copy(alpha = if (theme.isDark) 0.15f else 0.10f),
        border = BorderStroke(0.5.dp, theme.warning.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.warning.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = theme.warning,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$serviceName غير مفعّل",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = reason,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            if (onConfigureClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConfigureClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
