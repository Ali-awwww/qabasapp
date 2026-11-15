package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class ContactCategory(val key: String, val label: String, val icon: ImageVector)

private const val CONTACT_PREFS_NAME = "qabas_dev_chat_store"
private const val CONTACT_CATEGORY_KEY = "selected_contact_category"

private val contactCategories = listOf(
    ContactCategory("general", "رسالة عامة", Icons.Default.Chat),
    ContactCategory("feature", "اقتراح أو فكرة", Icons.Default.Lightbulb),
    ContactCategory("bug", "مشكلة تقنية", Icons.Default.BugReport),
    ContactCategory("prayer_issue", "الأذان والصلاة", Icons.Default.Mosque),
    ContactCategory("account_issue", "الحساب والملف الشخصي", Icons.Default.Person),
    ContactCategory("content_report", "بلاغ عن محتوى", Icons.Default.ReportProblem),
    ContactCategory("download_update", "التنزيل والتحديث", Icons.Default.CloudDownload),
    ContactCategory("general_question", "استفسار عام", Icons.Default.HelpOutline)
)

@Composable
private fun ContactAvatar(
    name: String,
    avatarUrl: String?,
    accent: Color
) {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = accent.copy(alpha = 0.18f)
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier.size(38.dp)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "؟",
                    color = accent,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperContactScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authEmail by SupabaseServices.Auth.currentUser.collectAsState()
    val accountIdentity by AccountIdentityManager.identity.collectAsState()
    val unreadReplies by DevChatRealtimeManager.unreadReplies.collectAsState()
    val userEmail = accountIdentity.email.ifBlank { authEmail?.email.orEmpty() }
    val localDisplayName = accountIdentity.displayName
    val localAvatarUrl = accountIdentity.avatarUrl
    val developerDisplayName = if (accountIdentity.isDeveloper) accountIdentity.displayName else "المطور"
    val developerAvatarUrl = if (accountIdentity.isDeveloper) accountIdentity.avatarUrl else null
    val isDeveloper = accountIdentity.isDeveloper
    var identityLoading by remember { mutableStateOf(true) }
    var threads by remember { mutableStateOf<Map<String, List<DevChatRepository.DevChatMessage>>>(emptyMap()) }
    var selectedThreadEmail by remember { mutableStateOf(userEmail) }
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf<List<DevChatRepository.DevChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    val savedCategoryKey = remember(context) {
        context.getSharedPreferences(CONTACT_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CONTACT_CATEGORY_KEY, null)
    }
    var selectedCategory by remember(savedCategoryKey) {
        mutableStateOf(contactCategories.firstOrNull { it.key == savedCategoryKey })
    }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var pendingMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var developerFilter by remember { mutableStateOf(0) }
    var threadProfiles by remember { mutableStateOf<Map<String, AccountIdentity>>(emptyMap()) }

    BackHandler {
        if (isDeveloper && selectedThreadEmail.isNotBlank()) {
            selectedThreadEmail = ""
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        DevChatRealtimeManager.setChatOpen(true)
        onDispose { DevChatRealtimeManager.setChatOpen(false) }
    }

    LaunchedEffect(authEmail?.id, userEmail) {
        runCatching { AccountIdentityManager.refresh(context) }
        identityLoading = false
    }

    LaunchedEffect(accountIdentity.isDeveloper, userEmail) {
        if (isDeveloper) {
            threads = DevChatRepository.getAllThreads(context)
            selectedThreadEmail = ""
            messages = emptyList()
        } else if (userEmail.isNotBlank()) {
            selectedThreadEmail = userEmail
            messages = DevChatRepository.getLocalMessages(context, userEmail)
            messages = DevChatRepository.syncMessages(context, userEmail)
        }
    }

    LaunchedEffect(selectedThreadEmail, isDeveloper) {
        if (isDeveloper && selectedThreadEmail.isNotBlank()) {
            messages = threads[selectedThreadEmail].orEmpty()
        }
    }

    LaunchedEffect(isDeveloper, selectedThreadEmail, messages.size) {
        if (isDeveloper && messages.isNotEmpty()) {
            val userIds = messages.filter { it.sender.equals("user", ignoreCase = true) }
                .mapNotNull { it.userId }
                .distinct()
            if (userIds.isNotEmpty()) {
                val profiles = userIds.mapNotNull { id ->
                    SupabaseServices.Profiles.getUserProfile(id)?.let { id to it }
                }.toMap()
                if (profiles.isNotEmpty()) {
                    messages = messages.map { message ->
                        val profile = profiles[message.userId]
                        if (profile != null) message.copy(
                            senderDisplayName = profile.fullName?.takeIf { it.isNotBlank() }
                                ?: message.senderDisplayName,
                            senderAvatarUrl = profile.avatarUrl ?: message.senderAvatarUrl
                        ) else message
                    }
                }
            }
        }
    }

    LaunchedEffect(isDeveloper, threads.keys.toList()) {
        if (isDeveloper) {
            val profileMap = mutableMapOf<String, AccountIdentity>()
            threads.forEach { (email, thread) ->
                val firstUserMessage = thread.firstOrNull { it.sender.equals("user", ignoreCase = true) }
                val profile = firstUserMessage?.userId?.let { SupabaseServices.Profiles.getUserProfile(it) }
                profileMap[email] = AccountIdentity(
                    userId = firstUserMessage?.userId,
                    email = email,
                    displayName = profile?.fullName?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore("@").ifBlank { "المستخدم" },
                    avatarUrl = profile?.avatarUrl
                )
            }
            threadProfiles = profileMap
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val visibleThreadEmails = threads.keys.filter { email ->
        val thread = threads[email].orEmpty()
        when (developerFilter) {
            1 -> thread.withIndex().any { indexed ->
                indexed.value.sender.equals("user", ignoreCase = true) &&
                    thread.drop(indexed.index + 1).none { it.sender.equals("developer", ignoreCase = true) }
            }
            2 -> thread.any { it.category.equals("bug", ignoreCase = true) }
            3 -> thread.any { it.category == "content_report" }
            else -> true
        }
    }
    val sortedThreadEmails = visibleThreadEmails.sortedByDescending { email ->
        threads[email].orEmpty().maxOfOrNull { it.createdAt } ?: 0L
    }

    fun sendMessageNow(text: String, category: ContactCategory) {
        val conversationEmail = if (isDeveloper) selectedThreadEmail else userEmail
        val targetUserId = messages.firstOrNull { it.sender.equals("user", ignoreCase = true) }?.userId
        if (text.isBlank() || isSending || conversationEmail.isBlank()) return
        isSending = true
        scope.launch {
            runCatching {
                DevChatRepository.sendMessage(
                    context = context,
                    userEmail = conversationEmail,
                    text = text,
                    sender = if (isDeveloper) "developer" else "user",
                    targetUserId = if (isDeveloper) targetUserId else null,
                    category = category.key,
                    isUrgent = !isDeveloper && category.key == "content_report"
                )
            }.onSuccess {
                input = ""
                messages = DevChatRepository.getLocalMessages(context, conversationEmail)
                if (isDeveloper) threads = DevChatRepository.getAllThreads(context)
                Toast.makeText(
                    context,
                    if (isDeveloper) "تم إرسال الرد إلى المستخدم" else "تم إرسال رسالتك إلى المطور",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    if (isDeveloper) "تعذر إرسال الرد إلى السحابة" else "تم حفظ الرسالة محليًا، وسنحاول مزامنتها عند عودة الاتصال",
                    Toast.LENGTH_LONG
                ).show()
            }
            isSending = false
        }
    }

    fun chooseCategory(category: ContactCategory) {
        selectedCategory = category
        context.getSharedPreferences(CONTACT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CONTACT_CATEGORY_KEY, category.key)
            .apply()
        showCategoryPicker = false
        val queued = pendingMessage.trim()
        pendingMessage = ""
        if (queued.isNotBlank()) sendMessageNow(queued, category)
    }

    if (identityLoading) {
        Box(modifier = Modifier.fillMaxSize().background(DeepSlate), contentAlignment = Alignment.Center) {
            Text("جارٍ التحقق من هوية الحساب...", color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
        }
        return
    }

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("التواصل مع المطور", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        Text(
                            if (isDeveloper) "لوحة المحادثات — ${threads.size} محادثة"
                            else if (unreadReplies > 0) "لديك $unreadReplies ردود جديدة" else "رسالتك تصل إلى لوحة المطور",
                            fontFamily = CairoFont,
                            color = if (unreadReplies > 0) GoldPrimary else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !isRefreshing,
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                if (isDeveloper) {
                                    threads = DevChatRepository.getAllThreads(context)
                                    messages = threads[selectedThreadEmail].orEmpty()
                                } else if (userEmail.isNotBlank()) {
                                    messages = DevChatRepository.syncMessages(context, userEmail)
                                }
                                isRefreshing = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث المحادثة", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF132238),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isDeveloper) "اختر محادثة للرد عليها، وستظهر رسالتك للمستخدم مباشرة."
                        else "اختر تصنيف الرسالة ثم اكتب ملاحظتك. ستظهر رسالتك في لوحة المطور للمراجعة والرد.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            if (isDeveloper) {
                Text(
                    if (selectedThreadEmail.isBlank()) "المحادثات — الأحدث أولاً" else "المحادثة الحالية",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    itemsIndexed(listOf("الكل", "بانتظار الرد", "أعطال", "بلاغات محتوى")) { index, label ->
                        val selected = developerFilter == index
                        Surface(
                            onClick = { developerFilter = index },
                            color = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) DeepSlate else Color.White.copy(alpha = 0.85f),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                if (selectedThreadEmail.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedThreadEmail = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة للمحادثات", tint = GoldPrimary)
                            Text("المحادثات", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                        Text(
                            threadProfiles[selectedThreadEmail]?.displayName
                                ?: selectedThreadEmail.substringBefore("@").ifBlank { "المستخدم" },
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else if (sortedThreadEmails.isEmpty()) {
                    Text("لا توجد محادثات مطابقة للفلتر", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(sortedThreadEmails, key = { it }) { email ->
                            val thread = threads[email].orEmpty()
                            val lastMessage = thread.maxByOrNull { it.createdAt }
                            val profile = threadProfiles[email]
                            val displayName = profile?.displayName
                                ?: email.substringBefore("@").ifBlank { "المستخدم" }
                            Surface(
                                onClick = { selectedThreadEmail = email },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF132238),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(displayName, profile?.avatarUrl, Color(0xFF7DD3FC))
                                    Spacer(Modifier.width(9.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(displayName, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            Text(lastMessage?.formattedTime ?: "", color = TextSecondary, fontFamily = CairoFont, fontSize = 9.sp)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(lastMessage?.message.orEmpty(), color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp, maxLines = 1)
                                    }
                                    if (lastMessage?.sender.equals("user", ignoreCase = true)) {
                                        Spacer(Modifier.width(7.dp))
                                        Surface(color = Color(0xFFFBBF24), shape = CircleShape) {
                                            Box(modifier = Modifier.size(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isDeveloper) {
                selectedCategory?.let { category ->
                    Surface(
                        onClick = { showCategoryPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        color = GoldPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(category.icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("الموضوع المحفوظ: ${category.label}", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("تغيير", color = TextSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                        }
                    }
                }
            }

            if (!isDeveloper || selectedThreadEmail.isNotBlank()) {
                if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد رسائل بعد\nابدأ أول محادثة مع المطور", color = TextSecondary, fontFamily = CairoFont, textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        val isUserMessage = message.sender.equals("user", ignoreCase = true)
                        val isMine = if (isDeveloper) !isUserMessage else isUserMessage
                        val senderName = when {
                            isDeveloper && isUserMessage -> message.senderDisplayName ?: message.userEmail.ifBlank { "المستخدم" }
                            isUserMessage -> localDisplayName
                            else -> developerDisplayName
                        }
                        val senderAvatar = when {
                            isDeveloper && isUserMessage -> message.senderAvatarUrl
                            isUserMessage -> localAvatarUrl
                            else -> developerAvatarUrl
                        }
                        val hasReplyAfter = messages.drop(index + 1).any { it.sender.equals("developer", ignoreCase = true) }
                        val statusLabel = when {
                            !isUserMessage -> ""
                            hasReplyAfter -> "✓✓ تم الرد"
                            message.status == "pending" -> "⏳ بانتظار الاتصال"
                            else -> "✓ مرسلة"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMine) {
                                ContactAvatar(senderName, senderAvatar, Color(0xFF7DD3FC))
                                Spacer(Modifier.width(6.dp))
                            }
                            Surface(
                                color = if (isMine) Color(0xFF1C3B45) else Color(0xFF263449),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(0.78f)
                            ) {
                                Column(modifier = Modifier.padding(11.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (isMine) "أنت" else senderName,
                                            color = if (isMine) GoldPrimary else Color(0xFF7DD3FC),
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isUserMessage && message.category.isNotBlank()) {
                                            Text(
                                                contactCategories.firstOrNull { it.key == message.category }?.label ?: message.category,
                                                color = TextSecondary,
                                                fontFamily = CairoFont,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(message.message, color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, lineHeight = 19.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        listOf(message.formattedTime, statusLabel).filter { it.isNotBlank() }.joinToString(" • "),
                                        color = if (statusLabel.contains("بانتظار")) Color(0xFFFBBF24) else TextSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 9.sp
                                    )
                                    if (isMine && message.status == "pending") {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    val updated = DevChatRepository.retryPendingMessage(context, message)
                                                    messages = messages.map { if (it.id == updated.id) updated else it }
                                                    if (isDeveloper) threads = DevChatRepository.getAllThreads(context)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                        ) {
                                            Text("إعادة الإرسال", color = Color(0xFFFBBF24), fontFamily = CairoFont, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            if (isMine) {
                                Spacer(Modifier.width(6.dp))
                                ContactAvatar(senderName, senderAvatar, GoldPrimary)
                            }
                        }
                    }
                }
                }
            }

            if (!isDeveloper || selectedThreadEmail.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    placeholder = { Text("اكتب رسالتك...", fontFamily = CairoFont, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = GoldPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        val text = input.trim()
                        if (text.isBlank() || isSending) return@Button
                        if (!isDeveloper && selectedCategory == null) {
                            pendingMessage = text
                            showCategoryPicker = true
                        } else {
                            sendMessageNow(text, selectedCategory ?: contactCategories.first())
                        }
                    },
                    enabled = input.isNotBlank() && !isSending && (if (isDeveloper) selectedThreadEmail.isNotBlank() else userEmail.isNotBlank()),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = DeepSlate)
                }
            }
        }
    }

    if (showCategoryPicker && !isDeveloper) {
        AlertDialog(
            onDismissRequest = {
                showCategoryPicker = false
                pendingMessage = ""
            },
            containerColor = Color(0xFF132238),
            title = {
                Text("اختر موضوع الرسالة", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("لن تُرسل الرسالة قبل اختيار تصنيفها. سيُحفظ اختيارك للرسائل القادمة.", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    contactCategories.forEach { category ->
                        Surface(
                            onClick = { chooseCategory(category) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(category.icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(category.label, color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false; pendingMessage = "" }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            }
        )
    }
}
