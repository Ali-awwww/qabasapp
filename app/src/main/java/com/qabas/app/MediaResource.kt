package com.qabas.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * مورد وسائط حقيقي يمكن رفعه من الجهاز أو اختياره من مكتبة B-Roll.
 * يُستخدم في شاشة «جمع الموارد» ويمرّر إلى مسار الإنتاج (Processing).
 */
data class MediaResource(
    val id: String,
    val type: String,          // "video" | "image" | "audio" | "document" | "broll"
    val name: String,
    val icon: ImageVector = Icons.Default.Movie,
    /** content:// أو file:// أو https:// */
    val uri: String? = null,
    val durationLabel: String? = null,
    val sizeLabel: String? = null,
    val isBRoll: Boolean = false,
    val category: String? = null
) {
    companion object {
        fun iconForType(type: String): ImageVector = when (type.lowercase()) {
            "video", "broll" -> Icons.Default.Movie
            "image" -> Icons.Default.Image
            "audio" -> Icons.Default.Audiotrack
            "document" -> Icons.Default.Description
            else -> Icons.Default.Movie
        }
    }
}
