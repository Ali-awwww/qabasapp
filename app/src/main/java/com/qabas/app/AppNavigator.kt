package com.qabas.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /**
     * وجهة تنقّل مطلوبة من إشعار (navigate_to / target_screen) — StateFlow
     * يبقى محفوظاً حتى استهلاكه، فيعمل مع الإقلاع البارد (قبل تركيب الواجهة)
     * ومع الاستئناف الدافئ على حد سواء.
     */
    private val _notificationDestination = kotlinx.coroutines.flow.MutableStateFlow<AppState?>(null)
    val notificationDestination: kotlinx.coroutines.flow.StateFlow<AppState?> =
        _notificationDestination.asStateFlow()

    /** طلب تنقّل من إشعار — يُستهلك مرة واحدة في QabasApp بعد جاهزية التطبيق */
    fun navigateFromNotification(appState: AppState) {
        _notificationDestination.value = appState
    }

    fun consumeNotificationDestination() {
        _notificationDestination.value = null
    }

    fun navigateToInputWithText(text: String) {
        _pendingInputText.tryEmit(text)
        _targetAppState.tryEmit(AppState.INPUT)
    }

    private val _pendingChatInfo = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)
    val pendingChatInfo: SharedFlow<Pair<String, String>> = _pendingChatInfo.asSharedFlow()

    /** طلب فتح محادثة دعم فني لبلاغ محدد */
    fun navigateToChat(userEmail: String, requestId: String) {
        _pendingChatInfo.tryEmit(userEmail to requestId)
        _targetAppState.tryEmit(AppState.REQUEST_CHAT)
    }
}
