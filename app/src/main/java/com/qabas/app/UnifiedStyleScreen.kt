package com.qabas.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
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
 * شاشة الأنماط الموحّدة — تجمع شاشات الأساليب الثلاث في مكان واحد:
 * اختيار الأساليب (StyleSelectionScreen) + صانع النمط (CreateStyleObjectScreen) +
 * مستنسخ الأساليب (VideoStyleClonerScreen).
 * الشاشات الأصلية باقية كما هي؛ حالات التنقل القديمة (STYLE_SELECTION / CREATE_STYLE_OBJECT /
 * VIDEO_STYLE_CLONER) تدخل كل واحدة على تبويبها الصحيح.
 */
@Composable
fun UnifiedStyleScreen(
    initialTab: Int = STYLE_TAB_SELECTION,
    onExit: () -> Unit,
    onApplyMasterStyle: (MasterStyle) -> Unit,
    onApplyStyleObject: (StyleObject) -> Unit,
    onGenerateProjectWithStyle: (String, VideoStyleAnalysis) -> Unit
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GoldPrimary
        ) {
            styleTabs.forEachIndexed { i, t ->
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
                STYLE_TAB_SELECTION -> StyleSelectionScreen(
                    onBack = onExit,
                    onApplyMasterStyleToProject = onApplyMasterStyle,
                    onCreateCustomStyle = { tab = STYLE_TAB_CREATE }
                )
                STYLE_TAB_CREATE -> CreateStyleObjectScreen(
                    onBack = { tab = STYLE_TAB_SELECTION },
                    onStyleSaved = { _ -> tab = STYLE_TAB_SELECTION },
                    onApplyDirectlyToProject = onApplyStyleObject
                )
                STYLE_TAB_CLONER -> VideoStyleClonerScreen(
                    onBack = onExit,
                    onGenerateProjectWithStyle = onGenerateProjectWithStyle
                )
            }
        }
    }
}

private data class StyleTab(val title: String, val icon: ImageVector)

private val styleTabs = listOf(
    StyleTab("اختيار الأساليب", Icons.Filled.Palette),
    StyleTab("صانع النمط", Icons.Filled.AddCircle),
    StyleTab("مستنسخ الأساليب", Icons.Filled.ContentCopy)
)

const val STYLE_TAB_SELECTION = 0
const val STYLE_TAB_CREATE = 1
const val STYLE_TAB_CLONER = 2
