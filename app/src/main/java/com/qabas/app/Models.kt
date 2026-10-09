package com.qabas.app

import androidx.compose.runtime.Immutable

@Immutable
data class IdeaAnalysis(
    val summary: String,
    val suggestedStyle: String = "",
    val goal: String = "",
    val targetAudience: String = "",
    val tone: String = "",
    val keywords: List<String> = emptyList(),
    val proposedScenes: List<String> = emptyList(),
    val viralityScore: Int = 0,
    val hookSuggestions: List<String> = emptyList(),
    val ctaSuggestions: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val themes: List<String> = emptyList(),
    val source: String = "MODEL"
)

@Immutable
data class VideoStyleAnalysis(
    val detectedStyle: String = "",
    val dominantColors: String = "",
    val transitionSpeed: String = "",
    val movementPatterns: String = "",
    val overallRhythm: String = "",
    val audioStyle: String = "",
    val typographyStyle: String = "",
    val contentTone: String = "",
    val targetAudience: String = "",
    val keywords: List<String> = emptyList()
)

@Immutable
data class TrendingIdea(
    val title: String,
    val description: String,
    val viralityScore: Int = 0,
    val tags: List<String> = emptyList(),
    val source: String = "MODEL"
)

@Immutable
data class Template(
    val id: String = "",
    val name: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val colors: String = "",
    val transitions: String = "",
    val textAnimation: String = ""
)

@Immutable
data class ScholarBiography(
    val id: String,
    val name: String,
    val brief: String,
    val imageRes: Int,
    val audioUrl: String? = null,
    val period: String? = null,
    val tags: List<String> = emptyList(),
    val sacrificeSummary: String? = null,
    val enduranceQuote: String? = null,
    val travelDistance: String? = null,
    val globalImpact: String? = null,
    val messageToGrandson: String? = null,
    val modernChallenge: String? = null,
    val whoHarmedHimAndHow: String? = null, // من الذين آذوه وكيف؟
    val howHeEndured: String? = null,       // كيف تحمّل وسر الصمود؟
    val whyHeEndured: String? = null,       // لماذا تحمّل؟ القضية والغاية الكبرى
    val deathScene: String? = null,         // مشهد الوفاة واللحظات الأخيرة
    val lastWords: String? = null,          // آخر كلماته قبل خروج الروح
    val funeralImpact: String? = null,      // مشهد الجنازة وتأثر الأمة
    val era: String? = null,                // العصر التاريخي (النبوي، الراشدي، الأموي، العباسي، المماليك)
    val majorHistoricalEvent: String? = null, // الحدث التاريخي الأبرز
    val journeyStops: List<String> = emptyList(), // محطات رحلة العلم والجهاد
    val estimatedTravelKm: Int? = null,     // المسافة التقديرية بالكيلومترات
    val customFacets: List<CharacterFacet> = emptyList() // محاور مخصصة حية وفق عبقرية وتفرد كل شخصية
)

@Immutable
data class CharacterFacet(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,            // عنوان المحور الخاص بالشخصية
    val subtitle: String = "",    // فكرة المحور بكلمات موجزة
    val badge: String = "",       // وسم تصنيفي يعبر عن جوهر المحور
    val iconKey: String = "book", // مفتاح الأيقونة المعبرة
    val narrative: String,        // السرد القصصي والتاريخي العميق
    val keyQuote: String? = null, // مقولة أو موقف خالد متعلق بالمحور
    val practicalInsight: String? = null // الأثر الإيماني والتطبيق العملي المعاصر
)

@Immutable
data class ScholarLecture(
    val id: String = java.util.UUID.randomUUID().toString(),
    val scholarId: String,
    val scholarName: String = "",
    val title: String,
    val speaker: String = "",
    val url: String,
    val type: String = "يوتيوب / مرئي", // "يوتيوب / مرئي", "صوتي / بودكاست", "مقال / رابط علمي"
    val category: String = "المحنة والثبات", // "المحنة والثبات", "المناقب والفضائل", "الجهاد والفتوحات", "الفقه والحديث", "سيرة جامعة"
    val synopsis: String = "", // سرد عميق ومفصل لمضمون الدرس وأبرز محاوره
    val durationApprox: String = "", // مدة تقريبية (مثلاً: 45 دقيقة / سلسلة)
    val isOfficialDeveloperCurated: Boolean = true, // معتمد من المطور والمنصة
    val notes: String = "",
    val addedAtMillis: Long = System.currentTimeMillis()
)

@Immutable
data class LectureProposal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val scholarId: String,
    val scholarName: String = "",
    val suggestedTitle: String,
    val suggestedSpeaker: String = "",
    val suggestedUrl: String = "",
    val suggestedTopic: String = "سيرة وعبر", // المحور المقترح
    val rationale: String = "", // سبب الاقتراح والفائدة المرجوة للأمة
    val userProposerName: String = "طالب علم",
    val upvotesCount: Int = 1,
    val status: String = "قيد المراجعة العلمية", // "قيد المراجعة العلمية", "معتمد ومجدول للتسجيل", "مضاف في المنصة"
    val createdAtMillis: Long = System.currentTimeMillis()
)
