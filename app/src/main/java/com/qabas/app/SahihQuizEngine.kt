package com.qabas.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🎓 محرك اختبارات الفهم والحفظ للصحيحين (يعمل على الكتاب الكامل).
 *
 * نوعا الأسئلة (مولّدة من نصوص الأحاديث نفسها — بلا بيانات خارجية):
 * - فهم: "في أي باب ورد هذا الحديث؟" (مقتطف + 4 أبواب)
 * - حفظ: "أكمل الحديث" (بداية النص + 4 تكملات من أحاديث مختلفة)
 */
enum class SahihQuizType { COMPREHENSION, MEMORIZATION }

data class SahihQuizQuestion(
    val type: SahihQuizType,
    /** المقتطف المعروض (بداية الحديث أو نصه) */
    val passage: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    /** يُعرض بعد الإجابة */
    val explanation: String
)

object SahihQuizEngine {

    const val QUESTIONS_PER_ROUND = 100
    private const val MIN_WORDS_FOR_COMPLETION = 12
    private const val COMPLETION_TAKE_WORDS = 5

    fun generateRound(
        book: FullSahihBook,
        count: Int = QUESTIONS_PER_ROUND,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): List<SahihQuizQuestion> {
        if (book.hadiths.isEmpty() || book.chapters.size < 4) return emptyList()
        val chapterName = book.chapters.associate { it.id to it.arabic }
        val eligible = book.hadiths.filter { it.arabic.wordCount() >= 8 }
        if (eligible.size < 4) return emptyList()

        val questions = mutableListOf<SahihQuizQuestion>()
        var guard = 0
        while (questions.size < count && guard < count * 20) {
            guard++
            val h = eligible.random(random)
            if (random.nextBoolean()) {
                buildComprehension(h, book, chapterName, random)?.let { questions.add(it) }
            } else {
                buildMemorization(h, eligible, chapterName, random)?.let { questions.add(it) }
            }
        }
        return questions
    }

    private fun buildComprehension(
        h: FullSahihHadith,
        book: FullSahihBook,
        chapterName: Map<Int, String>,
        random: kotlin.random.Random
    ): SahihQuizQuestion? {
        val correct = chapterName[h.chapterId] ?: return null
        val others = book.chapters.map { it.arabic }.filter { it != correct }.shuffled(random).take(3)
        if (others.size < 3) return null
        val options = (others + correct).shuffled(random)
        return SahihQuizQuestion(
            type = SahihQuizType.COMPREHENSION,
            passage = "«${h.arabic.take(160)}…»",
            prompt = "في أي باب ورد هذا الحديث؟",
            options = options,
            correctIndex = options.indexOf(correct),
            explanation = "ورد في باب: $correct (حديث رقم ${h.idInBook})"
        )
    }

    private fun buildMemorization(
        h: FullSahihHadith,
        pool: List<FullSahihHadith>,
        chapterName: Map<Int, String>,
        random: kotlin.random.Random
    ): SahihQuizQuestion? {
        val words = h.arabic.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < MIN_WORDS_FOR_COMPLETION) return null
        val cut = (words.size * 0.55).toInt().coerceIn(4, words.size - COMPLETION_TAKE_WORDS)
        val head = words.take(cut).joinToString(" ")
        val correct = words.drop(cut).take(COMPLETION_TAKE_WORDS).joinToString(" ")
        val distractors = pool
            .filter { it.id != h.id && it.arabic.wordCount() >= MIN_WORDS_FOR_COMPLETION }
            .shuffled(random)
            .take(3)
            .mapNotNull { d ->
                val w = d.arabic.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (w.size < cut + COMPLETION_TAKE_WORDS) null
                else w.drop(cut).take(COMPLETION_TAKE_WORDS).joinToString(" ")
            }
            .filter { it != correct }
            .distinct()
            .take(3)
        if (distractors.size < 3) return null
        val options = (distractors + correct).shuffled(random)
        val chapter = chapterName[h.chapterId] ?: ""
        return SahihQuizQuestion(
            type = SahihQuizType.MEMORIZATION,
            passage = "«$head … ؟»",
            prompt = "أكمل الحديث الشريف من حفظك:",
            options = options,
            correctIndex = options.indexOf(correct),
            explanation = "التكملة الصحيحة من حديث رقم ${h.idInBook} (باب: $chapter)"
        )
    }

    private fun String.wordCount(): Int = split(Regex("\\s+")).count { it.isNotBlank() }
}

/**
 * 🎓 حوار اختبار الفهم والحفظ — 100 سؤال من الكتاب الكامل مع نتيجة ونقاط صدارة.
 */
