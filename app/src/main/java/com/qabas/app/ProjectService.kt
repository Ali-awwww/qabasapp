package com.qabas.app

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ProjectService(private val context: Context) {
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

    /** قائمة سريعة من Room فقط — لا شبكة */
    suspend fun getLocalProjects(): List<Project> = withContext(Dispatchers.IO) {
        repository.getAllProjectsOnce()
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
            val localProjects = repository.getAllProjectsOnce().map { it.toProject() }

            if (!syncCloud) {
                return@withContext localProjects.sortedByDescending { it.updatedAt }
            }

            // 1. الأولوية الأولى: Supabase
            if (SupabaseServices.isSupabaseAvailable) {
                try {
                    val supabaseRows = SupabaseServices.Database.getUserProjects()
                    if (supabaseRows.isNotEmpty()) {
                        val merged = LinkedHashMap<String, Project>(localProjects.size + supabaseRows.size)
                        localProjects.forEach { merged[it.id] = it }

                        val toUpsert = ArrayList<ProjectEntity>()
                        supabaseRows.forEach { row ->
                            val meta = row.metadata
                            val ideaStr = meta["idea"] ?: (row.description ?: "")
                            val cloudTimestamp = (row.updatedAt ?: row.createdAt)?.let {
                                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
                            } ?: 0L
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
                                workspaceJson = ProjectWorkspaceCodec.decodeStored(meta[ProjectWorkspaceCodec.METADATA_KEY]) ?: ""
                            )
                            val existing = merged[proj.id]
                            if (existing == null || existing.synced && proj.updatedAt >= existing.updatedAt) {
                                merged[proj.id] = proj
                                toUpsert.add(proj.toEntity())
                            }
                        }
                        if (toUpsert.isNotEmpty()) {
                            repository.insertAll(toUpsert)
                        }
                        return@withContext merged.values.sortedByDescending { it.updatedAt }
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
        var isSynced = false
        // حفظ سحابي أساسي في Supabase
        if (SupabaseServices.isSupabaseAvailable && UserPreferencesManager.networkStatus.value == ConnectivityObserver.Status.Available) {
            val result = runCatching {
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
                SupabaseServices.Database.saveFullProject(
                    id = project.id,
                    title = project.title,
                    description = project.idea.take(200),
                    status = project.status.ifBlank { "draft" },
                    cost = project.cost,
                    tags = listOf("qabas_project"),
                    metadata = metadata,
                    createdAt = java.time.Instant.ofEpochMilli(project.createdAt.coerceAtLeast(1L)).toString(),
                    updatedAt = java.time.Instant.ofEpochMilli(project.updatedAt.coerceAtLeast(project.createdAt).coerceAtLeast(1L)).toString()
                )
            }
            isSynced = result.isSuccess
        }

        repository.insert(project.copy(synced = isSynced).toEntity())

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
        val online = SupabaseServices.isSupabaseAvailable &&
            UserPreferencesManager.networkStatus.value == ConnectivityObserver.Status.Available
        if (online) {
            val deletedRemotely = runCatching { SupabaseServices.Database.deleteProject(id) }.getOrDefault(false)
            if (deletedRemotely) {
                repository.delete(id)
            } else {
                android.util.Log.w("ProjectService", "Keeping local project $id because cloud deletion failed")
            }
        } else {
            // Without an outbox/tombstone, offline deletion is local-only and may
            // reappear after the next cloud sync; this is documented until schema v2.
            repository.delete(id)
        }
    }

    suspend fun syncPendingProjects() = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable || UserPreferencesManager.networkStatus.value != ConnectivityObserver.Status.Available) return@withContext
        
        val unsynced = repository.getAllProjectsOnce().filter { !it.synced }
        unsynced.forEach { entity ->
            val result = runCatching {
                val cloudWorkspaceJson = uploadWorkspaceRecording(entity.toProject())
                val metadata = mapOf(
                    "idea" to entity.idea,
                    "analysis" to entity.analysis,
                    "resources" to entity.resources,
                    "settings" to (if (ProjectWorkspaceCodec.decodeStored(entity.settings) != null) "" else entity.settings),
                    "script" to entity.script,
                    "finalVideoPath" to entity.finalVideoPath,
                    "lastScreen" to entity.lastScreen,
                    ProjectWorkspaceCodec.METADATA_KEY to cloudWorkspaceJson
                )
                SupabaseServices.Database.saveFullProject(
                    id = entity.id,
                    title = entity.title,
                    description = entity.idea.take(200),
                    status = entity.status.ifBlank { "draft" },
                    cost = entity.cost,
                    tags = listOf("qabas_project"),
                    metadata = metadata,
                    createdAt = java.time.Instant.ofEpochMilli(entity.createdAt.coerceAtLeast(1L)).toString(),
                    updatedAt = java.time.Instant.ofEpochMilli(entity.updatedAt.coerceAtLeast(entity.createdAt).coerceAtLeast(1L)).toString()
                )
            }
            if (result.isSuccess) {
                repository.update(entity.copy(synced = true))
            }
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
