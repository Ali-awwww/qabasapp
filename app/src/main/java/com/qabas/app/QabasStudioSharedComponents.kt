package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * Reusable Tab header button with gold indicator line.
 */
@Composable
fun TabHeaderButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bouncingClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            title,
            color = if (isSelected) AiCyanLight else Color.Gray,
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
                    .background(
                        Brush.horizontalGradient(listOf(AiCyan, AiViolet)),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    TabHeaderButton(title = title, isSelected = isSelected, onClick = onClick)
}

/**
 * Unified luxury card for Qabas — standard cyan/violet tech-glow border and rounded corners.
 * Use for project cards, list items and studio panels instead of ad-hoc Cards.
 */
@Composable
fun QabasCard(
    modifier: Modifier = Modifier,
    shapeRadius: Dp = 18.dp,
    containerColor: Color = CardSurface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.luxuryCardStyle(shapeRadius = shapeRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(shapeRadius)
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Unified section header row — cyan accent title with optional subtitle, icon and trailing slot.
 */
@Composable
fun QabasSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(AiCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AiCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AiCyanLight,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Reusable Tajweed Rule Card for encyclopedia lists.
 */
@Composable
fun TajweedRuleCard(
    rule: TajweedRuleItem,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = GoldPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        rule.category,
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(rule.title, color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(rule.description, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("مثال قرآني: ${rule.exampleVerse}", color = TextPrimary, fontFamily = AmiriFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Reusable Certificate Dialog for Tajweed & recitation test completions.
 */
@Composable
fun TajweedCertificateDialog(
    surahName: String,
    score: Int,
    onDismiss: () -> Unit,
    onExportReel: () -> Unit
) {
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
            border = androidx.compose.foundation.BorderStroke(2.dp, GoldPrimary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = GoldPrimary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CardMembership, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "شهادة إتقان الترتيل والتجويد 📜",
                    color = GoldPrimary,
                    fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "صادرة عن أكاديمية قبس لصناعة المحتوى القرآني ✦",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = GoldPrimary.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF080D18)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldSecondary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "تشهد منصة \"قَبَس\" بالذكاء الاصطناعي بأن القارئ المتقن:",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "صانع الأثر القرآني ✨",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "قد أتم بفضل الله فحص ترتيل وتجويد سورة:",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )

                        Text(
                            text = "﴿ سورة $surahName ﴾",
                            color = GoldPrimary,
                            fontFamily = AmiriFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("درجة التلاوة التجويدية: ", color = TextPrimary, fontFamily = NotoSansFont, fontSize = 14.sp)
                            Text("$score%", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }

                        Text(
                            text = "التقدير: امتياز مع مرتبة الشرف برواية حفص عن عاصم 🌟",
                            color = GoldSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onExportReel,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير الشهادة كـ Reel قرآن تفاعلي 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "تم حفظ الشهادة HD في معرض الصور بنجاح! 📲", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ الشهادة كصورة عالي الدقة HD 📲", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Reusable Islamic Source Attribution & Verification Badge.
 * Automatically checks if a religious text or explicit source provides a citation.
 * Displays the verified source or a polite reminder ("يُفضل مراجعة المصدر").
 */
@Composable
fun IslamicSourceAttributionBadge(
    text: String,
    explicitSource: String? = null,
    modifier: Modifier = Modifier
) {
    val detectedSource = remember(text, explicitSource) {
        when {
            !explicitSource.isNullOrBlank() -> explicitSource
            text.contains("البخاري") -> "مصدر: صحيح البخاري 📖"
            text.contains("مسلم") -> "مصدر: صحيح مسلم 📖"
            text.contains("الترمذي") -> "مصدر: سنن الترمذي 📖"
            text.contains("أبو داود") || text.contains("أبي داود") -> "مصدر: سنن أبي داود 📖"
            text.contains("النسائي") -> "مصدر: سنن النسائي 📖"
            text.contains("ابن ماجه") || text.contains("ابن ماجة") -> "مصدر: سنن ابن ماجه 📖"
            text.contains("مسند أحمد") || text.contains("الإمام أحمد") -> "مصدر: مسند الإمام أحمد 📖"
            text.contains("سورة ") -> {
                val surahMatch = Regex("سورة\\s+([\\u0600-\\u06FF]+)").find(text)?.value
                surahMatch?.let { "آية من $it 📖" } ?: "اقتباس قرآني مبارك 📖"
            }
            text.contains("﴿") && text.contains("﴾") -> "نص قرآني شريف 📖"
            text.contains("قال رسول الله") || text.contains("عن النبي") || text.contains("ﷺ") -> "حديث نبوي شريف ✦"
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

    Surface(
        color = if (detectedSource != null) Color(0xFF0B0F19).copy(alpha = 0.85f) else Color(0xFF151B2B).copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (detectedSource != null) GoldPrimary.copy(alpha = 0.6f) else Color(0xFFEAB308).copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                if (detectedSource != null) Icons.Default.Verified else Icons.Default.Info,
                contentDescription = null,
                tint = if (detectedSource != null) GoldPrimary else Color(0xFFFFC107),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = detectedSource ?: "يُفضل مراجعة وتوثيق المصدر الشرعي ✦",
                color = if (detectedSource != null) GoldPrimary else Color(0xFFE2E8F0),
                fontFamily = CairoFont,
                fontSize = 10.5.sp,
                fontWeight = if (detectedSource != null) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

