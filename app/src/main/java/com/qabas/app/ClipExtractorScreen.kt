package com.qabas.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private data class GoldenClip(val hook: String, val text: String)

/** حافظة لحظات ذهبية عبر التدوير: زوجا (خطاف، نص) مسطحان (ق101). */
private val goldenClipsSaver = listSaver<List<GoldenClip>, String>(
    save = { clips -> clips.flatMap { listOf(it.hook, it.text) } },
    restore = { flat -> flat.chunked(2).mapNotNull { if (it.size == 2) GoldenClip(it[0], it[1]) else null } }
)

/**
 * مقص المقاطع: خطبة/بودكاست طويل → 5 لحظات ذهبية جاهزة كريلزات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipExtractorScreen(
    initialText: String = "",
    onBack: () -> Unit,
    onCreateProject: (scriptText: String, topic: String) -> Unit
) {
    // ق101: النص الملصوق والتنبيه واللحظات تنجو من تدوير الشاشة —
    // كان تدوير واحد يفقد نصاً طويلاً ألصقه المستخدم ونتائج
    // الاستخراج معاً. أما working فيبقى remember عادياً قصداً:
    // التدوير يلغي الكوروتين فلو استُعيد true لتعطل الزر للأبد
    // بلا عمل جارٍ.
    var longText by rememberSaveable { mutableStateOf(initialText) }
    var clips by rememberSaveable(saver = goldenClipsSaver) { mutableStateOf<List<GoldenClip>>(emptyList()) }
    var working by remember { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** خطاف العرض: «…» للاقتطاع فقط لا لكل جملة (ق101). */
    fun clipHook(text: String): String = if (text.length > 40) text.take(40) + "…" else text

    fun localClips(t: String): List<GoldenClip> {
        val parts = t.split(Regex("[.،!؟\\n]")).map { it.trim() }.filter { it.length > 20 }.take(5)
        return parts.map { GoldenClip(hook = clipHook(it), text = it) }
    }

    fun extract() {
        // ق25: كان الزر لا يفعل شيئاً بصمت عند نص قصير.
        if (longText.length < 30) {
            notice = "الصق نصاً أطول (30 حرفاً على الأقل) ليجد المقص لحظات ذهبية."
            return
        }
        if (working) return
        notice = null
        working = true
        scope.launch {
            val p = """
                من هذا النص الطويل: "${longText.take(3000)}"
                استخرج 5 لحظات ذهبية تصلح ريلزات قصيرة. أخرج JSON فقط: [{"hook":"جملة خطافة أول ثانيتين","text":"نص المقطع 30-50 كلمة"}].
            """.trimIndent()
            val truncated = longText.length > 3000
            var usedLocalFallback = false
            // المسار الأول: Groq يعيد JSON مباشرة.
            val groqRaw = withContext(Dispatchers.IO) {
                try { RealGroqService.chatOrGenerate(p) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            }
            var parsed: List<GoldenClip> = emptyList()
            var groqOk = false
            if (groqRaw != null && groqRaw.contains("[")) {
                parsed = try {
                    val arr = JSONArray(groqRaw.substring(groqRaw.indexOf("["), groqRaw.lastIndexOf("]") + 1))
                    (0 until arr.length()).map {
                        val o = arr.getJSONObject(it)
                        GoldenClip(o.optString("hook", "لحظة ذهبية"), o.optString("text", ""))
                    }.filter { it.text.isNotBlank() }
                } catch (_: Exception) { emptyList() }
                groqOk = parsed.isNotEmpty()
            }
            clips = if (groqOk) {
                parsed
            } else {
                // ق101: احتياط Gemini كان joinToString لعناوين المشاهد
                // فينتج نصاً بلا "[" فيُرمى دائماً ويُستبدل بالتقسيم
                // المحلي بصمت — فالمسار الثاني كله كان وهماً. الآن
                // تُبنى اللحظات من المشاهد نفسها (عنوانها العربي
                // جملة قابلة للسرد).
                val geminiClips = withContext(Dispatchers.IO) {
                    try {
                        RealGeminiService.generateScript(longText, "", "موعظة", "مؤثر")
                            .filter { it.title.isNotBlank() }
                            .take(5)
                            .map { GoldenClip(hook = clipHook(it.title), text = it.title) }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
                }
                if (geminiClips.isNotEmpty()) geminiClips else {
                    usedLocalFallback = true
                    localClips(longText)
                }
            }
            // ق101: تنبيه صادق عن مصدر اللحظات — كان الفشل الصامت
            // يعرض تقسيماً محلياً على أنه استخراج ذكي، والنتيجة
            // الفارغة كانت تترك الشاشة خالية بلا أي رسالة.
            notice = when {
                clips.isEmpty() -> "لم تُعثر على لحظات في هذا النص — جرّب نصاً أطول بجمل مكتملة."
                usedLocalFallback -> "تعذّر استخراج اللحظات بالذكاء الاصطناعي — عُرضت لحظات مستخرجة محلياً من النص." +
                    if (truncated) " (حُلِّل أول 3000 حرف فقط.)" else ""
                truncated -> "حُلِّل أول 3000 حرف من النص فقط."
                else -> null
            }
            working = false
        }
    }

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = { Text("مقص المقاطع ✂️", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardSurface)
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = longText, onValueChange = { longText = it },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text("الصق نص الخطبة أو التفريغ الطويل هنا...", fontFamily = CairoFont, color = TextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PresetPrimary, unfocusedBorderColor = Color(0xFF1E293B), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                ProdPrimaryButton(text = if (working) "المقص يعمل..." else "قص اللحظات الذهبية ✂️", onClick = { extract() })
                // ق101: مؤشر تقدّم غير محدد أثناء الاستدعاء — كان نص
                // الزر وحده إشارة العمل، بلا أي مؤشر مرئي.
                if (working) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PresetPrimary, trackColor = Color(0xFF1E293B))
                }
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = PresetPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
            itemsIndexed(clips) { i, c ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("المقطع ${i + 1}: ${c.hook}", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(c.text, color = Color.White, fontFamily = CairoFont, fontSize = 13.sp)
                        Button(onClick = { onCreateProject(c.text, c.hook.take(30)) }, colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary), shape = RoundedCornerShape(10.dp)) {
                            Text("إنتاج فيديو 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
