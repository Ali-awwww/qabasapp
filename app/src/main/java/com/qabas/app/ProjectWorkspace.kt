package com.qabas.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, provider-neutral snapshot of the production workspace.
 * The JSON is intentionally stored as one metadata value so old project rows
 * remain readable and can be migrated lazily when they are next saved.
 */
object ProjectWorkspaceCodec {
    const val VERSION = 1
    const val METADATA_KEY = "workspace_v1"
    private const val PREFIX = "workspace_v1:"

    data class Snapshot(
        val selectedRatio: String = "9:16",
        val inputText: String = "",
        val contentType: String = "",
        val contentTone: String = "",
        val targetPlatform: String = "TikTok",
        val marketingGoal: String = "",
        val callToAction: String = "",
        val videoStyleAnalysis: VideoStyleAnalysis? = null,
        val mediaResources: List<MediaResource> = emptyList(),
        val currentProjectId: String? = null,
        val projectTitle: String = "",
        val appState: String = AppState.INPUT.name,
        val videoDuration: String = "",
        val editingStyle: String = "",
        val voiceOver: String = "",
        val musicVibe: String = "",
        val styleDescription: String = "",
        val selectedTemplate: String = "",
        val customTemplate: Template? = null,
        val videoQuality: String = "",
        val ambientSound: String = "",
        val libraryScenes: List<Scene> = emptyList(),
        val scenesToProcess: List<Scene>? = null,
        val finalVideoPath: String = "",
        val sourceType: String = "idea",
        val sourceUris: List<String> = emptyList(),
        val sourceLabel: String? = null,
        val selectedVerseText: String? = null,
        val selectedSurahName: String? = null,
        val selectedVerseNumber: Int? = null
    ) {
        fun applyTo(state: ProjectState): ProjectState = state.copy(
            selectedRatio = selectedRatio,
            inputText = inputText,
            contentType = contentType,
            contentTone = contentTone,
            targetPlatform = targetPlatform,
            marketingGoal = marketingGoal,
            callToAction = callToAction,
            videoStyleAnalysis = videoStyleAnalysis,
            mediaResources = mediaResources,
            currentProjectId = currentProjectId ?: state.currentProjectId,
            projectTitle = projectTitle,
            appState = appState.toAppStateOr(state.appState),
            videoDuration = videoDuration,
            editingStyle = editingStyle,
            voiceOver = voiceOver,
            musicVibe = musicVibe,
            styleDescription = styleDescription,
            selectedTemplate = selectedTemplate,
            customTemplate = customTemplate,
            videoQuality = videoQuality,
            ambientSound = ambientSound,
            libraryScenes = libraryScenes,
            scenesToProcess = scenesToProcess,
            finalVideoPath = finalVideoPath,
            sourceType = sourceType,
            sourceUris = sourceUris,
            sourceLabel = sourceLabel,
            selectedVerseText = selectedVerseText,
            selectedSurahName = selectedSurahName,
            selectedVerseNumber = selectedVerseNumber
        )
    }

    fun encode(state: ProjectState): String = JSONObject().apply {
        put("version", VERSION)
        put("selectedRatio", state.selectedRatio)
        put("inputText", state.inputText)
        put("contentType", state.contentType)
        put("contentTone", state.contentTone)
        put("targetPlatform", state.targetPlatform)
        put("marketingGoal", state.marketingGoal)
        put("callToAction", state.callToAction)
        state.videoStyleAnalysis?.let { put("videoStyleAnalysis", encodeStyle(it)) }
        put("mediaResources", JSONArray().apply { state.mediaResources.forEach { put(encodeMedia(it)) } })
        putNullable("currentProjectId", state.currentProjectId)
        put("projectTitle", state.projectTitle)
        put("appState", state.appState.name)
        put("videoDuration", state.videoDuration)
        put("editingStyle", state.editingStyle)
        put("voiceOver", state.voiceOver)
        put("musicVibe", state.musicVibe)
        put("styleDescription", state.styleDescription)
        put("selectedTemplate", state.selectedTemplate)
        state.customTemplate?.let { put("customTemplate", encodeTemplate(it)) }
        put("videoQuality", state.videoQuality)
        put("ambientSound", state.ambientSound)
        put("libraryScenes", encodeScenes(state.libraryScenes))
        state.scenesToProcess?.let { put("scenesToProcess", encodeScenes(it)) }
        put("finalVideoPath", state.finalVideoPath)
        put("sourceType", state.sourceType)
        put("sourceUris", JSONArray(state.sourceUris))
        putNullable("sourceLabel", state.sourceLabel)
        putNullable("selectedVerseText", state.selectedVerseText)
        putNullable("selectedSurahName", state.selectedSurahName)
        state.selectedVerseNumber?.let { put("selectedVerseNumber", it) }
    }.toString()

    fun encodeForLocalStorage(json: String): String = PREFIX + json

    fun decodeStored(value: String?): String? = when {
        value.isNullOrBlank() -> null
        value.startsWith(PREFIX) -> value.removePrefix(PREFIX)
        value.trimStart().startsWith("{") -> value
        else -> null
    }

