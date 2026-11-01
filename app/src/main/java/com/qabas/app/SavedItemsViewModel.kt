package com.qabas.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val repository: SavedItemsRepository
) : ViewModel() {
    private val authRepository = SupabaseRepository.getInstance()

    private val _selectedFilter = MutableStateFlow<SavedItemType?>(null)
    val selectedFilter: StateFlow<SavedItemType?> = _selectedFilter.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val uiState: StateFlow<SavedItemsUiState> = authRepository.authState
        .flatMapLatest { authState ->
            when (authState) {
                is AuthState.Loading -> flowOf(SavedItemsUiState.Loading)
                is AuthState.Error -> flowOf(SavedItemsUiState.Error(authState.message))
                is AuthState.Authenticated -> flow {
                    // Do not treat a local preference-only identity as a cloud owner.
                    val userId = SupabaseServices.Auth.awaitCurrentUserId()
                    if (userId.isNullOrBlank()) {
                        emit(SavedItemsUiState.Unauthenticated)
                    } else {
                        emitAll(
                            combine(
                                repository.observeSavedItems(userId),
                                _selectedFilter
                            ) { allItems, filter ->
                                val filtered = if (filter == null) allItems else allItems.filter { it.type == filter }
                                val row = authState.userRow
                                SavedItemsUiState.Success(
                                    items = filtered,
                                    activeFilter = filter,
                                    userProfile = UserProfile(
                                        userId = userId,
                                        displayName = row.name?.takeIf { it.isNotBlank() } ?: "مستخدم قبس",
                                        email = row.email.orEmpty(),
                                        photoUrl = SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
                                    ),
                                    totalAyatCount = allItems.count { it.type == SavedItemType.QURAN_AYAH },
                                    totalInspirationsCount = allItems.count { it.type == SavedItemType.INSPIRATION },
                                    totalAdhkarCount = allItems.count { it.type == SavedItemType.DHIKR }
                                )
                            }
                        )
                    }
                }
                else -> flowOf(SavedItemsUiState.Unauthenticated)
            }
        }
        .catch { error ->
            emit(SavedItemsUiState.Error(error.localizedMessage ?: "حدث خطأ أثناء تحميل البيانات"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SavedItemsUiState.Loading
        )

    fun setFilter(type: SavedItemType?) {
        _selectedFilter.value = type
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val googleResult = GoogleAuthHelper.signInWithGoogle(context)
            val googleCredentials = googleResult.getOrElse {
                _syncMessage.value = it.localizedMessage ?: "تعذر إتمام تسجيل الدخول"
                return@launch
            }
            val loginResult = authRepository.loginWithGoogle(googleCredentials.idToken)
            _syncMessage.value = if (loginResult.isSuccess) {
                "تم تسجيل الدخول بنجاح ومزامنة البيانات سحابياً ✦"
            } else {
                loginResult.exceptionOrNull()?.localizedMessage ?: "تعذر إتمام تسجيل الدخول"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val result = authRepository.logout()
            _syncMessage.value = if (result.isSuccess) "تم تسجيل الخروج" else "تعذر تسجيل الخروج"
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            val uid = SupabaseServices.Auth.awaitCurrentUserId() ?: return@launch
            try {
                repository.deleteItem(uid, itemId)
                _syncMessage.value = "تم الحذف من سحابة قبس"
            } catch (e: Exception) {
                _syncMessage.value = "تعذر الحذف: ${e.localizedMessage}"
            }
        }
    }

    fun saveAyah(surah: String, ayahNumber: Int, text: String) {
        viewModelScope.launch {
            val uid = SupabaseServices.Auth.awaitCurrentUserId() ?: return@launch
            val item = SavedItem(
                id = "ayah_${surah}_$ayahNumber",
                userId = uid,
                itemType = SavedItemType.QURAN_AYAH.rawValue,
                title = "سورة $surah - آية $ayahNumber",
                content = text,
                reference = "$surah: $ayahNumber",
                tags = listOf("قرآن كريم", surah)
            )
            try {
                repository.saveItem(item)
                _syncMessage.value = "تم حفظ الآية في سحابة قبس ✦"
            } catch (e: Exception) {
                _syncMessage.value = "تعذر الحفظ: ${e.localizedMessage}"
            }
        }
    }

    fun saveInspiration(title: String, content: String, reference: String) {
        viewModelScope.launch {
            val uid = SupabaseServices.Auth.awaitCurrentUserId() ?: return@launch
            val item = SavedItem(
                id = "insp_${System.currentTimeMillis()}",
                userId = uid,
                itemType = SavedItemType.INSPIRATION.rawValue,
                title = title,
                content = content,
                reference = reference,
                tags = listOf("إلهام", "خواطر")
            )
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
