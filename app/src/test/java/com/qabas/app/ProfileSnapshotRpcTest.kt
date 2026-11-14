package com.qabas.app

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileSnapshotRpcTest {
    @Test
    fun `profile snapshot RPC is invoker-only and owner-scoped`() {
        val sql = projectFile(
            "supabase/migrations/20261114085700_get_current_profile_snapshot.sql"
        ).readText()

        assertTrue(sql.contains("SECURITY INVOKER"))
        assertFalse(Regex("(?im)^\\s*SECURITY\\s+DEFINER\\b").containsMatchIn(sql))
        assertTrue(sql.contains("auth.uid()"))
        assertTrue(sql.contains("IF v_uid IS NULL"))
        assertTrue(sql.contains("REVOKE ALL ON FUNCTION public.get_current_profile_snapshot() FROM PUBLIC, anon"))
        assertTrue(sql.contains("GRANT EXECUTE ON FUNCTION public.get_current_profile_snapshot() TO authenticated"))

        listOf(
            "public.users",
            "public.profiles",
            "public.reels",
            "public.creator_follows",
            "public.reel_history",
            "public.reel_bookmarks",
            "public.reel_likes"
        ).forEach { table -> assertTrue("Missing snapshot source $table", sql.contains(table)) }

        assertTrue(sql.contains("WHERE h.user_id = v_uid"))
        assertTrue(sql.contains("WHERE b.user_id = v_uid"))
        assertTrue(sql.contains("WHERE l.user_id = v_uid"))
        assertTrue(sql.contains("WHERE f.follower_id = v_uid"))
        assertTrue(sql.contains("WHERE r.user_id = v_uid"))
        assertTrue(sql.contains("LIMIT 100"))
        assertTrue(sql.contains("LIMIT 500"))
    }

    @Test
    fun `profile screen loads Supabase profile data through the single snapshot RPC`() {
        val source = projectFile("app/src/main/java/com/qabas/app/ProfileScreen.kt").readText()

        assertTrue(source.contains("getCurrentProfileSnapshot("))
        assertFalse(source.contains("Database.getUserByEmail("))
        assertFalse(source.contains("UserSettings.getUserBio("))
        assertFalse(source.contains("Profiles.getUserProfile("))
        assertFalse(source.contains("CreatorFollowManager.cloudFollowerCount("))
        assertFalse(source.contains("refreshFollowedCreatorsFromCloud("))
    }

    @Test
    fun `snapshot keeps exact cloud counts when returned lists are bounded`() {
        val payload = SupabaseServices.CurrentProfileSnapshotPayload(
            publishedReelsCount = 140,
            publishedReelsTruncated = true,
            followingCount = 501,
            followingTruncated = true,
            activity = SupabaseServices.ProfileActivityPayload(
                watchHistoryCount = 151,
                savedReelsCount = 108,
                likedReelsCount = 203,
                totalWatchSeconds = 3600,
                watchHistoryTruncated = true,
                savedReelsTruncated = true,
                likedReelsTruncated = true
            )
        )

        val snapshot = payload.toDomain()

        assertEquals(151, snapshot.activity.watchHistoryCount)
        assertEquals(108, snapshot.activity.savedReelsCount)
        assertEquals(203, snapshot.activity.likedReelsCount)
        assertEquals(140, snapshot.publishedReelsCount)
        assertEquals(501, snapshot.followingCount)
        assertEquals(3600L, snapshot.activity.totalWatchSeconds)
        assertTrue(snapshot.publishedReelsTruncated)
        assertTrue(snapshot.followingTruncated)
        assertTrue(snapshot.activity.watchHistoryTruncated)
        assertTrue(snapshot.activity.savedReelsTruncated)
        assertTrue(snapshot.activity.likedReelsTruncated)
    }

    @Test
    fun `snapshot payload is serializable with empty defaults`() {
        val decoded = Json.decodeFromString<SupabaseServices.CurrentProfileSnapshotPayload>("{}")
        val snapshot = decoded.toDomain()

        assertEquals(0, snapshot.publishedReelsCount)
        assertEquals(0, snapshot.activity.watchHistoryCount)
        assertTrue(snapshot.publishedReels.isEmpty())
        assertTrue(snapshot.following.isEmpty())
    }

    @Test
    fun `snapshot decoder maps SQL json keys and preserves bounded activity counts`() {
        val decoded = Json.decodeFromString<SupabaseServices.CurrentProfileSnapshotPayload>(
            """{"published_reels_count":1,"published_reels":[{"id":"reel-1","user_id":"uid-1","title":"ذكر","video_url":"https://example.invalid/reel.mp4","tags":["ذكر"]}],"following":[{"creator_id":"creator-1","full_name":"Creator"}],"activity":{"watchHistoryCount":135,"watchHistory":[{"reelId":"reel-1","title":"ذكر","userName":"Creator","videoUrl":"https://example.invalid/reel.mp4","timestamp":1730000000000,"watchedDurationSec":12,"category":"ذكر"}]}}"""
        ).toDomain()

        assertEquals(1, decoded.publishedReels.size)
        assertEquals("reel-1", decoded.publishedReels.single().id)
        assertEquals("creator-1", decoded.following.single().creatorId)
        assertEquals(1, decoded.activity.watchHistory.size)
        assertEquals(135, decoded.activity.watchHistoryCount)
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
}
