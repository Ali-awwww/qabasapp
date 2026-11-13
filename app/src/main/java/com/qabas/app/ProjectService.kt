package com.qabas.app

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ProjectService(private val context: Context) {
    private companion object {
        const val DELETE_TOMBSTONES_PREFS = "qabas_project_sync"
        const val DELETE_TOMBSTONES_KEY = "deleted_project_ids"
        private val localMutationMutex = Mutex()
        private val cloudSyncMutex = Mutex()
    }
    @Immutable
    data class Project(
        val id: String = "",
        val title: String = "",
        val idea: String = "",
        val analysis: String = "",
        val resources: String = "",
        val settings: String = "",
        val script: String = "",
        val finalVideoPath: String = "",
        val cost: Double = 0.0,
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L,
        val status: String = "",
        val lastScreen: String = "",
        val workspaceJson: String = "",
        val synced: Boolean = true
    )

    private val repository: ProjectRepository by lazy {
        ProjectRepository(AppDatabase.getDatabase(context).projectDao())
    }

    private val syncPrefs by lazy {
        context.getSharedPreferences(DELETE_TOMBSTONES_PREFS, Context.MODE_PRIVATE)
    }

    private fun deletedProjectIds(): Set<String> = synchronized(syncPrefs) {
        syncPrefs.getStringSet(DELETE_TOMBSTONES_KEY, emptySet()).orEmpty().toSet()
    }

    private fun markProjectDeleted(id: String): Boolean = synchronized(syncPrefs) {
        val ids = deletedProjectIds().toMutableSet().apply { add(id) }
        syncPrefs.edit().putStringSet(DELETE_TOMBSTONES_KEY, ids).commit()
    }

    private fun clearProjectDeletion(id: String): Boolean = synchronized(syncPrefs) {
        val ids = deletedProjectIds().toMutableSet()
        if (ids.remove(id)) {
            syncPrefs.edit().putStringSet(DELETE_TOMBSTONES_KEY, ids).commit()
        } else true
    }

    /** قائمة سريعة من Room فقط — لا شبكة */
    suspend fun getLocalProjects(): List<Project> = withContext(Dispatchers.IO) {
        val deletedIds = deletedProjectIds()
        repository.getAllProjectsOnce()
            .filterNot { it.id in deletedIds }
            .map { it.toProject() }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * الافتراضي: محلي فقط.
     * syncCloud = true عند شاشة التحميل / سحب للتحديث فقط.
     * يعتمد على Supabase كخيار سحابي أساسي، مع الرجوع التلقائي لـ Firebase إذا لزم الأمر.
     */
    suspend fun getAllProjects(syncCloud: Boolean = false): List<Project> =
        withContext(Dispatchers.IO) {
            val deletedIds = deletedProjectIds()
            val localProjects = repository.getAllProjectsOnce()
                .filterNot { it.id in deletedIds }
                .map { it.toProject() }

            if (!syncCloud) {
                return@withContext localProjects.sortedByDescending { it.updatedAt }
            }

            // 1. الأولوية الأولى: Supabase
            if (SupabaseServices.isSupabaseAvailable) {
                try {
                    val supabaseRows = SupabaseServices.Database.getUserProjects()
                    if (supabaseRows.isNotEmpty()) {
                        val cloudProjects = ArrayList<ProjectEntity>(supabaseRows.size)
                        supabaseRows.forEach { row ->
                            if (row.id in deletedIds) return@forEach
                            val meta = row.metadata
                            val ideaStr = meta["idea"] ?: (row.description ?: "")
                            val cloudTimestamp = (row.updatedAt ?: row.createdAt)?.let {
                                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
                            } ?: 0L
                            val workspaceJson = hydrateWorkspaceRecording(
                                ProjectWorkspaceCodec.decodeStored(meta[ProjectWorkspaceCodec.METADATA_KEY]).orEmpty(),
                                row.id
                            )
                            val proj = Project(
                                id = row.id,
                                title = row.title,
                                idea = ideaStr,
                                analysis = meta["analysis"] ?: "",
                                resources = meta["resources"] ?: "",
                                settings = meta["settings"] ?: "",
                                script = meta["script"] ?: "",
                                finalVideoPath = meta["finalVideoPath"] ?: "",
                                cost = row.cost,
                                createdAt = cloudTimestamp,
                                updatedAt = cloudTimestamp,
                                status = row.status,
                                lastScreen = meta["lastScreen"] ?: "",
                                workspaceJson = workspaceJson
                            )
                            cloudProjects.add(proj.toEntity())
                        }
                        if (cloudProjects.isNotEmpty()) {
                            repository.mergeCloudProjects(cloudProjects)
                        }
                        return@withContext repository.getAllProjectsOnce()
                            .filterNot { it.id in deletedIds }
                            .map { it.toProject() }
                            .sortedByDescending { it.updatedAt }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ProjectService", "Supabase sync error: ${e.message}")
                }
            }

            localProjects.sortedByDescending { it.updatedAt }
        }

    suspend fun syncFromCloud(): List<Project> = getAllProjects(syncCloud = true)

    suspend fun getProject(id: String): Project? = withContext(Dispatchers.IO) {
        repository.getProject(id)?.toProject()
    }

    suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
        val (pending, deletionStateCleared) = localMutationMutex.withLock {
            val localProject = repository.savePending(project.toEntity())
            localProject to clearProjectDeletion(project.id)
        }

        // Room is the durable outbox: local success does not depend on network availability.
        if (deletionStateCleared && SupabaseServices.isSupabaseAvailable &&
            UserPreferencesManager.networkStatus.value == ConnectivityObserver.Status.Available
        ) {
            syncProject(pending.id)
        } else if (!deletionStateCleared) {
            android.util.Log.w("ProjectService", "Project sync deferred because delete state was not persisted")
        }

        try {
            if (project.analysis.isNotEmpty()) {
                val obj = org.json.JSONObject(project.analysis)
                val style = obj.optString("suggestedStyle")
                if (style.isNotBlank()) TasteManager.registerPreference(context, "STYLE", style, 1)
                val tone = obj.optString("tone")
                if (tone.isNotBlank()) TasteManager.registerPreference(context, "TONE", tone, 1)
                val keywords = obj.optJSONArray("keywords")
                if (keywords != null && keywords.length() > 0) {
                    for (i in 0 until keywords.length()) {
                        TasteManager.registerPreference(context, "TOPIC", keywords.getString(i), 1)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        val deletionStatePersisted = localMutationMutex.withLock {
            if (!markProjectDeleted(id)) return@withLock false
            repository.delete(id)
            true
        }
        if (!deletionStatePersisted) {
            android.util.Log.w("ProjectService", "Project delete deferred because its pending state was not persisted")
            return@withContext
        }
        syncDeletedProject(id)
    }

    suspend fun syncPendingProjects() = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable || UserPreferencesManager.networkStatus.value != ConnectivityObserver.Status.Available) return@withContext

        deletedProjectIds().forEach { id ->
            syncDeletedProject(id)
        }

        val pendingIds = repository.getAllProjectsOnce().filterNot { it.synced }.map { it.id }
        pendingIds.forEach { id ->
            syncProject(id)
        }
    }

    /** Serializes cloud writes and always uploads the latest local revision for this project. */
    private suspend fun syncProject(id: String): Boolean = cloudSyncMutex.withLock {
        if (!SupabaseServices.isSupabaseAvailable ||
            UserPreferencesManager.networkStatus.value != ConnectivityObserver.Status.Available
        ) return@withLock false

        val project = repository.getProject(id)?.toProject() ?: return@withLock false
        if (project.synced) return@withLock true

        try {
            val cloudWorkspaceJson = uploadWorkspaceRecording(project)
            val metadata = mapOf(
                "idea" to project.idea,
                "analysis" to project.analysis,
                "resources" to project.resources,
                "settings" to project.settings,
                "script" to project.script,
                "finalVideoPath" to project.finalVideoPath,
                "lastScreen" to project.lastScreen,
                ProjectWorkspaceCodec.METADATA_KEY to cloudWorkspaceJson
            )
            val cloudAccepted = SupabaseServices.Database.saveFullProject(
                id = project.id,
                title = project.title,
                description = project.idea.take(200),
                status = project.status.ifBlank { "draft" },
                cost = project.cost,
                tags = listOf("qabas_project"),
                metadata = metadata,
                createdAt = java.time.Instant.ofEpochMilli(project.createdAt.coerceAtLeast(1L)).toString(),
                updatedAt = java.time.Instant.ofEpochMilli(
                    project.updatedAt.coerceAtLeast(project.createdAt).coerceAtLeast(1L)
                ).toString()
            )
            // Do not let a completed upload mark a newer local edit as synchronized.
            val latestRevision = repository.getProject(project.id)?.updatedAt ?: return@withLock false
            if (!shouldMarkProjectSynced(cloudAccepted, project.updatedAt, latestRevision)) return@withLock false
            repository.markSyncedIfRevisionMatches(project.id, project.updatedAt) == 1
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("ProjectService", "Project sync deferred (${error.javaClass.simpleName})")
            false
        }
    }

    /** A pending delete is retained until Supabase confirms it; serialized against project uploads. */
    private suspend fun syncDeletedProject(id: String): Boolean = cloudSyncMutex.withLock {
        if (id !in deletedProjectIds()) return@withLock true
        if (!SupabaseServices.isSupabaseAvailable ||
            UserPreferencesManager.networkStatus.value != ConnectivityObserver.Status.Available
        ) return@withLock false

        try {
            val deleted = SupabaseServices.Database.deleteProject(id)
            if (deleted) clearProjectDeletion(id) else false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("ProjectService", "Project deletion sync deferred (${error.javaClass.simpleName})")
            false
        }
    }

    private suspend fun uploadWorkspaceRecording(project: Project): String {
        val workspaceJson = project.workspaceJson
        val snapshot = ProjectWorkspaceCodec.decode(workspaceJson) ?: return workspaceJson
        val voiceOver = snapshot.voiceOver.trim()
        if (voiceOver.isBlank() || voiceOver.startsWith("storage://")) return workspaceJson

        val localPath = if (voiceOver.startsWith("file://")) {
            runCatching { android.net.Uri.parse(voiceOver).path }.getOrNull()
        } else voiceOver
        val recording = localPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L } ?: return workspaceJson
        val storagePath = SupabaseServices.Storage.uploadPrivateProjectRecording(project.id, recording)
            ?: return workspaceJson
        return runCatching {
            JSONObject(workspaceJson)
                .put("voiceOver", "storage://project-recordings/$storagePath")
                .toString()
        }.getOrDefault(workspaceJson)
    }

    private suspend fun hydrateWorkspaceRecording(workspaceJson: String, projectId: String): String {
        val snapshot = ProjectWorkspaceCodec.decode(workspaceJson) ?: return workspaceJson
        val reference = snapshot.voiceOver.trim()
        if (!reference.startsWith("storage://project-recordings/")) return workspaceJson

        val safeProjectId = projectId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
        val remoteFileName = reference.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .take(90)
            .ifBlank { "voice.m4a" }
        val destination = File(context.filesDir, "recordings/cloud_${safeProjectId}_$remoteFileName")
        if (!destination.exists()) {
            val downloaded = SupabaseServices.Storage.downloadPrivateProjectRecording(reference, destination)
            if (!downloaded) return workspaceJson
        }
        return runCatching {
            JSONObject(workspaceJson).put("voiceOver", destination.absolutePath).toString()
        }.getOrDefault(workspaceJson)
    }

    private fun ProjectEntity.toProject() = Project(
        id = id, title = title, idea = idea, analysis = analysis,
        resources = resources, settings = settings, script = script,
        finalVideoPath = finalVideoPath, cost = cost,
        createdAt = createdAt, updatedAt = updatedAt,
        status = status, lastScreen = lastScreen,
        workspaceJson = ProjectWorkspaceCodec.decodeStored(settings).orEmpty(),
        synced = synced
    )

    private fun Project.toEntity() = ProjectEntity(
        id = id, title = title, idea = idea, analysis = analysis,
        resources = resources, script = script,
        settings = if (workspaceJson.isBlank()) settings else ProjectWorkspaceCodec.encodeForLocalStorage(workspaceJson),
        finalVideoPath = finalVideoPath, cost = cost,
        createdAt = createdAt, updatedAt = updatedAt,
        status = status, lastScreen = lastScreen,
        synced = synced
    )
}
