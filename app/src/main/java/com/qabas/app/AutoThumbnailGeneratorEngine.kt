package com.qabas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

enum class ThumbnailAspectRatio(val label: String, val ratio: Float, val iconName: String) {
    SHORTS_9_16("Reels / Shorts (9:16)", 9f / 16f, "📱"),
    YOUTUBE_16_9("YouTube (16:9)", 16f / 9f, "📺"),
    INSTAGRAM_4_5("Instagram Post (4:5)", 4f / 5f, "📸"),
    SQUARE_1_1("Square (1:1)", 1f / 1f, "⏹️")
}

enum class IslamicFrameStyle(val title: String, val primaryColor: Color, val secondaryColor: Color) {
    GOLDEN_ARCH("محراب ذهبي ملكي", Color(0xFFE8C547), Color(0xFF151B2B)),
    EMERALD_QURAN("أخضر زمردي قرآني", Color(0xFF10B981), Color(0xFF064E3B)),
    TEAL_DOCUMENTARY("وثائقي سينمائي Teal", Color(0xFF0EA5E9), Color(0xFF0B0F19)),
    PARCHMENT_HISTORIC("ورقي أندلسي عريق", Color(0xFFD97706), Color(0xFF78350F))
}

data class ThumbnailDesignPreset(
    val id: String,
    val name: String,
    val bgImageUrl: String,
    val frameStyle: IslamicFrameStyle,
    val badgeText: String,
    val sampleHeadline: String,
    val sampleSubline: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoThumbnailGeneratorDialog(
    context: Context,
    initialTitle: String = "سر عظيم في تدبر القرآن الكريم",
    onDismiss: () -> Unit = {}
) {
    var headlineText by remember { mutableStateOf(initialTitle) }
    var sublineText by remember { mutableStateOf("شاهد القصة كاملة ✦ مقطع يؤثر في القلوب") }
    var topicBadge by remember { mutableStateOf("✦ تدبر آية ✦") }
    var selectedRatio by remember { mutableStateOf(ThumbnailAspectRatio.SHORTS_9_16) }
    var selectedFrameStyle by remember { mutableStateOf(IslamicFrameStyle.GOLDEN_ARCH) }
    var showDevWatermark by remember { mutableStateOf(true) }
    var isGeneratingTitles by remember { mutableStateOf(false) }

    val presetBackgrounds = remember {
        listOf(
            ThumbnailDesignPreset(
                id = "p1",
                name = "المسجد الذهبي",
                bgImageUrl = "https://images.unsplash.com/photo-1564769625905-50e93615e769?w=800&q=80",
                frameStyle = IslamicFrameStyle.GOLDEN_ARCH,
                badgeText = "✦ قصص الأنبياء ✦",
                sampleHeadline = "سر لم تسمعه من قبل عن صبر النبي أيوب!",
                sampleSubline = "كيف تتجاوز الابتلاء بكلمة واحدة فقط؟"
            ),
            ThumbnailDesignPreset(
                id = "p2",
                name = "المصحف الشريف",
                bgImageUrl = "https://images.unsplash.com/photo-1609599006353-e629aaabfeae?w=800&q=80",
                frameStyle = IslamicFrameStyle.EMERALD_QURAN,
                badgeText = "✦ تدبر آية ✦",
                sampleHeadline = "آية تغيير حياتك إذا تدبرتها اليوم!",
                sampleSubline = "تلاوة خاشعة تريح القلوب"
            ),
            ThumbnailDesignPreset(
                id = "p3",
                name = "السماء والكون",
                bgImageUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=800&q=80",
                frameStyle = IslamicFrameStyle.TEAL_DOCUMENTARY,
                badgeText = "✦ ردود وشبهات ✦",
                sampleHeadline = "رد قاطع بالدليل العلي على الشبهة!",
                sampleSubline = "الرد العلماني الموثق بالأدلة"
            ),
            ThumbnailDesignPreset(
                id = "p4",
                name = "التراث الأندلسي",
                bgImageUrl = "https://images.unsplash.com/photo-1548013146-72479768bada?w=800&q=80",
                frameStyle = IslamicFrameStyle.PARCHMENT_HISTORIC,
                badgeText = "✦ تاريخ وحضارة ✦",
                sampleHeadline = "روائع العمارة الإسلامية في قرطبة",
                sampleSubline = "قصة فتح الأندلس بأجواء سينمائية"
            )
        )
    }

    var selectedPreset by remember { mutableStateOf(presetBackgrounds[0]) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, PresetPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = PresetPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "مولد أغلفة الفيديوهات والمصغرات 🎨",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Canvas Live Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(selectedRatio.ratio)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, selectedFrameStyle.primaryColor, RoundedCornerShape(16.dp))
                ) {
                    // Background Image
                    AsyncImage(
                        model = selectedPreset.bgImageUrl,
                        contentDescription = "Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Black.copy(alpha = 0.6f),
                                        selectedFrameStyle.secondaryColor.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )

                    // Islamic Arch Inner Border Accent
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .border(1.dp, selectedFrameStyle.primaryColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    )

                    // Canvas Content Layer
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Badge
                        Surface(
                            color = selectedFrameStyle.primaryColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                topicBadge,
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Center Headline with Dark Pill Background
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, selectedFrameStyle.primaryColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    headlineText,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )

                                if (sublineText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        sublineText,
                                        color = selectedFrameStyle.primaryColor,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Bottom Watermark & Branding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showDevWatermark) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "✦ QABAS DEV MASTER ✦",
                                        color = PresetPrimary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Surface(
                                color = selectedFrameStyle.primaryColor.copy(alpha = 0.2f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, selectedFrameStyle.primaryColor)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = selectedFrameStyle.primaryColor,
                                    modifier = Modifier.padding(4.dp).size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Aspect Ratio Selector
                Text("أبعاد ومقاس الغلاف:", color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ThumbnailAspectRatio.values()) { ratio ->
                        val isSelected = selectedRatio == ratio
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRatio = ratio },
                            label = { Text("${ratio.iconName} ${ratio.label}", fontSize = 12.sp, fontFamily = NotoSansFont) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PresetPrimary,
                                selectedLabelColor = DeepSlate,
                                containerColor = ThemeSecondarySurface,
                                labelColor = TextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Presets Gallery
                Text("اختر خلفية ونمط القالب:", color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetBackgrounds) { preset ->
                        val isSelected = selectedPreset.id == preset.id
                        Box(
                            modifier = Modifier
                                .size(70.dp, 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) PresetPrimary else TextTertiary,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedPreset = preset
                                    selectedFrameStyle = preset.frameStyle
                                    topicBadge = preset.badgeText
                                }
                        ) {
                            AsyncImage(
                                model = preset.bgImageUrl,
                                contentDescription = preset.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(preset.name, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Edit Inputs
                OutlinedTextField(
                    value = headlineText,
                    onValueChange = { headlineText = it },
                    label = { Text("العنوان الرئيسي الجاذب (CTR)", color = TextTertiary, fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PresetPrimary, unfocusedBorderColor = TextTertiary, focusedTextColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = sublineText,
                    onValueChange = { sublineText = it },
                    label = { Text("العبارة الفرعية / الخطاف", color = TextTertiary, fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PresetPrimary, unfocusedBorderColor = TextTertiary, focusedTextColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // AI Auto CTR Titles Button
                Button(
                    onClick = {
                        isGeneratingTitles = true
                        scope.launch {
                            // ق155: كان الزر «يولّد» بلا ذكاء اصطناعي —
                            // تأخير 1200ms ثم نسخ نص القالب الثابت مع
                            // تنبيه «تم الخلق تلقائياً». الآن استدعاء
                            // Groq حقيقي، وعند فشله يُستخدم نص القالب
                            // مع تنبيه صادق بذلك.
                            val generated = try {
                                RealGroqService.chatOrGenerate(
                                    "من هذا العنوان: \"$headlineText\" (موضوع: ${selectedPreset.name})، اقترح عنوان فيديو جذاباً قصيراً (سطر واحد) وجملة خطاف فرعية قصيرة (سطر واحد). أخرج سطرين فقط: السطر الأول العنوان، والسطر الثاني الجملة."
                                )
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                            val lines = generated?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                            if (lines.size >= 2) {
                                headlineText = lines[0]
                                sublineText = lines[1]
                                Toast.makeText(context, "تم توليد عنوان وجملة خطاف بالذكاء الاصطناعي! ⚡", Toast.LENGTH_SHORT).show()
                            } else {
                                headlineText = selectedPreset.sampleHeadline
                                sublineText = selectedPreset.sampleSubline
                                Toast.makeText(context, "تعذّر التوليد الذكي — استُخدم عنوان القالب.", Toast.LENGTH_SHORT).show()
                            }
                            isGeneratingTitles = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeSecondarySurface),
                    border = BorderStroke(1.dp, PresetPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGeneratingTitles) {
                        CircularProgressIndicator(color = PresetPrimary, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد عنوان ذكي عالِي النقرات (AI CTR Title)", color = PresetPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                var isExporting by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isExporting = true
                            scope.launch {
                                // ق155: كان فشل الحفظ (null) يُبشَّر به
                                // «تم إعداد الغلاف وجاهزيته!» وتُغلق
                                // الشاشة؛ وكان التنبيه يدّعي «4K» بينما
                                // الأقصى 1920×1080. الآن: فشل صادق بلا
                                // إغلاق، ونجاح بلا ادعاء دقة، وتنبيه إن
                                // حُفظ الغلاف بلا صورة خلفيته.
                                val (path, backgroundLoaded) = saveThumbnailHighResToGallery(
                                    context = context,
                                    headlineText = headlineText,
                                    sublineText = sublineText,
                                    topicBadge = topicBadge,
                                    aspectRatio = selectedRatio,
                                    frameStyle = selectedFrameStyle,
                                    showDevWatermark = showDevWatermark,
                                    bgImageUrl = selectedPreset.bgImageUrl
                                )
                                isExporting = false
                                if (path != null) {
                                    Toast.makeText(
                                        context,
                                        if (backgroundLoaded) "تم حفظ الغلاف المصغر بدقة عالية في معرض الصور! 🖼️✦"
                                        else "تم حفظ الغلاف، لكن تعذّر تحميل صورة الخلفية — حُفظ بتدرج داكن فقط.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "تعذّر حفظ الغلاف — تحقق من مساحة التخزين والأذونات وحاول مجدداً.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = DeepSlate)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ في المعرض ✦", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                shareThumbnailImage(
                                    context = context,
                                    headlineText = headlineText,
                                    sublineText = sublineText,
                                    topicBadge = topicBadge,
                                    aspectRatio = selectedRatio,
                                    frameStyle = selectedFrameStyle,
                                    showDevWatermark = showDevWatermark,
                                    bgImageUrl = selectedPreset.bgImageUrl
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, PresetPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = PresetPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة", color = PresetPrimary, fontFamily = CairoFont)
                    }
                }
            }
        }
    }
}

// Real High-Resolution Canvas Renderer for Thumbnails
/** نتيجة الرندر: الصورة + هل حُمّلت خلفية القالب فعلاً. */
private data class ThumbnailRenderResult(val bitmap: Bitmap, val backgroundLoaded: Boolean)

/** تحميل صورة خلفية القالب (OkHttp على IO كسائر الشبكة في المشروع). */
private suspend fun loadThumbnailBackground(url: String?): Bitmap? = withContext(Dispatchers.IO) {
    if (url.isNullOrBlank()) return@withContext null
    try {
        val request = Request.Builder().url(url).header("User-Agent", "QabasStudio/1.0").build()
        SharedHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bytes = response.body?.bytes() ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun renderThumbnailBitmap(
    context: Context,
    headlineText: String,
    sublineText: String,
    topicBadge: String,
    aspectRatio: ThumbnailAspectRatio,
    frameStyle: IslamicFrameStyle,
    showDevWatermark: Boolean,
    bgImageUrl: String? = null
): ThumbnailRenderResult {
    val (width, height) = when (aspectRatio) {
        ThumbnailAspectRatio.YOUTUBE_16_9 -> 1920 to 1080
        ThumbnailAspectRatio.SHORTS_9_16 -> 1080 to 1920
        ThumbnailAspectRatio.INSTAGRAM_4_5 -> 1080 to 1350
        ThumbnailAspectRatio.SQUARE_1_1 -> 1080 to 1080
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. خلفية القالب كما في المعاينة — كانت المعاينة تعرض صورة
    // القالب (AsyncImage بقص مركزي) بينما الملف المحفوظ تدرج
    // داكن فقط؛ فالغلاف المصدَّر لم يكن ما وافق عليه المستخدم.
    // عند تعذّر التحميل يبقى التدرج بديلاً معلناً في النتيجة.
    val bgBitmap = loadThumbnailBackground(bgImageUrl)
    if (bgBitmap != null) {
        val scale = maxOf(width / bgBitmap.width.toFloat(), height / bgBitmap.height.toFloat())
        val srcW = (width / scale).toInt().coerceAtMost(bgBitmap.width)
        val srcH = (height / scale).toInt().coerceAtMost(bgBitmap.height)
        val srcLeft = ((bgBitmap.width - srcW) / 2).coerceAtLeast(0)
        val srcTop = ((bgBitmap.height - srcH) / 2).coerceAtLeast(0)
        canvas.drawBitmap(
            bgBitmap,
            Rect(srcLeft, srcTop, srcLeft + srcW, srcTop + srcH),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            null
        )
        bgBitmap.recycle()
        // تدرج داكن كالمعاينة لتبقى النصوص مقروءة فوق الصورة.
        val overlayPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    android.graphics.Color.argb(102, 0, 0, 0),
                    android.graphics.Color.argb(153, 0, 0, 0),
                    frameStyle.secondaryColor.copy(alpha = 0.92f).toArgb()
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
    } else {
        // Background Fill & Radial Gradient (بلا صورة خلفية).
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                width / 2f, height / 2f, width.toFloat(),
                intArrayOf(frameStyle.secondaryColor.toArgb(), android.graphics.Color.parseColor("#0F172A"), android.graphics.Color.BLACK),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    }
    val backgroundLoaded = bgBitmap != null

    // 2. Islamic Frame Border
    val framePaint = Paint().apply {
        isAntiAlias = true
        color = frameStyle.primaryColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    val inset = 30f
    canvas.drawRoundRect(RectF(inset, inset, width - inset, height - inset), 32f, 32f, framePaint)

    // Inner subtle border
    framePaint.strokeWidth = 4f
    canvas.drawRoundRect(RectF(inset + 16f, inset + 16f, width - inset - 16f, height - inset - 16f), 20f, 20f, framePaint)

    // 3. Top Topic Badge
    val badgePaint = Paint().apply {
        isAntiAlias = true
        color = frameStyle.primaryColor.toArgb()
        style = Paint.Style.FILL
    }
    val badgeRect = RectF((width / 2f) - 220f, 80f, (width / 2f) + 220f, 160f)
    canvas.drawRoundRect(badgeRect, 18f, 18f, badgePaint)

    // ق155: نص الشارة كان سطراً واحداً بحجم ثابت داخل شريط
    // 440px — فشارة طويلة تفيض عن الشريط وتُقصّ. الآن يُصغَّر
    // الخط حتى يتسع.
    val badgeTextPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo_regular) }.getOrNull()
    }
    var badgeTextSize = 34f
    while (badgeTextSize > 18f && badgeTextPaint.measureText(topicBadge) > 400f) {
        badgeTextSize -= 2f
        badgeTextPaint.textSize = badgeTextSize
    }
    canvas.drawText(topicBadge, width / 2f, 132f, badgeTextPaint)

    // 4. Center Pill Card Background
    val centerY = height / 2f
    val pillPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#E6000000")
        style = Paint.Style.FILL
    }
    val pillBorder = Paint().apply {
        isAntiAlias = true
        color = frameStyle.primaryColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    val pillRect = RectF(100f, centerY - 180f, width - 100f, centerY + 180f)
    canvas.drawRoundRect(pillRect, 28f, 28f, pillPaint)
    canvas.drawRoundRect(pillRect, 28f, 28f, pillBorder)

    // 5. Headline Text — ق155: كان drawText سطراً واحداً ممركزاً،
    // فعنوان طويل يفيض عن جانبي الصورة ويُقصّ بصمت (والمعاينة
    // تلفّه داخل البطاقة). الآن التفاف متعدد الأسطر بخط القاهرة
    // نفسه، بحد 3 أسطر مع حذف، ممركز عمودياً داخل البطاقة.
    val cairoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo_regular) }.getOrNull()
    val pillWidth = (width - 200).coerceAtLeast(200)
    val headlinePaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = if (width > 1200) 56f else 42f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        typeface = cairoTypeface
    }
    val headlineLayout = StaticLayout.Builder.obtain(headlineText, 0, headlineText.length, headlinePaint, pillWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(3)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    // 6. Subline Text
    val sublineLayout = if (sublineText.isNotBlank()) {
        val sublinePaint = TextPaint().apply {
            isAntiAlias = true
            color = frameStyle.primaryColor.toArgb()
            textSize = if (width > 1200) 36f else 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            typeface = cairoTypeface
        }
        StaticLayout.Builder.obtain(sublineText, 0, sublineText.length, sublinePaint, pillWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    } else {
        null
    }
    val textBlockHeight = headlineLayout.height + (sublineLayout?.let { 12 + it.height } ?: 0)
    var textTop = centerY - textBlockHeight / 2f
    canvas.save()
    canvas.translate(100f, textTop)
    headlineLayout.draw(canvas)
    canvas.restore()
    if (sublineLayout != null) {
        textTop += headlineLayout.height + 12f
        canvas.save()
        canvas.translate(100f, textTop)
        sublineLayout.draw(canvas)
        canvas.restore()
    }

    // 7. Watermark Footer
    if (showDevWatermark) {
        val markPaint = Paint().apply {
            isAntiAlias = true
            color = frameStyle.primaryColor.toArgb()
            textSize = 28f
            textAlign = Paint.Align.CENTER
            alpha = 180
        }
        canvas.drawText("✦ QABAS STUDIO MASTER ✦", width / 2f, height - 70f, markPaint)
    }

    return ThumbnailRenderResult(bitmap, backgroundLoaded)
}

private suspend fun saveThumbnailHighResToGallery(
    context: Context,
    headlineText: String,
    sublineText: String,
    topicBadge: String,
    aspectRatio: ThumbnailAspectRatio,
    frameStyle: IslamicFrameStyle,
    showDevWatermark: Boolean,
    bgImageUrl: String? = null
): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
    var bitmap: Bitmap? = null
    var result: Pair<String?, Boolean> = Pair(null, false)
    try {
        val rendered = renderThumbnailBitmap(context, headlineText, sublineText, topicBadge, aspectRatio, frameStyle, showDevWatermark, bgImageUrl)
        bitmap = rendered.bitmap
        result = Pair(null, rendered.backgroundLoaded)
        val fileName = "QABAS_THUMBNAIL_${System.currentTimeMillis()}.png"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Qabas_Thumbnails")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                val stream = context.contentResolver.openOutputStream(it)
                stream?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                result = Pair(uri.toString(), rendered.backgroundLoaded)
            }
        } else {
            val imagesDir = context.getExternalFilesDir(null)
            val imageFile = File(imagesDir, fileName)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            result = Pair(imageFile.absolutePath, rendered.backgroundLoaded)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        result = Pair(null, false)
    } finally {
        bitmap?.recycle()
    }
    result
}

private suspend fun shareThumbnailImage(
    context: Context,
    headlineText: String,
    sublineText: String,
    topicBadge: String,
    aspectRatio: ThumbnailAspectRatio,
    frameStyle: IslamicFrameStyle,
    showDevWatermark: Boolean,
    bgImageUrl: String? = null
) = withContext(Dispatchers.IO) {
    var bitmap: Bitmap? = null
    try {
        val rendered = renderThumbnailBitmap(context, headlineText, sublineText, topicBadge, aspectRatio, frameStyle, showDevWatermark, bgImageUrl)
        bitmap = rendered.bitmap
        if (!rendered.backgroundLoaded) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تعذّر تحميل صورة الخلفية — تُشارَك نسخة بتدرج داكن.", Toast.LENGTH_LONG).show()
            }
        }
        val cacheDir = context.cacheDir
        val file = File(cacheDir, "qabas_shared_thumbnail.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, "$headlineText - $sublineText\n\nغلاف فيديو مصمم عبر استوديو خادم المسلم")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الغلاف المصغر"))
    } catch (e: Exception) {
        // ق155: جسم الدالة على IO الآن — والتنبيه يحتاج Main.
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "تعذر إعداد مشاركة الغلاف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    } finally {
        bitmap?.recycle()
    }
}

