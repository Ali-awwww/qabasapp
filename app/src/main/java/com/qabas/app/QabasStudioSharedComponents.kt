package com.qabas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * زر تبويب مشترك متجاوب مع الوضعين الفاتح والداكن.
 */
@Composable
fun TabHeaderButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .bouncingClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = if (isSelected) theme.primary else theme.textSecondary,
            fontFamily = CairoFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(theme.primary)
            )
        }
    }
}

@Composable
fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    TabHeaderButton(title = title, isSelected = isSelected, onClick = onClick)
}

/**
 * شارة توثيق المصدر الشرعي — تتحقق من وجود مصدر أو تنبّه إلى توثيق النقل الشرعي.
 */
@Composable
fun IslamicSourceAttributionBadge(
    text: String,
    explicitSource: String? = null,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val detectedSource = remember(text, explicitSource) {
        when {
            !explicitSource.isNullOrBlank() -> explicitSource
            text.contains("البخاري") -> "مصدر: صحيح البخاري"
            text.contains("مسلم") -> "مصدر: صحيح مسلم"
            text.contains("الترمذي") -> "مصدر: سنن الترمذي"
            text.contains("أبو داود") || text.contains("أبي داود") -> "مصدر: سنن أبي داود"
            text.contains("النسائي") -> "مصدر: سنن النسائي"
            text.contains("ابن ماجه") || text.contains("ابن ماجة") -> "مصدر: سنن ابن ماجه"
            text.contains("مسند أحمد") || text.contains("الإمام أحمد") -> "مصدر: مسند الإمام أحمد"
            text.contains("سورة ") -> {
                val surahMatch = Regex("سورة\\s+([\\u0600-\\u06FF]+)").find(text)?.value
                surahMatch?.let { "آية من $it" } ?: "اقتباس قرآني مبارك"
            }
            text.contains("﴿") && text.contains("﴾") -> "نص قرآني شريف"
            text.contains("قال رسول الله") || text.contains("عن النبي") || text.contains("ﷺ") -> "حديث نبوي شريف"
            else -> null
        }
    }

    val isReligiousContent = remember(text, explicitSource) {
        !explicitSource.isNullOrBlank() ||
            detectedSource != null ||
            text.contains("الله") ||
            text.contains("القرآن") ||
            text.contains("الرسول") ||
            text.contains("النبي") ||
            text.contains("آية") ||
            text.contains("حديث") ||
            text.contains("تدبر")
    }

    if (!isReligiousContent) return

    val accent = if (detectedSource != null) theme.primary else theme.warning

    Surface(
        color = accent.copy(alpha = if (theme.isDark) 0.16f else 0.10f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                if (detectedSource != null) Icons.Default.Verified else Icons.Default.Info,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = detectedSource ?: "يُفضل مراجعة وتوثيق المصدر الشرعي",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                fontWeight = if (detectedSource != null) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
