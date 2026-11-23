package com.qabas.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

/**
 * التفاعلات الروحية الحية ونثر الحسنات (Spiritual Quick Reactions & Hasanat Burst)
 */
data class SpiritualReaction(
    val id: String,
    val text: String,
    val icon: String,
)

val SPIRITUAL_REACTIONS = listOf(
    SpiritualReaction("subhanallah", "سبحان الله", "✦"),
    SpiritualReaction("mashallah", "ما شاء الله", "✧"),
    SpiritualReaction("alhamdulillah", "الحمد لله", "❖"),
    SpiritualReaction("jazakallah", "أجر لي ولك", "♡"),
    SpiritualReaction("ameen", "آمين يا رب", "◈")
)
