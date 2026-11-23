package com.qabas.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlobalAiViewModel(private val context: Context) : ViewModel() {
    private val _activeModelName = MutableStateFlow(resolveActiveModelName())
    val activeModelName: StateFlow<String> = _activeModelName.asStateFlow()

    init {
        viewModelScope.launch {
            UniversalKeyBrain.keyUpdateTrigger.collect {
                refreshActiveModel()
            }
        }
    }

    fun refreshActiveModel() {
        _activeModelName.value = resolveActiveModelName()
    }

    /**
     * اسم النموذج الفعلي لمسار التوليد الافتراضي — لا من سجل ميت:
     * كان الاسم يأتي من AiModelRegistry («OpenRouter Auto Router») وهو سجل
     * بلا أي كاتب (setActiveModel بلا مستدعٍ)، فعرض المؤشر اسماً لا ينفذه
     * أي مسار. الآن يُشتق من المحرك النشط حياً (UniversalKeyBrain):
     * - Gemini أو بلا مفتاح: Firebase AI Logic أولاً في AgentBrain،
     *   ونموذجه gemini-3.8-flash (FirebaseAiService).
     * - OpenRouter / Groq: النموذج الافتراضي للمزود (DetectedProvider).
     */
    private fun resolveActiveModelName(): String {
        val (provider, _) = UniversalKeyBrain.getActiveEngine()
        return when (provider) {
            DetectedProvider.OPENROUTER -> DetectedProvider.OPENROUTER.defaultModel
            DetectedProvider.GROQ -> DetectedProvider.GROQ.defaultModel
            DetectedProvider.GEMINI, DetectedProvider.NONE -> "gemini-3.8-flash"
        }
    }
}

class GlobalAiViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GlobalAiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GlobalAiViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
