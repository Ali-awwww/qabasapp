package com.qabas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TasteManager {
    private var lastToastTime = 0L

    suspend fun getTasteContext(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val tastes = db.tasteDao().getAllTastes()
            if (tastes.isEmpty()) return@withContext ""

            val builder = StringBuilder("ملاحظات المخرج (الذوق المتطور للمستخدم بناءً على المشاريع السابقة):\n")
            val grouped = tastes.groupBy { it.category }
            
            for ((category, list) in grouped) {
                val topPreference = list.maxByOrNull { it.weight }
                if (topPreference != null) {
                    val translatedCategory = when(category) {
                        "STYLE" -> "الأسلوب البصري"
                        "TONE" -> "النبرة"
                        "PACING" -> "الإيقاع"
                        "TOPIC" -> "الموضوع المفضل"
                        "AUDIO" -> "الخلفية الصوتية"
                        else -> category
                    }
                    builder.append("- $translatedCategory: ${topPreference.preferredValue}\n")
                }
            }
            
            builder.append("استخدم هذا الذوق لتخصيص النتيجة وجعلها متوافقة مع أسلوب المستخدم الذي يفضله، فالتطبيق يتطور مع ذوق المستخدم.\n\n")
            builder.toString()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun registerPreference(context: Context, category: String, value: String, weight: Int = 1) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val repo = TasteRepository(db.tasteDao())
            repo.registerPreference(category, value, weight)

            val now = System.currentTimeMillis()
            if (now - lastToastTime > 5000) { // Debounce for 5 seconds
                lastToastTime = now
                withContext(Dispatchers.Main) {
                    val translatedCategory = when(category) {
                        "STYLE" -> "الأسلوب البصري"
                        "TONE" -> "النبرة"
                        "PACING" -> "الإيقاع"
                        "TOPIC" -> "الموضوعات"
                        "AUDIO" -> "الخلفيات الصوتية"
                        else -> category
                    }
                    Toast.makeText(context, "الذكاء الاصطناعي يتعلم ذوقك في ($translatedCategory) 🧠✨", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
