@file:OptIn(kotlin.time.ExperimentalTime::class)

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
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * Reactive Authentication State representing the user's current session status.
 */
@Serializable
private data class IdentifierLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("token_type") val tokenType: String = "bearer"
)

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Guest(val displayName: String = "ضيف قبس") : AuthState()
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
     * Explicitly sets the reactive auth state to Guest Mode (unauthenticated browsing session).
     */
    fun emitGuestMode(displayName: String = "ضيف قبس") {
        _authState.value = AuthState.Guest(displayName)
    }

    private fun checkLocalAuthFallback() {
        val context = AppServices.appContext
        val prefs = runCatching {
            context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE)
        }.getOrNull()
        val isGuest = prefs?.getBoolean("is_guest", false) ?: false
        if (isGuest) {
            val name = prefs?.getString("user_name", "ضيف قبس") ?: "ضيف قبس"
            emitGuestMode(name)
        } else {
            // Local flags and cached email are presentation data, never proof of authentication.
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
    private suspend fun refreshUserAndEmitAuthenticated(
        uid: String,
        session: UserSession?,
        requestedCustomId: String? = null,
        requestedDisplayName: String? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val email = session?.user?.email ?: client.auth.currentUserOrNull()?.email
                val name = requestedDisplayName?.trim()?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@")
                val userRow = SupabaseServices.Database.ensureUser(uid, email, name, requestedCustomId)
                    ?: throw IllegalStateException("تعذر إنشاء أو استعادة ملف المستخدم السحابي")
                if (userRow.id != uid ||
                    userRow.customId.isNullOrBlank() ||
                    userRow.accountNumber.isNullOrBlank()
                ) {
                    throw IllegalStateException("ملف المستخدم السحابي لا يحتوي هوية مكتملة")
                }

                val role = SupabaseServices.Auth.getUserRoleFromMetadata()
                val authEmail = SupabaseServices.Auth.getCurrentUserEmail() ?: email
                val isAdmin = SupabaseServices.Auth.isAdminRole(role) ||
                    CloudServices.isOwnerAccount(authEmail)
                _authState.value = AuthState.Authenticated(
                    userRow = userRow,
                    session = session,
                    isAdmin = isAdmin
                )

                // Local preferences are only a cache of values that the cloud
                // profile has already issued. Restore them after every session load
                // so a second device never invents a different handle or number.
                val appContext = AppServices.appContext
                val profileEmail = userRow.email ?: email
                if (!profileEmail.isNullOrBlank()) {
                    userRow.customId?.takeIf { it.isNotBlank() }?.let {
                        UserIdentifierManager.saveCustomId(appContext, it, profileEmail)
                    }
                    userRow.accountNumber?.takeIf { it.isNotBlank() }?.let {
                        UserIdentifierManager.saveAccountNumber(appContext, profileEmail, it)
                    }
                    appContext.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("user_id", uid)
                        .putString("user_email", profileEmail)
                        .putString("user_name", userRow.name ?: profileEmail.substringBefore("@"))
                        .putString("user_display_name", userRow.name ?: profileEmail.substringBefore("@"))
                        .putString("user_custom_id", userRow.customId)
                        .putString("user_tag", userRow.customId?.let { UserIdentifierManager.formatDisplay(it) })
                        .putString("user_account_number", userRow.accountNumber)
                        .apply()
                }
                true
            }.getOrElse { e ->
                Log.e(TAG, "Failed to persist or load canonical profile for $uid: ${e.message}")
                _authState.value = AuthState.Error("تعذر تحميل ملف حسابك السحابي. حاول مجدداً.")
                false
            }
        }

    private fun syncLocalAuthSuccess(uid: String, email: String?) {
        val context = AppServices.appContext
        val prefs = runCatching {
            context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE)
        }.getOrNull() ?: return
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putBoolean("is_guest", false)
            putString("user_id", uid)
            if (!email.isNullOrBlank()) {
                putString("user_email", email)
            }
            apply()
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
        if (uid != null && refreshUserAndEmitAuthenticated(uid, currentSession)) {
            syncLocalAuthSuccess(uid, currentSession?.user?.email)
        } else if (uid == null) {
            checkLocalAuthFallback()
        }
        _authState.value
    }

    /**
     * Verifies and ensures an active Supabase authenticated session.
     * If the session is currently initializing, it waits for initialization to complete.
     * If the session has a valid token, it refreshes/validates if needed.
     * Returns the valid Supabase auth UID, or null if unauthenticated.
     */
    suspend fun ensureAuthenticatedSession(timeoutMillis: Long = 8_000L): String? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        runCatching {
            // Wait for initialization if necessary
            if (client.auth.sessionStatus.value is SessionStatus.Initializing) {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                    client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
                }
            }

            var currentSession = client.auth.currentSessionOrNull()
            var uid = currentSession?.user?.id ?: client.auth.currentUserOrNull()?.id

            // If UID is null, try to refresh or restore
            if (uid.isNullOrBlank()) {
                runCatching { client.auth.refreshCurrentSession() }
                currentSession = client.auth.currentSessionOrNull()
                uid = currentSession?.user?.id ?: client.auth.currentUserOrNull()?.id
            }

            if (!uid.isNullOrBlank()) {
                // Ensure local state is synced with this real session
                if (refreshUserAndEmitAuthenticated(uid, currentSession)) {
                    syncLocalAuthSuccess(uid, currentSession?.user?.email)
                    uid
                } else {
                    null
                }
            } else {
                // Definitely unauthenticated
                checkLocalAuthFallback()
                null
            }
        }.getOrElse { e ->
            Log.e(TAG, "ensureAuthenticatedSession failed: ${e.message}")
            checkLocalAuthFallback()
            null
        }
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
    suspend fun register(
        email: String,
        pass: String,
        customId: String? = null,
        displayName: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        _authState.value = AuthState.Loading
        runCatching {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }
            val session = client.auth.currentSessionOrNull()
            val uid = session?.user?.id ?: client.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("EMAIL_CONFIRMATION_REQUIRED")
            if (!refreshUserAndEmitAuthenticated(uid, session, customId, displayName)) {
                throw IllegalStateException("تعذر حفظ ملف الحساب في السحابة")
            }
            syncLocalAuthSuccess(uid, session?.user?.email)
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
            val session = client.auth.currentSessionOrNull()
            val uid = session?.user?.id ?: client.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("لم تُنشأ جلسة مصادقة صالحة")
            if (!refreshUserAndEmitAuthenticated(uid, session)) {
                throw IllegalStateException("تعذر تحميل ملف الحساب السحابي")
            }
            syncLocalAuthSuccess(uid, session?.user?.email)
            true
        }.onFailure { e ->
            _authState.value = AuthState.Error(e.message ?: "Login failed")
        }
    }

    /**
     * Sign in through the server-side username/account-number resolver.
     * The server returns only a Supabase Auth session; it never exposes an
     * unauthenticated profile/email lookup endpoint.
     */
    suspend fun loginWithIdentifier(identifier: String, pass: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            if (!isAvailable) {
                return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
            }
            _authState.value = AuthState.Loading
            runCatching {
                val response = client.functions.invoke(
                    function = "qabas-auth-identifier-login",
                    body = buildJsonObject {
                        put("identifier", identifier.trim())
                        put("password", pass)
                    },
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, "application/json")
                    }
                )
                val tokens = response.body<IdentifierLoginResponse>()
                require(tokens.accessToken.isNotBlank() && tokens.refreshToken.isNotBlank()) {
                    "بيانات الدخول غير صحيحة"
                }

                client.auth.importSession(
                    UserSession(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = tokens.expiresIn,
                        tokenType = tokens.tokenType,
                        user = null
                    )
                )
                // Importing the tokens does not populate the local user object.
                // Fetch it through Supabase Auth before using the UID for RLS.
                client.auth.retrieveUserForCurrentSession(updateSession = true)

                val session = client.auth.currentSessionOrNull()
                val uid = session?.user?.id ?: client.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("تعذر إنشاء جلسة مصادقة")
                if (!refreshUserAndEmitAuthenticated(uid, session)) {
                    throw IllegalStateException("تعذر تحميل ملف الحساب السحابي")
                }
                syncLocalAuthSuccess(uid, session?.user?.email ?: client.auth.currentUserOrNull()?.email)
                true
            }.onFailure { error ->
                Log.w(TAG, "Identifier sign-in failed: ${error.message}")
                _authState.value = AuthState.Error("بيانات الدخول غير صحيحة أو تعذر الوصول إلى خدمة المصادقة.")
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

    /** Sign out and remove cached account/session fields so another UID cannot inherit them. */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isAvailable) client.auth.signOut()

            AppServices.appContext.getSharedPreferences(
                "qabas_prefs",
                android.content.Context.MODE_PRIVATE
            ).edit().apply {
                putBoolean("is_logged_in", false)
                putBoolean("is_guest", false)
                remove("is_admin")
                remove("is_developer")
                remove("user_id")
                remove("user_email")
                remove("user_name")
                remove("user_display_name")
                remove("user_custom_id")
                remove("custom_id")
                remove("user_tag")
                remove("user_account_number")
                apply()
            }
            RolePromotionNotifier.dismissCelebration()
            SupabaseServices.Auth.clearVerifiedRole()
            _authState.value = AuthState.Unauthenticated
            Unit
        }.onFailure { e ->
            Log.e(TAG, "Logout failed: ${e.message}")
        }
    }

    /**
     * Registers or updates a user-specific API key for a service (e.g., 'gemini', 'qabas_api').
     * The key is stored in the 'user_api_keys' table in Supabase.
     */
    suspend fun registerApiKey(service: String, key: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext false
            val row = mapOf(
                "user_id" to userId,
                "service" to service,
                "encrypted_value" to key,
                "updated_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.ROOT).format(java.util.Date())
            )
            client.postgrest.from("user_api_keys").upsert(row) {
                onConflict = "user_id,service"
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register API key: ${e.message}")
            false
        }
    }

    /**
     * Fetches the user's API keys from the cloud.
     */
    suspend fun fetchApiKeys(): List<Map<String, String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()
            client.postgrest.from("user_api_keys").select {
                filter {
                    eq("user_id", userId)
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch API keys: ${e.message}")
            emptyList()
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
internal fun dispatchAuthNavigationState(
    state: AuthState,
    onAuthenticated: (isAdmin: Boolean) -> Unit,
    onUnauthenticated: () -> Unit,
    onGuest: () -> Unit
) {
    when (state) {
        is AuthState.Authenticated -> onAuthenticated(state.isAdmin)
        is AuthState.Loading -> Unit
        is AuthState.Error,
        is AuthState.Unauthenticated -> onUnauthenticated()
        is AuthState.Guest -> onGuest()
    }
}

@Composable
fun ReactiveAuthNavigationObserver(
    repository: SupabaseRepository = SupabaseRepository.getInstance(),
    onAuthenticated: (isAdmin: Boolean) -> Unit,
    onUnauthenticated: () -> Unit,
    onGuest: () -> Unit = {}
) {
    val authState by repository.authState.collectAsState()

    LaunchedEffect(authState) {
        dispatchAuthNavigationState(
            state = authState,
            onAuthenticated = onAuthenticated,
            onUnauthenticated = onUnauthenticated,
            onGuest = onGuest
        )
    }
}
