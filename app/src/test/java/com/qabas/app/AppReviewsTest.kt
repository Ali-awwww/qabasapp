package com.qabas.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReviewsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testAppReviewDtoSerialization() {
        val review = SupabaseServices.AppReviewDto(
            id = "test-uuid-1234",
            authorName = "أبو عبد الرحمن",
            rating = 5,
            categoryRating = "overall",
            appVersion = "v1.2.1 (3)",
            deviceModel = "Samsung SM-S928B (Android 15)",
            comment = "تطبيق رائع جداً ومفيد في إنتاج المقاطع الدعوية بدون موسيقى.",
            helpfulCount = 12,
            verifiedUser = true,
            createdAt = "2026-10-03T00:00:00Z"
        )

        val encoded = json.encodeToString(review)
        assertTrue(encoded.contains("author_name"))
        assertTrue(encoded.contains("device_model"))
        assertTrue(encoded.contains("helpful_count"))

        val decoded = json.decodeFromString<SupabaseServices.AppReviewDto>(encoded)
        assertEquals("test-uuid-1234", decoded.id)
        assertEquals("أبو عبد الرحمن", decoded.authorName)
        assertEquals(5, decoded.rating)
        assertEquals("overall", decoded.categoryRating)
        assertEquals("v1.2.1 (3)", decoded.appVersion)
        assertEquals(12, decoded.helpfulCount)
        assertTrue(decoded.verifiedUser)
    }

    @Test
    fun testRatingCoercionAndDefaults() {
        val defaultReview = SupabaseServices.AppReviewDto()
        assertEquals(5, defaultReview.rating)
        assertEquals("مستخدم قبس", defaultReview.authorName)
        assertTrue(defaultReview.verifiedUser)
        assertNotNull(defaultReview.id)
    }
}
