package com.qabas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qabas.app.ui.theme.QabasElevationTokens
import com.qabas.app.ui.theme.QabasShapeTokens
import com.qabas.app.ui.theme.QabasSpacingTokens
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont

/**
 * شريط التنقل القياسي للتطبيق. يحافظ على مساحة لمس لا تقل عن 48dp ويدعم RTL عبر أيقونة Compose.
 */
@Composable
fun QabasTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String = "qabas_back_button",
    largeTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val theme = QabasTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(theme.background.copy(alpha = 0.96f))
            .padding(top = QabasSpacingTokens.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = QabasSpacingTokens.sm),
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
                        tint = theme.primary
                    )
                }
            }
            if (!largeTitle) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(QabasSpacingTokens.xxs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        if (largeTitle) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QabasSpacingTokens.lg)
                    .padding(bottom = QabasSpacingTokens.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = theme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textSecondary
                    )
                }
            }
        }
        HorizontalDivider(color = theme.separator.copy(alpha = 0.55f))
    }
}

/** سطح المحتوى الأساسي، وهو نقطة التقاء البطاقات في جميع الأقسام. */
@Composable
fun QabasDesignCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = QabasTheme.current.cardSurface,
    shape: Shape = QabasShapeTokens.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    val border = BorderStroke(
        width = 1.dp,
        color = theme.separator.copy(alpha = if (theme.isDark) 0.62f else 0.38f)
    )
    val elevation = if (theme.isDark) QabasElevationTokens.flat else QabasElevationTokens.raised
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            shadowElevation = elevation,
            border = border
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            shadowElevation = elevation,
            border = border,
            content = { Column(content = content) }
        )
    }
}

/** قسم مجمّع للقوائم والإعدادات ببطاقة وحدود ومسافات موحدة. */
@Composable
fun QabasGroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QabasSpacingTokens.xs)
    ) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = QabasSpacingTokens.md)
            )
        }
        QabasDesignCard(content = content)
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = QabasSpacingTokens.md)
            )
        }
    }
}

@Composable
fun QabasPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val theme = QabasTheme.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = QabasShapeTokens.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.primary,
            contentColor = theme.onPrimary,
            disabledContainerColor = theme.secondarySurface,
            disabledContentColor = theme.textSecondary
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = theme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(QabasSpacingTokens.xs))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun QabasSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = QabasTheme.current.primary
) {
    val theme = QabasTheme.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = QabasShapeTokens.control,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accentColor,
            disabledContentColor = theme.textSecondary
        )
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(QabasSpacingTokens.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
