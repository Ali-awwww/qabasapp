package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.components.CloudSyncIndicator
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

private fun accountProfilePreferences(context: Context, email: String) =
    AccountScopedPreferences.forEmail(context, email)

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

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigate: (AppState) -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    var userEmail by remember {
        mutableStateOf(
            SupabaseServices.Auth.getCurrentUserEmail()?.takeIf { it.isNotBlank() }
                ?: prefs.getString("user_email", "")
                ?: ""
        )
    }
    val profilePrefs = remember(userEmail) { accountProfilePreferences(context, userEmail) }
    val authenticatedUser by SupabaseServices.Auth.currentUser.collectAsState()
    val authenticatedUid = authenticatedUser?.id?.takeIf { it.isNotBlank() }
        ?: SupabaseServices.Auth.getCurrentUserId()

    var displayName by remember {
        val savedName = profilePrefs.getString("user_name", "")?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_name", "")?.takeIf {
                prefs.getString("user_email", "")?.equals(userEmail, ignoreCase = true) == true && it.isNotBlank()
            }
            ?: "مستخدم خادم المسلم"
        mutableStateOf(savedName)
    }
    var userBio by remember {
        mutableStateOf(profilePrefs.getString("user_bio", "") ?: "نشر الخير والقرآن الكريم عبر خادم المسلم")
    }
    var userCustomTag by remember {
        val cid = UserIdentifierManager.getCustomId(context, userEmail)
        mutableStateOf(if (cid.isNotBlank()) UserIdentifierManager.formatDisplay(cid) else "@qabas_user")
    }
    var userAccountNumber by remember {
        mutableStateOf(UserIdentifierManager.getAccountNumber(context, userEmail))
    }
    var avatarUriString by remember {
        mutableStateOf(profilePrefs.getString("user_avatar_uri", "") ?: "")
    }
    var selectedPresetAvatar by remember {
        mutableIntStateOf(profilePrefs.getInt("user_avatar_preset", 0))
    }

    val projectService = remember { ProjectService(context) }
    var projectsCount by remember { mutableIntStateOf(0) }
    var profileViewer by remember { mutableStateOf<CreatorFollowManager.ViewerIdentity?>(null) }
    var followedCreators by remember { mutableStateOf<List<CreatorFollowManager.FollowedCreator>>(emptyList()) }
    var ownFollowers by remember { mutableStateOf(CreatorFollowManager.FollowerCount(0, isExact = false)) }
    var isLoadingFollowCommunity by remember { mutableStateOf(true) }
    var profileSnapshot by remember { mutableStateOf<SupabaseServices.CurrentProfileSnapshot?>(null) }
    var profileSnapshotOwnerUid by remember { mutableStateOf<String?>(null) }
    var followActionId by remember { mutableStateOf<String?>(null) }
    var showFollowList by remember { mutableStateOf(false) }
    val hasCurrentProfileSnapshot = authenticatedUid != null && profileSnapshotOwnerUid == authenticatedUid
    val profileFollowingCountText = if (hasCurrentProfileSnapshot) {
        profileSnapshot?.followingCount?.toString() ?: "—"
    } else "—"

    var socialAccounts by remember { mutableStateOf(SocialAccountManager.getAccounts(context)) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    var selectedEditAccount by remember { mutableStateOf<SocialPlatformAccount?>(null) }
    var editHandleText by remember { mutableStateOf("") }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refreshFollowCommunity() {
        val uid = SupabaseServices.Auth.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: return
        isLoadingFollowCommunity = true
        try {
            val snapshot = SupabaseServices.Profiles.getCurrentProfileSnapshot(uid) ?: return
            if (SupabaseServices.Auth.getCurrentUserId() != uid) return
            val refreshedFollowing = if (snapshot.followingTruncated) null else {
                CreatorFollowManager.applyProfileSnapshot(
                    context.applicationContext,
                    uid,
                    snapshot.following
                )
            }
            if (SupabaseServices.Auth.getCurrentUserId() != uid) return
            ReelPreferencesManager.init(context)
            ReelPreferencesManager.replaceActivityFromCloud(context, snapshot.activity)
            profileSnapshot = snapshot
            profileSnapshotOwnerUid = uid
            followedCreators = refreshedFollowing ?: CreatorFollowManager.followedCreators(context.applicationContext)
            ownFollowers = CreatorFollowManager.FollowerCount(snapshot.followerCount, isExact = true)
        } finally {
            if (SupabaseServices.Auth.getCurrentUserId() == uid) {
                isLoadingFollowCommunity = false
            }
        }
    }

    LaunchedEffect(authenticatedUid) {
        isLoadingFollowCommunity = true
        profileSnapshot = null
        profileSnapshotOwnerUid = null
        profileViewer = null
        followedCreators = emptyList()
        ownFollowers = CreatorFollowManager.FollowerCount(0, isExact = false)

        try {
            val sbEmail = SupabaseServices.Auth.getCurrentUserEmail()?.takeIf { it.isNotBlank() }
            val sbName = SupabaseServices.Auth.getCurrentUserMeta("full_name")
                ?: SupabaseServices.Auth.getCurrentUserMeta("name")
            val sbAvatar = SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
                ?: SupabaseServices.Auth.getCurrentUserMeta("picture")

            val expectedUid = authenticatedUid?.takeIf { it.isNotBlank() }
            val realEmail = sbEmail
            val identityChanged = !realEmail.isNullOrBlank() && !realEmail.equals(userEmail, ignoreCase = true)
            val activeEmail = realEmail.orEmpty()
            val activeProfilePrefs = accountProfilePreferences(context, activeEmail)

            displayName = activeProfilePrefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
                ?: sbName?.takeIf { expectedUid != null }
                ?: "مستخدم خادم المسلم"
            userBio = activeProfilePrefs.getString("user_bio", "") ?: "نشر الخير والقرآن الكريم عبر خادم المسلم"
            userCustomTag = UserIdentifierManager.getCustomId(context, activeEmail)
                .let { if (it.isNotBlank()) UserIdentifierManager.formatDisplay(it) else "@qabas_user" }
            avatarUriString = activeProfilePrefs.getString("user_avatar_uri", "") ?: ""
            userAccountNumber = UserIdentifierManager.getAccountNumber(context, activeEmail)
            selectedPresetAvatar = activeProfilePrefs.getInt("user_avatar_preset", 0)
            if (activeEmail.isBlank() && userEmail.isNotEmpty()) userEmail = ""

            val fetchedSnapshot = expectedUid?.let {
                SupabaseServices.Profiles.getCurrentProfileSnapshot(it)
            }
            val activeSnapshot = fetchedSnapshot?.takeIf {
                SupabaseServices.Auth.getCurrentUserId() == expectedUid
            }
            if (SupabaseServices.Auth.getCurrentUserId() != expectedUid) return@LaunchedEffect

            val cloudUser = activeSnapshot?.user
            val cloudProfileMirror = activeSnapshot?.profile
            val cloudDisplayName = cloudUser?.name?.takeIf { it.isNotBlank() }
                ?: cloudProfileMirror?.fullName?.takeIf { it.isNotBlank() }
            val savedAccountName = activeProfilePrefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
            val globalIdentityMatches = prefs.getString("user_email", null)?.equals(activeEmail, ignoreCase = true) == true
            val nameSnapshot = savedAccountName ?: if (globalIdentityMatches) {
                prefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
                    ?: prefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
            } else null

            if (identityChanged) {
                userEmail = activeEmail
                prefs.edit().putString("user_email", activeEmail).apply()
            }

            val effectiveName = if (!cloudDisplayName.isNullOrBlank()) {
                cloudDisplayName.trim()
            } else {
                val baseName = if (nameSnapshot == "علي وليد") null else savedAccountName
                baseName ?: sbName ?: "مستخدم خادم المسلم"
            }
            displayName = effectiveName
            activeProfilePrefs.edit().putString("user_name", effectiveName).apply()
            if (identityChanged || globalIdentityMatches) {
                prefs.edit().putString("user_display_name", effectiveName).putString("user_name", effectiveName).apply()
            }

            val cloudBio = activeSnapshot?.bio
            if (activeSnapshot != null && cloudBio != null) {
                userBio = cloudBio
                activeProfilePrefs.edit().putString("user_bio", cloudBio).apply()
            } else {
                userBio = activeProfilePrefs.getString("user_bio", "") ?: "نشر الخير والقرآن الكريم عبر خادم المسلم"
            }

            if (!cloudUser?.customId.isNullOrBlank()) {
                userCustomTag = UserIdentifierManager.formatDisplay(cloudUser.customId!!)
                UserIdentifierManager.saveCustomId(context, cloudUser.customId, activeEmail)
            } else {
                userCustomTag = UserIdentifierManager.getCustomId(context, realEmail ?: userEmail)
                    .let { if (it.isNotBlank()) UserIdentifierManager.formatDisplay(it) else "@qabas_user" }
            }

            val savedAvatarUri = activeProfilePrefs.getString("user_avatar_uri", "") ?: ""
            val cloudAvatarUrl = cloudProfileMirror?.avatarUrl?.trim()
                ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            avatarUriString = cloudAvatarUrl ?: savedAvatarUri.ifBlank { sbAvatar ?: "" }
            selectedPresetAvatar = activeProfilePrefs.getInt("user_avatar_preset", 0)

            if (!cloudAvatarUrl.isNullOrBlank()) {
                activeProfilePrefs.edit().putString("user_avatar_uri", cloudAvatarUrl).putInt("user_avatar_preset", -1).apply()
                selectedPresetAvatar = -1
            } else if (avatarUriString.isBlank() && sbAvatar != null) {
                avatarUriString = sbAvatar
                activeProfilePrefs.edit().putString("user_avatar_uri", sbAvatar).apply()
            }

            userAccountNumber = cloudUser?.accountNumber?.takeIf { it.isNotBlank() }
                ?: UserIdentifierManager.getAccountNumber(context, realEmail ?: userEmail)

            if (activeSnapshot != null) {
                val appliedFollowing = if (activeSnapshot.followingTruncated) null else {
                    CreatorFollowManager.applyProfileSnapshot(
                        context.applicationContext,
                        expectedUid,
                        activeSnapshot.following
                    )
                }
                if (SupabaseServices.Auth.getCurrentUserId() != expectedUid) return@LaunchedEffect
                ReelPreferencesManager.init(context)
                ReelPreferencesManager.replaceActivityFromCloud(context, activeSnapshot.activity)
                profileSnapshot = activeSnapshot
                profileSnapshotOwnerUid = expectedUid
                followedCreators = appliedFollowing ?: CreatorFollowManager.followedCreators(context.applicationContext)
                ownFollowers = CreatorFollowManager.FollowerCount(activeSnapshot.followerCount, isExact = true)
            } else {
                followedCreators = CreatorFollowManager.followedCreators(context.applicationContext)
            }

            profileViewer = CreatorFollowManager.ViewerIdentity(
                id = expectedUid.orEmpty(),
                displayName = effectiveName,
                email = realEmail,
                avatarUrl = avatarUriString.takeIf { it.isNotBlank() }
            )

            val targetEmail = activeEmail
            if (cloudUser?.accountNumber.isNullOrBlank() && targetEmail.isNotBlank() && !targetEmail.contains("guest")) {
                val cloudAcc = UserIdentifierManager.ensureCloudAccountNumber(context, targetEmail, displayName)
                if (SupabaseServices.Auth.getCurrentUserId() != expectedUid) return@LaunchedEffect
                if (cloudAcc.isNotBlank()) {
                    userAccountNumber = cloudAcc
                    prefs.edit().putString("user_account_number", cloudAcc).apply()
                }
            }
        } finally {
            if (SupabaseServices.Auth.getCurrentUserId() == authenticatedUid) {
                isLoadingFollowCommunity = false
            }
        }
    }

    // Zero-permission Android Photo Picker per Google Play Policy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null) {
                    Toast.makeText(context, "تعذر قراءة الصورة المختارة", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "image/jpeg"
                val localFile = withContext(Dispatchers.IO) {
                    runCatching {
                        val accountKey = MessageDigest.getInstance("SHA-256")
                            .digest(userEmail.trim().lowercase(Locale.ROOT).toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        val f = File.createTempFile("user_avatar_${accountKey}_", ".jpg", context.cacheDir)
                        FileOutputStream(f).use { it.write(bytes) }
                        f
                    }.getOrNull()
                }
                val cloudUrl = if (SupabaseServices.isSupabaseAvailable && localFile != null) {
                    val uploaderId = SupabaseServices.Auth.getCurrentUserId()
                    if (uploaderId.isNullOrBlank()) null else {
                        withContext(Dispatchers.IO) {
                            SupabaseServices.Storage.uploadFileWithProgress(
                                bucketName = "avatars",
                                path = "user_avatars/$uploaderId/avatar.jpg",
                                file = localFile,
                                contentType = if (mime.startsWith("image/")) "image/jpeg" else mime
                            )
                        }
                    }
                } else null

                localFile?.delete()

                if (cloudUrl.isNullOrBlank()) {
                    Toast.makeText(context, "تعذر رفع الصورة إلى السحابة. لم يتم تغيير الأفاتار.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val profileMirrorSynced = SupabaseServices.Auth.getCurrentUserId()?.takeIf { it.isNotBlank() }?.let { activeUid ->
                    withContext(Dispatchers.IO) {
                        SupabaseServices.Profiles.syncUserProfile(
                            userId = activeUid,
                            displayName = displayName,
                            email = userEmail,
                            avatarUrl = cloudUrl
                        )
                    }
                } ?: false

                avatarUriString = cloudUrl
                selectedPresetAvatar = -1
                profilePrefs.edit().putString("user_avatar_uri", cloudUrl).putInt("user_avatar_preset", -1).apply()
                AccountIdentityManager.refresh(context)
                if (profileMirrorSynced) {
                    Toast.makeText(context, "تم رفع الصورة وحفظ رابطها في الملف السحابي", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "رُفعت الصورة، لكن تعذر مزامنة رابطها مع الملف السحابي", Toast.LENGTH_LONG).show()
                }
            }
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
            else -> "أسطورة خادم المسلم"
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
                    val currentPublished = socialAccounts.filter { it.isConnected }.sumOf { it.publishedCount }
                    val fallbackPoints = (projectsCount * 50) + (currentPublished * 20)
                    userLeaguePoints = if (userLeaguePoints > 0) userLeaguePoints else fallbackPoints
                    userLeagueRank = if (userLeagueRank > 0) userLeagueRank else (leaderboard.size + 1).coerceAtMost(99)
                }
            } else {
                val currentPublished = socialAccounts.filter { it.isConnected }.sumOf { it.publishedCount }
                val fallbackPoints = (projectsCount * 50) + (currentPublished * 20)
                if (userLeaguePoints == 0) userLeaguePoints = fallbackPoints
                if (userLeagueRank == 0) userLeagueRank = 1
            }
            prefs.edit()
                .putInt("user_league_points", userLeaguePoints)
                .putInt("user_league_rank", userLeagueRank)
                .apply()

            SupabaseServices.Auth.getCurrentUserId()?.takeIf { it.isNotBlank() }?.let { authUserId ->
                SupabaseServices.Profiles.syncUserProfile(
                    userId = authUserId,
                    displayName = displayName,
                    email = userEmail,
                    avatarUrl = avatarUriString.takeIf {
                        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
                    } ?: SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
                        ?: SupabaseServices.Auth.getCurrentUserMeta("picture")
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            // Keep cached values
        } finally {
            isLoadingLeague = false
        }
    }

    val tournamentBadges = remember(userLeaguePoints, userLeagueRank, theme) {
        listOf(
            TournamentTierBadge(
                id = "tier_bronze",
                title = "وسام الصاعد الواعد",
                tierTitle = "الرتبة البرونزية",
                emoji = "🥉",
                icon = Icons.Default.Shield,
                primaryColor = Color(0xFFB46927),
                accentColor = Color(0xFFD98A48),
                pointsRequired = 0,
                isUnlocked = true,
                isCurrentTier = userLeaguePoints < 500,
                description = "يُمنح لكل صانع محتوى يبدأ رحلته في دوري خادم المسلم الدعوي بنشر أولى بصماته المباركة.",
                perk = "مضاعف نقاط 1.0x والدخول في السجل الأسبوعي."
            ),
            TournamentTierBadge(
                id = "tier_silver",
                title = "وسام النجم الفضي",
                tierTitle = "الرتبة الفضية",
                emoji = "🥈",
                icon = Icons.Default.MilitaryTech,
                primaryColor = Color(0xFF64748B),
                accentColor = Color(0xFF94A3B8),
                pointsRequired = 500,
                isUnlocked = userLeaguePoints >= 500,
                isCurrentTier = userLeaguePoints in 500..1499,
                description = "يُمنح للصناع الذين اجتازوا 500 نقطة وأثبتوا حضورهم المستمر بجودة عالية.",
                perk = "أولوية معالجة الفيديوهات في السحابة ومضاعف نقاط 1.2x."
            ),
            TournamentTierBadge(
                id = "tier_gold",
                title = "وسام الصانع الذهبي",
                tierTitle = "الرتبة الذهبية",
                emoji = "🥇",
                icon = Icons.Default.EmojiEvents,
                primaryColor = theme.warning,
                accentColor = theme.warning,
                pointsRequired = 1500,
                isUnlocked = userLeaguePoints >= 1500,
                isCurrentTier = userLeaguePoints in 1500..3499,
                description = "وسام نخبوي للصناع المتميزين الذين حققوا تأثيراً مشهوداً عبر أكثر من 1,500 نقطة.",
                perk = "إطار ذهبي للصورة الشخصية ومضاعف نقاط 1.5x."
            ),
            TournamentTierBadge(
                id = "tier_diamond",
                title = "وسام الفارس الماسي",
                tierTitle = "الرتبة الماسية",
                emoji = "💎",
                icon = Icons.Default.Diamond,
                primaryColor = theme.primary,
                accentColor = theme.accent,
                pointsRequired = 3500,
                isUnlocked = userLeaguePoints >= 3500,
                isCurrentTier = userLeaguePoints in 3500..6999,
                description = "يُمنح لكبار صناع المحتوى المؤثرين الذين بلغوا 3,500 نقطة في ميادين الدعوة.",
                perk = "توليد مشاهد فورية غير محدودة وإبراز المشاريع في صدارة المنصات."
            ),
            TournamentTierBadge(
                id = "tier_master",
                title = "وسام سفير الدعوة",
                tierTitle = "رتبة السفير",
                emoji = "👑",
                icon = Icons.Default.Stars,
                primaryColor = PresetAccent,
                accentColor = PresetAccent,
                pointsRequired = 7000,
                isUnlocked = userLeaguePoints >= 7000,
                isCurrentTier = userLeaguePoints in 7000..11999,
                description = "أعلى أوسمة التقدير لصناع الأثر الذين حققوا أكثر من 7,000 نقطة بطولة.",
                perk = "شارة سفير موثقة بجانب الاسم والوصول إلى أحدث الأدوات التجريبية."
            ),
            TournamentTierBadge(
                id = "tier_legend",
                title = "وسام أسطورة خادم المسلم",
                tierTitle = "الرتبة الأسطورية",
                emoji = "🌟",
                icon = Icons.Default.AutoAwesome,
                primaryColor = theme.warning,
                accentColor = theme.warning,
                pointsRequired = 12000,
                isUnlocked = userLeaguePoints >= 12000,
                isCurrentTier = userLeaguePoints >= 12000,
                description = "تاج البطولة التنافسية الكبرى لمن تجاوزوا 12,000 نقطة من العطاء الاستثنائي.",
                perk = "تخليد الاسم في لوحة الشرف الخالدة."
            ),
            TournamentTierBadge(
                id = "rank_top10",
                title = "وسام فرسان الصدارة",
                tierTitle = "نخبة الـ 10 الكبار",
                emoji = "⚡",
                icon = Icons.Default.Leaderboard,
                primaryColor = theme.accent,
                accentColor = theme.primary,
                pointsRequired = 0,
                rankRequired = 10,
                isUnlocked = userLeagueRank in 1..10,
                isCurrentTier = userLeagueRank in 4..10,
                description = "يُمنح لمن يدخل قائمة أفضل 10 صناع محتوى في الدوري الأسبوعي المباشر.",
                perk = "شارة تميز تظهر في مجتمع خادم المسلم العام."
            ),
            TournamentTierBadge(
                id = "rank_top3",
                title = "وسام منصة التتويج",
                tierTitle = "فرسان التتويج الثلاثة",
                emoji = "🔥",
                icon = Icons.Default.WorkspacePremium,
                primaryColor = theme.destructive,
                accentColor = theme.warning,
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
                tierTitle = "بطل البطولة الأول",
                emoji = "👑",
                icon = Icons.Default.WorkspacePremium,
                primaryColor = theme.warning,
                accentColor = theme.primary,
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

    Scaffold(
        topBar = {
            AppleTopBar(
                title = Translator.tr("الملف الشخصي"),
                subtitle = userCustomTag,
                onBack = onBack,
                backTestTag = "profile_back_btn",
                actions = {
                    CloudSyncIndicator(isSyncing = isLoadingLeague, compact = true)
                    IconButton(
                        onClick = { onNavigate(AppState.SETTINGS) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("profile_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "إعدادات التطبيق", tint = theme.primary)
                    }
                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("profile_edit_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل الملف الشخصي", tint = theme.primary)
                    }
                }
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Apple Profile Identity Header Card
            AppleCard(cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .clickable { showAvatarPickerSheet = true }
                            .testTag("profile_avatar_btn"),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            modifier = Modifier.size(92.dp),
                            shape = CircleShape,
                            color = theme.secondarySurface,
                            border = BorderStroke(2.5.dp, theme.primary)
                        ) {
                            if (avatarUriString.isNotBlank()) {
                                AsyncImage(
                                    model = avatarUriString,
                                    contentDescription = "الصورة الشخصية",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(theme.primary.copy(alpha = 0.12f)),
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
                                        tint = theme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = theme.primary,
                            border = BorderStroke(2.dp, theme.cardSurface)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "تغيير الصورة",
                                    tint = theme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = displayName,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    val effectiveAccountNumber = userAccountNumber.takeIf { it.isNotBlank() }
                        ?: UserIdentifierManager.getAccountNumber(context, userEmail).takeIf { it.isNotBlank() }
                        ?: "بانتظار المزامنة..."

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = theme.primary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { UserIdentifierManager.copyToClipboard(context, userCustomTag) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = userCustomTag,
                                    color = theme.primary,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "نسخ المعرّف",
                                    tint = theme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Surface(
                            color = theme.warning.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    UserIdentifierManager.copyAccountNumberToClipboard(context, effectiveAccountNumber)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = theme.warning,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = UserIdentifierManager.formatAccountNumber(effectiveAccountNumber),
                                    color = theme.warning,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "نسخ رقم الحساب",
                                    tint = theme.warning,
                                    modifier = Modifier.size(12.dp)
                                )
                                if (UserIdentifierManager.isFirstAccount(effectiveAccountNumber)) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "👑", fontSize = 11.sp)
                                }
                            }
                        }

                        Surface(
                            color = theme.success.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "صانع موثق ✓",
                                color = theme.success,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = userBio,
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // 2. Supabase Cloud Sync Status
            val isSupabaseActive = SupabaseServices.isSupabaseAvailable
            AppleCard(cornerRadius = 14.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background((if (isSupabaseActive) theme.success else theme.warning).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSupabaseActive) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (isSupabaseActive) theme.success else theme.warning,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المزامنة السحابية عبر Supabase",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isSupabaseActive) "حسابك متصل ومزامن مع السحابة" else "التخزين محلي حالياً — Supabase غير مهيأ",
                            color = if (isSupabaseActive) theme.success else theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        color = (if (isSupabaseActive) theme.success else theme.textSecondary).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isSupabaseActive) "متصل" else "محلي",
                            color = if (isSupabaseActive) theme.success else theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Account & Security Settings
            AppleGroupedSection(header = "إعدادات الحساب والأمان") {
                AppleListRow(
                    title = "تعديل بيانات الملف الشخصي",
                    subtitle = "الاسم، المعرّف الفريد، والنبذة التعريفية",
                    icon = Icons.Default.Edit,
                    iconBackground = theme.primary,
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier.testTag("profile_row_edit")
                )
                AppleInsetDivider()
                AppleListRow(
                    title = "تغيير كلمة المرور",
                    subtitle = "تحديث كلمة المرور عبر Supabase",
                    icon = Icons.Default.LockReset,
                    iconBackground = PresetAccent,
                    onClick = { showChangePasswordDialog = true },
                    modifier = Modifier.testTag("profile_row_password")
                )
                AppleInsetDivider()
                AppleListRow(
                    title = "تبديل الحساب",
                    subtitle = userEmail.ifBlank { "إدارة الجلسات والحسابات" },
                    icon = Icons.Default.SwitchAccount,
                    iconBackground = theme.warning,
                    onClick = { showSwitchAccountDialog = true },
                    modifier = Modifier.testTag("profile_row_switch")
                )
            }

            // 4. Stats 2x2 Grid
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
                        color = theme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = Translator.tr("المشاركات"),
                        value = totalPublished.toString(),
                        icon = Icons.Default.Publish,
                        color = theme.accent
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
                        color = theme.success
                    )
                }
            }

            // 5. Supabase Cloud Saved Items Card (Fixed badge from Firestore HUD -> Supabase)
            AppleCard(
                cornerRadius = 16.dp,
                onClick = { onNavigate(AppState.SAVED_ITEMS) },
                modifier = Modifier.testTag("profile_saved_items_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(theme.success.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = theme.success,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "المحفوظات السحابية",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = theme.success.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Supabase",
                                    color = theme.success,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "مزامنة الآيات القرآنية والأذكار والمفضلات سحابياً",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "فتح المحفوظات",
                        tint = theme.textSecondary
                    )
                }
            }

            // 6. Qabas Reels & Community Showcase Card
            LaunchedEffect(Unit) { ReelPreferencesManager.init(context) }
            val reelStats = ReelPreferencesManager.userStats.collectAsState().value
            val reelSavedCount = if (hasCurrentProfileSnapshot) reelStats.totalSaved.toString() else "—"
            val reelWatchedCount = if (hasCurrentProfileSnapshot) reelStats.totalWatched.toString() else "—"
            val reelLikedCount = if (hasCurrentProfileSnapshot) reelStats.totalLikes.toString() else "—"
            val reelPublishedCount = if (hasCurrentProfileSnapshot) {
                profileSnapshot?.publishedReelsCount?.toString() ?: "—"
            } else "—"

            AppleCard(cornerRadius = 16.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SlowMotionVideo,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ريلز خادم المسلم",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "سجل مشاهداتك ومشاركاتك الإيمانية",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniMetricCell("المحفوظات", reelSavedCount, theme.primary, Modifier.weight(1f))
                        MiniMetricCell("المشاهدات", reelWatchedCount, theme.accent, Modifier.weight(1f))
                        MiniMetricCell("الإعجابات", reelLikedCount, theme.destructive, Modifier.weight(1f))
                        MiniMetricCell("المنشورات", reelPublishedCount, PresetAccent, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApplePrimaryButton(
                            text = "فتح الريلز",
                            icon = Icons.Default.PlayArrow,
                            onClick = { onNavigate(AppState.REELS) },
                            modifier = Modifier.weight(1f)
                        )
                        AppleSecondaryButton(
                            text = "المحفوظات",
                            icon = Icons.Default.Bookmarks,
                            onClick = { onNavigate(AppState.SAVED_ITEMS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 7. Creator Follow Community Card
            AppleCard(cornerRadius = 16.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "مجتمع المتابعة",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "اتصال مباشر بصنّاع المحتوى",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isLoadingFollowCommunity = true
                                    refreshFollowCommunity()
                                    isLoadingFollowCommunity = false
                                }
                            },
                            enabled = !isLoadingFollowCommunity,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "تحديث المتابعة",
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiniMetricCell(
                            label = "أتابع",
                            value = if (isLoadingFollowCommunity) "…" else profileFollowingCountText,
                            accent = theme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricCell(
                            label = "يتابعني",
                            value = if (isLoadingFollowCommunity) "…" else if (ownFollowers.isExact) "${ownFollowers.value}" else "—",
                            accent = theme.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AppleSecondaryButton(
                        text = if (showFollowList) "إخفاء قائمة المتابَعين" else "عرض قائمة المتابَعين ($profileFollowingCountText)",
                        icon = if (showFollowList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        onClick = { showFollowList = !showFollowList }
                    )

                    if (showFollowList) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (followedCreators.isEmpty()) {
                            val emptyFollowMessage = when {
                                !hasCurrentProfileSnapshot -> "تعذر تأكيد قائمة المتابعة الآن. حدّث الملف وحاول مجدداً."
                                profileSnapshot?.followingTruncated == true ->
                                    "لديك $profileFollowingCountText متابعة؛ المتابعات كاملة محفوظة سحابياً."
                                profileSnapshot?.followingCount == 0 ->
                                    "لم تتابع أي صانع محتوى بعد. افتح أي ملف منشئ من الريلز وابدأ المتابعة."
                                else -> "تعذر عرض عناصر قائمة المتابعة الآن؛ حدّث الملف وحاول مجدداً."
                            }
                            Text(
                                text = emptyFollowMessage,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                followedCreators.sortedByDescending { it.followedAt }.forEach { creator ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.secondarySurface.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = creator.avatarUrl ?: "https://ui-avatars.com/api/?name=${creator.displayName}&background=006EE6&color=FFFFFF",
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    creator.displayName,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = theme.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    creator.id,
                                                    fontFamily = RobotoMonoFont,
                                                    fontSize = 10.sp,
                                                    color = theme.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    val activeViewer = profileViewer ?: return@TextButton
                                                    if (followActionId != null) return@TextButton
                                                    followActionId = creator.id
                                                    coroutineScope.launch {
                                                        val applied = withContext(Dispatchers.IO) {
                                                            CreatorFollowManager.setFollowed(
                                                                context.applicationContext,
                                                                activeViewer,
                                                                creator.id,
                                                                creator.displayName,
                                                                creator.avatarUrl,
                                                                false
                                                            )
                                                        }
                                                        if (applied != null) {
                                                            followedCreators = CreatorFollowManager.followedCreators(
                                                                context.applicationContext
                                                            )
                                                            AppNotificationService.sendNotification(
                                                                context,
                                                                "ألغيت متابعة ${creator.displayName}",
                                                                "أُزيل من تبويب المتابَعين وقائمتك الشخصية."
                                                            )
                                                            Toast.makeText(context, "تم إلغاء المتابعة", Toast.LENGTH_SHORT).show()
                                                        }
                                                        followActionId = null
                                                    }
                                                },
                                                enabled = followActionId == null
                                            ) {
                                                Text(
                                                    if (followActionId == creator.id) "جارٍ..." else "إلغاء المتابعة",
                                                    fontFamily = CairoFont,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = theme.destructive
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. League Championship Points & Rank Hero Card
            AppleCard(
                cornerRadius = 16.dp,
                onClick = { onNavigate(AppState.LEADERBOARD) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(theme.warning.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = theme.warning,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "دوري صنّاع الأثر",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "لوحة الترتيب والنقاط الأسبوعية",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { showTournamentInfoSheet = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "دليل الدوري",
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RankHighlightCard(
                            label = "الترتيب الحالي",
                            value = if (isLoadingLeague) "..." else "#$userLeagueRank",
                            icon = Icons.Default.Leaderboard,
                            accent = theme.primary
                        )
                        RankHighlightCard(
                            label = "رصيد النقاط",
                            value = if (isLoadingLeague) "..." else "$userLeaguePoints نقطة",
                            icon = Icons.Default.Stars,
                            accent = theme.warning
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val nextMilestone = remember(userLeaguePoints) {
                        when {
                            userLeaguePoints < 500 -> Triple(0, 500, "صانع برونزي")
                            userLeaguePoints < 1500 -> Triple(500, 1500, "صانع فضي")
                            userLeaguePoints < 3500 -> Triple(1500, 3500, "صانع ذهبي")
                            userLeaguePoints < 7000 -> Triple(3500, 7000, "صانع ماسي")
                            userLeaguePoints < 12000 -> Triple(7000, 12000, "سفير الدعوة")
                            else -> Triple(12000, userLeaguePoints + 2000, "أسطورة خادم المسلم")
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
                        shape = RoundedCornerShape(12.dp),
                        color = theme.secondarySurface.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "الرتبة القادمة: $nextMilestoneName",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    "$progressPercent%",
                                    color = theme.primary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = theme.primary,
                                trackColor = theme.cardSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "متبقي $pointsNeeded نقطة",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "$userLeaguePoints / $targetPoints",
                                    color = theme.textSecondary,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApplePrimaryButton(
                            text = "صالة البطولة",
                            icon = Icons.Default.MilitaryTech,
                            onClick = { onNavigate(AppState.LEADERBOARD) },
                            modifier = Modifier.weight(1.2f)
                        )
                        AppleSecondaryButton(
                            text = "الاحتفال",
                            icon = Icons.Default.Celebration,
                            onClick = { showRankMilestoneCelebration = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 9. Social Platforms Section
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

            // 10. Badges Section
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
                        Box(modifier = Modifier.width(136.dp)) {
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

            // 11. Account Plan & Quota Section
            val accountService = remember { AppServices.getAccountService(context) }
            val planTitle = when {
                accountService.isDeveloperOrAdmin -> "باقة المطور"
                accountService.hasCustomKeys -> "المفاتيح الخاصة (BYOK)"
                accountService.isPremium -> "خادم المسلم برو"
                else -> "باقة مجانية"
            }
            val isUnlimited = accountService.isDeveloperOrAdmin || accountService.hasCustomKeys || accountService.isPremium

            ProfileSection(
                title = "باقة الحساب والرصيد",
                icon = Icons.Default.AccountBalanceWallet
            ) {
                AppleCard(cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Translator.tr("خطة الاشتراك الحالية"),
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Surface(
                                color = (if (isUnlimited) theme.primary else theme.textSecondary).copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = planTitle,
                                    color = if (isUnlimited) theme.primary else theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.secondarySurface.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الرصيد", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                Text("${accountService.walletBalance}", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            VerticalDivider(modifier = Modifier.height(24.dp), color = theme.separator)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("فيديوهات", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                Text(
                                    if (isUnlimited) "∞" else "${accountService.dailyVideoCount}/${AccountService.MAX_DAILY_VIDEOS}",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            VerticalDivider(modifier = Modifier.height(24.dp), color = theme.separator)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("صور", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                Text(
                                    if (isUnlimited) "∞" else "${accountService.dailyImageCount}/${AccountService.MAX_DAILY_IMAGES}",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        AppleSecondaryButton(
                            text = "المتجر والترقية",
                            icon = Icons.Default.ShoppingCart,
                            onClick = { onNavigate(AppState.PREMIUM_UPGRADE) }
                        )
                    }
                }
            }

            if (projectsCount == 0) {
                AppleCard(cornerRadius = 16.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = theme.primary, modifier = Modifier.size(38.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "ابدأ رحلتك الدعوية الأولى",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "حوّل أي فكرة إلى مقطع ريلز احترافي في ثوانٍ",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        ApplePrimaryButton(
                            text = "بدء مشروع جديد",
                            icon = Icons.Default.Add,
                            onClick = { onNavigate(AppState.INPUT) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogs & Sheets delegated to ProfileDialogsAndCards.kt
    if (showChangePasswordDialog) {
        ProfileChangePasswordDialog(
            userEmail = userEmail,
            onDismiss = { showChangePasswordDialog = false }
        )
    }

    if (showSwitchAccountDialog) {
        ProfileSwitchAccountDialog(
            displayName = displayName,
            userEmail = userEmail,
            onDismiss = { showSwitchAccountDialog = false },
            onNavigateToLogin = { onNavigate(AppState.LOGIN) }
        )
    }

    if (showEditProfileDialog) {
        ProfileEditDialog(
            userEmail = userEmail,
            currentName = displayName,
            currentTag = userCustomTag,
            currentBio = userBio,
            currentAccountNumber = userAccountNumber,
            onDismiss = { showEditProfileDialog = false },
            onProfileUpdated = { newName, newTag, newBio, accNum ->
                displayName = newName
                userCustomTag = newTag
                userBio = newBio
                userAccountNumber = accNum
                showEditProfileDialog = false
            }
        )
    }

    if (showAvatarPickerSheet) {
        ProfileAvatarPickerDialog(
            selectedPresetAvatar = selectedPresetAvatar,
            hasCustomUri = avatarUriString.isNotBlank(),
            onPickFromGallery = {
                showAvatarPickerSheet = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSelectPreset = { index ->
                selectedPresetAvatar = index
                avatarUriString = ""
                profilePrefs.edit().putString("user_avatar_uri", "").putInt("user_avatar_preset", index).apply()
                showAvatarPickerSheet = false
                SupabaseServices.Auth.getCurrentUserId()?.takeIf { it.isNotBlank() }?.let { userId ->
                    coroutineScope.launch(Dispatchers.IO) {
                        SupabaseServices.Profiles.syncUserProfile(
                            userId = userId,
                            displayName = displayName,
                            email = userEmail,
                            avatarUrl = ""
                        )
                        AccountIdentityManager.refresh(context)
                    }
                }
                Toast.makeText(context, "تم تعيين الرمز الشخصي", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAvatarPickerSheet = false }
        )
    }

    selectedEditAccount?.let { account ->
        SocialHandleEditDialog(
            account = account,
            handleText = editHandleText,
            onHandleChange = { editHandleText = it },
            onSave = {
                SocialAccountManager.toggleConnection(context, account.id, isConnected = true, handle = editHandleText)
                socialAccounts = SocialAccountManager.getAccounts(context)
                selectedEditAccount = null
                Toast.makeText(context, "تم حفظ الحساب بنجاح", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { selectedEditAccount = null }
        )
    }

    if (showScheduleDialog) {
        SmartPublishScheduleDialog(
            initialTitle = "جدولة نشر مقطع دعوي جديد",
            onDismiss = { showScheduleDialog = false }
        )
    }

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

    if (showTournamentInfoSheet) {
        TournamentInfoBottomSheet(onDismiss = { showTournamentInfoSheet = false })
    }
}

@Composable
private fun MiniMetricCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = theme.secondarySurface.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontFamily = CairoFont, fontSize = 10.5.sp, color = theme.textSecondary, maxLines = 1)
            Text(value, fontFamily = RobotoMonoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
