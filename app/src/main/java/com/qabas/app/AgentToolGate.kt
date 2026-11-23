package com.qabas.app

import android.content.Context

/**
 * بوابة تنفيذ أدوات الوكيل (12.6 + 12.16) — نقطة الفحص الواحدة قبل أي تنفيذ أداة.
 *
 * حقائق مسار التنفيذ (مُتحقق منها في الكود، لا ادعاء):
 * - مفاتيح التفعيل في تبويب الأدوات كانت تُقرأ في الواجهة فقط
 *   ([AgentPrefs.isToolEnabled] لا يُستدعى إلا في AgentToolsTab) — هنا تُفرض فعلاً.
 * - حلقة تنفيذ أدوات LLM الحية الوحيدة هي [OpenRouterService.chatWithTools]
 *   (تُستدعى من AIAssistantScreen بأدوات AiTools فقط) — وهي مربوطة هنا.
 * - دوال CodeTools (delete_file / merge_pr / create_pr / …) لا يمررها أي مسار
 *   حي إلى حلقة أدوات اليوم؛ تُعرض فقط في تبويب الأدوات. مع ذلك تُغلَّف
 *   في [CodeTools.definitions] بهذه البوابة، فأي مستدعٍ مستقبلي لها مربوط
 *   بالفحص نفسه دون إعادة كتابة.
 * - تنفيذ خطة المحتوى في StudioAgentScreen يمر عبر runStudioTool بعد اعتماد
 *   بطاقة الخطة (PLAN_PENDING) — فاعتماد الخطة هو التأكيد البشري هناك،
 *   لذا يُفحص التفعيل ونطاق المسارات فقط دون طلب موافقة مدمرة ثانية.
 *
 * الأدوات المدمرة (delete_file / merge_pr / create_pr / publish_to_reels)
 * لا تُنفذ في حلقة LLM إلا بموافقة بشرية لمرة واحدة صالحة 10 دقائق،
 * تُمنح من تبويب الأدوات عبر حوار تأكيد حقيقي.
 */
object AgentToolGate {

    val DESTRUCTIVE_TOOLS = setOf("delete_file", "merge_pr", "create_pr", "publish_to_reels")

    /** صلاحية الموافقة المدمرة لمرة واحدة. */
    const val DESTRUCTIVE_APPROVAL_VALID_MS = 10 * 60 * 1000L

    fun requiresConfirmation(name: String): Boolean = name in DESTRUCTIVE_TOOLS

    /** يمنح موافقة بشرية لمرة واحدة على أداة مدمرة (من حوار تأكيد في تبويب الأدوات). */
    fun approveDestructive(context: Context, name: String) {
        AgentPrefs.putLong(context, "destructive_approval_${name}_at", System.currentTimeMillis())
    }

    fun hasDestructiveApproval(context: Context, name: String): Boolean {
        val at = AgentPrefs.getLong(context, "destructive_approval_${name}_at", 0L)
        return at > 0L && System.currentTimeMillis() - at <= DESTRUCTIVE_APPROVAL_VALID_MS
    }

    /** يستهلك الموافقة (مرة واحدة) — يُعيد false إن لم توجد موافقة صالحة. */
    fun consumeDestructiveApproval(context: Context, name: String): Boolean {
        if (!hasDestructiveApproval(context, name)) return false
        AgentPrefs.remove(context, "destructive_approval_${name}_at")
        return true
    }

    /**
     * فحص ما قبل التنفيذ. يُعيد null عند السماح، أو رسالة رفض صادقة عند المنع.
     * الترتيب: التفعيل ← نطاق المسار ← نطاق الفرع ← الموافقة المدمرة.
     */
    fun checkBeforeRun(
        context: Context,
        name: String,
        path: String? = null,
        branch: String? = null,
        requireDestructiveApproval: Boolean = true
    ): String? {
        if (!AgentPrefs.isToolEnabled(context, name)) {
            return "الأداة «$name» معطّلة من تبويب الأدوات — فعّلها أولاً."
        }
        if (path != null && !AgentPrefs.isPathAllowed(context, path)) {
            return "المسار «$path» خارج قائمة السماح في تبويب التحكم."
        }
        if (branch != null && !AgentPrefs.isBranchAllowed(context, branch)) {
            return "الفرع «$branch» خارج قائمة السماح في تبويب التحكم."
        }
        if (requireDestructiveApproval && name in DESTRUCTIVE_TOOLS &&
            !consumeDestructiveApproval(context, name)
        ) {
            return "الأداة «$name» مدمرة وتتطلب تأكيداً بشرياً — أكّدها من تبويب الأدوات " +
                "قبل التنفيذ (الموافقة صالحة 10 دقائق لمرة واحدة)."
        }
        return null
    }
}