    fun decode(json: String?): Snapshot? = runCatching {
        if (json.isNullOrBlank()) return null
        val root = JSONObject(json)
        if (root.optInt("version", 0) != VERSION) return null
        Snapshot(
            selectedRatio = root.optString("selectedRatio", "9:16"),
            inputText = root.optString("inputText"),
            contentType = root.optString("contentType"),
            contentTone = root.optString("contentTone"),
            targetPlatform = root.optString("targetPlatform", "TikTok"),
            marketingGoal = root.optString("marketingGoal"),
            callToAction = root.optString("callToAction"),
            videoStyleAnalysis = root.optJSONObject("videoStyleAnalysis")?.let(::decodeStyle),
            mediaResources = decodeMediaArray(root.optJSONArray("mediaResources")),
            currentProjectId = root.optNullableString("currentProjectId"),
            projectTitle = root.optString("projectTitle"),
            appState = root.optString("appState", AppState.INPUT.name),
            videoDuration = root.optString("videoDuration"),
            editingStyle = root.optString("editingStyle"),
            voiceOver = root.optString("voiceOver"),
            musicVibe = root.optString("musicVibe"),
            styleDescription = root.optString("styleDescription"),
            selectedTemplate = root.optString("selectedTemplate"),
            customTemplate = root.optJSONObject("customTemplate")?.let(::decodeTemplate),
            videoQuality = root.optString("videoQuality"),
            ambientSound = root.optString("ambientSound"),
            libraryScenes = decodeScenes(root.optJSONArray("libraryScenes")),
            scenesToProcess = root.optJSONArray("scenesToProcess")?.let(::decodeScenes),
            finalVideoPath = root.optString("finalVideoPath"),
            sourceType = root.optString("sourceType", "idea"),
            sourceUris = decodeStrings(root.optJSONArray("sourceUris")),
            sourceLabel = root.optNullableString("sourceLabel"),
            selectedVerseText = root.optNullableString("selectedVerseText"),
            selectedSurahName = root.optNullableString("selectedSurahName"),
            selectedVerseNumber = if (root.has("selectedVerseNumber")) root.optInt("selectedVerseNumber") else null
        )
    }.getOrNull()

    private fun encodeStyle(value: VideoStyleAnalysis) = JSONObject().apply {
        put("detectedStyle", value.detectedStyle); put("dominantColors", value.dominantColors)
        put("transitionSpeed", value.transitionSpeed); put("movementPatterns", value.movementPatterns)
        put("overallRhythm", value.overallRhythm); put("audioStyle", value.audioStyle)
        put("typographyStyle", value.typographyStyle); put("contentTone", value.contentTone)
        put("targetAudience", value.targetAudience); put("keywords", JSONArray(value.keywords))
    }

    private fun decodeStyle(value: JSONObject) = VideoStyleAnalysis(
        detectedStyle = value.optString("detectedStyle"), dominantColors = value.optString("dominantColors"),
        transitionSpeed = value.optString("transitionSpeed"), movementPatterns = value.optString("movementPatterns"),
        overallRhythm = value.optString("overallRhythm"), audioStyle = value.optString("audioStyle"),
        typographyStyle = value.optString("typographyStyle"), contentTone = value.optString("contentTone"),
        targetAudience = value.optString("targetAudience"), keywords = decodeStrings(value.optJSONArray("keywords"))
    )

    private fun encodeMedia(value: MediaResource) = JSONObject().apply {
        put("id", value.id); put("type", value.type); put("name", value.name); putNullable("uri", value.uri)
        putNullable("durationLabel", value.durationLabel); putNullable("sizeLabel", value.sizeLabel)
        put("isBRoll", value.isBRoll); putNullable("category", value.category)
    }

    private fun decodeMediaArray(array: JSONArray?): List<MediaResource> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i) ?: continue
            add(MediaResource(value.optString("id"), value.optString("type"), value.optString("name"),
                icon = MediaResource.iconForType(value.optString("type")), uri = value.optNullableString("uri"),
                durationLabel = value.optNullableString("durationLabel"), sizeLabel = value.optNullableString("sizeLabel"),
                isBRoll = value.optBoolean("isBRoll"), category = value.optNullableString("category")))
        }
    }

    private fun encodeTemplate(value: Template) = JSONObject().apply {
        put("id", value.id); put("name", value.name); put("parameters", JSONObject(value.parameters))
        put("colors", value.colors); put("transitions", value.transitions); put("textAnimation", value.textAnimation)
    }

    private fun decodeTemplate(value: JSONObject) = Template(
        id = value.optString("id"), name = value.optString("name"),
        parameters = value.optJSONObject("parameters")?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) } } ?: emptyMap(),
        colors = value.optString("colors"), transitions = value.optString("transitions"), textAnimation = value.optString("textAnimation")
    )

    private fun encodeScenes(values: List<Scene>) = JSONArray().apply { values.forEach { scene -> put(JSONObject().apply {
        put("title", scene.title); put("description", scene.description); put("durationInSeconds", scene.durationInSeconds)
        put("visualEffect", scene.visualEffect); put("tempo", scene.tempo); put("transitionType", scene.transitionType); putNullable("mediaUrl", scene.mediaUrl)
    }) } }

    private fun decodeScenes(array: JSONArray?): List<Scene> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i) ?: continue
            add(Scene(value.optString("title"), value.optString("description"), value.optInt("durationInSeconds"),
                value.optString("visualEffect", "بدون"), value.optString("tempo", "عادي"), value.optString("transitionType", "Fade"), value.optNullableString("mediaUrl")))
        }
    }

    private fun decodeStrings(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) add(array.optString(i))
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun String.toAppStateOr(fallback: AppState): AppState = runCatching { AppState.valueOf(this) }.getOrDefault(fallback)
}
