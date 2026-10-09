package com.qabas.app

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reactive Authentication State representing the user's current session status.
 */
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(
        val userRow: SupabaseServices.UserRow,
        val session: UserSession? = null,
        val isAdmin: Boolean = false
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Repository class that handles reactive authentication flows, Postgrest database operations,
 * and Supabase Storage operations.
 *
 * Broadcasts authentication state changes via [authState], [isAuthenticated], and [currentUser],
 * enabling UI components and navigation controllers to automatically switch between auth screens
 * (login/register) and the main dashboard.
 */
class SupabaseRepository(
    private val client: SupabaseClient = SupabaseConfig.client,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    companion object {
        private const val TAG = "SupabaseRepository"

        @Volatile
        private var INSTANCE: SupabaseRepository? = null

        fun getInstance(): SupabaseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseRepository().also { INSTANCE = it }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    /** Reactive authentication state stream broadcasted to the entire app. */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Derived reactive stream indicating whether a valid user session exists. */
    val isAuthenticated: StateFlow<Boolean> = _authState.map {
        it is AuthState.Authenticated
    }.stateIn(repositoryScope, SharingStarted.Eagerly, false)

    /** Derived reactive stream for the current authenticated user's profile. */
    val currentUser: StateFlow<SupabaseServices.UserRow?> = _authState.map {
        (it as? AuthState.Authenticated)?.userRow
    }.stateIn(repositoryScope, SharingStarted.Eagerly, null)

    /** Checks whether Supabase credentials are configured and active. */
    val isAvailable: Boolean
        get() = SupabaseConfig.isConfigured

    init {
        observeSupabaseSessionStatus()
    }

    /**
     * Explicitly sets the reactive auth state to Authenticated for local, guest, or fallback sessions.
     */
    fun emitLocalAuthenticated(email: String, name: String? = null, isAdmin: Boolean = false) {
        val uid = getCurrentUserId() ?: email.lowercase(java.util.Locale.ROOT)
        _authState.value = AuthState.Authenticated(
            userRow = SupabaseServices.UserRow(
                id = uid,
                externalId = uid,
                email = email,
                name = name ?: email.substringBefore("@")
            ),
            session = getCurrentSession(),
            isAdmin = isAdmin
        )
    }

    private fun checkLocalAuthFallback() {
        val context = AppServices.appContext
        val prefs = runCatching { context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE) }.getOrNull()
        val isLoggedInLocal = prefs?.getBoolean("is_logged_in", false) ?: false
        if (isLoggedInLocal) {
            val email = prefs?.getString("user_email", "") ?: "user@qabas.app"
            val name = prefs?.getString("user_name", "") ?: email.substringBefore("@")
            val isAdmin = prefs?.getBoolean("is_admin", false) ?: false
            emitLocalAuthenticated(email, name, isAdmin)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Subscribes reactively to Supabase Auth's native sessionStatus Flow to automatically
     * update [authState] whenever session changes (e.g. login, token refresh, logout, or session restore).
     */
    private fun observeSupabaseSessionStatus() {
        if (!isAvailable) {
            checkLocalAuthFallback()
            return
        }

        repositoryScope.launch {
            runCatching {
                client.auth.sessionStatus.collect { status ->
                    Log.d(TAG, "Supabase SessionStatus changed: $status")
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val session = status.session
                            val authUser = session.user
                            val uid = authUser?.id ?: client.auth.currentUserOrNull()?.id
                            if (uid != null) {
                                refreshUserAndEmitAuthenticated(uid, session)
                            } else {
                                checkLocalAuthFallback()
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            checkLocalAuthFallback()
                        }
                        is SessionStatus.Initializing -> {
                            _authState.value = AuthState.Loading
                        }
                        else -> {
                            val existingSession = client.auth.currentSessionOrNull()
                            val uid = existingSession?.user?.id ?: client.auth.currentUserOrNull()?.id
                            if (uid != null) {
                                refreshUserAndEmitAuthenticated(uid, existingSession)
                            } else {
                                checkLocalAuthFallback()
                            }
                        }
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to observe session status: ${e.message}")
                checkLocalAuthFallback()
            }
        }
    }

    /**
     * Refreshes user profile and admin status, then broadcasts [AuthState.Authenticated].
     */
    private suspend fun refreshUserAndEmitAuthenticated(uid: String, session: UserSession?) {
        withContext(Dispatchers.IO) {
            runCatching {
                val email = session?.user?.email ?: client.auth.currentUserOrNull()?.email
                val name = email?.substringBefore("@")
                val userRow = SupabaseServices.Database.ensureUser(uid, email, name) ?: SupabaseServices.UserRow(
                    id = uid,
                    externalId = uid,
                    email = email,
                    name = name
                )
                val role = SupabaseServices.Auth.getUserRoleFromMetadata()
                val isAdmin = role == "admin" || role == "developer" || role == "owner" || CloudServices.isOwnerAccount(email)

                _authState.value = AuthState.Authenticated(
                    userRow = userRow,
                    session = session,
                    isAdmin = isAdmin
                )
            }.onFailure { e ->
                Log.w(TAG, "Failed to load user row for $uid: ${e.message}")
                _authState.value = AuthState.Authenticated(
                    userRow = SupabaseServices.UserRow(id = uid, externalId = uid),
                    session = session,
                    isAdmin = false
                )
            }
        }
    }

    /**
     * Manually triggers an explicit check of the current authentication state.
     */
    suspend fun checkAuthState(): AuthState = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            checkLocalAuthFallback()
            return@withContext _authState.value
        }
        val currentSession = client.auth.currentSessionOrNull()
        val uid = currentSession?.user?.id ?: client.auth.currentUserOrNull()?.id
        if (uid != null) {
            refreshUserAndEmitAuthenticated(uid, currentSession)
        } else {
            checkLocalAuthFallback()
        }
        _authState.value
    }

    // ==========================================
    // Reactive Auth API Actions
    // ==========================================

    /** Returns current authenticated user ID or null. */
    fun getCurrentUserId(): String? {
        if (!isAvailable) return null
        return runCatching { client.auth.currentUserOrNull()?.id }.getOrNull()
    }

    /** Returns current user session if active. */
    fun getCurrentSession(): UserSession? {
        if (!isAvailable) return null
        return runCatching { client.auth.currentSessionOrNull() }.getOrNull()
    }

    /** Sign up with email and password and broadcast updated auth state. */
    suspend fun register(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        _authState.value = AuthState.Loading
        runCatching {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }
            checkAuthState()
            true
        }.onFailure { e ->
            _authState.value = AuthState.Error(e.message ?: "Registration failed")
        }
    }

    /**
     * Diagnostic sign-in function that prints detailed log responses from Supabase
     * and evaluates if a valid session object was returned.
     */
    suspend fun diagnoseSignInAttempt(email: String, pass: String): SupabaseServices.AuthDiagnosticsResult = withContext(Dispatchers.IO) {
        val result = SupabaseServices.Auth.diagnoseSignInAttempt(email, pass)
        Log.d(TAG, "[SupabaseRepository] Sign-in Diagnostic Output -> isSuccess: ${result.isSuccess}, hasSession: ${result.hasSession}, userId: ${result.userId}, rawError: ${result.rawErrorMessage}")
        if (result.isSuccess && result.userId != null) {
            refreshUserAndEmitAuthenticated(result.userId, getCurrentSession())
        }
        result
    }

    /** Sign in with email and password and broadcast updated auth state. */
    suspend fun login(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        _authState.value = AuthState.Loading
        runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            checkAuthState()
            true
        }.onFailure { e ->
            _authState.value = AuthState.Error(e.message ?: "Login failed")
        }
    }

    /** Sign in with Google ID token and broadcast updated auth state. */
    suspend fun loginWithGoogle(idToken: String, nonce: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        _authState.value = AuthState.Loading
        runCatching {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
                if (nonce != null) {
                    this.nonce = nonce
                }
            }
            checkAuthState()
            true
        }.onFailure { e ->
            _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
        }
    }

    /** Reset user password via email. */
    suspend fun resetPassword(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            client.auth.resetPasswordForEmail(email)
            true
        }
    }

    /** Update current user password. */
    suspend fun updatePassword(newPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            client.auth.updateUser {
                password = newPassword
            }
            true
        }
    }

    /** Sign out current user and broadcast Unauthenticated state to toggle UI back to login screen. */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            _authState.value = AuthState.Unauthenticated
            return@withContext Result.success(Unit)
        }
        runCatching {
            client.auth.signOut()
            _authState.value = AuthState.Unauthenticated
            Unit
        }.onFailure { e ->
            _authState.value = AuthState.Unauthenticated
        }
    }

    /** Fetch user role from auth app_metadata. */
    suspend fun getUserRoleFromMetadata(): String = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext "free"
        runCatching {
            val user = client.auth.retrieveUserForCurrentSession()
            val role = user.appMetadata?.get("role")?.let {
                if (it is JsonPrimitive) it.content else it.toString().removeSurrounding("\"")
            } ?: "free"
            role
        }.getOrDefault("free")
    }

    // ==========================================
    // Database Operations (Postgrest)
    // ==========================================

    /**
     * Syncs local user profile settings (like display name, email, avatar URL, or preferences)
     * with the 'profiles' table in Supabase via upsert.
     */
    suspend fun syncUserProfile(
        userId: String? = getCurrentUserId(),
        displayName: String?,
        email: String? = null,
        avatarUrl: String? = null,
        preferencesJson: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        val targetUserId = userId ?: getCurrentUserId() ?: return@withContext Result.failure(IllegalArgumentException("User ID is required"))
        runCatching {
            SupabaseServices.Profiles.syncUserProfile(
                userId = targetUserId,
                displayName = displayName,
                email = email,
                avatarUrl = avatarUrl,
                preferencesJson = preferencesJson
            )
        }
    }

    /**
     * Retrieves user profile settings from the 'profiles' table in Supabase.
     */
    suspend fun getUserProfile(userId: String? = getCurrentUserId()): Result<SupabaseServices.ProfileRow?> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        val targetUserId = userId ?: getCurrentUserId() ?: return@withContext Result.failure(IllegalArgumentException("User ID is required"))
        runCatching {
            SupabaseServices.Profiles.getUserProfile(targetUserId)
        }
    }

    suspend fun ensureUser(externalId: String, email: String?, name: String?): Result<SupabaseServices.UserRow?> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.ensureUser(externalId, email, name)
        }
    }

    suspend fun getAllUsers(): Result<List<SupabaseServices.UserRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getAllUsers()
        }
    }

    suspend fun updateUserRole(userId: String, newType: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.updateUserRole(userId, newType)
        }
    }

    suspend fun saveUserSettings(userId: String, jsonPayload: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.UserSettings.saveUserSettings(userId, jsonPayload)
        }
    }

    suspend fun getUserSettings(userId: String): Result<SupabaseServices.UserSettingsRow?> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.UserSettings.getUserSettings(userId)
        }
    }

    suspend fun saveProject(
        title: String,
        description: String?,
        status: String = "draft",
        cost: Double = 0.0,
        tags: List<String> = emptyList(),
        metadata: Map<String, String> = emptyMap()
    ): Result<SupabaseServices.ProjectRow?> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.saveProject(title, description, status, cost, tags, metadata)
        }
    }

    suspend fun getUserProjects(userId: String? = null): Result<List<SupabaseServices.ProjectRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getUserProjects(userId)
        }
    }

    suspend fun deleteProject(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.deleteProject(id)
        }
    }

    suspend fun getHadithPresets(): Result<List<SupabaseServices.HadithRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getHadithPresets()
        }
    }

    suspend fun getAudioTracks(): Result<List<SupabaseServices.AudioTrackRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getAudioTracks()
        }
    }

    suspend fun addAudioTrack(track: SupabaseServices.AudioTrackRow): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.addAudioTrack(track)
        }
    }

    suspend fun deleteAudioTrack(trackId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.deleteAudioTrack(trackId)
        }
    }

    suspend fun getScholarLectures(scholarId: String): Result<List<SupabaseServices.ScholarLectureRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getScholarLectures(scholarId)
        }
    }

    suspend fun saveScholarLecture(row: SupabaseServices.ScholarLectureRow): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.saveScholarLecture(row)
        }
    }

    suspend fun getScholarProposals(scholarId: String): Result<List<SupabaseServices.ScholarProposalRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getScholarProposals(scholarId)
        }
    }

    suspend fun upvoteScholarProposal(id: String, newCount: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.upvoteScholarProposal(id, newCount)
        }
    }

    suspend fun getLeaderboard(limit: Long = 50L): Result<List<SupabaseServices.LeaderboardRow>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            SupabaseServices.Database.getLeaderboard(limit)
        }
    }

    suspend fun upsertLeaderboardScore(
        userId: String,
        name: String,
        points: Long,
        isEligible: Boolean = true,
        avatarUrl: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Database.upsertLeaderboardScore(userId, name, points, isEligible, avatarUrl)
        }
    }

    suspend fun getAppReviews(): Result<List<SupabaseServices.AppReviewDto>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        SupabaseServices.AppReviews.getAppReviews()
    }

    suspend fun submitAppReview(
        rating: Int,
        comment: String,
        authorName: String,
        category: String = "overall"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        SupabaseServices.AppReviews.submitAppReview(rating, comment, authorName, category)
    }

    suspend fun submitFeedback(
        type: String,
        message: String,
        userName: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        SupabaseServices.Feedback.submitFeedback(type, message, userName)
    }

    // ==========================================
    // Storage Operations
    // ==========================================

    suspend fun uploadFile(
        bucketName: String = "media-assets",
        path: String,
        data: ByteArray,
        upsert: Boolean = true
    ): Result<String?> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Storage.uploadFile(bucketName, path, data, upsert)
        }
    }

    fun getPublicUrl(bucketName: String, path: String): String? {
        if (!isAvailable) return null
        return SupabaseServices.Storage.getPublicUrl(bucketName, path)
    }

    suspend fun deleteFiles(
        bucketName: String,
        paths: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        runCatching {
            SupabaseServices.Storage.deleteFiles(bucketName, paths)
        }
    }
}

/**
 * Composable extension that observes [SupabaseRepository.authState] reactively
 * and triggers navigation callbacks to switch automatically between Auth (Login/Register) screens
 * and the Main Dashboard.
 */
@Composable
fun ReactiveAuthNavigationObserver(
    repository: SupabaseRepository = SupabaseRepository.getInstance(),
    onAuthenticated: (isAdmin: Boolean) -> Unit,
    onUnauthenticated: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authState by repository.authState.collectAsState()

    LaunchedEffect(authState) {
        val prefs = runCatching { context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE) }.getOrNull()
        val isLoggedInLocal = prefs?.getBoolean("is_logged_in", false) ?: false
        val isAdminLocal = prefs?.getBoolean("is_admin", false) ?: false

        when (val state = authState) {
            is AuthState.Authenticated -> {
                onAuthenticated(state.isAdmin)
            }
            is AuthState.Unauthenticated -> {
                if (isLoggedInLocal) {
                    onAuthenticated(isAdminLocal)
                } else {
                    onUnauthenticated()
                }
            }
            else -> {
                if (isLoggedInLocal) {
                    onAuthenticated(isAdminLocal)
                }
            }
        }
    }
}
