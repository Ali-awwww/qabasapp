package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelAudioDetailsSheet(
    reel: SupabaseServices.ReelRow,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تفاصيل الصوت والتلاوة",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = theme.textPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppleCard(cornerRadius = 16.dp) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reel.title,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "بصوت: ${reel.userName ?: "صانع محتوى خادم المسلم"}",
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "محتوى إيماني في منصة خادم المسلم",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = theme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AppleGroupedSection(header = "الإجراءات المتاحة") {
                AppleListRow(
                    title = "نسخ نص الآية أو العنوان",
                    subtitle = "نسخ كلمات المقطع لمشاركتها كرسالة أو تذكير",
                    icon = Icons.Default.MenuBook,
                    iconBackground = theme.primary,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(
                            "Muslim AI Assistant Ayah",
                            listOfNotNull(reel.title, reel.description?.takeIf { it.isNotBlank() }).joinToString("\n")
                        )
                        clipboard.setPrimaryClip(clip)
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم نسخ النص بنجاح", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
                AppleInsetDivider()
                AppleListRow(
                    title = "إضافة لقائمة الاستماع الإيمانية",
                    subtitle = "إضافة المقطع إلى قائمة التشغيل المستمر في المشغّل الصوتي",
                    icon = Icons.Default.QueueMusic,
                    iconBackground = theme.success,
                    onClick = {
                        val track = PlayableAudioTrack(
                            id = "reel_${reel.id}",
                            title = reel.title,
                            artist = reel.userName ?: "صانع محتوى خادم المسلم",
                            audioUrl = reel.videoUrl,
                            category = "تلاوات ومقاطع الريلز",
                            badge = "ريلز"
                        )
                        AudioPlaybackManager.addToPlaylist(track)
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تمت الإضافة إلى قائمة الاستماع الإيمانية", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        }
    }
}

