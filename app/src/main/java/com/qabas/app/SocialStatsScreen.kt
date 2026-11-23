package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialStatsScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(SocialAccountManager.getAccounts(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    // حوار إدخال المعرّف الحقيقي — كان زر «ربط الآن» يكتب معرّفاً
    // مخترعاً «@qabas_user» ويُظهر «تم ربط الحساب بنجاح!» بلا أي ربط.
    var editAccount by remember { mutableStateOf<SocialPlatformAccount?>(null) }
    var editHandleText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DeepSlate,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Translator.tr("إحصائيات الانتشار"),
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = ThemeSuccess,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "جديد",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة", tint = PresetPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            isSyncing = true
                            accounts = SocialAccountManager.syncAnalytics(context)
                            kotlinx.coroutines.delay(1000)
                            isSyncing = false
                        }
                    }) {
                        Icon(
                            Icons.Default.Sync, 
                            contentDescription = "مزامنة الإحصائيات", 
                            tint = PresetPrimary,
                            modifier = if (isSyncing) Modifier.clip(CircleShape) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OverallStatsHeader(accounts)
            }

            item {
                Text(
                    text = Translator.tr("حساباتك المتصلة"),
                    color = TextPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(accounts) { account ->
                AccountStatCard(
                    account = account,
                    onAccountsChanged = { accounts = SocialAccountManager.getAccounts(context) },
                    onEditHandle = { editAccount = it; editHandleText = "" }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                ViralPotentialLab()
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                AiGrowthSuggestions()
            }
        }
    }

    editAccount?.let { account ->
        SocialHandleEditDialog(
            account = account,
            handleText = editHandleText,
            onHandleChange = { editHandleText = it },
            onSave = {
                val handle = editHandleText.trim()
                if (handle.isBlank()) {
                    android.widget.Toast.makeText(context, "أدخل معرّف حسابك على ${account.name} أولاً", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    SocialAccountManager.toggleConnection(context, account.id, isConnected = true, handle = handle)
                    accounts = SocialAccountManager.getAccounts(context)
                    editAccount = null
                    android.widget.Toast.makeText(context, "تم حفظ الحساب بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { editAccount = null }
        )
    }
}

@Composable
fun OverallStatsHeader(accounts: List<SocialPlatformAccount>) {
    val totalViews = accounts.sumOf { it.totalViews }
    val totalFollowers = accounts.sumOf { it.followers }
    
    Surface(
        color = ThemeSecondarySurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Translator.tr("إجمالي الوصول الدعوي"),
                color = TextPrimary.copy(alpha = 0.6f),
                fontFamily = CairoFont,
                fontSize = 12.sp
            )
            
            Text(
                text = formatNumber(totalViews.toLong()),
                color = PresetPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp
            )
            
            Text(
                text = Translator.tr("مشاهدة تراكمية"),
                color = PresetPrimary.copy(alpha = 0.8f),
                fontFamily = CairoFont,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMiniBox(Translator.tr("المتابعون"), formatNumber(totalFollowers.toLong()), Icons.Default.People)
                Divider(modifier = Modifier.width(1.dp).height(40.dp), color = TextPrimary.copy(alpha = 0.1f))
                StatMiniBox(Translator.tr("الفيديوهات"), accounts.sumOf { it.publishedCount }.toString(), Icons.Default.VideoLibrary)
            }
        }
    }
}

@Composable
fun StatMiniBox(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = TextPrimary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = NotoSansFont)
        Text(label, color = TextPrimary.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = CairoFont)
    }
}

@Composable
fun AccountStatCard(
    account: SocialPlatformAccount,
    onAccountsChanged: () -> Unit = {},
    onEditHandle: (SocialPlatformAccount) -> Unit = {}
) {
    val context = LocalContext.current
    val platformColor = when (account.id) {
        "youtube" -> Color(0xFFFF0000)
        "tiktok" -> Color(0xFF000000)
        "instagram" -> Color(0xFFE4405F)
        "facebook" -> Color(0xFF1877F2)
        "twitter" -> Color(0xFF1DA1F2)
        else -> PresetPrimary
    }

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        border = if (account.isConnected) null else androidx.compose.foundation.BorderStroke(1.dp, TextPrimary.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(platformColor.copy(alpha = 0.1f))
                    .border(1.dp, platformColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(account.id) {
                        "youtube" -> Icons.Default.PlayCircle
                        "tiktok" -> Icons.Default.MusicNote
                        "instagram" -> Icons.Default.CameraAlt
                        "facebook" -> Icons.Default.Facebook
                        else -> Icons.Default.AccountCircle
                    },
                    contentDescription = null,
                    tint = platformColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = CairoFont)
                Text(account.handle, color = TextPrimary.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = NotoSansFont)
            }
            
            if (account.isConnected) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatNumber(account.totalViews.toLong()) + " " + Translator.tr("مشاهدة"),
                        color = PresetPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = NotoSansFont
                    )
                    TextButton(
                        onClick = {
                            SocialAccountManager.toggleConnection(context, account.id, false)
                            onAccountsChanged()
                            android.widget.Toast.makeText(context, Translator.tr("تم قطع الاتصال بـ") + " " + account.name, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(Translator.tr("قطع الاتصال"), color = ThemeDestructive, fontSize = 10.sp, fontFamily = CairoFont)
                    }
                }
            } else {
                Button(
                    onClick = { onEditHandle(account) },
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary.copy(alpha = 0.1f), contentColor = PresetPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(Translator.tr("ربط الآن"), fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ViralPotentialLab() {
    var scriptText by remember { mutableStateOf("") }
    var score by remember { mutableStateOf<ViralScore?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        color = ThemeSecondarySurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Translator.tr("مختبر الانتشار (Viral Lab) 🧪"),
                    color = TextPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = Translator.tr("أدخل فكرتك أو السيناريو الخاص بك لنتوقع مدى نجاحه بناءً على خوارزميات المنصات:"),
                color = TextPrimary.copy(alpha = 0.6f),
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = scriptText,
                onValueChange = { scriptText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("اكتب فكرتك هنا...", color = TextTertiary, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PresetPrimary,
                    unfocusedBorderColor = TextPrimary.copy(alpha = 0.1f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (scriptText.isBlank()) return@Button
                    scope.launch {
                        isAnalyzing = true
                        kotlinx.coroutines.delay(1500)
                        score = SmartFeaturesEngine.predictViralPotential(scriptText)
                        isAnalyzing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                enabled = !isAnalyzing && scriptText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                } else {
                    Text("توقع الأداء 📈", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                }
            }

            score?.let { s ->
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = TextPrimary.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("قوة الخطاف (Hook)", color = TextTertiary, fontSize = 10.sp, fontFamily = CairoFont)
                        Text(s.hookGrade, color = PresetPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = NotoSansFont)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الانتشار المتوقع", color = TextTertiary, fontSize = 10.sp, fontFamily = CairoFont)
                        Text("${s.overallScore}%", color = ThemeSuccess, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, fontFamily = NotoSansFont)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("نصائح التحسين الذكية:", color = PresetPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = CairoFont)
                s.improvementTips.forEach { tip ->
                    Text("• $tip", color = TextPrimary.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = CairoFont, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun AiGrowthSuggestions() {
    val suggestions = listOf(
        "نشر مقاطع «تدبر آية» في وقت الفجر يزيد التفاعل بنسبة 40% على تيك توك.",
        "استخدم خط «القاهرة» باللون الذهبي في فيديوهات يوتيوب القصيرة لزيادة وقت المشاهدة.",
        "جمهورك يفضل المواضيع المتعلقة بـ «الراحة النفسية» والقصص المؤثرة هذا الأسبوع."
    )
    
    Surface(
        color = PresetPrimary.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Translator.tr("اقتراحات خادم المسلم الذكية للنمو"),
                    color = PresetPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = PresetPrimary, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = Translator.tr(suggestion),
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}
