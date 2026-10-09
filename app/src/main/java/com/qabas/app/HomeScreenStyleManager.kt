package com.qabas.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeScreenStyle(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val iconEmoji: String
) {
    SPATIAL_CYBER_HUD(
        id = "spatial_cyber_hud",
        title = "مركز القيادة الفضائي (Spatial HUD Tech)",
        subtitle = "واجهة فضائية هولوغرافية كوانتية مع رادار الأداء، منصة الإطلاق السريعة، وشبكة قطاعات تفاعلية",
        badge = "النمط الفضائي المطور 🚀",
        iconEmoji = "🛸"
    ),
    CLASSIC_STUDIO(
        id = "classic_studio",
        title = "النمط الكلاسيكي للاستوديو (Classic Studio)",
        subtitle = "التصميم الكلاسيكي الأصلي مع الأقسام المتتابعة والمحاور الأفقية",
        badge = "النمط الكلاسيكي 🏛️",
        iconEmoji = "🎬"
    );

    companion object {
        fun fromId(id: String?): HomeScreenStyle =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: SPATIAL_CYBER_HUD
    }
}

object HomeScreenStyleManager {
    private const val PREF_KEY = "home_screen_style_mode"
    private val _currentStyle = MutableStateFlow(HomeScreenStyle.SPATIAL_CYBER_HUD)
    val currentStyle: StateFlow<HomeScreenStyle> = _currentStyle.asStateFlow()

    var activeStyle by mutableStateOf(HomeScreenStyle.SPATIAL_CYBER_HUD)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString(PREF_KEY, HomeScreenStyle.SPATIAL_CYBER_HUD.id)
        val style = HomeScreenStyle.fromId(savedId)
        _currentStyle.value = style
        activeStyle = style
    }

    fun setStyle(context: Context, style: HomeScreenStyle) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY, style.id).apply()
        _currentStyle.value = style
        activeStyle = style
    }
}
