@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.qabas.app

import android.os.Build
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.util.concurrent.TimeUnit
import io.ktor.client.call.body
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

object SupabaseServices {
    private const val TAG = "SupabaseServices"

    val isSupabaseAvailable: Boolean
        get() = SupabaseConfig.isConfigured

    val client: SupabaseClient?
        get() = if (SupabaseConfig.isConfigured) SupabaseConfig.client else null

    var lastInteractionError: String? = null

    // ==========================================
    // Database Operations
    // ==========================================
    object Database {
        suspend fun ping(): Boolean = runCatching {
            if (!isSupabaseAvailable) return false
            val client = client ?: return false
            client.postgrest.from("app_updates").select {
                limit(1)
            }
            true
        }.getOrDefault(false)

        /** Read the single cloud-managed Mishkat reminder record. */
        suspend fun getMishkatReminderContent(): MishkatReminderContent? = runCatching {
            val client = client ?: return null
            client.postgrest.from("mishkat_reminder_content").select {
                filter { eq("id", "global") }
                limit(1)
            }.decodeSingleOrNull<MishkatReminderContent>()
        }.onFailure { Log.e(TAG, "getMishkatReminderContent failed: ${it.message}") }.getOrNull()

        /** Save the reminder only through the authenticated developer RLS policy. */
        suspend fun saveMishkatReminderContent(
            quoteText: String,
            attribution: String,
            videoUrl: String?,
            lessonUrl: String?,
            isEnabled: Boolean
        ): Boolean = runCatching {
            val client = client ?: return false
            val userId = Auth.getCurrentUserId() ?: return false
            val cleanVideoUrl = videoUrl?.trim()?.takeIf { it.isNotBlank() }
            val cleanLessonUrl = lessonUrl?.trim()?.takeIf { it.isNotBlank() }
            val payload = buildJsonObject {
                put("id", "global")
                put("quote_text", quoteText.trim())
                put("attribution", attribution.trim())
                put("video_url", cleanVideoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("lesson_url", cleanLessonUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("is_enabled", isEnabled)
                put("updated_by", userId)
                put("updated_at", java.time.Instant.now().toString())
            }
            client.postgrest.from("mishkat_reminder_content").upsert(payload) {
                onConflict = "id"
            }
            true
        }.onFailure { Log.e(TAG, "saveMishkatReminderContent failed: ${it.message}") }.getOrDefault(false)

        suspend fun getLatestActiveUpdateOrThrow(): ActiveUpdate? {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            return client.postgrest.from("app_updates")
                .select {
                    filter { eq("is_active", true) }
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<ActiveUpdate>()
        }

        suspend fun getLatestActiveUpdate(): ActiveUpdate? =
            runCatching { getLatestActiveUpdateOrThrow() }
                .onFailure { Log.e(TAG, "getLatestActiveUpdate failed: ${it.message}") }
                .getOrNull()

        @Serializable
        data class OnDemandDeltaJob(
            val id: String = "",
            val status: String = "",
            @SerialName("base_version_code") val baseVersionCode: Int = 0,
            @SerialName("target_version_code") val targetVersionCode: Int = 0,
            @SerialName("delta_url") val deltaUrl: String? = null,
            @SerialName("delta_size_bytes") val deltaSizeBytes: Long? = null,
            @SerialName("delta_sha256_hash") val deltaSha256Hash: String? = null,
            @SerialName("error_code") val errorCode: String? = null,
            @SerialName("expires_at") val expiresAt: String? = null
        )

        @Serializable
        data class OnDemandDeltaEnvelope(val job: OnDemandDeltaJob)

        suspend fun requestOnDemandDelta(baseVersionCode: Int, baseSha256: String): OnDemandDeltaJob {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val response = client.functions.invoke(
                function = "qabas-xdelta-request",
                body = buildJsonObject {
                    put("base_version_code", baseVersionCode)
                    put("base_sha256_hash", baseSha256)
                }
            )
            return response.body<OnDemandDeltaEnvelope>().job
        }

        suspend fun getOnDemandDeltaStatus(jobId: String): OnDemandDeltaJob {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val response = client.functions.invoke(
                function = "qabas-xdelta-status",
                body = buildJsonObject { put("job_id", jobId) }
            )
            return response.body<OnDemandDeltaEnvelope>().job
        }

        @Serializable
        data class GitHubTokenAdminResponse(val configured: Boolean = false)

        /** Reads only whether the server has a token; the credential itself is never returned. */
        suspend fun getGitHubTokenConfigured(): Boolean {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val response = client.functions.invoke(
                function = "qabas-xdelta-token-admin",
                body = buildJsonObject { put("action", "status") }
            )
            return response.body<GitHubTokenAdminResponse>().configured
        }

        /** Sends the token once to the owner-only server function; it is not persisted on-device. */
        suspend fun saveGitHubToken(token: String): Boolean {
            require(token.isNotBlank()) { "GitHub token is required" }
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val response = client.functions.invoke(
                function = "qabas-xdelta-token-admin",
                body = buildJsonObject {
                    put("action", "save")
                    put("token", token)
                }
            )
            return response.body<GitHubTokenAdminResponse>().configured
        }

        @Serializable
        data class AdminDeltaDispatchResponse(val accepted: Boolean = false)

        suspend fun dispatchAdminXdelta(targetTag: String, previousTag: String): Boolean {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val response = client.functions.invoke(
                function = "qabas-xdelta-admin-dispatch",
                body = buildJsonObject {
                    put("target_tag", targetTag)
                    put("previous_tag", previousTag)
                }
            )
            return response.body<AdminDeltaDispatchResponse>().accepted
        }

        suspend fun getAppUpdateAuditOrThrow(): List<AppUpdateAudit> {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            return client.postgrest.from("app_update_audit")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<AppUpdateAudit>()
        }

        suspend fun getAppUpdateHistoryOrThrow(): List<ActiveUpdate> {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            return client.postgrest.from("app_updates")
                .select { order("version_code", Order.DESCENDING) }
                .decodeList<ActiveUpdate>()
        }

        suspend fun publishAppUpdate(
            versionName: String,
            versionCode: Int,
            downloadUrl: String,
            releaseNotes: String,
            fileSizeBytes: Long,
            sha256Hash: String?,
            isMandatory: Boolean,
            githubReleaseUrl: String,
            releaseTag: String,
            assetName: String,
            deltaUrl: String? = null,
            deltaSizeBytes: Long = 0L,
            deltaSha256Hash: String? = null,
            baseVersionCode: Int? = null,
            baseSha256Hash: String? = null
        ): ActiveUpdate {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val params = buildJsonObject {
                put("p_version_name", versionName)
                put("p_version_code", versionCode)
                put("p_download_url", downloadUrl)
                put("p_release_notes", releaseNotes)
                put("p_file_size_bytes", fileSizeBytes)
                put("p_sha256_hash", sha256Hash ?: "")
                put("p_is_mandatory", isMandatory)
                put("p_github_release_url", githubReleaseUrl)
                put("p_release_tag", releaseTag)
                put("p_asset_name", assetName)
                put("p_delta_url", deltaUrl ?: "")
                put("p_delta_size_bytes", deltaSizeBytes)
                put("p_delta_sha256_hash", deltaSha256Hash ?: "")
                put("p_base_version_code", baseVersionCode ?: 0)
                put("p_base_sha256_hash", baseSha256Hash ?: "")
            }
            return client.postgrest.rpc("publish_qabas_app_update", params)
                .decodeAs<ActiveUpdate>()
        }

        suspend fun setAppUpdateActive(updateId: String?, isActive: Boolean): Boolean {
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            val params = buildJsonObject {
                put("p_update_id", updateId ?: "")
                put("p_is_active", isActive)
            }
            return client.postgrest.rpc("set_qabas_app_update_active", params)
                .decodeAs<Boolean>()
        }

        /**
         * Checks if the email or custom_id is already taken in the cloud database.
         * Authorized for anonymous access.
         */
        suspend fun checkAccountAvailability(email: String?, customId: String?): Result<AccountAvailabilityResponse> = withContext(Dispatchers.IO) {
            runCatching {
                val client = client ?: throw IllegalStateException("Supabase is not configured")
                val params = buildJsonObject {
                    put("p_email", email ?: "")
                    put("p_custom_id", customId ?: "")
                }
                client.postgrest.rpc("check_account_availability", params).decodeAs<AccountAvailabilityResponse>()
            }
        }

        suspend fun ensureUser(
            externalId: String,
            email: String?,
            name: String? = null,
            customId: String? = null,
            accountNumber: String? = null
        ): UserRow? = runCatching {
            val client = client ?: return null
            val authUser = client.auth.currentUserOrNull()
            val authUid = authUser?.id
                ?: client.auth.currentSessionOrNull()?.user?.id
                ?: return null

            // Auth UID is the only accepted profile key; never fall back to email,
            // an old external_id, or a local identifier.
            if (externalId != authUid) {
                Log.w(TAG, "ensureUser: rejected non-canonical profile identifier")
                return null
            }

            val authEmail = authUser?.email?.trim()?.lowercase(java.util.Locale.ROOT)
                ?: email?.trim()?.lowercase(java.util.Locale.ROOT)
                ?: return null
            if (!email.isNullOrBlank() && !email.trim().equals(authEmail, ignoreCase = true)) {
                Log.w(TAG, "ensureUser: session email does not match requested profile")
                return null
            }

            suspend fun readCanonical(): UserRow? =
                client.postgrest.from("users").select {
                    filter { eq("id", authUid) }
                    limit(1)
                }.decodeSingleOrNull<UserRow>()

            val normalizedInputId = customId?.let { UserIdentifierManager.normalize(it) }?.takeIf { it.isNotBlank() }

            var existing = readCanonical()
            if (existing == null) {
                val payload = UserProfileInsert(
                    id = authUid,
                    email = authEmail,
                    name = name?.trim()?.takeIf { it.isNotBlank() },
                    customId = normalizedInputId ?: UserIdentifierManager.generateUniqueId(name ?: authEmail)
                )
                runCatching {
                    client.postgrest.from("users").insert(payload) {
                        select()
                    }.decodeSingleOrNull<UserRow>()
                }.onFailure {
                    // Concurrent devices may race to create the same UID row.
                    Log.w(TAG, "ensureUser insert raced or failed: ${it.message}")
                }
                existing = readCanonical()
            } else {
                val shouldUpdateEmail = !existing.email.equals(authEmail, ignoreCase = true)
                val shouldUpdateName = !name.isNullOrBlank() &&
                    !name.trim().equals(existing.name?.trim(), ignoreCase = false)
                val targetCustomId = when {
                    !normalizedInputId.isNullOrBlank() &&
                        !normalizedInputId.equals(
                            UserIdentifierManager.normalize(existing.customId ?: ""),
                            ignoreCase = true
                        ) -> normalizedInputId
                    existing.customId.isNullOrBlank() ->
                        UserIdentifierManager.generateUniqueId(existing.name ?: authEmail)
                    else -> null
                }

                if (shouldUpdateEmail || shouldUpdateName || !targetCustomId.isNullOrBlank()) {
                    val updated = client.postgrest.from("users").update({
                        if (shouldUpdateEmail) set("email", authEmail)
                        if (shouldUpdateName) set("name", name!!.trim())
                        if (!targetCustomId.isNullOrBlank()) set("custom_id", targetCustomId)
                    }) {
                        filter { eq("id", authUid) }
                        select()
                    }.decodeSingleOrNull<UserRow>()
                    existing = updated ?: readCanonical()
                }
            }

            if (existing != null && existing.accountNumber.isNullOrBlank()) {
                val newAcc = "QBS-" + String.format(java.util.Locale.ROOT, "%04d", (1000..9999).random())
                runCatching {
                    client.postgrest.from("users").update(mapOf("account_number" to newAcc)) {
                        filter { eq("id", authUid) }
                    }
                    existing = readCanonical()
                }
            }

            existing?.takeIf {
                it.id == authUid &&
                    !it.customId.isNullOrBlank()
            } ?: run {
                Log.e(TAG, "ensureUser: canonical cloud profile is missing identity fields")
                null
            }
        }.onFailure { Log.e(TAG, "ensureUser failed: ${it.message}") }.getOrNull()

        suspend fun getUserByEmail(email: String, authUserId: String? = null): UserRow? = runCatching {
            val client = client ?: return null
            val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
            if (cleanEmail.isBlank()) return null

            // Authenticated profile reads are strictly UID-scoped.
            if (!authUserId.isNullOrBlank()) {
                val currentUid = SupabaseServices.Auth.getCurrentUserId()
                if (currentUid != authUserId) return null
                val byId = client.postgrest.from("users").select {
                    filter { eq("id", authUserId) }
                    limit(1)
                }.decodeSingleOrNull<UserRow>()
                return@runCatching byId?.takeIf {
                    it.id == authUserId &&
                        (it.email.isNullOrBlank() || it.email.equals(cleanEmail, ignoreCase = true))
                }
            }

            val matches = client.postgrest.from("users").select {
                filter { ilike("email", cleanEmail) }
                order("is_eligible", Order.DESCENDING)
                order("created_at", Order.ASCENDING)
                limit(2)
            }.decodeList<UserRow>()
            if (matches.size == 1) matches.first() else null
        }.onFailure { Log.e(TAG, "getUserByEmail failed: ${it.message}") }.getOrNull()

        suspend fun getUserByCustomId(customId: String): UserRow? = runCatching {
            val client = client ?: return null
            val cleanId = customId.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)
            if (cleanId.isBlank()) return null
            client.postgrest.from("users").select {
                filter {
                    eq("custom_id", cleanId)
                }
                limit(1)
            }.decodeSingleOrNull<UserRow>()
        }.onFailure { Log.e(TAG, "getUserByCustomId failed: ${it.message}") }.getOrNull()

        suspend fun getUserByAccountNumber(accountNumber: String): UserRow? = runCatching {
            val client = client ?: return null
            val cleanAcc = accountNumber.trim()
            if (cleanAcc.isBlank()) return null
            val formatted = if (cleanAcc.startsWith("#")) cleanAcc else "#$cleanAcc"
            val altFormatted = formatted.removePrefix("#")
            
            val direct = client.postgrest.from("users").select {
                filter {
                    or {
                        eq("account_number", formatted)
                        eq("account_number", altFormatted)
                        ilike("account_number", "%$altFormatted")
                    }
                }
                limit(1)
            }.decodeSingleOrNull<UserRow>()
            if (direct != null) return direct

            val digitsOnly = cleanAcc.filter { it.isDigit() }
            if (digitsOnly.isNotEmpty()) {
                val seq = digitsOnly.toIntOrNull()
                if (seq != null) {
                    val fullQbs = String.format(java.util.Locale.ROOT, "#QBS-%04d", seq)
                    val qbsNoHash = fullQbs.removePrefix("#")
                    return client.postgrest.from("users").select {
                        filter {
                            or {
                                eq("account_number", fullQbs)
                                eq("account_number", qbsNoHash)
                            }
                        }
                        limit(1)
                    }.decodeSingleOrNull<UserRow>()
                }
            }
            null
        }.onFailure { Log.e(TAG, "getUserByAccountNumber failed: ${it.message}") }.getOrNull()


        /**
         * Atomically updates the signed-in user's editable profile fields and
         * returns the database row only after PostgREST confirms the update.
         * Account number, UID and role remain server-owned.
         */
        suspend fun saveOwnProfile(
            id: String,
            name: String,
            email: String,
            customId: String
        ): Result<UserRow> = withContext(Dispatchers.IO) {
            try {
                val client = client ?: throw IllegalStateException("خدمة السحابة غير متاحة")
                val authUid = SupabaseServices.Auth.getCurrentUserId()
                    ?: throw IllegalStateException("حالة الحساب غير متاحة")
                if (id != authUid) {
                    throw SecurityException("جلسة الحساب لا تطابق الحساب المطلوب تحديثه")
                }

                val authEmail = SupabaseServices.Auth.getCurrentUserEmail()
                    ?.trim()?.lowercase(java.util.Locale.ROOT)
                    ?: throw IllegalStateException("تعذر قراءة بريد الحساب من حالة المصادقة")
                if (!authEmail.equals(email.trim(), ignoreCase = true)) {
                    throw SecurityException("بريد الحساب لا يطابق جلسة المصادقة")
                }

                val cleanName = name.trim()
                val cleanCustomId = UserIdentifierManager.normalize(customId)
                if (cleanName.isBlank()) {
                    throw IllegalArgumentException("اسم الحساب لا يمكن أن يكون فارغاً")
                }
                if (cleanCustomId.length !in 3..25 ||
                    !cleanCustomId.matches(Regex("^[a-z0-9][a-z0-9_-]{2,24}$"))
                ) {
                    throw IllegalArgumentException("صيغة اليوزر غير صحيحة")
                }

                val updated = client.postgrest.from("users").update({
                    set("name", cleanName)
                    set("custom_id", cleanCustomId)
                }) {
                    filter { eq("id", authUid) }
                    select()
                }.decodeSingleOrNull<UserRow>()
                    ?: throw IllegalStateException("لم تُرجع السحابة سجل الحساب بعد التحديث")

                if (updated.id != authUid) {
                    throw SecurityException("رفضت السحابة تحديث سجل الحساب الحالي")
                }
                // Verify the returned cloud row confirms the requested display name too.
                // A successful HTTP response alone is not enough if the row is stale.
                if (updated.name?.trim() != cleanName) {
                    throw IllegalStateException("لم تؤكد السحابة حفظ اسم الحساب المطلوب")
                }
                if (UserIdentifierManager.normalize(updated.customId.orEmpty()) != cleanCustomId) {
                    throw IllegalStateException("لم تؤكد السحابة حفظ اليوزر المطلوب")
                }

                Result.success(updated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "saveOwnProfile failed: ${error.message}", error)
                Result.failure(error)
            }
        }

        suspend fun saveUser(
            id: String,
            name: String,
            email: String,
            type: String = "مجاني",
            customId: String? = null,
            accountNumber: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId() ?: return false
            if (id != authUid) {
                Log.w(TAG, "saveUser: rejected write for a non-authenticated user")
                return false
            }

            val authEmail = SupabaseServices.Auth.getCurrentUserEmail()
                ?.trim()?.lowercase(java.util.Locale.ROOT)
                ?: return false
            if (!authEmail.equals(email.trim(), ignoreCase = true)) return false

            // This compatibility method can update profile fields only.
            // UID, role, status and account number are server-owned.
            val targetCustomId = customId?.let { UserIdentifierManager.normalize(it) }?.takeIf { it.isNotBlank() }
            if (name.isBlank() && targetCustomId.isNullOrBlank()) return true

            val saved = client.postgrest.from("users").update({
                if (name.isNotBlank()) set("name", name.trim())
                if (!targetCustomId.isNullOrBlank()) set("custom_id", targetCustomId)
            }) {
                filter { eq("id", authUid) }
                select()
            }.decodeSingleOrNull<UserRow>()

            saved?.id == authUid &&
                !saved.customId.isNullOrBlank() &&
                (targetCustomId.isNullOrBlank() ||
                    UserIdentifierManager.normalize(saved.customId) == targetCustomId)
        }.onFailure { Log.e(TAG, "saveUser failed: ${it.message}", it) }.getOrDefault(false)

        /** حجز/نبضة ذرّية من الخادم؛ تميّز صراحةً بين التعارض والملف المفقود وتعذر التحقق. */
        suspend fun claimActiveSession(
            userId: String,
            sessionId: String
        ): ActiveSessionClaimResult {
            if (!isSupabaseAvailable || userId.isBlank() || sessionId.isBlank()) {
                return ActiveSessionClaimResult.UNAVAILABLE
            }
            val client = client ?: return ActiveSessionClaimResult.UNAVAILABLE

            return runCatching {
                val params = buildJsonObject {
                    put("p_session_id", sessionId)
                }
                val status = client.postgrest
                    .rpc("claim_active_session", params)
                    .decodeAs<String>()
                parseActiveSessionClaimStatus(status)
            }.onFailure {
                Log.w(TAG, "claimActiveSession RPC failed (${it.javaClass.simpleName})")
            }.getOrDefault(ActiveSessionClaimResult.UNAVAILABLE)
        }

        /** تحرير الجلسة عند تسجيل الخروج (فقط إذا كانت الجلسة الحالية هي صاحبتها). */
        suspend fun releaseActiveSession(userId: String, sessionId: String): Boolean {
            if (!isSupabaseAvailable || userId.isBlank() || sessionId.isBlank()) return false
            val client = client ?: return false

            return runCatching {
                val params = buildJsonObject {
                    put("p_session_id", sessionId)
                }
                client.postgrest
                    .rpc("release_active_session", params)
                    .decodeAs<Boolean>()
            }.onFailure {
                Log.w(TAG, "releaseActiveSession RPC failed (${it.javaClass.simpleName})")
            }.getOrDefault(false)
        }

        suspend fun getAllUsers(): List<UserRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("users").select().decodeList<UserRow>()
        }.onFailure { Log.e(TAG, "getAllUsers failed: ${it.message}") }.getOrDefault(emptyList())

        /** Role changes must pass the server-authorized RPC; never fall back to direct profile writes. */
        suspend fun updateUserRole(userId: String, newType: String): Boolean = runCatching {
            val client = client ?: return false
            val params = buildJsonObject {
                put("p_target_user_id", userId)
                put("p_new_type", newType)
            }
            client.postgrest.rpc("promote_user_role", params)
            true
        }.onFailure { Log.e(TAG, "updateUserRole RPC failed: ${it.message}") }.getOrDefault(false)

        suspend fun updateUserSuspension(userId: String, isSuspended: Boolean): Boolean = runCatching {
            val client = client ?: return false
            val status = if (isSuspended) "suspended" else "active"
            client.postgrest.from("users").update(mapOf("status" to status)) {
                filter {
                    eq("id", userId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "updateUserSuspension failed: ${it.message}") }.getOrDefault(false)

        suspend fun saveProject(
            title: String,
            description: String?,
            status: String = "draft",
            cost: Double = 0.0,
            tags: List<String> = emptyList(),
            metadata: Map<String, String>? = emptyMap()
	        ): ProjectRow? = runCatching {
	            val client = client ?: return null
	            val currentUserId = Auth.getCurrentUserId() ?: return null
	            val row = ProjectRow(
	                id = UUID.randomUUID().toString(),
	                userId = currentUserId,
	                externalId = currentUserId,
                title = title,
                description = description,
                status = status,
                cost = cost,
                tags = tags,
                metadata = metadata ?: emptyMap(),
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("projects").insert(row)
            row
        }.onFailure { Log.e(TAG, "saveProject failed: ${it.message}") }.getOrNull()

        suspend fun saveFullProject(
            id: String,
            title: String,
            description: String?,
            cost: Double = 0.0,
            status: String = "draft",
            tags: List<String> = emptyList(),
            metadata: Map<String, String> = emptyMap(),
            createdAt: String = java.time.Instant.now().toString(),
            updatedAt: String = java.time.Instant.now().toString()
        ): Boolean = runCatching {
            val client = client ?: return false
            val currentUserId = Auth.getCurrentUserId() ?: return false
            val row = ProjectRow(
                id = id,
                userId = currentUserId,
                externalId = currentUserId,
                title = title,
                description = description,
                status = status,
                cost = cost,
                tags = tags,
                metadata = metadata,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            client.postgrest.from("projects").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveFullProject failed: ${it.message}") }.getOrDefault(false)

        suspend fun getUserProjects(userId: String? = null): List<ProjectRow> = runCatching {
            val client = client ?: return emptyList()
            val targetId = userId ?: Auth.getCurrentUserId()
            if (targetId.isNullOrBlank()) {
                return@runCatching emptyList()
            } else {
                client.postgrest.from("projects").select {
                    filter {
                        eq("user_id", targetId)
                    }
                }.decodeList<ProjectRow>()
            }
        }.onFailure { Log.e(TAG, "getUserProjects failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun getFeed(): List<ProjectRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("projects").select {
                order("created_at", Order.DESCENDING)
                limit(20)
            }.decodeList<ProjectRow>()
        }.onFailure { Log.e(TAG, "getFeed failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun deleteProject(id: String): Boolean = runCatching {
            val client = client ?: return false
            val currentUserId = Auth.getCurrentUserId() ?: return false
            client.postgrest.from("projects").delete {
                filter {
                    eq("id", id)
                    eq("user_id", currentUserId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteProject failed: ${it.message}") }.getOrDefault(false)

        suspend fun getHadithPresets(): List<HadithRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("hadith_presets").select {
                filter {
                    eq("is_active", true)
                }
            }.decodeList<HadithRow>()
        }.onFailure { Log.e(TAG, "getHadithPresets failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun getAudioTracks(): List<AudioTrackRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("audio_tracks").select {
                filter {
                    eq("is_active", true)
                }
            }.decodeList<AudioTrackRow>()
        }.onFailure { Log.e(TAG, "getAudioTracks failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun addAudioTrack(track: AudioTrackRow): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("audio_tracks").upsert(track)
            true
        }.onFailure { Log.e(TAG, "addAudioTrack failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteAudioTrack(trackId: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("audio_tracks").delete {
                filter {
                    eq("id", trackId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteAudioTrack failed: ${it.message}") }.getOrDefault(false)

        suspend fun updateAudioTrackLikes(trackId: String, newLikesCount: Int): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("audio_tracks").update(mapOf("likes_count" to newLikesCount)) {
                filter {
                    eq("id", trackId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "updateAudioTrackLikes failed: ${it.message}") }.getOrDefault(false)

        suspend fun getScholarLectures(scholarId: String): List<ScholarLectureRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("scholar_lectures").select {
                filter {
                    eq("scholar_id", scholarId)
                }
            }.decodeList<ScholarLectureRow>()
        }.onFailure { Log.e(TAG, "getScholarLectures failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun saveScholarLecture(row: ScholarLectureRow): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("scholar_lectures").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveScholarLecture failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteScholarLecture(lectureId: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("scholar_lectures").delete {
                filter {
                    eq("id", lectureId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteScholarLecture failed: ${it.message}") }.getOrDefault(false)

        suspend fun getScholarProposals(scholarId: String): List<ScholarProposalRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("scholar_proposals").select {
                filter {
                    eq("scholar_id", scholarId)
                }
            }.decodeList<ScholarProposalRow>()
        }.onFailure { Log.e(TAG, "getScholarProposals failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun saveScholarProposal(row: ScholarProposalRow): Boolean = runCatching {
            val client = client ?: return false
            val uid = Auth.getCurrentUserId()
            val enriched = if (row.userId.isNullOrBlank() && !uid.isNullOrBlank()) {
                row.copy(userId = uid)
            } else {
                row
            }
            client.postgrest.from("scholar_proposals").upsert(enriched)
            true
        }.onFailure { Log.e(TAG, "saveScholarProposal failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteScholarProposal(proposalId: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("scholar_proposals").delete {
                filter {
                    eq("id", proposalId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteScholarProposal failed: ${it.message}") }.getOrDefault(false)

        suspend fun upvoteScholarProposal(id: String, newCount: Int): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("scholar_proposals").update(mapOf("upvotes_count" to newCount)) {
                filter {
                    eq("id", id)
                }
            }
            true
        }.onFailure { Log.e(TAG, "upvoteScholarProposal failed: ${it.message}") }.getOrDefault(false)

        suspend fun getLeaderboard(limit: Long = 50L): List<LeaderboardRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("users").select {
                filter {
                    eq("is_eligible", true)
                }
                order("strikes", Order.DESCENDING)
                limit(limit * 3)
            }.decodeList<LeaderboardRow>()
                // صف واحد لكل اسم معروض حتى لا تتكرر الحسابات المكرّرة في لوحة الشرف
                .distinctBy { it.name.trim().lowercase(java.util.Locale.ROOT).ifBlank { it.userId } }
                .take(limit.toInt())
        }.onFailure { Log.e(TAG, "getLeaderboard failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun upsertLeaderboardScore(
            userId: String,
            name: String,
            points: Long,
            isEligible: Boolean = true,
            avatarUrl: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("users").update(
                mapOf(
                    "name" to name,
                    "strikes" to points.toInt(),
                    "is_eligible" to isEligible
                )
            ) {
                filter {
                    eq("id", userId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "upsertLeaderboardScore failed: ${it.message}") }.getOrDefault(false)

        suspend fun findEmailByCustomId(customId: String): String? = runCatching {
            val client = client ?: return null
            val user = client.postgrest.from("users").select {
                filter {
                    eq("custom_id", customId)
                }
                limit(1)
            }.decodeSingleOrNull<UserRow>()
            user?.email
        }.onFailure { Log.e(TAG, "findEmailByCustomId failed: ${it.message}") }.getOrNull()

        suspend fun findEmailByAccountNumber(accountNumber: String): String? = runCatching {
            getUserByAccountNumber(accountNumber)?.email
        }.onFailure { Log.e(TAG, "findEmailByAccountNumber failed: ${it.message}") }.getOrNull()

        // ── Promo Codes Operations ──
        suspend fun getValidPromoCodes(): List<PromoCodeRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("promo_codes").select {
                filter {
                    eq("is_active", true)
                }
            }.decodeList<PromoCodeRow>()
        }.onFailure { Log.e(TAG, "getValidPromoCodes failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun savePromoCode(code: String, type: String, value: Long): Boolean = runCatching {
            val client = client ?: return false
            val row = PromoCodeRow(
                code = code,
                type = type,
                value = value,
                isActive = true
            )
            client.postgrest.from("promo_codes").upsert(row)
            true
        }.onFailure { Log.e(TAG, "savePromoCode failed: ${it.message}") }.getOrDefault(false)

        suspend fun reservePromoCode(
            code: String,
            idempotencyKey: String? = UUID.randomUUID().toString()
        ): Result<PromoReservationResponse> = runCatching {
            val client = client ?: return Result.failure(IllegalStateException("Supabase not available"))
            val params = buildJsonObject {
                put("p_code", code.trim().uppercase(java.util.Locale.US))
                if (idempotencyKey != null) {
                    put("p_idempotency_key", idempotencyKey)
                }
            }
            client.postgrest.rpc(
                function = "reserve_promo_code",
                parameters = params
            ).decodeAs<PromoReservationResponse>()
        }.onFailure { Log.e(TAG, "reservePromoCode RPC failed: ${it.message}") }

        suspend fun releasePromoCode(reservationToken: String): Result<Boolean> = runCatching {
            val client = client ?: return Result.failure(IllegalStateException("Supabase not available"))
            val params = buildJsonObject {
                put("p_token", reservationToken)
            }
            val res = client.postgrest.rpc(
                function = "release_promo_code",
                parameters = params
            ).decodeAs<PromoReleaseResponse>()
            res.success
        }.onFailure { Log.e(TAG, "releasePromoCode RPC failed: ${it.message}") }

        // ── Transactions & Guards Operations ──
        suspend fun recordTransaction(
            id: String = UUID.randomUUID().toString(),
            userId: String,
            productId: String,
            purchaseToken: String,
            priceAmount: Double = 0.0,
            currency: String = "USD"
        ): Boolean = runCatching {
            val client = client ?: return false
            val payload = mapOf(
                "id" to id,
                "user_id" to userId,
                "product_id" to productId,
                "purchase_token" to purchaseToken,
                "price_amount" to priceAmount.toString(),
                "currency" to currency
            )
            client.postgrest.from("transactions").insert(payload)
            true
        }.onFailure { Log.e(TAG, "recordTransaction failed: ${it.message}") }.getOrDefault(false)

        suspend fun recordContentGuardCheck(
            id: String = "guard_${System.currentTimeMillis()}",
            userId: String,
            snippet: String,
            verdict: String,
            score: Int,
            reason: String
        ): Boolean = runCatching {
            val client = client ?: return false
            val payload = mapOf(
                "id" to id,
                "user_id" to userId,
                "snippet" to snippet,
                "verdict" to verdict,
                "score" to score.toString(),
                "reason" to reason
            )
            client.postgrest.from("content_guard_logs").insert(payload)
            true
        }.onFailure { Log.e(TAG, "recordContentGuardCheck failed: ${it.message}") }.getOrDefault(false)

        private suspend fun withSignedBugReportImage(row: BugReportRow): BugReportRow {
            val path = Storage.normalizeObjectPath("bug_reports", row.imageUrl) ?: return row
            val signedUrl = Storage.createSignedUrl(
                bucketName = "bug_reports",
                path = path
            )
            return if (signedUrl.isNullOrBlank()) row else row.copy(imageUrl = signedUrl)
        }

        // ── Bug Reports Operations ──
        suspend fun getAllBugReports(): List<BugReportRow> = runCatching {
            val client = client ?: return emptyList()
            val rows = client.postgrest.from("dev_bug_reports").select {
                order("created_at", Order.DESCENDING)
            }.decodeList<BugReportRow>()
            rows.map { withSignedBugReportImage(it) }
        }.onFailure { Log.e(TAG, "getAllBugReports failed: ${it.message}") }.getOrDefault(emptyList())


        /**
         * Returns only automatically collected crash telemetry for the protected
         * developer console. RLS remains the server-side authority for support admins.
         */
        suspend fun getCrashTelemetryReports(): List<BugReportRow> {
            if (!AdminGuard.isCrashTelemetryAccessAllowed()) {
                throw SecurityException("Crash telemetry is restricted to approved developers")
            }
            val client = client ?: throw IllegalStateException("Supabase is not configured")
            return client.postgrest.from("dev_bug_reports").select {
                filter {
                    ilike("description", "%report_category=CRASH_TELEMETRY%")
                }
                order("created_at", Order.DESCENDING)
                limit(500)
            }.decodeList<BugReportRow>()
        }

        suspend fun getBugReportsByUser(): List<BugReportRow> = runCatching {
            // Ownership is determined only by the authenticated Supabase UID.
            // Never use user_email as an authorization filter: report inserts do not
            // establish that email as the authenticated identity.
            val activeUserId = Auth.getCurrentUserId() ?: return emptyList()
            val client = client ?: return emptyList()
            val rows = client.postgrest.from("dev_bug_reports").select {
                filter {
                    eq("user_id", activeUserId)
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<BugReportRow>()
            rows.map { withSignedBugReportImage(it) }
        }.onFailure { Log.e(TAG, "getBugReportsByUser failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun recordBugReport(
            userEmail: String,
            screenName: String,
            description: String,
            imageUrl: String,
            reportType: String,
            appVersion: String,
            deviceModel: String,
            id: String = UUID.randomUUID().toString(),
            status: String = "open",
            developerReply: String? = null,
            accountNumber: String? = null,
            userId: String? = Auth.getCurrentUserId()
        ): Boolean = runCatching {
            val client = client ?: return false
            val activeUserId = Auth.getCurrentUserId()
            if (userId != activeUserId) {
                Log.w(TAG, "recordBugReport skipped: report belongs to a different authentication session")
                return false
            }
            val uid = userId
            val payload = mutableMapOf<String, Any>(
                "id" to id,
                "user_email" to userEmail,
                "screen_name" to screenName,
                "description" to description,
                "image_url" to imageUrl,
                "report_type" to reportType,
                "status" to status,
                "app_version" to appVersion,
                "device_info" to deviceModel,
                "developer_reply" to (developerReply ?: "")
            )
            if (!uid.isNullOrBlank()) {
                payload["user_id"] = uid
            }
            client.postgrest.from("dev_bug_reports").insert(payload)
            true
        }.onFailure { Log.e(TAG, "recordBugReport failed: ${it.message}") }.getOrDefault(false)

        suspend fun updateBugReportStatus(
            id: String,
            status: String,
            developerReply: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val payload = mutableMapOf<String, Any>("status" to status)
            if (developerReply != null) payload["developer_reply"] = developerReply
            client.postgrest.from("dev_bug_reports").update(payload) {
                filter {
                    eq("id", id)
                }
            }
            true
        }.onFailure { Log.e(TAG, "updateBugReportStatus failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteBugReport(id: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("dev_bug_reports").delete {
                filter {
                    eq("id", id)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteBugReport failed: ${it.message}") }.getOrDefault(false)

        // ── Chat Messages Operations ──
        suspend fun getAllChatThreads(): Map<String, List<ChatMessageRow>> = runCatching {
            val client = client ?: return emptyMap()
            val list = client.postgrest.from("dev_chat_messages").select {
                order("created_at", Order.ASCENDING)
            }.decodeList<ChatMessageRow>()
            list.groupBy { it.userEmail.lowercase().trim() }
        }.onFailure { Log.e(TAG, "getAllChatThreads failed: ${it.message}") }.getOrDefault(emptyMap())

        suspend fun getChatMessagesForCurrentUser(): List<ChatMessageRow> = runCatching {
            val activeUserId = Auth.getCurrentUserId() ?: return emptyList()
            val client = client ?: return emptyList()
            client.postgrest.from("dev_chat_messages").select {
                filter {
                    eq("user_id", activeUserId)
                }
                order("created_at", Order.ASCENDING)
            }.decodeList<ChatMessageRow>()
        }.onFailure { Log.e(TAG, "getChatMessagesForCurrentUser failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun sendChatMessage(
            userEmail: String,
            message: String,
            sender: String = "developer",
            targetUserId: String? = null,
            accountNumber: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val isUserMessage = sender.equals("user", ignoreCase = true)
            val effectiveUserId = if (isUserMessage) Auth.getCurrentUserId() else targetUserId
            if (effectiveUserId.isNullOrBlank()) return false

            // The sender identity is always taken from the Supabase session, never
            // from the caller-supplied email. Developer replies are attached to the
            // recipient's UID so only that account can read them.
            val effectiveEmail = if (isUserMessage) {
                Auth.getCurrentUserEmail()?.trim()?.lowercase(java.util.Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return false
            } else {
                userEmail.trim().lowercase(java.util.Locale.ROOT)
                    .takeIf { it.isNotBlank() }
                    ?: return false
            }
            val row = ChatMessageRow(
                id = UUID.randomUUID().toString(),
                userId = effectiveUserId,
                userEmail = effectiveEmail,
                accountNumber = accountNumber,
                sender = sender,
                message = message,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("dev_chat_messages").insert(row)
            true
        }.onFailure { Log.e(TAG, "sendChatMessage failed: ${it.message}") }.getOrDefault(false)

        // ── Styles Operations ──
        suspend fun getStyleObjects(userId: String? = null): List<StyleRow> = runCatching {
            val client = client ?: return emptyList()
            if (userId.isNullOrBlank()) {
                client.postgrest.from("styles").select().decodeList<StyleRow>()
            } else {
                client.postgrest.from("styles").select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<StyleRow>()
            }
        }.onFailure { Log.e(TAG, "getStyleObjects failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun saveStyleObject(
            id: String,
            name: String,
            userId: String,
            sourceVideo: String?,
            score: Int,
            tags: List<String>,
            payloadJson: String
        ): Boolean = runCatching {
            val client = client ?: return false
            val parsedPayload = runCatching {
                Json.decodeFromString<JsonObject>(payloadJson)
            }.getOrDefault(JsonObject(emptyMap()))
            val row = StyleRow(
                id = id,
                name = name,
                userId = userId,
                sourceVideo = sourceVideo,
                score = score,
                tags = tags,
                payload = parsedPayload,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("styles").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveStyleObject failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteStyleObject(id: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("styles").delete {
                filter {
                    eq("id", id)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteStyleObject failed: ${it.message}") }.getOrDefault(false)

        suspend fun getMasterStyles(): List<MasterStyleRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("master_styles").select().decodeList<MasterStyleRow>()
        }.onFailure { Log.e(TAG, "getMasterStyles failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun saveMasterStyle(
            id: String,
            name: String,
            description: String?,
            userId: String?,
            compliance: Int,
            payloadJson: String
        ): Boolean = runCatching {
            val client = client ?: return false
            val parsedPayload = runCatching {
                Json.decodeFromString<JsonObject>(payloadJson)
            }.getOrDefault(JsonObject(emptyMap()))
            val row = MasterStyleRow(
                id = id,
                name = name,
                description = description,
                userId = userId,
                compliance = compliance,
                payload = parsedPayload,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("master_styles").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveMasterStyle failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteMasterStyle(id: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("master_styles").delete {
                filter {
                    eq("id", id)
                }
            }
            true
        }.onFailure { Log.e(TAG, "deleteMasterStyle failed: ${it.message}") }.getOrDefault(false)
    }

    // ==========================================
    // Auth Operations
    // ==========================================
    object Auth {
        val currentUser: StateFlow<UserRow?>
            get() = SupabaseRepository.getInstance().currentUser

        private val _verifiedRole = MutableStateFlow("user")
        val verifiedRole: StateFlow<String> = _verifiedRole

        /** Reads only the canonical AuthState; feature screens must not restore sessions. */
        fun getCurrentUserId(): String? =
            SupabaseRepository.getInstance().getCurrentUserId()

        /**
         * Compatibility accessor for older call sites. This is an in-memory snapshot read,
         * not a network refresh or session-restoration attempt.
         */
        @Suppress("UNUSED_PARAMETER")
        suspend fun awaitCurrentUserId(timeoutMillis: Long = 8_000L): String? = getCurrentUserId()

        fun getCurrentUserEmail(): String? =
            SupabaseRepository.getInstance().authState.value.authenticatedSessionSnapshot()?.email

        fun getCurrentUserMeta(key: String): String? = runCatching {
            if (!isSupabaseAvailable) null
            else client?.auth?.currentUserOrNull()?.userMetadata
                ?.get(key)?.toString()?.trim('"')
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }.getOrNull()

        /**
         * قراءة الرتبة الرسمية الموثقة سحابياً من app_metadata في جلسة Supabase Auth
         * (حيث يتم حقن role: "developer" عند ترقية الحساب من لوحة التحكم أو SQL).
         */
        fun getCurrentUserAppRole(): String? =
            SupabaseRepository.getInstance().authState.value.authenticatedSessionSnapshot()?.role

        fun isDeveloperRole(role: String?): Boolean {
            if (role.isNullOrBlank()) return false
            val clean = role.trim().lowercase(java.util.Locale.ROOT)
            return clean in setOf("developer", "admin", "owner", "مطور", "مكور")
        }

        /** Account-level admin role, separate from developer-console authorization. */
        fun isAdminRole(role: String?): Boolean {
            if (role.isNullOrBlank()) return false
            val clean = role.trim().lowercase(java.util.Locale.ROOT)
            return isDeveloperRole(clean) || clean in setOf("special", "خاص")
        }

        /**
         * Authorization roles must come exclusively from server-managed app_metadata.
         * Database profile fields and user_metadata are not trusted role sources.
         */
        fun getUserRoleFromMetadata(): String {
            val role = SupabaseRepository.getInstance().authState.value
                .authenticatedSessionSnapshot()?.role ?: "user"
            _verifiedRole.value = role
            return role
        }

        internal fun readTrustedRoleFromCurrentSession(): String = runCatching {
            if (!isSupabaseAvailable) "user"
            else trustedRoleFromAppMetadata(client?.auth?.currentUserOrNull()?.appMetadata)
        }.getOrDefault("user")

        internal fun publishVerifiedRole(role: String) {
            _verifiedRole.value = role
        }

        fun clearVerifiedRole() {
            _verifiedRole.value = "user"
        }

        suspend fun refreshVerifiedRole(): String =
            SupabaseRepository.getInstance().refreshAuthenticatedRole()

        suspend fun isAuthAdmin(): Boolean = runCatching {
            // "special" is an account category, not an authorization role for admin APIs.
            isDeveloperRole(getUserRoleFromMetadata())
        }.getOrDefault(false)

        suspend fun register(email: String, pass: String, customId: String? = null, displayName: String? = null): Boolean = runCatching {
            val res = SupabaseRepository.getInstance().register(email, pass, customId, displayName)
            res.isSuccess
        }.getOrDefault(false)

        suspend fun login(email: String, pass: String): Boolean = runCatching {
            val res = SupabaseRepository.getInstance().login(email, pass)
            res.isSuccess
        }.getOrDefault(false)

        suspend fun resetPassword(email: String): Boolean = runCatching {
            if (!isSupabaseAvailable) return false
            val client = client ?: return false
            client.auth.resetPasswordForEmail(
                email = email,
                redirectUrl = SupabaseConfig.PASSWORD_RESET_REDIRECT_URL
            )
            true
        }.onFailure { Log.e(TAG, "resetPassword failed: ${it.message}") }.getOrDefault(false)

        suspend fun loginWithGoogle(idToken: String): Boolean = runCatching {
            if (!isSupabaseAvailable) return false
            val client = client ?: return false
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
            }
            true
        }.onFailure { Log.e(TAG, "loginWithGoogle failed: ${it.message}") }.getOrDefault(false)

        suspend fun updatePassword(newPassword: String): Boolean = runCatching {
            val client = client ?: return false
            client.auth.updateUser {
                password = newPassword
            }
            true
        }.onFailure { Log.e(TAG, "updatePassword failed: ${it.message}") }.getOrDefault(false)

        /**
         * Password changes originating from the recovery screen must be backed
         * by a verified Supabase recovery deep-link session.
         */
        suspend fun updateRecoveredPassword(newPassword: String): Boolean = runCatching {
            if (!PasswordRecoveryManager.isRecoveryVerified.value) return false
            val client = client ?: return false
            if (client.auth.currentSessionOrNull() == null) return false
            client.auth.updateUser {
                password = newPassword
            }
            true
        }.onFailure { Log.e(TAG, "updateRecoveredPassword failed: ${it.message}") }.getOrDefault(false)

        /** Route every application logout path through the repository cleanup. */
        suspend fun logout(): Boolean =
            SupabaseRepository.getInstance().logout().isSuccess

        suspend fun diagnoseSignInAttempt(email: String, pass: String): AuthDiagnosticsResult {
            if (!isSupabaseAvailable) {
                return AuthDiagnosticsResult(
                    isSuccess = false,
                    hasSession = false,
                    rawErrorMessage = "سحابة Supabase غير مهيأة",
                    friendlyErrorMessage = "الخدمات السحابية غير مهيأة في هذه النسخة. تواصل مع الدعم."
                )
            }
            return try {
                client?.auth?.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }
                val currentSession = client?.auth?.currentSessionOrNull()
                val uid = currentSession?.user?.id ?: client?.auth?.currentUserOrNull()?.id
                AuthDiagnosticsResult(
                    isSuccess = uid != null,
                    hasSession = currentSession != null,
                    userId = uid,
                    rawErrorMessage = null
                )
            } catch (e: Exception) {
                // التفاصيل الخام (روابط/مضيفون/أكواد) تبقى في السجلات فقط — لا تُعرض للمستخدم
                Log.e(TAG, "diagnoseSignInAttempt failed: ${e.message}", e)
                val safe = describeAuthFailure(e)
                AuthDiagnosticsResult(
                    isSuccess = false,
                    hasSession = false,
                    userId = null,
                    rawErrorMessage = e.message,
                    friendlyErrorMessage = safe.message,
                    isNetworkError = safe.isNetwork
                )
            }
        }

        /** نتيجة تحليل خطأ المصادقة بنسخة آمنة للعرض (بدون أي معلومات تقنية) */
        data class SafeAuthError(val message: String, val isNetwork: Boolean)

        /**
         * يحوّل أي استثناء مصادقة إلى رسالة عربية آمنة للشاشة.
         * لا تُعرض أبداً: الروابط، أسماء المضيفين، أكواد HTTP، أو نصوص المكتبات —
         * كشف مضيف السحابة يكشف بنية المشروع.
         */
        fun describeAuthFailure(throwable: Throwable?): SafeAuthError {
            var current = throwable
            var depth = 0
            while (current != null && depth < 6) {
                val message = current.message.orEmpty()
                when {
                    current is java.net.UnknownHostException ||
                        message.contains("Unable to resolve host", ignoreCase = true) ||
                        message.contains("No address associated", ignoreCase = true) ->
                        return SafeAuthError(
                            "تعذّر الوصول إلى خوادم قبس. تحقق من اتصالك بالإنترنت ثم أعد المحاولة.",
                            isNetwork = true
                        )
                    current is java.net.SocketTimeoutException ||
                        message.contains("timeout", ignoreCase = true) ||
                        message.contains("timed out", ignoreCase = true) ->
                        return SafeAuthError(
                            "استغرق الاتصال وقتاً طويلاً. تحقق من سرعة الشبكة وأعد المحاولة.",
                            isNetwork = true
                        )
                    current is javax.net.ssl.SSLException ||
                        message.contains("Unable to connect", ignoreCase = true) ||
                        message.contains("failed to connect", ignoreCase = true) ||
                        message.contains("Connection refused", ignoreCase = true) ->
                        return SafeAuthError(
                            "تعذّر إتمام الاتصال الآمن بالخادم. تحقق من الشبكة وأعد المحاولة.",
                            isNetwork = true
                        )
                    message.contains("Invalid login credentials", ignoreCase = true) ||
                        message.contains("invalid_grant", ignoreCase = true) ||
                        message.contains("Invalid_credentials", ignoreCase = true) ->
                        return SafeAuthError("بيانات الدخول غير صحيحة. راجع البريد وكلمة المرور.", isNetwork = false)
                    message.contains("Email not confirmed", ignoreCase = true) ->
                        return SafeAuthError("البريد الإلكتروني غير مؤكد بعد. أكّد بريدك ثم أعد المحاولة.", isNetwork = false)
                    message.contains("already registered", ignoreCase = true) ->
                        return SafeAuthError("هذا البريد مسجّل مسبقاً. استخدم شاشة تسجيل الدخول.", isNetwork = false)
                    message.contains("rate limit", ignoreCase = true) ||
                        message.contains("too many", ignoreCase = true) ->
                        return SafeAuthError("محاولات كثيرة خلال وقت قصير. انتظر قليلاً ثم أعد المحاولة.", isNetwork = false)
                }
                current = current.cause
                depth++
            }
            return SafeAuthError(
                "تعذّر تسجيل الدخول حالياً. أعد المحاولة أو تحقق من اتصالك بالإنترنت.",
                isNetwork = false
            )
        }
    }

    fun isSyntheticOrLocalUserId(userId: String?): Boolean {
        if (userId.isNullOrBlank()) return true
        return userId == "local_user" ||
               userId.startsWith("dev_") ||
               userId.startsWith("local_") ||
               userId.startsWith("anon_")
    }

    // ==========================================
    // Profiles Operations
    // ==========================================
    object Profiles {
        suspend fun syncUserProfile(
            userId: String,
            displayName: String?,
            email: String? = null,
            avatarUrl: String? = null,
            preferencesJson: String? = null
        ): Boolean {
            if (isSyntheticOrLocalUserId(userId)) {
                Log.d(TAG, "syncUserProfile skipped for synthetic/local userId: $userId")
                return false
            }
            return runCatching {
                val client = client ?: return false
                val existing = client.postgrest.from("profiles").select {
                    filter { eq("id", userId) }
                    limit(1)
                }.decodeSingleOrNull<ProfileRow>()

                if (existing == null) {
                    // The role is assigned only on insert; updates must never demote admins.
                    client.postgrest.from("profiles").insert(
                        ProfileRow(
                            id = userId,
                            email = email,
                            fullName = displayName,
                            avatarUrl = avatarUrl,
                            role = "user"
                        )
                    )
                } else {
                    val patch = buildJsonObject {
                        email?.let { put("email", it) }
                        displayName?.let { put("full_name", it) }
                        avatarUrl?.let { put("avatar_url", it) }
                    }
                    if (patch.isNotEmpty()) {
                        client.postgrest.from("profiles").update(patch) {
                            filter { eq("id", userId) }
                        }
                    }
                }

                // Confirm the mirror row is readable and contains the requested identity fields.
                val verified = client.postgrest.from("profiles").select {
                    filter { eq("id", userId) }
                    limit(1)
                }.decodeSingleOrNull<ProfileRow>()
                    ?: throw IllegalStateException("Profile mirror row was not returned after sync")
                if (displayName != null && verified.fullName?.trim() != displayName.trim()) {
                    throw IllegalStateException("Profile mirror did not confirm the requested display name")
                }
                if (email != null && !verified.email.equals(email, ignoreCase = true)) {
                    throw IllegalStateException("Profile mirror did not confirm the requested email")
                }
                true
            }.onFailure { err ->
                if (err.message?.contains("permission denied") == true || err.message?.contains("42501") == true) {
                    Log.w(TAG, "syncUserProfile RLS/permission denied for $userId: ${err.message}")
                } else {
                    Log.e(TAG, "syncUserProfile failed: ${err.message}")
                }
            }.getOrDefault(false)
        }

        suspend fun getUserProfile(userId: String): ProfileRow? {
            if (isSyntheticOrLocalUserId(userId)) {
                Log.d(TAG, "getUserProfile skipped for synthetic/local userId: $userId")
                return null
            }
            return runCatching {
                val client = client ?: return null
                client.postgrest.from("profiles").select {
                    filter {
                        eq("id", userId)
                    }
                    limit(1)
                }.decodeSingleOrNull<ProfileRow>()
            }.onFailure { err ->
                if (err.message?.contains("permission denied") == true || err.message?.contains("42501") == true) {
                    Log.w(TAG, "getUserProfile RLS/permission denied for $userId: ${err.message}")
                } else {
                    Log.e(TAG, "getUserProfile failed: ${err.message}")
                }
            }.getOrNull()
        }

        /** Reads only the allowlisted public fields from the community creator view. */
        suspend fun getPublicCreatorProfile(creatorId: String): PublicCreatorProfileRow? {
            if (isSyntheticOrLocalUserId(creatorId)) return null
            return runCatching {
                val client = client ?: return null
                client.postgrest.from("community_profiles_public").select {
                    filter { eq("id", creatorId) }
                    limit(1)
                }.decodeSingleOrNull<PublicCreatorProfileRow>()
            }.onFailure {
                Log.w(TAG, "getPublicCreatorProfile failed")
            }.getOrNull()
        }

        /**
         * Read a public custom handle from the author's public reel snapshot.
         * This avoids querying private user rows belonging to another account.
         */
        suspend fun getCreatorCustomId(creatorId: String): String? = runCatching {
            val client = client ?: return null
            if (creatorId.isBlank()) return null
            client.postgrest.from("reels").select {
                filter { eq("user_id", creatorId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<ReelRow>().firstOrNull()?.userCustomId
                ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        }.onFailure { Log.w(TAG, "getCreatorCustomId failed") }.getOrNull()

        /**
         * جلب مقاطع الريلز النشطة مع تقرير تشخيصي دقيق (زمن الاستجابة، رمز HTTP، ومرحلة الفشل).
         * يمنع الفشل الصامت ويتيح تفكيك الأعطال وإعادة تعيين قاطع الدائرة يدوياً.
         */
        suspend fun getActiveReelsDiagnosed(
            limit: Int = 20,
            offset: Int = 0,
            forceBypassCircuitBreaker: Boolean = false
        ): ReelsFetchDiagnosticResult {
            val startTime = System.currentTimeMillis()
            val endpoint = "${SupabaseConfig.url}/rest/v1/reels"

            if (!isSupabaseAvailable) {
                return ReelsFetchDiagnosticResult(
                    reels = null,
                    isSuccess = false,
                    httpCode = 0,
                    latencyMs = 0L,
                    errorStage = "CONFIG",
                    errorMessage = "إعدادات Supabase غير مهيأة أو المفاتيح مفقودة في البيئة",
                    rawEndpoint = endpoint
                )
            }

            if (!forceBypassCircuitBreaker && !ReelSafetyGuardrails.isSupabaseCallsAllowed()) {
                val remSec = ReelSafetyGuardrails.getCircuitRemainingCooldownSeconds()
                return ReelsFetchDiagnosticResult(
                    reels = null,
                    isSuccess = false,
                    httpCode = 429,
                    latencyMs = 0L,
                    errorStage = "CIRCUIT_BREAKER",
                    errorMessage = "قاطع الدائرة نشط لحماية التطبيق من الاستنزاف (متبقي $remSec ثانية). انقر إعادة المحاولة لإلغائه فورياً",
                    isCircuitBreakerActive = true,
                    remainingCooldownSec = remSec,
                    rawEndpoint = endpoint
                )
            }

            if (forceBypassCircuitBreaker) {
                ReelSafetyGuardrails.forceResetCircuitBreaker()
            }

            val client = client
            if (client == null) {
                return ReelsFetchDiagnosticResult(
                    reels = null,
                    isSuccess = false,
                    httpCode = 0,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorStage = "CONFIG",
                    errorMessage = "عميل Supabase غير متاح",
                    rawEndpoint = endpoint
                )
            }

            return try {
                val response = client.postgrest.from("reels").select {
                    filter {
                        eq("is_active", true)
                    }
                    order("created_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val list = response.decodeList<ReelRow>()
                ReelSafetyGuardrails.recordNetworkSuccess()
                ReelsFetchDiagnosticResult(
                    reels = list,
                    isSuccess = true,
                    httpCode = 200,
                    latencyMs = latency,
                    errorStage = if (list.isEmpty()) "EMPTY" else null,
                    errorMessage = if (list.isEmpty()) "لا توجد مقاطع نشطة مسجلة في السحابة حالياً" else null,
                    rawEndpoint = endpoint
                )
            } catch (t: Throwable) {
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                ReelSafetyGuardrails.recordNetworkFailure()
                Log.e(TAG, "getActiveReelsDiagnosed failed after ${latency}ms: ${t.javaClass.simpleName}: ${t.message}", t)

                val msg = t.message.orEmpty()
                val isTimeout = t is java.util.concurrent.TimeoutException ||
                        t is kotlinx.coroutines.TimeoutCancellationException ||
                        msg.contains("timeout", ignoreCase = true)
                val isAuth = msg.contains("401", ignoreCase = true) ||
                        msg.contains("JWT", ignoreCase = true) ||
                        msg.contains("apikey", ignoreCase = true) ||
                        msg.contains("Unauthorized", ignoreCase = true)
                val isSerialization = t is kotlinx.serialization.SerializationException ||
                        msg.contains("SerializationException") ||
                        msg.contains("UnknownKey")

                val code = when {
                    isAuth -> 401
                    isTimeout -> 408
                    msg.contains("403") -> 403
                    msg.contains("404") -> 404
                    msg.contains("500") -> 500
                    msg.contains("502") || msg.contains("503") || msg.contains("504") -> 503
                    else -> 0
                }

                val stage = when {
                    isSerialization -> "SERIALIZATION"
                    isAuth -> "AUTH"
                    isTimeout -> "TIMEOUT"
                    else -> "NETWORK"
                }

                val sanitizedMsg = msg
                    .replace(Regex("https?://[^\\s'\"]+"), "[🔒 مسار السحابة محمي]")
                    .replace(Regex("Unable to resolve host \"[^\"]+\":"), "تعذر حل عنوان الخادم (DNS):")
                    .take(180)

                val userFriendlyMessage = when (stage) {
                    "SERIALIZATION" -> "خطأ في بنية حقول البيانات (Serialization)"
                    "AUTH" -> "خطأ في صلاحيات مفتاح السحابة (HTTP 401/403)"
                    "TIMEOUT" -> "انتهت مهلة استجابة السحابة (${latency}ms)"
                    else -> if (sanitizedMsg.isNotBlank()) "تعذر الاتصال بالسحابة: $sanitizedMsg" else "تعذر الاتصال بالسحابة (تحقق من الإنترنت)"
                }

                ReelsFetchDiagnosticResult(
                    reels = null,
                    isSuccess = false,
                    httpCode = if (code != 0) code else null,
                    latencyMs = latency,
                    errorStage = stage,
                    errorMessage = userFriendlyMessage,
                    isCircuitBreakerActive = !ReelSafetyGuardrails.isSupabaseCallsAllowed(),
                    remainingCooldownSec = ReelSafetyGuardrails.getCircuitRemainingCooldownSeconds(),
                    rawEndpoint = "[🔒 https://*.supabase.co/rest/v1/reels]"
                )
            }
        }

        /**
         * جلب مقاطع الريلز النشطة — الأحدث أولاً (created_at ثم id لضمان ترتيب ثابت)
         * مع ترقيم صفحات مدى (range) لتمرير لانهائي.
         * تعيد null عند فشل الشبكة/العميل، وقائمة فارغة عند عدم وجود مقاطع — للتمييز بينهما.
         */
        suspend fun getActiveReels(
            limit: Int = 20,
            offset: Int = 0
        ): List<ReelRow>? = getActiveReelsDiagnosed(limit, offset).reels


        /** جلب الفيديوهات المخصصة لوضع الأثر (المحقونة صامتاً) */
        suspend fun getSilentSeedReels(): List<ReelRow>? = runCatching {
            val client = client ?: return null
            client.postgrest.from("reels").select {
                filter {
                    eq("is_active", true)
                    // البحث في قائمة الأوسمة عن الوسم المعتمد
                    cs("tags", listOf("silent_seed"))
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<ReelRow>()
        }.onFailure { Log.e(TAG, "getSilentSeedReels failed: ${it.message}") }.getOrNull()

        suspend fun saveReel(
            userId: String,
            title: String,
            description: String?,
            videoUrl: String,
            thumbnailUrl: String?,
            tags: List<String>,
            userName: String = "صانع محتوى",
            userAvatar: String? = null,
            publishAt: String? = null,
            expiryAt: String? = null,
            quality: String = "high",
            aiAnalysis: String? = null,
            isSilentSeed: Boolean = false,
            publishType: String = "normal",
            aspectRatio: String = "9:16"
        ): ReelRow {
            val client = client ?: run {
                val err = "خدمات سحابة Supabase غير متوفرة أو غير مهيأة"
                Log.e("ReelPublish", err)
                throw IllegalStateException(err)
            }
            val currentAuthUid = SupabaseServices.Auth.getCurrentUserId()
            if (currentAuthUid.isNullOrBlank()) {
                val err = "لا توجد جلسة مصادقة نشطة (auth.uid is null). يلزم تسجيل الدخول لإدراج ريلز."
                Log.e("ReelPublish", err)
                throw IllegalStateException(err)
            }
            val effectiveUserId = currentAuthUid

            if (title.isBlank()) {
                val err = "عنوان الريلز مطلوب ولا يمكن أن يكون فارغاً (title is empty)"
                Log.e("ReelPublish", err)
                throw IllegalArgumentException(err)
            }
            if (videoUrl.isBlank()) {
                val err = "رابط الفيديو مطلوب ولا يمكن أن يكون فارغاً (video_url is empty)"
                Log.e("ReelPublish", err)
                throw IllegalArgumentException(err)
            }
            if (effectiveUserId.isBlank()) {
                val err = "معرف المستخدم مطلوب ولا يمكن أن يكون فارغاً (user_id is empty)"
                Log.e("ReelPublish", err)
                throw IllegalArgumentException(err)
            }

            val creatorCustomId = runCatching {
                client.postgrest.from("users").select {
                    filter { eq("id", effectiveUserId) }
                    limit(1)
                }.decodeSingleOrNull<UserRow>()?.customId?.trim()?.removePrefix("@")
                    ?.takeIf { it.isNotBlank() }
            }.onFailure { Log.w("ReelPublish", "Could not load creator custom_id") }.getOrNull()

            val now = java.time.Instant.now().toString()
            val payload = ReelInsertPayload(
                id = UUID.randomUUID().toString(),
                userId = effectiveUserId,
                userCustomId = creatorCustomId,
                userName = userName.ifBlank { "مستخدم قبس" },
                userAvatar = userAvatar,
                title = title.trim(),
                description = description,
                videoUrl = videoUrl.trim(),
                thumbnailUrl = thumbnailUrl,
                tags = tags,
                publishAt = publishAt ?: now,
                expiryAt = expiryAt,
                quality = quality,
                aiAnalysis = aiAnalysis,
                publishType = publishType,
                isSilentSeed = isSilentSeed,
                metadata = buildJsonObject {
                    put("aspect_ratio", aspectRatio)
                }
            )

            // User-supplied video URLs may contain temporary access tokens; keep them and titles out of Logcat.
            Log.d("ReelPublish", "saveReel: Inserting reel row")

            return try {
                client.postgrest.from("reels").insert(payload) {
                    select()
                }.decodeSingle<ReelRow>()
            } catch (e: Exception) {
                Log.e("ReelPublish", "Supabase reels insert error: ${e.message}", e)
                throw e
            }
        }

        suspend fun deleteReel(reelId: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("reels").delete {
                filter {
                    eq("id", reelId)
                }
            }
            val remaining = client.postgrest.from("reels").select {
                filter {
                    eq("id", reelId)
                }
                limit(1)
            }.decodeList<ReelRow>()
            remaining.isEmpty()
        }.onFailure { Log.e(TAG, "deleteReel failed: ${it.message}") }.getOrDefault(false)

        suspend fun toggleReelActive(reelId: String, isActive: Boolean): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("reels").update(mapOf("is_active" to isActive)) {
                filter {
                    eq("id", reelId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "toggleReelActive failed: ${it.message}") }.getOrDefault(false)

        private suspend fun ensureStarterReel(reelId: String, creatorId: String): Boolean = runCatching {
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return false
            if (authUid != creatorId) return false

            // Seed reels are provisioned once by the database migration under the
            // platform-owned "qabas_system" identity. A viewer must never create
            // a platform reel or become its owner as a side effect of interacting.
            val rows = client.postgrest.from("reels").select {
                filter { eq("id", reelId) }
                limit(1)
            }.decodeList<ReelRow>()
            rows.isNotEmpty()
        }.onFailure { e ->
            Log.w(TAG, "ensureStarterReel failed for $reelId: ${e.message}")
        }.getOrDefault(false)

        suspend fun likeReel(reelId: String, userId: String): Boolean = runCatching {
            lastInteractionError = null
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: run {
                    lastInteractionError = "يجب تسجيل الدخول للإعجاب"
                    return false
                }
            if (authUid != userId) {
                lastInteractionError = "هوية المستخدم لا تطابق جلسة المصادقة الحالية"
                return false
            }

            if (reelId.startsWith("qabas_") && !ensureStarterReel(reelId, authUid)) {
                lastInteractionError = "تعذر تجهيز المقطع السحابي للتفاعل"
                return false
            }

            val row = ReelBookmarkRow(reelId = reelId, userId = authUid)
            client.postgrest.from("reel_likes").upsert(row) {
                onConflict = "reel_id,user_id"
            }
            true
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            Log.e(TAG, "likeReel failed for reel $reelId, user $userId: $msg", e)
            lastInteractionError = msg
        }.getOrDefault(false)

        suspend fun unlikeReel(reelId: String, userId: String): Boolean = runCatching {
            lastInteractionError = null
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: run {
                    lastInteractionError = "يجب تسجيل الدخول"
                    return false
                }
            if (authUid != userId) {
                lastInteractionError = "هوية المستخدم لا تطابق جلسة المصادقة الحالية"
                return false
            }

            client.postgrest.from("reel_likes").delete {
                filter {
                    eq("reel_id", reelId)
                    eq("user_id", authUid)
                }
            }
            true
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            Log.e(TAG, "unlikeReel failed for reel $reelId, user $userId: $msg")
            lastInteractionError = msg
        }.getOrDefault(false)

        suspend fun getReelLikesCount(reelId: String): Int? = runCatching {
            val client = client ?: return null
            client.postgrest.from("reels").select(
                columns = io.github.jan.supabase.postgrest.query.Columns.list("likes_count")
            ) {
                filter { eq("id", reelId) }
                limit(1)
            }.decodeSingleOrNull<ReelLikesCountRow>()?.likesCount
        }.onFailure { e ->
            Log.e(TAG, "getReelLikesCount failed for reel $reelId: ${e.message}")
        }.getOrNull()

        suspend fun getReelComments(reelId: String): List<ReelCommentRow>? = runCatching {
            val client = client ?: return null
            client.postgrest.from("reel_comments").select {
                filter {
                    eq("reel_id", reelId)
                }
                order("created_at", Order.ASCENDING)
            }.decodeList<ReelCommentRow>()
        }.onFailure { Log.e(TAG, "getReelComments failed: ${it.message}") }.getOrNull()

        suspend fun addReelBookmark(reelId: String, userId: String): Boolean = runCatching {
            lastInteractionError = null
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: run {
                    lastInteractionError = "يجب تسجيل الدخول"
                    return false
                }
            if (authUid != userId) {
                lastInteractionError = "هوية المستخدم لا تطابق جلسة المصادقة الحالية"
                return false
            }
            if (reelId.startsWith("qabas_") && !ensureStarterReel(reelId, authUid)) {
                lastInteractionError = "تعذر تجهيز المقطع السحابي للحفظ"
                return false
            }
            val row = ReelBookmarkRow(reelId = reelId, userId = authUid)
            client.postgrest.from("reel_bookmarks").upsert(row) {
                onConflict = "reel_id,user_id"
            }
            true
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            Log.e(TAG, "addReelBookmark failed: $msg")
            lastInteractionError = msg
        }.getOrDefault(false)

        suspend fun removeReelBookmark(reelId: String, userId: String): Boolean = runCatching {
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return false
            if (authUid != userId) return false
            client.postgrest.from("reel_bookmarks").delete {
                filter {
                    eq("reel_id", reelId)
                    eq("user_id", authUid)
                }
            }
            true
        }.onFailure { e -> Log.e(TAG, "removeReelBookmark failed: ${e.message}") }.getOrDefault(false)

        suspend fun addReelComment(
            reelId: String,
            userId: String,
            userName: String,
            commentText: String
        ): ReelCommentRow? = runCatching {
            val client = client ?: return null
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return null
            if (authUid != userId || reelId.isBlank()) {
                Log.w(TAG, "addReelComment rejected an invalid caller identity or reel id")
                return null
            }

            if (reelId.startsWith("qabas_") && !ensureStarterReel(reelId, authUid)) {
                Log.w(TAG, "addReelComment could not ensure cloud starter reel")
                return null
            }

            val safeName = userName.trim().take(60).ifBlank { "مستخدم قبس" }
            val authorCustomId = client.postgrest.from("users").select {
                filter { eq("id", authUid) }
                limit(1)
            }.decodeSingleOrNull<UserRow>()?.customId
                ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }

            val comment = ReelCommentRow(
                id = java.util.UUID.randomUUID().toString(),
                reelId = reelId,
                userId = authUid,
                userName = safeName,
                userCustomId = authorCustomId,
                commentText = commentText,
                likesCount = 0,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("reel_comments").insert(comment) {
                select()
            }.decodeSingleOrNull<ReelCommentRow>()
        }.onFailure { Log.e(TAG, "addReelComment failed: ${it.message}") }.getOrNull()

        /** Update a comment only when the current authenticated user owns the row. */
        suspend fun updateReelComment(
            commentId: String,
            userId: String,
            commentText: String
        ): ReelCommentRow? = runCatching {
            val client = client ?: return null
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return null
            val safeText = commentText.trim()
            if (authUid != userId || commentId.isBlank() || safeText.isBlank()) return null

            client.postgrest.from("reel_comments").update({
                set("comment_text", safeText)
                set("updated_at", java.time.Instant.now().toString())
            }) {
                filter {
                    eq("id", commentId)
                    eq("user_id", authUid)
                }
                select()
            }.decodeSingleOrNull<ReelCommentRow>()
        }.onFailure { Log.e(TAG, "updateReelComment failed: ${it.message}") }.getOrNull()

        /** Delete a comment only when the current authenticated user owns the row. */
        suspend fun deleteReelComment(commentId: String, userId: String): Boolean = runCatching {
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return false
            if (authUid != userId || commentId.isBlank()) return false

            client.postgrest.from("reel_comments").delete {
                filter {
                    eq("id", commentId)
                    eq("user_id", authUid)
                }
                select()
            }.decodeList<ReelCommentRow>().isNotEmpty()
        }.onFailure { Log.e(TAG, "deleteReelComment failed: ${it.message}") }.getOrDefault(false)

        suspend fun getBookmarkedReelIds(userId: String): Set<String>? = runCatching {
            val client = client ?: return null
            val authUid = SupabaseServices.Auth.getCurrentUserId() ?: return null
            if (authUid != userId) return null
            client.postgrest.from("reel_bookmarks").select(columns = io.github.jan.supabase.postgrest.query.Columns.list("reel_id")) {
                filter { eq("user_id", authUid) }
            }.decodeList<ReelBookmarkRow>().mapNotNull { it.reelId }.toSet()
        }.onFailure { Log.e(TAG, "getBookmarkedReelIds failed: ${it.message}") }.getOrNull()

        suspend fun getLikedReelIds(userId: String): Set<String>? = runCatching {
            val client = client ?: return null
            // Bind the query to the canonical AuthState UID; Supabase RLS remains authoritative.
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return null
            if (authUid != userId) return null
            client.postgrest.from("reel_likes").select(columns = io.github.jan.supabase.postgrest.query.Columns.list("reel_id")) {
                filter { eq("user_id", authUid) }
            }.decodeList<ReelBookmarkRow>().mapNotNull { it.reelId }.toSet()
        }.onFailure { Log.w(TAG, "getLikedReelIds failed (${it.javaClass.simpleName})") }.getOrNull()

        /**
         * Returns the signed-in user's activity only. RLS is enforced by Supabase as a
         * second boundary; this method also verifies that the supplied UID is the live UID.
         */
        suspend fun getUserReelActivity(userId: String): ReelActivitySnapshot? = runCatching {
            val client = client ?: return null
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return null
            if (authUid != userId) return null

            val historyRows = client.postgrest.from("reel_history").select(
                columns = io.github.jan.supabase.postgrest.query.Columns.list(
                    "reel_id, watched_duration_sec, created_at"
                )
            ) {
                filter { eq("user_id", authUid) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<ReelHistoryActivityRow>()

            val savedRows = client.postgrest.from("reel_bookmarks").select(
                columns = io.github.jan.supabase.postgrest.query.Columns.list("reel_id, created_at")
            ) {
                filter { eq("user_id", authUid) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<ReelActivityRelationRow>()

            val likedRows = client.postgrest.from("reel_likes").select(
                columns = io.github.jan.supabase.postgrest.query.Columns.list("reel_id, created_at")
            ) {
                filter { eq("user_id", authUid) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<ReelActivityRelationRow>()

            val reelIds = (historyRows.map { it.reelId } +
                savedRows.map { it.reelId } +
                likedRows.map { it.reelId })
                .filter { it.isNotBlank() }
                .distinct()

            if (reelIds.isEmpty()) {
                return ReelActivitySnapshot()
            }

            val reelsById = client.postgrest.from("reels").select {
                filter { isIn("id", reelIds) }
                limit(100)
            }.decodeList<ReelRow>().associateBy { it.id }

            fun toActivityItem(
                reelId: String,
                createdAt: String?,
                watchedDurationSec: Int = 0
            ): ReelHistoryItem? {
                val reel = reelsById[reelId] ?: return null
                val timestamp = createdAt?.let { value ->
                    runCatching {
                        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
                    }.getOrNull()
                } ?: System.currentTimeMillis()
                return ReelHistoryItem(
                    reelId = reel.id,
                    title = reel.title.ifBlank { "ريلز قبس" },
                    userName = reel.userName.ifBlank { "صانع محتوى قبس" },
                    userAvatar = reel.userAvatar,
                    videoUrl = reel.videoUrl,
                    timestamp = timestamp,
                    watchedDurationSec = watchedDurationSec.coerceAtLeast(0),
                    category = reel.tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: "عام",
                    thumbnailUrl = reel.thumbnailUrl
                )
            }

            ReelActivitySnapshot(
                watchHistory = historyRows.mapNotNull {
                    toActivityItem(it.reelId, it.createdAt, it.watchedDurationSec)
                },
                savedReels = savedRows.mapNotNull {
                    toActivityItem(it.reelId, it.createdAt)
                },
                likedReels = likedRows.mapNotNull {
                    toActivityItem(it.reelId, it.createdAt)
                },
                totalWatchSeconds = historyRows.sumOf {
                    it.watchedDurationSec.coerceAtLeast(0).toLong()
                }
            )
        }.onFailure {
            Log.w(TAG, "getUserReelActivity failed (${it.javaClass.simpleName})")
        }.getOrNull()

        suspend fun removeReelWatchHistory(reelId: String, userId: String): Boolean = runCatching {
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return false
            if (authUid != userId || reelId.isBlank()) return false
            client.postgrest.from("reel_history").delete {
                filter {
                    eq("reel_id", reelId)
                    eq("user_id", authUid)
                }
            }
            true
        }.onFailure { Log.e(TAG, "removeReelWatchHistory failed: ${it.message}") }.getOrDefault(false)

        suspend fun clearReelWatchHistory(userId: String): Boolean = runCatching {
            val client = client ?: return false
            val authUid = SupabaseServices.Auth.getCurrentUserId()
                ?: return false
            if (authUid != userId) return false
            client.postgrest.from("reel_history").delete {
                filter { eq("user_id", authUid) }
            }
            true
        }.onFailure { Log.e(TAG, "clearReelWatchHistory failed: ${it.message}") }.getOrDefault(false)

        suspend fun getReelById(reelId: String): ReelRow? = runCatching {
            if (reelId.isBlank()) return null
            val client = client ?: return null
            client.postgrest.from("reels").select {
                filter { eq("id", reelId) }
                limit(1)
            }.decodeList<ReelRow>().firstOrNull()
        }.onFailure { Log.e(TAG, "getReelById failed: ${it.message}") }.getOrNull()

        // ==========================================
        // سجل المشاهدات السحابي (reel_history)
        // ==========================================

        /**
         * يسجل مشاهدة واحدة لكل مستخدم/مقطع عبر معاملة ذرّية في Supabase.
         * القيمة المعادة هي العدد السحابي الحالي، بما في ذلك عند تكرار المشاهدة.
         */
        suspend fun recordReelWatch(reelId: String, userId: String, watchedDurationSec: Int): Int? = runCatching {
            val client = client ?: return null
            val authUid = SupabaseServices.Auth.getCurrentUserId() ?: return null
            if (authUid != userId || reelId.isBlank()) return null

            val params = buildJsonObject {
                put("p_reel_id", reelId)
                put("p_watched_duration_sec", watchedDurationSec.coerceIn(0, 3600))
            }
            client.postgrest.rpc("record_reel_view", params).decodeAs<Int>()
        }.onFailure { Log.e(TAG, "recordReelWatch failed: ${it.message}") }.getOrNull()
    }

    // ==========================================
    // UserSettings Operations
    // ==========================================
    object UserSettings {
        suspend fun saveUserSettings(userId: String, jsonPayload: String): Boolean {
            if (isSyntheticOrLocalUserId(userId)) {
                Log.d(TAG, "saveUserSettings skipped for synthetic/local userId: $userId")
                return false
            }
            return runCatching {
                val client = client ?: return false
                val row = UserSettingsRow(
                    userId = userId,
                    preferencesJson = jsonPayload,
                    updatedAt = java.time.Instant.now().toString()
                )
                client.postgrest.from("user_settings").upsert(row)
                true
            }.onFailure { err ->
                if (err.message?.contains("permission denied") == true || err.message?.contains("42501") == true) {
                    Log.w(TAG, "saveUserSettings RLS/permission denied for $userId: ${err.message}")
                } else {
                    Log.e(TAG, "saveUserSettings failed: ${err.message}")
                }
            }.getOrDefault(false)
        }

        suspend fun getUserSettings(userId: String): UserSettingsRow? {
            if (isSyntheticOrLocalUserId(userId)) {
                Log.d(TAG, "getUserSettings skipped for synthetic/local userId: $userId")
                return null
            }
            return runCatching {
                val client = client ?: return null
                client.postgrest.from("user_settings").select {
                    filter {
                        eq("user_id", userId)
                    }
                    limit(1)
                }.decodeSingleOrNull<UserSettingsRow>()
            }.onFailure { err ->
                if (err.message?.contains("permission denied") == true || err.message?.contains("42501") == true) {
                    Log.w(TAG, "getUserSettings RLS/permission denied for $userId: ${err.message}")
                } else {
                    Log.e(TAG, "getUserSettings failed: ${err.message}")
                }
            }.getOrNull()
        }

        suspend fun saveUserBio(userId: String, bio: String): Boolean {
            val existing = getUserSettings(userId)
            val json = try {
                val currentObj = if (!existing?.preferencesJson.isNullOrBlank()) {
                    org.json.JSONObject(existing!!.preferencesJson)
                } else {
                    org.json.JSONObject()
                }
                currentObj.put("bio", bio)
                currentObj.toString()
            } catch (e: Exception) {
                org.json.JSONObject().put("bio", bio).toString()
            }
            return saveUserSettings(userId, json)
        }

        suspend fun getUserBio(userId: String): String? {
            val existing = getUserSettings(userId) ?: return null
            if (existing.preferencesJson.isBlank()) return null
            return try {
                val obj = org.json.JSONObject(existing.preferencesJson)
                if (obj.has("bio")) obj.optString("bio", "") else null
            } catch (e: Exception) {
                null
            }
        }
    }

    // ==========================================
    // Verified Creators Operations
    // ==========================================
    object VerifiedCreators {
        suspend fun getVerifiedIds(): List<String> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("verified_creators").select()
                .decodeList<VerifiedCreatorRow>()
                .map { it.id }
                .filter { it.isNotBlank() }
        }.onFailure { Log.e(TAG, "getVerifiedIds failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun addVerified(id: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("verified_creators").upsert(mapOf("id" to id))
            true
        }.onFailure { Log.e(TAG, "addVerified failed: ${it.message}") }.getOrDefault(false)

        suspend fun removeVerified(id: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("verified_creators").delete {
                filter {
                    eq("id", id)
                }
            }
            true
        }.onFailure { Log.e(TAG, "removeVerified failed: ${it.message}") }.getOrDefault(false)
    }

    // ==========================================
    // AppReviews Operations
    // ==========================================
    object AppReviews {
        suspend fun getAppReviews(): Result<List<AppReviewDto>> = runCatching {
            if (!isSupabaseAvailable) emptyList()
            else {
                val client = client ?: return@runCatching emptyList()
                client.postgrest.from("app_reviews_public").select {
                    order("created_at", Order.DESCENDING)
                }.decodeList<AppReviewDto>()
            }
        }

        suspend fun markReviewHelpful(reviewId: String, currentCount: Int): Result<Boolean> = runCatching {
            if (!isSupabaseAvailable) return Result.failure(IllegalStateException("Supabase not available"))
            val client = client ?: return Result.failure(IllegalStateException("Supabase client is null"))
            client.postgrest.from("app_reviews").update(mapOf("helpful_count" to (currentCount + 1))) {
                filter {
                    eq("id", reviewId)
                }
            }
            true
        }

        suspend fun submitAppReview(
            rating: Int,
            comment: String,
            authorName: String,
            category: String = "overall"
        ): Result<Unit> = runCatching {
            if (!isSupabaseAvailable) return Result.failure(IllegalStateException("Supabase not available"))
            val client = client ?: return Result.failure(IllegalStateException("Supabase client is null"))
            val uid = Auth.getCurrentUserId()
            val review = AppReviewDto(
                id = UUID.randomUUID().toString(),
                userId = uid,
                authorName = authorName.ifBlank { "مستخدم قبس" },
                rating = rating.coerceIn(1, 5),
                categoryRating = category,
                appVersion = "v1.3.0",
                deviceModel = Build.MODEL ?: "Android Device",
                comment = comment,
                helpfulCount = 0,
                verifiedUser = true,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("app_reviews").insert(review)
            Unit
        }
    }

    // ==========================================
    // Feedback Operations
    // ==========================================
    object Feedback {
        suspend fun submitFeedback(type: String, message: String, userName: String): Result<Unit> = runCatching {
            if (!isSupabaseAvailable) return Result.failure(IllegalStateException("Supabase not available"))
            val client = client ?: return Result.failure(IllegalStateException("Supabase client is null"))
            val uid = Auth.getCurrentUserId()
            val payload = mutableMapOf<String, Any>(
                "report_type" to type,
                "description" to message,
                "user_email" to userName.ifBlank { "user@qabas.app" }
            )
            if (!uid.isNullOrBlank()) {
                payload["user_id"] = uid
            }
            client.postgrest.from("dev_bug_reports").insert(payload)
            Unit
        }
    }

    // ==========================================
    // Storage Operations
    // ==========================================
    object Storage {
        val DEFAULT_BUCKETS = listOf("media-assets", "audio-tracks", "bug_reports")

        /**
         * Return the access JWT for the active Supabase session. Reflection keeps this
         * compatible with the session property naming used by the project's SDK version.
         */
        private fun currentAccessToken(): String? {
            val session = runCatching { client?.auth?.currentSessionOrNull() }.getOrNull() ?: return null
            val tokenMethod = session.javaClass.methods.firstOrNull { method ->
                method.parameterTypes.isEmpty() &&
                    method.name.lowercase().replace("_", "").contains("accesstoken")
            } ?: return null
            return runCatching { tokenMethod.invoke(session) as? String }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        private data class SignedUrlCacheEntry(
            val url: String,
            val expiresAtMillis: Long
        )

        private val signedUrlCache = ConcurrentHashMap<String, SignedUrlCacheEntry>()

        private val rawHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .build()
        }

        /** جسم طلب عدّاد: يبث البايتات المرسلة أثناء الرفع (عداد رقمي حقيقي). */
        private class CountingRequestBody(
            private val file: File,
            private val mime: String,
            private val onProgress: (sent: Long, total: Long) -> Unit
        ) : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                val total = file.length()
                var sent = 0L
                file.inputStream().use { inp ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        onProgress(sent, total)
                    }
                }
            }
        }

        /**
         * رفع متدفق من ملف (بلا تحميل كامل في الذاكرة) مع عدّاد تقدم.
         * يُعيد الرابط العام أو null — والسبب التفصيلي في Log للتشخيص.
         */
        /** Upload a private account-owned file using the active user's JWT and Storage RLS. */
        suspend fun uploadPrivateFileWithProgress(
            bucketName: String,
            path: String,
            file: File,
            contentType: String,
            onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
        ): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists() || file.length() <= 0L) return@runCatching false
                val base = SupabaseConfig.url.trimEnd('/')
                val anon = SupabaseConfig.key
                if (anon.isBlank()) return@runCatching false
                val bearerToken = currentAccessToken() ?: return@runCatching false
                val encodedPath = path.split('/').joinToString("/") {
                    java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                val request = Request.Builder()
                    .url("$base/storage/v1/object/$bucketName/$encodedPath")
                    .header("apikey", anon)
                    .header("Authorization", "Bearer $bearerToken")
                    .header("x-upsert", "false")
                    .post(CountingRequestBody(file, contentType, onProgress))
                    .build()
                rawHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "private upload HTTP ${response.code}: ${response.body?.string().orEmpty().take(200)}")
                        false
                    } else true
                }
            }.onFailure { Log.e(TAG, "private upload failed: ${it.message}", it) }.getOrDefault(false)
        }

        /** Upload a project voice recording to the private, owner-scoped bucket. */
        suspend fun uploadPrivateProjectRecording(
            projectId: String,
            file: File,
            onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
        ): String? {
            val uid = Auth.getCurrentUserId() ?: return null
            val safeProjectId = projectId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
            val extension = file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "m4a"
            val path = "$uid/$safeProjectId/voice_${file.nameWithoutExtension.take(48)}.$extension"
            val contentType = when (extension) {
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                "ogg" -> "audio/ogg"
                "aac" -> "audio/aac"
                else -> "audio/mp4"
            }
            val uploaded = uploadPrivateFileWithProgress(
                bucketName = "project-recordings",
                path = path,
                file = file,
                contentType = contentType,
                onProgress = onProgress
            )
            return path.takeIf { uploaded }
        }

        suspend fun uploadFileWithProgress(
            bucketName: String = "media-assets",
            path: String,
            file: File,
            contentType: String = "video/mp4",
            upsert: Boolean = true,
            onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
        ): String? = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists() || file.length() <= 0) {
                    Log.e(TAG, "uploadFileWithProgress: missing/empty file ${file.absolutePath}")
                    return@runCatching null
                }
                val base = SupabaseConfig.url.trimEnd('/')
                // نفس مفتاح عميل PostgREST/Storage — يمنع انفصال مصدري المفاتيح عن بعضهما
                val anon = SupabaseConfig.key
                if (anon.isBlank()) {
                    Log.e(TAG, "uploadFileWithProgress: blank anon key")
                    return@runCatching null
                }
                val bearerToken = currentAccessToken() ?: anon
                val req = Request.Builder()
                    .url("$base/storage/v1/object/$bucketName/$path")
                    .header("apikey", anon)
                    .header("Authorization", "Bearer $bearerToken")
                    .header("x-upsert", if (upsert) "true" else "false")
                    .post(CountingRequestBody(file, contentType, onProgress))
                    .build()
                rawHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "uploadFileWithProgress HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}")
                        return@runCatching null
                    }
                    "$base/storage/v1/object/public/$bucketName/$path"
                }
            }.onFailure { Log.e(TAG, "uploadFileWithProgress failed: ${it.message}") }.getOrNull()
        }

        suspend fun uploadFile(
            bucketName: String = "media-assets",
            path: String,
            data: ByteArray,
            upsert: Boolean = true
        ): String? = runCatching {
            val client = client ?: return null
            client.storage.from(bucketName).upload(path, data) {
                this.upsert = upsert
            }
            client.storage.from(bucketName).publicUrl(path)
        }.onFailure { Log.e(TAG, "uploadFile failed: ${it.message}") }.getOrNull()

        suspend fun uploadAudioFile(
            bucketName: String = "audio-tracks",
            path: String,
            data: ByteArray
        ): String? = uploadFile(bucketName, path, data, true)

        fun normalizeObjectPath(bucketName: String, value: String?): String? {
            val raw = value?.trim().orEmpty()
            if (raw.isBlank()) return null
            if (!raw.startsWith("http://", ignoreCase = true) &&
                !raw.startsWith("https://", ignoreCase = true)
            ) return raw.removePrefix("/").substringBefore("?").takeIf { it.isNotBlank() }

            val markers = listOf(
                "/storage/v1/object/public/$bucketName/",
                "/storage/v1/object/sign/$bucketName/",
                "/storage/v1/object/authenticated/$bucketName/"
            )
            val marker = markers.firstOrNull { raw.contains(it) } ?: return null
            return raw.substringAfter(marker).substringBefore("?")
                .removePrefix("/")
                .takeIf { it.isNotBlank() }
        }

        suspend fun createSignedUrl(
            bucketName: String,
            path: String,
            expiresIn: kotlin.time.Duration = 15.minutes
        ): String? {
            return try {
                val requesterId = Auth.getCurrentUserId() ?: return null
                val cacheKey = "$requesterId:$bucketName:$path"
                val now = System.currentTimeMillis()
                signedUrlCache[cacheKey]?.takeIf { it.expiresAtMillis > now + 30_000L }?.let {
                    return it.url
                }

                val storageClient = client ?: return null
                val signedUrl = storageClient.storage.from(bucketName).createSignedUrl(
                    path = path,
                    expiresIn = expiresIn
                )
                val cacheLifetime = (expiresIn.inWholeMilliseconds - 30_000L).coerceAtLeast(0L)
                signedUrlCache[cacheKey] = SignedUrlCacheEntry(
                    url = signedUrl,
                    expiresAtMillis = now + cacheLifetime
                )
                signedUrl
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "createSignedUrl failed for $bucketName: ${error.message}")
                null
            }
        }

        /** Download an owner-private project recording through a short-lived signed URL. */
        suspend fun downloadPrivateProjectRecording(
            reference: String,
            destination: File,
            maxBytes: Long = 25L * 1024L * 1024L
        ): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                val marker = "storage://project-recordings/"
                if (!reference.startsWith(marker)) return@runCatching false
                val path = reference.removePrefix(marker).trim('/').takeIf { it.isNotBlank() }
                    ?: return@runCatching false
                if (path.split('/').firstOrNull() != Auth.getCurrentUserId()) return@runCatching false
                if (path.split('/').any { it.isBlank() || it == "." || it == ".." || it.contains('\\') }) {
                    return@runCatching false
                }

                val signedUrl = createSignedUrl("project-recordings", path) ?: return@runCatching false
                destination.parentFile?.mkdirs()
                val temporary = File(destination.parentFile, ".${destination.name}.part")
                rawHttpClient.newCall(Request.Builder().url(signedUrl).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching false
                    val body = response.body ?: return@runCatching false
                    if (body.contentLength() > maxBytes) return@runCatching false
                    body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) return@runCatching false
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (!temporary.exists() || temporary.length() <= 0L) return@runCatching false
                    if (destination.exists()) destination.delete()
                    if (!temporary.renameTo(destination)) return@runCatching false
                }
                true
            }.onFailure {
                if (it is CancellationException) throw it
                Log.e(TAG, "downloadPrivateProjectRecording failed: ${it.message}", it)
            }.getOrDefault(false)
        }

        fun getPublicUrl(bucketName: String, path: String): String? {
            val client = client ?: return null
            return client.storage.from(bucketName).publicUrl(path)
        }

        suspend fun listFiles(bucketName: String, path: String = ""): List<RemoteFile> = runCatching {
            val client = client ?: return emptyList()
            // The default view is scoped to this account because uploads are stored under UID folders.
            val effectivePath = path.ifBlank { Auth.getCurrentUserId().orEmpty() }
            if (effectivePath.isBlank()) return emptyList()
            val files = client.storage.from(bucketName).list(effectivePath)
            files.map { f ->
                val objectPath = "$effectivePath/${f.name}"
                RemoteFile(
                    name = f.name,
                    publicUrl = client.storage.from(bucketName).publicUrl(objectPath),
                    sizeBytes = 0L,
                    createdAt = f.createdAt?.toString()
                )
            }
        }.onFailure { Log.e(TAG, "listFiles failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun deleteFiles(bucketName: String, paths: List<String>): Boolean = runCatching {
            val client = client ?: return false
            client.storage.from(bucketName).delete(paths)
            true
        }.onFailure { Log.e(TAG, "deleteFiles failed: ${it.message}") }.getOrDefault(false)

        /**
         * التحقق من وجود وجاهزية رابط الفيديو في Supabase Storage قبل العرض في المشغل،
         * مع آلية إعادة المحاولة (Retry logic مع التراجع الأسّي) لمعالجة تأخير التحديث والمزامنة في السحابة.
         */
        suspend fun verifyVideoAvailabilityWithRetry(
            videoUrl: String,
            maxRetries: Int = 3,
            initialDelayMs: Long = 1000L
        ): Boolean = withContext(Dispatchers.IO) {
            if (videoUrl.isBlank()) return@withContext false

            // المسارات والروابط المحلية (content, file, android.resource) جاهزة محلياً دائماً
            if (!videoUrl.startsWith("http://", ignoreCase = true) &&
                !videoUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext true
            }

            var currentDelay = initialDelayMs
            for (attempt in 1..maxRetries) {
                val isAvailable = runCatching {
                    val req = Request.Builder()
                        .url(videoUrl)
                        .head() // Head request خفيف للتحقق دون تحميل الفيديو بالكامل
                        .header("User-Agent", ExoPlayerFactory.USER_AGENT)
                        .build()
                    rawHttpClient.newCall(req).execute().use { resp ->
                        val code = resp.code
                        resp.isSuccessful || code == 200 || code == 206 || code == 304
                    }
                }.getOrDefault(false)

                if (isAvailable) {
                    Log.d(TAG, "verifyVideoAvailabilityWithRetry: URL verified successfully on attempt $attempt -> $videoUrl")
                    return@withContext true
                }

                Log.w(TAG, "verifyVideoAvailabilityWithRetry: attempt $attempt/$maxRetries failed for $videoUrl. Retrying in ${currentDelay}ms...")
                if (attempt < maxRetries) {
                    delay(currentDelay)
                    currentDelay *= 2 // Exponential backoff (1s, 2s, 4s...)
                }
            }

            Log.e(TAG, "verifyVideoAvailabilityWithRetry: Video URL unavailable after $maxRetries attempts -> $videoUrl")
            false
        }
    }

    data class RemoteFile(
        val name: String = "",
        val publicUrl: String = "",
        val sizeBytes: Long = 0L,
        val createdAt: String? = null
    )

    // ==========================================
    // Model Definitions
    // ==========================================
    @Serializable
    data class UserProfileInsert(
        val id: String,
        val email: String,
        val name: String? = null,
        @SerialName("custom_id") val customId: String? = null
    )

    @Serializable
    data class UserRow(
        val id: String = "",
        @SerialName("external_id") val externalId: String? = null,
        val email: String? = null,
        val name: String? = null,
        val type: String = "مجاني",
        val status: String = "active",
        val strikes: Int = 0,
        @SerialName("is_eligible") val isEligible: Boolean = true,
        @SerialName("custom_id") val customId: String? = null,
        @SerialName("account_number") val accountNumber: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class UserSettingsRow(
        @SerialName("user_id") val userId: String = "",
        @SerialName("preferences_json") val preferencesJson: String = "{}",
        @SerialName("updated_at") val updatedAt: String? = null
    ) {
        val updatedAtMillis: Long
            get() = runCatching {
                updatedAt?.let {
                    java.time.Instant.parse(it).toEpochMilli()
                }
            }.getOrNull() ?: System.currentTimeMillis()
    }

    @Serializable
    data class ProfileRow(
        val id: String = "",
        val email: String? = null,
        @SerialName("full_name") val fullName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        val role: String = "user",
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class PublicCreatorProfileRow(
        val id: String = "",
        @SerialName("full_name") val fullName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null
    )

    @Serializable
    data class VerifiedCreatorRow(
        val id: String = ""
    )

    @Serializable
    data class ProjectRow(
        val id: String = "",
        @SerialName("user_id") val userId: String? = null,
        @SerialName("external_id") val externalId: String? = null,
        val title: String = "",
        val description: String? = null,
        val status: String = "draft",
        val cost: Double = 0.0,
        val tags: List<String> = emptyList(),
        val metadata: Map<String, String> = emptyMap(),
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class HadithRow(
        val id: String = "",
        val title: String = "",
        val narrator: String = "",
        val text: String = "",
        val status: String = "صحيح",
        val source: String = "",
        @SerialName("theme_bg") val themeBg: String = "black_gold",
        @SerialName("suggested_aspect") val suggestedAspect: String = "9:16",
        val category: String = "أخلاق وآداب",
        @SerialName("is_active") val isActive: Boolean = true
    )

    @Serializable
    data class AudioTrackRow(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val category: String = "تلاوات قرآنية",
        @SerialName("audio_url") val audioUrl: String = "",
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
        val id: String = "",
        @SerialName("scholar_id") val scholarId: String = "",
        @SerialName("scholar_name") val scholarName: String = "",
        val title: String = "",
        val speaker: String = "",
        val url: String = "",
        val type: String = "يوتيوب / مرئي",
        val category: String = "سيرة وعبر",
        val synopsis: String = "",
        @SerialName("duration_approx") val durationApprox: String = "",
        @SerialName("is_official") val isOfficial: Boolean = true,
        val notes: String = "",
        @SerialName("added_at_millis") val addedAtMillis: Long = System.currentTimeMillis(),
        @SerialName("created_by_user_id") val createdByUserId: String? = null
    )

    @Serializable
    data class ScholarProposalRow(
        val id: String = "",
        @SerialName("scholar_id") val scholarId: String = "",
        @SerialName("scholar_name") val scholarName: String = "",
        @SerialName("suggested_title") val suggestedTitle: String = "",
        @SerialName("suggested_speaker") val suggestedSpeaker: String = "",
        @SerialName("suggested_url") val suggestedUrl: String = "",
        @SerialName("suggested_topic") val suggestedTopic: String = "سيرة وعبر",
        val rationale: String = "",
        @SerialName("user_proposer_name") val userProposerName: String = "طالب علم",
        @SerialName("user_id") val userId: String? = null,
        @SerialName("upvotes_count") val upvotesCount: Int = 1,
        val status: String = "قيد المراجعة العلمية",
        @SerialName("created_at_millis") val createdAtMillis: Long = System.currentTimeMillis()
    )

    @Serializable
    data class LeaderboardRow(
        @SerialName("user_id") val userId: String = "",
        val name: String = "",
        val points: Long = 0L,
        @SerialName("is_eligible") val isEligible: Boolean = true,
        @SerialName("avatar_url") val avatarUrl: String? = null
    )

    @Serializable
    data class AppReviewDto(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("user_id") val userId: String? = null,
        @SerialName("author_name") val authorName: String = "مستخدم قبس",
        val rating: Int = 5,
        @SerialName("category_rating") val categoryRating: String = "overall",
        @SerialName("app_version") val appVersion: String = "v1.3.0",
        @SerialName("device_model") val deviceModel: String = "",
        val comment: String = "",
        @SerialName("helpful_count") val helpfulCount: Int = 0,
        @SerialName("verified_user") val verifiedUser: Boolean = true,
        @SerialName("created_at") val createdAt: String = ""
    )

    @Serializable
    data class ReelsFetchDiagnosticResult(
        val reels: List<ReelRow>? = null,
        val isSuccess: Boolean = false,
        val httpCode: Int? = null,
        val latencyMs: Long = 0L,
        val errorStage: String? = null,
        val errorMessage: String? = null,
        val isCircuitBreakerActive: Boolean = false,
        val remainingCooldownSec: Int = 0,
        val rawEndpoint: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    @Serializable
    data class ReelRow(
        val id: String = "",
        @SerialName("user_id") val userId: String = "",
        @SerialName("user_custom_id") val userCustomId: String? = null,
        @SerialName("user_name") val userName: String = "صانع محتوى",
        @SerialName("user_avatar") val userAvatar: String? = null,
        val title: String = "",
        val description: String? = null,
        @SerialName("video_url") val videoUrl: String = "",
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        val tags: List<String> = emptyList(),
        @SerialName("likes_count") val likesCount: Int = 0,
        @SerialName("comments_count") val commentsCount: Int = 0,
        @SerialName("shares_count") val sharesCount: Int = 0,
        @SerialName("views_count") val viewsCount: Int = 0,
        @SerialName("is_active") val isActive: Boolean = true,
        @SerialName("publish_at") val publishAt: String? = null,
        @SerialName("expiry_at") val expiryAt: String? = null,
        @SerialName("quality") val quality: String = "high",
        @SerialName("ai_analysis") val aiAnalysis: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("publish_type") val publishType: String? = "normal",
        @SerialName("is_silent_seed") val isSilentSeed: Boolean = false,
        @SerialName("is_featured_by_dev") val isFeaturedByDev: Boolean = false,
        @SerialName("metadata") val metadata: JsonObject? = null
    )

    @Serializable
    data class ReelInsertPayload(
        val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("user_custom_id") val userCustomId: String? = null,
        @SerialName("user_name") val userName: String = "مستخدم قبس",
        @SerialName("user_avatar") val userAvatar: String? = null,
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
        @SerialName("publish_at") val publishAt: String? = null,
        @SerialName("expiry_at") val expiryAt: String? = null,
        @SerialName("quality") val quality: String = "high",
        @SerialName("ai_analysis") val aiAnalysis: String? = null,
        @SerialName("publish_type") val publishType: String? = "normal",
        @SerialName("is_silent_seed") val isSilentSeed: Boolean = false,
        @SerialName("is_featured_by_dev") val isFeaturedByDev: Boolean = false,
        @SerialName("metadata") val metadata: JsonObject? = null
    )


    @Serializable
    data class ReelCommentRow(
        val id: String = "",
        @SerialName("reel_id") val reelId: String = "",
        @SerialName("user_id") val userId: String = "",
        @SerialName("user_name") val userName: String = "مستخدم قبس",
        @SerialName("user_custom_id") val userCustomId: String? = null,
        @SerialName("comment_text") val commentText: String = "",
        @SerialName("likes_count") val likesCount: Int = 0,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class ReelBookmarkRow(
        @SerialName("reel_id") val reelId: String,
        @SerialName("user_id") val userId: String
    )

    @Serializable
    data class ReelLikesCountRow(
        @SerialName("likes_count") val likesCount: Int = 0
    )

    @Serializable
    data class ReelHistoryActivityRow(
        @SerialName("reel_id") val reelId: String,
        @SerialName("watched_duration_sec") val watchedDurationSec: Int = 0,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class ReelActivityRelationRow(
        @SerialName("reel_id") val reelId: String,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class ChatMessageRow(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("user_id") val userId: String? = null,
        @SerialName("user_email") val userEmail: String = "anonymous",
        @SerialName("account_number") val accountNumber: String? = null,
        val sender: String = "user",
        val message: String = "",
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class BugReportRow(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("user_id") val userId: String? = null,
        @SerialName("user_email") val userEmail: String = "user@qabas.app",
        @SerialName("screen_name") val screenName: String = "",
        val description: String = "",
        @SerialName("image_url") val imageUrl: String = "",
        @SerialName("report_type") val reportType: String = "BUG",
        val status: String = "open",
        @SerialName("app_version") val appVersion: String = "",
        @SerialName("device_info") val deviceInfo: String = "",
        @SerialName("developer_reply") val developerReply: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class StyleRow(
        val id: String = "",
        val name: String = "",
        @SerialName("user_id") val userId: String? = null,
        @SerialName("source_video") val sourceVideo: String? = null,
        val score: Int = 0,
        val tags: List<String> = emptyList(),
        val payload: JsonObject = JsonObject(emptyMap()),
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class MasterStyleRow(
        val id: String = "",
        val name: String = "",
        val description: String? = null,
        @SerialName("user_id") val userId: String? = null,
        val compliance: Int = 95,
        val payload: JsonObject = JsonObject(emptyMap()),
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class PromoCodeRow(
        val code: String = "",
        val type: String = "PROMO",
        val value: Long = 0L,
        @SerialName("is_active") val isActive: Boolean = true
    )

    @Serializable
    data class PromoReservationResponse(
        val success: Boolean = false,
        @SerialName("is_retry") val isRetry: Boolean = false,
        @SerialName("reservation_token") val reservationToken: String? = null,
        val code: String? = null,
        @SerialName("discount_type") val discountType: String? = null,
        @SerialName("discount_value") val discountValue: Long = 0L,
        @SerialName("reserved_until") val reservedUntil: String? = null,
        @SerialName("error_code") val errorCode: String? = null,
        val message: String? = null
    )

    @Serializable
    data class PromoReleaseResponse(
        val success: Boolean = false,
        val message: String? = null
    )

    data class AuthDiagnosticsResult(
        val isSuccess: Boolean = false,
        val hasSession: Boolean = false,
        val userId: String? = null,
        /** للسجلات والتشخيص فقط — لا يُعرض على الشاشة */
        val rawErrorMessage: String? = null,
        /** نص عربي آمن للعرض: بلا روابط ولا أسماء مضيفين ولا أكواد */
        val friendlyErrorMessage: String? = null,
        /** هل سبب الفشل اتصال بالشبكة؟ (لعرض زر فحص الاتصال) */
        val isNetworkError: Boolean = false
    ) {
        val userFriendlyErrorMessage: String?
            get() = friendlyErrorMessage
    }
}

@Serializable
data class ActiveUpdate(
    val id: String = "",
    @SerialName("version_name") val version_name: String = "",
    @SerialName("version_code") val version_code: Int = 0,
    @SerialName("download_url") val download_url: String = "",
    @SerialName("release_notes") val release_notes: String = "",
    @SerialName("file_size_bytes") val file_size_bytes: Long = 0L,
    @SerialName("is_active") val is_active: Boolean = true,
    @SerialName("is_mandatory") val is_mandatory: Boolean = false,
    @SerialName("sha256_hash") val sha256_hash: String? = null,
    @SerialName("github_release_url") val github_release_url: String? = null,
    @SerialName("release_tag") val release_tag: String? = null,
    @SerialName("asset_name") val asset_name: String? = null,
    @SerialName("released_at") val released_at: String? = null,
    @SerialName("delta_url") val delta_url: String? = null,
    @SerialName("delta_size_bytes") val delta_size_bytes: Long = 0L,
    @SerialName("delta_sha256_hash") val delta_sha256_hash: String? = null,
    @SerialName("base_version_code") val base_version_code: Int? = null,
    @SerialName("base_sha256_hash") val base_sha256_hash: String? = null
) {
    val versionName: String get() = version_name
    val versionCode: Int get() = version_code
    val apkUrl: String get() = download_url
    val downloadUrl: String get() = download_url
    val releaseNotes: String get() = release_notes
    val fileSizeBytes: Long get() = file_size_bytes
    val isActive: Boolean get() = is_active
    val isMandatory: Boolean get() = is_mandatory
    val sha256Hash: String? get() = sha256_hash
    val githubReleaseUrl: String? get() = github_release_url
    val deltaUrl: String? get() = delta_url
    val deltaSizeBytes: Long get() = delta_size_bytes
    val deltaSha256Hash: String? get() = delta_sha256_hash
    val baseVersionCode: Int? get() = base_version_code
    val baseSha256Hash: String? get() = base_sha256_hash
}
@Serializable
data class AppUpdateAudit(
    val id: Long = 0,
    @SerialName("actor_id") val actorId: String = "",
    @SerialName("actor_role") val actorRole: String = "",
    val action: String = "",
    @SerialName("old_version_code") val oldVersionCode: Int? = null,
    @SerialName("new_version_code") val newVersionCode: Int? = null,
    @SerialName("version_name") val versionName: String? = null,
    @SerialName("is_mandatory") val isMandatory: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class AccountAvailabilityResponse(
    @SerialName("email_taken") val emailTaken: Boolean,
    @SerialName("custom_id_taken") val customIdTaken: Boolean
)
