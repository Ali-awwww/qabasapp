package com.qabas.app

/**
 * جسر مؤقت لتمرير موارد المستخدم من RESOURCES إلى PROCESSING
 * دون كسر توقيع ProcessingScreen الحالي.
 * يُملأ قبل الانتقال إلى PROCESSING ويُستهلك داخل مسار المشاهد.
 */
object PendingMediaBridge {
    @Volatile
    var resources: List<MediaResource> = emptyList()

    fun set(list: List<MediaResource>) {
        resources = list
    }

    fun take(): List<MediaResource> {
        val current = resources
        resources = emptyList()
        return current
    }

    fun peek(): List<MediaResource> = resources
}
