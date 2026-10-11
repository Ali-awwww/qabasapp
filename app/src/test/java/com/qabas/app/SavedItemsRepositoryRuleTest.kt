package com.qabas.app

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SavedItemsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun authenticatedOwner_canSaveAndObserveItem() = runBlocking {
        val userEmail = "test_user_${System.currentTimeMillis()}@example.com"
        val userId = signInTestUser(userEmail)

        val repository = SavedItemsRepository(firestore)

        val testItem = SavedItem(
            id = "item_ayah_1",
            userId = userId,
            itemType = SavedItemType.QURAN_AYAH.rawValue,
            title = "آية الكرسي",
            content = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
            reference = "سورة البقرة: 255",
            tags = listOf("قرآن", "أدعية")
        )

        // Save item
        repository.saveItem(testItem)

        // Observe items flow
        val items = repository.observeSavedItems(userId).first { it.isNotEmpty() }
        assertNotNull(items)
        assertEquals(1, items.size)
        assertEquals("آية الكرسي", items[0].title)
        assertEquals(SavedItemType.QURAN_AYAH, items[0].type)

        // Check isItemSavedFlow
        val isSaved = repository.isItemSavedFlow(userId, "item_ayah_1").first { it }
        assertTrue(isSaved)

        // Delete item
        repository.deleteItem(userId, "item_ayah_1")
        val itemsAfterDelete = repository.observeSavedItems(userId).first { it.isEmpty() }
        assertEquals(0, itemsAfterDelete.size)
    }

    @Test
    fun unauthenticatedAccess_failsWithPermissionDenied() = runBlocking {
        // Sign out to test unauthenticated access
        auth.signOut()

        val repository = SavedItemsRepository(firestore)

        val testItem = SavedItem(
            id = "unauth_item_1",
            userId = "unauth_user",
            itemType = SavedItemType.INSPIRATION.rawValue,
            title = "إلهام",
            content = "محتوى غير مصرح به"
        )

        try {
            repository.saveItem(testItem)
            fail("Expected FirebaseFirestoreException for unauthenticated write")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun crossUserAccess_cannotWriteToAnotherUsersCollection() = runBlocking {
        val aliceEmail = "alice_${System.currentTimeMillis()}@example.com"
        val aliceUid = signInTestUser(aliceEmail)

        val repository = SavedItemsRepository(firestore)

        // Alice attempts to write to Bob's collection
        val maliciousItem = SavedItem(
            id = "cross_item_1",
            userId = "bob_victim_uid",
            itemType = SavedItemType.QURAN_AYAH.rawValue,
            title = "اختراق",
            content = "محاولة كتابة غير مصرح بها"
        )

        try {
            repository.saveItem(maliciousItem)
            fail("Expected FirebaseFirestoreException for cross-user write")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
