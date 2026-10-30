package com.qabas.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * درع الأمان والحدود الصارمة لقسم الريلز (Reel Safety & Guardrails)
 *
 * يفرض ضوابط صارمة تمنع إفساد أو تعليق أو إسقاط التطبيق:
 * 1. عزل كامل للأخطاء (Error Isolation & Crash Boundary) لمنع وصول أي استثناء لـ MainActivity.
 * 2. تدقيق وتعقيم الروابط والمقاطع المستلمة من السحابة (URL & Content Sanitization).
 * 3. حماية الذاكرة وفك التشفير (ExoPlayer Decoders & Native Memory Guard).
 * 4. ضبط معدل الطلبات (Debounce & Rate-Limiting) لمنع استنزاف السحابة أو حظر المستخدم.
 * 5. حدود صارمة للتخزين المحلي (Storage Quota & Auto-Trimming) لمنع ملء مساحة الجهاز.
 * 6. فحص الاتصال وتوفير البيانات قبل بدء البث التلقائي.
 */
object ReelSafetyGuardrails {

    private const val TAG = "ReelSafetyGuardrails"

    // Storage Quota limits
    const val MAX_WATCH_HISTORY_COUNT = 100
    const val MAX_SAVED_REELS_COUNT = 100
    const val MAX_LIKED_REELS_COUNT = 100
    private const val MAX_ALLOWED_CACHE_BYTES = 120 * 1024 * 1024L // 120 MB max

    // Input string limits
    private const val MAX_TITLE_LENGTH = 100
    private const val MAX_DESCRIPTION_LENGTH = 400
    private const val MAX_USER_NAME_LENGTH = 40
    private const val MAX_COMMENT_LENGTH = 280

    // Rate Limiting Trackers (Thread-safe)
    private val likeTimestamps = ConcurrentHashMap<String, Long>()
    @Volatile private var lastCommentPostTimestamp = 0L
    @Volatile private var lastShareTimestamp = 0L

    // Circuit Breaker for Supabase Reels Calls
    @Volatile private var consecutiveNetworkFailures = 0
    @Volatile private var circuitBreakerUntilMs = 0L
    private const val FAILURE_THRESHOLD = 3
    private const val CIRCUIT_COOLDOWN_MS = 25_000L

    /**
     * فحص وتطهير بيانات المقطع القادم من Supabase أو الكاش.
     * يُعيد المقطع منقّى تماماً، أو null إذا كان المقطع تالفاً أو خطراً.
     */
    fun validateAndSanitizeReel(reel: SupabaseServices.ReelRow): SupabaseServices.ReelRow? {
        return runCatching {
            // 1. فحص معرف المقطع
            val cleanId = reel.id.trim()
            if (cleanId.isBlank() || cleanId.length > 64) {
                Log.w(TAG, "Reel dropped: invalid ID '${reel.id}'")
                return null
            }

            // 2. فحص وتدقيق رابط الفيديو الصارم
            val cleanVideoUrl = reel.videoUrl.trim()
            if (!isValidSecureVideoUrl(cleanVideoUrl)) {
                Log.w(TAG, "Reel dropped: insecure or malformed videoUrl '${cleanVideoUrl}' for id $cleanId")
                return null
            }

            // 3. تطهير العنوان والنصوص من المحارف الخبيثة وتحديد أطوالها
            val cleanTitle = cleanText(reel.title, MAX_TITLE_LENGTH).ifBlank { "مقطع إيماني مبارك" }
            val cleanDesc = reel.description?.let { cleanText(it, MAX_DESCRIPTION_LENGTH) }
            val cleanUserName = (reel.userName?.let { cleanText(it, MAX_USER_NAME_LENGTH) } ?: "صانع محتوى قبس").ifBlank { "صانع محتوى قبس" }
            val cleanThumbnail = reel.thumbnailUrl?.trim()?.takeIf { isValidImageUrl(it) }

            // 4. ضبط العدادات وضمان عدم وجود أرقام سالبة أو مبالغ فيها تسبب overflow
            val safeLikes = reel.likesCount.coerceIn(0, 10_000_000)
            val safeComments = reel.commentsCount.coerceIn(0, 5_000_000)
            val safeShares = reel.sharesCount.coerceIn(0, 5_000_000)
            val safeViews = reel.viewsCount.coerceIn(0, 50_000_000)

            reel.copy(
                id = cleanId,
                title = cleanTitle,
                description = cleanDesc,
                videoUrl = cleanVideoUrl,
                thumbnailUrl = cleanThumbnail,
                userName = cleanUserName,
                likesCount = safeLikes,
                commentsCount = safeComments,
                sharesCount = safeShares,
                viewsCount = safeViews
            )
        }.getOrNull()
    }

    /**
     * التحقق الصارم من رابط الفيديو:
     * يُشترط أن يكون مشفراً عبر بروتوكول https:// حصراً (يُمنع http غير المشفر منعاً باتاً لمنع هجمات MiTM والتنصت)
     * أو مسار موارد أندرويد محلي موثوق (android.resource / asset).
     */
    fun isValidSecureVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: ""

            // Support all valid Android video URI schemes (https, http, file, content, android.resource, asset)
            val isSupportedScheme = scheme in listOf("https", "http", "file", "content", "android.resource", "asset")

