package com.qabas.app.ui.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.Translator
import com.qabas.app.ui.theme.*

@Composable
fun SourceTypeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    badgeColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            color = badgeColor,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * شاشة اختيار نوع المدخل (فكرة / صوت / فيديو / صور).
 * مستخرجة من InputScreen لتقليل حجمه.
 */
@Composable
fun SourceTypeSelector(
    onSelectIdea: () -> Unit,
    onSelectAudio: () -> Unit,
    onSelectVideo: () -> Unit,
    onSelectImages: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = GoldPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    Translator.tr("اختر طريقة بدء صناعة الفيديو"),
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    Translator.tr("حدد مصدر المدخل الأساسي للبدء في توليد السيناريو والإنتاج"),
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        SourceTypeCard(
            title = Translator.tr("ابدأ من فكرة نصية"),
            subtitle = Translator.tr("كتابة سكريبت، قصة، نص دعوي أو توليد أفكار بالذكاء الاصطناعي"),
            icon = Icons.Default.Lightbulb,
            badge = Translator.tr("متاح الآن ✨"),
            badgeColor = GoldPrimary,
            accentColor = GoldPrimary,
            onClick = onSelectIdea
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من ملف صوتي"),
            subtitle = Translator.tr("تفريغ خطبة، مقطع صوتي، بودكاست أو تلاوة قرآنية وتحويلها لفيديو"),
            icon = Icons.Default.GraphicEq,
            badge = Translator.tr("متاح الآن 🎙️"),
            badgeColor = Color(0xFF38BDF8),
            accentColor = Color(0xFF38BDF8),
            onClick = onSelectAudio
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من فيديو جاهز"),
            subtitle = Translator.tr("استيراد مقطع فيديو موجود وإعادة مونتاجه أو إضافة تعليقات وB-Roll"),
            icon = Icons.Default.Videocam,
            badge = Translator.tr("متاح الآن 🎬"),
            badgeColor = Color(0xFFA78BFA),
            accentColor = Color(0xFFA78BFA),
            onClick = onSelectVideo
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من صور متعددة"),
            subtitle = Translator.tr("تحويل ألبوم صور إلى ريلز متحرك مع انتقالات وتعليق صوتي"),
            icon = Icons.Default.PhotoLibrary,
            badge = Translator.tr("متاح الآن 🖼️"),
            badgeColor = Color(0xFF34D399),
            accentColor = Color(0xFF34D399),
            onClick = onSelectImages
        )
    }
}
