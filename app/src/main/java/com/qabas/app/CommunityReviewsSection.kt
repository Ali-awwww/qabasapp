package com.qabas.app

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * قسم وبطاقة مراجعات وتقييمات المجتمع الحقيقية المرتبطة بسحابة Supabase (public.app_reviews).
 */
@Composable
fun CommunityReviewsCard(
    modifier: Modifier = Modifier,
    onOpenAllReviews: () -> Unit = {},
    onOpenSubmitReview: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var reviewsList by remember { mutableStateOf<List<SupabaseServices.AppReviewDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showReviewsDialog by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }

    fun loadReviews() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                SupabaseServices.AppReviews.getAppReviews()
            }
            result.onSuccess { list ->
                reviewsList = list
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.message
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadReviews()
    }

    val totalCount = reviewsList.size
    val averageRating = if (reviewsList.isNotEmpty()) {
        reviewsList.map { it.rating }.average()
    } else {
        0.0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "آراء ومراجعات المجتمع",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "تقييمات حقيقية موثقة سحابياً ✦",
                            color = qabasTextSecondary(),
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    color = GoldPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Text(
                        "Supabase Live 🛰️",
                        color = GoldPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NotoSansFont,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rating & Stats Display Box
            Surface(
                color = Color(0xFF09121E).copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Big Star Score
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GoldPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("جلب التقييمات...", color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
                        } else if (totalCount == 0) {
                            Text(
                                "5.0",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp
                            )
                            RatingStarsRow(rating = 5, size = 13.dp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "كن أول من يقيّم!",
                                color = qabasTextSecondary(),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        } else {
                            Text(
                                String.format(Locale.US, "%.1f", averageRating),
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp
                            )
                            RatingStarsRow(rating = averageRating.toInt().coerceIn(1, 5), size = 13.dp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "من إجمالي $totalCount تقييم",
                                color = qabasTextSecondary(),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(55.dp)
                            .background(Color(0xFF1E2D4A))
                    )

                    // Breakdown or Info
                    Column(
                        modifier = Modifier
                            .weight(1.4f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (totalCount > 0) {
                            val fiveStarCount = reviewsList.count { it.rating == 5 }
                            val fourStarCount = reviewsList.count { it.rating == 4 }
                            val threeStarCount = reviewsList.count { it.rating == 3 }

                            RatingProgressBar(label = "5 ★", count = fiveStarCount, total = totalCount, color = GoldPrimary)
                            RatingProgressBar(label = "4 ★", count = fourStarCount, total = totalCount, color = Color(0xFF10B981))
                            RatingProgressBar(label = "3 ★", count = threeStarCount, total = totalCount, color = Color(0xFF00E5FF))
                        } else {
                            Text(
                                "تقييمات المستخدمين تخزن مباشرة في سحابة Supabase ومربوطة بموديل الجهاز وإصدار التطبيق لضمان المصداقية التامة.",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = CairoFont,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button: View all reviews
                Button(
                    onClick = { showReviewsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.2f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "قراءة آراء المستخدمين (${totalCount})",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button: Submit review
                OutlinedButton(
                    onClick = { showSubmitDialog = true },
                    border = BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "قيّم التطبيق الآن",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button: Refresh
                IconButton(
                    onClick = { loadReviews() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "تحديث التقييمات",
                        tint = if (isLoading) GoldPrimary.copy(alpha = 0.4f) else GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Dialog: List of Reviews
    if (showReviewsDialog) {
        CommunityReviewsListDialog(
            reviews = reviewsList,
            averageRating = averageRating,
            isLoading = isLoading,
            onDismiss = { showReviewsDialog = false },
            onOpenSubmit = {
                showReviewsDialog = false
                showSubmitDialog = true
            },
            onRefresh = { loadReviews() },
            onHelpfulClicked = { review ->
                coroutineScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        SupabaseServices.AppReviews.markReviewHelpful(review.id, review.helpfulCount)
                    }
                    if (res.isSuccess) {
                        loadReviews()
                        Toast.makeText(context, "شكراً لإفادتك! تم تسجيل إعجابك بالمراجعة 👍", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Dialog: Submit New Review
    if (showSubmitDialog) {
        SubmitAppReviewDialog(
            onDismiss = { showSubmitDialog = false },
            onSubmitSuccess = {
                showSubmitDialog = false
                loadReviews()
                Toast.makeText(context, "تم إرسال تقييمك وحفظه في سحابة قبس بنجاح! جزاك الله خيراً 🌟", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
private fun RatingProgressBar(label: String, count: Int, total: Int, color: Color) {
    val progress = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontFamily = NotoSansFont,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = Color(0xFF1E293B),
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            color = Color(0xFFCBD5E1),
            fontSize = 9.sp,
            fontFamily = NotoSansFont,
            modifier = Modifier.width(16.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun RatingStarsRow(rating: Int, maxStars: Int = 5, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxStars) {
            Icon(
                imageVector = if (i <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i <= rating) GoldPrimary else Color(0xFF475569),
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * نافذة استعراض جميع تقييمات المجتمع
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityReviewsListDialog(
    reviews: List<SupabaseServices.AppReviewDto>,
    averageRating: Double,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onHelpfulClicked: (SupabaseServices.AppReviewDto) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }

    val filteredReviews = remember(reviews, selectedCategoryFilter) {
        if (selectedCategoryFilter == "الكل") reviews
        else reviews.filter { it.categoryRating == selectedCategoryFilter }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
            color = Color(0xFF0C1322)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GoldPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Forum, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "مراجعات مجتمع قبس",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "متوسط التقييم: ${if (reviews.isNotEmpty()) String.format(Locale.US, "%.1f", averageRating) else "5.0"} ★ (${reviews.size} مراجعة)",
                                    color = qabasTextSecondary(),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                }

                // Filter & Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة تقييمك", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // Reviews List
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GoldPrimary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("جاري الاتصال بسحابة قبس...", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }
                } else if (filteredReviews.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("لا توجد مراجعات حتى الآن", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("كن أول من يشارك رأيه وتجربته مع التطبيق!", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onOpenSubmit,
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("كتابة أول تقييم ✦", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredReviews, key = { it.id }) { review ->
                            ReviewItemCard(review = review, onHelpful = { onHelpfulClicked(review) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewItemCard(
    review: SupabaseServices.AppReviewDto,
    onHelpful: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top: Author & Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = review.authorName.ifBlank { "مستخدم قبس" },
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            if (review.verifiedUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = "موثوق", tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                            }
                        }
                        if (review.createdAt != null) {
                            Text(
                                text = formatReviewDate(review.createdAt),
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontFamily = NotoSansFont
                            )
                        }
                    }
                }

                RatingStarsRow(rating = review.rating, size = 13.dp)
            }

            // Comment text
            if (review.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = review.comment,
                    color = Color(0xFFE2E8F0),
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Device info & Helpful button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (review.deviceModel.isNotBlank()) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = review.deviceModel,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontFamily = NotoSansFont
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = review.appVersion,
                            color = GoldSecondary,
                            fontSize = 9.sp,
                            fontFamily = NotoSansFont,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Helpful button
                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable { onHelpful() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.ThumbUp, contentDescription = "مفيد", tint = Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "مفيد (${review.helpfulCount})",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontFamily = CairoFont
                        )
                    }
                }
            }
        }
    }
}

/**
 * نافذة إرسال تقييم جديد ومباشر
 */
@Composable
fun SubmitAppReviewDialog(
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE) }
    
    val savedName = remember {
        prefs.getString("user_display_name", null)
            ?: CloudServices.Auth.currentUser.value?.displayName
            ?: "مستخدم قبس"
    }

    var authorName by remember { mutableStateOf(savedName) }
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("overall") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val deviceInfo = remember {
        val man = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val mod = Build.MODEL
        val ver = Build.VERSION.RELEASE
        "$man $mod (Android $ver)"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
            color = Color(0xFF0C1322)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RateReview, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "تقييم تطبيق قبس",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Star Rating Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09121E), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (rating) {
                            5 -> "ممتاز جداً 🌟 (٥ من ٥)"
                            4 -> "جيد جداً ✨ (٤ من ٥)"
                            3 -> "جيد ومفيد 👍 (٣ من ٥)"
                            2 -> "متوسط ⚡ (٢ من ٥)"
                            else -> "يحتاج تحسين 🛠️ (١ من ٥)"
                        },
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            IconButton(
                                onClick = { rating = i },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= rating) GoldPrimary.copy(alpha = 0.15f) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = if (i <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$i نجوم",
                                    tint = if (i <= rating) GoldPrimary else Color(0xFF475569),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Author Name Field
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text("اسمك أو لقبك", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E2D4A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Comment Field
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("رأيك وملاحظاتك الصادقة حول التطبيق...", fontFamily = CairoFont, fontSize = 11.sp) },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E2D4A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Device Attachment Note
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سيتم إرفاق: $deviceInfo (v${BuildConfig.VERSION_NAME}) لتوثيق المصداقية",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontFamily = NotoSansFont
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "عذراً: $errorMessage",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Submit Button
                Button(
                    onClick = {
                        isSubmitting = true
                        errorMessage = null
                        coroutineScope.launch {
                            val res = withContext(Dispatchers.IO) {
                                SupabaseServices.AppReviews.submitAppReview(
                                    rating = rating,
                                    comment = comment,
                                    authorName = authorName,
                                    category = selectedCategory
                                )
                            }
                            isSubmitting = false
                            res.onSuccess {
                                onSubmitSuccess()
                            }.onFailure { e ->
                                errorMessage = e.message ?: "تعذر الإرسال، تحقق من اتصالك بالإنترنت"
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري الحفظ في السحابة...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إرسال التقييم واعتماده", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun formatReviewDate(dateStr: String): String {
    return runCatching {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val cleanStr = if (dateStr.length >= 19) dateStr.substring(0, 19) else dateStr
        val date = inputFormat.parse(cleanStr)
        val outputFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        if (date != null) outputFormat.format(date) else dateStr.take(10)
    }.getOrDefault(dateStr.take(10))
}
