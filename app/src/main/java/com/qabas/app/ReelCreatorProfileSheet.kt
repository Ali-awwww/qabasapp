package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCreatorProfileSheet(
    creatorId: String,
    creatorName: String,
    creatorAvatar: String?,
    allReels: List<SupabaseServices.ReelRow>,
    onSelectReel: (String) -> Unit,
    onDismiss: () -> Unit,
    viewer: CreatorFollowManager.ViewerIdentity? = null,
    isGuest: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onOpenMyProfile: () -> Unit = {},
    onFollowChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    val resolvedViewer = remember(viewer) { viewer ?: CreatorFollowManager.currentViewer(context) }
    var community by remember { mutableStateOf<CreatorFollowManager.CreatorCommunity?>(null) }
    var isLoadingCommunity by remember { mutableStateOf(true) }
    var communityRefresh by remember { mutableIntStateOf(0) }
    var isUpdatingFollow by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDuaaDialog by remember { mutableStateOf(false) }

    LaunchedEffect(creatorId, creatorName, creatorAvatar, communityRefresh) {
        isLoadingCommunity = true
        community = withContext(kotlinx.coroutines.Dispatchers.IO) {
            CreatorFollowManager.loadCreatorCommunity(
                context.applicationContext,
                creatorId,
                creatorName,
                creatorAvatar
            )
        }
        isLoadingCommunity = false
    }

    val resolvedCommunity = community
    val displayName = resolvedCommunity?.displayName ?: creatorName.ifBlank { creatorId }
    val displayAvatar = resolvedCommunity?.avatarUrl ?: creatorAvatar
    val isOwnProfile = resolvedCommunity?.isOwnProfile
        ?: CreatorFollowManager.sameCanonicalUserId(creatorId, resolvedViewer.id)
    val isFollowing = resolvedCommunity?.isFollowing
        ?: CreatorFollowManager.isFollowing(context, creatorId)
    val followers = resolvedCommunity?.followers
        ?: CreatorFollowManager.FollowerCount(
            CreatorFollowManager.estimatedBaselineFollowers(creatorId.ifBlank { creatorName }) +
                if (CreatorFollowManager.isFollowing(context, creatorId) && !isOwnProfile) 1 else 0,
            isExact = false
        )
    val appPrefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val ownHandle = remember(resolvedViewer.email) {
        resolvedViewer.email?.let { UserIdentifierManager.getCustomId(context, it) }.orEmpty()
    }
    val ownBio = remember {
        appPrefs.getString("user_bio", "نشر الخير والقرآن الكريم")
            ?: "نشر الخير والقرآن الكريم"
    }
    val profileCustomId = if (isOwnProfile) {
        ownHandle.ifBlank { resolvedCommunity?.customId.orEmpty() }
    } else {
        resolvedCommunity?.customId.orEmpty()
    }.trim().removePrefix("@")
    val profileHandle = profileCustomId.takeIf { it.isNotBlank() }
        ?.let { "@$it" } ?: "يوزر غير متاح"

    val creatorReels = remember(allReels, creatorId) {
        allReels.filter { CreatorFollowManager.sameCanonicalUserId(it.userId, creatorId) }
    }
    val totalLikes = remember(creatorReels) { creatorReels.sumOf { it.likesCount } }
    val totalViews = remember(creatorReels) { creatorReels.sumOf { it.viewsCount } }
    val isDevCreator = remember(creatorId, displayName) {
        creatorId.contains("dev") || creatorId.contains("admin") || displayName.contains("خادم المسلم")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .fillMaxHeight(0.85f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(
                            "Muslim AI Assistant Creator",
                            "حساب $displayName على استوديو خادم المسلم: https://qabas.studio/u/$creatorId"
                        )
                        clipboard.setPrimaryClip(clip)
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم نسخ رابط ملف المنشئ", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة الملف", tint = theme.primary)
                }

                Text(
                    text = if (isOwnProfile) "ملفي الشخصي" else "ملف المنشئ",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = theme.textPrimary
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = displayAvatar ?: "https://ui-avatars.com/api/?name=$displayName&background=007AFF&color=FFFFFF",
                        contentDescription = displayName,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, theme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        shape = CircleShape,
                        color = theme.primary,
                        modifier = Modifier.size(22.dp).offset(x = 2.dp, y = 2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDevCreator) Icons.Default.Verified else Icons.Default.Check,
                                contentDescription = null,
                                tint = theme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CreatorStatItem(count = "${creatorReels.size}", label = "المقاطع")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CreatorStatItem(count = "${followers.value}", label = "المتابعون")
                        if (!followers.isExact) {
                            Text(
                                text = "تقدير",
                                fontFamily = NotoSansFont,
                                fontSize = 9.5.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                    CreatorStatItem(count = "$totalLikes", label = "الإعجابات")
                    CreatorStatItem(count = "$totalViews", label = "المشاهدات")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = theme.textPrimary
                )
                if (isDevCreator) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = theme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "مطور وموثق",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (isOwnProfile) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "حسابك",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isOwnProfile) {
                    "$profileHandle • $ownBio"
                } else {
                    "$profileHandle • منشئ محتوى إيماني في منصة خادم المسلم"
                },
                fontFamily = NotoSansFont,
                fontSize = 12.5.sp,
                color = theme.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isOwnProfile) {
                    ApplePrimaryButton(
                        text = "فتح ملفي الشخصي",
                        icon = Icons.Default.AccountCircle,
                        onClick = {
                            ReelPreferencesManager.triggerHaptic(context)
                            onDismiss()
                            onOpenMyProfile()
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Button(
                        onClick = {
                            if (isGuest) {
                                onRequireLogin()
                                return@Button
                            }
                            if (isUpdatingFollow) return@Button
                            val next = !isFollowing
                            isUpdatingFollow = true
                            scope.launch {
                                val applied = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    CreatorFollowManager.setFollowed(
                                        context.applicationContext,
                                        resolvedViewer,
                                        creatorId,
                                        displayName,
                                        displayAvatar,
                                        next
                                    )
                                }
                                if (applied == null) {
                                    Toast.makeText(context, "لا يمكنك متابعة نفسك", Toast.LENGTH_SHORT).show()
                                } else {
                                    val snapshot = community
                                    if (snapshot != null) {
                                        val currentFollowers = snapshot.followers
                                        val nextFollowers = if (currentFollowers.isExact) {
                                            currentFollowers.copy(
                                                value = (currentFollowers.value + if (applied) 1 else -1).coerceAtLeast(0)
                                            )
                                        } else {
                                            CreatorFollowManager.FollowerCount(
                                                CreatorFollowManager.estimatedBaselineFollowers(
                                                    creatorId.ifBlank { displayName }
                                                ) + if (applied) 1 else 0,
                                                isExact = false
                                            )
                                        }
                                        community = snapshot.copy(isFollowing = applied, followers = nextFollowers)
                                    }
                                    communityRefresh++
                                    ReelPreferencesManager.triggerHaptic(context)
                                    AppNotificationService.sendNotification(
                                        context,
                                        if (applied) "بدأت متابعة $displayName" else "ألغيت متابعة $displayName",
                                        if (applied) {
                                            "ستظهر مقاطعه في تبويب المتابَعين، ويمكنك إدارة المتابعة من ملفك الشخصي."
                                        } else {
                                            "أُزيل من تبويب المتابَعين، ويمكنك متابعته مجدداً في أي وقت."
                                        }
                                    )
                                    Toast.makeText(
                                        context,
                                        if (applied) "تمت متابعة $displayName بنجاح" else "تم إلغاء المتابعة",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onFollowChanged(applied)
                                }
                                isUpdatingFollow = false
                            }
                        },
                        enabled = !isUpdatingFollow,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) theme.secondarySurface else theme.primary,
                            contentColor = if (isFollowing) theme.textPrimary else theme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUpdatingFollow) {
                                "جارٍ التحديث..."
                            } else if (isFollowing) {
                                "تتم المتابعة"
                            } else {
                                "متابعة المنشئ"
                            },
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        showDuaaDialog = true
                        ReelPreferencesManager.triggerHaptic(context)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary)
                ) {
                    Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "دعاء", fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppleSegmentedControl(
                items = listOf(
                    0 to "المقاطع (${creatorReels.size})",
                    1 to "الأثر الإيماني"
                ),
                selectedItem = selectedTab,
                onSelect = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    if (creatorReels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد مقاطع منشورة أخرى لهذا المنشئ حالياً.",
                                fontFamily = NotoSansFont,
                                fontSize = 13.sp,
                                color = theme.textSecondary
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(creatorReels) { reel ->
                                Surface(
                                    onClick = {
                                        onSelectReel(reel.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.aspectRatio(0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = theme.secondarySurface
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (!reel.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = reel.thumbnailUrl,
                                                contentDescription = reel.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(theme.secondarySurface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.SlowMotionVideo,
                                                    null,
                                                    tint = theme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                                    )
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = reel.title,
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        null,
                                                        tint = Color(0xFFFF453A),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "${reel.likesCount}",
                                                        fontFamily = RobotoMonoFont,
                                                        fontSize = 9.sp,
                                                        color = Color.White.copy(alpha = 0.9f)
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
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppleCard(containerColor = theme.primary.copy(alpha = 0.08f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "مؤشرات النفع والأجر المتعدي",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = theme.textPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "«مَنْ دَلَّ عَلَى خَيْرٍ فَلَهُ مِثْلُ أَجْرِ فَاعِلِهِ» — يساهم المنشئ في تذكير القلوب بآيات الله وأحاديث رسوله ﷺ.",
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        if (totalViews >= 100 || totalLikes >= 10) {
                            Text(
                                text = "الأوسمة المكتسبة",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = theme.textPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (totalViews >= 100) {
                                    ImpactBadgeItem(title = "ناشر للخير", desc = "أكثر من 100 مشاهدة")
                                }
                                if (totalLikes >= 10) {
                                    ImpactBadgeItem(title = "صانع أثر", desc = "تفاعل مجتمعي إيجابي")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDuaaDialog) {
        val duaaOptions = listOf(
            "جزاك الله خيراً ونفع بك الأمة",
            "بارك الله في جهودك الطيبة وزادك علماً",
            "اللهم اجعل ما يقدمه صدقة جارية ونوراً في ميزان حسناته",
            "اللهم وفقه وسدده وافتح عليه فتوح العارفين"
        )
        AlertDialog(
            onDismissRequest = { showDuaaDialog = false },
            containerColor = theme.cardSurface,
            title = {
                Text(
                    text = "دعاء بظهر الغيب للمنشئ",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "اختر دعاءً تدعو به بظهر الغيب (يُحفظ في سجل دعواتك على هذا الجهاز):",
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        color = theme.textSecondary
                    )
                    duaaOptions.forEach { duaa ->
                        Surface(
                            onClick = {
                                appPrefs.edit().putString("last_creator_duaa_${creatorId}", duaa).apply()
                                showDuaaDialog = false
                                ReelPreferencesManager.triggerHaptic(context)
                                Toast.makeText(context, "تقبل الله دعاءك بظهر الغيب", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = theme.secondarySurface,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = duaa,
                                    fontFamily = NotoSansFont,
                                    fontSize = 13.sp,
                                    color = theme.textPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showDuaaDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إغلاق", fontFamily = CairoFont, color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ImpactBadgeItem(title: String, desc: String) {
    val theme = QabasTheme.current
    AppleCard(cornerRadius = 12.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = title, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = theme.primary)
            Text(text = desc, fontFamily = NotoSansFont, fontSize = 11.sp, color = theme.textSecondary)
        }
    }
}

@Composable
private fun CreatorStatItem(count: String, label: String) {
    val theme = QabasTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontFamily = RobotoMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = theme.textPrimary
        )
        Text(
            text = label,
            fontFamily = NotoSansFont,
            fontSize = 11.5.sp,
            color = theme.textSecondary
        )
    }
}

