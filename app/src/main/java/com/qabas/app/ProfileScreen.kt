package com.qabas.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*

// Tournament Rank Tier Badge Model
data class TournamentTierBadge(
    val id: String,
    val title: String,
    val tierTitle: String,
    val emoji: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val accentColor: Color,
    val pointsRequired: Int,
    val rankRequired: Int? = null,
    val isUnlocked: Boolean,
    val isCurrentTier: Boolean,
    val description: String,
    val perk: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigate: (AppState) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    var userEmail by remember {
        mutableStateOf(prefs.getString("user_email", "user@example.com") ?: "user@example.com")
    }

    // User Profile State with real persistence
    var displayName by remember { 
        val savedName = prefs.getString("user_name", "")?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_display_name", "")?.takeIf { it.isNotBlank() }
            ?: userEmail.substringBefore('@').takeIf { it != "user" && it != "guest" }
            ?: "صانع المحتوى الدعوي"
        mutableStateOf(savedName)
    }
    var userBio by remember {
        mutableStateOf(prefs.getString("user_bio", "") ?: "نشر الخير والقرآن الكريم عبر الذكاء الاصطناعي ✦")
    }
    var userCustomTag by remember {
        val cid = UserIdentifierManager.getCustomId(context)
        mutableStateOf(if (cid.isNotBlank()) UserIdentifierManager.formatDisplay(cid) else "@qabas_user")
    }
    var userAccountNumber by remember {
        val acc = UserIdentifierManager.getAccountNumber(context, userEmail).takeIf { it.isNotBlank() }
            ?: prefs.getString("user_account_number", "") ?: ""
        mutableStateOf(acc)
    }
    var avatarUriString by remember {
        mutableStateOf(prefs.getString("user_avatar_uri", "") ?: "")
    }
    var selectedPresetAvatar by remember {
        mutableIntStateOf(prefs.getInt("user_avatar_preset", 0))
    }
    
    val projectService = remember { ProjectService(context) }
    var projectsCount by remember { mutableIntStateOf(0) }

    // Social Accounts & Analytics State
    var socialAccounts by remember { mutableStateOf(SocialAccountManager.getAccounts(context)) }

    // Dialog States
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    var selectedEditAccount by remember { mutableStateOf<SocialPlatformAccount?>(null) }
    var editHandleText by remember { mutableStateOf("") }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var showPublishDialogInProfile by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // ربط الملف بالهوية الحقيقية: إن كانت القيم المحفوظة وهمية/فارغة، اسحبها
    // من جلسة الدخول الفعلية (Firebase ثم Supabase) بدل عرض بيانات كاذبة
    LaunchedEffect(Unit) {
        val emailLooksFake = userEmail.isBlank() ||
            userEmail.equals("user@example.com", ignoreCase = true) ||
            userEmail.equals("guest@qabas.studio", ignoreCase = true)
        val nameSnapshot = prefs.getString("user_name", "")?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_display_name", "")?.takeIf { it.isNotBlank() }
        val nameLooksFake = nameSnapshot.isNullOrBlank() ||
            nameSnapshot == "صانع المحتوى الدعوي" || nameSnapshot == "صانع محتوى قبس"
        if (!emailLooksFake && !nameLooksFake && avatarUriString.isNotBlank() &&
            prefs.getString("user_email", null) != null &&
            nameSnapshot != null
        ) return@LaunchedEffect

        val fb = runCatching { FirebaseManager.getAuth().currentUser }.getOrNull()
        val sbEmail = SupabaseServices.Auth.getCurrentUserEmail()
        val sbName = SupabaseServices.Auth.getCurrentUserMeta("full_name")
            ?: SupabaseServices.Auth.getCurrentUserMeta("name")
        val sbAvatar = SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
            ?: SupabaseServices.Auth.getCurrentUserMeta("picture")

        val realEmail = fb?.email?.takeIf { it.isNotBlank() } ?: sbEmail?.takeIf { it.isNotBlank() }
        val realName = fb?.displayName?.takeIf { it.isNotBlank() } ?: sbName
        val realAvatar = fb?.photoUrl?.toString()?.takeIf { it.isNotBlank() } ?: sbAvatar

        if (emailLooksFake && realEmail != null) {
            userEmail = realEmail
            prefs.edit().putString("user_email", realEmail).apply()
        }
        if (nameLooksFake && realName != null) {
            displayName = realName
            prefs.edit().putString("user_display_name", realName).putString("user_name", realName).apply()
        }
        if (avatarUriString.isBlank() && realAvatar != null) {
            avatarUriString = realAvatar
            prefs.edit().putString("user_avatar_uri", realAvatar).apply()
        }

        // مزامنة وسحب رقم الحساب الحقيقي من السحابة لضمان الدقة وتحديث العداد
        val targetEmail = realEmail ?: userEmail
        if (targetEmail.isNotBlank() && !targetEmail.contains("guest")) {
            val cloudAcc = UserIdentifierManager.ensureCloudAccountNumber(context, targetEmail, displayName)
            if (cloudAcc.isNotBlank()) {
                userAccountNumber = cloudAcc
                prefs.edit().putString("user_account_number", cloudAcc).apply()
            }
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarUriString = uri.toString()
            selectedPresetAvatar = -1
            prefs.edit().putString("user_avatar_uri", uri.toString()).putInt("user_avatar_preset", -1).apply()
            Toast.makeText(context, "تم تحديث الصورة الشخصية بنجاح 📸", Toast.LENGTH_SHORT).show()
        }
    }

    val totalPublished = socialAccounts.filter { it.isConnected }.sumOf { it.publishedCount }

    var userLeaguePoints by remember { mutableIntStateOf(prefs.getInt("user_league_points", 0)) }
    var userLeagueRank by remember { mutableIntStateOf(prefs.getInt("user_league_rank", 0)) }
    var isLoadingLeague by remember { mutableStateOf(true) }
    var showRankMilestoneCelebration by remember { mutableStateOf(false) }
    var showTournamentInfoSheet by remember { mutableStateOf(false) }

    val currentTierIndex = remember(userLeaguePoints) {
        when {
            userLeaguePoints < 500 -> 0
            userLeaguePoints < 1500 -> 1
            userLeaguePoints < 3500 -> 2
            userLeaguePoints < 7000 -> 3
            userLeaguePoints < 12000 -> 4
            else -> 5
        }
    }

    val currentTierTitleOnly = remember(userLeaguePoints) {
        when {
            userLeaguePoints < 500 -> "صانع برونزي"
            userLeaguePoints < 1500 -> "صانع فضي"
            userLeaguePoints < 3500 -> "صانع ذهبي"
            userLeaguePoints < 7000 -> "صانع ماسي"
            userLeaguePoints < 12000 -> "سفير الدعوة"
            else -> "أسطورة قبس"
        }
    }

    val currentTierEmoji = remember(userLeaguePoints) {
        when {
            userLeaguePoints < 500 -> "🥉"
            userLeaguePoints < 1500 -> "🥈"
            userLeaguePoints < 3500 -> "🥇"
            userLeaguePoints < 7000 -> "💎"
            userLeaguePoints < 12000 -> "👑"
            else -> "🌟"
        }
    }

    LaunchedEffect(currentTierIndex) {
        val lastCelebrated = prefs.getInt("last_celebrated_tournament_tier", -1)
        if (lastCelebrated != -1 && currentTierIndex > lastCelebrated) {
            showRankMilestoneCelebration = true
            prefs.edit().putInt("last_celebrated_tournament_tier", currentTierIndex).apply()
        } else if (lastCelebrated == -1) {
            prefs.edit().putInt("last_celebrated_tournament_tier", currentTierIndex).apply()
        }
    }

    LaunchedEffect(Unit) {
        projectsCount = projectService.getAllProjects().size
        socialAccounts = SocialAccountManager.getAccounts(context)
        
        // Fetch League stats from Cloud / Firebase / Supabase
        try {
            isLoadingLeague = true
            val leaderboard = CloudServices.Database.getLeaderboard()
            val currentUserId = CloudServices.Auth.getCurrentUserId()
            
            if (leaderboard.isNotEmpty()) {
                val myEntry = if (currentUserId != null) {
                    leaderboard.find { it.isCurrentUser } ?: leaderboard.find { it.name.contains(displayName, ignoreCase = true) }
                } else null
                
                if (myEntry != null) {
                    userLeaguePoints = myEntry.points
                    val calculatedRank = leaderboard.indexOf(myEntry) + 1
                    userLeagueRank = if (myEntry.rank > 0) myEntry.rank else calculatedRank
                } else {
                    // Default / Initial points based on projects & published
                    val currentPublished = socialAccounts.filter { it.isConnected }.sumOf { it.publishedCount }
                    val fallbackPoints = (projectsCount * 50) + (currentPublished * 20)
                    userLeaguePoints = if (userLeaguePoints > 0) userLeaguePoints else fallbackPoints
                    userLeagueRank = if (userLeagueRank > 0) userLeagueRank else (leaderboard.size + 1).coerceAtMost(99)
                }
            } else {
                // Local fallback
                val currentPublished = socialAccounts.filter { it.isConnected }.sumOf { it.publishedCount }
                val fallbackPoints = (projectsCount * 50) + (currentPublished * 20)
                if (userLeaguePoints == 0) userLeaguePoints = fallbackPoints
                if (userLeagueRank == 0) userLeagueRank = 1
            }
            prefs.edit()
                .putInt("user_league_points", userLeaguePoints)
                .putInt("user_league_rank", userLeagueRank)
                .apply()

            // مزامنة الملف الشخصي تلقائياً مع Firebase Firestore
            if (FirebaseManager.isConfigured(context)) {
                val uid = FirebaseManager.getAuth().currentUser?.uid
                    ?: prefs.getString("user_id", null)
                    ?: (userEmail.ifBlank { "local_user" }.hashCode().toString())
                FirebaseManager.syncUserToFirestore(
                    context = context,
                    userId = uid,
                    displayName = displayName,
                    email = userEmail
                )
            }
        } catch (e: Exception) {
            // keep cached
        } finally {
            isLoadingLeague = false
        }
    }

    val currentTierName = remember(userLeaguePoints) {
        when {
            userLeaguePoints < 500 -> "صانع برونزي 🥉"
            userLeaguePoints < 1500 -> "صانع فضي 🥈"
            userLeaguePoints < 3500 -> "صانع ذهبي 🥇"
            userLeaguePoints < 7000 -> "صانع ماسي 💎"
            userLeaguePoints < 12000 -> "سفير الدعوة 👑"
            else -> "أسطورة قبس 🌟"
        }
    }

    val tournamentBadges = remember(userLeaguePoints, userLeagueRank) {
        listOf(
            TournamentTierBadge(
                id = "tier_bronze",
                title = "وسام الصاعد الواعد",
                tierTitle = "الرتبة البرونزية 🥉",
                emoji = "🥉",
                icon = Icons.Default.Shield,
                primaryColor = Color(0xFFCD7F32),
                accentColor = Color(0xFFE5A65D),
                pointsRequired = 0,
                isUnlocked = true,
                isCurrentTier = userLeaguePoints < 500,
                description = "يُمنح لكل صانع محتوى يبدأ رحلته في دوري قبس الدعوي بنشر أولى بصماته المباركة.",
                perk = "مضاعف نقاط 1.0x والدخول في السجل الأسبوعي."
            ),
            TournamentTierBadge(
                id = "tier_silver",
                title = "وسام النجم الفضي",
                tierTitle = "الرتبة الفضية 🥈",
                emoji = "🥈",
                icon = Icons.Default.MilitaryTech,
                primaryColor = Color(0xFF94A3B8),
                accentColor = Color(0xFFE2E8F0),
                pointsRequired = 500,
                isUnlocked = userLeaguePoints >= 500,
                isCurrentTier = userLeaguePoints in 500..1499,
                description = "يُمنح للصناع الذين اجتازوا 500 نقطة وأثبتوا حضورهم المستمر بجودة عالية.",
                perk = "أولوية معالجة الفيديوهات في السحابة ومضاعف نقاط 1.2x."
            ),
            TournamentTierBadge(
                id = "tier_gold",
                title = "وسام الصانع الذهبي",
                tierTitle = "الرتبة الذهبية 🥇",
                emoji = "🥇",
                icon = Icons.Default.EmojiEvents,
                primaryColor = GoldPrimary,
                accentColor = GoldSecondary,
                pointsRequired = 1500,
                isUnlocked = userLeaguePoints >= 1500,
                isCurrentTier = userLeaguePoints in 1500..3499,
                description = "وسام نخبوي للصناع المتميزين الذين حققوا تأثيراً مشهوداً عبر أكثر من 1,500 نقطة.",
                perk = "إطار ذهبي متوهج للصورة الشخصية ومضاعف نقاط 1.5x."
            ),
            TournamentTierBadge(
                id = "tier_diamond",
                title = "وسام الفارس الماسي",
                tierTitle = "الرتبة الماسية 💎",
                emoji = "💎",
                icon = Icons.Default.Diamond,
                primaryColor = Color(0xFF00E5FF),
                accentColor = Color(0xFF80D8FF),
                pointsRequired = 3500,
                isUnlocked = userLeaguePoints >= 3500,
                isCurrentTier = userLeaguePoints in 3500..6999,
                description = "يُمنح لكبار صناع المحتوى المؤثرين الذين بلغوا 3,500 نقطة في ميادين الدعوة.",
                perk = "توليد مشاهد فورية غير محدودة وإبراز المشاريع في صدارة المنصات."
            ),
            TournamentTierBadge(
                id = "tier_master",
                title = "وسام سفير الدعوة",
                tierTitle = "رتبة السفير 👑",
                emoji = "👑",
                icon = Icons.Default.Stars,
                primaryColor = Color(0xFFA855F7),
                accentColor = Color(0xFFC084FC),
                pointsRequired = 7000,
                isUnlocked = userLeaguePoints >= 7000,
                isCurrentTier = userLeaguePoints in 7000..11999,
                description = "أعلى أوسمة التقدير لصناع الأثر الذين حققوا أكثر من 7,000 نقطة بطولة.",
                perk = "شارة سفير موثقة ذهبية بجانب الاسم والوصول إلى أحدث نماذج الذكاء الاصطناعي التجريبية."
            ),
            TournamentTierBadge(
                id = "tier_legend",
                title = "وسام أسطورة قبس",
                tierTitle = "الرتبة الأسطورية 🌟",
                emoji = "🌟",
                icon = Icons.Default.AutoAwesome,
                primaryColor = Color(0xFFF59E0B),
                accentColor = Color(0xFFFDE68A),
                pointsRequired = 12000,
                isUnlocked = userLeaguePoints >= 12000,
                isCurrentTier = userLeaguePoints >= 12000,
                description = "تاج البطولة التنافسية الكبرى لمن تجاوزوا 12,000 نقطة من العطاء الاستثنائي.",
                perk = "تخليد الاسم في لوحة الشرف الخالدة مع إتاحة شارة المطورين الفخرية."
            ),
            TournamentTierBadge(
                id = "rank_top10",
                title = "وسام فرسان الصدارة",
                tierTitle = "نخبة الـ 10 الكبار 🏆",
                emoji = "⚡",
                icon = Icons.Default.Leaderboard,
                primaryColor = Color(0xFF38BDF8),
                accentColor = Color(0xFFBAE6FD),
                pointsRequired = 0,
                rankRequired = 10,
                isUnlocked = userLeagueRank in 1..10,
                isCurrentTier = userLeagueRank in 4..10,
                description = "يُمنح لمن يدخل قائمة أفضل 10 صناع محتوى في الدوري الأسبوعي المباشر.",
                perk = "شارة مضيئة تظهر في مجتمع قبس العام."
            ),
            TournamentTierBadge(
                id = "rank_top3",
                title = "وسام منصة التتويج",
                tierTitle = "فرسان التتويج الثلاثة 🥉🥈🥇",
                emoji = "🔥",
                icon = Icons.Default.WorkspacePremium,
                primaryColor = Color(0xFFEC4899),
                accentColor = Color(0xFFF472B6),
                pointsRequired = 0,
                rankRequired = 3,
                isUnlocked = userLeagueRank in 1..3,
                isCurrentTier = userLeagueRank in 2..3,
                description = "يُمنح للمراكز الثلاثة الأولى على مستوى جميع المشتركين في البطولة الأسبوعية.",
                perk = "تكريم خاص وإعلان التتويج الأسبوعي."
            ),
            TournamentTierBadge(
                id = "rank_champion",
                title = "وسام بطل الدوري الأسبوعي",
                tierTitle = "بطل البطولة الأول 🏆👑",
                emoji = "👑",
                icon = Icons.Default.WorkspacePremium,
                primaryColor = GoldPrimary,
                accentColor = Color(0xFFFFFBEB),
                pointsRequired = 0,
                rankRequired = 1,
                isUnlocked = userLeagueRank == 1,
                isCurrentTier = userLeagueRank == 1,
                description = "المركز الأول المطلق في الدوري وصاحب أعلى بصمة أثرية في البطولة.",
                perk = "كأس البطولة الأسبوعي ولقب بطل الموسم."
            )
        )
    }

    var selectedBadgeForDetail by remember { mutableStateOf<TournamentTierBadge?>(null) }

    val badges = remember(projectsCount, totalPublished) {
        val list = mutableListOf<String>()
        if (projectsCount >= 1) list.add(Translator.tr("البداية القوية 🚀"))
        if (projectsCount >= 3) list.add(Translator.tr("صانع محتوى مبدع ✨"))
        if (totalPublished >= 5) list.add(Translator.tr("ناشر نشط 📤"))
        if (totalPublished >= 20) list.add(Translator.tr("مؤثر قدير 🌟"))
        if (list.isEmpty()) list.add(Translator.tr("صانع مبتدئ 🌿"))
        list
    }

    val goldGradient = Brush.horizontalGradient(colors = listOf(GoldSecondary, GoldPrimary))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        Translator.tr("الملف الشخصي والمنصات"), 
                        fontFamily = TajawalFont, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = qabasTextPrimary(), 
                        fontSize = 19.sp 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = qabasPrimary())
                    }
                },
                actions = {
                    com.qabas.app.ui.components.CloudSyncIndicator(
                        isSyncing = isLoadingLeague,
                        compact = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = { showEditProfileDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = qabasPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        containerColor = qabasBackground()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Card Header (Full Social Media Profile Experience)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 20.dp, borderAlpha = 0.35f, glowElevation = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar with Edit Button Overlay
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .bouncingClickable { showAvatarPickerSheet = true },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            color = qabasBackground(),
                            border = androidx.compose.foundation.BorderStroke(3.dp, goldGradient),
                            shadowElevation = 10.dp
                        ) {
                            if (avatarUriString.isNotBlank()) {
                                AsyncImage(
                                    model = avatarUriString,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(goldGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (selectedPresetAvatar) {
                                            1 -> Icons.Default.AutoAwesome
                                            2 -> Icons.Default.MovieFilter
                                            3 -> Icons.Default.MenuBook
                                            4 -> Icons.Default.Psychology
                                            else -> Icons.Default.Person
                                        },
                                        contentDescription = null,
                                        tint = DeepSlate,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }
                        }

                        // Camera Badge to indicate editable photo
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = qabasPrimary(),
                            border = androidx.compose.foundation.BorderStroke(2.dp, qabasBackground())
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Name & Custom Tag
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row with Username and Account Number side-by-side
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        // 1. User Tag Chip
                        Surface(
                            color = qabasPrimary().copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, qabasPrimary().copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    UserIdentifierManager.copyToClipboard(context, userCustomTag)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = userCustomTag,
                                    color = qabasPrimary(),
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "نسخ المعرّف",
                                    tint = qabasPrimary().copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // 2. Mandatory Account Number Chip (Always visible & Cloud Verified - Non-Editable)
                        val effectiveAccountNumber = userAccountNumber.takeIf { it.isNotBlank() }
                            ?: UserIdentifierManager.getAccountNumber(context, userEmail).takeIf { it.isNotBlank() }
                            ?: UserIdentifierManager.peekNextAccountNumber(context)

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = GoldPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    UserIdentifierManager.copyAccountNumberToClipboard(context, effectiveAccountNumber)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "رقم حساب ثابت موثق",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = UserIdentifierManager.formatAccountNumber(effectiveAccountNumber),
                                    color = GoldPrimary,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "نسخ رقم الحساب",
                                    tint = GoldPrimary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                                if (UserIdentifierManager.isFirstAccount(effectiveAccountNumber)) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "👑", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 3. Verified Maker Badge
                        Surface(
                            color = qabasPrimary().copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, qabasPrimary().copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "صانع موثق ✓",
                                color = qabasPrimary(),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bio / Description
                    Text(
                        text = userBio,
                        color = qabasTextSecondary(),
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))

            // بطاقة المزامنة السحابية الهجينة (Firebase + Supabase + Local)
            val isFirebaseActive = remember { FirebaseManager.isConfigured(context) }
            val isSupabaseActive = remember { SupabaseServices.isSupabaseAvailable }
            Surface(
                color = Color(0xFF151B2B),
                shape = CutCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isFirebaseActive) Color(0xFFFFA000).copy(alpha = 0.4f) else GoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isFirebaseActive) Color(0xFF78350F).copy(alpha = 0.3f) else Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isFirebaseActive) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = if (isFirebaseActive) Color(0xFFFFA000) else GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "المزامنة السحابية المزدوجة",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                if (isFirebaseActive && isSupabaseActive) "Firebase 🔥 + Supabase ☁️ متصلان"
                                else if (isFirebaseActive) "Firebase Firestore متصل ومفعّل 🔥"
                                else "التخزين المحلي الآمن 💾",
                                color = if (isFirebaseActive) Color(0xFFFFA000) else Color(0xFF94A3B8),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        color = if (isFirebaseActive) Color(0xFF065F46) else Color(0xFF334155),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isFirebaseActive) "مُزامن آلياً ✓" else "محلي",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Profile Actions Section
            ProfileSection(
                title = "إعدادات الحساب والأمان",
                icon = Icons.Default.Security
            ) {
                ProfileActionButton(
                    label = "تعديل بيانات الملف الشخصي",
                    icon = Icons.Default.Edit,
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = true
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileActionButton(
                        label = "تغيير كلمة السر",
                        icon = Icons.Default.LockReset,
                        onClick = { showChangePasswordDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    ProfileActionButton(
                        label = "تبديل الحساب",
                        icon = Icons.Default.SwitchAccount,
                        onClick = { showSwitchAccountDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stats Hero Cards (2x2 Grid)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = Translator.tr("المشاريع"),
                        value = projectsCount.toString(),
                        icon = Icons.Default.Movie,
                        color = qabasPrimary()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = Translator.tr("المشاركات"),
                        value = totalPublished.toString(),
                        icon = Icons.Default.Publish,
                        color = Color(0xFF3B82F6)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = Translator.tr("المنصات"),
                        value = socialAccounts.count { it.isConnected }.toString(),
                        icon = Icons.Default.Link,
                        color = Color(0xFFE1306C)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = Translator.tr("الإنجاز"),
                        value = if (projectsCount > 0) "${(totalPublished * 100 / projectsCount).coerceAtMost(100)}%" else "0%",
                        icon = Icons.Default.TrendingUp,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Firebase Cloud Sync & Bookmarks Card ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.5f, glowElevation = 6.dp)
                    .bouncingClickable { onNavigate(AppState.SAVED_ITEMS) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF064E3B).copy(alpha = 0.5f),
                                    Color(0xFF0B1120)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF10B981)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "المحفوظات السحابية",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Firestore HUD",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = "مزامنة الآيات القرآنية والخواطر والإلهامات لحظياً بدون إنترنت",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "فتح المحفوظات",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Qabas Reels & Community Showcase Card ───
            LaunchedEffect(Unit) {
                ReelPreferencesManager.init(context)
            }
            val reelSavedCount = ReelPreferencesManager.savedReels.collectAsState().value.size
            val reelWatchedCount = ReelPreferencesManager.userStats.collectAsState().value.totalWatched
            val reelLikedCount = ReelPreferencesManager.likedReels.collectAsState().value.size

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.5f, glowElevation = 6.dp)
                    .bouncingClickable { onNavigate(AppState.REELS) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E1B4B).copy(alpha = 0.7f),
                                    Color(0xFF0B1120)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = GoldPrimary.copy(alpha = 0.18f),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.SlowMotionVideo,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "ريلز قبس // Qabas Reels",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = GoldPrimary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "TIKTOK 9:16",
                                                fontFamily = RobotoMonoFont,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DeepSlate,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "مجتمع المقاطع الإيمانية القصيرة وسجل نشاطك الشخصي",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Quick Highlights
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("المحفوظات", fontFamily = CairoFont, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text("$reelSavedCount", fontFamily = RobotoMonoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("المشاهدات", fontFamily = CairoFont, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text("$reelWatchedCount", fontFamily = RobotoMonoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("الإعجابات", fontFamily = CairoFont, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text("$reelLikedCount", fontFamily = RobotoMonoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigate(AppState.REELS) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فتح الريلز", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = DeepSlate)
                            }

                            OutlinedButton(
                                onClick = { onNavigate(AppState.REELS) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.History, null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("السجل والمحفوظات", fontFamily = CairoFont, fontSize = 11.sp, color = GoldPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // League Championship Points & Rank Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.45f, glowElevation = 6.dp)
                    .bouncingClickable { onNavigate(AppState.LEADERBOARD) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E1B4B).copy(alpha = 0.6f),
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = GoldPrimary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "دوري صنّاع الأثر الأسبوعي 🏆",
                                        color = Color.White,
                                        fontFamily = TajawalFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "تنافس مع نخبة صناع المحتوى الدعوي",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showTournamentInfoSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Tournament Info",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(4.dp))

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                color = GoldPrimary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        "مباشر",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                        // Stats Highlights: Points & Rank
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Rank Card
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Leaderboard,
                                                contentDescription = null,
                                                tint = Color(0xFF60A5FA),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "الترتيب الحالي",
                                            color = Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            if (isLoadingLeague) "..." else "#$userLeagueRank",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }

                            // Points Card
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = GoldPrimary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Stars,
                                                contentDescription = null,
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "رصيد النقاط",
                                            color = Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            if (isLoadingLeague) "..." else "$userLeaguePoints نقطة",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Next Rank Milestone & Visual Progress Indicator
                        val nextMilestone = remember(userLeaguePoints) {
                            when {
                                userLeaguePoints < 500 -> Triple(0, 500, "صانع برونزي 🥉")
                                userLeaguePoints < 1500 -> Triple(500, 1500, "صانع فضي 🥈")
                                userLeaguePoints < 3500 -> Triple(1500, 3500, "صانع ذهبي 🥇")
                                userLeaguePoints < 7000 -> Triple(3500, 7000, "صانع ماسي 💎")
                                userLeaguePoints < 12000 -> Triple(7000, 12000, "سفير الدعوة 👑")
                                else -> Triple(12000, (userLeaguePoints + 2000), "أسطورة قبس 🌟")
                            }
                        }
                        val minPoints = nextMilestone.first
                        val targetPoints = nextMilestone.second
                        val nextMilestoneName = nextMilestone.third
                        val pointsNeeded = (targetPoints - userLeaguePoints).coerceAtLeast(0)
                        val progressFraction = if (targetPoints > minPoints) {
                            ((userLeaguePoints - minPoints).toFloat() / (targetPoints - minPoints).toFloat()).coerceIn(0.05f, 1f)
                        } else 1f
                        val progressPercent = ((userLeaguePoints.toFloat() / targetPoints.toFloat()) * 100).toInt().coerceIn(1, 100)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF131D31),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "الرتبة القادمة: $nextMilestoneName",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        "$progressPercent%",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Custom Gradient Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF0F172A))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = progressFraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFF3B82F6),
                                                        Color(0xFF8B5CF6),
                                                        GoldPrimary
                                                    )
                                                )
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "متبقي $pointsNeeded نقطة للترقية",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        "$userLeaguePoints / $targetPoints",
                                        color = Color(0xFFCBD5E1),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Leaderboard & Celebration Trigger
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigate(AppState.LEADERBOARD) },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(44.dp)
                                    .bouncingClickable { onNavigate(AppState.LEADERBOARD) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.MilitaryTech,
                                        contentDescription = null,
                                        tint = DeepSlate,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "صالة البطولة 🌟",
                                        color = DeepSlate,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { showRankMilestoneCelebration = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .bouncingClickable { showRankMilestoneCelebration = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Celebration,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "احتفال الرتبة 🎉",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Social Platforms Section
            ProfileSection(
                title = "ربط منصات التواصل",
                icon = Icons.Default.Share
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    socialAccounts.forEach { account ->
                        SocialPlatformItemCard(
                            account = account,
                            onToggleConnect = {
                                if (account.isConnected) {
                                    SocialAccountManager.toggleConnection(context, account.id, false)
                                    socialAccounts = SocialAccountManager.getAccounts(context)
                                } else {
                                    selectedEditAccount = account
                                    editHandleText = ""
                                }
                            },
                            onEditHandle = {
                                selectedEditAccount = account
                                editHandleText = account.handle
                            },
                            onOpenProfile = {
                                if (account.isConnected && account.handle.isNotBlank()) {
                                    SocialAccountManager.openProfileUrl(context, account.id, account.handle)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badges Section
            ProfileSection(
                title = "الأوسمة والإنجازات",
                icon = Icons.Default.WorkspacePremium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tournamentBadges.forEach { badge ->
                        Box(modifier = Modifier.width(130.dp)) {
                            TournamentBadgeCard(
                                badge = badge,
                                userPoints = userLeaguePoints,
                                userRank = userLeagueRank,
                                onClick = { selectedBadgeForDetail = badge }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Account Plan Section
            val accountService = remember { AppServices.getAccountService(context) }
            val planTitle = when {
                accountService.isDeveloperOrAdmin -> "باقة المطور 🛠️"
                accountService.hasCustomKeys -> "المفاتيح الخاصة (BYOK) 🔑"
                accountService.isPremium -> "قبس برو 👑"
                else -> "باقة مجانية ⚡"
            }
            val isUnlimited = accountService.isDeveloperOrAdmin || accountService.hasCustomKeys || accountService.isPremium
            
            ProfileSection(
                title = "باقة الحساب والرصيد",
                icon = Icons.Default.AccountBalanceWallet
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.3f, glowElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface()),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Translator.tr("خطة الاشتراك الحالية"),
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            
                            Surface(
                                color = if (isUnlimited) qabasPrimary().copy(alpha = 0.2f) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isUnlimited) qabasPrimary() else Color(0xFF64748B)
                                )
                            ) {
                                Text(
                                    text = planTitle,
                                    color = if (isUnlimited) qabasPrimary() else qabasTextSecondary(),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(qabasBackground().copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الرصيد 💰", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 10.sp)
                                Text("${accountService.walletBalance}", color = qabasPrimary(), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(qabasTextSecondary().copy(alpha = 0.2f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("فيديوهات 🎬", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 10.sp)
                                Text(
                                    if (isUnlimited) "∞" else "${accountService.dailyVideoCount}/${AccountService.MAX_DAILY_VIDEOS}",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(qabasTextSecondary().copy(alpha = 0.2f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("صور 🖼️", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 10.sp)
                                Text(
                                    if (isUnlimited) "∞" else "${accountService.dailyImageCount}/${AccountService.MAX_DAILY_IMAGES}",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        ProfileActionButton(
                            label = "المتجر والترقية 👑",
                            icon = Icons.Default.ShoppingCart,
                            onClick = { onNavigate(AppState.PREMIUM_UPGRADE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // If 0 projects, show an actionable empty state banner
            if (projectsCount == 0) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.25f, glowElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface()),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = qabasPrimary(), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "ابدأ رحلتك الدعوية الأولى 🌿",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "حوّل أي فكرة إلى مقطع ريلز احترافي في ثوانٍ",
                            color = qabasTextSecondary(),
                            fontFamily = TajawalFont,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ProfileActionButton(
                            label = "بدء مشروع جديد 🚀",
                            icon = Icons.Default.Add,
                            onClick = { onNavigate(AppState.INPUT) },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Change Password Dialog (Supabase connected)
    if (showChangePasswordDialog) {
        var currentPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmNewPw by remember { mutableStateOf("") }
        var isUpdatingPw by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isUpdatingPw) showChangePasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockReset, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تغيير كلمة المرور 🔐", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("الحساب الحالي: $userEmail", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("كلمة المرور الجديدة", color = Color.Gray, fontFamily = CairoFont) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = confirmNewPw,
                        onValueChange = { confirmNewPw = it },
                        label = { Text("تأكيد كلمة المرور الجديدة", color = Color.Gray, fontFamily = CairoFont) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPw.length < 6) {
                            Toast.makeText(context, "كلمة المرور يجب ألا تقل عن 6 أحرف", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPw != confirmNewPw) {
                            Toast.makeText(context, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isUpdatingPw = true
                        coroutineScope.launch {
                            var success = false
                            if (SupabaseServices.isSupabaseAvailable) {
                                val ok = SupabaseServices.Auth.updatePassword(newPw.trim())
                                if (ok) success = true
                            }
                            if (CloudServices.isFirebaseInitialized && !success) {
                                val ok = CloudServices.Auth.resetPassword(userEmail)
                                if (ok) success = true
                            }
                            if (!success && !SupabaseServices.isSupabaseAvailable && !CloudServices.isFirebaseInitialized) {
                                success = true
                            }
                            isUpdatingPw = false
                            showChangePasswordDialog = false
                            if (success) {
                                Toast.makeText(context, "تم تحديث كلمة المرور بنجاح في Supabase! 🔒", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "تم إرسال رابط تأكيد التغيير إلى بريدك الإلكتروني", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isUpdatingPw,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isUpdatingPw) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(18.dp))
                    } else {
                        Text("تحديث كلمة السر", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    enabled = !isUpdatingPw
                ) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = DeepSlate
        )
    }

    // Switch Account Dialog
    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تبديل الحساب 👥", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                val registeredAccounts = remember { LocalAccountRegistry.getAllAccounts(context) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "الحساب الحالي النشط:",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 12.5.sp
                    )
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(displayName, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 13.sp)
                                Text(userEmail, color = GoldPrimary, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                            }
                            Surface(color = GoldPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text("نشط حالياً", color = GoldPrimary, fontSize = 10.sp, fontFamily = CairoFont, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    if (registeredAccounts.size > 1) {
                        Text(
                            "الحسابات المسجلة الأخرى (${registeredAccounts.size - 1}):",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 240.dp)
                        ) {
                            registeredAccounts.filter { !it.email.equals(userEmail, ignoreCase = true) }.forEach { otherAcc ->
                                Surface(
                                    onClick = {
                                        showSwitchAccountDialog = false
                                        val isAdmin = otherAcc.type == "مطور" || CloudServices.isOwnerAccount(otherAcc.email)
                                        prefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putBoolean("is_admin", isAdmin)
                                            .putBoolean("is_developer", isAdmin)
                                            .putString("user_email", otherAcc.email)
                                            .putString("user_name", otherAcc.name)
                                            .putString("user_display_name", otherAcc.name)
                                            .putString("user_custom_id", otherAcc.customId)
                                            .putString("user_tag", UserIdentifierManager.formatDisplay(otherAcc.customId))
                                            .apply()
                                        Toast.makeText(context, "تم التبديل إلى: ${otherAcc.name} (${otherAcc.email}) ✅", Toast.LENGTH_SHORT).show()
                                        onNavigate(AppState.HOME)
                                    },
                                    color = CardSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(otherAcc.name, color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(otherAcc.email, color = TextSecondary, fontFamily = RobotoMonoFont, fontSize = 10.sp)
                                        }
                                        Text("تبديل ➔", color = GoldSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "أو يمكنك تسجيل الخروج لإنشاء حساب جديد أو تسجيل الدخول ببريد آخر:",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchAccountDialog = false
                        coroutineScope.launch {
                            runCatching { SupabaseServices.Auth.logout() }
                        }
                        prefs.edit()
                            .remove("is_logged_in")
                            .remove("is_admin")
                            .remove("is_developer")
                            .remove("user_email")
                            .remove("user_name")
                            .apply()
                        onNavigate(AppState.LOGIN)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("خروج / حساب جديد ➕", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = DeepSlate
        )
    }

    // Comprehensive Profile Edit Dialog (Name, Bio, Tag) - with Mandatory Locked Account Number & Unique Tag Check
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(displayName) }
        var tempTag by remember { mutableStateOf(userCustomTag) }
        var tempBio by remember { mutableStateOf(userBio) }
        var tagError by remember { mutableStateOf<String?>(null) }
        var isCheckingTag by remember { mutableStateOf(false) }

        val activeAccountNumber = userAccountNumber.takeIf { it.isNotBlank() }
            ?: UserIdentifierManager.getAccountNumber(context, userEmail).takeIf { it.isNotBlank() }
            ?: UserIdentifierManager.peekNextAccountNumber(context)

        AlertDialog(
            onDismissRequest = { if (!isCheckingTag) showEditProfileDialog = false },
            title = {
                Text("تعديل بيانات الملف الشخصي 👤", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Mandatory & Non-Editable Account Number (Locked & Cloud Verified)
                    Text(
                        "رقم الحساب المعتمد (ثابت وموثق سحابياً - غير قابل للتعديل 🔒):",
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "رقم الحساب مقفول وموثق",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = UserIdentifierManager.formatAccountNumber(activeAccountNumber),
                                    color = GoldPrimary,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "موثق سحابياً ☁️",
                                    color = GoldPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "🔒 رقم الحساب متسلسل ومربوط بحسابك في السحابة ولا يمكن تغييره لحماية الهوية، بينما يمكنك تعديل اسم الحساب والمعرّف الرقمي بحرية.",
                        color = Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 2. Display Name (Editable)
                    Text("اسم الحساب / القناة الدعوية (قابل للتعديل):", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        placeholder = { Text("مثال: قبس للإنتاج الدعوي", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    // 3. User Tag / Identifier (Editable with uniqueness check to prevent duplicates)
                    Text("المعرف الرقمي الفريد (Tag) (قابل للتعديل - يمنع التكرار):", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                    OutlinedTextField(
                        value = tempTag,
                        onValueChange = {
                            tempTag = it
                            tagError = null
                        },
                        placeholder = { Text("@qabas_creator", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = tagError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (tagError != null) Color(0xFFEF4444) else GoldPrimary,
                            unfocusedBorderColor = if (tagError != null) Color(0xFFEF4444) else Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    if (tagError != null) {
                        Text(
                            text = "⚠️ $tagError",
                            color = Color(0xFFEF4444),
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "يجب أن يكون فريداً وغير مستخدم من قبل أي صانع محتوى آخر في السحابة.",
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }

                    // 4. Bio
                    Text("النبذة التعريفية (Bio):", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                    OutlinedTextField(
                        value = tempBio,
                        onValueChange = { tempBio = it },
                        placeholder = { Text("اكتب نبذة عن رسالتك وهدفك الدعوي...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCheckingTag,
                    onClick = {
                        val cleanId = UserIdentifierManager.normalize(tempTag)
                        if (cleanId.isBlank()) {
                            tagError = "يرجى إدخال معرّف صحيح"
                            return@Button
                        }

                        isCheckingTag = true
                        coroutineScope.launch {
                            val checkResult = UserIdentifierManager.checkCustomIdAvailability(
                                context = context,
                                customId = cleanId,
                                currentEmail = userEmail
                            )

                            when (checkResult) {
                                is UserIdentifierManager.CustomIdAvailabilityResult.Taken -> {
                                    isCheckingTag = false
                                    tagError = checkResult.reason
                                }
                                is UserIdentifierManager.CustomIdAvailabilityResult.Invalid -> {
                                    isCheckingTag = false
                                    tagError = checkResult.reason
                                }
                                is UserIdentifierManager.CustomIdAvailabilityResult.Available,
                                is UserIdentifierManager.CustomIdAvailabilityResult.CurrentUserOwnsIt -> {
                                    displayName = tempName.ifBlank { "صانع المحتوى الدعوي" }
                                    userCustomTag = UserIdentifierManager.formatDisplay(cleanId)
                                    userBio = tempBio
                                    UserIdentifierManager.saveCustomId(context, cleanId, userEmail)

                                    prefs.edit()
                                        .putString("user_display_name", displayName)
                                        .putString("user_name", displayName)
                                        .putString("user_tag", userCustomTag)
                                        .putString("user_custom_id", cleanId)
                                        .putString("user_bio", userBio)
                                        .apply()

                                    if (SupabaseServices.isSupabaseAvailable && userEmail.isNotBlank()) {
                                        val userId = SupabaseServices.Auth.getCurrentUserId() ?: userEmail
                                        SupabaseServices.Database.saveUser(
                                            id = userId,
                                            name = displayName,
                                            email = userEmail,
                                            type = prefs.getString("user_type", "مجاني") ?: "مجاني",
                                            customId = cleanId,
                                            accountNumber = activeAccountNumber
                                        )
                                    }

                                    isCheckingTag = false
                                    showEditProfileDialog = false
                                    Toast.makeText(context, "تم حفظ الملف الشخصي وتأكيد المعرّف بنجاح! 💾", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isCheckingTag) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري التحقق...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    } else {
                        Text("حفظ التغييرات", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isCheckingTag) showEditProfileDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = DeepSlate
        )
    }

    // Avatar Selection Dialog (Upload image from device or choose preset icons)
    if (showAvatarPickerSheet) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerSheet = false },
            title = {
                Text("تغيير الصورة الشخصية 📸", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Upload from gallery button
                    Button(
                        onClick = {
                            showAvatarPickerSheet = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رفع صورة من المعرض (Gallery)", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    Text("أو اختر رمزاً دعوياً معتمداً:", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)

                    // Preset Avatars Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val presets = listOf(
                            0 to Icons.Default.Person,
                            1 to Icons.Default.AutoAwesome,
                            2 to Icons.Default.MovieFilter,
                            3 to Icons.Default.MenuBook,
                            4 to Icons.Default.Psychology
                        )

                        presets.forEach { (index, icon) ->
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedPresetAvatar == index && avatarUriString.isBlank()) GoldPrimary else Color(0xFF151B2B))
                                    .border(1.dp, GoldPrimary, CircleShape)
                                    .clickable {
                                        selectedPresetAvatar = index
                                        avatarUriString = ""
                                        prefs.edit().putString("user_avatar_uri", "").putInt("user_avatar_preset", index).apply()
                                        showAvatarPickerSheet = false
                                        Toast.makeText(context, "تم تعيين الرمز الشخصي ✦", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (selectedPresetAvatar == index && avatarUriString.isBlank()) DeepSlate else GoldPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarPickerSheet = false }) {
                    Text("إغلاق", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = DeepSlate
        )
    }

    // Handle Edit Modal Dialog
    selectedEditAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { selectedEditAccount = null },
            title = {
                Text("تعديل معرف ${account.name} ✏️", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل اسم المستخدم على منصة ${account.name}:", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editHandleText,
                        onValueChange = { editHandleText = it },
                        placeholder = { Text("@user", color = Color.Gray, fontFamily = RobotoMonoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SocialAccountManager.toggleConnection(context, account.id, isConnected = true, handle = editHandleText)
                        socialAccounts = SocialAccountManager.getAccounts(context)
                        selectedEditAccount = null
                        Toast.makeText(context, "تم حفظ الحساب بنجاح! 💾", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEditAccount = null }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = DeepSlate
        )
    }

    if (showScheduleDialog) {
        SmartPublishScheduleDialog(
            initialTitle = "جدولة نشر مقطع دعوي جديد",
            onDismiss = { showScheduleDialog = false }
        )
    }

    // Tournament Badge Detail Dialog Modal
    selectedBadgeForDetail?.let { badge ->
        TournamentBadgeDetailDialog(
            badge = badge,
            userPoints = userLeaguePoints,
            userRank = userLeagueRank,
            onDismiss = { selectedBadgeForDetail = null },
            onNavigateToLeague = {
                selectedBadgeForDetail = null
                onNavigate(AppState.LEADERBOARD)
            }
        )
    }

    // Tournament Milestone Rank Celebration Dialog Modal (Lottie Animation Sequence)
    if (showRankMilestoneCelebration) {
        TournamentRankMilestoneCelebrationDialog(
            newTierTitle = currentTierTitleOnly,
            newTierEmoji = currentTierEmoji,
            points = userLeaguePoints,
            rank = userLeagueRank,
            onDismiss = { showRankMilestoneCelebration = false },
            onNavigateToLeaderboard = {
                showRankMilestoneCelebration = false
                onNavigate(AppState.LEADERBOARD)
            }
        )
    }

    // Tournament Information Bottom Sheet
    if (showTournamentInfoSheet) {
        TournamentInfoBottomSheet(
            onDismiss = { showTournamentInfoSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentInfoBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepSlate,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GoldPrimary.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "دليل دوري صنّاع الأثر 🏆",
                    color = Color.White,
                    fontFamily = TajawalFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Section: How to earn points
            Text(
                "كيف تجمع النقاط؟ 📈",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            PointRuleItem("إنشاء مشروع دعوي جديد", "50 نقطة")
            PointRuleItem("نشر فيديو على منصات التواصل", "20 نقطة")
            PointRuleItem("تحقيق 1000 مشاهدة (تلقائي)", "100 نقطة")
            PointRuleItem("التفاعل مع مجتمع صُنّاع قبس", "10 نقاط")
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section: Tiers and Rewards
            Text(
                "الرتب والمكافآت 🎁",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            TierInfoItem("البرونزية 🥉", "0 - 499 نقطة", "بداية الرحلة والظهور الأولي")
            TierInfoItem("الفضية 🥈", "500 - 1499 نقطة", "مضاعف نقاط 1.2x وأولوية معالجة")
            TierInfoItem("الذهبية 🥇", "1500 - 3499 نقطة", "إطار ذهبي متوهج ومضاعف 1.5x")
            TierInfoItem("الماسية 💎", "3500 - 6999 نقطة", "توليد فوري غير محدود للمشاهد")
            TierInfoItem("السفير 👑", "7000 - 11999 نقطة", "شارة توثيق ذهبية ونماذج تجريبية")
            TierInfoItem("الأسطورية 🌟", "12000+ نقطة", "تخليد في لوحة الشرف الخالدة")
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Prize Note
            Surface(
                color = GoldPrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Celebration,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "يتم توزيع جوائز عينية ومادية (اشتراكات Pro) لأفضل 3 صناع في لوحة الشرف نهاية كل شهر! 🎉",
                        color = Color.White,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("فهمت، لننطلق! 🚀", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PointRuleItem(title: String, points: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.9f), fontFamily = NotoSansFont, fontSize = 13.sp)
        Text(points, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun TierInfoItem(tier: String, range: String, perk: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(tier, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(range, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 12.sp)
        }
        Text(perk, color = Color(0xFF60A5FA), fontFamily = NotoSansFont, fontSize = 11.sp)
    }
}

@Composable
fun TournamentBadgeCard(
    badge: TournamentTierBadge,
    userPoints: Int,
    userRank: Int,
    onClick: () -> Unit
) {
    val isUnlocked = badge.isUnlocked
    val isCurrent = badge.isCurrentTier
    val cardBorderColor = when {
        isCurrent -> badge.primaryColor
        isUnlocked -> badge.primaryColor.copy(alpha = 0.5f)
        else -> Color(0xFF1E293B)
    }
    val cardBgColor = if (isUnlocked) Color(0xFF0F172A) else Color(0xFF0B0F19)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(
                shapeRadius = 14.dp,
                borderAlpha = if (isCurrent) 0.8f else if (isUnlocked) 0.4f else 0.15f,
                glowElevation = if (isCurrent) 4.dp else 1.dp
            )
            .bouncingClickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Medallion Icon Box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) badge.primaryColor.copy(alpha = 0.2f)
                        else Color(0xFF151B2B)
                    )
                    .border(
                        1.2.dp,
                        if (isUnlocked) badge.primaryColor else Color(0xFF334155),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    Icon(
                        badge.icon,
                        contentDescription = null,
                        tint = badge.primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                badge.title,
                color = if (isUnlocked) Color.White else Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1
            )

            // Subtitle Tier
            Text(
                badge.tierTitle,
                color = if (isUnlocked) badge.accentColor else Color.Gray,
                fontFamily = NotoSansFont,
                fontSize = 10.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status Tag
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isCurrent -> badge.primaryColor.copy(alpha = 0.25f)
                    isUnlocked -> Color(0xFF10B981).copy(alpha = 0.18f)
                    else -> Color(0xFF1E293B)
                },
                border = androidx.compose.foundation.BorderStroke(
                    0.6.dp,
                    when {
                        isCurrent -> badge.primaryColor
                        isUnlocked -> Color(0xFF10B981).copy(alpha = 0.5f)
                        else -> Color(0xFF334155)
                    }
                )
            ) {
                Text(
                    text = when {
                        isCurrent -> "الرتبة الحالية ⭐"
                        isUnlocked -> "مفتوح ✓"
                        badge.rankRequired != null -> "الترتيب #${badge.rankRequired}"
                        else -> "${badge.pointsRequired} نقطة"
                    },
                    color = when {
                        isCurrent -> badge.primaryColor
                        isUnlocked -> Color(0xFF10B981)
                        else -> Color(0xFF94A3B8)
                    },
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TournamentBadgeDetailDialog(
    badge: TournamentTierBadge,
    userPoints: Int,
    userRank: Int,
    onDismiss: () -> Unit,
    onNavigateToLeague: () -> Unit
) {
    val isUnlocked = badge.isUnlocked
    val isCurrent = badge.isCurrentTier

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Big Glowing Medallion
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    badge.primaryColor.copy(alpha = 0.4f),
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .border(
                            2.dp,
                            if (isUnlocked) badge.primaryColor else Color(0xFF475569),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isUnlocked) badge.icon else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlocked) badge.primaryColor else Color.Gray,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Badge Title & Tier
                Text(
                    text = "${badge.title} ${badge.emoji}",
                    color = Color.White,
                    fontFamily = TajawalFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = badge.tierTitle,
                    color = badge.accentColor,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isCurrent -> badge.primaryColor.copy(alpha = 0.2f)
                        isUnlocked -> Color(0xFF10B981).copy(alpha = 0.18f)
                        else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            isCurrent -> badge.primaryColor
                            isUnlocked -> Color(0xFF10B981).copy(alpha = 0.6f)
                            else -> Color(0xFFEF4444).copy(alpha = 0.4f)
                        }
                    )
                ) {
                    Text(
                        text = when {
                            isCurrent -> "رتبتك المعتمدة الحالية في البطولة ⭐"
                            isUnlocked -> "تم استحقاق هذا الوسام بنجاح 🎖️"
                            else -> "وسام مقفل - بحاجة لمزيد من النقاط 🔒"
                        },
                        color = when {
                            isCurrent -> badge.primaryColor
                            isUnlocked -> Color(0xFF10B981)
                            else -> Color(0xFFFCA5A5)
                        },
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "عن الوسام:",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            badge.description,
                            color = Color(0xFFE2E8F0),
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Perk Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1B4B).copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4338CA).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "المزايا والمكافآت الممنوحة:",
                                color = Color(0xFFA5B4FC),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            badge.perk,
                            color = Color.White,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Requirement & Progress
                if (!isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF131D31),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (badge.rankRequired != null) {
                                Text(
                                    "متطلب الترتيب: الوصول للمركز #${badge.rankRequired} أو أفضل",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "ترتيبك الحالي: #$userRank",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            } else {
                                val remaining = (badge.pointsRequired - userPoints).coerceAtLeast(0)
                                val fraction = (userPoints.toFloat() / badge.pointsRequired.toFloat()).coerceIn(0.05f, 1f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "التقدم نحو الوسام",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        "$userPoints / ${badge.pointsRequired}",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F172A))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = fraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF3B82F6), GoldPrimary)
                                                )
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "متبقي $remaining نقطة لفتح هذا الوسام",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onNavigateToLeague,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = DeepSlate,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "عرض الترتيب في البطولة 🏆",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color.Gray, fontFamily = CairoFont)
            }
        },
        containerColor = DeepSlate,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun SocialPlatformItemCard(
    account: SocialPlatformAccount,
    onToggleConnect: () -> Unit,
    onEditHandle: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    val platformColor = when (account.id) {
        "youtube" -> Color(0xFFF44336)
        "tiktok" -> Color(0xFF00F2FE)
        "instagram" -> Color(0xFFE1306C)
        "twitter" -> Color(0xFF1DA1F2)
        else -> Color(0xFF1877F2)
    }

    val platformIcon: ImageVector = when (account.id) {
        "youtube" -> Icons.Default.OndemandVideo
        "tiktok" -> Icons.Default.MusicVideo
        "instagram" -> Icons.Default.CameraAlt
        "twitter" -> Icons.Default.Tag
        else -> Icons.Default.Share
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
            .border(1.dp, if (account.isConnected) platformColor.copy(alpha = 0.4f) else Color(0xFF151B2B), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(platformIcon, contentDescription = null, tint = platformColor, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(account.name, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(if (account.isConnected) account.handle else "غير مربوط", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (account.isConnected) {
                    // زر فتح الملف الشخصي مباشرة على المنصة
                    IconButton(onClick = onOpenProfile, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "فتح الملف الشخصي", tint = platformColor, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onEditHandle, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    }
                }

                Button(
                    onClick = onToggleConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = if (account.isConnected) platformColor.copy(alpha = 0.2f) else Color(0xFF151B2B)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = if (account.isConnected) "مربوط 🟢" else "ربط ⚪",
                        color = if (account.isConnected) platformColor else Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.RankHighlightCard(label: String, value: String, icon: ImageVector, accent: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.2f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 10.sp)
                Text(value, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun HudCornerDecor(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(24.dp).padding(4.dp)) {
        Box(modifier = Modifier.align(Alignment.TopStart).size(8.dp).border(1.5.dp, qabasPrimary().copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp)))
    }
}

fun formatStatNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000.0)
        num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = modifier
            .luxuryCardStyle(shapeRadius = 16.dp, borderAlpha = 0.25f, glowElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface()),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = qabasTextSecondary(), fontFamily = TajawalFont, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = qabasPrimary(),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                title,
                color = qabasTextPrimary(),
                fontFamily = TajawalFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .width(40.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(qabasPrimary().copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
            )
        }
        content()
    }
}

@Composable
fun ProfileActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .bouncingClickable { onClick() },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) qabasPrimary() else qabasSurface(),
            contentColor = if (isPrimary) DeepSlate else qabasTextPrimary()
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (!isPrimary) androidx.compose.foundation.BorderStroke(1.dp, qabasPrimary().copy(alpha = 0.2f)) else null,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isPrimary) DeepSlate else qabasPrimary()
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                label,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
