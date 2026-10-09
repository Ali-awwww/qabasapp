@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.qabas.app

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * High-level Supabase service module.
 *
 * Mirrors the shape of [CloudServices] so callers can fall back to the local
 * handler when Supabase is not configured. Every public method returns
 * `null` / `emptyList()` / `false` when Supabase is unavailable so the app
 * degrades gracefully.
 *
 * Requires:
 *   - SUPABASE_URL in .env (BuildConfig.SUPABASE_URL)
 *   - SUPABASE_ANON_KEY in .env (BuildConfig.SUPABASE_ANON_KEY)
 */
object SupabaseServices {

    private const val TAG = "SupabaseServices"

    val isSupabaseAvailable: Boolean
        get() = SupabaseConfig.isConfigured

    private val client get() = SupabaseConfig.client

    // ---------- Typed rows ----------

    @Serializable
    data class UserRow(
        val id: String,
        @SerialName("external_id") val externalId: String? = null,
        val email: String? = null,
        val name: String? = null,
        val type: String = "free",
        val status: String? = "active",
        val strikes: Int = 0,
        @SerialName("is_eligible") val isEligible: Boolean = true,
        @SerialName("custom_id") val customId: String? = null,
        @SerialName("account_number") val accountNumber: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class HadithRow(
        val id: String,
        val title: String,
        val narrator: String,
        val text: String,
        val status: String,
        val source: String,
        @SerialName("theme_bg") val themeBg: String = "black_gold",
        @SerialName("suggested_aspect") val suggestedAspect: String = "9:16",
        val category: String? = null,
        @SerialName("display_order") val displayOrder: Int = 0,
        @SerialName("is_active") val isActive: Boolean = true
    )

    @Serializable
    data class ProjectRow(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("external_id") val externalId: String? = null,
        val title: String,
        val description: String? = null,
        val status: String = "draft",
        val cost: Double = 0.0,
        val tags: JsonElement = JsonArray(emptyList()),
        val metadata: JsonElement = JsonObject(emptyMap()),
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class ReelRow(
        val id: String,
        @SerialName("user_id") val userId: String,
        val title: String,
        val description: String? = null,
        @SerialName("video_url") val videoUrl: String,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        val tags: List<String> = emptyList(),
        @SerialName("likes_count") val likesCount: Int = 0,
        @SerialName("comments_count") val commentsCount: Int = 0,
        @SerialName("shares_count") val sharesCount: Int = 0,
        @SerialName("views_count") val viewsCount: Int = 0,
        @SerialName("is_active") val isActive: Boolean = true,
        val metadata: JsonElement = JsonObject(emptyMap()),
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("user_name") val userName: String? = null, // Virtual join field
        @SerialName("user_avatar") val userAvatar: String? = null // Virtual join field
    )

    @Serializable
    data class ReelCommentRow(
        val id: String,
        @SerialName("reel_id") val reelId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("comment_text") val commentText: String,
        @SerialName("likes_count") val likesCount: Int = 0,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("user_name") val userName: String? = null
    )

    @Serializable
    data class StyleRow(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        val name: String,
        @SerialName("source_video") val sourceVideo: String? = null,
        val payload: JsonElement = JsonObject(emptyMap()),
        val score: Int = 0,
        val tags: JsonElement = JsonArray(emptyList()),
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class MasterStyleRow(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        val name: String,
        val description: String? = null,
        val payload: JsonElement = JsonObject(emptyMap()),
        val compliance: Int = 95,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class PromoCodeRow(
        val code: String,
        val type: String = "PROMO",
        val value: Long = 0L,
        @SerialName("is_active") val isActive: Boolean = true,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class GuardLogRow(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        val snippet: String = "",
        val verdict: String = "",
        val score: Int = 0,
        val reason: String = "",
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class TransactionRow(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("product_id") val productId: String = "",
        @SerialName("purchase_token") val purchaseToken: String = "",
        @SerialName("price_amount") val priceAmount: Double = 0.0,
        val currency: String = "USD",
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class LeaderboardRow(
        val id: String? = null,
        @SerialName("user_id") val userId: String? = null,
        val name: String = "صانع محتوى",
        val points: Long = 0L,
        @SerialName("is_eligible") val isEligible: Boolean = true,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("last_won_at") val lastWonAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class ReelFeedRow(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("user_name") val userName: String = "صانع محتوى",
        val title: String = "",
        val topic: String? = null,
        val hook: String? = null,
        @SerialName("script_body") val scriptBody: String? = null,
        val cta: String? = null,
        @SerialName("likes_count") val likesCount: Int = 0,
        @SerialName("shares_count") val sharesCount: Int = 0,
        @SerialName("duration_seconds") val durationSeconds: Int = 15,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class AudioTrackRow(
        val id: String,
        val title: String,
        val artist: String = "",
        val category: String = "تلاوات قرآنية",
        @SerialName("audio_url") val audioUrl: String,
        val duration: String = "0:00",
        val badge: String = "سحابي",
        val author: String = "",
        @SerialName("likes_count") val likesCount: Int = 0,
        @SerialName("is_active") val isActive: Boolean = true,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class ScholarLectureRow(
        val id: String,
        @SerialName("scholar_id") val scholarId: String,
        @SerialName("scholar_name") val scholarName: String = "",
        val title: String,
        val speaker: String = "",
        val url: String,
        val type: String = "يوتيوب / مرئي",
        val category: String = "سيرة وعبر",
        val synopsis: String = "",
        @SerialName("duration_approx") val durationApprox: String = "",
        @SerialName("is_official") val isOfficial: Boolean = true,
        val notes: String = "",
        @SerialName("added_at_millis") val addedAtMillis: Long = System.currentTimeMillis()
    )

    @Serializable
    data class ScholarProposalRow(
        val id: String,
        @SerialName("scholar_id") val scholarId: String,
        @SerialName("scholar_name") val scholarName: String = "",
        @SerialName("suggested_title") val suggestedTitle: String,
        @SerialName("suggested_speaker") val suggestedSpeaker: String = "",
        @SerialName("suggested_url") val suggestedUrl: String = "",
        @SerialName("suggested_topic") val suggestedTopic: String = "سيرة وعبر",
        val rationale: String = "",
        @SerialName("user_proposer_name") val userProposerName: String = "طالب علم",
        @SerialName("upvotes_count") val upvotesCount: Int = 1,
        val status: String = "قيد المراجعة العلمية",
        @SerialName("created_at_millis") val createdAtMillis: Long = System.currentTimeMillis()
    )

    @Serializable
    data class AppReviewDto(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("user_id") val userId: String? = null,
        @SerialName("author_name") val authorName: String = "مستخدم قبس",
        val rating: Int = 5,
        @SerialName("category_rating") val categoryRating: String = "overall",
        @SerialName("app_version") val appVersion: String = "v1.2.1",
        @SerialName("device_model") val deviceModel: String = "",
        val comment: String = "",
        @SerialName("helpful_count") val helpfulCount: Int = 0,
        @SerialName("verified_user") val verifiedUser: Boolean = true,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class UserFeedbackDto(
        val id: String = UUID.randomUUID().toString(),
        val type: String, // "BUG_REPORT", "FEATURE_SUGGESTION", "GENERAL"
        val message: String,
        @SerialName("user_name") val userName: String = "مستخدم قبس",
        @SerialName("app_version") val appVersion: String = "",
        @SerialName("device_info") val deviceInfo: String = "",
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class BugReportRow(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("user_email") val userEmail: String = "",
        @SerialName("screen_name") val screenName: String = "",
        val description: String = "",
        @SerialName("image_url") val imageUrl: String = "",
        @SerialName("report_type") val reportType: String = "BUG",
        val status: String = "open",
        @SerialName("app_version") val appVersion: String = "",
        @SerialName("device_info") val deviceInfo: String = "",
        @SerialName("developer_reply") val developerReply: String? = null,
        @SerialName("account_number") val accountNumber: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class ChatMessageRow(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("user_id") val userId: String? = null,
        @SerialName("user_email") val userEmail: String = "",
        val sender: String = "user", // user | developer
        val message: String = "",
        @SerialName("account_number") val accountNumber: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class AppUpdateReleaseRow(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("version_name") val versionName: String,
        @SerialName("version_code") val versionCode: Int,
        @SerialName("apk_url") val apkUrl: String,
        @SerialName("release_notes") val releaseNotes: String = "",
        @SerialName("is_mandatory") val isMandatory: Boolean = false,
        @SerialName("file_size_bytes") val fileSizeBytes: Long = 0L,
        @SerialName("is_active") val isActive: Boolean = true,
        @SerialName("created_at") val createdAt: String? = null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class AuthDiagnosticsResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val email: String? = null,
        val hasSession: Boolean = false,
        val sessionExpiration: String? = null,
        val rawErrorMessage: String? = null,
        val userFriendlyErrorMessage: String? = null
    )

    // ---------- Authentication ----------

    object Auth {
        private val _currentUser = MutableStateFlow<UserRow?>(null)
        val currentUser: StateFlow<UserRow?> = _currentUser.asStateFlow()

        private val _verifiedRole = MutableStateFlow("free")
        val verifiedRole: StateFlow<String> = _verifiedRole.asStateFlow()

        fun getCurrentUserId(): String? {
            if (!isSupabaseAvailable) return null
            return runCatching { client.auth.currentUserOrNull()?.id }.getOrNull()
        }

        /**
         * Performs an explicit sign-in attempt and returns diagnostic details
         * regarding the response, session presence, user ID, and detailed error causes.
         */
        suspend fun diagnoseSignInAttempt(email: String, password: String): AuthDiagnosticsResult {
            if (!isSupabaseAvailable) {
                Log.e(TAG, "[AuthDiagnostics] Supabase is NOT configured/available.")
                return AuthDiagnosticsResult(
                    isSuccess = false,
                    rawErrorMessage = "Supabase service is unavailable",
                    userFriendlyErrorMessage = "خدمة السحابة غير مفعلة حالياً."
                )
            }

            Log.d(TAG, "[AuthDiagnostics] Initiating sign-in attempt for email: $email")
            return runCatching {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val session = client.auth.currentSessionOrNull()
                val user = client.auth.currentUserOrNull() ?: session?.user
                val hasSession = session != null

                Log.i(
                    TAG,
                    "[AuthDiagnostics] Supabase SignIn Result -> User ID: ${user?.id}, Email: ${user?.email}, Session Present: $hasSession"
                )

                if (session == null) {
                    Log.w(TAG, "[AuthDiagnostics] WARNING: Supabase returned without exception but session object is NULL!")
                    AuthDiagnosticsResult(
                        isSuccess = false,
                        userId = user?.id,
                        email = user?.email ?: email,
                        hasSession = false,
                        rawErrorMessage = "Session object is null despite sign-in call",
                        userFriendlyErrorMessage = "فشلت المصادقة: الجلسة السحابية غير مكتملة (Session Null)."
                    )
                } else {
                    AuthDiagnosticsResult(
                        isSuccess = true,
                        userId = user?.id ?: session.user?.id,
                        email = user?.email ?: session.user?.email ?: email,
                        hasSession = true,
                        sessionExpiration = session.expiresAt?.toString()
                    )
                }
            }.getOrElse { e ->
                val errorMsg = e.message ?: "Unknown authentication error"
                Log.e(TAG, "[AuthDiagnostics] Supabase SignIn Failed with Exception: $errorMsg", e)

                val friendlyMsg = when {
                    errorMsg.contains("Invalid login credentials", ignoreCase = true) ->
                        "البريد الإلكتروني أو كلمة المرور غير صحيحة."
                    errorMsg.contains("Email not confirmed", ignoreCase = true) ->
                        "البريد الإلكتروني بحاجة لتأكيد عبر الرسالة المرسلة لبريدك."
                    errorMsg.contains("User not found", ignoreCase = true) ->
                        "الحساب غير موجود. يمكنك إنشاء حساب جديد أولاً."
                    else -> "فشل تسجيل الدخول: $errorMsg"
                }

                AuthDiagnosticsResult(
                    isSuccess = false,
                    rawErrorMessage = errorMsg,
                    userFriendlyErrorMessage = friendlyMsg
                )
            }
        }

        suspend fun register(email: String, password: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                true
            }.onFailure { Log.w(TAG, "Supabase register failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun login(email: String, password: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                true
            }.onFailure { Log.w(TAG, "Supabase login failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun loginWithGoogle(idToken: String, nonce: String? = null): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    this.provider = Google
                    if (nonce != null) {
                        this.nonce = nonce
                    }
                }
                true
            }.onFailure { Log.w(TAG, "Supabase Google sign-in failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun resetPassword(email: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.auth.resetPasswordForEmail(email)
                true
            }.onFailure { Log.w(TAG, "Supabase reset password failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun updatePassword(newPassword: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.auth.updateUser {
                    password = newPassword
                }
                true
            }.onFailure { Log.w(TAG, "Supabase update password failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun logout() {
            if (!isSupabaseAvailable) return
            runCatching { client.auth.signOut() }
                .onFailure { Log.w(TAG, "Supabase logout failed: ${it.message}") }
        }

        /**
         * Verifies the user's role from Supabase Auth metadata (app_metadata).
         * This provides a secure way to verify permissions using JWT claims
         * synced from the Auth service.
         */
        suspend fun getUserRoleFromMetadata(): String {
            if (!isSupabaseAvailable) return "free"
            return runCatching {
                val user = client.auth.retrieveUserForCurrentSession()
                // app_metadata is typically where custom roles are stored by admins
                val role = user.appMetadata?.get("role")?.let {
                    if (it is JsonPrimitive) it.content else it.toString().removeSurrounding("\"")
                } ?: "free"
                Log.d(TAG, "User role from metadata: $role")
                role
            }.onFailure { Log.w(TAG, "getUserRoleFromMetadata failed: ${it.message}") }
                .getOrDefault("free")
        }

        /**
         * Quick check to see if the current user has administrative metadata.
         */
        suspend fun isAuthAdmin(): Boolean {
            val role = getUserRoleFromMetadata()
            _verifiedRole.value = role
            return role == "admin" || role == "developer" || role == "owner"
        }

        /**
         * Refreshes the local role state from Auth metadata.
         */
        suspend fun refreshVerifiedRole() {
            _verifiedRole.value = getUserRoleFromMetadata()
        }
    }

    // ---------- Database ----------

    object Database {
        suspend fun recordBugReport(
            userEmail: String,
            screenName: String,
            description: String,
            imageUrl: String,
            reportType: String,
            appVersion: String = "",
            deviceModel: String = "",
            developerReply: String? = null,
            id: String = UUID.randomUUID().toString(),
            accountNumber: String? = null
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                // Build a clean JSON object omitting nulls so Postgres default now() isn't overridden with null
                val payload = buildJsonObject {
                    put("id", id)
                    put("user_email", userEmail)
                    put("screen_name", screenName)
                    put("description", description)
                    put("image_url", imageUrl)
                    put("report_type", reportType)
                    put("status", "open")
                    put("app_version", appVersion)
                    if (deviceModel.isNotBlank()) put("device_info", deviceModel)
                    if (!developerReply.isNullOrBlank()) put("developer_reply", developerReply)
                    if (!accountNumber.isNullOrBlank()) put("account_number", accountNumber)
                }

                // 1. Try dev_bug_reports
                val firstAttempt = runCatching {
                    client.postgrest.from("dev_bug_reports").insert(payload)
                }
                if (firstAttempt.isSuccess) return@runCatching true

                // 2. Fallback without account_number if column not in schema
                if (!accountNumber.isNullOrBlank()) {
                    val fallbackPayload = buildJsonObject {
                        put("id", id)
                        put("user_email", userEmail)
                        put("screen_name", screenName)
                        put("description", description)
                        put("image_url", imageUrl)
                        put("report_type", reportType)
                        put("status", "open")
                        put("app_version", appVersion)
                        if (deviceModel.isNotBlank()) put("device_info", deviceModel)
                        if (!developerReply.isNullOrBlank()) put("developer_reply", developerReply)
                    }
                    if (runCatching { client.postgrest.from("dev_bug_reports").insert(fallbackPayload) }.isSuccess) return@runCatching true
                }

                // 3. Fallback: try bug_reports
                val secondAttempt = runCatching {
                    client.postgrest.from("bug_reports").insert(payload)
                }
                if (secondAttempt.isSuccess) return@runCatching true

                // 4. Fallback: try user_feedbacks
                val thirdAttempt = runCatching {
                    val fbPayload = buildJsonObject {
                        put("id", id)
                        put("type", reportType)
                        put("message", "[$screenName] $description")
                        put("user_name", userEmail)
                        put("app_version", appVersion)
                        put("device_info", deviceModel)
                    }
                    client.postgrest.from("user_feedbacks").insert(fbPayload)
                }
                thirdAttempt.isSuccess
            }.onFailure { Log.w(TAG, "recordBugReport failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getAllBugReports(): List<BugReportRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                val list = mutableListOf<BugReportRow>()
                val seenIds = mutableSetOf<String>()

                // 1. Fetch from dev_bug_reports
                val fromDev = runCatching {
                    client.postgrest.from("dev_bug_reports").select {
                        order("created_at", Order.DESCENDING)
                    }.decodeListOrEmpty<BugReportRow>()
                }.getOrNull().orEmpty()

                fromDev.forEach { item ->
                    if (item.id.isNotBlank() && seenIds.add(item.id)) {
                        list.add(item)
                    }
                }

                // 2. Fetch from bug_reports table to ensure zero loss
                val fromBug = runCatching {
                    client.postgrest.from("bug_reports").select {
                        order("created_at", Order.DESCENDING)
                    }.decodeListOrEmpty<BugReportRow>()
                }.getOrNull().orEmpty()

                fromBug.forEach { item ->
                    if (item.id.isNotBlank() && seenIds.add(item.id)) {
                        list.add(item)
                    }
                }

                list.sortedByDescending { it.createdAt }
            }.onFailure { Log.w(TAG, "getAllBugReports failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun sendChatMessage(
            userEmail: String,
            message: String,
            sender: String = "user",
            targetUserId: String? = null,
            accountNumber: String? = null
        ): Boolean {
            val cleanEmail = userEmail.trim().lowercase()
            if (!isSupabaseAvailable || cleanEmail.isBlank() || message.isBlank()) return false

            val userId = when (sender) {
                "user" -> Auth.getCurrentUserId() ?: cleanEmail
                "developer" -> targetUserId ?: "dev_admin"
                else -> cleanEmail
            }

            return runCatching {
                val msgId = UUID.randomUUID().toString()
                val payloadFull = buildJsonObject {
                    put("id", msgId)
                    put("user_id", userId)
                    put("user_email", cleanEmail)
                    put("sender", sender)
                    put("message", message.trim())
                    if (!accountNumber.isNullOrBlank()) put("account_number", accountNumber)
                }
                val payloadWithUserId = buildJsonObject {
                    put("id", msgId)
                    put("user_id", userId)
                    put("user_email", cleanEmail)
                    put("sender", sender)
                    put("message", message.trim())
                }
                val payloadWithoutId = buildJsonObject {
                    put("user_email", cleanEmail)
                    put("sender", sender)
                    put("message", message.trim())
                }

                // محاولة الإرسال إلى جدول dev_chat_messages
                if (runCatching { client.postgrest.from("dev_chat_messages").insert(payloadFull); true }.getOrDefault(false)) return@runCatching true
                if (runCatching { client.postgrest.from("dev_chat_messages").insert(payloadWithUserId); true }.getOrDefault(false)) return@runCatching true
                if (runCatching { client.postgrest.from("dev_chat_messages").insert(payloadWithoutId); true }.getOrDefault(false)) return@runCatching true

                // محاولة بديلة مع جدول chat_messages
                if (runCatching { client.postgrest.from("chat_messages").insert(payloadWithUserId); true }.getOrDefault(false)) return@runCatching true
                if (runCatching { client.postgrest.from("chat_messages").insert(payloadWithoutId); true }.getOrDefault(false)) return@runCatching true

                false
            }.onFailure { Log.w(TAG, "sendChatMessage failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getChatMessages(userEmail: String): List<ChatMessageRow> {
            val cleanEmail = userEmail.trim().lowercase()
            if (!isSupabaseAvailable || cleanEmail.isBlank()) return emptyList()
            val userId = Auth.getCurrentUserId()
            return runCatching {
                val devResult = runCatching {
                    client.postgrest.from("dev_chat_messages").select {
                        if (userId != null) {
                            filter {
                                or {
                                    eq("user_id", userId)
                                    eq("user_email", cleanEmail)
                                }
                            }
                        } else {
                            filter { eq("user_email", cleanEmail) }
                        }
                        order("created_at", Order.ASCENDING)
                    }.decodeListOrEmpty<ChatMessageRow>()
                }.getOrNull().orEmpty()

                if (devResult.isNotEmpty()) return@runCatching devResult

                // Fallback attempt with simple email filter
                val fallbackEmail = runCatching {
                    client.postgrest.from("dev_chat_messages").select {
                        filter { eq("user_email", cleanEmail) }
                        order("created_at", Order.ASCENDING)
                    }.decodeListOrEmpty<ChatMessageRow>()
                }.getOrNull().orEmpty()

                if (fallbackEmail.isNotEmpty()) return@runCatching fallbackEmail

                // Fallback table: chat_messages
                val chatTableResult = runCatching {
                    client.postgrest.from("chat_messages").select {
                        filter { eq("user_email", cleanEmail) }
                        order("created_at", Order.ASCENDING)
                    }.decodeListOrEmpty<ChatMessageRow>()
                }.getOrNull().orEmpty()

                chatTableResult
            }.onFailure { Log.w(TAG, "getChatMessages failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun getAllChatThreads(): Map<String, List<ChatMessageRow>> {
            if (!isSupabaseAvailable) return emptyMap()
            return runCatching {
                val result = mutableMapOf<String, MutableList<ChatMessageRow>>()

                val devMsgs = runCatching {
                    client.postgrest.from("dev_chat_messages").select {
                        order("created_at", Order.ASCENDING)
                    }.decodeListOrEmpty<ChatMessageRow>()
                }.getOrNull().orEmpty()

                devMsgs.filter { it.userEmail.isNotBlank() }.forEach { m ->
                    val key = m.userEmail.trim().lowercase()
                    result.getOrPut(key) { mutableListOf() }.add(m)
                }

                val altMsgs = runCatching {
                    client.postgrest.from("chat_messages").select {
                        order("created_at", Order.ASCENDING)
                    }.decodeListOrEmpty<ChatMessageRow>()
                }.getOrNull().orEmpty()

                altMsgs.filter { it.userEmail.isNotBlank() }.forEach { m ->
                    val key = m.userEmail.trim().lowercase()
                    val existing = result.getOrPut(key) { mutableListOf() }
                    if (existing.none { it.id == m.id && it.message == m.message }) {
                        existing.add(m)
                    }
                }

                result
            }.onFailure { Log.w(TAG, "getAllChatThreads failed: ${it.message}") }
                .getOrDefault(emptyMap())
        }

        suspend fun updateBugReportStatus(
            id: String,
            status: String,
            developerReply: String? = null
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val targetTable = "dev_bug_reports"
                client.postgrest.from(targetTable).update({
                    set("status", status)
                    if (developerReply != null) {
                        set("developer_reply", developerReply)
                    }
                }) {
                    filter { eq("id", id) }
                }
                true
            }.onFailure { Log.w(TAG, "updateBugReportStatus failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun deleteBugReport(id: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                runCatching {
                    client.postgrest.from("dev_bug_reports").delete {
                        filter { eq("id", id) }
                    }
                }
                runCatching {
                    client.postgrest.from("bug_reports").delete {
                        filter { eq("id", id) }
                    }
                }
                true
            }.onFailure { Log.w(TAG, "deleteBugReport failed: ${it.message}") }
                .getOrDefault(false)
        }

        /**
         * جلب أحدث إصدار نشط من جدول app_updates في Supabase.
         */
        suspend fun getLatestActiveUpdate(): AppUpdateReleaseRow? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                client.postgrest.from("app_updates").select {
                    filter { eq("is_active", true) }
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }.decodeListOrEmpty<AppUpdateReleaseRow>().firstOrNull()
            }.onFailure { Log.w(TAG, "getLatestActiveUpdate failed: ${it.message}") }
                .getOrNull()
        }

        /**
         * نشر إصدار جديد في Supabase لجميع مستخدمي التطبيق.
         */
        suspend fun publishAppUpdate(release: AppUpdateReleaseRow): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("app_updates").insert(
                    json.encodeToJsonElement(release)
                )
                true
            }.onFailure { Log.w(TAG, "publishAppUpdate failed: ${it.message}") }
                .getOrDefault(false)
        }

        /**
         * جلب كافة الإصدارات السابقة المسجلة في السحابة.
         */
        suspend fun getAllUpdateReleases(): List<AppUpdateReleaseRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("app_updates").select {
                    order("version_code", Order.DESCENDING)
                }.decodeListOrEmpty<AppUpdateReleaseRow>()
            }.onFailure { Log.w(TAG, "getAllUpdateReleases failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        /**
         * تبديل حالة تفعيل إصدار معين (تعطيل/تفعيل).
         */
        suspend fun toggleUpdateReleaseStatus(id: String, isActive: Boolean): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("app_updates").update({
                    set("is_active", isActive)
                }) {
                    filter { eq("id", id) }
                }
                true
            }.onFailure { Log.w(TAG, "toggleUpdateReleaseStatus failed: ${it.message}") }
                .getOrDefault(false)
        }

        /** Lightweight health check used by dashboards and the developer screen. */
        suspend fun ping(): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("users").select().decodeListOrEmpty<UserRow>()
                true
            }.onFailure { Log.w(TAG, "Supabase ping failed: ${it.message}") }
                .getOrDefault(false)
        }

        /**
         * Look up a user row by external_id (typically the Firebase Auth uid).
         * If no row is found, inserts a fresh row and returns the inserted
         * representation. Returns `null` when Supabase is unavailable.
         */
        suspend fun ensureUser(
            externalId: String,
            email: String?,
            name: String?,
            customId: String? = null,
            accountNumber: String? = null
        ): UserRow? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                val existing = client.postgrest.from("users").select {
                    filter { eq("external_id", externalId) }
                    limit(1)
                }.decodeListOrEmpty<UserRow>().firstOrNull()
                existing ?: run {
                    val uid = UUID.randomUUID().toString()
                    val payload = buildJsonObject {
                        put("id", uid)
                        put("external_id", externalId)
                        if (!email.isNullOrBlank()) put("email", email)
                        if (!name.isNullOrBlank()) put("name", name)
                        if (!customId.isNullOrBlank()) put("custom_id", customId)
                        if (!accountNumber.isNullOrBlank()) put("account_number", accountNumber)
                        put("type", "free")
                        put("status", "active")
                    }
                    runCatching {
                        client.postgrest.from("users").insert(JsonArray(listOf(payload)))
                    }.recoverCatching {
                        // Fallback without account_number / custom_id if column not in schema
                        val fallback = buildJsonObject {
                            put("id", uid)
                            put("external_id", externalId)
                            if (!email.isNullOrBlank()) put("email", email)
                            if (!name.isNullOrBlank()) put("name", name)
                            if (!customId.isNullOrBlank()) put("custom_id", customId)
                            put("type", "free")
                            put("status", "active")
                        }
                        client.postgrest.from("users").insert(JsonArray(listOf(fallback)))
                    }.recoverCatching {
                        val minimalFallback = buildJsonObject {
                            put("id", uid)
                            put("external_id", externalId)
                            if (!email.isNullOrBlank()) put("email", email)
                            if (!name.isNullOrBlank()) put("name", name)
                            put("type", "free")
                            put("status", "active")
                        }
                        client.postgrest.from("users").insert(JsonArray(listOf(minimalFallback)))
                    }
                    UserRow(
                        id = uid,
                        externalId = externalId,
                        email = email,
                        name = name,
                        customId = customId,
                        accountNumber = accountNumber
                    )
                }
            }.onFailure { Log.w(TAG, "ensureUser failed: ${it.message}") }
                .getOrNull()
        }

        /**
         * البحث عن البريد الإلكتروني للمستخدم باستخدام معرّفه الخاص (Custom User ID / Handle)
         * يدعم البحث عبر عمود custom_id مع بدائل مرنة للأسماء أو الإعدادات المحفوظة.
         */
        suspend fun findEmailByCustomId(identifier: String): String? {
            if (!isSupabaseAvailable || identifier.isBlank()) return null
            val cleanId = identifier.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)

            // 1. فحص جدول المستخدمين بعمود custom_id
            val byCustomId = runCatching {
                client.postgrest.from("users").select {
                    filter { eq("custom_id", cleanId) }
                    limit(1)
                }.decodeListOrEmpty<UserRow>().firstOrNull()?.email
            }.getOrNull()
            if (!byCustomId.isNullOrBlank()) return byCustomId

            // 2. فحص بديل عبر اسم المستخدم أو المعرّف الخارجي
            val byNameOrExt = runCatching {
                client.postgrest.from("users").select {
                    filter {
                        or {
                            ilike("name", cleanId)
                            eq("external_id", cleanId)
                        }
                    }
                    limit(1)
                }.decodeListOrEmpty<UserRow>().firstOrNull()?.email
            }.getOrNull()
            if (!byNameOrExt.isNullOrBlank()) return byNameOrExt

            // 3. فحص إعدادات المستخدمين في جدول user_settings إن وجد
            val bySettings = runCatching {
                val settings = client.postgrest.from("user_settings").select {
                    filter { ilike("preferences_json", "%$cleanId%") }
                    limit(5)
                }.decodeListOrEmpty<UserSettingsRow>()
                for (s in settings) {
                    try {
                        val obj = org.json.JSONObject(s.preferencesJson)
                        val storedId = obj.optString("custom_id", "")
                        val storedEmail = obj.optString("email", "")
                        if (storedId.equals(cleanId, ignoreCase = true) && storedEmail.isNotBlank()) {
                            return@runCatching storedEmail
                        }
                    } catch (_: Exception) {}
                }
                null
            }.getOrNull()

            return bySettings
        }

        suspend fun saveProject(
            title: String,
            description: String?,
            status: String = "draft",
            cost: Double = 0.0,
            tags: List<String> = emptyList(),
            metadata: Map<String, String> = emptyMap()
        ): ProjectRow? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                val userId = Auth.getCurrentUserId()
                val row = ProjectRow(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    externalId = userId,
                    title = title,
                    description = description,
                    status = status,
                    cost = cost,
                    tags = JsonArray(tags.map { JsonPrimitive(it) }),
                    metadata = JsonObject(metadata.mapValues { (_, v) -> JsonPrimitive(v) })
                )
                client.postgrest.from("projects").insert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ).decodeSingle<ProjectRow>()
            }.onFailure { Log.w(TAG, "saveProject failed: ${it.message}") }
                .getOrNull()
        }

        suspend fun saveFullProject(
            id: String,
            title: String,
            description: String?,
            status: String = "draft",
            cost: Double = 0.0,
            tags: List<String> = emptyList(),
            metadata: Map<String, String> = emptyMap()
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val userId = Auth.getCurrentUserId()
                val row = ProjectRow(
                    id = id,
                    userId = userId,
                    externalId = userId,
                    title = title,
                    description = description,
                    status = status,
                    cost = cost,
                    tags = JsonArray(tags.map { JsonPrimitive(it) }),
                    metadata = JsonObject(metadata.mapValues { (_, v) -> JsonPrimitive(v) })
                )
                client.postgrest.from("projects").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "id"
                }
                true
            }.onFailure { Log.w(TAG, "saveFullProject failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun deleteProject(id: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("projects").delete {
                    filter { eq("id", id) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteProject failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getUserProjects(userId: String? = null): List<ProjectRow> {
            if (!isSupabaseAvailable) return emptyList()
            val targetUid = userId ?: Auth.getCurrentUserId() ?: return emptyList()
            return runCatching {
                client.postgrest.from("projects").select {
                    filter {
                        or {
                            eq("user_id", targetUid)
                            eq("external_id", targetUid)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<ProjectRow>()
            }.onFailure { Log.w(TAG, "getUserProjects failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun getFeed(limit: Long = 20L): List<ProjectRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("projects").select {
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }.decodeList<ProjectRow>()
            }.onFailure { Log.w(TAG, "getFeed failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        /**
         * Fetch the full, real user list from the Supabase `users` table for the
         * developer dashboard. Returns an empty list when Supabase is unavailable.
         */
        suspend fun getAllUsers(): List<UserRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("users").select {
                    order("created_at", Order.DESCENDING)
                }.decodeList<UserRow>()
            }.recoverCatching {
                client.postgrest.from("users").select().decodeList<UserRow>()
            }.onFailure { Log.w(TAG, "getAllUsers failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun saveUser(
            id: String,
            name: String,
            email: String,
            type: String = "مجاني",
            customId: String? = null,
            accountNumber: String? = null
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                var saved = false
                if (!customId.isNullOrBlank() || !accountNumber.isNullOrBlank()) {
                    saved = runCatching {
                        val payload = buildJsonObject {
                            put("id", id)
                            put("external_id", id)
                            put("name", name)
                            put("email", email)
                            put("type", type)
                            put("status", "active")
                            if (!customId.isNullOrBlank()) put("custom_id", customId)
                            if (!accountNumber.isNullOrBlank()) put("account_number", accountNumber)
                        }
                        client.postgrest.from("users").upsert(
                            JsonArray(listOf(payload))
                        ) {
                            onConflict = "id"
                        }
                        true
                    }.getOrDefault(false)
                }

                if (!saved) {
                    val fallbackPayload = buildJsonObject {
                        put("id", id)
                        put("external_id", id)
                        put("name", name)
                        put("email", email)
                        put("type", type)
                        put("status", "active")
                    }
                    client.postgrest.from("users").upsert(
                        JsonArray(listOf(fallbackPayload))
                    ) {
                        onConflict = "id"
                    }
                }
                true
            }.onFailure { Log.w(TAG, "saveUser failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun updateUserRole(userId: String, newType: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("users").update(
                    JsonObject(mapOf("type" to JsonPrimitive(newType)))
                ) {
                    filter { eq("id", userId) }
                }
                true
            }.onFailure { Log.w(TAG, "updateUserRole failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun updateUserSuspension(userId: String, isSuspended: Boolean): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val strikes = if (isSuspended) 5 else 0
                val status = if (isSuspended) "suspended" else "active"
                client.postgrest.from("users").update(
                    JsonObject(mapOf(
                        "strikes" to JsonPrimitive(strikes),
                        "status" to JsonPrimitive(status)
                    ))
                ) {
                    filter { eq("id", userId) }
                }
                true
            }.onFailure { Log.w(TAG, "updateUserSuspension failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // أنماط الأسلوب الفني (Styles in Supabase)
        // ==========================================

        suspend fun saveStyleObject(
            id: String,
            name: String,
            userId: String,
            sourceVideo: String?,
            score: Int,
            tags: List<String>,
            payloadJson: String
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val payloadElem = runCatching { json.parseToJsonElement(payloadJson) }.getOrDefault(JsonObject(emptyMap()))
                val row = StyleRow(
                    id = id,
                    userId = userId,
                    name = name,
                    sourceVideo = sourceVideo,
                    payload = payloadElem,
                    score = score,
                    tags = JsonArray(tags.map { JsonPrimitive(it) })
                )
                client.postgrest.from("styles").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "id"
                }
                true
            }.onFailure { Log.w(TAG, "saveStyleObject failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getStyleObjects(userId: String? = null): List<StyleRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("styles").select {
                    if (!userId.isNullOrBlank()) {
                        filter { eq("user_id", userId) }
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<StyleRow>()
            }.onFailure { Log.w(TAG, "getStyleObjects failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun deleteStyleObject(styleId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("styles").delete {
                    filter { eq("id", styleId) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteStyleObject failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // أنماط الماستر (Master Styles in Supabase)
        // ==========================================

        suspend fun saveMasterStyle(
            id: String,
            name: String,
            description: String?,
            userId: String,
            compliance: Int,
            payloadJson: String
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val payloadElem = runCatching { json.parseToJsonElement(payloadJson) }.getOrDefault(JsonObject(emptyMap()))
                val row = MasterStyleRow(
                    id = id,
                    userId = userId,
                    name = name,
                    description = description,
                    payload = payloadElem,
                    compliance = compliance
                )
                client.postgrest.from("master_styles").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "id"
                }
                true
            }.onFailure { Log.w(TAG, "saveMasterStyle failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getMasterStyles(userId: String? = null): List<MasterStyleRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("master_styles").select {
                    if (!userId.isNullOrBlank()) {
                        filter { eq("user_id", userId) }
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<MasterStyleRow>()
            }.onFailure { Log.w(TAG, "getMasterStyles failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun deleteMasterStyle(masterStyleId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("master_styles").delete {
                    filter { eq("id", masterStyleId) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteMasterStyle failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // أكواد الهدايا والبرومو (Promo Codes in Supabase)
        // ==========================================

        suspend fun savePromoCode(code: String, type: String, value: Long): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val normalized = code.uppercase(java.util.Locale.ROOT)
                val row = PromoCodeRow(
                    code = normalized,
                    type = type,
                    value = value,
                    isActive = true
                )
                client.postgrest.from("promo_codes").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "code"
                }
                true
            }.onFailure { Log.w(TAG, "savePromoCode failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getValidPromoCodes(): List<PromoCodeRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("promo_codes").select {
                    filter { eq("is_active", true) }
                }.decodeList<PromoCodeRow>()
            }.onFailure { Log.w(TAG, "getValidPromoCodes failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        // ==========================================
        // سجلات الحارس والمشتريات (Guard & Purchases)
        // ==========================================

        suspend fun recordContentGuardCheck(
            id: String,
            userId: String,
            snippet: String,
            verdict: String,
            score: Int,
            reason: String
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val row = GuardLogRow(
                    id = id,
                    userId = userId,
                    snippet = snippet,
                    verdict = verdict,
                    score = score,
                    reason = reason
                )
                client.postgrest.from("content_guard_logs").insert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                )
                true
            }.onFailure { Log.w(TAG, "recordContentGuardCheck failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun recordTransaction(
            id: String,
            userId: String,
            productId: String,
            purchaseToken: String,
            priceAmount: Double,
            currency: String = "USD"
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val row = TransactionRow(
                    id = id,
                    userId = userId,
                    productId = productId,
                    purchaseToken = purchaseToken,
                    priceAmount = priceAmount,
                    currency = currency
                )
                client.postgrest.from("transactions").insert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                )
                true
            }.onFailure { Log.w(TAG, "recordTransaction failed: ${it.message}") }
                .getOrDefault(false)
        }

        /** Build a JSON object for jsonb columns from vararg pairs. */
        fun jsonObject(vararg pairs: Pair<String, Any?>): String = JsonObject(
            pairs.associate { (k, v) ->
                k to when (v) {
                    null -> JsonNull
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
            }
        ).toString()

        /**
         * Fetch the active hadith preset catalog from the cloud. Ordered by
         * `display_order` so admins control the sequence. Returns an empty
         * list when Supabase is unavailable so callers can fall back to a
         * hard-coded list without crashing.
         */
        suspend fun getHadithPresets(): List<HadithRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("hadith_presets").select {
                    filter { eq("is_active", true) }
                    order("display_order", Order.ASCENDING)
                }.decodeList<HadithRow>()
            }.onFailure { Log.w(TAG, "getHadithPresets failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        /**
         * Fetch all active audio tracks uploaded / managed in Supabase.
         */
        suspend fun getAudioTracks(): List<AudioTrackRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("audio_tracks").select {
                    filter { eq("is_active", true) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<AudioTrackRow>()
            }.onFailure { Log.w(TAG, "getAudioTracks failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        /**
         * Insert a new audio track record in Supabase.
         */
        suspend fun addAudioTrack(track: AudioTrackRow): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("audio_tracks").insert(
                    JsonArray(listOf(json.encodeToJsonElement(track)))
                )
                true
            }.onFailure { Log.w(TAG, "addAudioTrack failed: ${it.message}") }
                .getOrDefault(false)
        }

        /**
         * Delete an audio track from Supabase.
         */
        suspend fun deleteAudioTrack(trackId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("audio_tracks").delete {
                    filter { eq("id", trackId) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteAudioTrack failed: ${it.message}") }
                .getOrDefault(false)
        }

        /**
         * Update likes count for an audio track in Supabase.
         */
        suspend fun updateAudioTrackLikes(trackId: String, newCount: Int): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("audio_tracks").update(
                    { set("likes_count", newCount) }
                ) {
                    filter { eq("id", trackId) }
                }
                true
            }.onFailure { Log.w(TAG, "updateAudioTrackLikes failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // لوحة المتصدرين والدوري (Leaderboard & League in Supabase)
        // ==========================================

        suspend fun getLeaderboard(limit: Long = 50L): List<LeaderboardRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("league_leaderboard").select {
                    filter { eq("is_eligible", true) }
                    order("points", Order.DESCENDING)
                    limit(limit)
                }.decodeList<LeaderboardRow>()
            }.onFailure { Log.w(TAG, "getLeaderboard failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun upsertLeaderboardScore(
            userId: String,
            name: String,
            points: Long,
            isEligible: Boolean = true,
            avatarUrl: String? = null
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val row = LeaderboardRow(
                    userId = userId,
                    name = name,
                    points = points,
                    isEligible = isEligible,
                    avatarUrl = avatarUrl
                )
                client.postgrest.from("league_leaderboard").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "user_id"
                }
                true
            }.onFailure { Log.w(TAG, "upsertLeaderboardScore failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // مجتمع الإلهام والريلز (Reels Community Feed in Supabase)
        // ==========================================

        suspend fun getReelsFeed(limit: Long = 30L): List<ReelFeedRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("reels_feed").select {
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }.decodeList<ReelFeedRow>()
            }.onFailure { Log.w(TAG, "getReelsFeed failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun publishReelToFeed(reel: ReelFeedRow): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("reels_feed").insert(
                    JsonArray(listOf(json.encodeToJsonElement(reel)))
                )
                true
            }.onFailure { Log.w(TAG, "publishReelToFeed failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // سحابة الدروس والمحاضرات (Scholar Lectures in Supabase)
        // ==========================================

        suspend fun getScholarLectures(scholarId: String): List<ScholarLectureRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("scholar_lectures").select {
                    filter { eq("scholar_id", scholarId) }
                    order("added_at_millis", Order.DESCENDING)
                }.decodeListOrEmpty<ScholarLectureRow>()
            }.onFailure { Log.w(TAG, "getScholarLectures failed for $scholarId: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun saveScholarLecture(row: ScholarLectureRow): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("scholar_lectures").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "id"
                }
                true
            }.onFailure { Log.w(TAG, "saveScholarLecture failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun deleteScholarLecture(id: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("scholar_lectures").delete {
                    filter { eq("id", id) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteScholarLecture failed: ${it.message}") }
                .getOrDefault(false)
        }

        // ==========================================
        // سحابة مقترحات المستخدمين (Scholar Proposals in Supabase)
        // ==========================================

        suspend fun getScholarProposals(scholarId: String): List<ScholarProposalRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("scholar_proposals").select {
                    filter { eq("scholar_id", scholarId) }
                    order("upvotes_count", Order.DESCENDING)
                }.decodeListOrEmpty<ScholarProposalRow>()
            }.onFailure { Log.w(TAG, "getScholarProposals failed for $scholarId: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun saveScholarProposal(row: ScholarProposalRow): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("scholar_proposals").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "id"
                }
                true
            }.onFailure { Log.w(TAG, "saveScholarProposal failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun upvoteScholarProposal(id: String, newCount: Int): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("scholar_proposals").update(
                    { set("upvotes_count", newCount) }
                ) {
                    filter { eq("id", id) }
                }
                true
            }.onFailure { Log.w(TAG, "upvoteScholarProposal failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun deleteScholarProposal(id: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("scholar_proposals").delete {
                    filter { eq("id", id) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteScholarProposal failed: ${it.message}") }
                .getOrDefault(false)
        }
    }

    // ==========================================
    // سحابة تقييمات ومراجعات التطبيق (App Reviews in Supabase)
    // ==========================================

    object AppReviews {
        suspend fun getAppReviews(): Result<List<AppReviewDto>> {
            if (!isSupabaseAvailable) {
                return Result.failure(IllegalStateException("Supabase is not configured"))
            }
            return runCatching {
                client.postgrest.from("app_reviews").select {
                    order("created_at", Order.DESCENDING)
                }.decodeListOrEmpty<AppReviewDto>()
            }.onFailure { Log.w(TAG, "getAppReviews failed: ${it.message}") }
        }

        suspend fun submitAppReview(
            rating: Int,
            comment: String,
            authorName: String,
            category: String = "overall"
        ): Result<Unit> {
            if (!isSupabaseAvailable) {
                return Result.failure(IllegalStateException("Supabase is not configured"))
            }
            return runCatching {
                val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = android.os.Build.MODEL
                val androidVer = android.os.Build.VERSION.RELEASE
                val fullDeviceModel = "$manufacturer $model (Android $androidVer)"
                val currentVer = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

                val currentUserId = runCatching { client.auth.currentUserOrNull()?.id }.getOrNull()

                val review = AppReviewDto(
                    id = UUID.randomUUID().toString(),
                    userId = currentUserId,
                    authorName = authorName.trim().ifBlank { "مستخدم قبس" },
                    rating = rating.coerceIn(1, 5),
                    categoryRating = category.trim().ifBlank { "overall" },
                    appVersion = currentVer,
                    deviceModel = fullDeviceModel,
                    comment = comment.trim(),
                    helpfulCount = 0,
                    verifiedUser = true,
                    createdAt = null
                )

                client.postgrest.from("app_reviews").insert(
                    json.encodeToJsonElement(review)
                )
                Unit
            }.onFailure { Log.w(TAG, "submitAppReview failed: ${it.message}") }
        }

        suspend fun markReviewHelpful(reviewId: String, currentCount: Int): Result<Unit> {
            if (!isSupabaseAvailable) {
                return Result.failure(IllegalStateException("Supabase is not configured"))
            }
            return runCatching {
                client.postgrest.from("app_reviews").update(
                    { set("helpful_count", currentCount + 1) }
                ) {
                    filter { eq("id", reviewId) }
                }
                Unit
            }.onFailure { Log.w(TAG, "markReviewHelpful failed: ${it.message}") }
        }

        suspend fun updateReview(reviewId: String, newComment: String, newRating: Int): Result<Unit> {
            if (!isSupabaseAvailable) {
                return Result.failure(IllegalStateException("Supabase is not configured"))
            }
            return runCatching {
                client.postgrest.from("app_reviews").update(
                    {
                        set("comment", newComment.trim())
                        set("rating", newRating.coerceIn(1, 5))
                    }
                ) {
                    filter { eq("id", reviewId) }
                }
                Unit
            }.onFailure { Log.w(TAG, "updateReview failed: ${it.message}") }
        }

        suspend fun deleteReview(reviewId: String): Result<Unit> {
            if (!isSupabaseAvailable) {
                return Result.failure(IllegalStateException("Supabase is not configured"))
            }
            return runCatching {
                client.postgrest.from("app_reviews").delete {
                    filter { eq("id", reviewId) }
                }
                Unit
            }.onFailure { Log.w(TAG, "deleteReview failed: ${it.message}") }
        }
    }

    // ---------- Storage ----------

    @Serializable
    data class SupabaseStorageItem(
        val name: String,
        val id: String? = null,
        val bucketName: String,
        val publicUrl: String,
        val createdAt: String? = null,
        val updatedAt: String? = null,
        val sizeBytes: Long = 0L,
        val mimeType: String = ""
    )

    object Storage {
        val DEFAULT_BUCKETS = listOf("media-assets", "ai-generated", "audio-tracks", "user-uploads")

        /**
         * Upload audio bytes to Supabase Storage bucket (default bucket: 'audio-tracks').
         * Returns the public URL of the uploaded audio file, or null if failed.
         */
        suspend fun uploadAudioFile(
            bucketName: String = "audio-tracks",
            fileName: String,
            data: ByteArray
        ): String? = uploadFile(bucketName, fileName, data)

        /**
         * General file upload to any Supabase Storage bucket (images, videos, audio).
         * Returns the public URL of the uploaded file, or null on failure.
         */
        suspend fun uploadFile(
            bucketName: String = "media-assets",
            path: String,
            data: ByteArray,
            upsert: Boolean = true
        ): String? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                val bucket = client.storage.from(bucketName)
                bucket.upload(path, data) {
                    this.upsert = upsert
                }
                bucket.publicUrl(path)
            }.onFailure { Log.w(TAG, "uploadFile failed for $bucketName/$path: ${it.message}") }
                .getOrNull()
        }

        /**
         * List files in a specific Supabase Storage bucket.
         */
        @OptIn(kotlin.time.ExperimentalTime::class)
        suspend fun listFiles(
            bucketName: String,
            prefix: String = ""
        ): List<SupabaseStorageItem> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                val bucket = client.storage.from(bucketName)
                val files = bucket.list(prefix)
                files.map { file ->
                    val path = if (prefix.isBlank()) file.name else "$prefix/${file.name}"
                    val publicUrl = bucket.publicUrl(path)
                    SupabaseStorageItem(
                        name = file.name,
                        id = file.id,
                        bucketName = bucketName,
                        publicUrl = publicUrl,
                        createdAt = file.createdAt?.toString(),
                        updatedAt = file.updatedAt?.toString(),
                        sizeBytes = 0L
                    )
                }
            }.onFailure { Log.w(TAG, "listFiles failed for bucket $bucketName: ${it.message}") }
                .getOrDefault(emptyList())
        }

        /**
         * Delete one or more files from a Supabase Storage bucket.
         */
        suspend fun deleteFiles(
            bucketName: String,
            paths: List<String>
        ): Boolean {
            if (!isSupabaseAvailable || paths.isEmpty()) return false
            return runCatching {
                val bucket = client.storage.from(bucketName)
                bucket.delete(paths)
                true
            }.onFailure { Log.w(TAG, "deleteFiles failed for $bucketName: ${it.message}") }
                .getOrDefault(false)
        }

        /**
         * Get public URL for an existing asset in Supabase Storage.
         */
        fun getPublicUrl(bucketName: String, path: String): String? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                client.storage.from(bucketName).publicUrl(path)
            }.getOrNull()
        }
    }

    // ==========================================
    // سحابة بلاغات الأخطاء واقتراحات الميزات (User Feedbacks & Bug Reports)
    // ==========================================

    object Feedback {
        suspend fun submitFeedback(
            type: String,
            message: String,
            userName: String = ""
        ): Result<Unit> {
            if (!isSupabaseAvailable) {
                return Result.failure(IllegalStateException("Supabase is not configured"))
            }
            return runCatching {
                val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = android.os.Build.MODEL
                val androidVer = android.os.Build.VERSION.RELEASE
                val fullDeviceModel = "$manufacturer $model (Android $androidVer)"
                val currentVer = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

                val feedback = UserFeedbackDto(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    message = message.trim(),
                    userName = userName.trim().ifBlank { "مستخدم قبس" },
                    appVersion = currentVer,
                    deviceInfo = fullDeviceModel,
                    createdAt = null
                )

                client.postgrest.from("user_feedbacks").insert(
                    json.encodeToJsonElement(feedback)
                )
                Unit
            }.onFailure { Log.w(TAG, "submitFeedback failed: ${it.message}") }
        }
    }

    // ==========================================
    // سحابة إعدادات وتفضيلات المستخدم (User Settings Sync)
    // ==========================================

    @Serializable
    data class ProfileRow(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
        val email: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("preferences_json") val preferencesJson: String? = null,
        @SerialName("updated_at_millis") val updatedAtMillis: Long = System.currentTimeMillis()
    )

    object Profiles {
        suspend fun syncUserProfile(
            userId: String,
            displayName: String?,
            email: String?,
            avatarUrl: String? = null,
            preferencesJson: String? = null
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val profile = ProfileRow(
                    id = userId,
                    displayName = displayName,
                    email = email,
                    avatarUrl = avatarUrl,
                    preferencesJson = preferencesJson,
                    updatedAtMillis = System.currentTimeMillis()
                )
                client.postgrest.from("profiles").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(profile)))
                ) {
                    onConflict = "id"
                }
                true
            }.onFailure { Log.w(TAG, "syncUserProfile failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getUserProfile(userId: String): ProfileRow? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                client.postgrest.from("profiles").select {
                    filter { eq("id", userId) }
                    limit(1)
                }.decodeListOrEmpty<ProfileRow>().firstOrNull()
            }.onFailure { Log.w(TAG, "getUserProfile failed: ${it.message}") }
                .getOrNull()
        }

        suspend fun getActiveReels(): List<ReelRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                // Fetching all active reels
                client.postgrest.from("reels").select {
                    filter { eq("is_active", true) }
                    order("created_at", Order.DESCENDING)
                }.decodeListOrEmpty<ReelRow>()
            }.onFailure { Log.w(TAG, "getActiveReels failed: ${it.message}") }
                .getOrDefault(emptyList())
        }

        suspend fun saveReel(
            userId: String,
            title: String,
            description: String?,
            videoUrl: String,
            thumbnailUrl: String? = null,
            tags: List<String> = emptyList()
        ): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val reelId = UUID.randomUUID().toString()
                val payload = buildJsonObject {
                    put("id", reelId)
                    put("user_id", userId)
                    put("title", title)
                    put("description", description)
                    put("video_url", videoUrl)
                    put("thumbnail_url", thumbnailUrl)
                    put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
                    put("is_active", true)
                }
                client.postgrest.from("reels").insert(payload)
                Log.i(TAG, "saveReel succeeded for reel $reelId (user: $userId)")
                true
            }.onFailure { Log.w(TAG, "saveReel failed: ${it.message}", it) }
                .getOrDefault(false)
        }

        suspend fun deleteReel(reelId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("reels").delete {
                    filter { eq("id", reelId) }
                }
                true
            }.onFailure { Log.w(TAG, "deleteReel failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun toggleReelActive(reelId: String, isActive: Boolean): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val payload = buildJsonObject {
                    put("is_active", isActive)
                }
                client.postgrest.from("reels").update(payload) {
                    filter { eq("id", reelId) }
                }
                true
            }.onFailure { Log.w(TAG, "toggleReelActive failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun likeReel(reelId: String, userId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val payload = buildJsonObject {
                    put("reel_id", reelId)
                    put("user_id", userId)
                }
                client.postgrest.from("reel_likes").insert(payload)
                true
            }.onFailure { Log.w(TAG, "likeReel failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun unlikeReel(reelId: String, userId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                client.postgrest.from("reel_likes").delete {
                    filter {
                        eq("reel_id", reelId)
                        eq("user_id", userId)
                    }
                }
                true
            }.onFailure { Log.w(TAG, "unlikeReel failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun isReelLiked(reelId: String, userId: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val response = client.postgrest.from("reel_likes").select {
                    filter {
                        eq("reel_id", reelId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                response.data.length > 4 // Check if result list is not empty []
            }.getOrDefault(false)
        }

        suspend fun addReelComment(reelId: String, userId: String, text: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val payload = buildJsonObject {
                    put("reel_id", reelId)
                    put("user_id", userId)
                    put("comment_text", text)
                }
                client.postgrest.from("reel_comments").insert(payload)
                true
            }.onFailure { Log.w(TAG, "addReelComment failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getReelComments(reelId: String): List<ReelCommentRow> {
            if (!isSupabaseAvailable) return emptyList()
            return runCatching {
                client.postgrest.from("reel_comments").select {
                    filter { eq("reel_id", reelId) }
                    order("created_at", Order.DESCENDING)
                }.decodeListOrEmpty<ReelCommentRow>()
            }.onFailure { Log.w(TAG, "getReelComments failed: ${it.message}") }
                .getOrDefault(emptyList())
        }
    }

    @Serializable
    data class UserSettingsRow(
        @SerialName("user_id") val userId: String,
        @SerialName("preferences_json") val preferencesJson: String,
        @SerialName("updated_at_millis") val updatedAtMillis: Long = System.currentTimeMillis()
    )

    object UserSettings {
        suspend fun saveUserSettings(userId: String, jsonPayload: String): Boolean {
            if (!isSupabaseAvailable) return false
            return runCatching {
                val row = UserSettingsRow(
                    userId = userId,
                    preferencesJson = jsonPayload,
                    updatedAtMillis = System.currentTimeMillis()
                )
                client.postgrest.from("user_settings").upsert(
                    JsonArray(listOf(json.encodeToJsonElement(row)))
                ) {
                    onConflict = "user_id"
                }
                true
            }.onFailure { Log.w(TAG, "saveUserSettings failed: ${it.message}") }
                .getOrDefault(false)
        }

        suspend fun getUserSettings(userId: String): UserSettingsRow? {
            if (!isSupabaseAvailable) return null
            return runCatching {
                client.postgrest.from("user_settings").select {
                    filter { eq("user_id", userId) }
                    limit(1)
                }.decodeListOrEmpty<UserSettingsRow>().firstOrNull()
            }.onFailure { Log.w(TAG, "getUserSettings failed: ${it.message}") }
                .getOrNull()
        }
    }
}

/** Best-effort decode that returns an empty list instead of throwing. */
private suspend inline fun <reified T> io.github.jan.supabase.postgrest.result.PostgrestResult
    .decodeListOrEmpty(): List<T> = runCatching { decodeList<T>() }.getOrDefault(emptyList())
