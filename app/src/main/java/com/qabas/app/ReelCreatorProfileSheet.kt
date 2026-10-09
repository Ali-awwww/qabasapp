package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCreatorProfileSheet(
    creatorId: String,
    creatorName: String,
    creatorAvatar: String?,
    allReels: List<SupabaseServices.ReelRow>,
    onSelectReel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("qabas_creator_follows", Context.MODE_PRIVATE) }
    var isFollowing by remember { mutableStateOf(prefs.getBoolean("follow_$creatorId", false)) }
    var followersCount by remember { mutableIntStateOf(if (isFollowing) 129 else 128) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDuaaDialog by remember { mutableStateOf(false) }

    val creatorReels = remember(allReels, creatorId, creatorName) {
        allReels.filter { it.userId == creatorId || it.userName == creatorName }
    }

    val totalLikes = remember(creatorReels) {
        creatorReels.sumOf { it.likesCount }.coerceAtLeast(creatorReels.size * 14)
    }

    val totalViews = remember(creatorReels, totalLikes) {
        (totalLikes * 4.5).toInt().coerceAtLeast(120)
    }

    val isDevCreator = remember(creatorId, creatorName) {
        creatorId.contains("dev") || creatorId.contains("admin") || creatorName.contains("قبس")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .fillMaxHeight(0.85f)
        ) {
            // Header with Close and Share Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Qabas Creator", "حساب $creatorName على استوديو قبس الإيماني: https://qabas.studio/u/$creatorId")
                        clipboard.setPrimaryClip(clip)
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(context, "تم نسخ رابط ملف المنشئ 📋", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة الملف", tint = Color.White.copy(alpha = 0.7f))
                }

                Text(
                    text = "الملف الإيماني للمنشئ ✦",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Creator Hero Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with luxury ring
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = creatorAvatar ?: "https://ui-avatars.com/api/?name=$creatorName&background=D4AF37&color=0B0F19",
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, GoldPrimary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        shape = CircleShape,
                        color = GoldPrimary,
                        modifier = Modifier.size(22.dp).offset(x = 2.dp, y = 2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDevCreator) Icons.Default.Verified else Icons.Default.Check,
                                contentDescription = null,
                                tint = DeepSlate,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Stats Row
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CreatorStatItem(count = "${creatorReels.size.coerceAtLeast(1)}", label = "المقاطع")
                    CreatorStatItem(count = "$followersCount", label = "المتابعون")
                    CreatorStatItem(count = "$totalLikes", label = "الإعجابات")
                    CreatorStatItem(count = "$totalViews", label = "المشاهدات")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Creator Name and Bio
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = creatorName,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp,
                    color = Color.White
                )
                if (isDevCreator) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "مطور وموثق 👑",
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = "@${creatorId.take(14)} • منشئ محتوى إيماني معتمد في منصة قبس 🌿",
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Follow & Duaa Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val next = !isFollowing
                        isFollowing = next
                        followersCount = if (next) followersCount + 1 else (followersCount - 1).coerceAtLeast(0)
                        prefs.edit().putBoolean("follow_$creatorId", next).apply()
                        ReelPreferencesManager.triggerHaptic(context)
                        Toast.makeText(
                            context,
                            if (next) "تمت متابعة $creatorName بنجاح 🔔" else "تم إلغاء المتابعة",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) Color.White.copy(alpha = 0.15f) else GoldPrimary,
                        contentColor = if (isFollowing) Color.White else DeepSlate
                    )
                ) {
                    Icon(
                        imageVector = if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFollowing) "تتم المتابعة 🔔" else "متابعة المنشئ",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }

                // Duaa button
                OutlinedButton(
                    onClick = {
                        showDuaaDialog = true
                        ReelPreferencesManager.triggerHaptic(context)
                    },
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                ) {
                    Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "دعاء 🤲", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Selector: [ المقاطع | الأثر الإيماني | معلومات ]
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = GoldPrimary,
                divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "المقاطع (${creatorReels.size})",
                            fontFamily = CairoFont,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.5.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "الأثر الإيماني ✨",
                            fontFamily = CairoFont,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.5.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // Grid of creator reels
                    if (creatorReels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد مقاطع منشورة أخرى لهذا المنشئ حالياً.",
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
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
                                    modifier = Modifier
                                        .aspectRatio(0.65f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectReel(reel.id)
                                            onDismiss()
                                        },
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (!reel.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = reel.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.SlowMotionVideo,
                                                    null,
                                                    tint = GoldPrimary.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        // Bottom Shadow & Title
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                    )
                                                )
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = reel.title,
                                                    fontFamily = CairoFont,
                                                    fontSize = 9.5.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        null,
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "${reel.likesCount}",
                                                        fontFamily = RobotoMonoFont,
                                                        fontSize = 8.5.sp,
                                                        color = Color.White.copy(alpha = 0.8f)
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
                    // Spiritual Impact & Badges Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "مؤشرات النفع والأجر المتعدي",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = GoldPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "«مَنْ دَلَّ عَلَى خَيْرٍ فَلَهُ مِثْلُ أَجْرِ فَاعِلِهِ» — ساهم هذا المنشئ في تذكير القلوب بآيات الله وأحاديث رسوله ﷺ.",
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Badges list
                        Text(
                            text = "الأوسمة التقديرية 🎖️",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImpactBadgeItem(title = "ناشر للخير 🕊️", desc = "أكثر من 100 مشاهدة")
                            ImpactBadgeItem(title = "صانع أثر 🌿", desc = "تفاعل إيماني متميز")
                        }
                    }
                }
            }
        }
    }

    if (showDuaaDialog) {
        val duaaOptions = listOf(
            "جزاك الله خيراً ونفع بك الأمة 🤲",
            "بارك الله في جهودك الطيبة وزادك علماً 🤍",
            "اللهم اجعل ما يقدمه صدقة جارية ونوراً في ميزان حسناته ✨",
            "اللهم وفقه وسدده وافتح عليه فتوح العارفين 🌿"
        )
        AlertDialog(
            onDismissRequest = { showDuaaDialog = false },
            title = {
                Text(
                    text = "إهداء دعاء للمنشئ 🤲",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "اختر دعاءً طيباً ليصل للمنشئ تعزيزاً للأخوة الإيمانية:",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    duaaOptions.forEach { duaa ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDuaaDialog = false
                                    ReelPreferencesManager.triggerHaptic(context)
                                    Toast.makeText(context, "تم إرسال الدعاء للمنشئ، بارك الله فيك 🤲✨", Toast.LENGTH_LONG).show()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = duaa,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDuaaDialog = false }) {
                    Text("إغلاق", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = DeepSlate
        )
    }
}

@Composable
private fun ImpactBadgeItem(title: String, desc: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text = title, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = GoldPrimary)
            Text(text = desc, fontFamily = CairoFont, fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun CreatorStatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontFamily = RobotoMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White
        )
        Text(
            text = label,
            fontFamily = CairoFont,
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
