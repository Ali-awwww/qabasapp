package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showTelemetryDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    var selectedReportReason by remember { mutableStateOf("محتوى مخالف للضوابط الشرعية أو به منكرات") }
    var reportAdditionalNotes by remember { mutableStateOf("") }

    val verifiedRole by SupabaseServices.Auth.verifiedRole.collectAsState()
    val isDeveloper = remember(currentUserEmail, verifiedRole) {
        ReelPublishManager.isDeveloperUser(context, currentUserEmail)
    }

    val isOwner = remember(reel, currentUserEmail, currentUserId) {
        ReelPublishManager.isReelOwner(context, currentUserEmail, currentUserId, reel)
    }

    val canDelete = isDeveloper || isOwner

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "خيارات المقطع",
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

            AppleGroupedSection {
                AppleListRow(
                    icon = if (isSaved) Icons.Default.BookmarkRemove else Icons.Default.Bookmark,
                    title = if (isSaved) "إزالة من المحفوظات" else "حفظ المقطع في المفضلة",
                    subtitle = "الوصول السريع للمقطع في أي وقت عبر السجل",
                    iconBackground = theme.primary,
                    onClick = {
                        onToggleSave()
                        onDismiss()
                    }
                )
                AppleInsetDivider()
                AppleListRow(
                    icon = Icons.Default.FileDownload,
                    title = "تنزيل الريل إلى مركز التنزيلات",
                    subtitle = "حفظ الفيديو في الهاتف للمشاهدة بدون إنترنت",
                    iconBackground = theme.success,
                    onClick = {
                        DownloadsHubManager.downloadReel(
                            context = context,
                            reelId = reel.id,
                            title = reel.title,
                            videoUrl = reel.videoUrl,
                            thumbnailUrl = reel.thumbnailUrl,
                            author = reel.userName,
                            onComplete = { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "اكتمل تنزيل الريل بنجاح" else "فشل تنزيل الريل، حاول مرة أخرى",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        Toast.makeText(context, "بدأ تنزيل الريل للمشاهدة دون إنترنت", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
                AppleInsetDivider()
                AppleListRow(
                    icon = Icons.Default.Speed,
                    title = "سرعة تشغيل الريلز",
                    subtitle = "تطبيق السرعة على المقاطع أثناء التصفح",
                    iconBackground = theme.accent,
                    onClick = { showSpeedDialog = true }
                )
                AppleInsetDivider()
                AppleListRow(
                    icon = Icons.Default.ContentCopy,
                    title = "نسخ رابط الريل",
                    subtitle = "مشاركة الرابط المباشر مع أصدقائك",
                    iconBackground = theme.primary,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Muslim AI Assistant Reel Link", reel.videoUrl)
                        clipboard.setPrimaryClip(clip)
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم نسخ رابط المقطع إلى الحافظة", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
                AppleInsetDivider()
                AppleListRow(
                    icon = Icons.Default.Share,
                    title = "مشاركة عبر التطبيقات",
                    subtitle = "إرسال المقطع عبر واتساب، تيليجرام وغيرها",
                    iconBackground = theme.success,
                    onClick = {
                        runCatching {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "شاهد مقطع «${reel.title}» المميز عبر استوديو خادم المسلم: ${reel.videoUrl}"
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
            }

            AppleGroupedSection {
                AppleListRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "غير مهتم بهذا المحتوى",
                    subtitle = "إخفاء هذا المقطع من خلاصتك الحالية",
                    iconBackground = theme.textSecondary,
                    onClick = {
                        onNotInterested()
                        Toast.makeText(context, "تم إخفاء هذا المقطع وتحديث تفضيلاتك", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
                AppleInsetDivider()
                AppleListRow(
                    icon = Icons.Default.ReportProblem,
                    title = "إبلاغ عن مشكلة في المقطع",
                    subtitle = "إرسال البلاغ إلى فريق الإشراف للمراجعة",
                    iconBackground = theme.destructive,
                    destructive = true,
                    onClick = { showReportDialog = true }
                )
            }

            if (canDelete || isDeveloper) {
                AppleGroupedSection(header = if (isDeveloper) "إدارة المقطع" else null) {
                    if (canDelete) {
                        val deleteTitle = when {
                            isOwner && !isDeveloper -> "حذف مقطعي نهائياً"
                            isDeveloper && !isOwner -> "حذف المقطع من السحابة (المطور)"
                            else -> "حذف المقطع نهائياً"
                        }
                        val deleteSubtitle = when {
                            isOwner && !isDeveloper -> "إزالة هذا الفيديو الذي قمت بنشره نهائياً من منصة خادم المسلم"
                            isDeveloper && !isOwner -> "إزالة هذا المقطع نهائياً من سحابة Supabase"
                            else -> "إزالة هذا المقطع نهائياً من قاعدة البيانات"
                        }
                        AppleListRow(
                            icon = Icons.Default.DeleteForever,
                            title = deleteTitle,
                            subtitle = deleteSubtitle,
                            iconBackground = theme.destructive,
                            destructive = true,
                            onClick = { showConfirmDeleteDialog = true }
                        )
                    }
                    if (canDelete && isDeveloper) {
                        AppleInsetDivider()
                    }
                    if (isDeveloper) {
                        AppleListRow(
                            icon = Icons.Default.Analytics,
                            title = "فحص البيانات والتشخيص التقني",
                            subtitle = "استعراض بيانات الفيديو وسرعة الاتصال والمعرّفات",
                            iconBackground = theme.warning,
                            onClick = { showTelemetryDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        val reasons = listOf(
            "محتوى مخالف للضوابط الشرعية أو به منكرات",
            "يحتوي على موسيقى أو معازف مخالفة",
            "محتوى مسيء، جارح أو يحث على الكراهية",
            "انتهاك خصوصية أو حقوق ملكية فكرية",
            "محتوى وهمي أو مضلل"
        )

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = theme.cardSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = theme.destructive,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إبلاغ عن مشكلة في المقطع",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppleCard(containerColor = theme.secondarySurface) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "المقطع: «${reel.title}»",
                                fontFamily = CairoFont,
                                color = theme.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الناشر: ${reel.userName ?: "صانع المحتوى"}",
                                fontFamily = NotoSansFont,
                                color = theme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = "اختر سبب البلاغ وسيتم توجيهه لفريق الإشراف:",
                        fontFamily = NotoSansFont,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )

                    reasons.forEach { reason ->
                        val isSelected = selectedReportReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) theme.primary.copy(alpha = 0.12f)
                                    else theme.secondarySurface
                                )
                                .clickable { selectedReportReason = reason }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedReportReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = reason,
                                fontFamily = CairoFont,
                                color = if (isSelected) theme.primary else theme.textPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reportAdditionalNotes,
                        onValueChange = { reportAdditionalNotes = it },
                        placeholder = {
                            Text(
                                text = "تفاصيل إضافية (اختياري)...",
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            color = theme.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.separator,
                            cursorColor = theme.primary
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إرسال البلاغ",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReportDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary)
                }
            }
        )
    }

    if (showConfirmDeleteDialog) {
        val isDevOnly = isDeveloper && !isOwner
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            containerColor = theme.cardSurface,
            title = {
                Text(
                    text = if (isDevOnly) "تأكيد حذف المقطع (صلاحية المطور)" else "تأكيد حذف مقطعك",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Text(
                    text = if (isDevOnly) {
                        "هل أنت متأكد من حذف مقطع «${reel.title}» نهائياً من سحابة Supabase؟ لا يمكن التراجع عن هذا الإجراء."
                    } else {
                        "هل أنت متأكد من حذف مقطعك «${reel.title}»؟ سيتم حذفه نهائياً من المنصة."
                    },
                    fontFamily = NotoSansFont,
                    color = theme.textSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteDialog = false
                        onDismiss()
                        onDeleteReel?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("حذف نهائي", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDeleteDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary)
                }
            }
        )
    }

    if (showTelemetryDialog) {
        AlertDialog(
            onDismissRequest = { showTelemetryDialog = false },
            containerColor = theme.cardSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperMode, null, tint = theme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بيانات التشخيص للمطور",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Toast.makeText(context, "تم نسخ بيانات التشخيص", Toast.LENGTH_SHORT).show()
                        showTelemetryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("نسخ البيانات", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTelemetryDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إغلاق", fontFamily = CairoFont, color = theme.textSecondary)
                }
            }
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = theme.cardSurface,
            title = {
                Text(
                    text = "سرعة التشغيل",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { speed ->
                        Surface(
                            onClick = {
                                onSpeedSelected(speed)
                                showSpeedDialog = false
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = theme.secondarySurface
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "${speed}x",
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showSpeedDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun TelemetryItem(label: String, value: String) {
    val theme = QabasTheme.current
    Column {
        Text(text = label, fontFamily = RobotoMonoFont, fontSize = 10.sp, color = theme.primary, fontWeight = FontWeight.Bold)
        Text(text = value, fontFamily = RobotoMonoFont, fontSize = 11.5.sp, color = theme.textPrimary)
    }
}

