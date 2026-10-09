package com.qabas.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * ميزات التفاعل المتقدمة: وضع الخشوع، تكرار الترديد والحفظ، وتوليد بطاقات الستوري الإيمانية
 */
object ReelKhushuModeHelper {

    /**
     * توليد ومشاركة بطاقة ستوري إيمانية فاخرة لمشاركتها عبر واتساب وإنستغرام
     */
    suspend fun generateAndShareStoryCard(
        context: Context,
        title: String,
        description: String?,
        reciterName: String?
    ) = withContext(Dispatchers.IO) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Dark Emerald-Slate Gradient Background
            val bgPaint = Paint().apply {
                isAntiAlias = true
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        android.graphics.Color.rgb(15, 23, 42),
                        android.graphics.Color.rgb(6, 78, 59),
                        android.graphics.Color.rgb(2, 44, 34),
                        android.graphics.Color.rgb(15, 23, 42)
                    ),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Gold Islamic Frame
            val goldPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = android.graphics.Color.rgb(212, 175, 55)
            }
            canvas.drawRoundRect(RectF(60f, 60f, width - 60f, height - 60f), 40f, 40f, goldPaint)

            // 3. Header Text (Qabas Studio)
            val headerPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(212, 175, 55)
                textSize = 48f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("✨ قبس من النور والهدى ✨", width / 2f, 220f, headerPaint)

            // 4. Main Ayah / Quote Text
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = 58f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

            val fullText = description?.takeIf { it.isNotBlank() } ?: title
            val words = fullText.split(" ")
            var lineY = 650f
            var curLine = ""
            for (w in words) {
                val test = if (curLine.isEmpty()) w else "$curLine $w"
                if (textPaint.measureText(test) < 900) {
                    curLine = test
                } else {
                    canvas.drawText(curLine, width / 2f, lineY, textPaint)
                    lineY += 90f
                    curLine = w
                }
            }
            if (curLine.isNotEmpty()) {
                canvas.drawText(curLine, width / 2f, lineY, textPaint)
            }

            // 5. Reciter / Author badge
            val authorPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(203, 213, 225)
                textSize = 42f
                textAlign = Paint.Align.CENTER
            }
            val authorStr = "🎙️ ${reciterName ?: "تطبيق قبس القرآني"}"
            canvas.drawText(authorStr, width / 2f, lineY + 180f, authorPaint)

            // 6. Footer Call-to-action
            val footerPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(212, 175, 55)
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("﴿وَذَكِّرْ فَإِنَّ الذِّكْرَىٰ تَنفَعُ الْمُؤْمِنِينَ﴾", width / 2f, height - 160f, footerPaint)

            // Save to Cache
            val cacheDir = File(context.cacheDir, "story_cards")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cardFile = File(cacheDir, "qabas_story_${System.currentTimeMillis()}.png")
            FileOutputStream(cardFile).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, it)
            }

            // Share Intent
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cardFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "🌿 تدبر وآية مباركة من تطبيق قبس:\n«$title»")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "مشاركة بطاقة الستوري الإيمانية")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تعذر مشاركة البطاقة: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * زر وضع الخشوع وأدوات الانغماس في التلاوة
 */
@Composable
fun KhushuToolbar(
    isKhushuMode: Boolean,
    onToggleKhushu: () -> Unit,
    onOpenSoundscape: () -> Unit,
    onGenerateStory: () -> Unit,
    onToggleTadabbur: () -> Unit,
    repeatCount: Int,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Khushu' Immersion Toggle
            Surface(
                shape = CircleShape,
                color = if (isKhushuMode) GoldPrimary else Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp).clickable { onToggleKhushu() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isKhushuMode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "وضع الخشوع",
                        tint = if (isKhushuMode) DeepSlate else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Soundscape Natural Mixer
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp).clickable { onOpenSoundscape() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🌿", fontSize = 14.sp)
                }
            }

            // 3. Repeat & Memorization Loop (1x, 3x, 5x, ∞)
            Surface(
                shape = CircleShape,
                color = if (repeatCount > 1) GoldPrimary else Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp).clickable { onCycleRepeat() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when (repeatCount) {
                            1 -> "1x"
                            3 -> "3x"
                            5 -> "5x"
                            else -> "∞"
                        },
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (repeatCount > 1) DeepSlate else Color.White
                    )
                }
            }

            // 4. Tadabbur Quiz Toggle
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp).clickable { onToggleTadabbur() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Psychology, contentDescription = "وقفة تدبر", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // 5. Story Card Generator
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp).clickable { onGenerateStory() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "بطاقة ستوري", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
