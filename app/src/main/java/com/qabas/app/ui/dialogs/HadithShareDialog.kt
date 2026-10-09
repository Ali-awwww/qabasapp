package com.qabas.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*

/**
 * Premium Share Dialog for Hadith Content & Cards
 * Offers multiple beautifully styled formats:
 * 1. Visual Card Layout (With ornaments, calligraphy brackets & attribution)
 * 2. WhatsApp / Telegram Direct share
 * 3. Formatted Markdown for Social Media (X/Twitter, Instagram caption, Facebook)
 * 4. Image Share with embedded text
 */
@Composable
fun HadithShareDialog(
    narrator: String,
    hadithText: String,
    hadithStatus: String,
    hadithSource: String,
    userHandle: String,
    imagePath: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: تنسيق بصري فاخر, 1: نص سريع مختصر

    // Elegant text formatting with Arabic Islamic decorative marks
    val visualFormattedText = remember(narrator, hadithText, hadithStatus, hadithSource) {
        buildString {
            append("﷽\n\n")
            append("✦ قال رسول الله ﷺ ✦\n")
            if (narrator.isNotBlank()) {
                append("« $narrator »\n\n")
            }
            append("❝ $hadithText ❞\n\n")
            append("────────────────────\n")
            if (hadithStatus.isNotBlank()) {
                append("📜 درجة الحديث: $hadithStatus\n")
            }
            if (hadithSource.isNotBlank()) {
                append("📚 المصدر: $hadithSource\n")
            }
            append("────────────────────\n")
            append("✨ تم التوثيق والتصميم عبر تطبيق قبس")
            if (userHandle.isNotBlank()) {
                append(" ($userHandle)")
            }
            append("\n#حديث_شريف #السنة_النبوية #قبس #حديث")
        }
    }

    val compactFormattedText = remember(hadithText, hadithSource) {
        buildString {
            append("قال ﷺ: « $hadithText »\n")
            if (hadithSource.isNotBlank()) {
                append("[$hadithSource]\n")
            }
            append("عبر تطبيق قبس ✨")
        }
    }

    val currentShareText = if (selectedTab == 0) visualFormattedText else compactFormattedText

    fun shareTextViaIntent(textToShare: String, specificPackage: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "حديث نبوي شريف")
                putExtra(Intent.EXTRA_TEXT, textToShare)
                if (specificPackage != null) {
                    setPackage(specificPackage)
                }
            }
            if (specificPackage != null) {
                // Try direct app launch, fallback to chooser if app not installed
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                    // Fall through to general chooser
                }
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة الحديث الشريف عبر"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح قائمة المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareImageAndText() {
        if (imagePath != null) {
            try {
                val uri = Uri.parse(imagePath)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, currentShareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة بطاقة الحديث مع النص"))
            } catch (e: Exception) {
                shareTextViaIntent(currentShareText)
            }
        } else {
            shareTextViaIntent(currentShareText)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111726)),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(listOf(GoldPrimary.copy(alpha = 0.6f), Color(0xFF1E293B)))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with icon and title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "مشاركة الحديث الشريف",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "تنسيق بصري منسق للنشر والإرسال",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                // Format Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("✨ تنسيق فاخر وموثق" to 0, "⚡ نص مقتضب" to 1).forEach { (label, idx) ->
                        val isSelected = selectedTab == idx
                        Surface(
                            onClick = { selectedTab = idx },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) GoldPrimary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DeepSlate else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Visual Preview Card Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131F)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("معاينة التنسيق الجاهز للنشر:", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GoldPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    if (selectedTab == 0) "مزخرف ✦" else "سريع ⚡",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF070A11),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentShareText,
                                color = Color(0xFFE2E8F0),
                                fontFamily = AmiriFont,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Quick Action Share Buttons Row (WhatsApp, Telegram, General Share)
                Text(
                    "مشاركة مباشرة عبر التطبيقات:",
                    color = TextPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WhatsApp
                    OutlinedButton(
                        onClick = { shareTextViaIntent(currentShareText, "com.whatsapp") },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x1525D366)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("واتساب 💬", color = Color(0xFF25D366), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Telegram
                    OutlinedButton(
                        onClick = { shareTextViaIntent(currentShareText, "org.telegram.messenger") },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF229ED9)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x15229ED9)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("تيليجرام ✈️", color = Color(0xFF229ED9), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Copy Text
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("hadith_formatted", currentShareText))
                            Toast.makeText(context, "تم نسخ التنسيق البصري للحديث بنجاح! 📋✨", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Primary System Share Button
                Button(
                    onClick = { shareImageAndText() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (imagePath != null) "مشاركة البطاقة المرئية والنص الآن 🚀" else "مشاركة النص المنسق لكافة التطبيقات 🚀",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
