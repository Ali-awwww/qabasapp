package com.qabas.app

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * مدير المتابعة الموحّد لملفات صنّاع المحتوى.
 *
 * يربط بطاقة المنشئ بالملف الشخصي الحقيقي للمشاهد، ويوحّد حالة المتابعة بين
 * البطاقة وتبويب «متابعون» والملف الشخصي، مع مزامنة سحابية اختيارية لا تكسر
 * العمل المحلي عند غياب جدول `creator_follows`.
 */
object CreatorFollowManager {
    private const val TAG = "CreatorFollowManager"
    private const val LEGACY_PREFS = "qabas_creator_follows"
    private const val LEGACY_PREFIX = "follow_"
    private const val FOLLOW_IDS_KEY = "followed_creators"
    private const val METADATA_KEY = "followed_creator_metadata_v1"
    private const val CLOUD_STATE_PREFIX = "creator_follow_cloud_"
    private const val TABLE = "creator_follows"

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var followTableUnavailableUntilMs = 0L
    private const val FOLLOW_TABLE_RETRY_MS = 600_000L

    data class ViewerIdentity(
        val id: String,
        val displayName: String,
        val email: String? = null,
        val avatarUrl: String? = null
    )

    data class FollowedCreator(
        val id: String,
        val displayName: String,
        val avatarUrl: String? = null,
        val followedAt: Long = System.currentTimeMillis()
    )

    data class FollowerCount(
        val value: Int,
        val isExact: Boolean
    )

    data class CreatorCommunity(
        val creatorId: String,
        val displayName: String,
        val avatarUrl: String?,
        val isOwnProfile: Boolean,
        val isFollowing: Boolean,
        val followers: FollowerCount,
        val followingCount: Int,
        val cloudAvailable: Boolean,
        val customId: String? = null
    )

