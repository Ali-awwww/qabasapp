package com.qabas.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DefaultSourceSelectionView(
    onSelectType: (String) -> Unit,
    onOpenDirector: () -> Unit,
    onOpenQasas: () -> Unit,
    onOpenClips: () -> Unit,
    onPickTrending: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(12.dp),
            backgroundColor = Color(0xFF151B2B).copy(alpha = 0.85f),
            borderColor = GoldPrimary.copy(alpha = 0.3f)
        ) {
            HudCornerDecor(modifier = Modifier.matchParentSize(), color = GoldPrimary.copy(alpha = 0.1f))
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
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
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
            title = Translator.tr("المخرج — احكِ وهو يجهز"),
            subtitle = Translator.tr("محادثة حرة: افهم فكرتك ويسألك عن الناقص ويبني موجز المشروع"),
            icon = Icons.Default.AutoAwesome,
            badge = Translator.tr("جديد ✨"),
            badgeColor = Color(0xFF8B5CF6),
            accentColor = Color(0xFF8B5CF6),
            onClick = { onOpenDirector() }
        )

        SourceTypeCard(
            title = Translator.tr("قاص قبس — قصص تتحول لريلز"),
            subtitle = Translator.tr("5 قصص قرآنية مروية + اسأل القاص وحول أي قصة لفيديو"),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            badge = Translator.tr("جديد 📖"),
            badgeColor = GoldPrimary,
            accentColor = GoldPrimary,
            onClick = { onOpenQasas() }
        )

        SourceTypeCard(
            title = Translator.tr("مقص المقاطع — من الطويل للقصير"),
            subtitle = Translator.tr("الصق خطبة أو تفريغاً طويلاً فيستخرج 5 لحظات ذهبية كريلزات"),
            icon = Icons.Default.ContentCut,
            badge = Translator.tr("جديد ✂️"),
            badgeColor = Color(0xFF34D399),
            accentColor = Color(0xFF34D399),
            onClick = { onOpenClips() }
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من فكرة نصية"),
            subtitle = Translator.tr("كتابة سكريبت، قصة، نص دعوي أو توليد أفكار بالذكاء الاصطناعي"),
            icon = Icons.Default.Lightbulb,
            badge = Translator.tr("متاح الآن ✨"),
            badgeColor = GoldPrimary,
            accentColor = GoldPrimary,
            onClick = { onSelectType("idea") }
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من ملف صوتي"),
            subtitle = Translator.tr("تفريغ خطبة، مقطع صوتي، بودكاست أو تلاوة قرآنية وتحويلها لفيديو"),
            icon = Icons.Default.GraphicEq,
            badge = Translator.tr("متاح الآن 🎙️"),
            badgeColor = Color(0xFF38BDF8),
            accentColor = Color(0xFF38BDF8),
            onClick = { onSelectType("audio") }
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من فيديو موجود"),
            subtitle = Translator.tr("إعادة مونتاج، استخراج نصوص، أو استنساخ أسلوب مرئي"),
            icon = Icons.Default.VideoLibrary,
            badge = Translator.tr("متاح الآن 🎬"),
            badgeColor = Color(0xFF34D399),
            accentColor = Color(0xFF34D399),
            onClick = { onSelectType("video") }
        )

        SourceTypeCard(
            title = Translator.tr("ابدأ من صور"),
            subtitle = Translator.tr("تحويل ألبوم صور، تصاميم أو لوحات إلى ريلز سينمائي متحرك"),
            icon = Icons.Default.PhotoLibrary,
            badge = Translator.tr("متاح الآن 🖼️"),
            badgeColor = Color(0xFFFBBF24),
            accentColor = Color(0xFFFBBF24),
            onClick = { onSelectType("images") }
        )
    }
}
