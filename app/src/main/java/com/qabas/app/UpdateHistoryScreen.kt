package com.qabas.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun UpdateHistoryScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = QabasTheme.current
    var history by remember { mutableStateOf<List<UpdateManager.UpdateInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            history = UpdateManager.getUpdateHistory(context)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            AppleTopBar(
                title = "تاريخ التحديثات",
                subtitle = "سجل التحديثات والإصدارات السابقة",
                onBack = onBack
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = theme.primary
                )
            } else if (history.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = theme.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "لا يوجد سجل تحديثات حالياً",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(history) { update ->
                        UpdateHistoryItem(update)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateHistoryItem(update: UpdateManager.UpdateInfo) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = theme.cardSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "إصدار ${update.versionName}",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "بناء رقم ${update.versionCode}",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }

                if (update.isForce) {
                    Surface(
                        color = theme.destructive.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.6.dp, theme.destructive.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "تحديث إجباري",
                            color = theme.destructive,
                            fontSize = 11.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = theme.separator.copy(alpha = 0.45f),
                thickness = 0.5.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ما الجديد:",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = update.releaseNotes.ifBlank { "لم يتم توفير ملاحظات لهذا الإصدار." },
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            )

            if (update.apkSize > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = theme.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حجم الحزمة: ${UpdateManager.formatSize(update.apkSize)}",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
