package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelMoreOptionsSheet(
    reel: SupabaseServices.ReelRow,
    isSaved: Boolean,
    currentUserEmail: String? = null,
    currentUserId: String? = null,
    onToggleSave: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onNotInterested: () -> Unit,
    onDeleteReel: (() -> Unit)? = null,
    onReportToDevChat: ((reason: String, details: String) -> Unit)? = null,
    onOpenDevChat: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showTelemetryDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    var selectedReportReason by remember { mutableStateOf("محتوى مخالف للضوابط الشرعية أو به منكرات ⚠️") }
    var reportAdditionalNotes by remember { mutableStateOf("") }

    val isDeveloper = remember(currentUserEmail) {
        ReelPublishManager.isDeveloperUser(context, currentUserEmail)
    }

    val isOwner = remember(reel, currentUserEmail, currentUserId) {
        ReelPublishManager.isReelOwner(context, currentUserEmail, currentUserId, reel)
    }

    val canDelete = isDeveloper || isOwner

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "خيارات إضافية للمقطع",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 1. Save / Unsave
            OptionRow(
                icon = if (isSaved) Icons.Default.BookmarkRemove else Icons.Default.Bookmark,
                title = if (isSaved) "إزالة من المحفوظات" else "حفظ المقطع في المفضلة",
                subtitle = "الوصول السريع للمقطع في أي وقت عبر السجل",
                tint = if (isSaved) GoldPrimary else Color.White,
                onClick = {
                    onToggleSave()
                    onDismiss()
                }
            )

            // Download Reel to Download Hub
            OptionRow(
                icon = Icons.Default.FileDownload,
                title = "تنزيل الريل إلى مركز التنزيلات",
                subtitle = "حفظ الفيديو في الهاتف لمشاهدته بجودة عالية بدون إنترنت",
                tint = Color(0xFF00E5FF),
                onClick = {
                    DownloadsHubManager.downloadReel(
                        context = context,
                        reelId = reel.id,
                        title = reel.title,
                        videoUrl = reel.videoUrl,
                        thumbnailUrl = reel.thumbnailUrl,
                        author = reel.userName
                    )
                    Toast.makeText(context, "بدأ تنزيل الريل في مركز التنزيلات 🚀", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // 2. Copy Link
            OptionRow(
                icon = Icons.Default.ContentCopy,
                title = "نسخ رابط الريل",
                subtitle = "مشاركة الرابط المباشر مع أصدقائك",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Qabas Reel Link", reel.videoUrl)
                    clipboard.setPrimaryClip(clip)
                    ReelPreferencesManager.triggerHaptic(context)
                    Toast.makeText(context, "تم نسخ رابط المقطع إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // 3. Playback Speed
            OptionRow(
                icon = Icons.Default.Speed,
                title = "سرعة تشغيل الفيديو",
                subtitle = "تعديل سرعة التلاوة أو الشرح",
                onClick = {
                    showSpeedDialog = true
                }
            )

            // 4. Share to Apps
            OptionRow(
                icon = Icons.Default.Share,
                title = "مشاركة عبر التطبيقات",
                subtitle = "إرسال المقطع عبر واتساب، تيليجرام وغيرها",
                onClick = {
                    runCatching {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "شاهد مقطع «${reel.title}» المميز عبر استوديو قبس: ${reel.videoUrl}"
                            )
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "مشاركة الريلز")
                        context.startActivity(shareIntent)
                        ReelPreferencesManager.recordShare(context)
                    }
                    onDismiss()
                }
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // 5. Not Interested
            OptionRow(
                icon = Icons.Default.VisibilityOff,
                title = "غير مهتم بهذا المحتوى",
                subtitle = "إخفاء هذا المقطع من خلاصتك الحالية",
                onClick = {
                    onNotInterested()
                    Toast.makeText(context, "تم إخفاء هذا المقطع وتحديث تفضيلاتك", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // 6. Report Offensive Content (Directly connected to developer chat)
            OptionRow(
                icon = Icons.Default.ReportProblem,
                title = "إبلاغ عن فيديو مسيء (متصل بمحادثة المطور)",
                subtitle = "إرسال بلاغ فوري ومباشر إلى محادثة المطور لمراجعة المقطع وحذفه",
                tint = Color(0xFFEF4444),
                onClick = {
                    showReportDialog = true
                }
            )

            // 7. Open Direct Dev Chat
            OptionRow(
                icon = Icons.Default.Chat,
                title = "محادثة حية مع المطور مباشرة",
                subtitle = "تواصل فوري مع فريق تطوير قبس للاقتراحات والملاحظات",
                tint = GoldPrimary,
                onClick = {
                    onDismiss()
                    onOpenDevChat?.invoke()
                }
            )

            // 8. Delete Option (Enabled for Video Owner OR Developer)
            if (canDelete) {
                HorizontalDivider(
                    color = Color(0xFFEF4444).copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                val deleteTitle = when {
                    isOwner && !isDeveloper -> "حذف مقطعي نهائياً"
                    isDeveloper && !isOwner -> "حذف المقطع من السحابة (صلاحية المطور)"
                    else -> "حذف المقطع نهائياً (المطور / صاحب الفيديو)"
                }
                val deleteSubtitle = when {
                    isOwner && !isDeveloper -> "إزالة هذا الفيديو الذي قمت بنشره نهائياً من منصة قبس"
                    isDeveloper && !isOwner -> "إزالة هذا المقطع نهائياً من سحابة Supabase بالإشراف التقني"
                    else -> "إزالة هذا المقطع نهائياً من قاعدة البيانات والمنصة"
                }

                OptionRow(
                    icon = Icons.Default.DeleteForever,
                    title = deleteTitle,
                    subtitle = deleteSubtitle,
                    tint = Color(0xFFEF4444),
                    onClick = {
                        showConfirmDeleteDialog = true
                    }
                )
            }

            // 9. Developer Telemetry
            if (isDeveloper) {
                OptionRow(
                    icon = Icons.Default.Analytics,
                    title = "فحص البيانات والتشخيص التقني (Telemetry)",
                    subtitle = "استعراض بيانات الفيديو وسرعة الاتصال ومعرفات النظام",
                    tint = GoldPrimary,
                    onClick = {
                        showTelemetryDialog = true
                    }
                )
            }
        }
    }

    // Dialog for Reporting Offensive Video (Directly connects to dev chat)
    if (showReportDialog) {
        val reasons = listOf(
            "محتوى مخالف للضوابط الشرعية أو به منكرات ⚠️",
            "يحتوي على موسيقى أو معازف محرمة 🎵",
            "محتوى مسيء، جارح أو يحث على الكراهية 🚫",
            "انتهاك خصوصية أو حقوق ملكية فكرية 🛡️",
            "محتوى وهمي أو مضلل ❌"
        )

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إبلاغ عن فيديو مسيء للمطور",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "المقطع: «${reel.title}»",
                                fontFamily = CairoFont,
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الناشر: ${reel.userName ?: "صانع المحتوى"}",
                                fontFamily = CairoFont,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "اختر سبب البلاغ (سيتم توجيهه فوراً لقناة المراسلة مع المطور):",
                        fontFamily = CairoFont,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.5.sp
                    )

                    reasons.forEach { reason ->
                        val isSelected = selectedReportReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                .clickable { selectedReportReason = reason }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedReportReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = reason,
                                fontFamily = CairoFont,
                                color = if (isSelected) GoldPrimary else Color.White,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reportAdditionalNotes,
                        onValueChange = { reportAdditionalNotes = it },
                        placeholder = {
                            Text(
                                text = "تفاصيل إضافية عن موضع الإساءة (اختياري)...",
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = GoldPrimary
                        ),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        onDismiss()
                        onReportToDevChat?.invoke(selectedReportReason, reportAdditionalNotes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إرسال لمحادثة المطور 🛰️",
                        fontFamily = CairoFont,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = DeepSlate
        )
    }

    // Confirmation Dialog for Deletion (Owner OR Developer)
    if (showConfirmDeleteDialog) {
        val isDevOnly = isDeveloper && !isOwner
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = {
                Text(
                    text = if (isDevOnly) "تأكيد حذف المقطع (صلاحية المطور)" else "تأكيد حذف مقطعك",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = if (isDevOnly) {
                        "هل أنت متأكد من حذف مقطع «${reel.title}» نهائياً من سحابة Supabase بصلاحية الإشراف التقني للمطور؟ لا يمكن التراجع عن هذا الإجراء."
                    } else {
                        "هل أنت متأكد من حذف مقطعك «${reel.title}»؟ سيتم حذفه نهائياً من المنصة والسحابة ولن يتمكن أحد من مشاهدته."
                    },
                    fontFamily = CairoFont,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteDialog = false
                        onDismiss()
                        onDeleteReel?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف نهائي", fontFamily = CairoFont, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = DeepSlate
        )
    }

    if (showTelemetryDialog) {
        AlertDialog(
            onDismissRequest = { showTelemetryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperMode, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بيانات التشخيص للمطور",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TelemetryItem(label = "Reel ID", value = reel.id)
                    TelemetryItem(label = "Publisher User ID", value = reel.userId)
                    TelemetryItem(label = "Publisher Name", value = reel.userName ?: "Unknown")
                    TelemetryItem(label = "Likes Count", value = "${reel.likesCount}")
                    TelemetryItem(label = "Comments Count", value = "${reel.commentsCount}")
                    TelemetryItem(label = "Shares Count", value = "${reel.sharesCount}")
                    TelemetryItem(label = "Video Stream URL", value = reel.videoUrl.take(45) + "...")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Reel JSON", "ReelID: ${reel.id}, UserID: ${reel.userId}, URL: ${reel.videoUrl}")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ بيانات التشخيص 📋", Toast.LENGTH_SHORT).show()
                        showTelemetryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate)
                ) {
                    Text("نسخ JSON", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTelemetryDialog = false }) {
                    Text("إغلاق", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = DeepSlate
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text(
                    text = "اختر سرعة التشغيل",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { speed ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSpeedSelected(speed)
                                    showSpeedDialog = false
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        ) {
                            Text(
                                text = "${speed}x السرعة",
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = DeepSlate
        )
    }
}

@Composable
private fun TelemetryItem(label: String, value: String) {
    Column {
        Text(text = label, fontFamily = RobotoMonoFont, fontSize = 10.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
        Text(text = value, fontFamily = RobotoMonoFont, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.10f))
                    .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = tint
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}
