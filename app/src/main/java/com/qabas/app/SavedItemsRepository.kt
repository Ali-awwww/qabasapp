package com.qabas.app

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * مستودع العناصر المحفوظة (القرآن الكريم، الإلهام، الأدعية) المدعوم بـ Firebase Firestore
 * يستغل مميزات فايربيس الفريدة:
 * 1. التخزين المحلي التلقائي والمفهرس بدون اتصال (Offline Persistent Cache)
 * 2. المزامنة اللحظية الحية عبر تدفقات Snapshot Listeners
 * 3. تتبع مصدر البيانات (هل البيانات من الكاش المحلي أم من السحابة)
 */
class SavedItemsRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseManager.getFirestore(context.applicationContext)
    )

    init {
        // تفعيل الإعدادات المحسنة لذاكرة التخزين المؤقت الدائمة
        try {
            val settings = FirebaseFirestoreSettings.Builder(db.firestoreSettings)
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(104857600L) // 100 MB Cache
                        .build()
                )
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) {
            // يتم ضبط الإعدادات مرة واحدة عند تهيئة النسخة
        }
    }

    /**
     * مراقبة كافة العناصر المحفوظة للمستخدم في الوقت الحقيقي عبر تدفق Snapshot Listener
     */
    fun observeSavedItems(userId: String): Flow<List<SavedItem>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val collectionRef = db.collection("users")
            .document(userId)
            .collection("saved_items")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$userId/saved_items")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val item = doc.toObject(SavedItem::class.java)
                        item?.copy(id = doc.id)
                    } catch (_: Exception) {
                        null
                    }
                }
                trySend(items)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * مراقبة العناصر المحفوظة حسب النوع (قرآن / خواطر / أذكار)
     */
    fun observeSavedItemsByType(userId: String, type: SavedItemType): Flow<List<SavedItem>> =
        observeSavedItems(userId).map { list ->
            list.filter { it.type == type }
        }

    /**
     * التحقق اللحظي مما إذا كان عنصر معين محفوظاً في السحابة
     */
    fun isItemSavedFlow(userId: String, itemId: String): Flow<Boolean> = callbackFlow {
        if (userId.isBlank() || itemId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val docRef = db.collection("users")
            .document(userId)
            .collection("saved_items")
            .document(itemId)

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                trySend(false)
                return@addSnapshotListener
            }
            trySend(snapshot != null && snapshot.exists())
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * حفظ عنصر مع ضمان التحديث المتفائل في الكاش المحلي والمزامنة مع السيرفر
     */
    suspend fun saveItem(item: SavedItem) {
        require(item.userId.isNotBlank()) { "User ID must not be blank" }
        require(item.id.isNotBlank()) { "Item ID must not be blank" }

        val docRef = db.collection("users")
            .document(item.userId)
            .collection("saved_items")
            .document(item.id)

        try {
            val payload = hashMapOf<String, Any>(
                "id" to item.id,
                "userId" to item.userId,
                "itemType" to item.itemType,
                "title" to item.title,
                "content" to item.content,
                "reference" to item.reference,
                "tags" to item.tags,
                "createdAt" to (item.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    /**
     * حذف عنصر محفوظ
     */
    suspend fun deleteItem(userId: String, itemId: String) {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        require(itemId.isNotBlank()) { "Item ID must not be blank" }

        val docRef = db.collection("users")
            .document(userId)
            .collection("saved_items")
            .document(itemId)

        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    /**
     * تبديل حالة الحفظ (حفظ إذا لم يكن محفوظاً، وحذف إذا كان محفوظاً)
     */
    suspend fun toggleSaved(userId: String, item: SavedItem): Boolean {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        require(item.id.isNotBlank()) { "Item ID must not be blank" }

        val docRef = db.collection("users")
            .document(userId)
            .collection("saved_items")
            .document(item.id)

        val doc = try {
            docRef.get(Source.CACHE).await()
        } catch (_: Exception) {
            docRef.get().await()
        }

        return if (doc.exists()) {
            deleteItem(userId, item.id)
            false
        } else {
            saveItem(item.copy(userId = userId))
            true
        }
    }
}
