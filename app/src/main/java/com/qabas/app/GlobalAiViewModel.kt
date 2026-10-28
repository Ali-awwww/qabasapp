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
    private val _activeModelName = MutableStateFlow(AiModelRegistry.getActiveModel(context).name)
    val activeModelName: StateFlow<String> = _activeModelName.asStateFlow()

    init {
        viewModelScope.launch {
            AiModelRegistry.activeModelIdFlow.collect {
                refreshActiveModel()
            }
        }
    }

    fun refreshActiveModel() {
        _activeModelName.value = AiModelRegistry.getActiveModel(context).name
    }

    /**
     * Update active model in registry and refresh local state
     */
    fun setActiveModel(modelId: String) {
        AiModelRegistry.setActiveModel(context, modelId)
        refreshActiveModel()
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
