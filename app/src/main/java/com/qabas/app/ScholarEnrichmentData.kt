package com.qabas.app

/**
 * تصنيف زمني عام لسير الأكابر.
 *
 * لا تُضاف هنا مسافات سفر أو أحداث أو محاور قصصية تلقائياً؛
 * لا يظهر في التطبيق إلا ما كُتب في سجل الترجمة بعد المراجعة.
 */
object ScholarEnrichmentData {
    const val ERA_ALL = "الكل"
    const val ERA_PROPHETIC = "العصر النبوي"
    const val ERA_RASHIDUN = "عصر الخلفاء الراشدين"
    const val ERA_UMAYYAD = "العصر الأموي"
    const val ERA_ABBASID = "العصر العباسي"
    const val ERA_AYYUBID = "العصر الأيوبي وبدايات المماليك"
    const val ERA_MAMLUK = "العصر المملوكي"

    val ERAS_LIST = listOf(
        ERA_ALL, ERA_PROPHETIC, ERA_RASHIDUN, ERA_UMAYYAD,
        ERA_ABBASID, ERA_AYYUBID, ERA_MAMLUK
    )

    const val TRIAL_ALL = "جميع المحن"
    const val TRIAL_PRISON = "⛓️ السجن والتعذيب"
    const val TRIAL_MARTYRDOM = "⚔️ الشهادة والجهاد"
    const val TRIAL_POVERTY = "💧 الفقر والجوع والزهد"
    const val TRIAL_RULERS = "🛡️ الثبات ومواجهة الطغيان"
    const val TRIAL_EXILE = "🌾 الغربة والتهجير"

    val TRIAL_TYPES = listOf(
        TRIAL_ALL, TRIAL_PRISON, TRIAL_MARTYRDOM,
        TRIAL_POVERTY, TRIAL_RULERS, TRIAL_EXILE
    )

    fun enrich(scholar: ScholarBiography): ScholarBiography {
        val era = scholar.era ?: when {
            scholar.id in setOf("scholar_1", "scholar_2") -> ERA_UMAYYAD
            scholar.id in setOf("scholar_3", "scholar_4", "scholar_5", "scholar_6", "scholar_7", "scholar_8") -> ERA_ABBASID
            scholar.id in setOf("scholar_9", "scholar_10") -> ERA_AYYUBID
            scholar.id in setOf("scholar_11", "scholar_12", "scholar_13") -> ERA_MAMLUK
            scholar.id in setOf("sahabi_20", "sahabi_21") -> ERA_UMAYYAD
            scholar.id.startsWith("sahabi_") -> ERA_RASHIDUN
            else -> ERA_ABBASID
        }

        return scholar.copy(
            era = era,
            majorHistoricalEvent = null,
            journeyStops = emptyList(),
            estimatedTravelKm = null,
            customFacets = emptyList()
        )
    }
}
