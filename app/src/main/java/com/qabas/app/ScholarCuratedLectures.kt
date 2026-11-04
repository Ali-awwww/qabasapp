package com.qabas.app

/**
 * مكتبة المحاضرات المعتمدة.
 *
 * لا تُعرض نتائج بحث عامة على أنها محاضرات محددة أو مواد راجعها المطور.
 * تُضاف أي مادة إلى هذا المسار بعد مراجعة الرابط والمتحدث والمحتوى.
 */
object ScholarCuratedLectures {
    fun getCuratedLectures(scholarId: String, scholarName: String): List<ScholarLecture> {
        if (scholarId == SamirMustafaAqaidAlBalaaData.SCHOLAR_ID ||
            scholarName.contains("سمير مصطفى")
        ) {
            return SamirMustafaAqaidAlBalaaData.toScholarLectures()
        }
        return emptyList()
    }
}
