package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * نموذج عنصر سجل قرارات العقل الإخراجي (Brain Decision History Item)
 * يربط بين الفكرة المدخلة، القرار الإخراجي المتخذ، وكائن النمط (Style Object) المؤثر في صياغة النتيجة.
 */
@Immutable
data class BrainDecisionHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val ideaInput: String = "",
    val tone: String = "وقور وملهم",
    val targetAudience: String = "الجمهور العام",
    val decision: BrainDecision = BrainDecision(),
    val influencingStyleObject: StyleObject? = null,
    val userFeedback: Boolean? = null // null: لا يوجد، true: إيجابي (+ تعزيز)، false: سلبي
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("timestamp", timestamp)
        obj.put("ideaInput", ideaInput)
        obj.put("tone", tone)
        obj.put("targetAudience", targetAudience)
        if (userFeedback != null) {
            obj.put("userFeedback", userFeedback)
        }

        // تسلسل القرار الإخراجي
        val decObj = JSONObject()
        decObj.put("title", decision.title)
        decObj.put("hook", decision.hook)
        decObj.put("voiceOverTone", decision.voiceOverTone)
        decObj.put("visualDirectives", decision.visualDirectives)
        decObj.put("primaryColorHex", decision.primaryColorHex)
        decObj.put("backgroundColorHex", decision.backgroundColorHex)
        decObj.put("ffmpegFilterSnippet", decision.ffmpegFilterSnippet)
        decObj.put("selectedStyleName", decision.selectedStyleName)
        decObj.put("compatibilityScore", decision.compatibilityScore)
        decObj.put("aiRationale", decision.aiRationale)
        decObj.put("targetAudience", decision.targetAudience)
        decObj.put("callToAction", decision.callToAction)

        val scenesArr = JSONArray()
        decision.scriptScenes.forEach { sc ->
            val scObj = JSONObject()
            scObj.put("title", sc.title)
            scObj.put("description", sc.description)
            scObj.put("durationInSeconds", sc.durationInSeconds)
            scObj.put("visualEffect", sc.visualEffect)
            scObj.put("tempo", sc.tempo)
            scObj.put("transitionType", sc.transitionType)
            scObj.put("mediaUrl", sc.mediaUrl)
            scenesArr.put(scObj)
        }
        decObj.put("scriptScenes", scenesArr)

        val sfxArr = JSONArray()
        decision.audioSfxSuggestions.forEach { sfxArr.put(it) }
        decObj.put("audioSfxSuggestions", sfxArr)

        obj.put("decision", decObj)

        // تسلسل كائن النمط المؤثر إن وجد
        influencingStyleObject?.let { style ->
            val styleJson = JSONObject()
            styleJson.put("id", style.id)
            styleJson.put("name", style.name)
            styleJson.put("userId", style.userId)
            styleJson.put("sourceVideoPathOrUrl", style.sourceVideoPathOrUrl)
            styleJson.put("overallScore", style.overallScore)
            styleJson.put("analysisSummary", style.analysisSummary)
            styleJson.put("createdAt", style.createdAt)
            styleJson.put("isCloudSynced", style.isCloudSynced)

            val visObj = JSONObject(style.visualStyle.toMap())
            styleJson.put("visualStyle", visObj)

            val motObj = JSONObject(style.motionRhythm.toMap())
            styleJson.put("motionRhythm", motObj)

            val tonObj = JSONObject(style.contentTone.toMap())
            styleJson.put("contentTone", tonObj)

            val tagsArr = JSONArray()
            style.tags.forEach { tagsArr.put(it) }
            styleJson.put("tags", tagsArr)

            obj.put("influencingStyleObject", styleJson)
        }

        return obj
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BrainDecisionHistoryItem {
            val id = json.optString("id", UUID.randomUUID().toString())
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val ideaInput = json.optString("ideaInput", "")
            val tone = json.optString("tone", "وقور وملهم")
            val targetAudience = json.optString("targetAudience", "الجمهور العام")
            val userFeedback = if (json.has("userFeedback")) json.optBoolean("userFeedback") else null

            val decJson = json.optJSONObject("decision")
            val decision = if (decJson != null) {
                val scenesList = mutableListOf<Scene>()
                val scenesArr = decJson.optJSONArray("scriptScenes")
                if (scenesArr != null) {
                    for (i in 0 until scenesArr.length()) {
                        val sc = scenesArr.getJSONObject(i)
                        scenesList.add(
                            Scene(
                                title = sc.optString("title", ""),
                                description = sc.optString("description", ""),
                                durationInSeconds = sc.optInt("durationInSeconds", 5),
                                visualEffect = sc.optString("visualEffect", ""),
                                tempo = sc.optString("tempo", ""),
                                transitionType = sc.optString("transitionType", "Dissolve"),
                                mediaUrl = sc.optString("mediaUrl", "")
                            )
                        )
                    }
                }

                val sfxList = mutableListOf<String>()
                val sfxArr = decJson.optJSONArray("audioSfxSuggestions")
                if (sfxArr != null) {
                    for (i in 0 until sfxArr.length()) {
                        sfxList.add(sfxArr.getString(i))
                    }
                }

                BrainDecision(
                    title = decJson.optString("title", ""),
                    hook = decJson.optString("hook", ""),
                    scriptScenes = scenesList,
                    voiceOverTone = decJson.optString("voiceOverTone", "وقور وملهم"),
                    visualDirectives = decJson.optString("visualDirectives", ""),
                    primaryColorHex = decJson.optString("primaryColorHex", "#E8C547"),
                    backgroundColorHex = decJson.optString("backgroundColorHex", "#0B0F19"),
                    ffmpegFilterSnippet = decJson.optString("ffmpegFilterSnippet", ""),
                    audioSfxSuggestions = sfxList,
                    selectedStyleName = decJson.optString("selectedStyleName", ""),
                    compatibilityScore = decJson.optInt("compatibilityScore", 94),
                    aiRationale = decJson.optString("aiRationale", ""),
                    targetAudience = decJson.optString("targetAudience", "الجمهور العام"),
                    callToAction = decJson.optString("callToAction", "شارك المقطع")
                )
            } else {
                BrainDecision()
            }

            val styleJson = json.optJSONObject("influencingStyleObject")
            val styleObject = if (styleJson != null) {
                val visMap = styleJson.optJSONObject("visualStyle")?.let { jsonToMap(it) } ?: emptyMap()
                val motMap = styleJson.optJSONObject("motionRhythm")?.let { jsonToMap(it) } ?: emptyMap()
                val tonMap = styleJson.optJSONObject("contentTone")?.let { jsonToMap(it) } ?: emptyMap()
                val tagsList = mutableListOf<String>()
                styleJson.optJSONArray("tags")?.let { arr ->
                    for (i in 0 until arr.length()) tagsList.add(arr.getString(i))
                }

                StyleObject(
                    id = styleJson.optString("id", UUID.randomUUID().toString()),
                    name = styleJson.optString("name", "أسلوب فني"),
                    userId = styleJson.optString("userId", "default_user"),
                    sourceVideoPathOrUrl = styleJson.optString("sourceVideoPathOrUrl", ""),
                    visualStyle = VisualStylePattern.fromMap(visMap),
                    motionRhythm = MotionRhythmPattern.fromMap(motMap),
                    contentTone = ContentTonePattern.fromMap(tonMap),
                    overallScore = styleJson.optInt("overallScore", 90),
                    analysisSummary = styleJson.optString("analysisSummary", ""),
                    tags = tagsList,
                    createdAt = styleJson.optLong("createdAt", System.currentTimeMillis()),
                    isCloudSynced = styleJson.optBoolean("isCloudSynced", false)
                )
            } else null

            return BrainDecisionHistoryItem(
                id = id,
                timestamp = timestamp,
                ideaInput = ideaInput,
                tone = tone,
                targetAudience = targetAudience,
                decision = decision,
                influencingStyleObject = styleObject,
                userFeedback = userFeedback
            )
        }

        private fun jsonToMap(json: JSONObject): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                if (value is JSONArray) {
                    val list = mutableListOf<Any>()
                    for (i in 0 until value.length()) {
                        list.add(value.get(i))
                    }
                    map[key] = list
                } else if (value != JSONObject.NULL) {
                    map[key] = value
                }
            }
            return map
        }
    }
}