    @Serializable
    data class CreatorFollowRow(
        @SerialName("follower_id") val followerId: String,
        @SerialName("creator_id") val creatorId: String,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    data class CreatorFollowRelationRow(
        @SerialName("creator_id") val creatorId: String,
        @SerialName("created_at") val createdAt: String? = null
    )

    // ───────── منطق نقي قابل للاختبار ─────────

    fun normalizedIdentity(value: String?): String? {
        var candidate = value?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        if (candidate.startsWith("mailto:", ignoreCase = true)) {
            candidate = candidate.substring(7)
        }
        if (candidate.startsWith("@")) candidate = candidate.drop(1)
        return candidate.lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }

    fun identityTokens(vararg values: String?): Set<String> =
        values.mapNotNull { normalizedIdentity(it) }.toSet()

    fun identitiesMatch(
        creatorId: String?,
        creatorName: String?,
        viewerId: String?,
        viewerName: String?,
        viewerEmail: String?
    ): Boolean {
        val creatorTokens = identityTokens(creatorId, creatorName)
        if (creatorTokens.isEmpty()) return false
        val viewerTokens = identityTokens(viewerId, viewerName, viewerEmail)
        return creatorTokens.intersect(viewerTokens).isNotEmpty()
    }

    /**
     * مطابقة صارمة للحساب نفسه: تتطلب تطابق المعرّف أو البريد/اسم المستخدم،
     * ولا تكتفي بتشابه الاسم المعروض وحده لأن الأسماء قد تتكرر.
     */
    fun isSameAccount(
        creatorId: String?,
        creatorName: String?,
        viewerId: String?,
        viewerName: String?,
        viewerEmail: String?
    ): Boolean {
        val creatorIdToken = normalizedIdentity(creatorId)
        val creatorNameToken = normalizedIdentity(creatorName)
        val viewerIdToken = normalizedIdentity(viewerId)
        val viewerEmailToken = normalizedIdentity(viewerEmail)
        if (creatorIdToken != null && (creatorIdToken == viewerIdToken || creatorIdToken == viewerEmailToken)) {
            return true
        }
        if (creatorNameToken != null && (creatorNameToken == viewerIdToken || creatorNameToken == viewerEmailToken)) {
            return true
        }
        return false
    }

    fun sameCanonicalUserId(firstId: String?, secondId: String?): Boolean {
        val firstUid = firstId?.trim().orEmpty()
        val secondUid = secondId?.trim().orEmpty()
        return firstUid.isNotEmpty() && secondUid.isNotEmpty() && firstUid == secondUid
    }

    fun userScopedPreferenceKey(baseKey: String, userId: String?): String? {
        val ownerUid = userId?.trim().orEmpty()
        if (baseKey.isBlank() || ownerUid.isBlank()) return null
        return "$baseKey:$ownerUid"
    }

    /**
     * مطابقة صارمة لسجل المنشئ نفسه عبر المعرّف أو اسم المستخدم، دون دمج
     * الأسماء المعروضة المتشابهة الخاصة بأشخاص مختلفين.
     */
    fun isSameCreatorRecord(
        firstId: String?,
        firstName: String?,
        secondId: String?,
        secondName: String?
    ): Boolean {
        val firstIdToken = normalizedIdentity(firstId)
        val secondIdToken = normalizedIdentity(secondId)
        val firstNameToken = normalizedIdentity(firstName)
        val secondNameToken = normalizedIdentity(secondName)
        if (firstIdToken != null && firstIdToken == secondIdToken) return true
        if (firstIdToken != null && (firstIdToken == secondNameToken)) return true
        if (secondIdToken != null && (secondIdToken == firstNameToken)) return true
        return false
    }

    /**
     * قيمة عرض أولية مستقرة لنفس المنشئ عند غياب العدّ السحابي.
     * ليست رقماً حقيقياً، لذلك تُعرض دائماً مع وسم «تقدير أولي».
     */
    fun estimatedBaselineFollowers(seed: String): Int {
        val stable = seed.hashCode() and Int.MAX_VALUE
        return 96 + (stable % 96)
    }

    fun withFollowedId(ids: Set<String>, creatorId: String): Set<String> =
        (ids + creatorId).filter { it.isNotBlank() }.toSet()

    fun withoutFollowedId(ids: Set<String>, creatorId: String): Set<String> =
        ids.filter { it.isNotBlank() && !it.equals(creatorId, ignoreCase = true) }.toSet()

    /**
     * يميّز غياب جدول المتابعة عن عطل الشبكة الحقيقي حتى لا نُسقط قاطع
     * الدائرة الخاص بالريلز بسبب مخطط سحابي غير مُثبّت بعد.
     */
    fun isMissingFollowTable(message: String?): Boolean {
        val text = message?.lowercase(Locale.ROOT).orEmpty()
        if (text.isBlank()) return false
        return text.contains("pgrst205") ||
            text.contains("could not find the table") ||
            text.contains("relation") && text.contains("does not exist") ||
            text.contains("schema cache") ||
            text.contains(" 404") ||
            text.contains("(404") ||
            text.contains("not found") && text.contains("creator_follows")
    }

    // ───────── الهوية المحلية ─────────

    fun currentViewer(context: Context): ViewerIdentity {
        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val authId = SupabaseServices.Auth.getCurrentUserId()
        val authEmail = SupabaseServices.Auth.getCurrentUserEmail()
        val storedEmail = appPrefs.getString("user_email", null)?.takeIf { it.isNotBlank() }
        val displayName = appPrefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
            ?: appPrefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
            ?: SupabaseServices.Auth.getCurrentUserMeta("full_name")
            ?: "صانع محتوى قبس"
        val avatar = appPrefs.getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }
            ?: SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
            ?: SupabaseServices.Auth.getCurrentUserMeta("picture")

        // A social follower must be a Supabase Auth user. Device IDs, email
        // addresses, and account numbers are descriptive/cached values only.
        return ViewerIdentity(
            id = authId.orEmpty(),
            displayName = displayName,
            email = authEmail ?: storedEmail,
            avatarUrl = avatar
        )
    }

    // ───────── الحالة المحلية ─────────

    fun followedIds(context: Context): Set<String> {
        val userId = SupabaseServices.Auth.getCurrentUserId() ?: return emptySet()
        return followedIdsForUser(context, userId)
    }

