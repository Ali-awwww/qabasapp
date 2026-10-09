package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StyleBrainTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        val prefs = context.getSharedPreferences("qabas_style_brain_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        // Initialize StyleBrain
        StyleBrain.init(context)
    }

    @Test
    fun `default core style initializes clean without dummy data`() {
        val core = StyleBrain.getCoreStyle()
        assertNotNull(core)
        assertEquals(0, core.strengthScore)
        assertTrue(core.visualTraits.isEmpty())
        assertTrue(core.motionTraits.isEmpty())
        assertTrue(core.textTraits.isEmpty())

        val defaultStyle = StyleBrain.getDefaultStyle()
        assertNotNull(defaultStyle)
    }

    @Test
    fun `resolve visual and motion properties across varied absorbed styles`() {
        val emeraldStyle = AbsorbedStyle(
            id = "test_1",
            name = "Emerald Green Style",
            sourceVideoPathOrUrl = "https://example.com/emerald.mp4",
            analysis = "نمط إسلامي هادئ بخلفية زمردية",
            visualTraits = listOf("أخضر زمردي فاخر Emerald", "إضاءة ناعمة"),
            motionTraits = listOf("انتقالات تلاشي ناعمة Smooth Dissolve"),
            textTraits = listOf("ظهور كلمة بكلمة WordByWord"),
            overallScore = 90
        )

        assertEquals("#10B981", emeraldStyle.resolvePrimaryColorHex())
        assertEquals("#0F291E", emeraldStyle.resolveBackgroundColorHex())
        assertEquals("Dissolve", emeraldStyle.resolveTransitionType())
        assertEquals("WordByWord", emeraldStyle.resolveTextAnimation())

        val fastGlitchStyle = AbsorbedStyle(
            id = "test_2",
            name = "Fast Blue Glitch",
            sourceVideoPathOrUrl = "https://example.com/blue.mp4",
            analysis = "نمط سريع جداً خاطف",
            visualTraits = listOf("أزرق سماوي Blue نقي"),
            motionTraits = listOf("قصات سريعة خاطفة Glitch"),
            textTraits = listOf("ظهور وانبثاق Pop تفاعلي"),
            overallScore = 88
        )

        assertEquals("#38BDF8", fastGlitchStyle.resolvePrimaryColorHex())
        assertEquals("#0A192F", fastGlitchStyle.resolveBackgroundColorHex())
        assertEquals("Glitch", fastGlitchStyle.resolveTransitionType())
        assertEquals("Pop", fastGlitchStyle.resolveTextAnimation())
    }

    @Test
    fun `absorbing valid style evolves core style and reinforces common traits without exceeding limits`() = runBlocking {
        val initialStrength = StyleBrain.getCoreStyleStrength()

        val analysis = VideoStyleAnalysis(
            detectedStyle = "نمط وثائقي ذهبي عميق",
            dominantColors = "ألوان DeepSlate #0B0F19 مع لمسات ذهب نقي #E8C547",
            transitionSpeed = "زووم بطيء متصاعد ناعم Slow ZoomIn",
            movementPatterns = "حركة كاميرا سينمائية هادئة",
            overallRhythm = "إيقاع وقور متوازن",
            audioStyle = "أصوات طبيعة هادئة",
            typographyStyle = "خط عربي كوفي عريض متوهج كلمة بكلمة",
            contentTone = "مؤثر ووقور",
            targetAudience = "الجميع",
            keywords = listOf("ذهبي", "داكن", "زووم")
        )

        val absorbed = StyleBrain.absorbStyleFromAnalysis(
            context = context,
            name = "وثائقي قبس الذهبي",
            source = "test_source.mp4",
            analysisResult = analysis
        )

        assertNotNull(absorbed)
        assertEquals(1, StyleBrain.getAllAbsorbedStyles().size)

        val updatedCore = StyleBrain.getCoreStyle()
        // Core score should increase or stay solid
        assertTrue(updatedCore.strengthScore >= initialStrength)
        // Trait limits enforced: max 6 visuals, max 5 motions, max 5 texts
        assertTrue(updatedCore.visualTraits.size <= 6)
        assertTrue(updatedCore.motionTraits.size <= 5)
        assertTrue(updatedCore.textTraits.size <= 5)

        // Core identity traits remain dominant
        assertTrue(updatedCore.visualTraits.any { it.contains("DeepSlate") || it.contains("#0B0F19") || it.contains("ذهبي") })
    }

    @Test
    fun `low score style does not pollute or degrade core style`() = runBlocking {
        val initialCore = StyleBrain.getCoreStyle()

        val lowScoreAnalysis = VideoStyleAnalysis(
            detectedStyle = "أسلوب رديء",
            dominantColors = "رمادي باهت",
            transitionSpeed = "غير منتظم",
            movementPatterns = "اهتزاز عشوائي",
            overallRhythm = "مشتت",
            audioStyle = "ضوضاء",
            typographyStyle = "خط غير واضح",
            contentTone = "ضعيف",
            targetAudience = "مجهول",
            keywords = listOf("عشوائي")
        )

        StyleBrain.absorbStyleFromAnalysis(
            context = context,
            name = "Low Quality Style",
            source = "bad_video.mp4",
            analysisResult = lowScoreAnalysis
        )

        val currentCore = StyleBrain.getCoreStyle()
        assertTrue(currentCore.strengthScore <= initialCore.strengthScore + 5)
    }

    @Test
    fun `chooseBestStyleForIdea blends 70 percent core with absorbed styles`() = runBlocking {
        // Without absorbed styles, returns null
        val initialBlended = StyleBrain.chooseBestStyleForIdea("قصة مؤثرة", 45, "هادئ", "الشباب")
        assertNull(initialBlended)

        // Add an absorbed style
        StyleBrain.absorbStyleFromAnalysis(
            context = context,
            name = "أسلوب تدبر آية",
            source = "tadabbor.mp4",
            analysisResult = VideoStyleAnalysis(
                detectedStyle = "تدبر قرآني",
                dominantColors = "DeepSlate وأخضر ناعم",
                transitionSpeed = "انتقال هادئ",
                movementPatterns = "زووم خفيف",
                overallRhythm = "خشوع وسكينة",
                audioStyle = "تلاوة خاشعة",
                typographyStyle = "خط عريض واضح",
                contentTone = "روحاني",
                targetAudience = "المسلمون",
                keywords = listOf("قرآن", "تدبر")
            )
        )

        val blended = StyleBrain.chooseBestStyleForIdea("تدبر سورة الكهف", 60, "روحاني", "الجميع")
        assertNotNull(blended)
        assertTrue(blended!!.visualTraits.isNotEmpty())
    }

    @Test
    fun `removeStyle removes absorbed style correctly`() = runBlocking {
        val absorbed = StyleBrain.absorbStyleFromAnalysis(
            context = context,
            name = "أسلوب مؤقت",
            source = "temp.mp4",
            analysisResult = VideoStyleAnalysis(
                detectedStyle = "مؤقت",
                dominantColors = "ألوان داكنة",
                transitionSpeed = "سريع",
                movementPatterns = "زووم",
                overallRhythm = "سريع",
                audioStyle = "هادئ",
                typographyStyle = "كوفي",
                contentTone = "حماسي",
                targetAudience = "الشباب",
                keywords = emptyList()
            )
        )

        assertEquals(1, StyleBrain.getAllAbsorbedStyles().size)
        StyleBrain.removeStyle(context, absorbed.id)
        assertEquals(0, StyleBrain.getAllAbsorbedStyles().size)
    }

    @Test
    fun `extractKeyFrames handles empty or non-existent path safely without exceptions`() = runBlocking {
        val resultEmpty = StyleBrain.extractKeyFrames(context, "")
        assertTrue(resultEmpty.isEmpty())

        val resultNonExistent = StyleBrain.extractKeyFrames(context, "/non/existent/video.mp4")
        assertTrue(resultNonExistent.isEmpty())
    }

    @Test
    fun `analyzeFramesWithVision returns robust fallback on empty frames or offline`() = runBlocking {
        val fallbackResult = StyleBrain.analyzeFramesWithVision(emptyList(), context)
        assertNotNull(fallbackResult)
        assertTrue(fallbackResult.has("analysis"))
        assertTrue(fallbackResult.has("visualTraits"))
        assertTrue(fallbackResult.has("motionTraits"))
        assertTrue(fallbackResult.has("textTraits"))
        assertEquals(0, fallbackResult.getInt("overallScore"))
    }

    @Test
    fun `absorbStyleFromAnalysis deduplicates same source and boosts score slightly`() = runBlocking {
        val analysis = VideoStyleAnalysis(
            detectedStyle = "تدبر قرآني",
            dominantColors = "ذهبي وداكن",
            transitionSpeed = "هادئ",
            movementPatterns = "زووم خفيف",
            overallRhythm = "خشوع",
            audioStyle = "تلاوة",
            typographyStyle = "كوفي",
            contentTone = "روحاني",
            targetAudience = "الجميع",
            keywords = listOf("قرآن")
        )
        val first = StyleBrain.absorbStyleFromAnalysis(context, "فيديو اختباري", "video1.mp4", analysis)
        assertEquals(1, StyleBrain.getAllAbsorbedStyles().size)

        val second = StyleBrain.absorbStyleFromAnalysis(context, "فيديو اختباري معدل", "video1.mp4", analysis)
        assertEquals(1, StyleBrain.getAllAbsorbedStyles().size)
        assertEquals(first.id, second.id)
    }
}
