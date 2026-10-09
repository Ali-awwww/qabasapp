package com.qabas.app

/**
 * يربط موارد المستخدم (رفع جهاز + B-Roll) بمشاهد السيناريو قبل استدعاء التوليد.
 * الأولوية:
 * 1) فيديو/صورة المستخدم بالترتيب
 * 2) B-Roll المختار يدوياً
 * 3) مطابقة BRollEngine حسب وصف المشهد
 * 4) وإلا يُترك فارغاً ليقوم ProcessingScreen بالتوليد/الجلب
 */
object ResourceMediaResolver {

    /**
     * يوزّع الوسائط على قائمة المشاهد.
     * @param userOnly إن true لا يُستخدم مطابقة B-Roll التلقائية من المكتبة
     */
    fun assignMediaToScenes(
        scenes: List<Scene>,
        resources: List<MediaResource>,
        userOnly: Boolean = false
    ): List<Scene> {
        if (scenes.isEmpty()) return scenes

        val userVisual = resources.filter {
            !it.uri.isNullOrBlank() &&
                (it.type.equals("video", true) || it.type.equals("image", true)) &&
                !it.isBRoll
        }
        val userBRoll = resources.filter {
            !it.uri.isNullOrBlank() && (it.isBRoll || it.type.equals("broll", true))
        }

        var userIdx = 0
        var brollIdx = 0

        return scenes.map { scene ->
            // إن كان للمشهد media مسبقاً صالحاً اتركه
            val existing = scene.mediaUrl
            if (!existing.isNullOrBlank() &&
                (existing.startsWith("http") || existing.startsWith("content:") ||
                    existing.startsWith("file:") || java.io.File(existing).exists())
            ) {
                return@map scene
            }

            // 1) موارد المستخدم
            if (userIdx < userVisual.size) {
                val uri = userVisual[userIdx].uri!!
                userIdx++
                return@map scene.copy(mediaUrl = uri)
            }

            // 2) B-Roll الذي أضافه المستخدم
            if (brollIdx < userBRoll.size) {
                val uri = userBRoll[brollIdx].uri!!
                brollIdx++
                return@map scene.copy(mediaUrl = uri)
            }

            // 3) مطابقة تلقائية من مكتبة B-Roll (ما لم يطلب المستخدم «مواردي فقط»)
            if (!userOnly) {
                val matched = BRollEngine.matchBRoll(
                    "${scene.title} ${scene.description}"
                )
                if (matched.videoUrl.isNotBlank()) {
                    return@map scene.copy(mediaUrl = matched.videoUrl)
                }
            }

            scene
        }
    }

    /** صوت مرفوع من المستخدم (أول مقطع صوتي) — يمكن دمجه كـ ambient لاحقاً */
    fun firstUserAudioUri(resources: List<MediaResource>): String? =
        resources.firstOrNull {
            !it.uri.isNullOrBlank() && it.type.equals("audio", true)
        }?.uri

    fun countAssigned(scenes: List<Scene>): Int =
        scenes.count { !it.mediaUrl.isNullOrBlank() }
}
