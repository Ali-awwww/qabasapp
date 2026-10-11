package com.qabas.app

import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * نوع العنصر المحفوظ في سحابة قبس (Firestore)
 */
enum class SavedItemType(val rawValue: String, val labelAr: String) {
    QURAN_AYAH("QURAN_AYAH", "آية قرآنية"),
    INSPIRATION("INSPIRATION", "إلهام وخواطر"),
    DHIKR("DHIKR", "ذكر ودعاء");

    companion object {
        fun fromRaw(raw: String): SavedItemType {
            return entries.find { it.rawValue.equals(raw, ignoreCase = true) } ?: QURAN_AYAH
        }
    }
}

/**
 * نموذج العنصر المحفوظ مع دعم كامل للمزامنة اللحظية والتخزين المحلي دون اتصال (Offline Persistence)
 */
@Immutable
@IgnoreExtraProperties
data class SavedItem(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val itemType: String = SavedItemType.QURAN_AYAH.rawValue,
    val title: String = "",
    val content: String = "",
    val reference: String = "",
    val tags: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    val type: SavedItemType
        get() = SavedItemType.fromRaw(itemType)

    val isQuran: Boolean
        get() = type == SavedItemType.QURAN_AYAH
}

/**
 * ملف تعريف المستخدم السحابي في Firestore
 */
@Immutable
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val fcmToken: String? = null,
    val has_reward: Boolean = false,
    val total_wins: Int = 0,
    val is_relative: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
