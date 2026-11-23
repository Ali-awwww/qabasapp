package com.qabas.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * مهمة فيديو حية في طابور الاستوديو — داخل العملية فقط.
 *
 * (ق41) طابور الاستوديو السحابي سجلٌ للمنتهيات فقط بالتصميم: جدول
 * `studio_jobs` بلا تحديث من العميل (RLS تسمح بالإلغاء فقط، ولا منحة
 * UPDATE أصلاً)، فلا صف «جارٍ» يظهر أبداً أثناء التوليد المتزامن
 * (حتى ~6 دقائق)، وإن مات التطبيق أثناءه اختفت المهمة بلا أثر. التوليد
 * نفسه متزامن داخل العملية، فالحياة الصحيحة للمهمة الحية هي حياة
 * العملية: سجل في الذاكرة يبدأ مع التوليد وينتهي في finally، وتقدمه
 * الحقيقي من onProgress في HiggsfieldVideoService — بلا صفوف عالقة.
 */
data class StudioLiveJob(
    val id: String,
    val prompt: String,
    val startedAtMs: Long,
    val progress: Float,
    val statusText: String
)

object StudioLiveJobs {
    private val _jobs = MutableStateFlow<List<StudioLiveJob>>(emptyList())
    val jobs: StateFlow<List<StudioLiveJob>> = _jobs.asStateFlow()

    fun start(prompt: String): String {
        val id = UUID.randomUUID().toString()
        _jobs.value = _jobs.value + StudioLiveJob(
            id = id,
            prompt = prompt,
            startedAtMs = System.currentTimeMillis(),
            progress = 0f,
            statusText = "بدء التوليد…"
        )
        return id
    }

    fun update(id: String, progress: Float, statusText: String) {
        _jobs.value = _jobs.value.map {
            if (it.id == id) it.copy(
                progress = progress.coerceIn(0f, 1f),
                statusText = statusText
            ) else it
        }
    }

    fun finish(id: String) {
        _jobs.value = _jobs.value.filterNot { it.id == id }
    }
}
