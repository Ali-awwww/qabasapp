package com.qabas.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont

/**
 * عدّاد نقل موحّد (رفع/تنزيل) — يُستخدم في:
 * - عدّاد رفع الريلز (ReelPublishScreen)
 * - عدّاد رفع الأذان (DevAzanUploadDialog)
 * - عدّاد تنزيل الريلز (DownloadsHubScreen)
 * - عدّاد تنزيل الأذان (AzanVoicesDialog)
 * يعتمد على مدير التنزيلات DownloadsHubManager كمصدر للحالة.
 */
@Composable
fun TransferCounterBar(
    label: String,
    sentBytes: Long,
    totalBytes: Long,
    isUpload: Boolean,
    speedText: String? = null,
    modifier: Modifier = Modifier
) {
    val pct = if (totalBytes > 0) (sentBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = pct, animationSpec = tween(250), label = "transferCounter"
    )
    val accent = if (isUpload) Color(0xFFD4AF37) else Color(0xFF10B981)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0B1324),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                        contentDescription = null, tint = accent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                Text(
                    "${(animated * 100).toInt()}%",
                    color = accent, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${StorageMetrics.formatBytes(sentBytes)} / ${if (totalBytes > 0) StorageMetrics.formatBytes(totalBytes) else "…"}",
                    color = Color.White.copy(alpha = 0.65f), fontFamily = CairoFont, fontSize = 10.sp
                )
                if (speedText != null) {
                    Text(speedText, color = Color.White.copy(alpha = 0.65f), fontFamily = CairoFont, fontSize = 10.sp)
                }
            }
        }
    }
}
