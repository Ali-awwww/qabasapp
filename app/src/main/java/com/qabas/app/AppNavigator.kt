package com.qabas.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 🧭 ملاح التطبيق الفضائي العام (Global Spatial App Navigator)
 * يتيح لجميع المكونات والواجهات العائمة (مثل عدسة الكاميرا الفضائية) طلب التنقل الفوري
 * بين حالات وشاشات التطبيق بسلاسة تامة.
 */
object AppNavigator {
    private val _targetAppState = MutableSharedFlow<AppState>(extraBufferCapacity = 8)
    val targetAppState: SharedFlow<AppState> = _targetAppState.asSharedFlow()

    private val _pendingInputText = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val pendingInputText: SharedFlow<String> = _pendingInputText.asSharedFlow()

    fun navigateTo(appState: AppState) {
        _targetAppState.tryEmit(appState)
    }

    fun navigateToInputWithText(text: String) {
        _pendingInputText.tryEmit(text)
        _targetAppState.tryEmit(AppState.INPUT)
    }
}
