package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreatorFollowManagerTest {
    @Test
    fun `identity matching tolerates handles and case`() {
        assertTrue(
            CreatorFollowManager.identitiesMatch(
                "AliWalead.2007",
                "aliwalead.2007",
                "aliwalead.2007",
                "Aliwalead.2007",
                "ALIWALEAD.2007@example.com"
            )
        )
        assertTrue(
            CreatorFollowManager.identitiesMatch(
                "user-123",
                "منشئ قبس",
                "another-id",
                "منشئ قبس",
                "creator@example.com"
            )
        )
        assertFalse(
            CreatorFollowManager.identitiesMatch(
                "creator-a",
                "المنشئ أ",
                "viewer-b",
                "المشاهد ب",
                "viewer@example.com"
            )
        )
    }

    @Test
    fun `creator relationships require the exact canonical user id`() {
        assertTrue(CreatorFollowManager.sameCanonicalUserId("auth-uid", "auth-uid"))
        assertFalse(CreatorFollowManager.sameCanonicalUserId("same-display-name", "auth-uid"))
        assertFalse(CreatorFollowManager.sameCanonicalUserId("", "auth-uid"))
        assertFalse(CreatorFollowManager.sameCanonicalUserId("auth-uid", ""))
    }

    @Test
    fun `follow storage keys are isolated by authenticated user id`() {
        val firstAccount = CreatorFollowManager.userScopedPreferenceKey("followed_creators", "uid-a")
        val secondAccount = CreatorFollowManager.userScopedPreferenceKey("followed_creators", "uid-b")

        assertEquals("followed_creators:uid-a", firstAccount)
        assertEquals("followed_creators:uid-b", secondAccount)
        assertFalse(firstAccount == secondAccount)
        assertEquals("followed_creators:uid-a", CreatorFollowManager.userScopedPreferenceKey("followed_creators", " uid-a "))
        assertEquals(null, CreatorFollowManager.userScopedPreferenceKey("followed_creators", " "))
        assertEquals(null, CreatorFollowManager.userScopedPreferenceKey("", "uid-a"))
    }

    @Test
    fun `social relationship migration and setup template never grant raw public reads`() {
        val migration = projectFile(
            "supabase/migrations/20261114080057_harden_social_relationship_reads.sql"
        ).readText()
        val rpcHardening = projectFile(
            "supabase/migrations/20261114080251_restrict_social_rpc_surface.sql"
        ).readText()
        val setupTemplate = projectFile(
            "app/src/main/java/com/qabas/app/ui/dialogs/SupabaseCloudConfigDialog.kt"
        ).readText()

        assertTrue(migration.contains("REVOKE SELECT ON TABLE public.creator_follows FROM PUBLIC, anon"))
        assertTrue(migration.contains("REVOKE SELECT ON TABLE public.reel_likes FROM PUBLIC, anon"))
        assertTrue(migration.contains("creator_follows_select_own"))
        assertTrue(migration.contains("reel_likes_select_own"))
        assertTrue(migration.contains("get_creator_follower_count"))
        assertTrue(rpcHardening.contains("JOIN public.reels AS r"))
        assertTrue(rpcHardening.contains("REVOKE ALL ON FUNCTION public.sync_reel_likes_count()"))
        assertFalse(setupTemplate.contains("CREATE POLICY \"Allow public access for app anon likes\""))
        assertFalse(setupTemplate.contains("CREATE POLICY \"Allow public access for app anon creator follows\""))
    }

    private fun projectFile(relativePath: String): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("Project file not found: $relativePath")
    }

    @Test
    fun `public creator profile contract contains only public fields`() {
        val descriptor = SupabaseServices.PublicCreatorProfileRow.serializer().descriptor
        val fields = (0 until descriptor.elementsCount)
            .map { descriptor.getElementName(it) }
            .toSet()

        assertEquals(setOf("id", "full_name", "avatar_url"), fields)
    }

    @Test
    fun `baseline follower estimate is stable and bounded`() {
        val first = CreatorFollowManager.estimatedBaselineFollowers("creator-1")
        val second = CreatorFollowManager.estimatedBaselineFollowers("creator-1")
        assertEquals(first, second)
        assertTrue(first in 96..191)
    }

    @Test
    fun `missing table errors do not look like network errors`() {
        assertTrue(
            CreatorFollowManager.isMissingFollowTable(
                "PGRST205: Could not find the table 'public.creator_follows' in the schema cache"
            )
        )
        assertFalse(CreatorFollowManager.isMissingFollowTable("SocketTimeoutException: timeout"))
        assertFalse(CreatorFollowManager.isMissingFollowTable(null))
    }

    @Test
    fun `follow id sets toggle without duplicates`() {
        val added = CreatorFollowManager.withFollowedId(setOf("a", "b"), "C")
        assertEquals(setOf("a", "b", "C"), added)
        val removed = CreatorFollowManager.withoutFollowedId(added, "c")
        assertEquals(setOf("a", "b"), removed)
    }
}
