package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelAudioDetailsSheet(
    reel: SupabaseServices.ReelRow,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تفاصيل الصوت والتلاوة 🎧",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = GoldPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio track card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GraphicEq, null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reel.title,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "بصوت: ${reel.userName ?: "صانع محتوى قبس"}",
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "استوديو قبس الإيماني المعتمد ✦",
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            color = GoldPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actions list
            Text(
                text = "الإجراءات المتاحة:",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action 1: Copy Ayah / Title
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Qabas Ayah", "${reel.title}\n${reel.description.orEmpty()}")
                        clipboard.setPrimaryClip(clip)
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم نسخ نص الآية / العنوان بنجاح 📋", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "نسخ نص الآية الكريمة", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Text(text = "نسخ كلمات المقطع لمشاركتها كرسالة أو تذكير", fontFamily = CairoFont, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action 2: Save Audio reminder
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تمت إضافة التلاوة إلى قائمة الاستماع الإيماني 🎧✨", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QueueMusic, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "إضافة لقائمة الاستماع الإيمانية", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Text(text = "الاستماع المتتابع لتلاوات خاشعة", fontFamily = CairoFont, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
