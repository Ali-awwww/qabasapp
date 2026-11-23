package com.qabas.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    onBack: () -> Unit,
    onAudioSelected: (String) -> Unit = {},
    onApplyToProject: (audioPath: String, usageType: String) -> Unit = { path, _ -> onAudioSelected(path) }
) {
    var selectedTab by remember { mutableStateOf("library") } // "library", "ai_voice", "supabase", "saved"
    val context = LocalContext.current
    val theme = QabasTheme.current

    val activePlayingTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val activePlayingUrl = activePlayingTrack?.audioUrl

    var showApplyDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    var activeDownloadTrackTitle by remember { mutableStateOf<String?>(null) }
    var activeDownloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadedAudioTitles by remember { mutableStateOf<Set<String>>(emptySet()) }

    val coroutineScope = rememberCoroutineScope()

    fun playTrack(audioUrl: String, title: String, youtubeUrl: String? = null, artist: String = "خادم المسلم") {
        if (activePlayingTrack?.audioUrl == audioUrl) {
            AudioPlaybackManager.playPause(context)
            return
        }
        val item = PlayableAudioTrack(
            id = audioUrl,
            title = title,
            artist = artist.ifBlank { "خادم المسلم" },
            audioUrl = audioUrl,
            category = "صوتيات خادم المسلم",
            youtubeUrl = youtubeUrl
        )
        AudioPlaybackManager.playTrack(context, item)
    }

    fun triggerDownload(url: String, title: String) {
        activeDownloadJob?.cancel()
        activeDownloadTrackTitle = title
        activeDownloadProgress = DownloadProgress(percent = 0.05f)
        activeDownloadJob = coroutineScope.launch {
            try {
                AudioDownloadHelper.downloadAudioWithProgress(
                    context = context,
                    audioUrl = url,
                    title = title
                ) { p ->
                    activeDownloadProgress = p
                    if (p.isCompleted) downloadedAudioTitles = downloadedAudioTitles + title
                }
            } finally {
                activeDownloadJob = null
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.AUDIO_LIBRARY_SCREEN),
        topBar = {
            Column(modifier = Modifier.background(theme.background)) {
                AppleTopBar(
                    title = Translator.tr("المكتبة الصوتية"),
                    subtitle = "التلاوات والدروس والتعليق الصوتي",
                    onBack = onBack
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    AppleSegmentedControl(
                        items = listOf(
                            "library" to "المكتبة",
                            "ai_voice" to "تعليق AI",
                            "supabase" to "السحابة",
                            "saved" to "المحفوظات"
                        ),
                        selectedItem = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }
        },
        bottomBar = {
            ProAudioPlayerBar(
                onApplyToProject = { url, title ->
                    showApplyDialog = Pair(url, title)
                }
            )
        },
        containerColor = theme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                "library" -> {
                    AudioLibrarySection(
                        activePlayingUrl = activePlayingUrl,
                        isPlaying = isPlaying,
                        onPlayTrack = { url, title, ytUrl, author -> playTrack(url, title, ytUrl, author) },
                        onApply = { url, title -> showApplyDialog = Pair(url, title) },
                        onDownloadTrack = { url, title -> triggerDownload(url, title) },
                        downloadedAudioTitles = downloadedAudioTitles,
                        onDownloadedTitlesFound = { found -> downloadedAudioTitles = downloadedAudioTitles + found },
                        heroBanner = {
                            SectionHeroBanner(
                                title = "المكتبة الصوتية المركزية",
                                description = "مجموعة مختارة من التلاوات، الدروس، والمؤثرات الصوتية عالية الجودة لنشر الأثر.",
                                imageRes = R.drawable.hud_audio_lab_banner_1790878245000,
                                accentColor = theme.primary
                            )
                        }
                    )
                }
                "supabase" -> {
                    SupabaseAudioSection(
                        activePlayingUrl = activePlayingUrl,
                        isPlaying = isPlaying,
                        onPlayTrack = { url, title -> playTrack(url, title) },
                        onApply = { url, title -> showApplyDialog = Pair(url, title) },
                        heroBanner = {
                            SectionHeroBanner(
                                title = "سحابة خادم المسلم (Supabase)",
                                description = "مزامنة سحابية فورية لجميع تسجيلاتك المرفوعة والمولدة، متاحة من أي مكان.",
                                imageRes = R.drawable.hud_cloud_storage_banner_1790878859957,
                                accentColor = theme.success
                            )
                        }
                    )
                }
                "ai_voice" -> {
                    AIVoiceoverSection(
                        onPlayGenerated = { path, title -> playTrack(path, title) },
                        onApply = { path, title -> showApplyDialog = Pair(path, title) },
                        heroBanner = {
                            SectionHeroBanner(
                                title = "استوديو التعليق الصوتي",
                                description = "حوّل نصوصك إلى تعليق صوتي واضح باستخدام المحركات السحابية أو النطق المحلي.",
                                imageRes = R.drawable.hud_voice_synth_banner_1790878874174,
                                accentColor = theme.accent
                            )
                        }
                    )
                }
                "saved" -> {
                    SavedAudioTracksSection(
                        activePlayingUrl = activePlayingUrl,
                        isPlaying = isPlaying,
                        onPlayTrack = { url, title -> playTrack(url, title) },
                        onApply = { url, title -> showApplyDialog = Pair(url, title) },
                        onDownloadTrack = { url, title -> triggerDownload(url, title) },
                        heroBanner = {
                            SectionHeroBanner(
                                title = "الأرشيف والمحفوظات",
                                description = "سجل كامل بجميع التعليقات الصوتية والتلاوات التي قمت بحفظها محلياً.",
                                fallbackEmoji = "💾",
                                accentColor = theme.primary
                            )
                        }
                    )
                }
            }
        }
    }

    if (activeDownloadTrackTitle != null && activeDownloadProgress != null) {
        RealDownloadProgressDialog(
            trackTitle = activeDownloadTrackTitle!!,
            progress = activeDownloadProgress!!,
            onCancelOrDismiss = {
                if (activeDownloadJob?.isActive == true) activeDownloadJob?.cancel()
                activeDownloadJob = null
                activeDownloadTrackTitle = null
                activeDownloadProgress = null
            }
        )
    }

    if (showApplyDialog != null) {
        val (audioPath, trackTitle) = showApplyDialog!!
        AlertDialog(
            onDismissRequest = { showApplyDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "اعتماد الصوت في المشروع",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "كيف ترغب في تطبيق \"$trackTitle\" على مشروعك الحالي؟",
                        fontFamily = NotoSansFont,
                        color = theme.textSecondary,
                        fontSize = 14.sp
                    )

                    AppleSecondaryButton(
                        text = "تعيين كتعليق صوتي رئيسي",
                        icon = Icons.Default.RecordVoiceOver,
                        onClick = {
                            onApplyToProject(audioPath, "voiceover")
                            Toast.makeText(context, "تم تعيين الصوت كتعليق صوتي رئيسي للمشروع", Toast.LENGTH_SHORT).show()
                            showApplyDialog = null
                            onBack()
                        }
                    )

                    AppleSecondaryButton(
                        text = "تعيين كخلفية صوتية ومؤثر",
                        icon = Icons.Default.MusicNote,
                        tint = theme.accent,
                        onClick = {
                            onApplyToProject(audioPath, "ambient")
                            Toast.makeText(context, "تم تعيين الصوت كخلفية ومؤثر محيطي للمشروع", Toast.LENGTH_SHORT).show()
                            showApplyDialog = null
                            onBack()
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showApplyDialog = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun AudioLibrarySection(
    activePlayingUrl: String?,
    isPlaying: Boolean,
    onPlayTrack: (url: String, title: String, youtubeUrl: String?, author: String) -> Unit,
    onApply: (url: String, title: String) -> Unit,
    onDownloadTrack: (url: String, title: String) -> Unit = { _, _ -> },
    downloadedAudioTitles: Set<String> = emptySet(),
    onDownloadedTitlesFound: (Set<String>) -> Unit = {},
    heroBanner: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    val audioTrackDao = remember { AppDatabase.getDatabase(context).audioTrackDao() }
    val savedTracks by audioTrackDao.getAllAudioTracks().collectAsState(initial = emptyList())

    var cloudTracks by remember { mutableStateOf<List<SupabaseServices.AudioTrackRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var favoriteTrackIds by remember {
        mutableStateOf(
            context.getSharedPreferences("qabas_audio_favorites", Context.MODE_PRIVATE)
                .getStringSet("favorite_audio_ids", emptySet())?.toSet().orEmpty()
        )
    }

    val categories = remember {
        listOf(
            "الكل",
            "القرآن الكريم",
            "الخطب المنبرية",
            "المحاضرات الإسلامية",
            "الدروس العلمية",
            "الأذكار والأدعية",
            "الأناشيد الإسلامية",
            "القصص الإسلامية",
            "صوتيات الأطفال",
            "السيرة النبوية",
            "الكتب الصوتية"
        )
    }
    var showCategoryMenu by remember { mutableStateOf(false) }

    fun loadRealTracks() {
        isLoading = true
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    SupabaseServices.Database.getAudioTracks()
                }
                cloudTracks = tracks
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                cloudTracks = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRealTracks()
    }

    val allRealTracks = remember(cloudTracks, savedTracks) {
        val list = mutableListOf<LiveAudioItem>()
        list.addAll(SamirMustafaAqaidAlBalaaData.toLiveAudioItems())

        cloudTracks.forEach { ct ->
            if (list.none { it.id == ct.id || it.audioUrl == ct.audioUrl }) {
                list.add(
                    LiveAudioItem(
                        id = ct.id,
                        title = ct.title,
                        category = ct.category,
                        duration = ct.duration,
                        audioUrl = ct.audioUrl,
                        isAsset = false,
                        badge = ct.badge.ifBlank { "سحابي" },
                        author = ct.artist.ifBlank { ct.author }
                    )
                )
            }
        }
        savedTracks.forEach { st ->
            if (list.none { it.id == st.id || it.audioUrl == st.audioUrlOrPath }) {
                list.add(
                    LiveAudioItem(
                        id = st.id,
                        title = st.title,
                        category = when (st.typeCategory) {
                            "RECITATION" -> "تلاوات قرآنية"
                            "NASHEED" -> "أناشيد (بدون موسيقى)"
                            else -> st.typeCategory
                        },
                        duration = st.duration,
                        audioUrl = st.audioUrlOrPath,
                        isAsset = false,
                        badge = "محلي",
                        author = st.artistOrVibe
                    )
                )
            }
        }
        list
    }

    fun matchesAudioCategory(track: LiveAudioItem, requested: String): Boolean {
        if (requested == "الكل") return true
        if (requested == "المفضلة ★") return track.id in favoriteTrackIds
        val source = (track.category + " " + track.title + " " + track.author).lowercase(Locale.ROOT)
        return when (requested) {
            "القرآن الكريم" -> listOf("قرآن", "تلاوة", "تلاوات", "recitation", "quran").any { source.contains(it) }
            "الخطب المنبرية" -> listOf("خطبة", "خطب", "منبر", "sermon", "khutbah").any { source.contains(it) }
            "المحاضرات الإسلامية" -> listOf("محاضرة", "محاضرات", "lecture").any { source.contains(it) }
            "الدروس العلمية" -> listOf("درس", "دروس", "علمي", "عقائد", "العقائد", "شرح", "lesson", "aqaid").any { source.contains(it) }
            "الأذكار والأدعية" -> listOf("ذكر", "أذكار", "اذكار", "دعاء", "أدعية", "ادعية", "dhikr", "dua").any { source.contains(it) }
            "الأناشيد الإسلامية" -> listOf("نشيد", "أناشيد", "اناشيد", "nasheed").any { source.contains(it) }
            "القصص الإسلامية" -> listOf("قصة", "قصص", "حكاية", "قصصي", "story").any { source.contains(it) }
            "صوتيات الأطفال" -> listOf("طفل", "أطفال", "اطفال", "children", "kids").any { source.contains(it) }
            "السيرة النبوية" -> listOf("السيرة", "سيرة", "النبي", "نبوي", "seerah").any { source.contains(it) }
            "الكتب الصوتية" -> listOf("كتاب صوتي", "كتب صوتية", "audiobook").any { source.contains(it) }
            else -> false
        }
    }

    LaunchedEffect(allRealTracks) {
        val foundTitles = withContext(Dispatchers.IO) {
            AudioDownloadHelper.getDownloadedAudioTitles(context, allRealTracks.map { it.title })
        }
        onDownloadedTitlesFound(foundTitles)
    }

    val filteredTracks by remember(allRealTracks, selectedCategory, searchQuery, favoriteTrackIds) {
        derivedStateOf {
            allRealTracks.filter { track ->
                val matchCategory = matchesAudioCategory(track, selectedCategory)
                val matchQuery = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.category.contains(searchQuery, ignoreCase = true) ||
                    track.author.contains(searchQuery, ignoreCase = true)
                matchCategory && matchQuery
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        heroBanner?.invoke()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppleSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "ابحث في الدروس والتلاوات...",
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SEARCH_FIELD)
            )

            Box {
                Surface(
                    onClick = { showCategoryMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = theme.secondarySurface,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = "التصنيفات",
                            tint = theme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (selectedCategory == "الكل") "التصنيفات" else selectedCategory,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false },
                    modifier = Modifier.background(theme.cardSurface)
                ) {
                    (categories + "المفضلة ★").forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category, fontFamily = CairoFont, color = theme.textPrimary) },
                            onClick = {
                                selectedCategory = category
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = { loadRealTracks() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.secondarySurface)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "مزامنة وتحديث",
                    tint = theme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(34.dp))
                    Text(
                        "جاري جلب الصوتيات والدروس من سحابة Supabase...",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                AppleCard(cornerRadius = 18.dp) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Text(
                            text = "المكتبة الصوتية والدروس العلمية",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategory != "الكل") {
                                "لا توجد نتائج مطابقة لبحثك في الصوتيات المرفوعة."
                            } else {
                                "القسم جاهز لعرض الصوتيات والدروس العلمية من سحابة Supabase."
                            },
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        AppleSecondaryButton(
                            text = "إعادة المزامنة",
                            icon = Icons.Default.Refresh,
                            onClick = { loadRealTracks() }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    val isTrackPlaying = isPlaying && activePlayingUrl == track.audioUrl
                    AppleCard(
                        cornerRadius = 16.dp,
                        containerColor = if (isTrackPlaying) theme.primary.copy(alpha = if (theme.isDark) 0.14f else 0.08f) else theme.cardSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onPlayTrack(track.audioUrl, track.title, track.youtubeUrl, track.author) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isTrackPlaying) theme.primary else theme.secondarySurface)
                                ) {
                                    Icon(
                                        imageVector = if (isTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isTrackPlaying) "إيقاف" else "تشغيل",
                                        tint = if (isTrackPlaying) Color.White else theme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = track.title,
                                            color = if (isTrackPlaying) theme.primary else theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (track.badge.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = theme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = track.badge,
                                                    color = theme.primary,
                                                    fontSize = 10.sp,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = track.author.ifBlank { track.category },
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("•", color = theme.textSecondary, fontSize = 10.sp)
                                        Text(
                                            text = track.duration,
                                            color = theme.primary,
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = theme.secondarySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = track.category,
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val nextFavorites = if (track.id in favoriteTrackIds) favoriteTrackIds - track.id else favoriteTrackIds + track.id
                                            favoriteTrackIds = nextFavorites
                                            context.getSharedPreferences("qabas_audio_favorites", Context.MODE_PRIVATE)
                                                .edit().putStringSet("favorite_audio_ids", nextFavorites.toMutableSet()).apply()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            if (track.id in favoriteTrackIds) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (track.id in favoriteTrackIds) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                            tint = if (track.id in favoriteTrackIds) theme.warning else theme.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val ytUrl = if (!track.youtubeUrl.isNullOrBlank()) {
                                                track.youtubeUrl
                                            } else {
                                                "https://www.youtube.com/results?search_query=${Uri.encode("${track.title} ${track.author}")}"
                                            }
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "تعذر فتح يوتيوب", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.OndemandVideo,
                                            contentDescription = "مشاهدة عبر يوتيوب",
                                            tint = theme.destructive,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { shareAudioTrack(context, track) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "مشاركة",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDownloadTrack(track.audioUrl, track.title) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            if (track.title in downloadedAudioTitles) Icons.Default.FileDownloadDone else Icons.Default.FileDownload,
                                            contentDescription = if (track.title in downloadedAudioTitles) "تم تنزيل الصوت" else "تنزيل للهاتف",
                                            tint = theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Button(
                                        onClick = { onApply(track.audioUrl, track.title) },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اعتماد", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIVoiceoverSection(
    onPlayGenerated: (path: String, title: String) -> Unit,
    onApply: (path: String, title: String) -> Unit,
    heroBanner: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    var scriptText by remember { mutableStateOf("") }
    var selectedVoiceName by remember { mutableStateOf("ar-SA-HamedNeural") }
    var selectedTone by remember { mutableStateOf("وثائقي") }
    var speakingRate by remember { mutableStateOf("+0%") }
    val pitchSetting by remember { mutableStateOf("+0%") }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedAudioPath by remember { mutableStateOf<String?>(null) }
    var generatedAudioTitle by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val neuralVoices = remember {
        listOf(
            VoiceCharacter("ar-SA-HamedNeural", "حامد (سعودي)", "وقور ورخيم - تدبر وتلاوة", "خاشع", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/hamed_preview.mp3"),
            VoiceCharacter("ar-EG-ShakirNeural", "شاكر (مصري)", "وثائقي ورصين - سيرة وقصص", "وثائقي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/shakir_preview.mp3"),
            VoiceCharacter("ar-SA-NaayfNeural", "نايف (سعودي)", "حماسي وسريع - خطاف وريلز", "حماسي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/naayf_preview.mp3"),
            VoiceCharacter("ar-SA-ZariyahNeural", "سلمى (سعودية)", "هادئة وإرشادية - تزكية وخواطر", "هادئ", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/salma_preview.mp3"),
            VoiceCharacter("ar-AE-HamdanNeural", "حمدان (إماراتي)", "فصيح ودافئ - حكمة وعبر", "رسمي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/hamdan_preview.mp3"),
            VoiceCharacter("ar-QA-MoazNeural", "معاذ (قطري)", "روحاني خاشع - أدعية وأذكار", "دعاء", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/moaz_preview.mp3")
        )
    }


    val previewPlayer = remember { MediaPlayer() }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    fun playVoicePreview(url: String) {
        try {
            previewPlayer.reset()
            previewPlayer.setDataSource(url)
            previewPlayer.prepareAsync()
            previewPlayer.setOnPreparedListener {
                it.start()
                isPreviewPlaying = true
            }
            previewPlayer.setOnCompletionListener {
                isPreviewPlaying = false
            }
            previewPlayer.setOnErrorListener { _, _, _ ->
                isPreviewPlaying = false
                Toast.makeText(context, "تعذر تشغيل العينة الصوتية", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            isPreviewPlaying = false
            Toast.makeText(context, "تعذر تشغيل العينة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        heroBanner?.invoke()

        AppleCard(cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("قوالب نصوص سريعة جاهزة للإلقاء:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SuggestionPresetChip("قصة صحابي") {
                            scriptText = "في ظلال السيرة النبوية، سطّر أصحاب النبي صلى الله عليه وسلم ملاحم من الصبر واليقين أنارت دروب البشرية جمعاء."
                            selectedTone = "قصصي"
                            selectedVoiceName = "ar-EG-ShakirNeural"
                        }
                    }
                    item {
                        SuggestionPresetChip("تدبر خاشع") {
                            scriptText = "يا ابن آدم، لو تأملت في ملكوت السماوات والأرض لوجدت أن كل ذرة تسبح بحمد الله.. فما بالك بقلبك؟"
                            selectedTone = "روحاني"
                            selectedVoiceName = "ar-SA-HamedNeural"
                        }
                    }
                    item {
                        SuggestionPresetChip("خطاف ريلز") {
                            scriptText = "ثلاث دقائق قد تغيّر مسار يومك بالكامل! هل فكرت يوماً ماذا لو كانت هذه اللحظة هي بدايتك الحقيقية مع الله؟"
                            selectedTone = "حماسي"
                            selectedVoiceName = "ar-SA-NaayfNeural"
                        }
                    }
                    item {
                        SuggestionPresetChip("وصية نبوية") {
                            scriptText = "احرص على ما ينفعك واستعن بالله ولا تعجز، فما قدّر الله لك نافذ، والخير في كل قضاء."
                            selectedTone = "رسمي"
                            selectedVoiceName = "ar-AE-HamdanNeural"
                        }
                    }
                }
            }
        }

        AppleCard(cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("النص المراد تحويله لصوت", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${scriptText.length} حرف", color = theme.textSecondary, fontFamily = RobotoMonoFont, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = {
                        Text(
                            "اكتب نص السكريبت هنا مشكولاً أو عادياً...",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                        focusedBorderColor = theme.primary,
                        unfocusedContainerColor = theme.secondarySurface,
                        focusedContainerColor = theme.secondarySurface,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        AppleCard(cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("محرك التوليد الصوتي المحلي", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("يُولَّد التعليق محلياً عبر Kokoro وEdge-TTS ومحرك النظام — بلا مفاتيح سحابية.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
        }


        Column {
            Text("المؤدي الصوتي والنبرة", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val currentVoiceList = neuralVoices
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(currentVoiceList) { voice ->
                    val isSelected = selectedVoiceName == voice.id
                    AppleCard(
                        modifier = Modifier.width(195.dp),
                        cornerRadius = 14.dp,
                        containerColor = if (isSelected) theme.primary.copy(alpha = if (theme.isDark) 0.18f else 0.10f) else theme.cardSurface,
                        onClick = {
                            selectedVoiceName = voice.id
                            selectedTone = voice.tag
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    voice.name,
                                    color = if (isSelected) theme.primary else theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (voice.previewUrl != null) {
                                    IconButton(
                                        onClick = { playVoicePreview(voice.previewUrl) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying && previewPlayer.isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayCircle,
                                            contentDescription = "استماع للعينة",
                                            tint = theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(voice.desc, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }

        Column {
            Text("سرعة الإلقاء", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AppleSegmentedControl(
                items = listOf("-10%" to "هادئ 0.9x", "+0%" to "عادي 1.0x", "+15%" to "سريع 1.15x"),
                selectedItem = speakingRate,
                onSelect = { speakingRate = it }
            )
        }

        ApplePrimaryButton(
            text = if (isGenerating) "جاري توليد التعليق الصوتي..." else "توليد التعليق الصوتي",
            icon = Icons.Default.GraphicEq,
            isLoading = isGenerating,
            enabled = !isGenerating,
            onClick = {
                if (scriptText.isBlank()) {
                    Toast.makeText(context, "الرجاء كتابة نص السكريبت أولاً", Toast.LENGTH_SHORT).show()
                    return@ApplePrimaryButton
                }
                isGenerating = true
                coroutineScope.launch {
                    val resultPath = AppServices.generateVoiceover(
                        text = scriptText,
                        tone = selectedTone,
                        voiceId = selectedVoiceName,
                        rate = speakingRate,
                        pitch = pitchSetting
                    )
                    isGenerating = false
                    if (resultPath != null) {
                        generatedAudioPath = resultPath
                        generatedAudioTitle = "تعليق صوتي: ${scriptText.take(25)}..."
                        Toast.makeText(context, "تم توليد الصوت وحفظه بنجاح", Toast.LENGTH_SHORT).show()

                        try {
                            val trackEntity = AudioTrackEntity(
                                id = "AI_VOICE_${System.currentTimeMillis()}",
                                title = generatedAudioTitle,
                                artistOrVibe = "محلي | $selectedTone",
                                typeCategory = "AI_VOICEOVER",
                                audioUrlOrPath = resultPath,
                                duration = "0:30",
                                scriptText = scriptText,
                                createdAt = System.currentTimeMillis()
                            )
                            AppDatabase.getDatabase(context).audioTrackDao().insertTrack(trackEntity)
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                        }
                    } else {
                        Toast.makeText(context, "تعذر التوليد: تحقق من مفاتيح TTS السحابية أو نطق النظام.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        if (generatedAudioPath != null) {
            AppleCard(cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("الصوت جاهز في ذاكرة المشروع", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(
                            onClick = { onPlayGenerated(generatedAudioPath!!, generatedAudioTitle) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "معاينة", tint = theme.primary, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(generatedAudioTitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, maxLines = 2)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ApplePrimaryButton(
                                text = "اعتماد للمشروع",
                                icon = Icons.Default.DoneAll,
                                onClick = { onApply(generatedAudioPath!!, generatedAudioTitle) }
                            )
                        }
                        Box(modifier = Modifier.weight(0.75f)) {
                            AppleSecondaryButton(
                                text = "مشاركة",
                                icon = Icons.Default.Share,
                                onClick = {
                                    try {
                                        val file = File(generatedAudioPath!!)
                                        if (file.exists()) {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "audio/*"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملف الصوتي"))
                                        } else {
                                            Toast.makeText(context, "الملف غير متوفر في الذاكرة", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "المسار: $generatedAudioPath", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun SavedAudioTracksSection(
    activePlayingUrl: String?,
    isPlaying: Boolean,
    onPlayTrack: (url: String, title: String) -> Unit,
    onApply: (url: String, title: String) -> Unit,
    onDownloadTrack: (url: String, title: String) -> Unit = { _, _ -> },
    heroBanner: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    val audioTrackDao = remember { AppDatabase.getDatabase(context).audioTrackDao() }
    val savedTracks by audioTrackDao.getAllAudioTracks().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        heroBanner?.invoke()

        if (savedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(54.dp))
                    Text("لا توجد تسجيلات أو تعليقات صوتية محفوظة بعد", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text("قم بتوليد تعليق صوتي وسيتم حفظه وأرشفته هنا تلقائياً.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedTracks, key = { it.id }) { item ->
                    val isTrackPlaying = isPlaying && activePlayingUrl == item.audioUrlOrPath
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onPlayTrack(item.audioUrlOrPath, item.title) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isTrackPlaying) theme.primary else theme.secondarySurface)
                                ) {
                                    Icon(
                                        if (isTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isTrackPlaying) "إيقاف" else "تشغيل",
                                        tint = if (isTrackPlaying) Color.White else theme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        color = if (isTrackPlaying) theme.primary else theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${item.artistOrVibe} • ${formatTimestamp(item.createdAt)}",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            audioTrackDao.deleteTrackById(item.id)
                                            Toast.makeText(context, "تم حذف المقطع من الأرشيف", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = theme.destructive, modifier = Modifier.size(20.dp))
                                }
                            }

                            if (item.scriptText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "\"${item.scriptText}\"",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AppleSecondaryButton(
                                        text = "تنزيل للهاتف",
                                        icon = Icons.Default.FileDownload,
                                        onClick = { onDownloadTrack(item.audioUrlOrPath, item.title) }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ApplePrimaryButton(
                                        text = "تطبيق للمشروع",
                                        icon = Icons.Default.Check,
                                        onClick = { onApply(item.audioUrlOrPath, item.title) }
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

@Composable
fun SuggestionPresetChip(label: String, onClick: () -> Unit) {
    val theme = QabasTheme.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = theme.primary.copy(alpha = if (theme.isDark) 0.16f else 0.10f),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(label, color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
fun EngineCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = modifier,
        cornerRadius = 14.dp,
        containerColor = if (isSelected) theme.primary.copy(alpha = if (theme.isDark) 0.18f else 0.10f) else theme.cardSurface,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) theme.primary else theme.textSecondary, modifier = Modifier.size(19.dp))
                Icon(
                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) theme.primary else theme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = if (isSelected) theme.primary else theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            Text(subtitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

data class LiveAudioItem(
    val id: String,
    val title: String,
    val category: String,
    val duration: String,
    val audioUrl: String,
    val isAsset: Boolean = false,
    val badge: String = "خادم المسلم",
    val author: String = "",
    val youtubeUrl: String? = null
)

data class VoiceCharacter(
    val id: String,
    val name: String,
    val desc: String,
    val tag: String,
    val previewUrl: String? = null
)

@Composable
private fun SectionHeroBanner(
    title: String,
    description: String,
    imageRes: Int? = null,
    fallbackEmoji: String = "🎧",
    accentColor: Color = qabasPrimary()
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (imageRes != null) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(fallbackEmoji, fontSize = 28.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun shareAudioTrack(context: Context, track: LiveAudioItem) {
    try {
        val shareBody = buildString {
            appendLine("🎧 مادة صوتية ومحاضرة من «مكتبة خادم المسلم»:")
            appendLine("«${track.title}»")
            if (track.author.isNotBlank()) appendLine("🎙️ الإلقاء: ${track.author}")
            if (track.category.isNotBlank()) appendLine("🏷️ التصنيف: ${track.category}")
            if (track.duration.isNotBlank()) appendLine("⏱️ المدة: ${track.duration}")
            appendLine()
            appendLine("🔗 رابط الاستماع والتحميل المباشر:")
            appendLine(track.audioUrl)
            appendLine()
            appendLine("✨ تم المشاركة عبر تطبيق خادم المسلم (Muslim AI Assistant)")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "صوتية: ${track.title}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة رابط الاستماع والتحميل عبر"))
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر فتح نافذة المشاركة", Toast.LENGTH_SHORT).show()
    }
}

fun shareAudioTrack(context: Context, track: SupabaseServices.AudioTrackRow) {
    try {
        val shareBody = buildString {
            appendLine("🎧 مادة صوتية مميزة من «مكتبة خادم المسلم»:")
            appendLine("«${track.title}»")
            if (track.author.isNotBlank()) appendLine("🎙️ الإلقاء: ${track.author}")
            if (track.category.isNotBlank()) appendLine("🏷️ التصنيف: ${track.category}")
            if (track.duration.isNotBlank()) appendLine("⏱️ المدة: ${track.duration}")
            appendLine()
            appendLine("🔗 رابط الاستماع والتحميل المباشر:")
            appendLine(track.audioUrl)
            appendLine()
            appendLine("✨ تم المشاركة عبر تطبيق خادم المسلم (Muslim AI Assistant)")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "صوتية: ${track.title}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة رابط الاستماع والتحميل عبر"))
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر فتح نافذة المشاركة", Toast.LENGTH_SHORT).show()
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
