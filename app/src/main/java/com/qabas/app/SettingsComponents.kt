package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*

@Composable
fun SubScreenScaffold(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (ColumnScope.() -> Unit)
) {
    val theme = QabasTheme.current
    Scaffold(
        topBar = {
            AppleTopBar(
                title = Translator.tr(title),
                onBack = onBack
            )
        },
        bottomBar = bottomBar,
        containerColor = theme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    val theme = QabasTheme.current
    AppleCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Translator.tr(title),
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Translator.tr(subtitle),
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = theme.textSecondary.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (content != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
fun LanguageSelectionSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val currentLang by UserPreferencesManager.appLanguage.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("ar", "العربية (Arabic)", "🇪🇬"),
            Triple("en", "English (الإنجليزية)", "🇺🇸")
        ).forEach { (langCode, langName, flag) ->
            val isSelected = currentLang == langCode
            Surface(
                onClick = {
                    UserPreferencesManager.setAppLanguage(context, langCode)
                    AppSoundManager.playSuccess()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.secondarySurface.copy(alpha = 0.55f),
                border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(flag, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            langName,
                            color = if (isSelected) theme.primary else theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = theme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FontSelectionSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val selectedFont = FontManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "اختر الخط الذي يناسب استخدامك. سيتغير شكل واجهة التطبيق فورًا ويُحفظ الاختيار تلقائيًا.",
            color = theme.textSecondary,
            fontFamily = selectedFont.family,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        AppFontChoice.entries.forEach { choice ->
            val isSelected = choice == selectedFont
            Surface(
                onClick = {
                    FontManager.apply(context, choice)
                    AppSoundManager.playSuccess()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.secondarySurface.copy(alpha = 0.55f),
                border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            choice.title,
                            color = if (isSelected) theme.primary else theme.textPrimary,
                            fontFamily = choice.family,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            choice.description,
                            color = theme.textSecondary,
                            fontFamily = choice.family,
                            fontSize = 11.5.sp
                        )
                        Text(
                            "خادم المسلم — نموذج لوضوح القراءة العربية",
                            color = theme.textPrimary.copy(alpha = 0.8f),
                            fontFamily = choice.family,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = theme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AiIdentitySection(onNavigateToTasteProfile: () -> Unit) {
    AppleSecondaryButton(
        text = Translator.tr("تخصيص محرك الذوق (Taste Engine)"),
        icon = Icons.Default.Psychology,
        onClick = onNavigateToTasteProfile
    )
}

@Composable
fun CommunityAndLinksSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current

    fun openUrlSafely(url: String, toastSuccessMsg: String) {
        AppSoundManager.playToggle()
        if (QabasLinkOpener.openUrl(context, url, toastSuccessMsg)) {
            Toast.makeText(context, toastSuccessMsg, Toast.LENGTH_SHORT).show()
        }
    }

    val communityLinks = listOf(
        CommunityLinkItem(
            title = "مجتمع خادم المسلم الرسمي • فيسبوك",
            subtitle = "الصفحة الرسمية لمتابعة الجديد والمشاركات",
            url = "https://www.facebook.com/share/18p5cMSJ17/",
            icon = Icons.Default.Public,
            color = Color(0xFF1877F2),
            badge = "فيسبوك"
        ),
        CommunityLinkItem(
            title = "ملتقى المبدعين • واتساب",
            subtitle = "مجتمع صُنّاع المحتوى لتبادل الخبرات",
            url = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=sh&p=a&mlu=4&ilr=4&iam=2",
            icon = Icons.Default.Forum,
            color = Color(0xFF25D366),
            badge = "واتساب"
        ),
        CommunityLinkItem(
            title = "الموقع الرسمي • خادم المسلم",
            subtitle = "بوابة خادم المسلم الرسمية للتحميل والأخبار",
            url = "https://alippappa7-maker.github.io/Website-/",
            icon = Icons.Default.Language,
            color = theme.primary,
            badge = "الموقع"
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        communityLinks.forEach { item ->
            Surface(
                onClick = { openUrlSafely(item.url, "جاري فتح ${item.title}...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = theme.secondarySurface.copy(alpha = 0.55f),
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(item.color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            Translator.tr(item.title),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            Translator.tr(item.subtitle),
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            AppSoundManager.playToggle()
                            clipboardManager.setText(AnnotatedString(item.url))
                            Toast.makeText(context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرابط", tint = theme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private data class CommunityLinkItem(
    val title: String,
    val subtitle: String,
    val url: String,
    val icon: ImageVector,
    val color: Color,
    val badge: String
)

@Composable
fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = QabasTheme.current.primary,
    accentColor: Color = QabasTheme.current.primary,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Translator.tr(title),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!badge.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = badgeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = Translator.tr(badge),
                                    color = badgeColor,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Translator.tr(subtitle),
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "فتح",
                tint = theme.textSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsGroupHeader(title: String) {
    val theme = QabasTheme.current
    Text(
        text = Translator.tr(title),
        color = theme.textSecondary,
        fontFamily = CairoFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AppleSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = "البحث في الإعدادات...",
        modifier = modifier
    )
}

@Composable
fun SettingsQuickActions(
    onClearCache: () -> Unit,
    onSyncCloud: () -> Unit,
    onCheckUpdate: () -> Unit,
    isSyncing: Boolean,
    isCheckingUpdate: Boolean,
    isDownloadingUpdate: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionItem(
            icon = Icons.Default.DeleteSweep,
            label = "تنظيف الكاش",
            onClick = onClearCache,
            modifier = Modifier.weight(1f)
        )
        QuickActionItem(
            icon = Icons.Default.CloudSync,
            label = if (isSyncing) "جاري المزامنة..." else "مزامنة سحابية",
            onClick = onSyncCloud,
            enabled = !isSyncing,
            modifier = Modifier.weight(1f)
        )
        QuickActionItem(
            icon = Icons.Default.Update,
            label = when {
                isCheckingUpdate -> "جاري الفحص..."
                isDownloadingUpdate -> "جاري التنزيل..."
                else -> "تحديثات"
            },
            onClick = onCheckUpdate,
            enabled = !isCheckingUpdate && !isDownloadingUpdate,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = modifier.heightIn(min = 76.dp),
        onClick = if (enabled) onClick else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) theme.primary else theme.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                color = if (enabled) theme.textPrimary else theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProfileSettingsCard(
    savedDisplayName: String,
    userEmail: String,
    avatarUriString: String,
    selectedPresetAvatar: Int,
    isAdmin: Boolean,
    isGuest: Boolean,
    isRelative: Boolean,
    onUpgrade: () -> Unit,
    onAvatarClick: () -> Unit,
    context: Context
) {
    val theme = QabasTheme.current
    AppleCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onAvatarClick,
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color = theme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, theme.primary.copy(alpha = 0.45f))
                ) {
                    if (avatarUriString.isNotBlank()) {
                        AsyncImage(
                            model = avatarUriString,
                            contentDescription = "صورة الملف الشخصي",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (selectedPresetAvatar) {
                                    1 -> Icons.Default.AutoAwesome
                                    2 -> Icons.Default.MovieFilter
                                    3 -> Icons.Default.MenuBook
                                    4 -> Icons.Default.Psychology
                                    else -> Icons.Default.Person
                                },
                                contentDescription = "صورة رمزية",
                                tint = theme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = savedDisplayName,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = userEmail,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )

                    val customId = remember(userEmail, isGuest) {
                        if (isGuest) "guest" else UserIdentifierManager.getCustomId(context, userEmail)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { UserIdentifierManager.copyToClipboard(context, customId) },
                            color = theme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "المعرّف: ${UserIdentifierManager.formatDisplay(customId)}",
                                    color = theme.primary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ المعرف", tint = theme.primary, modifier = Modifier.size(12.dp))
                            }
                        }

                        Surface(
                            color = if (isAdmin || isRelative) theme.warning.copy(alpha = 0.16f) else theme.secondarySurface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = when {
                                    isAdmin -> Translator.tr("مطور النظام")
                                    isRelative -> Translator.tr("حساب الاكتفاء الذاتي")
                                    isGuest -> Translator.tr("زائر")
                                    else -> Translator.tr("عضو خادم المسلم")
                                },
                                color = if (isAdmin || isRelative) theme.warning else theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (!isAdmin && !isGuest && !isRelative) {
                    IconButton(onClick = onUpgrade, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = "ترقية", tint = theme.warning, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
