package com.qabas.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * حالة الحفظ التلقائي الحالية لمساحة العمل
 */
data class WorkspaceAutoSaveStatus(
    val isSaving: Boolean = false,
    val lastSavedTime: Long? = null,
    val formattedLastSaved: String = "",
    val hasRecoverableDraft: Boolean = false,
    val draftTitle: String = "",
    val draftSnippet: String = "",
    val draftTimestamp: Long? = null
)

/**
 * مدير الحفظ التلقائي الدوري لمساحات العمل في قاعدة بيانات Room المحلية.
 * يحفظ مسودة العمل دورياً كل 15 ثانية وعند حدوث تعديلات نشطة لحماية محتوى الفيديو من الفقدان.
 */
object WorkspaceAutoSaveManager {
    private var repository: WorkspaceRepository? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicJob: Job? = null
    private var debounceJob: Job? = null

    private val _status = MutableStateFlow(WorkspaceAutoSaveStatus())
    val status: StateFlow<WorkspaceAutoSaveStatus> = _status.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var lastSavedAiHash: Int = 0
    private var lastSavedProHash: Int = 0

    const val DRAFT_ID_AI = "active_ai_workspace"
    const val DRAFT_ID_PRO = "active_pro_workspace"

    fun init(context: Context) {
        if (repository == null) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            repository = WorkspaceRepository(db.workspaceDraftDao())
            checkExistingDrafts()
        }
    }

    private fun checkExistingDrafts() {
        scope.launch {
            val repo = repository ?: return@launch
            val draft = repo.getDraft(DRAFT_ID_AI) ?: repo.getDraft(DRAFT_ID_PRO)
            if (draft != null && draft.contentText.isNotBlank()) {
                val fmtTime = timeFormat.format(Date(draft.lastSavedAt))
                _status.value = _status.value.copy(
                    hasRecoverableDraft = true,
                    draftTitle = draft.title,
                    draftSnippet = if (draft.contentText.length > 60) draft.contentText.take(60) + "..." else draft.contentText,
                    draftTimestamp = draft.lastSavedAt,
                    formattedLastSaved = fmtTime
                )
            }
        }
    }

    // ==================== AI Studio Workspace Auto-Save ====================

    /**
     * حفظ مساحة عمل استوديو الذكاء الاصطناعي دورياً في Room
     */
    fun scheduleAiStudioSave(state: ProjectState, debounceMs: Long = 1200L) {
        val currentHash = state.inputText.hashCode() xor state.libraryScenes.size xor state.mediaResources.size xor state.selectedRatio.hashCode()
        if (currentHash == lastSavedAiHash) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            performAiStudioSave(state)
        }
    }

    suspend fun performAiStudioSave(state: ProjectState) = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext
        if (state.inputText.isBlank() && state.libraryScenes.isEmpty()) return@withContext

        _status.value = _status.value.copy(isSaving = true)
        try {
            val json = serializeAiState(state)
            repo.saveWorkspace(
                id = DRAFT_ID_AI,
                workspaceType = "AI_STUDIO",
                projectId = state.currentProjectId ?: "draft_ai_${System.currentTimeMillis()}",
                title = state.projectTitle.ifBlank { "مشروع فيديو تلقائي" },
                contentText = state.inputText,
                stateJson = json,
                lastScreen = state.appState.name
            )
            lastSavedAiHash = state.inputText.hashCode() xor state.libraryScenes.size xor state.mediaResources.size xor state.selectedRatio.hashCode()
            val now = System.currentTimeMillis()
            _status.value = _status.value.copy(
                isSaving = false,
                lastSavedTime = now,
                formattedLastSaved = timeFormat.format(Date(now)),
                hasRecoverableDraft = true,
                draftTitle = state.projectTitle,
                draftSnippet = if (state.inputText.length > 60) state.inputText.take(60) + "..." else state.inputText,
                draftTimestamp = now
            )
        } catch (e: Exception) {
            _status.value = _status.value.copy(isSaving = false)
        }
    }

    private fun serializeAiState(state: ProjectState): String {
        val root = JSONObject()
        root.put("selectedRatio", state.selectedRatio)
        root.put("inputText", state.inputText)
        root.put("contentType", state.contentType)
        root.put("contentTone", state.contentTone)
        root.put("targetPlatform", state.targetPlatform)
        root.put("projectTitle", state.projectTitle)
        root.put("currentProjectId", state.currentProjectId ?: "")
        root.put("videoDuration", state.videoDuration)
        root.put("editingStyle", state.editingStyle)
        root.put("voiceOver", state.voiceOver)
        root.put("musicVibe", state.musicVibe)
        root.put("styleDescription", state.styleDescription)
        root.put("selectedTemplate", state.selectedTemplate)
        root.put("videoQuality", state.videoQuality)
        root.put("ambientSound", state.ambientSound)
        root.put("finalVideoPath", state.finalVideoPath)
        root.put("appState", state.appState.name)

        // Scenes serialization
        val scenesArr = JSONArray()
        state.libraryScenes.forEach { scene ->
            val scObj = JSONObject()
            scObj.put("title", scene.title)
            scObj.put("description", scene.description)
            scObj.put("durationInSeconds", scene.durationInSeconds)
            scObj.put("visualEffect", scene.visualEffect)
            scObj.put("tempo", scene.tempo)
            scObj.put("transitionType", scene.transitionType)
            scObj.put("mediaUrl", scene.mediaUrl ?: "")
            scenesArr.put(scObj)
        }
        root.put("scenes", scenesArr)

        // Media resources serialization
        val mediaArr = JSONArray()
        state.mediaResources.forEach { media ->
            val mObj = JSONObject()
            mObj.put("id", media.id)
            mObj.put("type", media.type)
            mObj.put("name", media.name)
            mObj.put("uri", media.uri ?: "")
            mObj.put("durationLabel", media.durationLabel ?: "")
            mObj.put("sizeLabel", media.sizeLabel ?: "")
            mObj.put("isBRoll", media.isBRoll)
            mObj.put("category", media.category ?: "")
            mediaArr.put(mObj)
        }
        root.put("mediaResources", mediaArr)

        return root.toString()
    }

    fun deserializeAiState(jsonStr: String): RestoredAiWorkspace? {
        return try {
            val root = JSONObject(jsonStr)
            val scenes = ArrayList<Scene>()
            val scenesArr = root.optJSONArray("scenes")
            if (scenesArr != null) {
                for (i in 0 until scenesArr.length()) {
                    val sc = scenesArr.getJSONObject(i)
                    scenes.add(
                        Scene(
                            title = sc.optString("title", ""),
                            description = sc.optString("description", ""),
                            durationInSeconds = sc.optInt("durationInSeconds", 5),
                            visualEffect = sc.optString("visualEffect", "بدون"),
                            tempo = sc.optString("tempo", "عادي"),
                            transitionType = sc.optString("transitionType", "Fade"),
                            mediaUrl = sc.optString("mediaUrl", "").ifBlank { null }
                        )
                    )
                }
            }

            val mediaList = ArrayList<MediaResource>()
            val mediaArr = root.optJSONArray("mediaResources")
            if (mediaArr != null) {
                for (i in 0 until mediaArr.length()) {
                    val m = mediaArr.getJSONObject(i)
                    val mType = m.optString("type", "video")
                    mediaList.add(
                        MediaResource(
                            id = m.optString("id", UUID.randomUUID().toString()),
                            type = mType,
                            name = m.optString("name", "مورد مسودة"),
                            icon = MediaResource.iconForType(mType),
                            uri = m.optString("uri", "").ifBlank { null },
                            durationLabel = m.optString("durationLabel", "").ifBlank { null },
                            sizeLabel = m.optString("sizeLabel", "").ifBlank { null },
                            isBRoll = m.optBoolean("isBRoll", false),
                            category = m.optString("category", "").ifBlank { null }
                        )
                    )
                }
            }

            RestoredAiWorkspace(
                selectedRatio = root.optString("selectedRatio", "9:16"),
                inputText = root.optString("inputText", ""),
                contentType = root.optString("contentType", "قصة تاريخية"),
                contentTone = root.optString("contentTone", "ملحمي"),
                targetPlatform = root.optString("targetPlatform", "TikTok"),
                projectTitle = root.optString("projectTitle", "مشروع مستعاد"),
                currentProjectId = root.optString("currentProjectId", "").ifBlank { null },
                videoDuration = root.optString("videoDuration", "30 ثانية"),
                editingStyle = root.optString("editingStyle", "أسلوب 3nvus / نيون داكن"),
                voiceOver = root.optString("voiceOver", "عميق"),
                musicVibe = root.optString("musicVibe", "ملحمية"),
                styleDescription = root.optString("styleDescription", ""),
                selectedTemplate = root.optString("selectedTemplate", "تلقائي"),
                videoQuality = root.optString("videoQuality", "عالية الدقة 1080p"),
                ambientSound = root.optString("ambientSound", "تلقائي"),
                finalVideoPath = root.optString("finalVideoPath", ""),
                appState = try { AppState.valueOf(root.optString("appState", "INPUT")) } catch (_: Exception) { AppState.INPUT },
                scenes = scenes,
                mediaResources = mediaList
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==================== Pro Editor Workspace Auto-Save ====================

    fun scheduleProEditorSave(state: EditorProjectState, debounceMs: Long = 1000L) {
        val currentHash = state.projectId.hashCode() xor state.tracks.size xor state.tracks.sumOf { it.clips.size }
        if (currentHash == lastSavedProHash) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            performProEditorSave(state)
        }
    }

    suspend fun performProEditorSave(state: EditorProjectState) = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext
        _status.value = _status.value.copy(isSaving = true)
        try {
            val json = serializeProState(state)
            repo.saveWorkspace(
                id = DRAFT_ID_PRO,
                workspaceType = "PRO_EDITOR",
                projectId = state.projectId,
                title = state.title,
                contentText = "مشروع محرر: ${state.tracks.sumOf { it.clips.size }} مقاطع",
                stateJson = json,
                lastScreen = "PRO_EDITOR"
            )
            lastSavedProHash = state.projectId.hashCode() xor state.tracks.size xor state.tracks.sumOf { it.clips.size }
            val now = System.currentTimeMillis()
            _status.value = _status.value.copy(
                isSaving = false,
                lastSavedTime = now,
                formattedLastSaved = timeFormat.format(Date(now)),
                hasRecoverableDraft = true,
                draftTitle = state.title,
                draftSnippet = "${state.tracks.size} مسارات، ${state.tracks.sumOf { it.clips.size }} عناصر",
                draftTimestamp = now
            )
        } catch (e: Exception) {
            _status.value = _status.value.copy(isSaving = false)
        }
    }

    private fun serializeProState(state: EditorProjectState): String {
        val root = JSONObject()
        root.put("projectId", state.projectId)
        root.put("title", state.title)
        root.put("currentTimeMs", state.currentTimeMs)
        root.put("totalDurationMs", state.totalDurationMs)
        root.put("zoomLevel", state.zoomLevel.toDouble())
        root.put("isSmartCaptionsEnabled", state.isSmartCaptionsEnabled)
        root.put("isMagneticTimeline", state.isMagneticTimeline)
        root.put("workspaceMode", state.workspaceMode.name)

        val tracksArr = JSONArray()
        state.tracks.forEach { track ->
            val tObj = JSONObject()
            tObj.put("id", track.id)
            tObj.put("type", track.type.name)
            tObj.put("name", track.name)
            tObj.put("isMuted", track.isMuted)
            tObj.put("isLocked", track.isLocked)

            val clipsArr = JSONArray()
            track.clips.forEach { clip ->
                val cObj = JSONObject()
                cObj.put("id", clip.id)
                cObj.put("title", clip.title)
                cObj.put("resourceUri", clip.resourceUri)
                cObj.put("startOffsetMs", clip.startOffsetMs)
                cObj.put("durationMs", clip.durationMs)
                cObj.put("trackIndex", clip.trackIndex)
                cObj.put("type", clip.type.name)
                cObj.put("volume", clip.volume.toDouble())
                cObj.put("speed", clip.speed.toDouble())
                cObj.put("blendMode", clip.blendMode)

                // Keyframes
                val kfArr = JSONArray()
                clip.keyframes.forEach { kf ->
                    val kfObj = JSONObject()
                    kfObj.put("timeMs", kf.timeMs)
                    kfObj.put("property", kf.property)
                    kfObj.put("value", kf.value.toDouble())
                    kfArr.put(kfObj)
                }
                cObj.put("keyframes", kfArr)

                clipsArr.put(cObj)
            }
            tObj.put("clips", clipsArr)
            tracksArr.put(tObj)
        }
        root.put("tracks", tracksArr)

        return root.toString()
    }

    fun deserializeProState(jsonStr: String): EditorProjectState? {
        return try {
            val root = JSONObject(jsonStr)
            val tracks = ArrayList<EditorTrack>()
            val tracksArr = root.optJSONArray("tracks")
            if (tracksArr != null) {
                for (i in 0 until tracksArr.length()) {
                    val t = tracksArr.getJSONObject(i)
                    val tType = try { TrackType.valueOf(t.getString("type")) } catch (_: Exception) { TrackType.VIDEO }
                    val clips = ArrayList<EditorClip>()
                    val clipsArr = t.optJSONArray("clips")
                    if (clipsArr != null) {
                        for (j in 0 until clipsArr.length()) {
                            val c = clipsArr.getJSONObject(j)
                            val cType = try { TrackType.valueOf(c.getString("type")) } catch (_: Exception) { TrackType.VIDEO }

                            val kfList = ArrayList<EditorKeyframe>()
                            val kfArr = c.optJSONArray("keyframes")
                            if (kfArr != null) {
                                for (k in 0 until kfArr.length()) {
                                    val kf = kfArr.getJSONObject(k)
                                    kfList.add(
                                        EditorKeyframe(
                                            timeMs = kf.optLong("timeMs", 0L),
                                            property = kf.optString("property", "scale"),
                                            value = kf.optDouble("value", 1.0).toFloat()
                                        )
                                    )
                                }
                            }

                            clips.add(
                                EditorClip(
                                    id = c.optString("id", UUID.randomUUID().toString()),
                                    title = c.optString("title", "مقطع"),
                                    resourceUri = c.optString("resourceUri", ""),
                                    startOffsetMs = c.optLong("startOffsetMs", 0L),
                                    durationMs = c.optLong("durationMs", 5000L),
                                    trackIndex = c.optInt("trackIndex", i),
                                    type = cType,
                                    color = when (cType) {
                                        TrackType.TEXT -> Color(0xFFFACC15)
                                        TrackType.VIDEO -> Color(0xFF3B82F6)
                                        TrackType.AUDIO -> Color(0xFF10B981)
                                        TrackType.OVERLAY -> Color(0xFFEC4899)
                                    },
                                    volume = c.optDouble("volume", 1.0).toFloat(),
                                    speed = c.optDouble("speed", 1.0).toFloat(),
                                    blendMode = c.optString("blendMode", "Normal"),
                                    keyframes = kfList
                                )
                            )
                        }
                    }

                    tracks.add(
                        EditorTrack(
                            id = t.optString("id", UUID.randomUUID().toString()),
                            type = tType,
                            name = t.optString("name", "مسار"),
                            clips = clips,
                            isMuted = t.optBoolean("isMuted", false),
                            isLocked = t.optBoolean("isLocked", false)
                        )
                    )
                }
            }

            EditorProjectState(
                projectId = root.optString("projectId", UUID.randomUUID().toString()),
                title = root.optString("title", "مشروع محرر مستعاد"),
                tracks = if (tracks.isNotEmpty()) tracks else listOf(
                    EditorTrack(type = TrackType.TEXT, name = "نصوص"),
                    EditorTrack(type = TrackType.VIDEO, name = "فيديو"),
                    EditorTrack(type = TrackType.AUDIO, name = "صوت")
                ),
                currentTimeMs = root.optLong("currentTimeMs", 0L),
                totalDurationMs = root.optLong("totalDurationMs", 30000L),
                zoomLevel = root.optDouble("zoomLevel", 1.0).toFloat(),
                isSmartCaptionsEnabled = root.optBoolean("isSmartCaptionsEnabled", false),
                isMagneticTimeline = root.optBoolean("isMagneticTimeline", true),
                workspaceMode = try { WorkspaceMode.valueOf(root.optString("workspaceMode", "EDIT")) } catch (_: Exception) { WorkspaceMode.EDIT }
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==================== Periodic Auto-Save Timer ====================

    /**
     * يبدأ دورة حفظ دورية كل 15 ثانية لمساحة عمل الذكاء الاصطناعي
     */
    fun startPeriodicAiAutoSave(stateSupplier: () -> ProjectState) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(15000L) // كل 15 ثانية
                val state = stateSupplier()
                if (state.inputText.isNotBlank() || state.libraryScenes.isNotEmpty()) {
                    performAiStudioSave(state)
                }
            }
        }
    }

    /**
     * يبدأ دورة حفظ دورية كل 15 ثانية للمحرر اليدوي
     */
    fun startPeriodicProAutoSave(stateSupplier: () -> EditorProjectState) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(15000L) // كل 15 ثانية
                val state = stateSupplier()
                performProEditorSave(state)
            }
        }
    }

    fun stopPeriodicAutoSave() {
        periodicJob?.cancel()
        periodicJob = null
    }

    // ==================== Recovery & Clearing ====================

    suspend fun getAiDraft(): WorkspaceDraftEntity? = withContext(Dispatchers.IO) {
        repository?.getDraft(DRAFT_ID_AI)
    }

    suspend fun getProDraft(): WorkspaceDraftEntity? = withContext(Dispatchers.IO) {
        repository?.getDraft(DRAFT_ID_PRO)
    }

    suspend fun clearAiDraft() = withContext(Dispatchers.IO) {
        repository?.deleteDraft(DRAFT_ID_AI)
        lastSavedAiHash = 0
        _status.value = _status.value.copy(hasRecoverableDraft = false)
    }

    suspend fun clearProDraft() = withContext(Dispatchers.IO) {
        repository?.deleteDraft(DRAFT_ID_PRO)
        lastSavedProHash = 0
        _status.value = _status.value.copy(hasRecoverableDraft = false)
    }
}

/**
 * البيانات المسترجعة لمساحة عمل الذكاء الاصطناعي
 */
data class RestoredAiWorkspace(
    val selectedRatio: String,
    val inputText: String,
    val contentType: String,
    val contentTone: String,
    val targetPlatform: String,
    val projectTitle: String,
    val currentProjectId: String?,
    val videoDuration: String,
    val editingStyle: String,
    val voiceOver: String,
    val musicVibe: String,
    val styleDescription: String,
    val selectedTemplate: String,
    val videoQuality: String,
    val ambientSound: String,
    val finalVideoPath: String,
    val appState: AppState,
    val scenes: List<Scene>,
    val mediaResources: List<MediaResource>
)
