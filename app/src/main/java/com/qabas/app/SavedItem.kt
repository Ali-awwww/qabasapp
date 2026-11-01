package com.qabas.app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SavedItemType(val rawValue: String, val labelAr: String) {
    QURAN_AYAH("QURAN_AYAH", "آية قرآنية"),
    INSPIRATION("INSPIRATION", "إلهام وخواطر"),
    DHIKR("DHIKR", "ذكر ودعاء");

    companion object {
        fun fromRaw(raw: String): SavedItemType =
            entries.find { it.rawValue.equals(raw, ignoreCase = true) } ?: QURAN_AYAH
    }
}

@Immutable
@Serializable
data class SavedItem(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("item_type") val itemType: String = SavedItemType.QURAN_AYAH.rawValue,
    val title: String = "",
    val content: String = "",
    val reference: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val type: SavedItemType
        get() = SavedItemType.fromRaw(itemType)

    val isQuran: Boolean
        get() = type == SavedItemType.QURAN_AYAH
}

/** UI-only projection of a Supabase-authenticated user for the saved-items header. */
@Immutable
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null
)
