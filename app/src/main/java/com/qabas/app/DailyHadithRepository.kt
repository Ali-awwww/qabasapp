package com.qabas.app

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

@Serializable
data class DailyHadith(
    val dayId: Int, // 1-365
    val text: String,
    val narrator: String,
    val benefit: String,
    val book: String // Sahih Bukhari or Sahih Muslim
)

object DailyHadithRepository {
    private const val TAG = "DailyHadithRepo"
    private const val COLLECTION_NAME = "daily_hadiths"

    suspend fun getHadithForDay(context: Context, dayId: Int): DailyHadith? {
        return try {
            val db = FirebaseManager.getFirestore(context)
            val doc = db.collection(COLLECTION_NAME).document("day_$dayId").get().await()
            if (doc.exists()) {
                DailyHadith(
                    dayId = doc.getLong("dayId")?.toInt() ?: dayId,
                    text = doc.getString("text") ?: "",
                    narrator = doc.getString("narrator") ?: "",
                    benefit = doc.getString("benefit") ?: "",
                    book = doc.getString("book") ?: ""
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch daily hadith for day $dayId", e)
            null
        }
    }

    suspend fun uploadHadiths(context: Context, hadiths: List<DailyHadith>): Boolean {
        return try {
            val db = FirebaseManager.getFirestore(context)
            val batch = db.batch()
            hadiths.forEach { hadith ->
                val docRef = db.collection(COLLECTION_NAME).document("day_${hadith.dayId}")
                batch.set(docRef, hadith)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload batch of hadiths", e)
            false
        }
    }
}
