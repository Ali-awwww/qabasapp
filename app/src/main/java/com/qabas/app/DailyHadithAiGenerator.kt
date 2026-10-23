package com.qabas.app

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

object DailyHadithAiGenerator {
    private const val TAG = "DailyHadithAiGen"

    private val hadithSchema = Schema.obj(
        properties = mapOf(
            "hadiths" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "dayId" to Schema.integer(description = "Day of the year (1-365)"),
                        "text" to Schema.string(description = "Full Arabic text of the Hadith from Sahih Bukhari or Muslim"),
                        "narrator" to Schema.string(description = "The narrator of the Hadith (e.g. عن أبي هريرة رضي الله عنه)"),
                        "benefit" to Schema.string(description = "A very short practical benefit or wisdom in Arabic"),
                        "book" to Schema.string(description = "The source book (صحيح البخاري or صحيح مسلم)")
                    )
                )
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = hadithSchema
            }
        )
    }

    suspend fun generateBatch(startDay: Int, count: Int): List<DailyHadith> {
        val prompt = """
            Generate exactly $count daily Hadiths from Sahih al-Bukhari and Sahih al-Muslim.
            Start from Day ID: $startDay.
            The Hadiths should be short, meaningful, and suitable for a daily notification.
            Provide the full Arabic text, narrator, a short benefit, and the source book.
            Ensure each Hadith has a unique dayId starting from $startDay up to ${startDay + count - 1}.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("Model returned empty response")
            val json = JSONObject(jsonString)
            val hadithsArray = json.getJSONArray("hadiths")
            
            val result = mutableListOf<DailyHadith>()
            for (i in 0 until hadithsArray.length()) {
                val item = hadithsArray.getJSONObject(i)
                result.add(
                    DailyHadith(
                        dayId = item.getInt("dayId"),
                        text = item.getString("text"),
                        narrator = item.getString("narrator"),
                        benefit = item.getString("benefit"),
                        book = item.getString("book")
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Batch generation failed for days $startDay to ${startDay + count - 1}", e)
            emptyList()
        }
    }
}
