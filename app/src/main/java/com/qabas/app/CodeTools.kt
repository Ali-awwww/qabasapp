package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * أدوات وكيل الكود 🤖 — يستدعيها نموذج OpenRouter أثناء بناء تطبيق العميل:
 * تصفح الشجرة • قراءة ملف • كتابة ملف • تشغيل البناء • حالة البناء • فتح سحب.
 * تعمل على فرع التطوير فقط — الرئيسي محمي بالدمج اليدوي.
 */
object CodeTools {

    data class Ctx(
        val owner: String,
        val repo: String,
        val token: String,
        val branch: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun strProp(desc: String) = JSONObject().put("type", "string").put("description", desc)

    /**
     * تعريفات الأدوات مع بوابة التنفيذ (12.6 + 12.16): كل استدعاء لأي أداة يمر
     * عبر [AgentToolGate.checkBeforeRun] — التفعيل من تبويب الأدوات، ونطاق
     * المسار/الفرع من تبويب التحكم، وموافقة بشرية لمرة واحدة للأدوات المدمرة.
     * لا يمرر أي مسار حي هذه التعريفات إلى حلقة أدوات LLM اليوم (الحلقة
     * الوحيدة تستدعي AiTools فقط) — والبوابة هنا دفاعٌ في نقطة التعريف نفسها
     * لأي مستدعٍ حالي أو مستقبلي.
     */
    fun definitions(context: Context, ctx: CodeTools.Ctx): List<AiTools.ToolDef> {
        return buildDefinitions(ctx).map { def ->
            def.copy(run = { args ->
                val path = args.optString("path").takeIf { it.isNotBlank() }
                AgentToolGate.checkBeforeRun(context, def.name, path = path, branch = ctx.branch)
                    ?: try {
                        def.run(args)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // الإلغاء ليس خطأً ولا يُبتلَع (نمط الجولة 87 في AgentBrain):
                        // إعادة رميه حتى لا يواصل الوكيل تنفيذ الأدوات بعد أن
                        // ألغى المستخدم المهمة — وإلا عاد «تعذر تنفيذ الأداة»
                        // وتابعت الحلقة كأن الأداة فشلت فحسب.
                        throw e
                    } catch (e: Exception) {
                        "تعذر تنفيذ الأداة ${def.name}."
                    }
            })
        }
    }

    private fun buildDefinitions(ctx: CodeTools.Ctx): List<AiTools.ToolDef> {
        val rc = GitHubRepoClient(ctx.owner, ctx.repo, ctx.token)
        return listOf(
            AiTools.ToolDef(
                "list_tree",
                "قائمة ملفات المستودع على فرع العمل (المسارات فقط).",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    val tree = rc.getTree(ctx.branch)
                        ?: return@withContext "تعذر قراءة الشجرة."
                    tree.take(120).joinToString("\n") { it.path }.ifBlank { "الفرع فارغ." }
                }
            },
            AiTools.ToolDef(
                "read_file",
                "قراءة محتوى ملف من فرع العمل (حتى 8000 محرف).",
                JSONObject().put("path", strProp("مسار الملف، مثال: app/src/main/java/com/client/app/MainActivity.kt")),
                listOf("path")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val path = args.optString("path")
                    val content = rc.getFileContent(path, ctx.branch)?.content
                        ?.let { runCatching { String(android.util.Base64.decode(it.replace("\\s".toRegex(), ""), android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() }
                    content?.take(8000) ?: "تعذر قراءة $path."
                }
            },
            AiTools.ToolDef(
                "write_file",
                "إنشاء ملف جديد أو استبداله كاملاً على فرع العمل. اكتب المحتوى الكامل لا فرقاً.",
                JSONObject()
                    .put("path", strProp("مسار الملف الكامل"))
                    .put("content", strProp("المحتوى الكامل للملف"))
                    .put("message", strProp("رسالة الـ commit القصيرة")),
                listOf("path", "content")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val path = args.optString("path")
                    val content = args.optString("content")
                    val msg = args.optString("message").ifBlank { "وكيل خادم المسلم: $path" }
                    if (path.isBlank() || content.isBlank()) return@withContext "مسار أو محتوى فارغ."
                    if (".." in path || path.length > 200) return@withContext "مسار مرفوض."
                    val ok = rc.createFile(path, ctx.branch, msg, content)
                    if (ok) "حُفظ $path (${content.length} محرف) ✅" else "فشل حفظ $path."
                }
            },
            AiTools.ToolDef(
                "trigger_build",
                "تشغيل بناء APK (ملف android-ci.yml على الفرع الافتراضي).",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    val body = JSONObject().put("ref", ctx.branch).toString()
                        .toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/${ctx.owner}/${ctx.repo}/actions/workflows/android-ci.yml/dispatches")
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "Qabas-Studio")
                        .header("Authorization", "Bearer ${ctx.token}")
                        .post(body)
                        .build()
                    val code = runCatching { client.newCall(req).execute().use { it.code } }.getOrDefault(-1)
                    if (code == 204) "أُرسل أمر البناء ✅" else "تعذر التشغيل (كود $code) — تحقق من وجود android-ci.yml والصلاحيات."
                }
            },
            AiTools.ToolDef(
                "build_status",
                "حالة آخر تشغيلات البناء (ناجح/فاشل/يعمل) مع أول سطر سبب عند الفشل.",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/${ctx.owner}/${ctx.repo}/actions/runs?per_page=5")
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "Qabas-Studio")
                        .header("Authorization", "Bearer ${ctx.token}")
                        .get()
                        .build()
                    val body = runCatching { client.newCall(req).execute().use { it.body?.string().orEmpty() } }.getOrDefault("")
                    if (body.isBlank()) return@withContext "تعذر جلب حالة البناء."
                    val arr = JSONObject(body).optJSONArray("workflow_runs") ?: return@withContext "لا تشغيلات بعد."
                    if (arr.length() == 0) return@withContext "لا تشغيلات بعد — شغّل البناء أولاً."
                    buildString {
                        for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            append("#${r.optString("name")} [${r.optString("status")}/${r.optString("conclusion", "—")}] فرع ${r.optString("head_branch")} (${r.optString("created_at").take(10)})\n")
                        }
                    }
                }
            },
            AiTools.ToolDef(
                "create_pr",
                "فتح سحب من فرع العمل إلى الفرع الافتراضي بعد انتهائك.",
                JSONObject()
                    .put("title", strProp("عنوان السحب"))
                    .put("body", strProp("وصف التغييرات")),
                listOf("title")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val n = rc.createPullRequest(
                        ctx.branch,
                        args.optString("title").ifBlank { "عمل الوكيل" },
                        args.optString("body"),
                        rc.getDefaultBranch()
                    )
                    if (n != null && n > 0) "فُتح السحب #$n ✅" else "تعذر فتح السحب (قد يوجد واحد مفتوح)."
                }
            },
            AiTools.ToolDef(
                "update_file",
                "تعديل ملف موجود على فرع العمل (يجلب sha تلقائياً). للملفات الجديدة استخدم write_file.",
                JSONObject()
                    .put("path", strProp("مسار الملف الكامل"))
                    .put("content", strProp("المحتوى الكامل الجديد للملف"))
                    .put("message", strProp("رسالة الـ commit القصيرة")),
                listOf("path", "content")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val path = args.optString("path")
                    val content = args.optString("content")
                    val msg = args.optString("message").ifBlank { "وكيل خادم المسلم: تحديث $path" }
                    if (path.isBlank() || content.isBlank()) return@withContext "مسار أو محتوى فارغ."
                    if (".." in path || path.length > 200) return@withContext "مسار مرفوض."
                    val sha = rc.getFileContent(path, ctx.branch)?.sha
                        ?: return@withContext "الملف غير موجود على الفرع — استخدم write_file للإنشاء."
                    val ok = rc.createFile(path, ctx.branch, msg, content, sha)
                    if (ok) "حُدّث $path (${content.length} محرف) ✅" else "فشل تحديث $path."
                }
            },
            AiTools.ToolDef(
                "delete_file",
                "حذف ملف من فرع العمل نهائياً — استخدمه بحذر.",
                JSONObject()
                    .put("path", strProp("مسار الملف الكامل"))
                    .put("message", strProp("سبب الحذف")),
                listOf("path")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val path = args.optString("path")
                    if (path.isBlank() || ".." in path) return@withContext "مسار مرفوض."
                    val sha = rc.getFileContent(path, ctx.branch)?.sha
                        ?: return@withContext "الملف غير موجود أصلاً."
                    val ok = rc.deleteFile(path, ctx.branch, args.optString("message").ifBlank { "وكيل خادم المسلم: حذف $path" }, sha)
                    if (ok) "حُذف $path ✅" else "فشل حذف $path."
                }
            },
            AiTools.ToolDef(
                "list_branches",
                "كل فروع المستودع — ليعرف الوكيل أين يعمل وأين دُمج.",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    rc.getBranches()?.joinToString("\n") { it.name }?.ifBlank { "لا فروع." }
                        ?: "تعذر جلب الفروع."
                }
            },
            AiTools.ToolDef(
                "recent_commits",
                "آخر 10 commits على فرع العمل — سياق ما تم فعله.",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    rc.getCommits(ctx.branch, 10)?.joinToString("\n") { "${it.sha.take(7)} ${it.message.take(80)}" }
                        ?.ifBlank { "لا commits." } ?: "تعذر جلب السجل."
                }
            },
            AiTools.ToolDef(
                "list_prs",
                "السحوبات المفتوحة — ليراجع الوكيل عمله قبل الدمج.",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    rc.listPullRequests("open").joinToString("\n").ifBlank { "لا سحوبات مفتوحة." }
                }
            },
            AiTools.ToolDef(
                "pr_files",
                "ملفات سحب معين مع حجم التغيير — للمراجعة قبل الدمج.",
                JSONObject().put("number", strProp("رقم السحب")),
                listOf("number")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val n = args.optString("number").toIntOrNull() ?: return@withContext "رقم غير صالح."
                    rc.getPullFiles(n).joinToString("\n").ifBlank { "لا ملفات أو سحب غير موجود." }
                }
            },
            AiTools.ToolDef(
                "merge_pr",
                "دمج سحب بعد مراجعة ملفاته — الخطوة الأخيرة قبل التسليم.",
                JSONObject().put("number", strProp("رقم السحب")),
                listOf("number")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val n = args.optString("number").toIntOrNull() ?: return@withContext "رقم غير صالح."
                    if (rc.mergePullRequest(n)) "دُمج السحب #$n ✅" else "فشل الدمج — راجع الصلاحيات أو تعارضات الدمج."
                }
            },
            AiTools.ToolDef(
                "create_issue",
                "فتح قضية لتتبع عيب أو مهمة اكتشفها الوكيل أثناء العمل.",
                JSONObject()
                    .put("title", strProp("عنوان القضية"))
                    .put("body", strProp("وصف العيب أو المهمة")),
                listOf("title")
            ) { args ->
                withContext(Dispatchers.IO) {
                    if (rc.createIssue(args.optString("title"), args.optString("body"))) "فُتحت القضية ✅"
                    else "تعذر فتح القضية."
                }
            },
            AiTools.ToolDef(
                "list_issues",
                "القضايا المفتوحة — مهام بانتظار المعالجة.",
                JSONObject(), emptyList()
            ) { _ ->
                withContext(Dispatchers.IO) {
                    rc.listIssues().joinToString("\n").ifBlank { "لا قضايا مفتوحة 🎉" }
                }
            },
            AiTools.ToolDef(
                "search_code_symbols",
                "بحث في أسماء ملفات المشروع (لا بحث دلالي في المحتوى): يطابق " +
                    "الاستعلام مسارات الملفات في شجرة الفرع فقط.",
                JSONObject().put("query", strProp("جزء من اسم ملف أو مسار، مثال: MainActivity")),
                listOf("query")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val query = args.optString("query").trim()
                    if (query.isBlank()) return@withContext "استعلام البحث فارغ."
                    val tree = rc.getTree(ctx.branch) ?: return@withContext "تعذر قراءة شجرة الملفات."
                    val matched = tree.filter { it.path.contains(query, ignoreCase = true) }.take(15)
                    if (matched.isEmpty()) "لم يتم العثور على ملفات تطابق «$query»."
                    else matched.joinToString("\n") { "• ${it.path} (${it.size} bytes)" }
                }
            },
            AiTools.ToolDef(
                "diagnose_build_error",
                "تشخيص وتحليل أسباب فشل البناء (Build Error Diagnostics) وتحديد السطر المسبب والحل المقترح.",
                JSONObject().put("log_snippet", strProp("مقتطف من سجل الخطأ أو رسالة الفشل")),
                listOf("log_snippet")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val log = args.optString("log_snippet")
                    when {
                        log.contains("Unresolved reference", true) -> "تشخيص: دالة أو مكتبة مفقودة (Missing Import). الحل: إضافة استيراد الحزمة أو تعريف الدالة."
                        log.contains("Type mismatch", true) -> "تشخيص: عدم تطابق في الأنواع (Type Mismatch). الحل: تحويل النوع أو مراجعة المعاملات."
                        log.contains("Out of memory", true) -> "تشخيص: استهلاك ذاكرة زائد. الحل: تحسين أوزان الصور وزيادة gradle heap."
                        else -> "لم يطابق السجل أي نمط خطأ معروف (مرجع مفقود/نوع/ذاكرة) — لا تشخيص محدد هنا. راجع أول سطر خطأ في السجل الكامل وتحقق من الاستيرادات والتبعيات."
                    }
                }
            },
            AiTools.ToolDef(
                "run_linter_check",
                "فحص توازن الأقواس فقط ({} و() و[]) وتوازن علامات السلاسل النصية — " +
                    "لا تحليل نحوي كامل ولا كشف أخطاء الأنواع؛ النجاح هنا يعني التوازن فقط.",
                JSONObject().put("code", strProp("الكود المراد تدقيقه")),
                listOf("code")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val code = args.optString("code")
                    // توازن الأقواس بأنواعها الثلاثة + علامات السلاسل النصية
                    // (مع تجاهل المهرّبة \" والتعليقات السطرية // — تقريبي
                    // وصريح بذلك، لا ادعاء تدقيق نحوي كامل).
                    var unclosedString = false
                    val stripped = buildString {
                        var inString = false
                        var inLineComment = false
                        var i = 0
                        while (i < code.length) {
                            val c = code[i]
                            if (inLineComment) {
                                if (c == '\n') inLineComment = false
                                i++
                                continue
                            }
                            if (inString) {
                                if (c == '\\') { i += 2; continue }
                                if (c == '"') inString = false
                                i++
                                continue
                            }
                            if (c == '/' && i + 1 < code.length && code[i + 1] == '/') {
                                inLineComment = true
                                i += 2
                                continue
                            }
                            if (c == '"') { inString = true; i++; continue }
                            append(c)
                            i++
                        }
                        unclosedString = inString
                    }
                    val openBraces = stripped.count { it == '{' }
                    val closeBraces = stripped.count { it == '}' }
                    val openParens = stripped.count { it == '(' }
                    val closeParens = stripped.count { it == ')' }
                    val openBrackets = stripped.count { it == '[' }
                    val closeBrackets = stripped.count { it == ']' }
                    if (openBraces == closeBraces && openParens == closeParens &&
                        openBrackets == closeBrackets && !unclosedString
                    ) {
                        "✅ الأقواس متوازنة ({}: $openBraces, (): $openParens, []: $openBrackets) وعلامات السلاسل مغلقة — هذا فحص توازن فقط، لا ضمان سلامة نحوية كاملة."
                    } else {
                        "⚠️ عدم توازن: {}: $openBraces/$closeBraces, (): $openParens/$closeParens, []: $openBrackets/$closeBrackets" +
                            if (unclosedString) "، سلسلة نصية بلا علامة إغلاق." else "."
                    }
                }
            },
            AiTools.ToolDef(
                "generate_unit_test",
                "توليد اختبار آلي لوحدة الكود (Unit Test) للتأكد من سلامة منطق العمل قبل الاعتماد.",
                JSONObject().put("function_name", strProp("اسم الدالة المستهدفة")),
                listOf("function_name")
            ) { args ->
                withContext(Dispatchers.IO) {
                    generateUnitTestSnippet(args.optString("function_name"))
                }
            },
            AiTools.ToolDef(
                "refactor_and_optimize",
                "إرشادات إعادة هيكلة عامة لواجهات Jetpack Compose — إرشادات نصية " +
                    "فقط، لا تقرأ شيفرة المكوّن ولا تتحقق من تطبيقه.",
                JSONObject().put("composable_name", strProp("اسم واجهة Compose المراد تحسينها")),
                listOf("composable_name")
            ) { args ->
                withContext(Dispatchers.IO) {
                    val comp = args.optString("composable_name").ifBlank { "MyScreen" }
                    "إرشادات عامة لتحسين «$comp» (لم تُقرأ الشيفرة هنا — طبّقها يدوياً وتحقق بنفسك): " +
                        "استخدم remember{} للحالات المحلية بدل إعادة التوليد، وقدّم Modifier كمعامل افتراضي، " +
                        "وتفادَ تخصيص كائنات داخل حلقة الرسم، واستخرج الحالات الثقيلة إلى ViewModel إن أمكن."
                }
            }
        )
    }

    /**
     * نص اختبار وحدة مولّد لدالة (12.13): توليد نص فقط — لا يشغّل
     * الاختبار. التشغيل عبر CI البعيد في تبويب المعاينة.
     */
    fun generateUnitTestSnippet(functionName: String): String {
        val fn = functionName.trim().ifBlank { "targetFunction" }
        // معرّف الاختبار يجب أن يكون اسم Kotlin صالحاً: اسم مثل
        // com.example.foo() كان يولّد test_com.example.foo()_execution()
        // وهو غير صالح للتجميع — تنظيف كل ما ليس حرفاً/رقماً/شرطة سفلية.
        val safeName = fn.replace(Regex("[^A-Za-z0-9_]"), "_").trim('_').ifBlank { "function" }
        // الاستدعاء: الاسم كما هو (قد يكون مؤهلاً بنقطة) بلا أقواس مُمررة.
        val call = fn.substringBefore("(").trim().ifBlank { "targetFunction" }
        return """
            @Test
            fun test_${safeName}_execution() {
                // عدّل الاستدعاء بمعاملات الدالة إن كانت تأخذ معاملات.
                val result = $call()
                assertNotNull(result)
            }
        """.trimIndent()
    }

    fun toApiJson(context: Context, ctx: CodeTools.Ctx): JSONArray = AiTools.toApiJson(definitions(context, ctx))

}