/**
 * المكون البصري لسجل قرارات العقل وكائنات الأنماط المؤثرة (Brain Decision History Log Component)
 * يعرض القرارات الإخراجية السابقة، النمط المرجعي المستخدم لكل قرار، وتفاصيل الفلاتر والسيناريو.
 */
@Composable
fun BrainDecisionHistoryLog(
    historyItems: List<BrainDecisionHistoryItem>,
    modifier: Modifier = Modifier,
    onApplyDecisionToProject: (BrainDecisionHistoryItem) -> Unit = {},
    onFeedback: (itemId: String, isPositive: Boolean) -> Unit = { _, _ -> },
    onDeleteItem: (itemId: String) -> Unit = {},
    onClearAll: () -> Unit = {},
    onInspectStyleObject: (StyleObject) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") }
    var itemToInspect by remember { mutableStateOf<BrainDecisionHistoryItem?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // تصفية العناصر
    val filteredItems = remember(historyItems, searchQuery, selectedFilter) {
        historyItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.decision.title.contains(searchQuery, ignoreCase = true) ||
                    item.ideaInput.contains(searchQuery, ignoreCase = true) ||
                    item.decision.selectedStyleName.contains(searchQuery, ignoreCase = true) ||
                    (item.influencingStyleObject?.name?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                "الكل" -> true
                "توافق عالي (+90%)" -> item.decision.compatibilityScore >= 90
                "تم تقييمه إيجاباً" -> item.userFeedback == true
                "أنماط مخصصة" -> item.influencingStyleObject != null && !item.influencingStyleObject.name.contains("قبس الأساسية")
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    // إحصائيات سريعة لسجل العقل
    val avgScore = remember(historyItems) {
        if (historyItems.isEmpty()) 0 else historyItems.map { it.decision.compatibilityScore }.average().toInt()
    }
    val stylesUsedCount = remember(historyItems) {
        historyItems.mapNotNull { it.influencingStyleObject?.name.ifNullOrBlank { it.decision.selectedStyleName } }.distinct().size
    }
    val positiveCount = remember(historyItems) {
        historyItems.count { it.userFeedback == true }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DeepSlate)
    ) {
        // 1. بطاقة إحصائيات السجل العصبية
        HistoryStatsOverviewBar(
            totalDecisions = historyItems.size,
            stylesUsed = stylesUsedCount,
            avgCompatibility = avgScore,
            positiveReinforcements = positiveCount,
            onClearClick = { if (historyItems.isNotEmpty()) showClearDialog = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. شريط البحث والتصفية
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث في القرارات، الأفكار، أو كائنات الأنماط...", fontSize = 13.sp, color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // رقاقات التصفية السريعة
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("الكل", "توافق عالي (+90%)", "تم تقييمه إيجاباً", "أنماط مخصصة")
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    color = if (isSelected) GoldPrimary else CardSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) DeepSlate else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = CairoFont,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. قائمة عناصر السجل
        if (filteredItems.isEmpty()) {
            EmptyHistoryStateView(
                hasAnyHistory = historyItems.isNotEmpty(),
                onResetFilter = {
                    searchQuery = ""
                    selectedFilter = "الكل"
                }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    BrainDecisionHistoryCard(
                        item = item,
                        onApply = { onApplyDecisionToProject(item) },
                        onFeedback = { isPos -> onFeedback(item.id, isPos) },
                        onDelete = { onDeleteItem(item.id) },
                        onInspectStyle = {
                            item.influencingStyleObject?.let { onInspectStyleObject(it) }
                        },
                        onOpenDetails = { itemToInspect = item }
                    )
                }
            }
        }
    }

    // نافذة الفحص العميق لتأثير كائن النمط على القرار
    itemToInspect?.let { inspected ->
        BrainDecisionDetailDialog(
            item = inspected,
            onDismiss = { itemToInspect = null },
            onApply = {
                onApplyDecisionToProject(inspected)
                itemToInspect = null
            }
        )
    }

    // حوار تأكيد مسح السجل
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CardSurface,
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp)) },
            title = { Text("مسح سجل قرارات العقل", color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = CairoFont) },
            text = { Text("هل أنت متأكد من رغبتك في مسح كافة السجلات الإخراجية المحفوظة؟ لن يؤثر ذلك على كائنات الأنماط الأساسية في Firestore.", color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("مسح الكل", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            }
        )
    }
}

