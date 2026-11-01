package com.qabas.app

/**
 * رقع الوكيل (Agent Patches): استخراج مسار الملف المستهدف
 * واستخراج مقترحات الفروقات من الكود المولّد — في مكان واحد.
 */
fun extractTargetFile(prompt: String, responseText: String): String {
    // 1. فحص وجود مسار صريح في رأس الكود أو التعليقات
    val fileRegex = Regex("""(?://|#|--)\s*(?:File|Path|Target):\s*([a-zA-Z0-9_/.-]+\.kt)|```[a-zA-Z0-9_]*:([a-zA-Z0-9_/.-]+\.kt)""", RegexOption.IGNORE_CASE)
    val match = fileRegex.find(responseText)
    val found = match?.groups?.get(1)?.value ?: match?.groups?.get(2)?.value
    if (!found.isNullOrBlank()) {
        return found.substringAfterLast("/")
    }

    // 2. مطابقة أسماء الملفات الرئيسية في المشروع عند ذكرها في الرد أو البرومبت
    val knownFiles = listOf(
        "MainActivity.kt", "QabasHomeScreen.kt", "AppNavigation.kt", "SettingsScreen.kt",
        "DeveloperDashboardScreen.kt", "AudioPlayerManager.kt", "RealServices.kt",
        "AgentConsoleTab.kt", "BuildCenterSection.kt", "CrashLogsHubScreen.kt",
        "QuranDataProvider.kt", "QabasBrainViewModel.kt", "BRollEngine.kt",
        "HealthCenterSection.kt", "AuditLogSection.kt", "RepoVaultManager.kt"
    )
    for (f in knownFiles) {
        val base = f.removeSuffix(".kt")
        if (prompt.contains(base, ignoreCase = true) || prompt.contains(f, ignoreCase = true) ||
            responseText.contains(f, ignoreCase = true)) {
            return f
        }
    }

    // 3. لا نخمن ملفاً مستهدفاً عند غياب مسار صريح أو اسم ملف معروف.
    // يُحفظ الناتج كمقترح عام منفصل بدلاً من نسبته إلى ملف مصدر غير متحقق منه.
    return "AgentProposal.kt"
}

object AgentPatchKit {
    /**
     * 🔑 مسار آمن لحفظ مقترح الوكيل دون استبدال ملفات الكود الحقيقية.
     * المقترح الناتج مقتطف جزئي (ليس ملفاً كاملاً)، لذا يُكتب في مجلد منفصل للمراجعة اليدوية.
     */
    fun proposalPath(originalPath: String): String {
        val name = originalPath.substringAfterLast('/').ifBlank { "proposal.kt" }
        return "qabas-agent-proposals/${System.currentTimeMillis()}_$name"
    }

    fun extractDiffs(prompt: String, codeText: String): List<FileDiff> {
        val blocks = Regex("```(\\w*)\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(codeText)
            .map { it.groupValues[2].trim() }
            .filter { it.length > 20 }
            .toList()
        if (blocks.isEmpty()) {
            if (codeText.contains("fun ") || codeText.contains("class ")) {
                val target = runCatching { extractTargetFile(prompt, codeText) }.getOrDefault("AgentProposal.kt")
                return listOf(
                    FileDiff(
                        filePath = "app/src/main/java/com/qabas/app/$target",
                        additions = codeText.lines().size,
                        deletions = 0,
                        diffText = codeText.take(4000)
                    )
                )
            }
            return emptyList()
        }
        val mainTarget = runCatching { extractTargetFile(prompt, codeText) }
            .getOrDefault("مقترح_عام.kt")
        return blocks.take(5).mapIndexed { idx, code ->
            val name = if (idx == 0) mainTarget else "مقترح_${idx + 1}.kt"
            FileDiff(
                filePath = "app/src/main/java/com/qabas/app/$name",
                additions = code.lines().size,
                deletions = 0,
                diffText = code.take(4000)
            )
        }
    }
}
