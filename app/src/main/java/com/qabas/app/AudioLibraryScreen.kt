package com.qabas.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import com.qabas.app.ui.components.GlassmorphicSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    onBack: () -> Unit,
    onAudioSelected: (String) -> Unit = {},
    onApplyToProject: (audioPath: String, usageType: String) -> Unit = { path, _ -> onAudioSelected(path) }
) {
    var selectedTab by remember { mutableStateOf("library") } // "library", "ai_voice", "saved"
    val context = LocalContext.current

    // Global Background Player State from AudioPlaybackManager
    val activePlayingTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val activePlayingUrl = activePlayingTrack?.audioUrl

    var showApplyDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(audioPath, title)

    // Real Download Progress State
    var activeDownloadTrackTitle by remember { mutableStateOf<String?>(null) }
    var activeDownloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Playback controller using background service & playlist manager
    fun playTrack(audioUrl: String, title: String) {
        if (activePlayingTrack?.audioUrl == audioUrl) {
            AudioPlaybackManager.playPause(context)
            return
        }

        val item = PlayableAudioTrack(
            id = audioUrl,
            title = title,
            artist = "قبس",
            audioUrl = audioUrl,
            category = "صوتيات قبس"
        )
        AudioPlaybackManager.playTrack(context, item)
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.AUDIO_LIBRARY_SCREEN),
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF030712).copy(alpha = 0.95f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            Translator.tr("المكتبة الصوتية"),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Translator.tr("العودة"), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )

                ScrollableTabRow(
                    selectedTabIndex = when (selectedTab) {
                        "library" -> 0
                        "supabase" -> 1
                        "ai_voice" -> 2
                        "saved" -> 3
                        else -> 0
                    },
                    containerColor = Color.Transparent,
                    contentColor = qabasPrimary(),
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[when (selectedTab) {
                                    "library" -> 0
                                    "supabase" -> 1
                                    "ai_voice" -> 2
                                    "saved" -> 3
                                    else -> 0
                                }])
                                .padding(horizontal = 12.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(qabasPrimary(), AiCyan)
                                    )
                                )
                        )
                    }
                ) {
                    val tabs = listOf(
                        "library" to "المكتبة 🏠",
                        "supabase" to "السحابة ☁️",
                        "ai_voice" to "استوديو AI 🎙️",
                        "saved" to "المحفوظات 💾"
                    )
                    tabs.forEachIndexed { index, (id, label) ->
                        Tab(
                            selected = selectedTab == id,
                            onClick = { selectedTab = id },
                            text = {
                                Text(
                                    label,
                                    fontFamily = CairoFont,
                                    fontWeight = if (selectedTab == id) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
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
        containerColor = Color(0xFF030712)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val primaryColor = qabasPrimary()
            
            // Spatial HUD Background Pattern
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 30.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(
                        color = primaryColor.copy(alpha = 0.04f),
                        start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                        end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawLine(
                        color = primaryColor.copy(alpha = 0.04f),
                        start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                        end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()),
                        strokeWidth = 1f
                    )
                }
            }
            
            HudCornerDecor(modifier = Modifier.fillMaxSize().padding(12.dp), color = primaryColor.copy(alpha = 0.15f))

            fun triggerDownload(url: String, title: String) {
                activeDownloadTrackTitle = title
                activeDownloadProgress = DownloadProgress(percent = 0.05f)
                coroutineScope.launch {
                    AudioDownloadHelper.downloadAudioWithProgress(
                        context = context,
                        audioUrl = url,
                        title = title
                    ) { p ->
                        activeDownloadProgress = p
                    }
                }
            }

            when (selectedTab) {
                "library" -> {
                    AudioLibrarySection(
                        activePlayingUrl = activePlayingUrl,
                        isPlaying = isPlaying,
                        onPlayTrack = { url, title -> playTrack(url, title) },
                        onApply = { url, title -> showApplyDialog = Pair(url, title) },
                        onDownloadTrack = { url, title -> triggerDownload(url, title) },
                        heroBanner = {
                            SectionHeroBanner(
                                title = "المكتبة الصوتية المركزية",
                                description = "مجموعة مختارة من التلاوات، الدروس، والمؤثرات الصوتية عالية الجودة لنشر الأثر.",
                                imageRes = R.drawable.hud_audio_lab_banner_1790878245000,
                                accentColor = primaryColor
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
                                title = "سحابة قبس (Supabase)",
                                description = "مزامنة سحابية فورية لجميع تسجيلاتك المرفوعة والمولدة، متاحة من أي مكان.",
                                imageRes = R.drawable.hud_cloud_storage_banner_1790878859957,
                                accentColor = EmeraldGreen
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
                                title = "استوديو التعليق العصبي",
                                description = "حول نصوصك إلى صوت فخم باستخدام تقنيات الذكاء الاصطناعي الأحدث (Edge-TTS & Kokoro).",
                                imageRes = R.drawable.hud_voice_synth_banner_1790878874174,
                                accentColor = Color(0xFFA855F7)
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
                                accentColor = GoldPrimary
                            )
                        }
                    )
                }
            }
        }
    }

    // Real Download Progress Dialog
    if (activeDownloadTrackTitle != null && activeDownloadProgress != null) {
        RealDownloadProgressDialog(
            trackTitle = activeDownloadTrackTitle!!,
            progress = activeDownloadProgress!!,
            onCancelOrDismiss = {
                activeDownloadTrackTitle = null
                activeDownloadProgress = null
            }
        )
    }

    // Apply Audio Dialog
    if (showApplyDialog != null) {
        val (audioPath, trackTitle) = showApplyDialog!!
        AlertDialog(
            onDismissRequest = { showApplyDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اعتماد الصوت في المشروع", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "كيف ترغب في تطبيق \"$trackTitle\" على مشروعك الحالي؟",
                        fontFamily = NotoSansFont,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    // Option 1: As Voiceover
                    Button(
                        onClick = {
                            onApplyToProject(audioPath, "voiceover")
                            Toast.makeText(context, "تم تعيين الصوت كـ (تعليق صوتي رئيسي) للمشروع 🎙️", Toast.LENGTH_SHORT).show()
                            showApplyDialog = null
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تعيين كـ تعليق صوتي رئيسي (Voiceover)", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp)
                    }

                    // Option 2: As Background Music / Ambient
                    Button(
                        onClick = {
                            onApplyToProject(audioPath, "ambient")
                            Toast.makeText(context, "تم تعيين الصوت كـ (خلفية ومؤثر محيطي) للمشروع 🎵", Toast.LENGTH_SHORT).show()
                            showApplyDialog = null
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تعيين كـ خلفية صوتية ومؤثر (Background)", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showApplyDialog = null }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF151B2B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun AudioTabButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primaryColor = qabasPrimary()
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) primaryColor else Color(0xFF1E293B).copy(alpha = 0.4f))
            .border(1.dp, if (isSelected) primaryColor else Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) DeepSlate else Color.White.copy(alpha = 0.8f),
            fontFamily = CairoFont,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AudioLibrarySection(
    activePlayingUrl: String?,
    isPlaying: Boolean,
    onPlayTrack: (url: String, title: String) -> Unit,
    onApply: (url: String, title: String) -> Unit,
    onDownloadTrack: (url: String, title: String) -> Unit = { _, _ -> },
    heroBanner: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioTrackDao = remember { AppDatabase.getDatabase(context).audioTrackDao() }
    val savedTracks by audioTrackDao.getAllAudioTracks().collectAsState(initial = emptyList())

    var cloudTracks by remember { mutableStateOf<List<SupabaseServices.AudioTrackRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل 🌐") }
    val isDeveloper = remember { AdminGuard.isDashboardAccessAllowed(context) }
    var showUploadDialog by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            "الكل 🌐",
            "الشيخ سمير مصطفى 🎙️",
            "تلاوات قرآنية 📖",
            "دروس ومحاضرات 🎓",
            "طبيعة وهدوء 🌿",
            "مؤثرات (SFX) ⚡",
            "آهات وقورة 👤",
            "أناشيد 🎵",
            "المطور 🛠️"
        )
    }

    fun loadRealTracks() {
        isLoading = true
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    SupabaseServices.Database.getAudioTracks()
                }
                cloudTracks = tracks
            } catch (_: Exception) {
                cloudTracks = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRealTracks()
    }

    // Combine cloud tracks, curated scholar tracks, and local uploaded audio tracks without ANY dummy/placeholder records
    val allRealTracks = remember(cloudTracks, savedTracks) {
        val list = mutableListOf<LiveAudioItem>()
        // 1. سلاسل الدروس العلمية المعتمدة لفضيلة الشيخ سمير مصطفى (عقائد البلاء)
        list.addAll(SamirMustafaAqaidAlBalaaData.toLiveAudioItems())

        // 2. Cloud tracks from Supabase
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
        // 3. Local tracks that are not duplicates
        savedTracks.forEach { st ->
            if (list.none { it.id == st.id || it.audioUrl == st.audioUrlOrPath }) {
                list.add(
                    LiveAudioItem(
                        id = st.id,
                        title = st.title,
                        category = if (st.typeCategory == "RECITATION") "تلاوات قرآنية" else if (st.typeCategory == "NASHEED") "أناشيد (بدون موسيقى)" else st.typeCategory,
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

    val filteredTracks by remember(allRealTracks, selectedCategory, searchQuery) {
        derivedStateOf {
            val selectedClean = selectedCategory.replace(Regex("[^\\u0600-\\u06FF\\s\\w]"), "").trim()
            allRealTracks.filter { track ->
                val trackClean = track.category.replace(Regex("[^\\u0600-\\u06FF\\s\\w]"), "").trim()
                val matchCategory = selectedClean == "الكل" || trackClean == selectedClean
                val matchQuery = searchQuery.isBlank() || track.title.contains(searchQuery, ignoreCase = true) ||
                         track.category.contains(searchQuery, ignoreCase = true) ||
                         track.author.contains(searchQuery, ignoreCase = true)
                matchCategory && matchQuery
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        heroBanner?.invoke()
        val primaryColor = qabasPrimary()
        // Search & Refresh bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF030712))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("ابحث في الدروس والتلاوات المرفوعة...", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = primaryColor) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedBorderColor = primaryColor,
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = CutCornerShape(12.dp),
                    singleLine = true
                )

                IconButton(
                    onClick = { loadRealTracks() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CutCornerShape(12.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        .border(1.dp, Color(0xFF334155), CutCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "مزامنة وتحديث",
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (isDeveloper) {
                    IconButton(
                        onClick = { showUploadDialog = true },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CutCornerShape(12.dp))
                            .background(primaryColor)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = "رفع صوتية من حساب المطور",
                            tint = DeepSlate,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Categories Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = {
                        Text(
                            text = cat,
                            fontFamily = CairoFont,
                            fontWeight = if (selectedCategory == cat) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B).copy(alpha = 0.4f),
                        labelColor = Color.White.copy(alpha = 0.7f),
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        selectedLabelColor = primaryColor
                    ),
                    border = BorderStroke(1.dp, if (selectedCategory == cat) primaryColor else Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
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
                    CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(36.dp))
                    Text(
                        "جاري جلب الصوتيات والدروس الحية من سحابة Supabase...",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (filteredTracks.isEmpty()) {
            // Clean authentic empty state with zero fake data
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                    borderColor = primaryColor.copy(alpha = 0.4f),
                    shape = CutCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.12f))
                                .border(1.dp, primaryColor.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "المكتبة الصوتية والدروس العلمية",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "AUDIO_ASSET_VAULT // STATUS: READY",
                                color = primaryColor.copy(alpha = 0.6f),
                                fontFamily = RobotoMonoFont,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategory != "الكل") {
                                "لا توجد نتائج مطابقة لبحثك في الصوتيات المرفوعة."
                            } else {
                                "القسم نظيف وجاهز لاستقبال الصوتيات والدروس العلمية من حساب المطور وسحابة Supabase. ما يتم رفعه يظهر فوراً لجميع مستخدمي التطبيق دون أي بيانات وهمية أو تجريبية."
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        if (isDeveloper) {
                            Button(
                                onClick = { showUploadDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = CutCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DeepSlate)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "إضافة أو رفع صوتية جديدة (حساب المطور)",
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { loadRealTracks() },
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                                shape = CutCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = primaryColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إعادة المزامنة مع السحابة", color = primaryColor, fontFamily = CairoFont, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Track List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    val isTrackPlaying = isPlaying && activePlayingUrl == track.audioUrl
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        border = BorderStroke(1.dp, if (isTrackPlaying) primaryColor else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Row 1: Play button + Title + Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Play/Pause circular button
                                IconButton(
                                    onClick = { onPlayTrack(track.audioUrl, track.title) },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(if (isTrackPlaying) primaryColor else Color(0xFF1E293B).copy(alpha = 0.8f))
                                        .border(1.dp, if (isTrackPlaying) primaryColor else Color(0xFF334155), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isTrackPlaying) "إيقاف" else "تشغيل",
                                        tint = if (isTrackPlaying) DeepSlate else primaryColor,
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
                                            color = if (isTrackPlaying) primaryColor else TextPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (track.badge.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = primaryColor.copy(alpha = 0.12f),
                                                border = BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = track.badge,
                                                    color = primaryColor,
                                                    fontSize = 9.5.sp,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Author & Duration metadata
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = track.author.ifBlank { track.category },
                                            color = TextSecondary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("•", color = Color.Gray, fontSize = 10.sp)
                                        Text(
                                            text = track.duration,
                                            color = primaryColor.copy(alpha = 0.85f),
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.6f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Bottom Action Bar (Clear, spacious, zero overlapping)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Tag on the left/start
                                Surface(
                                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = track.category,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                // Interactive Actions: Share, Download, Apply
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Share button
                                    OutlinedIconButton(
                                        onClick = { shareAudioTrack(context, track) },
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "مشاركة رابط الاستماع",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Download button
                                    OutlinedIconButton(
                                        onClick = { onDownloadTrack(track.audioUrl, track.title) },
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            contentDescription = "تنزيل للهاتف",
                                            tint = primaryColor,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Apply to project button
                                    Button(
                                        onClick = { onApply(track.audioUrl, track.title) },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = DeepSlate,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            "اعتماد",
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
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

    if (showUploadDialog) {
        AddAudioTrackDialog(
            categories = categories.filter { it != "الكل" },
            onDismiss = { showUploadDialog = false },
            onTrackAdded = {
                showUploadDialog = false
                loadRealTracks()
            }
        )
    }
}

@Composable
fun AIVoiceoverSection(
    onPlayGenerated: (path: String, title: String) -> Unit,
    onApply: (path: String, title: String) -> Unit,
    heroBanner: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    var scriptText by remember { mutableStateOf("") }
    var selectedEngine by remember { mutableStateOf("AZURE") } // "AZURE" or "ELEVENLABS"
    var selectedVoiceName by remember { mutableStateOf("ar-SA-HamedNeural") }
    var selectedTone by remember { mutableStateOf("وثائقي") }
    var speakingRate by remember { mutableStateOf("+0%") } // "-10%", "+0%", "+15%", "+30%"
    var pitchSetting by remember { mutableStateOf("+0%") } // "-5%", "+0%", "+5%"

    var isGenerating by remember { mutableStateOf(false) }
    var generatedAudioPath by remember { mutableStateOf<String?>(null) }
    var generatedAudioTitle by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val azureVoices = remember {
        listOf(
            VoiceCharacter("ar-SA-HamedNeural", "حامد (سعودي)", "وقور ورخيم - تدبر وتلاوة", "خاشع", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/hamed_preview.mp3"),
            VoiceCharacter("ar-EG-ShakirNeural", "شاكر (مصري)", "وثائقي ورصين - سيرة وقصص", "وثائقي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/shakir_preview.mp3"),
            VoiceCharacter("ar-SA-NaayfNeural", "نايف (سعودي)", "حماسي وسريع - خطاف وريلز", "حماسي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/naayf_preview.mp3"),
            VoiceCharacter("ar-SA-ZariyahNeural", "سلمى (سعودية)", "هادئة وإرشادية - تزكية وخواطر", "هادئ", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/salma_preview.mp3"),
            VoiceCharacter("ar-AE-HamdanNeural", "حمدان (إماراتي)", "فصيح ودافئ - حكمة وعبر", "رسمي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/hamdan_preview.mp3"),
            VoiceCharacter("ar-QA-MoazNeural", "معاذ (قطري)", "روحاني خاشع - أدعية وأذكار", "دعاء", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/moaz_preview.mp3")
        )
    }

    val elevenlabsVoices = remember {
        listOf(
            VoiceCharacter("21m00Tcm4TlvDq8ikWAM", "Rachel (ElevenLabs)", "هادئ متزن وواضح", "طبيعي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/rachel_preview.mp3"),
            VoiceCharacter("pNInz6obpgDQGcFmaJgB", "Adam (ElevenLabs)", "عميق وملحمي سينمائي", "ملحمي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/adam_preview.mp3"),
            VoiceCharacter("ErXwobaYiN019PkySvjV", "Antoni (ElevenLabs)", "حيوي وشبابي معبّر", "حماسي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/antoni_preview.mp3"),
            VoiceCharacter("EXAVITQu4vr4xnSDxMaL", "Bella (ElevenLabs)", "سردي وناعم للمونتاج", "قصصي", "https://vauisbhajmjpjjpkmvqy.supabase.co/storage/v1/object/public/previews/bella_preview.mp3")
        )
    }

    // Quick Voice Preview Player (Independent from main player)
    val previewPlayer = remember { android.media.MediaPlayer() }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            previewPlayer.release()
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
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر تشغيل العينة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        heroBanner?.invoke()
        // Quick Presets Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("قوالب نصوص سريعة جاهزة للإلقاء:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SuggestionPresetChip("📜 قصة صحابي") {
                            scriptText = "في ظلال السيرة النبوية، سطّر أصحاب النبي صلى الله عليه وسلم ملاحم من الصبر واليقين أنارت دروب البشرية جمعاء."
                            selectedTone = "قصصي"
                            selectedVoiceName = "ar-EG-ShakirNeural"
                        }
                    }
                    item {
                        SuggestionPresetChip("🌿 تدبر خاشع") {
                            scriptText = "يا ابن آدم، لو تأملت في ملكوت السماوات والأرض لوجدت أن كل ذرة تسبح بحمد الله.. فما بالك بقلبك؟"
                            selectedTone = "روحاني"
                            selectedVoiceName = "ar-SA-HamedNeural"
                        }
                    }
                    item {
                        SuggestionPresetChip("⚡ خطاف ريلز") {
                            scriptText = "ثلاث دقائق قد تغيّر مسار يومك بالكامل! هل فكرت يوماً ماذا لو كانت هذه اللحظة هي بدايتك الحقيقية مع الله؟"
                            selectedTone = "حماسي"
                            selectedVoiceName = "ar-SA-NaayfNeural"
                        }
                    }
                    item {
                        SuggestionPresetChip("🕊️ وصية نبوية") {
                            scriptText = "احرص على ما ينفعك واستعن بالله ولا تعجز، فما قدّر الله لك نافذ، والخير في كل قضاء."
                            selectedTone = "رسمي"
                            selectedVoiceName = "ar-AE-HamdanNeural"
                        }
                    }
                }
            }
        }

        // Script Text Input
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("النص المراد تحويله لصوت:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${scriptText.length} حرف", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = scriptText,
                onValueChange = { scriptText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = {
                    Text(
                        "اكتب نص السكريبت هنا مشكولاً أو عادياً (مثال: في عمق التاريخ الإسلامي، برزت أسماء أنارت دروب البشرية...)",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedBorderColor = GoldPrimary,
                    unfocusedContainerColor = Color(0xFF151B2B),
                    focusedContainerColor = Color(0xFF151B2B),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Engine Selector
        Column {
            Text("محرك التوليد الصوتي:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EngineCard(
                    title = "Azure Neural Speech",
                    subtitle = "نطق عربي وتشكيل فائق الدقة",
                    icon = Icons.Default.Bolt,
                    isSelected = selectedEngine == "AZURE",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedEngine = "AZURE"
                    selectedVoiceName = "ar-SA-HamedNeural"
                }

                EngineCard(
                    title = "ElevenLabs Studio",
                    subtitle = "نبرات سينمائية فائقة النقاء",
                    icon = Icons.Default.Mic,
                    isSelected = selectedEngine == "ELEVENLABS",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedEngine = "ELEVENLABS"
                    selectedVoiceName = "21m00Tcm4TlvDq8ikWAM"
                }
            }
        }

        // Voice Character Selector
        Column {
            Text("المؤدي الصوتي والنبرة:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val currentVoiceList = if (selectedEngine == "AZURE") azureVoices else elevenlabsVoices
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(currentVoiceList) { voice ->
                    val isSelected = selectedVoiceName == voice.id
                    Card(
                        modifier = Modifier
                            .width(180.dp)
                            .clickable {
                                selectedVoiceName = voice.id
                                selectedTone = voice.tag
                            },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF151B2B)),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(voice.name, color = if (isSelected) GoldPrimary else TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (voice.previewUrl != null) {
                                        IconButton(
                                            onClick = { playVoicePreview(voice.previewUrl) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPreviewPlaying && previewPlayer.isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayCircle,
                                                contentDescription = "استماع للعينة",
                                                tint = if (isSelected) GoldPrimary else GoldPrimary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(voice.desc, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }

        // Pacing & Rate Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("سرعة الإلقاء:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("-10%" to "هادئ 0.9x", "+0%" to "عادي 1.0x", "+15%" to "سريع 1.15x").forEach { (valRate, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (speakingRate == valRate) GoldPrimary else Color(0xFF151B2B))
                                .clickable { speakingRate = valRate }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (speakingRate == valRate) DeepSlate else TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                        }
                    }
                }
            }
        }

        // Generate Button
        Button(
            onClick = {
                if (scriptText.isBlank()) {
                    Toast.makeText(context, "الرجاء كتابة نص السكريبت أولاً ✍️", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isGenerating = true
                coroutineScope.launch {
                    val resultPath = if (selectedEngine == "ELEVENLABS") {
                        AppServices.generateVoiceover(
                            text = scriptText,
                            tone = selectedTone,
                            voiceId = selectedVoiceName,
                            engine = selectedEngine,
                            rate = speakingRate,
                            pitch = pitchSetting
                        )
                    } else {
                        AppServices.generateVoiceover(
                            text = scriptText,
                            tone = selectedTone,
                            customVoiceName = selectedVoiceName,
                            engine = selectedEngine,
                            rate = speakingRate,
                            pitch = pitchSetting
                        )
                    }
                    isGenerating = false
                    if (resultPath != null) {
                        generatedAudioPath = resultPath
                        generatedAudioTitle = "تعليق صوتي: ${scriptText.take(25)}..."
                        Toast.makeText(context, "تم توليد الصوت وحفظه في الذاكرة بنجاح! 🎙️✨", Toast.LENGTH_SHORT).show()

                        // Auto-archive in Room database
                        try {
                            val trackEntity = AudioTrackEntity(
                                id = "AI_VOICE_${System.currentTimeMillis()}",
                                title = generatedAudioTitle,
                                artistOrVibe = "$selectedEngine | $selectedTone",
                                typeCategory = "AI_VOICEOVER",
                                audioUrlOrPath = resultPath,
                                duration = "0:30",
                                scriptText = scriptText,
                                createdAt = System.currentTimeMillis()
                            )
                            AppDatabase.getDatabase(context).audioTrackDao().insertTrack(trackEntity)
                        } catch (_: Exception) {}
                    } else {
                        Toast.makeText(context, "تعذر التوليد. تأكد من مفاتيح API أو الاتصال.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            shape = RoundedCornerShape(12.dp),
            enabled = !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("جاري توليد الصوت الحقيقي...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = DeepSlate)
                Spacer(modifier = Modifier.width(8.dp))
                Text("توليد التعليق الصوتي الذكي 🎙️", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Generated Audio Preview Card
        if (generatedAudioPath != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                border = BorderStroke(1.5.dp, GoldPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("الصوت جاهز في ذاكرة المشروع", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = {
                            onPlayGenerated(generatedAudioPath!!, generatedAudioTitle)
                        }) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "معاينة", tint = GoldPrimary, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        generatedAudioTitle,
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onApply(generatedAudioPath!!, generatedAudioTitle)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اعتماد للمشروع", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = File(generatedAudioPath!!)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
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
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = BorderStroke(1.dp, Color(0xFF2A344A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
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
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(54.dp))
                Text("لا توجد تسجيلات أو تعليقات صوتية محفوظة بعد", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                Text("قم بتوليد تعليق صوتي بالذكاء الاصطناعي وسيتم حفظه وأرشفته هنا تلقائياً.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(savedTracks, key = { it.id }) { item ->
                val isTrackPlaying = isPlaying && activePlayingUrl == item.audioUrlOrPath
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                    border = BorderStroke(1.dp, if (isTrackPlaying) GoldPrimary else Color(0xFF1E293B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onPlayTrack(item.audioUrlOrPath, item.title) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isTrackPlaying) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Icon(
                                    if (isTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isTrackPlaying) DeepSlate else GoldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    color = if (isTrackPlaying) GoldPrimary else TextPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${item.artistOrVibe} • ${formatTimestamp(item.createdAt)}",
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        audioTrackDao.deleteTrackById(item.id)
                                        Toast.makeText(context, "تم حذف المقطع من الأرشيف", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (item.scriptText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "\"${item.scriptText}\"",
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    onDownloadTrack(item.audioUrlOrPath, item.title)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = "تنزيل", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تنزيل للهاتف", color = TextPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { onApply(item.audioUrlOrPath, item.title) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تطبيق على المشروع", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
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
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF151B2B)),
        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) GoldPrimary else TextSecondary, modifier = Modifier.size(18.dp))
                if (isSelected) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, color = if (isSelected) GoldPrimary else TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1)
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
    val badge: String = "قبس",
    val author: String = ""
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
    fallbackEmoji: String = "🛸",
    accentColor: Color = qabasPrimary()
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.6f),
        borderColor = accentColor.copy(alpha = 0.25f),
        shape = CutCornerShape(14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Technical Background Pattern
            Canvas(modifier = Modifier.matchParentSize()) {
                val step = 16.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.03f),
                        start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                        end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                        strokeWidth = 1f
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Graphic / Visual asset container with fixed bounds
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
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
                        Text(fallbackEmoji, fontSize = 32.sp)
                    }
                }

                // Text Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.15f),
                            shape = CutCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "SUBSYSTEM_ACTIVE",
                                color = accentColor,
                                fontFamily = RobotoMonoFont,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * مشاركة رابط الاستماع والتنزيل للصوتيات والمحاضرات عبر تطبيقات التواصل
 */
fun shareAudioTrack(context: Context, track: LiveAudioItem) {
    try {
        val shareBody = buildString {
            appendLine("🎧 مادة صوتية ومحاضرة من «مكتبة قبس»:")
            appendLine("«${track.title}»")
            if (track.author.isNotBlank()) {
                appendLine("🎙️ الإلقاء: ${track.author}")
            }
            if (track.category.isNotBlank()) {
                appendLine("🏷️ التصنيف: ${track.category}")
            }
            if (track.duration.isNotBlank()) {
                appendLine("⏱️ المدة: ${track.duration}")
            }
            appendLine()
            appendLine("🔗 رابط الاستماع والتحميل المباشر:")
            appendLine(track.audioUrl)
            appendLine()
            appendLine("✨ تم المشاركة عبر تطبيق قبس (Qabas Studio) - الاستوديو الإسلامي الذكي")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "صوتية: ${track.title}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة رابط الاستماع والتحميل عبر"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر فتح نافذة المشاركة", Toast.LENGTH_SHORT).show()
    }
}

fun shareAudioTrack(context: Context, track: SupabaseServices.AudioTrackRow) {
    try {
        val shareBody = buildString {
            appendLine("🎧 مادة صوتية مميزة من «مكتبة قبس»:")
            appendLine("«${track.title}»")
            if (track.author.isNotBlank()) {
                appendLine("🎙️ الإلقاء: ${track.author}")
            }
            if (track.category.isNotBlank()) {
                appendLine("🏷️ التصنيف: ${track.category}")
            }
            if (track.duration.isNotBlank()) {
                appendLine("⏱️ المدة: ${track.duration}")
            }
            appendLine()
            appendLine("🔗 رابط الاستماع والتحميل المباشر:")
            appendLine(track.audioUrl)
            appendLine()
            appendLine("✨ تم المشاركة عبر تطبيق قبس (Qabas Studio) - الاستوديو الإسلامي الذكي")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "صوتية: ${track.title}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة رابط الاستماع والتحميل عبر"))
    } catch (e: Exception) {
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
