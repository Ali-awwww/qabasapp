package com.qabas.app

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
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
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    var reviewsList by remember { mutableStateOf<List<SupabaseServices.AppReviewDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showReviewsDialog by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var myHelpfulVotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reviewToEdit by remember { mutableStateOf<SupabaseServices.AppReviewDto?>(null) }
    var reviewToDelete by remember { mutableStateOf<SupabaseServices.AppReviewDto?>(null) }
    var reviewToReport by remember { mutableStateOf<SupabaseServices.AppReviewDto?>(null) }

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
            currentUserId = SupabaseServices.Auth.getCurrentUserId()
            myHelpfulVotes = withContext(Dispatchers.IO) {
                SupabaseServices.AppReviews.getMyHelpfulVoteReviewIds()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadReviews()
    }

    val totalCount = reviewsList.size
    val averageRating = if (reviewsList.isNotEmpty()) reviewsList.map { it.rating }.average() else 0.0

    AppleCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(theme.warning.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = theme.warning, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "آراء ومراجعات المجتمع",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        Text(
                            "تقييمات حقيقية موثقة عبر Supabase",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = { loadReviews() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "تحديث التقييمات",
                        tint = if (isLoading) theme.textSecondary else theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.55f else 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = theme.primary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("جلب التقييمات...", color = theme.textSecondary, fontSize = 10.5.sp, fontFamily = NotoSansFont)
                        } else if (totalCount == 0) {
                            // لا متوسط ولا نجوم قبل وجود أي تقييم: عرض متوسط
                            // وهمي ونجوم ممتلئة لصفر مراجعات كان تقييماً مختلقاً.
                            if (errorMessage != null) {
                                Text(
                                    "—",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 26.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "تعذر الجلب",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp
                                )
                            } else {
                                Icon(
                                    Icons.Default.RateReview,
                                    contentDescription = null,
                                    tint = theme.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "لا تقييمات بعد",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "كن أول من يقيّم",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp
                                )
                            }
                        } else {
                            Text(
                                String.format(Locale.US, "%.1f", averageRating),
                                color = theme.warning,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp
                            )
                            RatingStarsRow(rating = averageRating.toInt().coerceIn(1, 5), size = 14.dp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "من إجمالي $totalCount تقييم",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(54.dp)
                            .background(theme.separator.copy(alpha = 0.6f))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1.4f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (totalCount > 0) {
                            val fiveStarCount = reviewsList.count { it.rating == 5 }
                            val fourStarCount = reviewsList.count { it.rating == 4 }
                            val threeStarCount = reviewsList.count { it.rating == 3 }

                            RatingProgressBar("5 ★", fiveStarCount, totalCount, theme.warning)
                            RatingProgressBar("4 ★", fourStarCount, totalCount, theme.success)
                            RatingProgressBar("3 ★", threeStarCount, totalCount, theme.primary)
                        } else {
                            Text(
                                "تخزن مراجعات المستخدمين عبر Supabase وتربط بإصدار التطبيق لضمان الشفافية.",
                                color = theme.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = NotoSansFont,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApplePrimaryButton(
                    text = "قراءة الآراء ($totalCount)",
                    icon = Icons.Default.Forum,
                    onClick = { showReviewsDialog = true },
                    modifier = Modifier.weight(1.1f)
                )

                AppleSecondaryButton(
                    text = "قيّم التطبيق",
                    icon = Icons.Default.RateReview,
                    onClick = { showSubmitDialog = true },
                    modifier = Modifier.weight(0.9f)
                )
            }
        }
    }

    if (showReviewsDialog) {
        CommunityReviewsListDialog(
            reviews = reviewsList,
            averageRating = averageRating,
            isLoading = isLoading,
            currentUserId = currentUserId,
            myHelpfulVotes = myHelpfulVotes,
            onDismiss = { showReviewsDialog = false },
            onOpenSubmit = {
                showReviewsDialog = false
                showSubmitDialog = true
            },
            onRefresh = { loadReviews() },
            onHelpfulToggle = { review ->
                coroutineScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        SupabaseServices.AppReviews.toggleHelpfulVote(review.id)
                    }
                    res.onSuccess {
                        loadReviews()
                    }.onFailure { e ->
                        Toast.makeText(
                            context,
                            e.message ?: "تعذر تسجيل التصويت",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onEditReview = { review ->
                reviewToEdit = review
                showReviewsDialog = false
                showSubmitDialog = true
            },
            onDeleteReview = { review -> reviewToDelete = review },
            onReportReview = { review -> reviewToReport = review }
        )
    }

    if (showSubmitDialog) {
        SubmitAppReviewDialog(
            existingReview = reviewToEdit,
            onDismiss = {
                showSubmitDialog = false
                reviewToEdit = null
            },
            onSubmitSuccess = {
                showSubmitDialog = false
                reviewToEdit = null
                loadReviews()
                Toast.makeText(context, "تم حفظ تقييمك بنجاح", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (reviewToDelete != null) {
        val target = reviewToDelete!!
        AlertDialog(
            onDismissRequest = { reviewToDelete = null },
            title = {
                Text(
                    "حذف تقييمك",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Text(
                    "سيُحذف تقييمك نهائياً ولا يمكن التراجع عن الحذف.",
                    fontFamily = NotoSansFont,
                    color = theme.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reviewToDelete = null
                        coroutineScope.launch {
                            val res = withContext(Dispatchers.IO) {
                                SupabaseServices.AppReviews.deleteMyReview(target.id)
                            }
                            res.onSuccess { loadReviews() }
                                .onFailure { e ->
                                    Toast.makeText(
                                        context,
                                        e.message ?: "تعذر حذف التقييم",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }
                ) { Text("حذف", color = theme.destructive, fontFamily = CairoFont) }
            },
            dismissButton = {
                TextButton(onClick = { reviewToDelete = null }) {
                    Text("إلغاء", fontFamily = CairoFont)
                }
            }
        )
    }

    if (reviewToReport != null) {
        val target = reviewToReport!!
        ReportReviewDialog(
            onDismiss = { reviewToReport = null },
            onReasonSelected = { reason ->
                reviewToReport = null
                coroutineScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        SupabaseServices.AppReviews.submitReviewReport(target.id, reason)
                    }
                    res.onSuccess {
                        Toast.makeText(context, "تم إرسال البلاغ، شكراً لك", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(
                            context,
                            e.message ?: "تعذر إرسال البلاغ",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun RatingProgressBar(label: String, count: Int, total: Int, color: Color) {
    val theme = QabasTheme.current
    val progress = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = theme.textSecondary,
            fontSize = 10.sp,
            fontFamily = NotoSansFont,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = theme.separator.copy(alpha = 0.3f),
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            color = theme.textPrimary,
            fontSize = 10.sp,
            fontFamily = NotoSansFont,
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun RatingStarsRow(rating: Int, maxStars: Int = 5, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val theme = QabasTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxStars) {
            Icon(
                imageVector = if (i <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i <= rating) theme.warning else theme.textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
fun CommunityReviewsListDialog(
    reviews: List<SupabaseServices.AppReviewDto>,
    averageRating: Double,
    isLoading: Boolean,
    currentUserId: String? = null,
    myHelpfulVotes: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onOpenSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onHelpfulToggle: (SupabaseServices.AppReviewDto) -> Unit,
    onEditReview: (SupabaseServices.AppReviewDto) -> Unit = {},
    onDeleteReview: (SupabaseServices.AppReviewDto) -> Unit = {},
    onReportReview: (SupabaseServices.AppReviewDto) -> Unit = {}
) {
    val theme = QabasTheme.current
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
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(22.dp),
            color = theme.background,
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.cardSurface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "مراجعات مجتمع قبس",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (reviews.isNotEmpty()) "متوسط التقييم: ${String.format(Locale.US, "%.1f", averageRating)} ★ (${reviews.size} مراجعة)"
                                else "لا تقييمات بعد",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textSecondary)
                    }
                }

                HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApplePrimaryButton(
                        text = "إضافة تقييمك",
                        icon = Icons.Default.AddComment,
                        onClick = onOpenSubmit,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = theme.primary)
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = theme.primary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("جاري الاتصال بسحابة قبس...", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
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
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = theme.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("لا توجد مراجعات حتى الآن", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("كن أول من يشارك رأيه وتجربته مع التطبيق.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            ApplePrimaryButton(text = "كتابة أول تقييم", onClick = onOpenSubmit)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredReviews, key = { it.id }) { review ->
                            ReviewItemCard(
                                review = review,
                                isMine = currentUserId != null && review.userId == currentUserId,
                                isHelpfulByMe = myHelpfulVotes.contains(review.id),
                                onHelpful = { onHelpfulToggle(review) },
                                onEdit = { onEditReview(review) },
                                onDelete = { onDeleteReview(review) },
                                onReport = { onReportReview(review) }
                            )
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
    isMine: Boolean = false,
    isHelpfulByMe: Boolean = false,
    onHelpful: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReport: () -> Unit = {}
) {
    val theme = QabasTheme.current
    AppleCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = review.authorName.ifBlank { "مستخدم قبس" },
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (review.verifiedUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = "بريد مؤكد", tint = theme.success, modifier = Modifier.size(13.dp))
                            }
                            if (isMine) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = theme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "تقييمك",
                                        color = theme.primary,
                                        fontSize = 10.sp,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (review.createdAt != null) {
                            Text(
                                text = formatReviewDate(review.createdAt),
                                color = theme.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = NotoSansFont
                            )
                        }
                    }
                }

                RatingStarsRow(rating = review.rating, size = 14.dp)
            }

            if (review.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = review.comment,
                    color = theme.textPrimary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (review.deviceModel.isNotBlank()) {
                        Surface(
                            color = theme.secondarySurface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(review.deviceModel, color = theme.textSecondary, fontSize = 10.sp, fontFamily = NotoSansFont)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // بلا إصدار مختلق: الشارة تظهر فقط عند وجود إصدار حقيقي.
                    if (review.appVersion.isNotBlank()) {
                        Surface(color = theme.secondarySurface, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = review.appVersion,
                                color = theme.primary,
                                fontSize = 10.sp,
                                fontFamily = NotoSansFont,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isMine) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل تقييمك", tint = theme.primary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف تقييمك", tint = theme.destructive, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(onClick = onReport, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Flag, contentDescription = "الإبلاغ عن المراجعة", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // "مفيد" صوت واحد قابل للسحب لكل مستخدم؛ العدد من صفوف
                    // الأصوات الحقيقية، لا زيادة بلا حد عند كل نقرة.
                    Surface(
                        color = if (isHelpfulByMe) theme.primary.copy(alpha = 0.18f) else theme.secondarySurface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onHelpful() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "مفيد",
                                tint = if (isHelpfulByMe) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مفيد (${review.helpfulCount})",
                                color = theme.textPrimary,
                                fontSize = 10.5.sp,
                                fontFamily = CairoFont
                            )
                        }
                    }
                }
            }
        }
    }
}

/** حوار اختيار سبب البلاغ على مراجعة (يُحفظ صفاً في `review_reports`). */
@Composable
private fun ReportReviewDialog(
    onDismiss: () -> Unit,
    onReasonSelected: (String) -> Unit
) {
    val theme = QabasTheme.current
    val reasons = listOf("محتوى مسيء", "معلومات غير صحيحة", "بريد مزعج", "أخرى")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "الإبلاغ عن المراجعة",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "اختر سبب البلاغ:",
                    fontFamily = NotoSansFont,
                    color = theme.textSecondary,
                    fontSize = 12.sp
                )
                reasons.forEach { reason ->
                    TextButton(
                        onClick = { onReasonSelected(reason) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            reason,
                            fontFamily = CairoFont,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", fontFamily = CairoFont) }
        }
    )
}

@Composable
fun SubmitAppReviewDialog(
    existingReview: SupabaseServices.AppReviewDto? = null,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE) }

    // الاسم الحقيقي للمستخدم المسجّل؛ الضيوف لا يرسلون تقييماً إطلاقاً.
    val displayName = remember {
        prefs.getString("user_display_name", null)
            ?: CloudServices.Auth.currentUser.value?.displayName
            ?: "مستخدم قبس"
    }

    var currentUserId by remember { mutableStateOf(SupabaseServices.Auth.getCurrentUserId()) }
    var rating by remember { mutableIntStateOf(existingReview?.rating?.coerceIn(1, 5) ?: 5) }
    var comment by remember { mutableStateOf(existingReview?.comment ?: "") }
    var selectedCategory by remember { mutableStateOf(existingReview?.categoryRating ?: "overall") }
    var editingReviewId by remember { mutableStateOf(existingReview?.id) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // مراجعة واحدة لكل مستخدم: عند الفتح بلا مراجعة محددة، نتحقق هل
    // للمستخدم تقييم سابق؛ فإن وُجد يُفتح الحوار في وضع التعديل بدل
    // إرسال تقييم ثانٍ.
    LaunchedEffect(Unit) {
        currentUserId = SupabaseServices.Auth.getCurrentUserId()
        if (existingReview == null && currentUserId != null) {
            val mine = withContext(Dispatchers.IO) {
                SupabaseServices.AppReviews.getMyReview().getOrNull()
            }
            if (mine != null) {
                editingReviewId = mine.id
                rating = mine.rating.coerceIn(1, 5)
                comment = mine.comment
                selectedCategory = mine.categoryRating
            }
        }
    }

    val isEditing = editingReviewId != null
    val isGuest = currentUserId == null

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
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(22.dp),
            color = theme.cardSurface,
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RateReview, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isEditing) "تعديل تقييمك" else "تقييم تطبيق قبس",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء", tint = theme.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isGuest) {
                    Text(
                        text = "سجّل الدخول أولاً لإرسال تقييمك — لا يمكن للضيوف التقييم.",
                        color = theme.destructive,
                        fontSize = 12.sp,
                        fontFamily = NotoSansFont
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (isEditing) {
                    Text(
                        text = "لديك تقييم سابق — تعديله يحدّث التقييم نفسه بدل إرسال تقييم ثانٍ.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = NotoSansFont
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.secondarySurface.copy(alpha = 0.65f))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (rating) {
                            5 -> "ممتاز جداً (٥ من ٥)"
                            4 -> "جيد جداً (٤ من ٥)"
                            3 -> "جيد ومفيد (٣ من ٥)"
                            2 -> "متوسط (٢ من ٥)"
                            else -> "يحتاج تحسين (١ من ٥)"
                        },
                        color = theme.warning,
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
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= rating) theme.warning.copy(alpha = 0.14f) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = if (i <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$i نجوم",
                                    tint = if (i <= rating) theme.warning else theme.textSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // الاسم الحقيقي للمستخدم المسجّل — عرض فقط، لا إدخال حر لأي اسم.
                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayName,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("رأيك وملاحظاتك حول التطبيق...", fontFamily = CairoFont, fontSize = 12.sp) },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = theme.success, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سيتم إرفاق: $deviceInfo (v${BuildConfig.VERSION_NAME}) لتوثيق المراجعة",
                            color = theme.textSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = NotoSansFont
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "عذراً: $errorMessage",
                        color = theme.destructive,
                        fontSize = 11.5.sp,
                        fontFamily = NotoSansFont
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ApplePrimaryButton(
                    text = if (isEditing) "حفظ التعديل" else "إرسال التقييم",
                    icon = Icons.Default.Send,
                    isLoading = isSubmitting,
                    onClick = {
                        when {
                            isGuest -> errorMessage =
                                "سجّل الدخول أولاً لإرسال تقييمك — لا يمكن للضيوف التقييم"
                            comment.isBlank() -> errorMessage = "اكتب تعليقاً قبل الإرسال"
                            else -> {
                                isSubmitting = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        val targetId = editingReviewId
                                        if (targetId != null) {
                                            SupabaseServices.AppReviews.updateMyReview(
                                                reviewId = targetId,
                                                rating = rating,
                                                comment = comment,
                                                category = selectedCategory
                                            )
                                        } else {
                                            SupabaseServices.AppReviews.submitAppReview(
                                                rating = rating,
                                                comment = comment,
                                                authorName = displayName,
                                                category = selectedCategory
                                            )
                                        }
                                    }
                                    isSubmitting = false
                                    res.onSuccess {
                                        onSubmitSuccess()
                                    }.onFailure { e ->
                                        errorMessage = e.message ?: "تعذر الإرسال، تحقق من اتصالك بالإنترنت"
                                    }
                                }
                            }
                        }
                    }
                )
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