/**
 * بطاقة عرض عنصر واحد من سجل قرارات العقل مع كائن النمط المؤثر
 */
@Composable
fun BrainDecisionHistoryCard(
    item: BrainDecisionHistoryItem,
    onApply: () -> Unit,
    onFeedback: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onInspectStyle: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val styleObj = item.influencingStyleObject

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (item.userFeedback == true) GoldPrimary.copy(alpha = 0.5f) else Color(0xFF1E293B), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // رأس البطاقة: العنوان، التوافق، التاريخ، وزر الحذف
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            text = item.decision.title.ifBlank { "قرار إخراجي ذكي" },
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = CairoFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = formattedDate,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // شارة التوافق
                    Surface(
                        color = if (item.decision.compatibilityScore >= 90) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (item.decision.compatibilityScore >= 90) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    ) {
                        Text(
                            text = "${item.decision.compatibilityScore}% توافق",
                            color = if (item.decision.compatibilityScore >= 90) Color(0xFF34D399) else Color(0xFFFBBF24),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "حذف", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // الفكرة المدخلة
            Surface(
                color = Color(0xFF0B1120),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "الفكرة: ${item.ideaInput}",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontFamily = CairoFont,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // قسم كائن النمط المؤثر (Hero Section)
            InfluencingStyleObjectCard(
                styleObject = styleObj,
                fallbackStyleName = item.decision.selectedStyleName,
                onInspect = onInspectStyle
            )

            Spacer(modifier = Modifier.height(10.dp))

            // الخطاف الإخراجي
            if (item.decision.hook.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Text(
                        text = "الخطاف: ${item.decision.hook}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // التفاصيل الموسعة: المشاهد وفلاتر FFmpeg والتحليل
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // المنطق الإخراجي للذكاء الاصطناعي
                    if (item.decision.aiRationale.isNotBlank()) {
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("توجيهات العقل الإخراجية:", color = GoldPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.decision.aiRationale, color = TextSecondary, fontSize = 12.sp, fontFamily = CairoFont, lineHeight = 18.sp)
                            }
                        }
                    }

                    // المشاهد المقترحة
                    if (item.decision.scriptScenes.isNotEmpty()) {
                        Text(
                            "المشاهد الإخراجية (${item.decision.scriptScenes.size} مشاهد):",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                        item.decision.scriptScenes.forEachIndexed { idx, sc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0B1120), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${idx + 1}", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sc.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                    Text(sc.description, color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont, maxLines = 2)
                                }
                                Text("${sc.durationInSeconds}ث", color = GoldSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // كود FFmpeg وميزة النسخ السريع
                    if (item.decision.ffmpegFilterSnippet.isNotBlank()) {
                        Surface(
                            color = Color(0xFF06090E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("معادلة فلاتر FFmpeg المولدة:", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("FFmpeg Filter", item.decision.ffmpegFilterSnippet))
                                            Toast.makeText(context, "تم نسخ معادلة FFmpeg للحافظة", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    text = item.decision.ffmpegFilterSnippet,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontFamily = NotoSansFont,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط الإجراءات السفلي
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // زر التوسيع / الإخفاء وزر التفاصيل
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "عرض أقل ▲" else "عرض التفاصيل ▼",
                            color = GoldPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = CairoFont
                        )
                    }

                    TextButton(
                        onClick = onOpenDetails,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("الفحص العميق 🔍", color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                    }
                }

                // أزرار التغذية الراجعة والتطبيق
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // تقييم إيجابي
                    IconButton(
                        onClick = { onFeedback(true) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (item.userFeedback == true) Color(0xFF10B981).copy(alpha = 0.25f) else Color.Transparent,
                                CircleShape
                            )
                            .border(0.8.dp, if (item.userFeedback == true) Color(0xFF10B981) else Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = "تعزيز النمط",
                            tint = if (item.userFeedback == true) Color(0xFF10B981) else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // تقييم سلبي
                    IconButton(
                        onClick = { onFeedback(false) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (item.userFeedback == false) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.Transparent,
                                CircleShape
                            )
                            .border(0.8.dp, if (item.userFeedback == false) Color(0xFFEF4444) else Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ThumbDown,
                            contentDescription = "خفض النمط",
                            tint = if (item.userFeedback == false) Color(0xFFEF4444) else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // زر التطبيق
                    Button(
                        onClick = onApply,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تطبيق", color = DeepSlate, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                    }
                }
            }
        }
    }
}

/**
 * بطاقة مميزة تعرض كائن النمط المؤثر (Influencing Style Object)
 */
@Composable
private fun InfluencingStyleObjectCard(
    styleObject: StyleObject?,
    fallbackStyleName: String,
    onInspect: () -> Unit
) {
    val styleName = styleObject?.name ?: fallbackStyleName.ifBlank { "هوية قبس السينمائية التراكمية" }
    val primaryColor = remember(styleObject) {
        try {
            Color(android.graphics.Color.parseColor(styleObject?.visualStyle?.primaryColorHex ?: "#E8C547"))
        } catch (e: Exception) {
            GoldPrimary
        }
    }
    val bgColor = remember(styleObject) {
        try {
            Color(android.graphics.Color.parseColor(styleObject?.visualStyle?.backgroundColorHex ?: "#0B0F19"))
        } catch (e: Exception) {
            DeepSlate
        }
    }

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // شريط العنوان لكائن النمط
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = primaryColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "كائن النمط المؤثر",
                            color = primaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = styleName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (styleObject != null) {
                    Text(
                        text = "تقييم: ${styleObject.overallScore}/100",
                        color = GoldSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                }
            }

            // الخصائص البصرية والحركية الموجهة للقرار
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // دوائر الألوان
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }

                // سمات الحركة والنبرة
                val motionText = styleObject?.motionRhythm?.movementPatterns?.take(22) ?: "زووم بطيء متصاعد"
                val toneText = styleObject?.contentTone?.tone?.take(18) ?: "وقور وهادف"

                Text(
                    text = "• حركة: $motionText  • نبرة: $toneText",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = CairoFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * شريط إحصائيات علوي لسجل العقل الإخراجي
 */
@Composable
private fun HistoryStatsOverviewBar(
    totalDecisions: Int,
    stylesUsed: Int,
    avgCompatibility: Int,
    positiveReinforcements: Int,
    onClearClick: () -> Unit
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                    Text("سجل قرارات عقل قبس (Brain Decisions Log)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = CairoFont)
                }

                if (totalDecisions > 0) {
                    IconButton(onClick = onClearClick, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "مسح السجل", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatBadgeItem("إجمالي القرارات", "$totalDecisions", GoldPrimary)
                StatBadgeItem("الأنماط المؤثرة", "$stylesUsed", Color(0xFF38BDF8))
                StatBadgeItem("متوسط التوافق", "$avgCompatibility%", Color(0xFF10B981))
                StatBadgeItem("التعزيز الإيجابي", "$positiveReinforcements 👍", GoldSecondary)
            }
        }
    }
}

@Composable
private fun StatBadgeItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = CairoFont)
        Text(label, color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
    }
}

/**
 * نافذة الفحص العميق لكائن النمط والقرار الإخراجي (Deep Inspection Dialog)
 */
@Composable
fun BrainDecisionDetailDialog(
    item: BrainDecisionHistoryItem,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    val styleObj = item.influencingStyleObject

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary)
                    Text(item.decision.title.ifBlank { "تفاصيل القرار الإخراجي" }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = CairoFont)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. الفكرة الأصلية
                DetailSection(title = "الفكرة الأصلية:", content = item.ideaInput, icon = Icons.Default.Lightbulb)

                // 2. كائن النمط المؤثر وتحليله
                if (styleObj != null) {
                    Surface(
                        color = Color(0xFF0B1120),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("كائن النمط الحاكم: ${styleObj.name}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFont)
                            Text("• النبرة: ${styleObj.contentTone.tone} (الجمهور: ${styleObj.contentTone.targetAudience})", color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                            Text("• الهوية البصرية: ${styleObj.visualStyle.dominantColors} | لون رئيسي: ${styleObj.visualStyle.primaryColorHex}", color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                            Text("• حركة الكاميرا والإيقاع: ${styleObj.motionRhythm.movementPatterns} | سرعة الانتقال: ${styleObj.motionRhythm.transitionSpeed}", color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                            Text("• الخط والكابشن: ${styleObj.contentTone.typographyStyle} | حركة النص: ${styleObj.contentTone.captionAnimation}", color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                            if (styleObj.analysisSummary.isNotBlank()) {
                                Text("• ملخص التوجيه: ${styleObj.analysisSummary}", color = Color(0xFF38BDF8), fontSize = 11.5.sp, fontFamily = CairoFont)
                            }
                        }
                    }
                }

                // 3. الخطاف والنداء (Hook & CTA)
                DetailSection(title = "الخطاف الاستفتاحي (Hook):", content = item.decision.hook, icon = Icons.Default.FlashOn)
                DetailSection(title = "دعوة العمل (Call to Action):", content = item.decision.callToAction, icon = Icons.Default.Campaign)

                // 4. سيناريو المشاهد
                if (item.decision.scriptScenes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("السكريبت والمشاهد الإخراجية (${item.decision.scriptScenes.size} مشاهد):", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFont)
                        item.decision.scriptScenes.forEachIndexed { i, sc ->
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${i + 1}. ${sc.title}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = CairoFont)
                                        Text("${sc.durationInSeconds} ثواني | ${sc.transitionType}", color = GoldSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(sc.description, color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                                    if (sc.visualEffect.isNotBlank()) {
                                        Text("مؤثر بصري: ${sc.visualEffect}", color = Color(0xFF34D399), fontSize = 10.5.sp, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. فلاتر FFmpeg
                if (item.decision.ffmpegFilterSnippet.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("كود فلاتر FFmpeg المولد:", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFont)
                        Surface(
                            color = Color(0xFF06090E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.decision.ffmpegFilterSnippet, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = NotoSansFont, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("FFmpeg Filter", item.decision.ffmpegFilterSnippet))
                                        Toast.makeText(context, "تم نسخ معادلة FFmpeg", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تطبيق على المشروع 🎬", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = TextSecondary, fontFamily = CairoFont)
            }
        }
    )
}

@Composable
private fun DetailSection(title: String, content: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    if (content.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                Text(title, color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, fontFamily = CairoFont)
            }
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(content, color = TextPrimary, fontSize = 12.sp, fontFamily = CairoFont, modifier = Modifier.padding(10.dp), lineHeight = 18.sp)
            }
        }
    }
}

/**
 * واجهة الحالة الفارغة للسجل
 */
@Composable
private fun EmptyHistoryStateView(
    hasAnyHistory: Boolean,
    onResetFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.HistoryToggleOff,
            contentDescription = null,
            tint = GoldPrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasAnyHistory) "لا توجد نتائج مطابقة لخيارات البحث أو التصفية" else "لا توجد قرارات مسجلة في عقل قبس حتى الآن",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            fontFamily = CairoFont,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasAnyHistory) "جرب تغيير مصطلح البحث أو اختيار فلتر آخر." else "عند قيامك بإرسال أي فكرة للمخرج الذكي، سيتم حفظ القرار وكائن النمط المؤثر هنا تلقائياً.",
            color = TextSecondary,
            fontSize = 12.5.sp,
            fontFamily = CairoFont,
            textAlign = TextAlign.Center
        )

        if (hasAnyHistory) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onResetFilter,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إعادة ضبط الفلتر", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun String?.ifNullOrBlank(fallback: () -> String): String {
    return if (this.isNullOrBlank()) fallback() else this
}
