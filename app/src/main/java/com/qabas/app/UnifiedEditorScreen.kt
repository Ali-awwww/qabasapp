package com.qabas.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.TextSecondary

/**
 * المحرر الموحّد — يجمع شاشات التحرير الثلاث في مكان واحد:
 * محرر النص-هو-الفيديو (TranscriptVideoEditorScreen) + المونتاج المتقدم (AdvancedEditScreen) +
 * المحرر الاحترافي (StudioProEditorScreen).
 * الشاشات الأصلية باقية كما هي؛ حالات التنقل القديمة (TRANSCRIPT_EDIT / ADVANCED_EDIT / PRO_EDITOR)
 * تدخل كل واحدة على تبويبها الصحيح.
 */
@Composable
fun UnifiedEditorScreen(
    initialTab: Int = EDITOR_TAB_TRANSCRIPT,
    scenes: List<Scene>,
    onScenesApplied: (List<Scene>) -> Unit,
    onExit: () -> Unit,
    onProEditorExit: () -> Unit,
    onProEditorSave: () -> Unit
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GoldPrimary
        ) {
            editorTabs.forEachIndexed { i, t ->
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
                EDITOR_TAB_TRANSCRIPT -> TranscriptVideoEditorScreen(
                    scenes = scenes,
                    onBack = onExit,
                    onApply = onScenesApplied
                )
                EDITOR_TAB_ADVANCED -> AdvancedEditScreen(
                    scenes = scenes,
                    onUpdateScenes = onScenesApplied,
                    onBack = onExit
                )
                EDITOR_TAB_PRO -> StudioProEditorScreen(
                    onBack = onProEditorExit,
                    onSave = onProEditorSave
                )
            }
        }
    }
}

private data class EditorTab(val title: String, val icon: ImageVector)

private val editorTabs = listOf(
    EditorTab("محرر النص", Icons.Filled.Article),
    EditorTab("المونتاج المتقدم", Icons.Filled.AutoFixHigh),
    EditorTab("المحرر الاحترافي", Icons.Filled.Tune)
)

const val EDITOR_TAB_TRANSCRIPT = 0
const val EDITOR_TAB_ADVANCED = 1
const val EDITOR_TAB_PRO = 2
