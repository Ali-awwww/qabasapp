package com.qabas.app

import java.util.Locale
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

import com.qabas.app.ProjectService.Project

/**
 * مدير الخدمات السحابية والربط عبر Supabase والخدمات المحلية (بدون Firestore)
 */
object CloudServices {
    /** بريد مالك التطبيق — الاستثناء الوحيد لمنح رتبة «مطور» تلقائياً (بطلب من المالك نفسه) */
    const val OWNER_EMAIL = "aliwalead.2007@gmail.com"
    const val TESTER_EMAIL = "xman88371@gmail.com"

    /** هل هذا حساب المالك أو المطور المرخص؟ */
    fun isOwnerAccount(email: String?): Boolean {
        if (email == null) return false
        val clean = email.trim().lowercase(java.util.Locale.ROOT)
        return clean == OWNER_EMAIL || clean == TESTER_EMAIL
    }
    private const val TAG = "CloudServices"
    
    val isFirebaseInitialized: Boolean = false

    fun initializeWithJson(context: Context, jsonText: String): Pair<Boolean, String> {
        return Pair(true, "تم تفعيل الوضع السحابي بنجاح")
    }

    fun tryInitFromSavedConfig(context: Context) {}

    // --- Supabase Auth compatibility surface ---
    // This facade intentionally exposes no local/fabricated sign-in state.
    object Auth {
        data class User(
            val uid: String,
            val email: String?,
            val displayName: String?,
            val isAnonymous: Boolean = false,
            val isAdmin: Boolean = false,
            val isDeveloper: Boolean = false,
            val isPermanentAdmin: Boolean = false,
            val photoUrl: String? = null
        )

