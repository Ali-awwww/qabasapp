package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * تغطية الترجمة (ق33): القاموس الإنجليزي كان منفصلاً عن الواجهة —
 * 460 من 540 مدخلاً بلا أي استعمال، و89% من النصوص المستعملة بلا
 * ترجمة، فيظهر الوضع الإنجليزي عربياً في معظمه، وtr() يعيد النص
 * العربي بصمت عند غياب الترجمة فلا يفشل شيء أبداً.
 * هذا الاختبار يفحص الشجرة المصدرية نفسها ويفشل عند أي انحراف جديد:
 * مفتاح مكرر، أو استدعاء tr() بنص فيه استيفاء ${...}/$var (لا يترجم
 * أبداً — مكانه trf() بقالب {0}...)، أو نص ثابت بلا ترجمة.
 * ملاحظة: مدخلات القاموس بلا مستعمل ثابت مسموحة — بعضها مفاتيح
 * ديناميكية (عناوين بيانات، أسماء كتب تفسير) تمرر عبر tr(var).
 */
class TranslatorCoverageTest {

    private fun projectDir(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            if (File(directory, "app/src/main/java/com/qabas/app/Translator.kt").isFile) {
                return directory
            }
            directory = directory.parentFile
        }
        throw AssertionError("Project root not found from user.dir")
    }

    private fun dictionaryKeys(translatorSource: String): List<String> {
        val block = Regex(
            """private val dictionaryEn = mapOf\((.*?)\)\s*\n\s*fun tr""",
            RegexOption.DOT_MATCHES_ALL
        ).find(translatorSource)?.groupValues?.get(1)
            ?: throw AssertionError("dictionaryEn block not found in Translator.kt")
        return Regex("""^\s*"((?:[^"\\]|\\.)*)" to """, RegexOption.MULTILINE)
            .findAll(block).map { it.groupValues[1] }.toList()
    }

    /** نص الاستدعاء الكامل مع موازنة الأقواس (يحتمل سلاسل وif/else). */
    private fun scanCall(source: String, openParenIndex: Int): String {
        var depth = 1
        var index = openParenIndex
        var inString = false
        var escaped = false
        while (index < source.length && depth > 0) {
            val c = source[index]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                if (c == '"') inString = true
                else if (c == '(') depth++
                else if (c == ')') depth--
            }
            index++
        }
        return source.substring(openParenIndex, index - 1)
    }

    private fun stringLiterals(callBody: String): List<String> =
        // سلسلة عادية (لا خام) لتجنّب غموض """" في السلاسل الخام.
        Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(callBody).map { it.groupValues[1] }.toList()

    @Test
    fun `dictionary has no duplicate keys`() {
        val translator = File(
            projectDir(),
            "app/src/main/java/com/qabas/app/Translator.kt"
        ).readText()
        val keys = dictionaryKeys(translator)
        val duplicates = keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("Duplicate dictionary keys: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every static tr and trf literal is translated and none is interpolated`() {
        val root = File(projectDir(), "app/src/main/java")
        val translator = File(root, "com/qabas/app/Translator.kt").readText()
        val keys = dictionaryKeys(translator).toSet()
        val interpolated = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var checked = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val source = file.readText()
            Regex("""Translator\.(trf?)\(""").findAll(source).forEach { match ->
                val isTemplate = match.groupValues[1] == "trf"
                val body = scanCall(source, match.range.last + 1)
                var literals = stringLiterals(body)
                if (isTemplate) literals = literals.take(1)
                for (literal in literals) {
                    checked++
                    if (literal.contains('$')) {
                        interpolated.add("${file.name}: $literal")
                    } else if (literal !in keys) {
                        missing.add("${file.name}: $literal")
                    }
                }
            }
        }
        assertTrue("Interpolated tr() literals (use trf instead): $interpolated", interpolated.isEmpty())
        assertTrue("Untranslated literals: $missing", missing.isEmpty())
        assertTrue("Expected hundreds of checked literals, got $checked", checked > 500)
    }

    @Test
    fun `trf substitutes placeholders and falls back to the template`() {
        try {
            Translator.setLanguage("ar")
            assertEquals("تم اختيار 3 صور بنجاح ✓", Translator.trf("تم اختيار {0} صور بنجاح ✓", 3))
            Translator.setLanguage("en")
            assertEquals("3 images selected successfully ✓", Translator.trf("تم اختيار {0} صور بنجاح ✓", 3))
            assertEquals(
                "Source: QuranEnc — Saheeh International",
                Translator.trf("المصدر: QuranEnc — {0}", "Saheeh International")
            )
            // قالب بلا ترجمة يُعاد كما هو مع القيم (لا يُخترع نص ولا يُرمى استثناء).
            assertEquals("قالب غير مترجم 7", Translator.trf("قالب غير مترجم {0}", 7))
        } finally {
            Translator.setLanguage("ar")
        }
    }

    @Test
    fun `dictionary keys resolve to themselves in arabic mode`() {
        try {
            Translator.setLanguage("ar")
            assertEquals("الإعدادات", Translator.tr("الإعدادات"))
            Translator.setLanguage("en")
            assertEquals("Settings", Translator.tr("الإعدادات"))
        } finally {
            Translator.setLanguage("ar")
        }
    }
}
