package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * خريطة الصوت الأساسي في VoiceStudio صادقة: كل نمط صوتي يحمل baseEngine
 * مثل «Edge-Neural / ar-SA-NaayfNeural» فيُسمَع فعلاً بذلك الصوت —
 * لا «F5-TTS / Edge-Neural» عام يسقط كل الأصوات على HamedNeural.
 */
class VoiceStudioBaseVoiceTest {

    @Test
    fun `hamed label maps to HamedNeural`() {
        assertEquals(
            "ar-SA-HamedNeural",
            VoiceStudioService.baseVoiceForEngine("Edge-Neural / ar-SA-HamedNeural")
        )
    }

    @Test
    fun `salma label maps to ZariyahNeural`() {
        assertEquals(
            "ar-SA-ZariyahNeural",
            VoiceStudioService.baseVoiceForEngine("Edge-Neural / ar-SA-ZariyahNeural")
        )
    }

    @Test
    fun `shakir label maps to ShakirNeural`() {
        assertEquals(
            "ar-EG-ShakirNeural",
            VoiceStudioService.baseVoiceForEngine("Edge-Neural / ar-EG-ShakirNeural")
        )
    }

    @Test
    fun `hamdan label maps to HamdanNeural`() {
        assertEquals(
            "ar-AE-HamdanNeural",
            VoiceStudioService.baseVoiceForEngine("Edge-Neural / ar-AE-HamdanNeural")
        )
    }

    @Test
    fun `naayf label maps to NaayfNeural instead of falling through to Hamed`() {
        assertEquals(
            "ar-SA-NaayfNeural",
            VoiceStudioService.baseVoiceForEngine("Edge-Neural / ar-SA-NaayfNeural")
        )
    }

    @Test
    fun `unknown engine falls back to HamedNeural`() {
        assertEquals(
            "ar-SA-HamedNeural",
            VoiceStudioService.baseVoiceForEngine("F5-TTS / Edge-Neural")
        )
        assertEquals(
            "ar-SA-HamedNeural",
            VoiceStudioService.baseVoiceForEngine("")
        )
    }
}
