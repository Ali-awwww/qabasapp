package com.qabas.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
 * بطاقة قبس الموحدة بنمط Apple Inset Grouped Card.
 */
@Composable
fun QabasCard(
    modifier: Modifier = Modifier,
    shapeRadius: Dp = 16.dp,
    containerColor: Color = QabasTheme.current.cardSurface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    AppleCard(
        modifier = modifier,
        cornerRadius = shapeRadius,
        containerColor = containerColor
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * ترويسة أقسام قبس بنمط Apple HIG.
 */
@Composable
fun QabasSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val theme = QabasTheme.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(theme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * بطاقة أحكام التجويد بتصميم Apple النظيف.
 */
@Composable
fun TajweedRuleCard(
    rule: TajweedRuleItem,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    AppleCard(modifier = modifier, cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = theme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    rule.category,
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                rule.title,
                color = theme.textPrimary,
                fontFamily = TajawalFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                rule.description,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "ستُعرض أمثلة هذا الحكم عند ربطها بمواضع موثّقة من المصحف.",
                color = theme.textSecondary.copy(alpha = 0.85f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * حوار شهادة إتقان الترتيل والتجويد — مع حفظ حقيقي للصورة في المعرض.
 */
@Composable
fun TajweedCertificateDialog(
    surahName: String,
    score: Int,
    onDismiss: () -> Unit,
    onExportReel: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        AppleCard(
            cornerRadius = 22.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CardMembership,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "شهادة إتقان الترتيل والتجويد",
                    color = theme.textPrimary,
                    fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "صادرة عن استوديو قبس للقرآن والتجويد",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = theme.secondarySurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "أتم القارئ فحص الترتيل والتجويد لسورة:",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "﴿ سورة $surahName ﴾",
                            color = theme.primary,
                            fontFamily = AmiriFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "درجة التلاوة: ",
                                color = theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 14.sp
                            )
                            Text(
                                "$score%",
                                color = theme.success,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (score >= 85) "التقدير: ممتاز برواية حفص عن عاصم" else "التقدير: جيد جداً مع الاستمرار في التدريب",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                ApplePrimaryButton(
                    text = "تصدير الشهادة كريلز تفاعلي",
                    icon = Icons.Default.Movie,
                    onClick = onExportReel
                )

                Spacer(modifier = Modifier.height(8.dp))

                AppleSecondaryButton(
                    text = if (isSaving) "جاري الحفظ..." else "حفظ الشهادة كصورة HD",
                    icon = Icons.Default.Download,
                    enabled = !isSaving,
                    onClick = {
                        scope.launch {
                            isSaving = true
                            val savedPath = withContext(Dispatchers.IO) {
                                saveTajweedCertificateBitmap(context, surahName, score)
                            }
                            isSaving = false
                            if (savedPath != null) {
                                Toast.makeText(context, "تم حفظ الشهادة في معرض الصور بنجاح", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "تعذر حفظ ملف الشهادة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun saveTajweedCertificateBitmap(context: Context, surahName: String, score: Int): String? {
    var bmp: Bitmap? = null
    return try {
        val w = 1080
        val h = 1350
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.parseColor("#0F172A"))

        val framePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRoundRect(RectF(48f, 48f, w - 48f, h - 48f), 36f, 36f, framePaint)

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#D4AF37")
            textSize = 54f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("شهادة إتقان الترتيل والتجويد", w / 2f, 260f, titlePaint)

        val surahPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 72f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("سورة $surahName", w / 2f, 560f, surahPaint)

        val scorePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#34D399")
            textSize = 86f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$score%", w / 2f, 760f, scorePaint)

        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("صادرة عن استوديو قبس للقرآن الكريم", w / 2f, 1140f, footerPaint)

        val fileName = "QABAS_CERT_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Qabas_Certificates")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                return it.toString()
            }
            null
        } else {
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file.absolutePath
        }
    } catch (_: Throwable) {
        null
    } finally {
        bmp?.recycle()
    }
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
