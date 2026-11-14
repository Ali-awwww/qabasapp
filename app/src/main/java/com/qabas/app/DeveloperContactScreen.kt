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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class ContactCategory(val key: String, val label: String)

private val contactCategories = listOf(
    ContactCategory("general", "رسالة عامة"),
    ContactCategory("feature", "اقتراح فكرة"),
    ContactCategory("bug", "مشكلة تقنية"),
    ContactCategory("content_report", "بلاغ محتوى"),
    ContactCategory("prayer_issue", "الصلاة والأذان"),
    ContactCategory("account_issue", "مشكلة الحساب")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperContactScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val authEmail by SupabaseServices.Auth.currentUser.collectAsState()
    val userEmail = authEmail?.email?.takeIf { it.isNotBlank() }
        ?: prefs.getString("user_email", "").orEmpty()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf<List<DevChatRepository.DevChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(contactCategories.first()) }
    var isSending by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(userEmail) {
        if (userEmail.isBlank()) return@LaunchedEffect
        messages = DevChatRepository.getLocalMessages(context, userEmail)
        messages = DevChatRepository.syncMessages(context, userEmail)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            SmallTopAppBar(
                title = {
                    Column {
                        Text("التواصل مع المطور", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        Text("رسالتك تصل إلى لوحة المطور", fontFamily = CairoFont, color = TextSecondary, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.smallTopAppBarColors(containerColor = DeepSlate)
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
                        "اختر تصنيف الرسالة ثم اكتب ملاحظتك. ستظهر رسالتك في لوحة المطور للمراجعة والرد.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(contactCategories) { category ->
                    Surface(
                        onClick = { selectedCategory = category },
                        color = if (selectedCategory.key == category.key) GoldPrimary else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            category.label,
                            color = if (selectedCategory.key == category.key) DeepSlate else Color.White.copy(alpha = 0.85f),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = if (selectedCategory.key == category.key) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

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
                    items(messages, key = { it.id }) { message ->
                        val isUser = message.sender.equals("user", ignoreCase = true)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End) {
                            Surface(
                                color = if (isUser) Color(0xFF1C3B45) else Color(0xFF263449),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(0.88f)
                            ) {
                                Column(modifier = Modifier.padding(11.dp)) {
                                    Text(
                                        if (isUser) "أنت • ${message.category}" else "المطور",
                                        color = if (isUser) GoldPrimary else Color(0xFF7DD3FC),
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(message.message, color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, lineHeight = 19.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(message.formattedTime, color = TextSecondary, fontFamily = CairoFont, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

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
                        if (text.isBlank() || isSending || userEmail.isBlank()) return@Button
                        isSending = true
                        scope.launch {
                            runCatching {
                                DevChatRepository.sendMessage(
                                    context = context,
                                    userEmail = userEmail,
                                    text = text,
                                    sender = "user",
                                    category = selectedCategory.key,
                                    isUrgent = selectedCategory.key == "content_report"
                                )
                            }.onSuccess {
                                input = ""
                                messages = DevChatRepository.getLocalMessages(context, userEmail)
                                Toast.makeText(context, "تم إرسال رسالتك إلى المطور", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "تم حفظ الرسالة محليًا، وسنحاول مزامنتها عند عودة الاتصال", Toast.LENGTH_LONG).show()
                            }
                            isSending = false
                        }
                    },
                    enabled = input.isNotBlank() && !isSending && userEmail.isNotBlank(),
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
}
