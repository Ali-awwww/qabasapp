package com.qabas.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.TextSecondary

/**
 * غرفة التوجيه الموحّدة — تجمع شاشات ما قبل الإنتاج الأربع في مكان واحد:
 * الفكرة (InputScreen) ← حوار المخرج (DirectorChatScreen) ← الفهم (UnderstandingScreen) ← المخرج الذكي (AiSmartDirectorScreen).
 * الشاشات الأصلية باقية كما هي؛ هذه الشاشة وعاء يوحّدها بتبويبات، ويُستدعى كل تابب من حالته الأصلية في AppState
 * (INPUT / DIRECTOR_CHAT / UNDERSTANDING / SMART_DIRECTOR) فلا تنكسر أي نقطة تنقل قائمة.
 */
@Composable
fun UnifiedBriefScreen(
    initialTab: Int = TAB_IDEA,
    state: ProjectState,
    onStateChange: (ProjectState) -> Unit,
    onExit: () -> Unit,
    onProceedFromUnderstanding: () -> Unit,
    onOpenAudioLibrary: () -> Unit,
    onOpenQasas: () -> Unit,
    onOpenClips: () -> Unit,
    onBriefComplete: (DirectorBrief) -> Unit,
    onMakeMagic: () -> Unit,
    onSmartDirectorExit: () -> Unit
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GoldPrimary
        ) {
            briefTabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Column {
                            Icon(
                                t.icon, contentDescription = t.title,
                                tint = if (tab == i) GoldPrimary else TextSecondary
                            )
                            Text(
                                t.title,
                                color = if (tab == i) GoldPrimary else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                TAB_IDEA -> InputScreen(
                    state = state,
                    onStateChange = onStateChange,
                    onProceed = { tab = TAB_UNDERSTANDING },
                    onBack = onExit,
                    onOpenAudioLibrary = onOpenAudioLibrary,
                    onOpenDirector = { tab = TAB_DIRECTOR },
                    onOpenQasas = onOpenQasas,
                    onOpenClips = onOpenClips
                )
                TAB_DIRECTOR -> DirectorChatScreen(
                    initialText = state.inputText,
                    onBack = { tab = TAB_IDEA },
                    onComplete = onBriefComplete
                )
                TAB_UNDERSTANDING -> UnderstandingScreen(
                    inputText = state.inputText,
                    contentType = state.contentType,
                    contentTone = state.contentTone,
                    selectedRatio = state.selectedRatio,
                    videoDuration = state.videoDuration,
                    videoStyleAnalysis = state.videoStyleAnalysis,
                    styleDescription = state.styleDescription,
                    onStyleDescriptionChange = { newStyle -> onStateChange(state.copy(styleDescription = newStyle)) },
                    onEditIdea = { tab = TAB_IDEA },
                    onProceed = onProceedFromUnderstanding
                )
                TAB_SMART_DIRECTOR -> AiSmartDirectorScreen(
                    onBack = onSmartDirectorExit,
                    onMakeMagic = onMakeMagic
                )
            }
        }
    }
}

private data class BriefTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val briefTabs = listOf(
    BriefTab("الفكرة", Icons.Filled.Edit),
    BriefTab("حوار المخرج", Icons.Filled.Chat),
    BriefTab("الفهم", Icons.Filled.School),
    BriefTab("المخرج الذكي", Icons.Filled.Movie)
)

const val TAB_IDEA = 0
const val TAB_DIRECTOR = 1
const val TAB_UNDERSTANDING = 2
const val TAB_SMART_DIRECTOR = 3
