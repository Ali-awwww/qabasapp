package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectWorkspaceCodecTest {
    @Test
    fun `workspace snapshot round trips scenes resources and source details`() {
        val state = ProjectState(
            selectedRatio = "1:1",
            inputText = "فكرة اختبارية",
            projectTitle = "مشروع محفوظ",
            mediaResources = listOf(
                MediaResource("media-1", "video", "clip.mp4", uri = "file:///clip.mp4", isBRoll = true)
            ),
            libraryScenes = listOf(
                Scene("المشهد الأول", "وصف المشهد", 12, mediaUrl = "file:///scene.mp4")
            ),
            sourceType = "hadith",
            sourceUris = listOf("content://source/1"),
            selectedVerseNumber = 7,
            finalVideoPath = "files/exports/final.mp4"
        )

        val restored = ProjectWorkspaceCodec.decode(ProjectWorkspaceCodec.encode(state))

        assertNotNull(restored)
        assertEquals("فكرة اختبارية", restored?.inputText)
        assertEquals("1:1", restored?.selectedRatio)
        assertEquals("clip.mp4", restored?.mediaResources?.single()?.name)
        assertEquals(12, restored?.libraryScenes?.single()?.durationInSeconds)
        assertEquals("hadith", restored?.sourceType)
        assertEquals(7, restored?.selectedVerseNumber)
        assertEquals("files/exports/final.mp4", restored?.finalVideoPath)
    }
}
