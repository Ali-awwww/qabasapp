package com.qabas.app

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

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
    private const val INSTALL_ID_KEY = "creator_follow_install_id"
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
        val cloudAvailable: Boolean
    )

    @Serializable
    data class CreatorFollowRow(
        @SerialName("follower_id") val followerId: String,
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

    private fun installFollowerId(context: Context): String {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(INSTALL_ID_KEY, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val created = "local-" + UUID.randomUUID().toString()
        prefs.edit().putString(INSTALL_ID_KEY, created).apply()
        return created
    }

    fun currentViewer(context: Context): ViewerIdentity {
        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val authId = SupabaseServices.Auth.getCurrentUserId()
        val authEmail = SupabaseServices.Auth.getCurrentUserEmail()
        val firebaseUser = runCatching {
            if (FirebaseManager.isConfigured(context)) FirebaseManager.getAuth().currentUser else null
        }.getOrNull()
        val storedEmail = appPrefs.getString("user_email", null)?.takeIf { it.isNotBlank() }
        val displayName = appPrefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
            ?: appPrefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.displayName?.takeIf { it.isNotBlank() }
            ?: SupabaseServices.Auth.getCurrentUserMeta("full_name")
            ?: "صانع محتوى قبس"
        val avatar = appPrefs.getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.photoUrl?.toString()?.takeIf { it.isNotBlank() }
            ?: SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
        val localId = appPrefs.getString("local_user_id", null)?.takeIf { it.isNotBlank() }
            ?: appPrefs.getString("user_id", null)?.takeIf { it.isNotBlank() }
            ?: appPrefs.getString("user_account_number", null)?.takeIf { it.isNotBlank() }
        val id = listOf(authId, firebaseUser?.uid, localId, authEmail, firebaseUser?.email, storedEmail)
            .firstOrNull { !it.isNullOrBlank() }
            ?: installFollowerId(context)

        return ViewerIdentity(
            id = id!!,
            displayName = displayName,
            email = authEmail ?: firebaseUser?.email ?: storedEmail,
            avatarUrl = avatar
        )
    }

    // ───────── الحالة المحلية ─────────

    fun followedIds(context: Context): Set<String> {
        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val configured = appPrefs.getStringSet(FOLLOW_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        legacy.all.forEach { (key, value) ->
            if (key.startsWith(LEGACY_PREFIX) && value == true) {
                configured.add(key.removePrefix(LEGACY_PREFIX))
            }
        }
        return configured.filter { it.isNotBlank() }.toSet()
    }

    fun isFollowing(context: Context, creatorId: String): Boolean {
        if (creatorId.isBlank()) return false
        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        if (appPrefs.getStringSet(FOLLOW_IDS_KEY, emptySet()).orEmpty().any { it.equals(creatorId, ignoreCase = true) }) {
            return true
        }
        return context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(LEGACY_PREFIX + creatorId, false)
    }

    fun followedCreators(context: Context): List<FollowedCreator> {
        val raw = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(METADATA_KEY, null) ?: return followedIds(context).map {
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

    private fun saveFollowedCreators(context: Context, creators: List<FollowedCreator>) {
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
            .edit().putString(METADATA_KEY, payload).apply()
    }

    private fun cloudState(context: Context, creatorId: String): String? =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(CLOUD_STATE_PREFIX + creatorId, null)

    private fun saveCloudState(context: Context, creatorId: String, state: String) {
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().putString(CLOUD_STATE_PREFIX + creatorId, state).apply()
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
        if (cleanId.isBlank()) return null
        if (isSameAccount(cleanId, creatorName, viewer.id, viewer.displayName, viewer.email)) {
            return null
        }

        val appPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val current = followedIds(context).toMutableSet()
        val nextIds = if (follow) withFollowedId(current, cleanId) else withoutFollowedId(current, cleanId)
        appPrefs.edit().putStringSet(FOLLOW_IDS_KEY, nextIds).apply()
        legacy.edit().putBoolean(LEGACY_PREFIX + cleanId, follow).apply()

        val creators = followedCreators(context).filterNot { it.id.equals(cleanId, ignoreCase = true) }.toMutableList()
        if (follow) {
            creators.add(
                FollowedCreator(
                    id = cleanId,
                    displayName = creatorName.ifBlank { cleanId },
                    avatarUrl = creatorAvatar
                )
            )
        }
        saveFollowedCreators(context, creators)
        saveCloudState(context, cleanId, "pending")
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
            Log.w(TAG, "creator follow cloud query failed: ${e.message}")
            null
        }
    }

    suspend fun syncFollowToCloud(
        context: Context,
        viewer: ViewerIdentity,
        creatorId: String,
        follow: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!cloudReady()) return@withContext false
        val ok = runFollowQuery {
            val client = SupabaseServices.client ?: return@runFollowQuery false
            if (follow) {
                client.postgrest.from(TABLE).upsert(
                    CreatorFollowRow(
                        followerId = viewer.id,
                        creatorId = creatorId,
                        createdAt = java.time.Instant.now().toString()
                    )
                )
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
        saveCloudState(context.applicationContext, creatorId, state)
        ok
    }

    suspend fun cloudFollowerCount(creatorId: String): Int? = withContext(Dispatchers.IO) {
        if (!cloudReady() || creatorId.isBlank()) return@withContext null
        runFollowQuery {
            val client = SupabaseServices.client ?: return@runFollowQuery null
            val rows = client.postgrest.from(TABLE).select {
                filter { eq("creator_id", creatorId) }
            }.decodeList<CreatorFollowRow>()
            rows.map { it.followerId }.toSet().size
        }
    }

    suspend fun cloudIsFollowing(viewerId: String, creatorId: String): Boolean? = withContext(Dispatchers.IO) {
        if (!cloudReady() || viewerId.isBlank() || creatorId.isBlank()) return@withContext null
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
            if (creatorId.isBlank()) null else SupabaseServices.Profiles.getUserProfile(creatorId)
        }
        val resolvedName = profile?.fullName?.takeIf { it.isNotBlank() }
            ?: creatorName.ifBlank { creatorId }
        val resolvedAvatar = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: creatorAvatar
        val isOwn = isSameAccount(
            creatorId,
            resolvedName,
            viewer.id,
            viewer.displayName,
            viewer.email
        ) || (!profile?.email.isNullOrBlank() && isSameAccount(
            profile?.email,
            resolvedName,
            viewer.id,
            viewer.displayName,
            viewer.email
        ))

        var localFollowing = isFollowing(context.applicationContext, creatorId)
        val remoteFollowing = if (isOwn) null else cloudIsFollowing(viewer.id, creatorId)
        if (remoteFollowing != null && remoteFollowing != localFollowing) {
            // السحابة هي المرجع عند التعارض بين الأجهزة، مع بقاء الاستجابة محلية أولاً.
            val appPrefs = context.applicationContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val ids = followedIds(context.applicationContext).toMutableSet()
            val nextIds = if (remoteFollowing) withFollowedId(ids, creatorId) else withoutFollowedId(ids, creatorId)
            appPrefs.edit().putStringSet(FOLLOW_IDS_KEY, nextIds).apply()
            context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(LEGACY_PREFIX + creatorId, remoteFollowing).apply()
            localFollowing = remoteFollowing
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

        CreatorCommunity(
            creatorId = creatorId,
            displayName = if (isOwn) viewer.displayName else resolvedName,
            avatarUrl = if (isOwn) viewer.avatarUrl ?: resolvedAvatar else resolvedAvatar,
            isOwnProfile = isOwn,
            isFollowing = localFollowing && !isOwn,
            followers = followers,
            followingCount = if (isOwn) followedIds(context.applicationContext).size else 0,
            cloudAvailable = cloudCount != null || remoteFollowing != null
        )
    }
}
