package com.qabas.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.RateReview
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.TextSecondary

/**
 * شاشة النشر الموحّدة — تجمع نهاية خط الإنتاج في مكان واحد:
 * مراجعة الإنتاج (ReviewScreen) + التصدير السينمائي (SaveShareScreen) + نشر الريلز (ReelPublishScreen).
 * الشاشات الأصلية باقية كما هي؛ حالتا REVIEW وSAVE_SHARE تدخلان على التبويب المناسب.
 */
@Composable
fun UnifiedPublishScreen(
    initialTab: Int = PUBLISH_TAB_REVIEW,
    state: ProjectState,
    onExit: () -> Unit,
    onUpdateScenes: (List<Scene>) -> Unit,
    onUpdateAmbientSound: (String) -> Unit,
    onAdvancedEdit: () -> Unit,
    onApprove: () -> Unit,
    onBackToEditor: () -> Unit,
    onStartSeries: () -> Unit,
    onRequireLogin: () -> Unit = {}
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val userName = remember {
        val prefs = context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE)
        prefs.getString("user_name", "صانع محتوى قبس") ?: "صانع محتوى قبس"
    }
    val userEmail = remember {
        val prefs = context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE)
        prefs.getString("user_email", "guest@qabas.studio") ?: "guest@qabas.studio"
    }
    val authId = remember { SupabaseServices.Auth.getCurrentUserId() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GoldPrimary
        ) {
            publishTabs.forEachIndexed { i, t ->
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
                PUBLISH_TAB_REVIEW -> ReviewScreen(
                    state = state,
                    onBack = onExit,
                    onUpdateScenes = onUpdateScenes,
                    onUpdateAmbientSound = onUpdateAmbientSound,
                    onAdvancedEdit = onAdvancedEdit,
                    onApprove = onApprove
                )
                PUBLISH_TAB_EXPORT -> SaveShareScreen(
                    scriptText = state.inputText,
                    videoDuration = state.videoDuration,
                    selectedRatio = state.selectedRatio,
                    scenes = state.libraryScenes,
                    ambientSound = state.ambientSound,
                    videoStyleAnalysis = state.videoStyleAnalysis,
                    onBackToHome = onExit,
                    onBackToEditor = onBackToEditor,
                    onStartSeries = onStartSeries
                )
                PUBLISH_TAB_PUBLISH -> {
                    if (authId.isNullOrBlank()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                "سجّل الدخول أولاً لنشر مقطعك للمجتمع",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 16.sp
                            )
                            Text(
                                "النشر يتطلب حساباً في قبس حتى تُنسب أعمالك إليك",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        ReelPublishScreen(
                            currentUserId = authId,
                            currentUserName = userName,
                            currentUserEmail = userEmail,
                            onDismiss = { tab = PUBLISH_TAB_REVIEW },
                            onPublishedSuccess = { },
                            onRequireLogin = onRequireLogin
                        )
                    }
                }
            }
        }
    }
}

private data class PublishTab(val title: String, val icon: ImageVector)

private val publishTabs = listOf(
    PublishTab("مراجعة الإنتاج", Icons.Filled.RateReview),
    PublishTab("التصدير السينمائي", Icons.Filled.FileDownload),
    PublishTab("نشر الريلز", Icons.Filled.Publish)
)

const val PUBLISH_TAB_REVIEW = 0
const val PUBLISH_TAB_EXPORT = 1
const val PUBLISH_TAB_PUBLISH = 2
