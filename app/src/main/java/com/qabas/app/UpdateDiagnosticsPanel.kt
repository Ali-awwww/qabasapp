package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.CardSurface
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 🛠️ UpdateDiagnosticsPanel — لوحة تشخيص التحديث (للمطور فقط)
 *
 * تعرض كل ما يحتاجه المطور لتشخيص مسار التحديث:
 * - المستودع الفعلي ورابط الـ API
 * - النسخة المثبّتة (versionCode / versionName)
 * - آخر إصدار منشور وأصوله (APK / delta / xdelta لكل ABI)
 * - بصمة مفتاح التوقيع (SHA-256) وتعارض التوقيع
 * - زر تثبيت يدوي للحزمة المنزّلة + رابط صفحة الإصدارات
 *
 * لا تُعرض هذه اللوحة إطلاقاً لغير المطور (تُركّب خلف AdminGuard).
 */
@Composable
fun UpdateDiagnosticsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // الحماية: لا يُعرض أي شيء لغير المطوّر الموثّق سحابياً —
    // لا اسم مستودع، ولا رابط API، ولا بصمة توقيع، ولا إشارة إلى وجود لوحة مخفية
    if (!UpdateChannelFacade.isVerifiedDeveloper()) return

    var inspection by remember { mutableStateOf<UpdateManager.GitHubReleaseInspection?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var localApk by remember { mutableStateOf<java.io.File?>(null) }

    val currentVersionCode = remember { UpdateManager.getCurrentVersionCode(context) }
    val currentVersionName = remember { UpdateManager.getCurrentVersionName(context) }
    val signingInfo = remember { readSigningFingerprint(context) }

    LaunchedEffect(Unit) {
        localApk = UpdateManager.getLatestDownloadedUpdateApk(context)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.30f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── الترويسة ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تشخيص قناة التحديث",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "تفاصيل المصدر والتوقيع والإصدارات",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                }
            }

            // ── بيانات ثابتة ──
            DevInfoRow("المستودع الفعلي", UpdateChannelFacade.effectiveRepo(context))
            DevInfoRow("نقطة الفحص", UpdateChannelFacade.releasesApiUrl(context))
            DevInfoRow("النسخة المثبّتة", "v$currentVersionName ($currentVersionCode)")
            DevInfoRow("بصمة التوقيع", signingInfo)

            // ── آخر إصدار ──
            GeoDividerCompat()

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = GoldPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "جارٍ فحص الإصدارات…",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }

                errorText != null -> Text(
                    text = "⚠️ $errorText",
                    color = Color(0xFFEF4444),
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                inspection != null -> {
                    val ins = inspection!!
                    DevInfoRow("آخر وسم", ins.tagName.ifBlank { "—" })
                    DevInfoRow("اسم الإصدار", ins.releaseName.ifBlank { "—" })
                    DevInfoRow("عدد الأصول", "${ins.totalAssetsCount}")
                    DevInfoRow(
                        "أصل APK",
                        ins.apkAsset?.let { "$it (${UpdateManager.formatSize(ins.apkSize)})" } ?: "غير موجود"
                    )
                    DevInfoRow(
                        "أصل delta",
                        ins.deltaAsset?.let { "$it (${UpdateManager.formatSize(ins.deltaSize)})" } ?: "غير موجود"
                    )
                    DevInfoRow(
                        "ثنائيات xdelta",
                        if (ins.xdeltaBinaries.isEmpty()) "غير موجودة"
                        else ins.xdeltaBinaries.joinToString(", ")
                    )
                    DevInfoRow(
                        "أحدث من المثبّت؟",
                        if (ins.isNewerThanCurrent) "نعم ✅" else "لا (النسخة الحالية أحدث أو مساوية)"
                    )
                }
            }

            // ── الحزمة المنزّلة ──
            if (localApk != null) {
                DevInfoRow(
                    "حزمة منزّلة",
                    "${localApk!!.name} (${UpdateManager.formatSize(localApk!!.length())})"
                )
            }

            // ── أزرار ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        loading = true
                        errorText = null
                        scope.launch {
                            val result = UpdateManager.inspectGitHubRelease(context)
                            result.onSuccess {
                                inspection = it
                                errorText = null
                            }.onFailure {
                                errorText = it.message ?: "فشل الفحص"
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null, tint = DeepSlate, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "فحص الآن",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChannelFacade.releasesPageUrl(context)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "صفحة الإصدارات",
                        color = Color(0xFF00E5FF),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            // ── تثبيت يدوي + نسخ التقرير ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val apk = localApk
                        if (apk != null) {
                            UpdateManager.launchInstaller(context, apk)
                        } else {
                            scope.launch {
                                val result = UpdateManager.inspectGitHubRelease(context)
                                result.onSuccess { inspection = it }
                                    .onFailure { errorText = it.message }
                            }
                        }
                    },
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (localApk != null) "تثبيت الآن" else "تثبيت بعد الفحص",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        val report = buildString {
                            appendLine("=== تقرير تشخيص التحديث ===")
                            appendLine("المستودع: ${UpdateChannelFacade.effectiveRepo(context)}")
                            appendLine("API: ${UpdateChannelFacade.releasesApiUrl(context)}")
                            appendLine("النسخة المثبتة: v$currentVersionName ($currentVersionCode)")
                            appendLine("بصمة التوقيع: $signingInfo")
                            inspection?.let {
                                appendLine("آخر وسم: ${it.tagName}")
                                appendLine("أصول APK: ${it.apkAsset}")
                                appendLine("أصول delta: ${it.deltaAsset}")
                                appendLine("xdelta: ${it.xdeltaBinaries.joinToString(", ")}")
                            }
                            localApk?.let { appendLine("حزمة منزلة: ${it.absolutePath}") }
                        }
                        clipboard.setText(AnnotatedString(report))
                    },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "نسخ التقرير",
                        color = Color.White.copy(alpha = 0.8f),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** صف معلومات: تسمية + قيمة (نص أحادي المسافة للقيم التقنية) */
@Composable
private fun DevInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontFamily = CairoFont,
            fontSize = 10.5.sp,
            modifier = Modifier.weight(0.38f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            color = Color.White,
            fontFamily = RobotoMonoFont,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(0.62f)
        )
    }
}

@Composable
private fun GeoDividerCompat() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

/**
 * بصمة مفتاح التوقيع SHA-256 للنسخة المثبتة — للمطور فقط.
 * تُستعمل لمطابقة ما في CI.
 */
private fun readSigningFingerprint(context: Context): String {
    return try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signing = info.signingInfo
            if (signing != null && signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing?.signingCertificateHistory ?: emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                .signatures ?: emptyArray()
        }
        if (signatures.isEmpty()) {
            "غير متاح"
        } else {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            signatures.first().let { sig ->
                md.update(sig.toByteArray())
                md.digest().joinToString(":") { "%02X".format(it) }.take(47)
            }
        }
    } catch (e: Exception) {
        "غير متاح (${e.message})"
    }
}
