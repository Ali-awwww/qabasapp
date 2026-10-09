package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * نافذة تفاعلية تفصل بين:
 * 1. الدروس والروابط المعتمدة التي يضعها المطور، ويدخل إليها المستخدم مباشرة.
 * 2. قسم مقترحات المستخدمين لعناوين دروس ومحاضرات جديدة بسرد معتنٍ وأفضل.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScholarLecturesSheet(
    scholar: ScholarBiography,
    onDismiss: () -> Unit,
    onLecturesCountChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { ScholarPreferences(context) }

    var selectedMainTab by remember { mutableIntStateOf(0) } // 0: الدروس المعتمدة (المطور), 1: مقترحات المستخدمين
    var isDevMode by remember { mutableStateOf(prefs.isDeveloperMode()) }

    var officialLectures by remember { mutableStateOf(prefs.getOfficialLectures(scholar.id, scholar.name)) }
    var userProposals by remember { mutableStateOf(prefs.getUserProposals(scholar.id)) }

    // Forms visibility
    var showDevAddDialog by remember { mutableStateOf(false) }
    var showUserProposalDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var showSqlHelpDialog by remember { mutableStateOf(false) }

    fun triggerCloudSync(showToast: Boolean = true) {
        scope.launch {
            isSyncing = true
            try {
                val cloudLecs = prefs.syncLecturesFromCloud(scholar.id)
                val cloudProps = prefs.syncProposalsFromCloud(scholar.id)
                officialLectures = cloudLecs
                userProposals = cloudProps
                onLecturesCountChanged(cloudLecs.size)
                if (showToast) {
                    Toast.makeText(context, "تمت المزامنة مع سحابة Supabase بنجاح ☁️", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (showToast) {
                    Toast.makeText(context, "تم الحفظ محلياً 💾", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSyncing = false
            }
        }
    }

    LaunchedEffect(scholar.id) {
        triggerCloudSync(showToast = false)
    }

    fun refreshAll() {
        val updatedLectures = prefs.getOfficialLectures(scholar.id, scholar.name)
        val updatedProposals = prefs.getUserProposals(scholar.id)
        officialLectures = updatedLectures
        userProposals = updatedProposals
        onLecturesCountChanged(updatedLectures.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AgedPaper,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ManuscriptGold) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            // Top Header: Title + Scholar Avatar + Dev Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = OldInk)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "المكتبة العلمية والمقترحات",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = OldInk
                    )
                    Text(
                        text = scholar.name,
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ManuscriptGold
                    )
                }

                // Avatar or Dev Mode Button
                Surface(
                    onClick = {
                        val next = !isDevMode
                        isDevMode = next
                        prefs.setDeveloperMode(next)
                        Toast.makeText(
                            context,
                            if (next) "تم تفعيل نمط المطور لإدارة الدروس 🛠️" else "تم العودة لنمط المستخدم العادي 👤",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDevMode) Color(0xFF0F172A) else ManuscriptGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, ManuscriptGold)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDevMode) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = "تبديل النمط",
                            tint = if (isDevMode) Color(0xFFFBBF24) else ManuscriptGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDevMode) "المطور" else "المستخدم",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDevMode) Color.White else OldInk
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // شريط المزامنة السحابية (Supabase Cloud Sync Status Bar)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = OldInk.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (SupabaseServices.isSupabaseAvailable) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (SupabaseServices.isSupabaseAvailable) "سحابة Supabase متصلة للجميع" else "الوضع المحلي (Offline)",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = OldInk
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "☁️",
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = if (isSyncing) "جاري تحديث البيانات السحابية..." else "أي إضافة تنشر سحابياً ليراها الجميع",
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                color = OldInk.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // زر التحديث اليدوي
                        IconButton(
                            onClick = { triggerCloudSync(showToast = true) },
                            enabled = !isSyncing,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = ManuscriptGold
                                )
                            } else {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "مزامنة الآن",
                                    tint = ManuscriptGold,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // زر كود SQL لإعداد الجداول في Supabase
                        Surface(
                            onClick = { showSqlHelpDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = ManuscriptGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = "كود SQL",
                                    tint = OldInk,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "كود SQL",
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OldInk
                                )
                            }
                        }
                    }
                }
            }

            // Two Tabs Separating: 1) الدروس المعتمدة (رسمية) 2) مقترحات المستخدمين
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                // Tab 1: الدروس والروابط المعتمدة (يدخل لها المستخدم)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedMainTab = 0 },
                    color = if (selectedMainTab == 0) ManuscriptGold else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = if (selectedMainTab == 0) Color.White else OldInk,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الدروس المعتمدة (${officialLectures.size})",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selectedMainTab == 0) Color.White else OldInk
                        )
                    }
                }

                // Tab 2: مقترحات المستخدمين (سرد أمعن)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedMainTab = 1 },
                    color = if (selectedMainTab == 1) ManuscriptGold else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = if (selectedMainTab == 1) Color.White else OldInk,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مقترحات الدروس (${userProposals.size})",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selectedMainTab == 1) Color.White else OldInk
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TAB 0: الدروس المعتمدة (المطور يضعها والمستخدم يدخل إليها)
            if (selectedMainTab == 0) {
                // Developer Mode Action Button
                if (isDevMode) {
                    Button(
                        onClick = { showDevAddDialog = true },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "🛠️ وضع المطور: نشر درس أو رابط معتمد جديد",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Header Note for Users
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ManuscriptGold.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "هذه الدروس منتقاة وموثقة بعناية، انقر على أي درس للانتقال مباشرة ومشاهدته عبر يوتيوب أو المتصفح.",
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            color = OldInk.copy(alpha = 0.85f),
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Curated Official Lectures
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(officialLectures, key = { it.id }) { lecture ->
                        CuratedLectureItemCard(
                            lecture = lecture,
                            isDevMode = isDevMode,
                            onOpen = { openLectureUrl(context, lecture.url) },
                            onDelete = {
                                prefs.deleteDeveloperLecture(lecture.id)
                                refreshAll()
                                Toast.makeText(context, "تم حذف الدرس من المكتبة", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // TAB 1: مقترحات المستخدمين (سرد أمعن واقتراح عناوين ومحاور جديدة)
            else {
                // User Proposal Action Button
                Button(
                    onClick = { showUserProposalDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "➕ اقترح عنوان درس أو محاضرة عن ${scholar.name}",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "مساهمات طلاب العلم والمستمعين (سرد عميق للأفكار والعناوين المقترحة):",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = OldInk.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (userProposals.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = AntiqueParchment.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "كن أول من يقترح عنوان درس أو سلسلة علمية!",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = OldInk
                            )
                            Text(
                                text = "شارك فكرتك أو عنوان محاضرة ترى أن الأمة بحاجة إليها في سيرة ${scholar.name}",
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                color = OldInk.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(userProposals, key = { it.id }) { proposal ->
                            UserProposalItemCard(
                                proposal = proposal,
                                isDevMode = isDevMode,
                                onUpvote = {
                                    prefs.upvoteProposal(proposal.id)
                                    refreshAll()
                                    Toast.makeText(context, "جزاكم الله خيراً، تم تسجيل تأييدك للاقتراح 👍", Toast.LENGTH_SHORT).show()
                                },
                                onAdoptAsOfficial = {
                                    // Developer can convert user proposal into official lecture
                                    val newLecture = ScholarLecture(
                                        scholarId = proposal.scholarId,
                                        scholarName = proposal.scholarName,
                                        title = proposal.suggestedTitle,
                                        speaker = proposal.suggestedSpeaker,
                                        url = if (proposal.suggestedUrl.isNotBlank()) proposal.suggestedUrl else "https://www.youtube.com/results?search_query=${proposal.suggestedTitle.replace(" ", "+")}",
                                        category = proposal.suggestedTopic,
                                        synopsis = proposal.rationale,
                                        isOfficialDeveloperCurated = true,
                                        notes = "مُقترح معتمد ومقدم من: ${proposal.userProposerName}"
                                    )
                                    prefs.addDeveloperLecture(newLecture)
                                    prefs.deleteUserProposal(proposal.id)
                                    refreshAll()
                                    Toast.makeText(context, "تم اعتماد المقترح وإضافته رسمياً للدروس المعتمدة! 🌟", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    prefs.deleteUserProposal(proposal.id)
                                    refreshAll()
                                    Toast.makeText(context, "تم حذف المقترح", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal Form for Developer to Add New Official Lecture
    if (showDevAddDialog) {
        DeveloperAddLectureDialog(
            scholar = scholar,
            onDismiss = { showDevAddDialog = false },
            onSave = { newLec ->
                prefs.addDeveloperLecture(newLec)
                refreshAll()
                showDevAddDialog = false
                Toast.makeText(context, "تم نشر الدرس المعتمد بنجاح 🚀", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Form for User to Propose Lecture
    if (showUserProposalDialog) {
        UserProposeLectureDialog(
            scholar = scholar,
            onDismiss = { showUserProposalDialog = false },
            onSave = { newProp ->
                prefs.addUserProposal(newProp)
                refreshAll()
                showUserProposalDialog = false
                Toast.makeText(context, "بارك الله فيك، تم إرسال مقترح الدرس بنجاح وسيحفظ سحابياً ✨", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal for Supabase SQL Table Setup Guidance
    if (showSqlHelpDialog) {
        SupabaseSqlHelpDialog(
            onDismiss = { showSqlHelpDialog = false },
            onCopySql = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Supabase Qabas SQL Schema", SUPABASE_SCHOLAR_SQL.trimIndent())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "تم نسخ كود SQL بنجاح! الصقه في Supabase SQL Editor واضغط Run ✅", Toast.LENGTH_LONG).show()
                showSqlHelpDialog = false
            }
        )
    }
}

/**
 * بطاقة عرض الدرس المعتمد من المطور (بسرد أمعن ومفصل وزر دخول مباشر)
 */
@Composable
fun CuratedLectureItemCard(
    lecture: ScholarLecture,
    isDevMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AntiqueParchment,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, ManuscriptGold.copy(alpha = 0.4f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category Badge
                    Surface(
                        color = ManuscriptGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = lecture.category,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = ManuscriptGold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    // Duration / Format Badge
                    if (lecture.durationApprox.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⏱️ ${lecture.durationApprox}",
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                color = OldInk.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Official Platform Badge + Dev Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF047857).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "معتمد",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF047857)
                            )
                        }
                    }

                    if (isDevMode) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف من المنصة", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = lecture.title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = OldInk
            )

            // Speaker
            if (lecture.speaker.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "المحاضر: ${lecture.speaker}",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OldInk.copy(alpha = 0.8f)
                    )
                }
            }

            // Synopsis (السرد الأمعن لمحتوى الدرس)
            if (lecture.synopsis.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = lecture.synopsis,
                    fontFamily = NotoSansFont,
                    fontSize = 12.5.sp,
                    color = OldInk.copy(alpha = 0.85f),
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User Action Buttons: الدخول المباشر للمشاهدة + زر المشاركة عبر تطبيقات التواصل الاجتماعي + نسخ الرابط
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1.3f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مشاهدة واستماع 🚀",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                // زر مشاركة رابط الدرس عبر تطبيقات التواصل الاجتماعي (واتساب، تيليجرام، فيسبوك، إلخ)
                Button(
                    onClick = { shareLecture(context, lecture) },
                    modifier = Modifier.weight(1.1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "مشاركة 📤",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                // زر نسخ الرابط السريع
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("رابط درس ${lecture.title}", lecture.url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ رابط الدرس للحافظة 📋", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ManuscriptGold),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرابط", tint = ManuscriptGold, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

/**
 * بطاقة مقترح المستخدم بسرد عميق وتفصيلي وإمكانية التصويت
 */
@Composable
fun UserProposalItemCard(
    proposal: LectureProposal,
    isDevMode: Boolean,
    onUpvote: () -> Unit,
    onAdoptAsOfficial: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AntiqueParchment,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Topic + Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "💡 ${proposal.suggestedTopic}",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = ManuscriptGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = proposal.status,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ManuscriptGold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Suggested Title
            Text(
                text = "« ${proposal.suggestedTitle} »",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = OldInk
            )

            // Sheikh & Proposer info
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (proposal.suggestedSpeaker.isNotBlank()) {
                    Text(
                        text = "🎙️ الشيخ المقترح: ${proposal.suggestedSpeaker}",
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        color = OldInk.copy(alpha = 0.75f)
                    )
                }
                Text(
                    text = "بواسطة: ${proposal.userProposerName}",
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    color = ManuscriptGold
                )
            }

            // Rationale (السرد الأمعن لأهمية الدرس المقترح)
            if (proposal.rationale.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📖 سبب الاقتراح والفائدة للأمة:\n${proposal.rationale}",
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = OldInk.copy(alpha = 0.85f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Upvote / Endorse + Share Proposal + Dev Adoption
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Upvote Button
                    Button(
                        onClick = onUpvote,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "أؤيد (${proposal.upvotesCount})",
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Share Proposal Button
                    OutlinedButton(
                        onClick = { shareProposal(context, proposal) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", modifier = Modifier.size(14.dp), tint = ManuscriptGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة", fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ManuscriptGold)
                    }
                }

                // If Dev Mode: Option to adopt as official lecture or delete
                if (isDevMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onAdoptAsOfficial) {
                            Text("اعتماد ونشر", fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * نافذة المطور لإضافة درس معتمد جديد بروابطه
 */
@Composable
fun DeveloperAddLectureDialog(
    scholar: ScholarBiography,
    onDismiss: () -> Unit,
    onSave: (ScholarLecture) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var speaker by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("المحنة والثبات") }
    var synopsis by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("45 دقيقة") }

    val categories = listOf("المحنة والثبات", "المناقب والفضائل", "الجهاد والفتوحات", "الفقه والحديث", "سيرة جامعة")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🛠️ نشر درس معتمد جديد في سيرة ${scholar.name}",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = OldInk
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الدرس المعتمد *", fontFamily = CairoFont, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = speaker,
                    onValueChange = { speaker = it },
                    label = { Text("اسم الشيخ أو العالم المحاضر *", fontFamily = CairoFont, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("رابط اليوتيوب أو المحاضرة *", fontFamily = CairoFont, fontSize = 12.sp) },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                url = clip.getItemAt(0).text?.toString() ?: ""
                            }
                        }) {
                            Text("لصق", fontFamily = CairoFont, fontSize = 11.sp, color = ManuscriptGold)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = { Text("السرد المفصل لمضمون الدرس والعبر المستفادة *", fontFamily = CairoFont, fontSize = 12.sp) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("المدة أو صيغة السلسلة", fontFamily = CairoFont, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || url.isBlank()) {
                        Toast.makeText(context, "يرجى كتابة العنوان والرابط", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(
                        ScholarLecture(
                            scholarId = scholar.id,
                            scholarName = scholar.name,
                            title = title.trim(),
                            speaker = speaker.trim(),
                            url = url.trim(),
                            category = category,
                            synopsis = synopsis.trim(),
                            durationApprox = duration.trim(),
                            isOfficialDeveloperCurated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46))
            ) {
                Text("نشر الدرس للجميع", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = CairoFont, color = OldInk)
            }
        },
        containerColor = AgedPaper
    )
}

/**
 * نافذة المستخدم لاقتراح عنوان درس ومحور علمي جديد بسرد أمعن
 */
@Composable
fun UserProposeLectureDialog(
    scholar: ScholarBiography,
    onDismiss: () -> Unit,
    onSave: (LectureProposal) -> Unit
) {
    val context = LocalContext.current
    var suggestedTitle by remember { mutableStateOf("") }
    var suggestedSpeaker by remember { mutableStateOf("") }
    var suggestedTopic by remember { mutableStateOf("المحنة والثبات") }
    var rationale by remember { mutableStateOf("") }
    var userProposerName by remember { mutableStateOf("") }

    val topics = listOf("المحنة والثبات", "فقه القيادة والعدل", "التربية والزهد", "الرد على الشبهات", "معارك وبطولات")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "💡 اقتراح درس أو محاضرة عن ${scholar.name}",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = OldInk
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "شارك برأيك في موضوع أو فكرة درس ترى أنها تلهم المسلمين في سيرة هذا الإمام:",
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    color = OldInk.copy(alpha = 0.75f)
                )

                OutlinedTextField(
                    value = suggestedTitle,
                    onValueChange = { suggestedTitle = it },
                    label = { Text("عنوان الدرس المقترح *", fontFamily = CairoFont, fontSize = 12.sp) },
                    placeholder = { Text("مثال: فقه الصبر عند الشدائد في سيرة الإمام", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = suggestedSpeaker,
                    onValueChange = { suggestedSpeaker = it },
                    label = { Text("الشيخ أو العالم المفضل لإلقاء الدرس", fontFamily = CairoFont, fontSize = 12.sp) },
                    placeholder = { Text("مثال: د. راغب السرجاني / الشيخ سعيد الكملي", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rationale,
                    onValueChange = { rationale = it },
                    label = { Text("لماذا تقترح هذا الدرس؟ وما أثره للأمة؟ *", fontFamily = CairoFont, fontSize = 12.sp) },
                    placeholder = { Text("اكتب سرداً وافياً للأثر التربوي والفائدة من هذا الطرح...", fontFamily = CairoFont, fontSize = 11.sp) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = userProposerName,
                    onValueChange = { userProposerName = it },
                    label = { Text("اسمك (اختياري)", fontFamily = CairoFont, fontSize = 12.sp) },
                    placeholder = { Text("طالب علم", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (suggestedTitle.isBlank() || rationale.isBlank()) {
                        Toast.makeText(context, "يرجى كتابة عنوان الدرس وسبب الاقتراح", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(
                        LectureProposal(
                            scholarId = scholar.id,
                            scholarName = scholar.name,
                            suggestedTitle = suggestedTitle.trim(),
                            suggestedSpeaker = suggestedSpeaker.trim(),
                            suggestedTopic = suggestedTopic,
                            rationale = rationale.trim(),
                            userProposerName = if (userProposerName.isBlank()) "طالب علم" else userProposerName.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold)
            ) {
                Text("إرسال المقترح", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = CairoFont, color = OldInk)
            }
        },
        containerColor = AgedPaper
    )
}

/**
 * دالة مساعدة لفتح الروابط عبر متصفح الجهاز أو تطبيق يوتيوب
 */
fun openLectureUrl(context: Context, url: String) {
    try {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر فتح الرابط، يرجى التأكد من توفر متصفح إنترنت", Toast.LENGTH_SHORT).show()
    }
}

/**
 * دالة لمشاركة رابط وتفاصيل الدرس المعتمد عبر تطبيقات التواصل الاجتماعي
 */
fun shareLecture(context: Context, lecture: ScholarLecture) {
    try {
        val shareBody = buildString {
            appendLine("🌟 درس ومحاضرة موثقة من «تطبيق قبس»:")
            appendLine("«${lecture.title}»")
            if (lecture.speaker.isNotBlank()) {
                appendLine("🎙️ إلقاء: ${lecture.speaker}")
            }
            if (lecture.scholarName.isNotBlank()) {
                appendLine("👤 سيرة الإمام: ${lecture.scholarName}")
            }
            if (lecture.category.isNotBlank()) {
                appendLine("🏷️ التصنيف: ${lecture.category}")
            }
            if (lecture.synopsis.isNotBlank()) {
                appendLine()
                appendLine("📖 نبذة عن المحتوى: ${lecture.synopsis}")
            }
            appendLine()
            appendLine("🔗 رابط الاستماع والمشاهدة المباشر:")
            appendLine(lecture.url)
            appendLine()
            appendLine("✨ تم المشاركة عبر تطبيق قبس (Qabas Studio) - الاستوديو الإسلامي الذكي")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "درس: ${lecture.title}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة رابط الدرس عبر تطبيقات التواصل"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر فتح نافذة المشاركة", Toast.LENGTH_SHORT).show()
    }
}

/**
 * دالة لمشاركة مقترح الدرس مع طلاب العلم
 */
fun shareProposal(context: Context, proposal: LectureProposal) {
    try {
        val shareBody = buildString {
            appendLine("💡 مقترح درس علمي من مجتمع «قبس»:")
            appendLine("«${proposal.suggestedTitle}»")
            if (proposal.suggestedSpeaker.isNotBlank()) {
                appendLine("🎙️ الشيخ المقترح: ${proposal.suggestedSpeaker}")
            }
            if (proposal.scholarName.isNotBlank()) {
                appendLine("👤 في سيرة: ${proposal.scholarName}")
            }
            if (proposal.rationale.isNotBlank()) {
                appendLine()
                appendLine("📖 الفائدة والأهمية: ${proposal.rationale}")
            }
            if (proposal.suggestedUrl.isNotBlank()) {
                appendLine()
                appendLine("🔗 الرابط المقترح: ${proposal.suggestedUrl}")
            }
            appendLine()
            appendLine("✨ شارك برأيك وتأييدك للمقترح عبر تطبيق قبس!")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "مقترح درس: ${proposal.suggestedTitle}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة مقترح الدرس عبر"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر فتح نافذة المشاركة", Toast.LENGTH_SHORT).show()
    }
}

/**
 * نافذة إرشادية تعرض خطوات إعداد جداول Supabase للسحابة مع زر نسخ كود SQL بضغطة واحدة
 */
@Composable
fun SupabaseSqlHelpDialog(
    onDismiss: () -> Unit,
    onCopySql: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onCopySql,
                colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = OldInk, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("📋 نسخ كود SQL بالكامل", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = OldInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", fontFamily = CairoFont, color = OldInk)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تهيئة سحابة Supabase", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = OldInk)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "لكي تظهر الدروس ومقترحات المستخدمين للجميع عبر السحابة، يجب إنشاء الجداول وسياسات الأمان في مشروعك بـ Supabase مرة واحدة فقط:",
                    fontFamily = CairoFont,
                    fontSize = 12.5.sp,
                    color = OldInk,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = OldInk.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("1️⃣ ادخل إلى لوحة تحكم مشروعك في supabase.com", fontFamily = CairoFont, fontSize = 11.5.sp, color = OldInk, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("2️⃣ اختر SQL Editor من القائمة الجانبية.", fontFamily = CairoFont, fontSize = 11.5.sp, color = OldInk, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("3️⃣ اضغط زر النسخ أدناه، والصق الكود ثم اضغط Run.", fontFamily = CairoFont, fontSize = 11.5.sp, color = OldInk, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "معاينة كود الجداول والسياسات:",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = ManuscriptGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Text(
                        text = SUPABASE_SCHOLAR_SQL.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        containerColor = AgedPaper,
        shape = RoundedCornerShape(16.dp)
    )
}

const val SUPABASE_SCHOLAR_SQL = """
-- 1. جدول الدروس والمحاضرات المعتمدة
CREATE TABLE IF NOT EXISTS public.scholar_lectures (
    id TEXT PRIMARY KEY,
    scholar_id TEXT NOT NULL,
    scholar_name TEXT,
    title TEXT NOT NULL,
    speaker TEXT DEFAULT '',
    url TEXT NOT NULL,
    type TEXT DEFAULT 'يوتيوب / مرئي',
    category TEXT DEFAULT 'سيرة وعبر',
    synopsis TEXT DEFAULT '',
    duration_approx TEXT DEFAULT '',
    is_official BOOLEAN DEFAULT TRUE,
    notes TEXT DEFAULT '',
    added_at_millis BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scholar_lectures_scholar_id ON public.scholar_lectures(scholar_id);

-- 2. جدول مقترحات المستخدمين وتصويتاتهم
CREATE TABLE IF NOT EXISTS public.scholar_proposals (
    id TEXT PRIMARY KEY,
    scholar_id TEXT NOT NULL,
    scholar_name TEXT,
    suggested_title TEXT NOT NULL,
    suggested_speaker TEXT DEFAULT '',
    suggested_url TEXT DEFAULT '',
    suggested_topic TEXT DEFAULT 'سيرة وعبر',
    rationale TEXT DEFAULT '',
    user_proposer_name TEXT DEFAULT 'طالب علم',
    upvotes_count INTEGER DEFAULT 1,
    status TEXT DEFAULT 'قيد المراجعة العلمية',
    created_at_millis BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scholar_proposals_scholar_id ON public.scholar_proposals(scholar_id);

-- 3. تفعيل RLS وسياسات الأمان
ALTER TABLE public.scholar_lectures ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scholar_proposals ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public select on scholar_lectures" ON public.scholar_lectures FOR SELECT USING (true);
CREATE POLICY "Public insert on scholar_lectures" ON public.scholar_lectures FOR INSERT WITH CHECK (true);
CREATE POLICY "Public update on scholar_lectures" ON public.scholar_lectures FOR UPDATE USING (true);
CREATE POLICY "Public delete on scholar_lectures" ON public.scholar_lectures FOR DELETE USING (true);

CREATE POLICY "Public select on scholar_proposals" ON public.scholar_proposals FOR SELECT USING (true);
CREATE POLICY "Public insert on scholar_proposals" ON public.scholar_proposals FOR INSERT WITH CHECK (true);
CREATE POLICY "Public update on scholar_proposals" ON public.scholar_proposals FOR UPDATE USING (true);
CREATE POLICY "Public delete on scholar_proposals" ON public.scholar_proposals FOR DELETE USING (true);
"""

