@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.qabas.app

import android.os.Build
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID

object SupabaseServices {
    private const val TAG = "SupabaseServices"

    val isSupabaseAvailable: Boolean
        get() = SupabaseConfig.isConfigured

    val client: SupabaseClient?
        get() = if (SupabaseConfig.isConfigured) SupabaseConfig.client else null

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

        suspend fun getLatestActiveUpdate(): ActiveUpdate? = runCatching {
            val client = client ?: return null
            client.postgrest.from("app_updates")
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<ActiveUpdate>()
        }.onFailure { Log.e(TAG, "getLatestActiveUpdate failed: ${it.message}") }.getOrNull()

        suspend fun ensureUser(
            externalId: String,
            email: String?,
            name: String? = null,
            customId: String? = null,
            accountNumber: String? = null
        ): UserRow? = runCatching {
            val client = client ?: return null
            val cleanEmail = email?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()

            // 1. الحل القانوني أولاً بالبريد (case-insensitive): نفس حساب Google = صف واحد دائماً،
            // حتى لو تغيّر الـ externalId بين الجلسات (مثلاً fallback للبريد قبل اكتمال استعادة الجلسة).
            if (cleanEmail.isNotBlank()) {
                val byEmail = client.postgrest.from("users").select {
                    filter {
                        ilike("email", cleanEmail)
                    }
                    limit(1)
                }.decodeSingleOrNull<UserRow>()
                if (byEmail != null) {
                    if ((byEmail.customId.isNullOrBlank() && !customId.isNullOrBlank()) ||
                        (byEmail.accountNumber.isNullOrBlank() && !accountNumber.isNullOrBlank())) {
                        val updated = byEmail.copy(
                            customId = byEmail.customId?.takeIf { it.isNotBlank() } ?: customId,
                            accountNumber = byEmail.accountNumber?.takeIf { it.isNotBlank() } ?: accountNumber
                        )
                        client.postgrest.from("users").upsert(updated)
                        return updated
                    }
                    return byEmail
                }
            }

            // 2. ثم بالمعرّف الخارجي
            val existing = client.postgrest.from("users").select {
                filter {
                    eq("id", externalId)
                }
                limit(1)
            }.decodeSingleOrNull<UserRow>()

            if (existing != null) {
                if ((existing.customId.isNullOrBlank() && !customId.isNullOrBlank()) ||
                    (existing.accountNumber.isNullOrBlank() && !accountNumber.isNullOrBlank())) {
                    val updated = existing.copy(
                        customId = existing.customId?.takeIf { it.isNotBlank() } ?: customId,
                        accountNumber = existing.accountNumber?.takeIf { it.isNotBlank() } ?: accountNumber
                    )
                    client.postgrest.from("users").upsert(updated)
                    return updated
                }
                return existing
            }

            val newUser = UserRow(
                id = externalId,
                externalId = externalId,
                email = cleanEmail.ifBlank { email },
                name = name ?: cleanEmail.substringBefore("@").ifBlank { email?.substringBefore("@") } ?: "مستخدم قبس",
                type = "مجاني",
                status = "active",
                customId = customId,
                accountNumber = accountNumber,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("users").upsert(newUser)
            newUser
        }.onFailure { Log.e(TAG, "ensureUser failed: ${it.message}") }.getOrNull()

        suspend fun getUserByEmail(email: String): UserRow? = runCatching {
            val client = client ?: return null
            val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
            if (cleanEmail.isBlank()) return null
            client.postgrest.from("users").select {
                filter {
                    ilike("email", cleanEmail)
                }
                limit(1)
            }.decodeSingleOrNull<UserRow>()
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

        suspend fun getNextAccountSequenceFromCloud(): Int = runCatching {
            val client = client ?: return 1
            val users = client.postgrest.from("users").select().decodeList<UserRow>()
            val maxSeq = users.mapNotNull { user ->
                user.accountNumber?.removePrefix("#")?.removePrefix("QBS-")?.toIntOrNull()
            }.maxOrNull() ?: 0
            maxSeq + 1
        }.onFailure { Log.e(TAG, "getNextAccountSequenceFromCloud failed: ${it.message}") }.getOrDefault(1)

        suspend fun saveUser(
            id: String,
            name: String,
            email: String,
            type: String = "مجاني",
            customId: String? = null,
            accountNumber: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val row = UserRow(
                id = id,
                externalId = id,
                name = name,
                email = email,
                type = type,
                customId = customId,
                accountNumber = accountNumber,
                status = "active"
            )
            client.postgrest.from("users").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveUser failed: ${it.message}") }.getOrDefault(false)

        suspend fun getAllUsers(): List<UserRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("users").select().decodeList<UserRow>()
        }.onFailure { Log.e(TAG, "getAllUsers failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun updateUserRole(userId: String, newType: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("users").update(mapOf("type" to newType)) {
                filter {
                    eq("id", userId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "updateUserRole failed: ${it.message}") }.getOrDefault(false)

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
            val row = ProjectRow(
                id = UUID.randomUUID().toString(),
                userId = Auth.getCurrentUserId(),
                externalId = Auth.getCurrentUserId(),
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
            metadata: Map<String, String> = emptyMap()
        ): Boolean = runCatching {
            val client = client ?: return false
            val row = ProjectRow(
                id = id,
                userId = Auth.getCurrentUserId(),
                externalId = Auth.getCurrentUserId(),
                title = title,
                description = description,
                status = status,
                cost = cost,
                tags = tags,
                metadata = metadata,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("projects").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveFullProject failed: ${it.message}") }.getOrDefault(false)

        suspend fun getUserProjects(userId: String? = null): List<ProjectRow> = runCatching {
            val client = client ?: return emptyList()
            val targetId = userId ?: Auth.getCurrentUserId()
            if (targetId.isNullOrBlank()) {
                client.postgrest.from("projects").select().decodeList<ProjectRow>()
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
            client.postgrest.from("projects").delete {
                filter {
                    eq("id", id)
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
            client.postgrest.from("scholar_proposals").upsert(row)
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
                limit(limit)
            }.decodeList<LeaderboardRow>()
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

        // ── Bug Reports Operations ──
        suspend fun getAllBugReports(): List<BugReportRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("dev_bug_reports").select {
                order("created_at", Order.DESCENDING)
            }.decodeList<BugReportRow>()
        }.onFailure { Log.e(TAG, "getAllBugReports failed: ${it.message}") }.getOrDefault(emptyList())

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
            accountNumber: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val payload = mapOf(
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

        suspend fun getChatMessages(userEmail: String): List<ChatMessageRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("dev_chat_messages").select {
                filter {
                    eq("user_email", userEmail.trim().lowercase())
                }
                order("created_at", Order.ASCENDING)
            }.decodeList<ChatMessageRow>()
        }.onFailure { Log.e(TAG, "getChatMessages failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun sendChatMessage(
            userEmail: String,
            message: String,
            sender: String = "developer",
            targetUserId: String? = null,
            accountNumber: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val row = ChatMessageRow(
                id = UUID.randomUUID().toString(),
                userId = targetUserId,
                userEmail = userEmail,
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

        val verifiedRole: StateFlow<String>
            get() = MutableStateFlow(getUserRoleFromMetadata())

        fun getCurrentUserId(): String? = runCatching {
            if (!isSupabaseAvailable) null else client?.auth?.currentUserOrNull()?.id
        }.getOrNull()

        fun getCurrentUserEmail(): String? = runCatching {
            if (!isSupabaseAvailable) null else client?.auth?.currentUserOrNull()?.email
        }.getOrNull()

        fun getCurrentUserMeta(key: String): String? = runCatching {
            if (!isSupabaseAvailable) null
            else client?.auth?.currentUserOrNull()?.userMetadata
                ?.get(key)?.toString()?.trim('"')
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }.getOrNull()

        fun getUserRoleFromMetadata(): String = runCatching {
            if (!isSupabaseAvailable) "user"
            else {
                client?.auth?.currentUserOrNull()?.userMetadata?.get("role")?.toString()?.replace("\"", "") ?: "user"
            }
        }.getOrDefault("user")

        suspend fun refreshVerifiedRole(): String = runCatching {
            getUserRoleFromMetadata()
        }.getOrDefault("user")

        suspend fun isAuthAdmin(): Boolean = runCatching {
            val role = getUserRoleFromMetadata()
            role == "admin" || role == "developer" || role == "owner"
        }.getOrDefault(false)

        suspend fun register(email: String, pass: String): Boolean = runCatching {
            val res = SupabaseRepository.getInstance().register(email, pass)
            res.isSuccess
        }.getOrDefault(false)

        suspend fun login(email: String, pass: String): Boolean = runCatching {
            val res = SupabaseRepository.getInstance().login(email, pass)
            res.isSuccess
        }.getOrDefault(false)

        suspend fun resetPassword(email: String): Boolean = runCatching {
            val client = client ?: return false
            client.auth.resetPasswordForEmail(email)
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

        suspend fun logout(): Boolean = runCatching {
            val client = client ?: return false
            client.auth.signOut()
            true
        }.onFailure { Log.e(TAG, "logout failed: ${it.message}") }.getOrDefault(false)

        suspend fun diagnoseSignInAttempt(email: String, pass: String): AuthDiagnosticsResult {
            if (!isSupabaseAvailable) {
                return AuthDiagnosticsResult(isSuccess = false, hasSession = false, rawErrorMessage = "سحابة Supabase غير مهيأة")
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
                AuthDiagnosticsResult(
                    isSuccess = false,
                    hasSession = false,
                    userId = null,
                    rawErrorMessage = e.message
                )
            }
        }
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
        ): Boolean = runCatching {
            val client = client ?: return false
            val row = ProfileRow(
                id = userId,
                email = email,
                fullName = displayName,
                avatarUrl = avatarUrl,
                role = "user"
            )
            client.postgrest.from("profiles").upsert(row)
            true
        }.onFailure { Log.e(TAG, "syncUserProfile failed: ${it.message}") }.getOrDefault(false)

        suspend fun getUserProfile(userId: String): ProfileRow? = runCatching {
            val client = client ?: return null
            client.postgrest.from("profiles").select {
                filter {
                    eq("id", userId)
                }
                limit(1)
            }.decodeSingleOrNull<ProfileRow>()
        }.onFailure { Log.e(TAG, "getUserProfile failed: ${it.message}") }.getOrNull()

        suspend fun getActiveReels(): List<ReelRow>? = runCatching {
            val client = client ?: return null
            client.postgrest.from("reels").select {
                filter {
                    eq("is_active", true)
                }
                limit(20)
            }.decodeList<ReelRow>()
        }.onFailure { Log.e(TAG, "getActiveReels failed: ${it.message}") }.getOrNull()

        suspend fun saveReel(
            userId: String,
            title: String,
            description: String?,
            videoUrl: String,
            thumbnailUrl: String?,
            tags: List<String>,
            userName: String = "صانع محتوى",
            userAvatar: String? = null
        ): Boolean = runCatching {
            val client = client ?: return false
            val row = ReelRow(
                id = UUID.randomUUID().toString(),
                userId = userId,
                userName = userName.ifBlank { "صانع محتوى" },
                userAvatar = userAvatar,
                title = title,
                description = description,
                videoUrl = videoUrl,
                thumbnailUrl = thumbnailUrl,
                tags = tags,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("reels").insert(row)
            true
        }.onFailure { Log.e(TAG, "saveReel failed: ${it.message}") }.getOrDefault(false)

        suspend fun deleteReel(reelId: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("reels").delete {
                filter {
                    eq("id", reelId)
                }
            }
            true
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

        suspend fun likeReel(reelId: String, userId: String): Boolean = runCatching {
            val client = client ?: return false
            val row = mapOf("reel_id" to reelId, "user_id" to userId)
            client.postgrest.from("reel_likes").upsert(row)
            true
        }.onFailure { Log.e(TAG, "likeReel failed: ${it.message}") }.getOrDefault(false)

        suspend fun unlikeReel(reelId: String, userId: String): Boolean = runCatching {
            val client = client ?: return false
            client.postgrest.from("reel_likes").delete {
                filter {
                    eq("reel_id", reelId)
                    eq("user_id", userId)
                }
            }
            true
        }.onFailure { Log.e(TAG, "unlikeReel failed: ${it.message}") }.getOrDefault(false)

        suspend fun getReelComments(reelId: String): List<ReelCommentRow> = runCatching {
            val client = client ?: return emptyList()
            client.postgrest.from("reel_comments").select {
                filter {
                    eq("reel_id", reelId)
                }
                order("created_at", Order.ASCENDING)
            }.decodeList<ReelCommentRow>()
        }.onFailure { Log.e(TAG, "getReelComments failed: ${it.message}") }.getOrDefault(emptyList())

        suspend fun addReelComment(reelId: String, userId: String, commentText: String): Boolean = runCatching {
            val client = client ?: return false
            val comment = ReelCommentRow(
                id = UUID.randomUUID().toString(),
                reelId = reelId,
                userId = userId,
                commentText = commentText,
                likesCount = 0,
                createdAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("reel_comments").insert(comment)
            true
        }.onFailure { Log.e(TAG, "addReelComment failed: ${it.message}") }.getOrDefault(false)
    }

    // ==========================================
    // UserSettings Operations
    // ==========================================
    object UserSettings {
        suspend fun saveUserSettings(userId: String, jsonPayload: String): Boolean = runCatching {
            val client = client ?: return false
            val row = UserSettingsRow(
                userId = userId,
                preferencesJson = jsonPayload,
                updatedAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("user_settings").upsert(row)
            true
        }.onFailure { Log.e(TAG, "saveUserSettings failed: ${it.message}") }.getOrDefault(false)

        suspend fun getUserSettings(userId: String): UserSettingsRow? = runCatching {
            val client = client ?: return null
            client.postgrest.from("user_settings").select {
                filter {
                    eq("user_id", userId)
                }
                limit(1)
            }.decodeSingleOrNull<UserSettingsRow>()
        }.onFailure { Log.e(TAG, "getUserSettings failed: ${it.message}") }.getOrNull()
    }

    // ==========================================
    // AppReviews Operations
    // ==========================================
    object AppReviews {
        suspend fun getAppReviews(): Result<List<AppReviewDto>> = runCatching {
            if (!isSupabaseAvailable) emptyList()
            else {
                val client = client ?: return@runCatching emptyList()
                client.postgrest.from("app_reviews").select {
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
            val review = AppReviewDto(
                id = UUID.randomUUID().toString(),
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
            val payload = mapOf(
                "report_type" to type,
                "description" to message,
                "user_email" to userName.ifBlank { "user@qabas.app" }
            )
            client.postgrest.from("dev_bug_reports").insert(payload)
            Unit
        }
    }

    // ==========================================
    // Storage Operations
    // ==========================================
    object Storage {
        val DEFAULT_BUCKETS = listOf("media-assets", "audio-tracks", "bug_reports")

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
                val anon = KeyVault.supabaseAnonKey
                if (anon.isBlank()) {
                    Log.e(TAG, "uploadFileWithProgress: blank anon key")
                    return@runCatching null
                }
                val req = Request.Builder()
                    .url("$base/storage/v1/object/$bucketName/$path")
                    .header("apikey", anon)
                    .header("Authorization", "Bearer $anon")
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

        fun getPublicUrl(bucketName: String, path: String): String? {
            val client = client ?: return null
            return client.storage.from(bucketName).publicUrl(path)
        }

        suspend fun listFiles(bucketName: String, path: String = ""): List<RemoteFile> = runCatching {
            val client = client ?: return emptyList()
            val files = client.storage.from(bucketName).list(path)
            files.map { f ->
                RemoteFile(
                    name = f.name,
                    publicUrl = client.storage.from(bucketName).publicUrl(if (path.isEmpty()) f.name else "$path/${f.name}"),
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
        @SerialName("created_at") val createdAt: String? = null
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
        @SerialName("added_at_millis") val addedAtMillis: Long = System.currentTimeMillis()
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
    data class ReelRow(
        val id: String = "",
        @SerialName("user_id") val userId: String = "",
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
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class ReelCommentRow(
        val id: String = "",
        @SerialName("reel_id") val reelId: String = "",
        @SerialName("user_id") val userId: String = "",
        @SerialName("comment_text") val commentText: String = "",
        @SerialName("likes_count") val likesCount: Int = 0,
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

    data class AuthDiagnosticsResult(
        val isSuccess: Boolean = false,
        val hasSession: Boolean = false,
        val userId: String? = null,
        val rawErrorMessage: String? = null
    ) {
        val userFriendlyErrorMessage: String?
            get() = rawErrorMessage
    }
}

@Serializable
data class ActiveUpdate(
    val id: Long = 0,
    @SerialName("version_name") val version_name: String = "",
    @SerialName("version_code") val version_code: Int = 0,
    @SerialName("apk_url") val apk_url: String = "",
    @SerialName("release_notes") val release_notes: String = "",
    @SerialName("file_size_bytes") val file_size_bytes: Long = 0L,
    @SerialName("is_active") val is_active: Boolean = true
) {
    val versionName: String get() = version_name
    val versionCode: Int get() = version_code
    val apkUrl: String get() = apk_url
    val releaseNotes: String get() = release_notes
    val fileSizeBytes: Long get() = file_size_bytes
    val isActive: Boolean get() = is_active
}