@Composable
fun SahihQuizDialog(
    bookType: SahihBookType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val goldColor = Color(0xFFD4AF37)
    val bookName = if (bookType == SahihBookType.BUKHARI) "صحيح البخاري" else "صحيح مسلم"
    val prefsKey = if (bookType == SahihBookType.BUKHARI) "quiz_best_bukhari" else "quiz_best_muslim"

    var questions by remember { mutableStateOf<List<SahihQuizQuestion>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(-1) }
    // النقاط تُمنح لأول جولة مكتملة فقط — منعاً لثغرة جمع النقاط، الإعادة للمراجعة بلا نقاط
    val awardedKey = prefsKey + "_points_awarded"
    val quizPrefs = remember { context.getSharedPreferences("sahih_quiz", Context.MODE_PRIVATE) }
    var pointsAwarded by remember { mutableStateOf(quizPrefs.getBoolean(awardedKey, false)) }
    var roundPaid by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(quizPrefs.getInt(prefsKey, 0)) }

    LaunchedEffect(bookType) {
        questions = withContext(Dispatchers.Default) {
            val book = FullSahihHadithRepository.loadBook(context, bookType)
            book?.let { SahihQuizEngine.generateRound(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", color = goldColor) }
        },
        title = { Text("🎓 اختبار $bookName", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            val qs = questions
            when {
                qs == null -> {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = goldColor)
                        Spacer(Modifier.height(8.dp))
                        Text("جاري تجهيز الأسئلة من الكتاب الكامل...", fontSize = 12.sp)
                    }
                }
                qs.isEmpty() -> {
                    Text("تعذّر تجهيز الأسئلة — أعد فتح الكتاب ثم حاول مجدداً.", fontSize = 13.sp)
                }
                index >= qs.size -> {
                    // النتيجة النهائية — صرف النقاط مرة واحدة فقط لأول جولة مكتملة
                    val isRecord = score > best
                    if (isRecord) {
                        best = score
                        quizPrefs.edit().putInt(prefsKey, score).apply()
                    }
                    val isPerfect = score == qs.size
                    val roundPoints = score * 2 + (if (isPerfect) 50 else 0)
                    if (!pointsAwarded && !roundPaid && roundPoints > 0) {
                        roundPaid = true
                        pointsAwarded = true
                        quizPrefs.edit().putBoolean(awardedKey, true).apply()
                        PointsManager.awardCustomPoints(
                            context,
                            roundPoints,
                            "اختبار $bookName: $score/${qs.size}"
                        )
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (score >= 80) "🏆 ممتاز! حافظ متقن"
                            else if (score >= 50) "🌟 جيد — واصل المراجعة"
                            else "📖 راجع الأبواب وحاول مجدداً",
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("نتيجتك: $score / ${qs.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = goldColor)
                        if (!quizPrefs.getBoolean(awardedKey, false) || roundPaid) {
                            Text("نقاط الجولة: $roundPoints 🏅 (تُصرف لأول جولة فقط)", fontSize = 12.sp, color = Color(0xFF10B981))
                        } else {
                            Text("وضع مراجعة — النقاط صُرفت سابقاً لأول جولة", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("أفضل درجة: $best / ${qs.size}", fontSize = 12.sp, color = Color.Gray)
                        if (isRecord) Text("🎉 رقم قياسي جديد!", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        if (isPerfect) Text("💯 علامة كاملة — اسمك يصعد في الصدارة!", color = goldColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                index = 0; score = 0; picked = -1; roundPaid = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                        ) { Text("جولة جديدة 🔄", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
                else -> {
                    val q = qs[index]
                    Column {
                        LinearProgressIndicator(
                            progress = { (index + 1).toFloat() / qs.size },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "سؤال ${index + 1} / ${qs.size} • " +
                                if (q.type == SahihQuizType.COMPREHENSION) "فهم 📖" else "حفظ 🧠" +
                                if (pointsAwarded) " • مراجعة (بلا نقاط)" else " • النقاط: $score إجابة ✅",
                            fontSize = 11.sp, color = goldColor, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(q.passage, fontSize = 13.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(q.prompt, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            itemsIndexed(q.options) { i, opt ->
                                val isCorrect = i == q.correctIndex
                                val isPicked = i == picked
                                val bg = when {
                                    picked == -1 -> Color(0xFF1E293B)
                                    isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                    isPicked -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                    else -> Color(0xFF1E293B)
                                }
                                Surface(
                                    onClick = {
                                        if (picked == -1) {
                                            picked = i
                                            if (isCorrect) score++
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = bg,
                                    border = BorderStroke(1.dp, if (isCorrect && picked != -1) Color(0xFF10B981) else Color.Transparent),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Text(opt, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                                }
                            }
                        }
                        if (picked != -1) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                (if (picked == q.correctIndex) "✅ أحسنت! " else "❌ ") + q.explanation,
                                fontSize = 12.sp, color = if (picked == q.correctIndex) Color(0xFF10B981) else Color(0xFFFCA5A5)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { index++; picked = -1 }) {
                                    Text(if (index < qs.size - 1) "التالي ⬅️" else "النتيجة 🏁", color = goldColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
