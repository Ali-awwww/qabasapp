package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