    private fun followedIdsForUser(context: Context, userId: String): Set<String> {
        val key = userScopedPreferenceKey(FOLLOW_IDS_KEY, userId) ?: return emptySet()
        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return appPrefs.getStringSet(key, emptySet()).orEmpty()
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isFollowing(context: Context, creatorId: String): Boolean {
        if (creatorId.isBlank()) return false
        return followedIds(context).any { it.equals(creatorId, ignoreCase = true) }
    }

    fun followedCreators(context: Context): List<FollowedCreator> {
        val userId = SupabaseServices.Auth.getCurrentUserId() ?: return emptyList()
        return followedCreatorsForUser(context, userId)
    }

    private fun followedCreatorsForUser(context: Context, userId: String): List<FollowedCreator> {
        val key = userScopedPreferenceKey(METADATA_KEY, userId) ?: return emptyList()
        val raw = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return followedIdsForUser(context, userId).map {
            FollowedCreator(id = it, displayName = it)
        }
        return runCatching {
            val items = JSONObject(raw).optJSONArray("items") ?: JSONArray()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FollowedCreator(
                    id = id,
                    displayName = item.optString("name").takeIf { it.isNotBlank() } ?: id,
                    avatarUrl = item.optString("avatar").takeIf { it.isNotBlank() },
                    followedAt = item.optLong("followed_at", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveFollowedCreators(context: Context, userId: String, creators: List<FollowedCreator>) {
        val key = userScopedPreferenceKey(METADATA_KEY, userId) ?: return
        val payload = JSONObject().put("items", JSONArray().apply {
            creators.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.displayName)
                        .put("avatar", it.avatarUrl.orEmpty())
                        .put("followed_at", it.followedAt)
                )
            }
        }).toString()
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, payload).apply()
    }

    private fun cloudState(context: Context, userId: String, creatorId: String): String? {
        val key = userScopedPreferenceKey(CLOUD_STATE_PREFIX + creatorId, userId) ?: return null
        return context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
    }

    private fun saveCloudState(context: Context, userId: String, creatorId: String, state: String) {
        val key = userScopedPreferenceKey(CLOUD_STATE_PREFIX + creatorId, userId) ?: return
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, state).apply()
    }

