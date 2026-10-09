package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ScholarPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("qabas_scholar_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun isFavorite(scholarId: String): Boolean {
        return getFavorites().contains(scholarId)
    }

    fun toggleFavorite(scholarId: String): Boolean {
        val current = getFavorites().toMutableSet()
        val newState = if (current.contains(scholarId)) {
            current.remove(scholarId)
            false
        } else {
            current.add(scholarId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return newState
    }

    fun getCompletedChallenges(): Set<String> {
        return prefs.getStringSet(KEY_COMPLETED_CHALLENGES, emptySet()) ?: emptySet()
    }

    fun isChallengeCompleted(scholarId: String): Boolean {
        return getCompletedChallenges().contains(scholarId)
    }

    fun toggleChallengeCompleted(scholarId: String): Boolean {
        val current = getCompletedChallenges().toMutableSet()
        val newState = if (current.contains(scholarId)) {
            current.remove(scholarId)
            false
        } else {
            current.add(scholarId)
            true
        }
        prefs.edit().putStringSet(KEY_COMPLETED_CHALLENGES, current).apply()
        return newState
    }

    // --- Developer Mode Management (وضع المطور / إدارة المحتوى) ---

    fun isDeveloperMode(): Boolean {
        return prefs.getBoolean(KEY_DEV_MODE, false)
    }

    fun setDeveloperMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
    }

    // --- 1. الدروس والروابط المعتمدة من المطور والمنصة (Developer Curated Content) ---

    fun getOfficialLectures(scholarId: String, scholarName: String = ""): List<ScholarLecture> {
        val curated = ScholarCuratedLectures.getCuratedLectures(scholarId, scholarName)
        val devAdded = getDeveloperAddedLectures().filter { it.scholarId == scholarId }
        val deletedIds = getDeletedCuratedIds()

        val all = (devAdded + curated.filter { it.id !in deletedIds })
        return all.distinctBy { it.id }.sortedByDescending { it.addedAtMillis }
    }

    fun getLecturesForScholar(scholarId: String): List<ScholarLecture> {
        return getOfficialLectures(scholarId)
    }

    fun getLectureCountForScholar(scholarId: String): Int {
        return getOfficialLectures(scholarId).size
    }

    fun addDeveloperLecture(lecture: ScholarLecture) {
        val current = getDeveloperAddedLectures().toMutableList()
        current.removeAll { it.id == lecture.id }
        val updated = lecture.copy(isOfficialDeveloperCurated = true)
        current.add(0, updated)
        saveDeveloperAddedLectures(current)

        // Asynchronously sync to Supabase Cloud
        CoroutineScope(Dispatchers.IO).launch {
            if (SupabaseServices.isSupabaseAvailable) {
                val row = SupabaseServices.ScholarLectureRow(
                    id = updated.id,
                    scholarId = updated.scholarId,
                    scholarName = updated.scholarName,
                    title = updated.title,
                    speaker = updated.speaker,
                    url = updated.url,
                    type = updated.type,
                    category = updated.category,
                    synopsis = updated.synopsis,
                    durationApprox = updated.durationApprox,
                    isOfficial = updated.isOfficialDeveloperCurated,
                    notes = updated.notes,
                    addedAtMillis = updated.addedAtMillis
                )
                SupabaseServices.Database.saveScholarLecture(row)
            }
        }
    }

    fun deleteDeveloperLecture(lectureId: String) {
        // Remove from custom added
        val current = getDeveloperAddedLectures().toMutableList()
        val removed = current.removeAll { it.id == lectureId }
        if (removed) {
            saveDeveloperAddedLectures(current)
        } else {
            // Track deleted curated lecture
            val deleted = getDeletedCuratedIds().toMutableSet()
            deleted.add(lectureId)
            prefs.edit().putStringSet(KEY_DELETED_CURATED_IDS, deleted).apply()
        }

        // Asynchronously sync deletion to Supabase Cloud
        CoroutineScope(Dispatchers.IO).launch {
            if (SupabaseServices.isSupabaseAvailable) {
                SupabaseServices.Database.deleteScholarLecture(lectureId)
            }
        }
    }

    suspend fun syncLecturesFromCloud(scholarId: String): List<ScholarLecture> {
        if (!SupabaseServices.isSupabaseAvailable) return getOfficialLectures(scholarId)
        try {
            val cloudRows = SupabaseServices.Database.getScholarLectures(scholarId)
            if (cloudRows.isNotEmpty()) {
                val current = getDeveloperAddedLectures().toMutableList()
                for (row in cloudRows) {
                    val item = ScholarLecture(
                        id = row.id,
                        scholarId = row.scholarId,
                        scholarName = row.scholarName,
                        title = row.title,
                        speaker = row.speaker,
                        url = row.url,
                        type = row.type,
                        category = row.category,
                        synopsis = row.synopsis,
                        durationApprox = row.durationApprox,
                        isOfficialDeveloperCurated = row.isOfficial,
                        notes = row.notes,
                        addedAtMillis = row.addedAtMillis
                    )
                    val index = current.indexOfFirst { it.id == item.id }
                    if (index >= 0) {
                        current[index] = item
                    } else {
                        current.add(item)
                    }
                }
                saveDeveloperAddedLectures(current)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getOfficialLectures(scholarId)
    }

    private fun getDeveloperAddedLectures(): List<ScholarLecture> {
        val jsonString = prefs.getString(KEY_DEV_LECTURES, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<ScholarLecture>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ScholarLecture(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        scholarId = obj.optString("scholarId", ""),
                        scholarName = obj.optString("scholarName", ""),
                        title = obj.optString("title", ""),
                        speaker = obj.optString("speaker", ""),
                        url = obj.optString("url", ""),
                        type = obj.optString("type", "يوتيوب / مرئي"),
                        category = obj.optString("category", "المحنة والثبات"),
                        synopsis = obj.optString("synopsis", ""),
                        durationApprox = obj.optString("durationApprox", ""),
                        isOfficialDeveloperCurated = obj.optBoolean("isOfficialDeveloperCurated", true),
                        notes = obj.optString("notes", ""),
                        addedAtMillis = obj.optLong("addedAtMillis", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveDeveloperAddedLectures(lectures: List<ScholarLecture>) {
        val array = JSONArray()
        for (lec in lectures) {
            val obj = JSONObject().apply {
                put("id", lec.id)
                put("scholarId", lec.scholarId)
                put("scholarName", lec.scholarName)
                put("title", lec.title)
                put("speaker", lec.speaker)
                put("url", lec.url)
                put("type", lec.type)
                put("category", lec.category)
                put("synopsis", lec.synopsis)
                put("durationApprox", lec.durationApprox)
                put("isOfficialDeveloperCurated", lec.isOfficialDeveloperCurated)
                put("notes", lec.notes)
                put("addedAtMillis", lec.addedAtMillis)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_DEV_LECTURES, array.toString()).apply()
    }

    private fun getDeletedCuratedIds(): Set<String> {
        return prefs.getStringSet(KEY_DELETED_CURATED_IDS, emptySet()) ?: emptySet()
    }

    // --- 2. مقترحات ومساهمات المستخدمين (User Proposals & Suggestions) ---

    fun getUserProposals(scholarId: String): List<LectureProposal> {
        val allProposals = getAllProposals()
        val userList = allProposals.filter { it.scholarId == scholarId }
        val defaults = getDefaultProposalsForScholar(scholarId).filter { def ->
            userList.none { it.id == def.id }
        }
        return (userList + defaults).sortedByDescending { it.upvotesCount }
    }

    fun getAllProposals(): List<LectureProposal> {
        val jsonString = prefs.getString(KEY_USER_PROPOSALS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<LectureProposal>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LectureProposal(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        scholarId = obj.optString("scholarId", ""),
                        scholarName = obj.optString("scholarName", ""),
                        suggestedTitle = obj.optString("suggestedTitle", ""),
                        suggestedSpeaker = obj.optString("suggestedSpeaker", ""),
                        suggestedUrl = obj.optString("suggestedUrl", ""),
                        suggestedTopic = obj.optString("suggestedTopic", "سيرة وعبر"),
                        rationale = obj.optString("rationale", ""),
                        userProposerName = obj.optString("userProposerName", "طالب علم"),
                        upvotesCount = obj.optInt("upvotesCount", 1),
                        status = obj.optString("status", "قيد المراجعة العلمية"),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun addUserProposal(proposal: LectureProposal) {
        val current = getAllProposals().toMutableList()
        current.removeAll { it.id == proposal.id }
        current.add(0, proposal)
        saveProposals(current)

        // Asynchronously sync proposal to Supabase Cloud
        CoroutineScope(Dispatchers.IO).launch {
            if (SupabaseServices.isSupabaseAvailable) {
                val row = SupabaseServices.ScholarProposalRow(
                    id = proposal.id,
                    scholarId = proposal.scholarId,
                    scholarName = proposal.scholarName,
                    suggestedTitle = proposal.suggestedTitle,
                    suggestedSpeaker = proposal.suggestedSpeaker,
                    suggestedUrl = proposal.suggestedUrl,
                    suggestedTopic = proposal.suggestedTopic,
                    rationale = proposal.rationale,
                    userProposerName = proposal.userProposerName,
                    upvotesCount = proposal.upvotesCount,
                    status = proposal.status,
                    createdAtMillis = proposal.createdAtMillis
                )
                SupabaseServices.Database.saveScholarProposal(row)
            }
        }
    }

    fun upvoteProposal(proposalId: String) {
        val current = getAllProposals().toMutableList()
        val index = current.indexOfFirst { it.id == proposalId }
        var newCount = 1
        if (index != -1) {
            val item = current[index]
            newCount = item.upvotesCount + 1
            current[index] = item.copy(upvotesCount = newCount)
            saveProposals(current)
        } else {
            // Check in defaults
            for (scholarId in listOf("abu_bakr", "umar_ibn_al_khattab", "ahmad_ibn_hanbal", "ibn_taymiyyah")) {
                val def = getDefaultProposalsForScholar(scholarId).find { it.id == proposalId }
                if (def != null) {
                    newCount = def.upvotesCount + 1
                    current.add(def.copy(upvotesCount = newCount))
                    saveProposals(current)
                    break
                }
            }
        }

        // Asynchronously sync upvote to Supabase Cloud
        CoroutineScope(Dispatchers.IO).launch {
            if (SupabaseServices.isSupabaseAvailable) {
                SupabaseServices.Database.upvoteScholarProposal(proposalId, newCount)
            }
        }
    }

    fun deleteUserProposal(proposalId: String) {
        val current = getAllProposals().toMutableList()
        current.removeAll { it.id == proposalId }
        saveProposals(current)

        // Asynchronously sync deletion to Supabase Cloud
        CoroutineScope(Dispatchers.IO).launch {
            if (SupabaseServices.isSupabaseAvailable) {
                SupabaseServices.Database.deleteScholarProposal(proposalId)
            }
        }
    }

    suspend fun syncProposalsFromCloud(scholarId: String): List<LectureProposal> {
        if (!SupabaseServices.isSupabaseAvailable) return getUserProposals(scholarId)
        try {
            val cloudRows = SupabaseServices.Database.getScholarProposals(scholarId)
            if (cloudRows.isNotEmpty()) {
                val current = getAllProposals().toMutableList()
                for (row in cloudRows) {
                    val item = LectureProposal(
                        id = row.id,
                        scholarId = row.scholarId,
                        scholarName = row.scholarName,
                        suggestedTitle = row.suggestedTitle,
                        suggestedSpeaker = row.suggestedSpeaker,
                        suggestedUrl = row.suggestedUrl,
                        suggestedTopic = row.suggestedTopic,
                        rationale = row.rationale,
                        userProposerName = row.userProposerName,
                        upvotesCount = row.upvotesCount,
                        status = row.status,
                        createdAtMillis = row.createdAtMillis
                    )
                    val index = current.indexOfFirst { it.id == item.id }
                    if (index >= 0) {
                        current[index] = item
                    } else {
                        current.add(item)
                    }
                }
                saveProposals(current)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getUserProposals(scholarId)
    }

    private fun saveProposals(proposals: List<LectureProposal>) {
        val array = JSONArray()
        for (prop in proposals) {
            val obj = JSONObject().apply {
                put("id", prop.id)
                put("scholarId", prop.scholarId)
                put("scholarName", prop.scholarName)
                put("suggestedTitle", prop.suggestedTitle)
                put("suggestedSpeaker", prop.suggestedSpeaker)
                put("suggestedUrl", prop.suggestedUrl)
                put("suggestedTopic", prop.suggestedTopic)
                put("rationale", prop.rationale)
                put("userProposerName", prop.userProposerName)
                put("upvotesCount", prop.upvotesCount)
                put("status", prop.status)
                put("createdAtMillis", prop.createdAtMillis)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_USER_PROPOSALS, array.toString()).apply()
    }

    private fun getDefaultProposalsForScholar(scholarId: String): List<LectureProposal> {
        return when (scholarId) {
            "abu_bakr" -> listOf(
                LectureProposal(
                    id = "prop_abu_bakr_1",
                    scholarId = "abu_bakr",
                    scholarName = "أبو بكر الصديق",
                    suggestedTitle = "فقه المعاملات المالية وإنفاق المال عند الصديق",
                    suggestedSpeaker = "الشيخ سعيد الكملي",
                    suggestedTopic = "الفقه والتربية المالية",
                    rationale = "نحتاج درساً يفصل كيف كان الصديق تاجراً ثرياً ولكنه زهد في كل درهم لله، ليتعلم شباب الأمة التوازن بين الكسب والتضحية.",
                    userProposerName = "عبد الله السالم",
                    upvotesCount = 14,
                    status = "معتمد ومجدول للتسجيل"
                )
            )
            "umar_ibn_al_khattab" -> listOf(
                LectureProposal(
                    id = "prop_umar_1",
                    scholarId = "umar_ibn_al_khattab",
                    scholarName = "عمر بن الخطاب",
                    suggestedTitle = "فقه إدارة الأزمات في عام الرمادة",
                    suggestedSpeaker = "د. عثمان الخميس",
                    suggestedTopic = "إدارة الأزمات والقيادة",
                    rationale = "أهمية استخراج العبر العملية من موقف عمر حين امتنع عن أكل السمن واللحم حتى يشبع فقراء المسلمين.",
                    userProposerName = "أبو أنس المغربي",
                    upvotesCount = 21,
                    status = "قيد المراجعة العلمية"
                )
            )
            "ahmad_ibn_hanbal" -> listOf(
                LectureProposal(
                    id = "prop_ahmad_1",
                    scholarId = "ahmad_ibn_hanbal",
                    scholarName = "الإمام أحمد بن حنبل",
                    suggestedTitle = "منهج الإمام أحمد في التعامل مع أهل الأهواء والشبهات المعاصرة",
                    suggestedSpeaker = "الشيخ صالح العصيمي",
                    suggestedTopic = "العقيدة والرد على الشبهات",
                    rationale = "تأصيل قواعد الصبر والحجة العلمية في مواجهة التيارات الفكرية المنحرفة دون غلو أو تفريط.",
                    userProposerName = "محب السنة",
                    upvotesCount = 18,
                    status = "قيد المراجعة العلمية"
                )
            )
            else -> emptyList()
        }
    }

    companion object {
        private const val KEY_FAVORITES = "bookmarked_scholars"
        private const val KEY_COMPLETED_CHALLENGES = "completed_scholar_challenges"
        private const val KEY_DEV_MODE = "scholar_developer_mode_enabled"
        private const val KEY_DEV_LECTURES = "custom_scholar_lectures"
        private const val KEY_DELETED_CURATED_IDS = "deleted_curated_lecture_ids"
        private const val KEY_USER_PROPOSALS = "user_lecture_proposals"
    }
}
