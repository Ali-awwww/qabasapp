package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectPublisherDialog(
    scriptText: String,
    videoFile: File?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var postTitle by remember { mutableStateOf(scriptText.take(50).ifBlank { "فيديو دعوي جديد عبر تطبيق قبس 🌟" }) }
    var postDescription by remember { mutableStateOf("شاهد هذا المقطع الدعوي التدبري المبسط من إنتاج استوديو قبس لصناعة المحتوى الإسلامي ✨") }
    var hashtagsText by remember { mutableStateOf("#قبس #ريلز_إسلامي #تدبر #حديث_شريف #دعوة_إلكترونية") }

    var accounts by remember { mutableStateOf(SocialAccountManager.getAccounts(context)) }
    var selectedPlatformIds by remember {
        mutableStateOf(accounts.filter { it.isConnected }.map { it.id }.toMutableSet())
    }

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
        containerColor = DeepSlate,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("مركز النشر المباشر المتعدد (Cross-Publisher) 🚀", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Post Title Input
                item {
                    Column {
                        Text("عنوان المنشور 📝", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = postTitle,
                            onValueChange = { postTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = CardSurface,
                                unfocusedContainerColor = CardSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Hashtags Row & AI Generator
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الوسوم والهاشتاجات (Hashtags) #", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row {
                                TextButton(onClick = { showScheduleDialog = true }) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("جدولة ⏰", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { showSeoDialog = true }) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الـ SEO 📈", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = hashtagsText,
                            onValueChange = { hashtagsText = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = CardSurface,
                                unfocusedContainerColor = CardSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Target Platforms Selection List
                item {
                    Column {
                        Text("اختر المنصات المستهدفة للنشر الفوري 🌐", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { account ->
                                val isChecked = selectedPlatformIds.contains(account.id)
                                val platformColor = when (account.id) {
                                    "youtube" -> Color(0xFFF44336)
                                    "tiktok" -> Color(0xFF00F2FE)
                                    "instagram" -> Color(0xFFE1306C)
                                    "twitter" -> Color(0xFF1DA1F2)
                                    else -> Color(0xFF1877F2)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isChecked) platformColor.copy(alpha = 0.15f) else CardSurface, RoundedCornerShape(10.dp))
                                        .border(1.dp, if (isChecked) platformColor else Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val newSet = selectedPlatformIds.toMutableSet()
                                            if (isChecked) newSet.remove(account.id) else newSet.add(account.id)
                                            selectedPlatformIds = newSet
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    val newSet = selectedPlatformIds.toMutableSet()
                                                    if (checked == true) newSet.add(account.id) else newSet.remove(account.id)
                                                    selectedPlatformIds = newSet
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = platformColor)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(account.name, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(account.handle, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                            }
                                        }

                                        Text(
                                            if (account.isConnected) "مربوط 🟢" else "غير مربوط ⚪",
                                            color = if (account.isConnected) Color(0xFF4CAF50) else Color.Gray,
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Publishing Progress Indicator Box
                if (isPublishing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                                .border(1.dp, GoldPrimary, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(currentPublishLog, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Results list after publishing
                if (publishResults.isNotEmpty()) {
                    item {
                        Column {
                            Text("روابط المنشورات المباشرة 🔗", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                publishResults.forEach { res ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(res.platformName, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                                Text(res.postUrl, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }

                                            IconButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(res.postUrl))
                                                Toast.makeText(context, "تم نسخ رابط ${res.platformName}! 📋", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(16.dp))
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
                Button(
                    onClick = {
                        if (selectedPlatformIds.isEmpty()) {
                            Toast.makeText(context, "يرجى اختيار منصة واحدة على الأقل بالنقر عليها", Toast.LENGTH_SHORT).show()
                            return@Button
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
                            Toast.makeText(context, "تم النشر المباشر عبر المنصات بنجاح! 🎉", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isPublishing
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نشر فوري عبر كافة المنصات المختارة 🚀", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Native Share Fallback Button
                OutlinedButton(
                    onClick = {
                        SocialAccountManager.shareVideoNatively(context, videoFile, "$postTitle\n\n$postDescription\n\n$hashtagsText")
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("فتح في تطبيق المنصة الأصلي (Native Share Sheet) 📲", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPublishing
            ) {
                Text("إغلاق", color = Color.Gray, fontFamily = CairoFont)
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
