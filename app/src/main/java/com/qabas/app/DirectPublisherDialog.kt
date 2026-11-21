package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DirectPublisherDialog(
    scriptText: String,
    videoFile: File?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var postTitle by remember { mutableStateOf(scriptText.take(50).ifBlank { "فيديو دعوي جديد عبر تطبيق مساعد المسلم" }) }
    var postDescription by remember { mutableStateOf("شاهد هذا المقطع الدعوي التدبري من إنتاج استوديو مساعد المسلم") }
    var hashtagsText by remember { mutableStateOf("#مساعد_المسلم #ريلز_إسلامي #تدبر #حديث_شريف #دعوة_إلكترونية") }

    val accounts = remember { SocialAccountManager.getAccounts(context) }
    var selectedPlatformIds by remember {
        mutableStateOf(accounts.filter { it.isConnected }.map { it.id }.toMutableSet())
    }

    val canDirectPublish = remember { ServiceAvailabilityRegistry.canPublishDirectlyViaSocialApi() }
    var isPublishing by remember { mutableStateOf(false) }
    var currentPublishLog by remember { mutableStateOf("") }
    var publishResults by remember { mutableStateOf<List<PublishResult>>(emptyList()) }
    var showSeoDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "مركز النشر والمشاركة",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                if (!canDirectPublish) {
                    item {
                        AppleKeyRequiredBanner(
                            serviceName = "النشر الآلي عبر واجهات المنصات",
                            reason = "النشر المباشر التلقائي يتطلب تفعيل مفاتيح OAuth Client ID لرفع الفيديو. يمكنك استخدام «المشاركة الأصلية للنظام» أدناه فوراً لنشر الفيديو."
                        )
                    }
                }

                item {
                    Column {
                        Text("عنوان المنشور", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = postTitle,
                            onValueChange = { postTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primary,
                                unfocusedBorderColor = theme.separator,
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary,
                                focusedContainerColor = theme.secondarySurface,
                                unfocusedContainerColor = theme.secondarySurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الوسوم (Hashtags)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row {
                                TextButton(onClick = { showScheduleDialog = true }) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = theme.primary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("جدولة", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { showSeoDialog = true }) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = theme.primary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SEO", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = hashtagsText,
                            onValueChange = { hashtagsText = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primary,
                                unfocusedBorderColor = theme.separator,
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary,
                                focusedContainerColor = theme.secondarySurface,
                                unfocusedContainerColor = theme.secondarySurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("المنصات المربوطة", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        accounts.forEach { account ->
                            val isChecked = selectedPlatformIds.contains(account.id)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canDirectPublish) {
                                        val newSet = selectedPlatformIds.toMutableSet()
                                        if (isChecked) newSet.remove(account.id) else newSet.add(account.id)
                                        selectedPlatformIds = newSet
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChecked) theme.primary.copy(alpha = 0.08f) else theme.secondarySurface,
                                border = BorderStroke(0.5.dp, if (isChecked) theme.primary else theme.separator.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked,
                                            enabled = canDirectPublish,
                                            onCheckedChange = { checked ->
                                                val newSet = selectedPlatformIds.toMutableSet()
                                                if (checked == true) newSet.add(account.id) else newSet.remove(account.id)
                                                selectedPlatformIds = newSet
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text(account.name, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(account.handle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        text = if (account.isConnected) "مربوط" else "غير مربوط",
                                        color = if (account.isConnected) theme.success else theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (isPublishing) {
                    item {
                        AppleCard(containerColor = theme.secondarySurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(currentPublishLog, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (publishResults.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("حالة النشر", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            publishResults.forEach { res ->
                                AppleCard(containerColor = theme.secondarySurface) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (res.isSuccess) theme.success else theme.warning,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(res.platformName, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Text(
                                                text = if (res.postUrl.isNotBlank()) res.postUrl else res.message,
                                                color = theme.textSecondary,
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (res.postUrl.isNotBlank()) {
                                            IconButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(res.postUrl))
                                                Toast.makeText(context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApplePrimaryButton(
                    text = "فتح في تطبيقات النشر (Native Share)",
                    icon = Icons.Default.Share,
                    modifier = Modifier.testTag("native_share_video_button"),
                    onClick = {
                        SocialAccountManager.shareVideoNatively(
                            context,
                            videoFile,
                            "$postTitle\n\n$postDescription\n\n$hashtagsText"
                        )
                    }
                )

                AppleSecondaryButton(
                    text = "نشر آلي عبر API (يتطلب مفاتيح OAuth)",
                    icon = Icons.Default.Send,
                    enabled = canDirectPublish && !isPublishing,
                    onClick = {
                        if (selectedPlatformIds.isEmpty()) {
                            Toast.makeText(context, "يرجى اختيار منصة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                            return@AppleSecondaryButton
                        }
                        isPublishing = true
                        scope.launch {
                            publishResults = SocialAccountManager.publishVideoToPlatforms(
                                context = context,
                                title = postTitle,
                                description = postDescription,
                                hashtags = hashtagsText,
                                selectedPlatformIds = selectedPlatformIds.toList(),
                                onProgressUpdate = { log -> currentPublishLog = log }
                            )
                            isPublishing = false
                        }
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPublishing) {
                Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
            }
        }
    )

    if (showSeoDialog) {
        ViralSeoHashtagsDialog(
            context = context,
            initialTopicOrScript = postTitle,
            onDismiss = { showSeoDialog = false },
            onApplySeoData = { selectedTitle, hashtags ->
                if (selectedTitle.isNotBlank()) postTitle = selectedTitle
                if (hashtags.isNotBlank()) hashtagsText = hashtags
                showSeoDialog = false
            }
        )
    }

    if (showScheduleDialog) {
        SmartPublishScheduleDialog(
            initialTitle = postTitle,
            initialHashtags = hashtagsText,
            onDismiss = { showScheduleDialog = false }
        )
    }
}