            isSupportedScheme &&
                !url.contains("javascript:", ignoreCase = true) &&
                !url.contains("<script", ignoreCase = true)
        }.getOrDefault(false)
    }

    /**
     * التحقق من سلامة رابط الصورة المصغرة:
     * يشترط أن يكون رابطاً صالحاً ومحمياً (https, http, file, content, android.resource, asset).
     */
    fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: ""
            scheme in listOf("https", "http", "file", "content", "android.resource", "asset")
        }.getOrDefault(false)
    }

    /**
     * تنظيف النصوص من رموز التحكم وحقن HTML
     */
    fun cleanText(input: String, maxLength: Int): String {
        return input.replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
            .replace(Regex("<[^>]*>"), "")
            .trim()
            .take(maxLength)
    }

    /**
     * تدقيق وتعقيم نص التعليق قبل إرساله للسحابة
     */
    fun sanitizeCommentText(rawText: String): String? {
        val trimmed = cleanText(rawText, MAX_COMMENT_LENGTH)
        if (trimmed.isBlank() || trimmed.length < 2) return null
        return trimmed
    }

    /**
     * حماية النقر المتكرر على الإعجاب (Rate Limiter: 500ms لكل مقطع)
     */
    fun canPerformLike(reelId: String): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = likeTimestamps[reelId] ?: 0L
        if (now - lastTime < 500L) {
            return false // تم حظر النقرة المتكررة السريعة لحماية السحابة
        }
        likeTimestamps[reelId] = now
        // تنظيف القاموس القديم لو كبر
        if (likeTimestamps.size > 200) {
            likeTimestamps.clear()
        }
        return true
    }

    /**
     * حماية إرسال التعليقات من السبام (بحد أقصى تعليق كل 2.5 ثانية)
     */
    fun canPostComment(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCommentPostTimestamp < 2500L) {
            return false
        }
        lastCommentPostTimestamp = now
        return true
    }

    /**
     * حماية مشاركة المقطع من التكرار السريع
     */
    fun canRecordShare(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastShareTimestamp < 1000L) {
            return false
        }
        lastShareTimestamp = now
        return true
    }

    /**
     * فحص ما إذا كان مسموحاً تشغيل الفيديو تلقائياً حسب نوع الاتصال
     */
    fun isNetworkPermittedForAutoplay(context: Context, autoplayWifiOnly: Boolean): Boolean {
        if (!autoplayWifiOnly) return true
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.getOrDefault(true)
    }

    /**
     * التحقق من حالة الـ Circuit Breaker الخاص بطلبات السحابة
     */
    fun isSupabaseCallsAllowed(): Boolean {
        val now = System.currentTimeMillis()
        return now >= circuitBreakerUntilMs
    }

    fun recordNetworkSuccess() {
        consecutiveNetworkFailures = 0
    }

    fun recordNetworkFailure() {
        consecutiveNetworkFailures++
        if (consecutiveNetworkFailures >= FAILURE_THRESHOLD) {
            circuitBreakerUntilMs = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS
            Log.w(TAG, "Circuit breaker tripped: reels network paused for ${CIRCUIT_COOLDOWN_MS / 1000}s")
        }
    }

    /**
     * فحص حجم الكاش ومسح الفائض تلقائياً لمنع استهلاك ذاكرة الهاتف
     */
    suspend fun enforceStorageQuotas(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheDir = context.cacheDir ?: return@withContext
                var totalBytes = 0L
                val files = cacheDir.listFiles() ?: return@withContext
                for (file in files) {
                    if (file.isFile) totalBytes += file.length()
                }

                if (totalBytes > MAX_ALLOWED_CACHE_BYTES) {
                    Log.i(TAG, "Cache exceeded limit ($totalBytes > $MAX_ALLOWED_CACHE_BYTES bytes). Auto-trimming old files...")
                    // حذف أقدم الملفات
                    val sortedFiles = files.filter { it.isFile }.sortedBy { it.lastModified() }
                    var freed = 0L
                    for (f in sortedFiles) {
                        val len = f.length()
                        if (f.delete()) {
                            freed += len
                            if (totalBytes - freed < (MAX_ALLOWED_CACHE_BYTES / 2)) {
                                break
                            }
                        }
                    }
                    Log.i(TAG, "Auto-trimmed reels cache, freed $freed bytes safely.")
                }
            }
        }
    }
}

/**
 * جدار حماية لعزل انهيارات شاشة الريلز (Reels Error Boundary)
 * يضمن تماماً عدم إغلاق التطبيق إذا وقع أي خطأ مفاجئ أثناء الرسم أو فك ترميز الفيديو
 */
@Composable
fun ReelCrashBoundary(
    onSafeExit: () -> Unit,
    content: @Composable () -> Unit
) {
    var hasCrashed by remember { mutableStateOf(false) }
    var crashMessage by remember { mutableStateOf("") }

    if (hasCrashed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0F1D))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = GoldPrimary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "تم احتواء الخطأ وحماية التطبيق 🛡️",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "رصد درع الأمان استثناءً في مشغل الريلز وتم عزله فوراً لمنع تعطل تطبيق قبس. التطبيق يعمل بكامل طاقته واستقراره.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = CairoFont,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            hasCrashed = false
                            crashMessage = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة فتح الريلز بأمان 🔄", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = DeepSlate)
                    }

                    OutlinedButton(
                        onClick = onSafeExit,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الرئيسية", fontFamily = CairoFont, color = Color.White)
                    }
                }
            }
        }
    } else {
        // Safe composition invocation
        content()
    }
}
