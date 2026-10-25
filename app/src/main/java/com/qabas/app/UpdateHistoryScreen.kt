package com.qabas.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            TopAppBar(
                title = {
                    Text(
                        "تاريخ التحديثات",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = GoldPrimary
                )
            } else if (history.isEmpty()) {
                Text(
                    "لا يوجد سجل تحديثات حالياً",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray,
                    fontFamily = CairoFont
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(GoldPrimary.copy(alpha = 0.3f), Color.Transparent)),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.5f))
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
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "بناء رقم ${update.versionCode}",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                
                if (update.isForce) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "تحديث إجباري",
                            color = Color.Red,
                            fontSize = 10.sp,
                            fontFamily = CairoFont,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "ما الجديد:",
                color = GoldSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = update.releaseNotes.ifBlank { "لم يتم توفير ملاحظات لهذا الإصدار." },
                color = TextPrimary,
                fontFamily = CairoFont,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            
            if (update.apkSize > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حجم الحزمة: ${UpdateManager.formatSize(update.apkSize)}",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
