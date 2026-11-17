package com.qabas.app

import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private val DeveloperInboxBackground = Color(0xFF070B14)
private val DeveloperInboxCard = Color(0xFF14213A)
private val DeveloperInboxBorder = Color(0xFF263550)
private val DeveloperInboxAccent = Color(0xFF19CFE0)
private val DeveloperInboxPrimaryText = Color(0xFFEAF2FF)
private val DeveloperInboxSecondaryText = Color(0xFF9AA9BE)

private fun formatContactTime(message: DevChatRepository.DevChatMessage?): String {
    if (message == null) return ""
    return if (message.createdAt > 0L) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(message.createdAt))
    } else {
        message.formattedTime
    }
}

@Composable
private fun ContactAvatar(
    name: String,
    avatarUrl: String?,
    accent: Color,
    size: Dp = 38.dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = accent.copy(alpha = 0.18f)
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier.size(size)
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
    val theme = QabasTheme.current
    val isDeveloper = accountIdentity.isDeveloper
    val contactBackground = theme.background
    val contactCard = theme.cardSurface
    val contactBorder = theme.separator.copy(alpha = 0.5f)
    val contactAccent = theme.primary
    val contactPrimaryText = theme.textPrimary
    val contactSecondaryText = theme.textSecondary
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
    var threadMenuEmail by remember { mutableStateOf<String?>(null) }
    var messageMenuId by remember { mutableStateOf<String?>(null) }
    var messageToEdit by remember { mutableStateOf<DevChatRepository.DevChatMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var messageToDelete by remember { mutableStateOf<DevChatRepository.DevChatMessage?>(null) }
    var conversationToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var profileToOpen by remember { mutableStateOf<AccountIdentity?>(null) }
    var publicProfile by remember { mutableStateOf<SupabaseServices.PublicCreatorProfileRow?>(null) }
    var publicProfileHandle by remember { mutableStateOf<String?>(null) }
    var publicProfileReelCount by remember { mutableStateOf<Int?>(null) }
    var isPublicProfileLoading by remember { mutableStateOf(false) }
    var isChatActionInProgress by remember { mutableStateOf(false) }

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

    LaunchedEffect(profileToOpen?.userId) {
        val targetUserId = profileToOpen?.userId?.takeIf { it.isNotBlank() }
        publicProfile = null
        publicProfileHandle = null
        publicProfileReelCount = null
        if (targetUserId == null) {
            isPublicProfileLoading = false
        } else {
            isPublicProfileLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        SupabaseServices.Profiles.getPublicCreatorProfile(targetUserId),
                        SupabaseServices.Profiles.getCreatorCustomId(targetUserId),
                        SupabaseServices.Profiles.getPublicCreatorReels(targetUserId).size
                    )
                }
            }.onSuccess { (profile, handle, reelCount) ->
                publicProfile = profile
                publicProfileHandle = handle
                publicProfileReelCount = reelCount
            }
            isPublicProfileLoading = false
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
    val selectedThreadMessages = threads[selectedThreadEmail].orEmpty()
    val selectedThreadDisplayName = threadProfiles[selectedThreadEmail]?.displayName
        ?: selectedThreadEmail.substringBefore("@").ifBlank { "المستخدم" }
    val selectedThreadCategoryKey = selectedThreadMessages
        .filter { it.sender.equals("user", ignoreCase = true) && it.category.isNotBlank() }
        .maxByOrNull { it.createdAt }
        ?.category
        ?: selectedThreadMessages.maxByOrNull { it.createdAt }?.category
    val selectedThreadCategoryLabel = selectedThreadCategoryKey?.let { categoryKey ->
        contactCategories.firstOrNull { it.key == categoryKey }?.label
            ?: categoryKey.takeIf { it.isNotBlank() }
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
        Box(modifier = Modifier.fillMaxSize().background(contactBackground), contentAlignment = Alignment.Center) {
            Text("جارٍ التحقق من هوية الحساب...", color = contactSecondaryText, fontFamily = CairoFont, fontSize = 13.sp)
        }
        return
    }

    Scaffold(
        containerColor = contactBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (isDeveloper && selectedThreadEmail.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(
                                selectedThreadDisplayName,
                                threadProfiles[selectedThreadEmail]?.avatarUrl,
                                contactAccent,
                                size = 34.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "التواصل مع المطور",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    color = contactPrimaryText,
                                    fontSize = 14.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        selectedThreadDisplayName,
                                        color = contactSecondaryText,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    selectedThreadCategoryLabel?.let { categoryLabel ->
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = contactAccent.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                categoryLabel,
                                                color = contactAccent,
                                                fontFamily = CairoFont,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column {
                            Text("التواصل مع المطور", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = contactPrimaryText, fontSize = 18.sp)
                            Text(
                                if (isDeveloper) "لوحة المحادثات — ${threads.size} محادثة"
                                else if (unreadReplies > 0) "لديك $unreadReplies ردود جديدة" else "رسالتك تصل إلى لوحة المطور",
                                fontFamily = CairoFont,
                                color = if (unreadReplies > 0) contactAccent else contactSecondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isDeveloper && selectedThreadEmail.isNotBlank()) {
                                selectedThreadEmail = ""
                                messages = emptyList()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isDeveloper && selectedThreadEmail.isNotBlank()) "العودة للمحادثات" else "رجوع",
                            tint = contactAccent
                        )
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
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث المحادثة", tint = contactAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = contactBackground)
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (!isDeveloper || selectedThreadEmail.isBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, contactBorder, RoundedCornerShape(18.dp)),
                    color = contactCard,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = contactAccent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isDeveloper) "اختر محادثة للرد عليها، وستظهر رسالتك للمستخدم مباشرة."
                            else "اختر تصنيف الرسالة ثم اكتب ملاحظتك. ستظهر رسالتك في لوحة المطور للمراجعة والرد.",
                            color = contactPrimaryText.copy(alpha = 0.92f),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (isDeveloper && selectedThreadEmail.isBlank()) {
                Text(
                    "المحادثات — الأحدث أولاً",
                    color = contactSecondaryText,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    itemsIndexed(listOf("الكل", "بانتظار الرد", "أعطال", "بلاغات محتوى")) { index, label ->
                        val selected = developerFilter == index
                        Surface(
                            onClick = { developerFilter = index },
                            modifier = Modifier.border(
                                1.dp,
                                if (selected) contactAccent.copy(alpha = 0.75f) else contactBorder,
                                RoundedCornerShape(18.dp)
                            ),
                            color = if (selected) contactAccent else contactCard,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) DeveloperInboxBackground else contactPrimaryText.copy(alpha = 0.9f),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                if (sortedThreadEmails.isEmpty()) {
                    Text("لا توجد محادثات مطابقة للفلتر", color = contactSecondaryText, fontFamily = CairoFont, fontSize = 12.sp)
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
                            val waitingForReply = lastMessage?.sender.equals("user", ignoreCase = true)
                            Surface(
                                onClick = { selectedThreadEmail = email },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, contactBorder, RoundedCornerShape(18.dp)),
                                color = contactCard,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(displayName, profile?.avatarUrl, contactAccent, size = 44.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                displayName,
                                                color = contactPrimaryText,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (waitingForReply) {
                                                Surface(
                                                    color = contactAccent.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Text(
                                                        "بانتظار ردك",
                                                        color = contactAccent,
                                                        fontFamily = CairoFont,
                                                        fontSize = 9.sp,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    formatContactTime(lastMessage),
                                                    color = contactSecondaryText,
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            lastMessage?.message?.takeIf { it.isNotBlank() } ?: "لا توجد رسائل بعد",
                                            color = contactSecondaryText,
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box {
                                        IconButton(
                                            onClick = { threadMenuEmail = email },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "إجراءات المحادثة", tint = contactSecondaryText)
                                        }
                                        DropdownMenu(
                                            expanded = threadMenuEmail == email,
                                            onDismissRequest = { threadMenuEmail = null },
                                            containerColor = contactCard
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("فتح ملف المستخدم", color = contactPrimaryText, fontFamily = CairoFont) },
                                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = contactAccent) },
                                                enabled = !profile?.userId.isNullOrBlank(),
                                                onClick = {
                                                    profile?.let { profileToOpen = it }
                                                    threadMenuEmail = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("حذف المحادثة", color = Color(0xFFFF8A8A), fontFamily = CairoFont) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF8A8A)) },
                                                enabled = !profile?.userId.isNullOrBlank(),
                                                onClick = {
                                                    profile?.userId?.let { conversationToDelete = email to it }
                                                    threadMenuEmail = null
                                                }
                                            )
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
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        val isUserMessage = message.sender.equals("user", ignoreCase = true)
                        val isMine = if (isDeveloper) !isUserMessage else isUserMessage
                        val previousMessage = messages.getOrNull(index - 1)
                        val nextMessage = messages.getOrNull(index + 1)
                        val startsNewGroup = previousMessage == null ||
                            !previousMessage.sender.equals(message.sender, ignoreCase = true)
                        val endsGroup = nextMessage == null ||
                            !nextMessage.sender.equals(message.sender, ignoreCase = true)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (startsNewGroup) 6.dp else 0.dp),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMine) {
                                if (endsGroup) {
                                    ContactAvatar(senderName, senderAvatar, contactAccent, size = 34.dp)
                                } else {
                                    Spacer(Modifier.size(34.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Surface(
                                color = if (isMine) {
                                    contactAccent.copy(alpha = 0.16f)
                                } else {
                                    theme.secondarySurface
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(0.78f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(11.dp)) {
                                    if (startsNewGroup) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                if (isMine) "أنت" else senderName,
                                                color = if (isMine) contactAccent else contactAccent.copy(alpha = 0.9f),
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
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Text(message.message, color = contactPrimaryText, fontFamily = CairoFont, fontSize = 13.sp, lineHeight = 19.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                formatContactTime(message),
                                                color = contactSecondaryText,
                                                fontFamily = CairoFont,
                                                fontSize = 9.sp
                                            )
                                        }
                                        if (statusLabel.isNotBlank()) {
                                            Text(
                                                " • $statusLabel",
                                                color = if (statusLabel.contains("بانتظار")) Color(0xFFFBBF24) else contactSecondaryText,
                                                fontFamily = CairoFont,
                                                fontSize = 9.sp
                                            )
                                        }
                                        if (isDeveloper && message.updatedAt != null) {
                                            Text(" • معدّلة", color = contactAccent, fontFamily = CairoFont, fontSize = 9.sp)
                                        }
                                        if (isDeveloper) {
                                            Box {
                                                IconButton(
                                                    onClick = { messageMenuId = message.id },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات الرسالة", tint = contactSecondaryText, modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = messageMenuId == message.id,
                                                    onDismissRequest = { messageMenuId = null },
                                                    containerColor = contactCard
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("تعديل الرسالة", color = contactPrimaryText, fontFamily = CairoFont) },
                                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = contactAccent) },
                                                        onClick = {
                                                            messageToEdit = message
                                                            editDraft = message.message
                                                            messageMenuId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("حذف الرسالة", color = Color(0xFFFF8A8A), fontFamily = CairoFont) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF8A8A)) },
                                                        onClick = {
                                                            messageToDelete = message
                                                            messageMenuId = null
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                                if (endsGroup) {
                                    ContactAvatar(senderName, senderAvatar, contactAccent, size = 34.dp)
                                } else {
                                    Spacer(Modifier.size(34.dp))
                                }
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
                        focusedBorderColor = contactAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = contactAccent
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
                    colors = ButtonDefaults.buttonColors(containerColor = contactAccent)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = if (isDeveloper) DeveloperInboxBackground else DeepSlate
                    )
                }
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(contactCategories, key = { it.key }) { category ->
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false; pendingMessage = "" }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    messageToEdit?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isChatActionInProgress) messageToEdit = null },
            containerColor = contactCard,
            title = { Text("تعديل الرسالة", color = contactPrimaryText, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it.take(10000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    label = { Text("نص الرسالة", fontFamily = CairoFont) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = contactAccent,
                        unfocusedBorderColor = contactBorder,
                        focusedTextColor = contactPrimaryText,
                        unfocusedTextColor = contactPrimaryText,
                        cursorColor = contactAccent
                    )
                )
            },
            confirmButton = {
                TextButton(enabled = editDraft.isNotBlank() && !isChatActionInProgress, onClick = {
                    isChatActionInProgress = true
                    scope.launch {
                        val updated = DevChatRepository.editMessage(context, target.userEmail.ifBlank { selectedThreadEmail }, target.id, editDraft)
                        if (updated != null) {
                            threads = DevChatRepository.getAllThreads(context)
                            messages = threads[selectedThreadEmail].orEmpty()
                            Toast.makeText(context, "تم تعديل الرسالة", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "تعذر تعديل الرسالة؛ لم يتغير سجلها السحابي", Toast.LENGTH_LONG).show()
                        isChatActionInProgress = false
                        messageToEdit = null
                    }
                }) {
                    Text(if (isChatActionInProgress) "جارٍ الحفظ…" else "حفظ التعديل", color = contactAccent, fontFamily = CairoFont)
                }
            },
            dismissButton = { TextButton(enabled = !isChatActionInProgress, onClick = { messageToEdit = null }) { Text("إلغاء", color = contactSecondaryText, fontFamily = CairoFont) } }
        )
    }

    messageToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isChatActionInProgress) messageToDelete = null },
            containerColor = contactCard,
            title = { Text("حذف الرسالة؟", color = contactPrimaryText, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف هذه الرسالة من المحادثة السحابية.\n\n${target.message.take(220)}", color = contactSecondaryText, fontFamily = CairoFont) },
            confirmButton = {
                TextButton(enabled = !isChatActionInProgress, onClick = {
                    isChatActionInProgress = true
                    scope.launch {
                        val deleted = DevChatRepository.deleteMessage(context, target.userEmail, target.id)
                        if (deleted) {
                            threads = DevChatRepository.getAllThreads(context)
                            messages = threads[selectedThreadEmail].orEmpty()
                            Toast.makeText(context, "تم حذف الرسالة", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "تعذر حذف الرسالة؛ لم يتغير سجلها السحابي", Toast.LENGTH_LONG).show()
                        isChatActionInProgress = false
                        messageToDelete = null
                    }
                }) {
                    Text(if (isChatActionInProgress) "جارٍ الحذف…" else "حذف", color = Color(0xFFFF8A8A), fontFamily = CairoFont)
                }
            },
            dismissButton = { TextButton(enabled = !isChatActionInProgress, onClick = { messageToDelete = null }) { Text("إلغاء", color = contactSecondaryText, fontFamily = CairoFont) } }
        )
    }

    conversationToDelete?.let { (email, userId) ->
        AlertDialog(
            onDismissRequest = { if (!isChatActionInProgress) conversationToDelete = null },
            containerColor = contactCard,
            title = { Text("حذف المحادثة؟", color = contactPrimaryText, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف جميع رسائل هذه المحادثة من Supabase نهائيًا، ولا يمكن التراجع عن ذلك.", color = contactSecondaryText, fontFamily = CairoFont) },
            confirmButton = {
                TextButton(enabled = !isChatActionInProgress, onClick = {
                    isChatActionInProgress = true
                    scope.launch {
                        val deleted = DevChatRepository.deleteConversation(context, email, userId)
                        if (deleted) {
                            threads = DevChatRepository.getAllThreads(context)
                            messages = emptyList()
                            Toast.makeText(context, "تم حذف المحادثة", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "تعذر حذف المحادثة؛ لم يتغير سجلها", Toast.LENGTH_LONG).show()
                        isChatActionInProgress = false
                        conversationToDelete = null
                    }
                }) {
                    Text(if (isChatActionInProgress) "جارٍ الحذف…" else "حذف المحادثة", color = Color(0xFFFF8A8A), fontFamily = CairoFont)
                }
            },
            dismissButton = { TextButton(enabled = !isChatActionInProgress, onClick = { conversationToDelete = null }) { Text("إلغاء", color = contactSecondaryText, fontFamily = CairoFont) } }
        )
    }

    profileToOpen?.let { target ->
        ModalBottomSheet(onDismissRequest = { profileToOpen = null }, containerColor = contactCard, contentColor = contactPrimaryText) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val targetName = publicProfile?.fullName?.takeIf { it.isNotBlank() } ?: target.displayName
                ContactAvatar(targetName, publicProfile?.avatarUrl ?: target.avatarUrl, contactAccent, size = 76.dp)
                Text(targetName, color = contactPrimaryText, fontFamily = CairoFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                publicProfileHandle?.takeIf { it.isNotBlank() }?.let {
                    Text("@${it.removePrefix("@")}", color = contactAccent, fontFamily = CairoFont, fontSize = 12.sp)
                }
                when {
                    isPublicProfileLoading -> CircularProgressIndicator(color = contactAccent, modifier = Modifier.size(28.dp))
                    publicProfile == null -> Text("لا تتوفر بيانات ملف عام إضافية لهذا الحساب حاليًا.", color = contactSecondaryText, fontFamily = CairoFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                    else -> Surface(color = contactBackground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ملف المستخدم في قبس", color = contactAccent, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("المقاطع المنشورة: ${publicProfileReelCount ?: 0}", color = contactPrimaryText, fontFamily = CairoFont, fontSize = 12.sp)
                            Text("تُعرض هنا المعلومات العامة المتاحة فقط.", color = contactSecondaryText, fontFamily = CairoFont, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