    suspend fun refreshFollowedCreatorsFromCloud(context: Context, userId: String): List<FollowedCreator>? =
        withContext(Dispatchers.IO) {
            if (!cloudReady() || userId.isBlank()) return@withContext null
            val authUid = SupabaseServices.Auth.getCurrentUserId() ?: return@withContext null
            if (!sameCanonicalUserId(authUid, userId)) return@withContext null

            val remoteRows = runFollowQuery {
                val client = SupabaseServices.client ?: return@runFollowQuery null
                val allRows = mutableListOf<CreatorFollowRelationRow>()
                val pageSize = 500
                val maxPages = 20
                var complete = false
                for (pageIndex in 0 until maxPages) {
                    val offset = pageIndex * pageSize
                    val page = client.postgrest.from(TABLE).select(
                        columns = io.github.jan.supabase.postgrest.query.Columns.list("creator_id, created_at")
                    ) {
                        filter { eq("follower_id", authUid) }
                        order("creator_id", Order.ASCENDING)
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<CreatorFollowRelationRow>()
                    allRows.addAll(page)
                    if (page.size < pageSize) {
                        complete = true
                        break
                    }
                }
                allRows.takeIf { complete }
            } ?: return@withContext null

            if (!sameCanonicalUserId(SupabaseServices.Auth.getCurrentUserId(), authUid)) {
                return@withContext null
            }

            val existingById = followedCreatorsForUser(context, authUid.orEmpty())
                .associateBy { it.id.lowercase(Locale.ROOT) }
            val synced = remoteRows.mapNotNull { row ->
                val id = row.creatorId.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val previous = existingById[id.lowercase(Locale.ROOT)]
                FollowedCreator(
                    id = id,
                    displayName = previous?.displayName?.takeIf { it.isNotBlank() } ?: id,
                    avatarUrl = previous?.avatarUrl,
                    followedAt = row.createdAt?.let { timestamp ->
                        runCatching { java.time.Instant.parse(timestamp).toEpochMilli() }.getOrNull()
                    } ?: previous?.followedAt ?: System.currentTimeMillis()
                )
            }
            saveFollowedCreators(context, authUid.orEmpty(), synced)
            val followedIdsKey = userScopedPreferenceKey(FOLLOW_IDS_KEY, authUid)
                ?: return@withContext null
            context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                .edit().putStringSet(followedIdsKey, synced.map { it.id }.toSet()).apply()
            synced
        }

    /**
     * يطبّق المتابعة/الإلغاء محلياً فوراً، ثم يزامن السحابة في الخلفية.
     * يُعيد الحالة الجديدة أو null عند محاولة متابعة النفس.
     */
    fun setFollowed(
        context: Context,
        viewer: ViewerIdentity,
        creatorId: String,
        creatorName: String,
        creatorAvatar: String?,
        follow: Boolean
    ): Boolean? {
        val cleanId = creatorId.trim()
        val authenticatedUid = SupabaseServices.Auth.getCurrentUserId()
        if (cleanId.isBlank() || !sameCanonicalUserId(authenticatedUid, viewer.id)) return null
        if (isSameAccount(cleanId, creatorName, viewer.id, viewer.displayName, viewer.email)) {
            return null
        }

        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val followedIdsKey = userScopedPreferenceKey(FOLLOW_IDS_KEY, viewer.id) ?: return null
        val legacyFollowKey = userScopedPreferenceKey(LEGACY_PREFIX + cleanId, viewer.id) ?: return null
        val current = followedIdsForUser(context, viewer.id).toMutableSet()
        val nextIds = if (follow) withFollowedId(current, cleanId) else withoutFollowedId(current, cleanId)
        if (!sameCanonicalUserId(SupabaseServices.Auth.getCurrentUserId(), viewer.id)) return null
        appPrefs.edit().putStringSet(followedIdsKey, nextIds).apply()
        legacy.edit().putBoolean(legacyFollowKey, follow).apply()

        val creators = followedCreatorsForUser(context, viewer.id)
            .filterNot { it.id.equals(cleanId, ignoreCase = true) }.toMutableList()
        if (follow) {
            creators.add(
                FollowedCreator(
                    id = cleanId,
                    displayName = creatorName.ifBlank { cleanId },
                    avatarUrl = creatorAvatar
                )
            )
        }
        saveFollowedCreators(context, viewer.id, creators)
        saveCloudState(context, viewer.id, cleanId, "pending")
        syncScope.launch { syncFollowToCloud(context, viewer, cleanId, follow) }
        return follow
    }

    // ───────── المزامنة السحابية الاختيارية ─────────

    private fun cloudReady(): Boolean {
        if (!SupabaseServices.isSupabaseAvailable) return false
        if (System.currentTimeMillis() < followTableUnavailableUntilMs) return false
        return ReelSafetyGuardrails.isSupabaseCallsAllowed()
    }

    private suspend fun <T> runFollowQuery(block: suspend () -> T): T? {
        return try {
            val result = withTimeoutOrNull(8_000L) { block() }
            if (result == null && System.currentTimeMillis() >= followTableUnavailableUntilMs) {
                // قد تكون مهلة شبكية عادية؛ لا نُسجّل غياب الجدول هنا.
                ReelSafetyGuardrails.recordNetworkFailure()
                return null
            }
            followTableUnavailableUntilMs = 0L
            ReelSafetyGuardrails.recordNetworkSuccess()
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (isMissingFollowTable(e.message)) {
                followTableUnavailableUntilMs = System.currentTimeMillis() + FOLLOW_TABLE_RETRY_MS
                Log.i(TAG, "creator_follows table is not installed yet; using local follow state")
                return null
            }
            ReelSafetyGuardrails.recordNetworkFailure()
            Log.w(TAG, "creator follow cloud query failed (${e.javaClass.simpleName})")
            null
        }
    }

    suspend fun syncFollowToCloud(
        context: Context,
        viewer: ViewerIdentity,
        creatorId: String,
        follow: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!cloudReady() || viewer.id.isBlank()) return@withContext false
        val authenticatedUid = SupabaseServices.Auth.getCurrentUserId()
        if (authenticatedUid.isNullOrBlank() || authenticatedUid != viewer.id) return@withContext false
        val ok = runFollowQuery {
            val client = SupabaseServices.client ?: return@runFollowQuery false
            if (follow) {
                client.postgrest.from(TABLE).upsert(
                    CreatorFollowRow(
                        followerId = viewer.id,
                        creatorId = creatorId,
                        createdAt = java.time.Instant.now().toString()
                    )
                ) {
                    onConflict = "follower_id,creator_id"
                }
            } else {
                client.postgrest.from(TABLE).delete {
                    filter {
                        eq("follower_id", viewer.id)
                        eq("creator_id", creatorId)
                    }
                }
            }
            true
        } ?: false

        val state = when {
            ok -> "synced"
            System.currentTimeMillis() < followTableUnavailableUntilMs -> "local-only"
            else -> "failed"
        }
        saveCloudState(context.applicationContext, viewer.id, creatorId, state)
        ok
    }