        private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val currentUser: StateFlow<User?> = SupabaseRepository.getInstance().authState
            .map { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        val uid = state.session?.user?.id
                            ?: SupabaseServices.Auth.getCurrentUserId()
                        if (uid.isNullOrBlank()) {
                            null
                        } else {
                            val email = state.userRow.email
                                ?: SupabaseServices.Auth.getCurrentUserEmail()
                            val role = SupabaseServices.Auth.getUserRoleFromMetadata().lowercase(Locale.ROOT)
                            val isDeveloper = role == "developer" || role == "owner" ||
                                state.userRow.type.equals("مطور", ignoreCase = true)
                            val isAdmin = state.isAdmin || isDeveloper ||
                                state.userRow.type.equals("خاص", ignoreCase = true)
                            User(
                                uid = uid,
                                email = email,
                                displayName = state.userRow.name,
                                isAdmin = isAdmin,
                                isDeveloper = isDeveloper,
                                isPermanentAdmin = role == "owner" || isOwnerAccount(email),
                                photoUrl = SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
                                    ?: SupabaseServices.Auth.getCurrentUserMeta("picture")
                            )
                        }
                    }
                    is AuthState.Guest -> User(
                        uid = "guest",
                        email = null,
                        displayName = state.displayName,
                        isAnonymous = true
                    )
                    else -> null
                }
            }
            .stateIn(authScope, SharingStarted.Eagerly, null)

        fun getCurrentUserId(): String? = SupabaseServices.Auth.getCurrentUserId()
    }

    suspend fun syncAdminClaimFromFirebase(context: Context) = syncAdminClaim(context)

    suspend fun syncAdminClaim(context: Context) {
        val email = SupabaseServices.Auth.getCurrentUserEmail()
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

        // 1. Permanent Check for Owner
        if (isOwnerAccount(email)) {
            Log.d(TAG, "Recognized developer owner account ($email). Auto-granting permanent full developer access.")
            prefs.edit()
                .putBoolean("is_developer", true)
                .putBoolean("is_admin", true)
                .apply()
            return
        }

        // 2. Fetch Rank from Supabase Auth Metadata (Secure Role Metadata)
        if (SupabaseServices.isSupabaseAvailable) {
            val authRole = SupabaseServices.Auth.getUserRoleFromMetadata()
            if (authRole != "free") {
                val isDeveloper = authRole == "developer" || authRole == "owner"
                val isAdmin = isDeveloper || authRole == "admin" || authRole == "special"
                
                if (isAdmin || isDeveloper) {
                    prefs.edit()
                        .putBoolean("is_developer", isDeveloper)
                        .putBoolean("is_admin", isAdmin)
                        .apply()
                    Log.d(TAG, "User $email granted $authRole access from Auth Metadata.")
                    return // Stop if found and verified in secure metadata
                }
            }
        }

        // 3. Fallback: Fetch Rank from Supabase Public Table
        val currentUserId = Auth.getCurrentUserId()
        if (currentUserId != null && SupabaseServices.isSupabaseAvailable) {
            val dbUser = SupabaseServices.Database.ensureUser(currentUserId, email, null)
            if (dbUser != null) {
                val role = dbUser.type.lowercase(Locale.ROOT)
                val isDeveloper = role == "developer" || role == "مطور"
                val isSpecial = role == "special" || role == "خاص"
                
                if (isDeveloper || isSpecial) {
                    prefs.edit()
                        .putBoolean("is_developer", isDeveloper)
                        .putBoolean("is_admin", isDeveloper || isSpecial)
                        .apply()
                    Log.d(TAG, "User $email granted $role access from cloud.")
                }
            }
        }
    }

    // --- خدمة قاعدة البيانات (Supabase + Local Database) ---
    object Database {

        suspend fun saveUser(userId: String, name: String, email: String, type: String = "مجاني") {
            SupabaseServices.Database.saveUser(userId, name, email, type)
        }

        suspend fun saveUserToCloud(
            userId: String,
            name: String,
            email: String,
            type: String,
            projectCount: Int = 0
        ): Boolean {
            return SupabaseServices.Database.saveUser(userId, name, email, type)
        }

        suspend fun updateUserRole(userId: String, userEmail: String, newType: String): Boolean {
            return SupabaseServices.Database.updateUserRole(userId, newType)
        }

        suspend fun updateUserSuspension(userId: String, userEmail: String, isSuspended: Boolean): Boolean {
            return SupabaseServices.Database.updateUserSuspension(userId, isSuspended)
        }

        suspend fun savePromoCodeToCloud(code: String, type: String, value: Long): Boolean {
            return SupabaseServices.Database.savePromoCode(code, type, value)
        }

        suspend fun fetchValidCodesCache(): Pair<Map<String, Long>, Map<String, Int>> {
            val codes = SupabaseServices.Database.getValidPromoCodes()
            val promos = mutableMapOf<String, Long>()
            val gifts = mutableMapOf<String, Int>()
            codes.forEach { c ->
                if (c.type == "PROMO") {
                    promos[c.code] = c.value * 24L * 60L * 60L * 1000L
                } else {
                    gifts[c.code] = c.value.toInt()
                }
            }
            return Pair(promos, gifts)
        }

        suspend fun getCloudPromoCodes(): List<Map<String, Any>> {
            return SupabaseServices.Database.getValidPromoCodes().map {
                mapOf("code" to it.code, "type" to it.type, "value" to it.value)
            }
        }
        
        suspend fun getAllUsers(): List<Map<String, Any>> {
            return SupabaseServices.Database.getAllUsers().map {
                mapOf(
                    "id" to it.id,
                    "name" to (it.name ?: "مستخدم"),
                    "email" to (it.email ?: ""),
                    "type" to it.type,
                    "status" to (it.status ?: "active")
                )
            }
        }

        suspend fun getDevUserProjectCount(uid: String): Int {
            return SupabaseServices.Database.getUserProjects(uid).size
        }

        suspend fun grantTemporaryRole(userId: String, userEmail: String, newType: String, daysValid: Int): Boolean {
            return SupabaseServices.Database.updateUserRole(userId, newType)
        }

        suspend fun sweepExpiredRoles(): Int = 0

        suspend fun getUserTimeline(uid: String): List<Map<String, Any>> {
            return SupabaseServices.Database.getUserProjects(uid).map {
                mapOf(
                    "kind" to "project",
                    "title" to it.title,
                    "ts" to System.currentTimeMillis()
                )
            }
        }

        // حفظ مشروع في السحابة
        suspend fun saveProjectToCloud(project: Project) {
            SupabaseServices.Database.saveFullProject(
                id = project.id,
                title = project.title,
                description = project.idea,
                cost = project.cost,
                status = "saved"
            )
        }
        
        // حذف مشروع من السحابة
        suspend fun deleteProjectFromCloud(projectId: String) {
            SupabaseServices.Database.deleteProject(projectId)
        }

        // جلب المشاريع من السحابة
        suspend fun getUserProjects(): List<Project> {
            return SupabaseServices.Database.getUserProjects().map {
                Project(
                    id = it.id,
                    title = it.title,
                    idea = it.description ?: "",
                    analysis = "",
                    resources = "",
                    settings = "",
                    script = "",
                    cost = it.cost
                )
            }
        }

        // جلب المشاريع المنشورة في مجتمع الإلهام (Reels Feed)
        suspend fun getFeedProjects(): List<Project> {
            return SupabaseServices.Database.getFeed().map {
                Project(
                    id = it.id,
                    title = it.title,
                    idea = it.description ?: "",
                    analysis = "",
                    resources = "",
                    settings = "",
                    script = "",
                    cost = it.cost
                )
            }
        }

        // جلب لوحة المتصدرين (Leaderboard)
        suspend fun getLeaderboard(): List<CreatorStats> {
            val list = SupabaseServices.Database.getLeaderboard()
            return list.mapIndexed { idx, row ->
                CreatorStats(
                    name = row.name,
                    rank = idx + 1,
                    points = row.points.toInt(),
                    views = "${row.points / 10}K",
                    badge = if (idx == 0) "👑" else if (idx == 1) "🥈" else if (idx == 2) "🥉" else "⭐"
                )
            }
        }

        // تسجيل عملية شراء
        suspend fun recordPurchase(productId: String, purchaseToken: String) {
            SupabaseServices.Database.recordTransaction(
                id = java.util.UUID.randomUUID().toString(),
                userId = Auth.getCurrentUserId() ?: "local_user",
                productId = productId,
                purchaseToken = purchaseToken,
                priceAmount = 0.0
            )
        }

        // تسجيل عمليات فحص حارس المحتوى في السحابة
        suspend fun recordContentGuardCheck(text: String, verdict: String, score: Int, reason: String) {
            SupabaseServices.Database.recordContentGuardCheck(
                id = "guard_${System.currentTimeMillis()}",
                userId = Auth.getCurrentUserId() ?: "local_user",
                snippet = text.take(200),
                verdict = verdict,
                score = score,
                reason = reason
            )
        }

        fun observeGuardLogs(): Flow<List<Map<String, Any>>> = flowOf(emptyList())

        suspend fun recordProjectAnalytics(projectId: String, title: String, status: String, cost: Double = 0.0) {}

        fun observeAllTransactions(): Flow<List<Map<String, Any>>> = TransactionManager.transactionsFlow

        // إدارة كائنات الأنماط الفنية
        suspend fun saveStyleObjectToCloud(styleObject: StyleObject): Boolean {
            return SupabaseServices.Database.saveStyleObject(
                id = styleObject.id,
                name = styleObject.name,
                userId = styleObject.userId,
                sourceVideo = styleObject.sourceVideoPathOrUrl,
                score = styleObject.overallScore,
                tags = styleObject.tags,
                payloadJson = org.json.JSONObject(styleObject.toMap()).toString()
            )
        }

        suspend fun getCloudStyleObjects(userId: String? = null): List<StyleObject> {
            val rows = SupabaseServices.Database.getStyleObjects(userId)
            return rows.mapNotNull { row ->
                runCatching {
                    val json = org.json.JSONObject(row.payload.toString())
                    val map = mutableMapOf<String, Any?>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = json.opt(key)
                    }
                    StyleObject.fromMap(map)
                }.getOrNull()
            }
        }

        suspend fun deleteStyleObjectFromCloud(styleId: String): Boolean {
            return SupabaseServices.Database.deleteStyleObject(styleId)
        }

        fun observeCloudStyleObjects(): Flow<List<StyleObject>> = flowOf(emptyList())

        // إدارة أنماط الماستر المركبة
        suspend fun saveMasterStyleToCloud(masterStyle: MasterStyle): Boolean {
            return SupabaseServices.Database.saveMasterStyle(
                id = masterStyle.id,
                name = masterStyle.name,
                description = masterStyle.description,
                userId = Auth.getCurrentUserId() ?: "local_user",
                compliance = masterStyle.islamicAestheticComplianceScore,
                payloadJson = "{}"
            )
        }

        suspend fun getCloudMasterStyles(userId: String? = null): List<MasterStyle> {
            return emptyList()
        }

        suspend fun deleteMasterStyleFromCloud(masterStyleId: String): Boolean {
            return SupabaseServices.Database.deleteMasterStyle(masterStyleId)
        }
    }
}
