package com.qabas.app

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.launch

/**
 * شارة تذييل تعرض رقم إصدار وبناء التطبيق الحقيقي مباشرة من نظام الأندرويد والبيلد (BuildConfig / PackageInfo).
 * بدون أي محاكاة أو أرقام ثابتة.
 * النقر على الشارة يجري فحصاً فورياً للتحديثات عبر UpdateManager مع إشعار المستخدم بالنتيجة.
 */
@Composable
fun AppRealVersionFooterBadge(
    modifier: Modifier = Modifier,
    onViewDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    // قراءة رقم النسخة ورقم البناء الفعلي والحقيقي من النظام
    val versionName = remember(context) {
        val detected = UpdateManager.getCurrentVersionName(context).trim()
        if (detected.isNotBlank()) detected else BuildConfig.VERSION_NAME
    }
    val versionCode = remember(context) {
        val code = UpdateManager.getCurrentVersionCode(context)
        if (code > 0) code else BuildConfig.VERSION_CODE
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp),
            color = Color(0xFF0B132B).copy(alpha = 0.88f),
            border = BorderStroke(
                0.9.dp,
                Brush.horizontalGradient(
                    listOf(
                        GoldPrimary.copy(alpha = 0.5f),
                        Color(0xFF00E5FF).copy(alpha = 0.4f),
                        GoldPrimary.copy(alpha = 0.5f)
                    )
                )
            ),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("app_version_footer_badge")
                .clickable(enabled = !isChecking) {
                    AppSoundManager.playClick()
                    scope.launch {
                        isChecking = true
                        Toast.makeText(context, "🔍 جاري فحص تحديثات قَبَس الرسمية...", Toast.LENGTH_SHORT).show()
                        try {
                            val update = UpdateManager.checkForUpdate(context, force = true)
                            if (update != null) {
                                Toast.makeText(
                                    context,
                                    "🚀 يتوفر تحديث جديد: v${update.versionName} (${update.versionCode})!",
                                    Toast.LENGTH_LONG
                                ).show()
                                onViewDownloads()
                            } else {
                                Toast.makeText(
                                    context,
                                    "✨ تطبيق قَبَس محدث لأحدث إصدار رسمي: v$versionName Build $versionCode",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "تعذر الاتصال بخادم التحديثات، تحقق من الإنترنت",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isChecking = false
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = isChecking,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "version_check_spinner"
                ) { checking ->
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.6.dp,
                            color = GoldPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "فحص التحديثات",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // صيغة الإصدار الحقيقي كما طلبها المستخدم تماماً: v1.3.3 Build 102
                Text(
                    text = "v$versionName",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.9f),
                    border = BorderStroke(0.6.dp, GoldPrimary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Build $versionCode",
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        color = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "اضغط للتحقق",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(12.dp)
                )

                Spacer(modifier = Modifier.width(3.dp))

                Text(
                    text = "فحص التحديثات",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