    suspend fun cloudFollowerCount(creatorId: String): Int? = withContext(Dispatchers.IO) {
        if (!cloudReady() || creatorId.isBlank()) return@withContext null
        runFollowQuery {
            val client = SupabaseServices.client ?: return@runFollowQuery null
            val parameters = buildJsonObject {
                put("p_creator_id", creatorId.trim())
            }
            client.postgrest.rpc("get_creator_follower_count", parameters)
                .decodeAs<Long>()
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    suspend fun cloudIsFollowing(viewerId: String, creatorId: String): Boolean? = withContext(Dispatchers.IO) {
        if (!cloudReady() || viewerId.isBlank() || creatorId.isBlank()) return@withContext null
        val authenticatedUid = SupabaseServices.Auth.getCurrentUserId()
        if (authenticatedUid.isNullOrBlank() || authenticatedUid != viewerId) return@withContext null
        runFollowQuery {
            val client = SupabaseServices.client ?: return@runFollowQuery null
            val rows = client.postgrest.from(TABLE).select {
                filter {
                    eq("follower_id", viewerId)
                    eq("creator_id", creatorId)
                }
                limit(1)
            }.decodeList<CreatorFollowRow>()
            rows.isNotEmpty()
        }
    }

    /**
     * يجلب ملف المنشئ الحقيقي عند توفره، ويحل هوية المشاهد، ويوحّد العدّادات.
     */
    suspend fun loadCreatorCommunity(
        context: Context,
        creatorId: String,
        creatorName: String,
        creatorAvatar: String?
    ): CreatorCommunity = withContext(Dispatchers.IO) {
        val viewer = currentViewer(context.applicationContext)
        val profile = withTimeoutOrNull(6_000L) {
            if (creatorId.isBlank()) null else SupabaseServices.Profiles.getPublicCreatorProfile(creatorId)
        }
        val resolvedName = profile?.fullName?.takeIf { it.isNotBlank() }
            ?: creatorName.ifBlank { creatorId }
        val resolvedAvatar = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: creatorAvatar
        val isOwn = sameCanonicalUserId(creatorId, viewer.id)

        var localFollowing = isFollowing(context.applicationContext, creatorId)
        val remoteFollowing = if (isOwn) null else cloudIsFollowing(viewer.id, creatorId)
        if (remoteFollowing != null && remoteFollowing != localFollowing) {
            // السحابة هي المرجع عند التعارض بين الأجهزة، مع بقاء الاستجابة محلية أولاً.
            if (SupabaseServices.Auth.getCurrentUserId() == viewer.id) {
                val appPrefs = context.applicationContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                val followedIdsKey = userScopedPreferenceKey(FOLLOW_IDS_KEY, viewer.id)
                val legacyFollowKey = userScopedPreferenceKey(LEGACY_PREFIX + creatorId, viewer.id)
                if (followedIdsKey != null && legacyFollowKey != null) {
                    val ids = followedIdsForUser(context.applicationContext, viewer.id).toMutableSet()
                    val nextIds = if (remoteFollowing) withFollowedId(ids, creatorId) else withoutFollowedId(ids, creatorId)
                    appPrefs.edit().putStringSet(followedIdsKey, nextIds).apply()
                    context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(legacyFollowKey, remoteFollowing).apply()
                    localFollowing = remoteFollowing
                }
            }
        }

        val cloudCount = if (creatorId.isBlank()) null else cloudFollowerCount(creatorId)
        val followers = if (cloudCount != null) {
            FollowerCount(cloudCount, isExact = true)
        } else {
            FollowerCount(
                estimatedBaselineFollowers(creatorId.ifBlank { resolvedName }) +
                    if (localFollowing && !isOwn) 1 else 0,
                isExact = false
            )
        }

        val publicCustomId = if (creatorId.isBlank()) null else
            SupabaseServices.Profiles.getCreatorCustomId(creatorId)

        CreatorCommunity(
            creatorId = creatorId,
            displayName = if (isOwn) viewer.displayName else resolvedName,
            avatarUrl = if (isOwn) viewer.avatarUrl ?: resolvedAvatar else resolvedAvatar,
            isOwnProfile = isOwn,
            isFollowing = localFollowing && !isOwn,
            followers = followers,
            followingCount = if (isOwn) followedIds(context.applicationContext).size else 0,
            cloudAvailable = cloudCount != null || remoteFollowing != null,
            customId = publicCustomId
        )
    }
}
