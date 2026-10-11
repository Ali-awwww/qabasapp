package com.qabas.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SavedItemsUiState {
    data object Loading : SavedItemsUiState
    data object Unauthenticated : SavedItemsUiState
    data class Success(
        val items: List<SavedItem>,
        val activeFilter: SavedItemType?,
        val userProfile: UserProfile?,
        val totalAyatCount: Int,
        val totalInspirationsCount: Int,
        val totalAdhkarCount: Int
    ) : SavedItemsUiState
    data class Error(val message: String) : SavedItemsUiState
}

class SavedItemsViewModel(
    private val repository: SavedItemsRepository,
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<SavedItemType?>(null)
    val selectedFilter: StateFlow<SavedItemType?> = _selectedFilter.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(authRepository.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    val uiState: StateFlow<SavedItemsUiState> = combine(
        authRepository.authStateFlow(),
        _selectedFilter
    ) { user, filter ->
        Pair(user, filter)
    }.combine(
        repository.observeSavedItems(authRepository.currentUser?.uid ?: "")
    ) { (user, filter), allItems ->
        if (user == null) {
            SavedItemsUiState.Unauthenticated
        } else {
            val filtered = if (filter == null) allItems else allItems.filter { it.type == filter }
            SavedItemsUiState.Success(
                items = filtered,
                activeFilter = filter,
                userProfile = UserProfile(
                    userId = user.uid,
                    displayName = user.displayName ?: "مستخدم قبس",
                    email = user.email ?: "",
                    photoUrl = user.photoUrl?.toString()
                ),
                totalAyatCount = allItems.count { it.type == SavedItemType.QURAN_AYAH },
                totalInspirationsCount = allItems.count { it.type == SavedItemType.INSPIRATION },
                totalAdhkarCount = allItems.count { it.type == SavedItemType.DHIKR }
            )
        }
    }.catch { error ->
        emit(SavedItemsUiState.Error(error.localizedMessage ?: "حدث خطأ أثناء تحميل البيانات"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = if (authRepository.currentUser == null) SavedItemsUiState.Unauthenticated else SavedItemsUiState.Loading
    )

    init {
        viewModelScope.launch {
            authRepository.authStateFlow().collect { user ->
                _currentUser.value = user
            }
        }
    }

    fun setFilter(type: SavedItemType?) {
        _selectedFilter.value = type
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(context)
            if (result.isSuccess) {
                _syncMessage.value = "تم تسجيل الدخول بنجاح ومزامنة البيانات سحابياً ✦"
            } else {
                val error = result.exceptionOrNull()
                _syncMessage.value = error?.localizedMessage ?: "تعذر إتمام تسجيل الدخول"
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _syncMessage.value = "تم تسجيل الخروج"
    }

    fun deleteItem(itemId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.deleteItem(uid, itemId)
                _syncMessage.value = "تم الحذف من السحابة والكاش المحلي"
            } catch (e: Exception) {
                _syncMessage.value = "تعذر الحذف: ${e.localizedMessage}"
            }
        }
    }

    fun saveAyah(surah: String, ayahNumber: Int, text: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val id = "ayah_${surah}_$ayahNumber"
        val item = SavedItem(
            id = id,
            userId = uid,
            itemType = SavedItemType.QURAN_AYAH.rawValue,
            title = "سورة $surah - آية $ayahNumber",
            content = text,
            reference = "$surah: $ayahNumber",
            tags = listOf("قرآن كريم", surah)
        )
        viewModelScope.launch {
            try {
                repository.saveItem(item)
                _syncMessage.value = "تم حفظ الآية في سحابة قبس ✦"
            } catch (e: Exception) {
                _syncMessage.value = "تعذر الحفظ: ${e.localizedMessage}"
            }
        }
    }

    fun saveInspiration(title: String, content: String, reference: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val id = "insp_${System.currentTimeMillis()}"
        val item = SavedItem(
            id = id,
            userId = uid,
            itemType = SavedItemType.INSPIRATION.rawValue,
            title = title,
            content = content,
            reference = reference,
            tags = listOf("إلهام", "خواطر")
        )
        viewModelScope.launch {
            try {
                repository.saveItem(item)
                _syncMessage.value = "تم حفظ الخاطرة في سحابة قبس ✦"
            } catch (e: Exception) {
                _syncMessage.value = "تعذر الحفظ: ${e.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _syncMessage.value = null
    }
}
