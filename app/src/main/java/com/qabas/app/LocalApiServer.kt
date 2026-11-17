package com.qabas.app

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

class LocalApiServer(
    private val context: Context,
    private val apiKeyProvider: () -> String = {
        val agentKey = AgentPrefs.getString(context, "api_server_key", "").trim()
        if (agentKey.isNotBlank()) agentKey
        else context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString("user_api_key", null).orEmpty().trim()
    }
) {
    private val lifecycleLock = Any()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    fun isServerActive(): Boolean = isRunning

    fun start(port: Int = 8080) {
        synchronized(lifecycleLock) {
            if (isRunning) return

            val socket = ServerSocket()
            try {
                val loopback = java.net.InetAddress.getByName("127.0.0.1")
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(loopback, port), 50)

                serverSocket = socket
                isRunning = true
                acceptJob = scope.launch {
                    try {
                        while (isActive && isRunning) {
                            handleClient(socket.accept())
                        }
                    } catch (e: java.net.SocketException) {
                        if (isRunning) e.printStackTrace()
                    } catch (e: Exception) {
                        if (isRunning) e.printStackTrace()
                    } finally {
                        synchronized(lifecycleLock) {
                            if (serverSocket === socket) {
                                isRunning = false
                                serverSocket = null
                                acceptJob = null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    socket.close()
                } catch (_: Exception) {}
                throw e
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            isRunning = false
            val socket = serverSocket
            serverSocket = null
            acceptJob?.cancel()
            acceptJob = null
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!socket.inetAddress.isLoopbackAddress) {
                    socket.close()
                    return@launch
                }

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@launch

                val method = parts[0].uppercase()
                val rawTarget = parts[1]
                val path = rawTarget.substringBefore("?")

                var authHeader: String? = null
                var contentLength = 0
                while (true) {
                    val currentLine = reader.readLine() ?: break
                    if (currentLine.isEmpty()) break
                    if (currentLine.lowercase().startsWith("authorization:")) {
                        authHeader = currentLine.substringAfter(":").trim()
                    }
                    if (currentLine.lowercase().startsWith("content-length:")) {
                        contentLength = (currentLine.substringAfter(":").trim().toIntOrNull() ?: 0).coerceIn(0, 1_048_576)
                    }
                }

                if (method == "OPTIONS") {
                    sendResponse(writer, 200, JSONObject().put("status", "ok").toString())
                    return@launch
                }

                if (method == "GET" && path == "/api/health") {
                    val healthJson = JSONObject().apply {
                        put("status", "healthy")
                        put("service", "Qabas Local API")
                        put("aiKeyConfigured", ServiceAvailabilityRegistry.hasCloudLlmKey(context))
                        put("localModelInstalled", ServiceAvailabilityRegistry.hasLocalAiModel(context))
                    }
                    sendResponse(writer, 200, healthJson.toString())
                    return@launch
                }

                val expectedKey = apiKeyProvider().trim()
                if (expectedKey.isBlank() || authHeader != "Bearer $expectedKey") {
                    sendResponse(writer, 401, JSONObject().put("error", "Unauthorized: Valid API Key required").toString())
                    return@launch
                }

                val bodyString = if (contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = reader.read(bodyChars, readTotal, contentLength - readTotal)
                        if (count <= 0) break
                        readTotal += count
                    }
                    String(bodyChars, 0, readTotal)
                } else ""

                if (method == "GET") {
                    when (path) {
                        "/api/hadith/random" -> {
                            val hadith = SahihHadithsRepository.getRandomDailyHadith()
                            val json = JSONObject().apply {
                                put("status", "success")
                                put("id", hadith.id)
                                put("book", hadith.bookType.name)
                                put("chapter", hadith.chapterTitle)
                                put("narrator", hadith.narrator)
                                put("matn", hadith.matn)
                                put("reference", hadith.reference)
                            }
                            sendResponse(writer, 200, json.toString())
                            return@launch
                        }
                        else -> {
                            sendResponse(writer, 404, JSONObject().put("error", "Not Found").toString())
                            return@launch
                        }
                    }
                }

                if (method != "POST") {
                    sendResponse(writer, 405, JSONObject().put("error", "Method Not Allowed").toString())
                    return@launch
                }

                val requestJson = runCatching { JSONObject(bodyString) }.getOrDefault(JSONObject())

                when (path) {
                    "/api/generate-script" -> {
                        if (!ServiceAvailabilityRegistry.hasAnyAiAssistantEngine(context)) {
                            sendResponse(writer, 503, JSONObject().put("error", "AI API key or local model is required").toString())
                            return@launch
                        }
                        val idea = requestJson.optString("idea", "").trim()
                        val style = requestJson.optString("styleDescription", "").trim()
                        if (idea.isBlank()) {
                            sendResponse(writer, 400, JSONObject().put("error", "Missing required field: idea").toString())
                            return@launch
                        }
                        val scenes = AppServices.generateScript(idea, style, "", "")
                        val scenesArray = JSONArray()
                        scenes.forEach { scene ->
                            scenesArray.put(JSONObject().apply {
                                put("title", scene.title)
                                put("description", scene.description)
                                put("duration", scene.durationInSeconds)
                            })
                        }
                        val responseJson = JSONObject().apply {
                            put("status", "success")
                            put("scenes", scenesArray)
                        }
                        sendResponse(writer, 200, responseJson.toString())
                    }
                    "/api/generate-video" -> {
                        val description = requestJson.optString("description", "").trim()
                        if (description.isBlank()) {
                            sendResponse(writer, 400, JSONObject().put("error", "Missing required field: description").toString())
                            return@launch
                        }
                        val duration = requestJson.optInt("duration", 5).coerceIn(1, 60)
                        val videoPath = AppServices.generateVideo(
                            sceneDescription = description,
                            durationInSeconds = duration,
                            tempo = Translator.tr("متوسط"),
                            colors = Translator.tr("افتراضي"),
                            transitions = Translator.tr("عادي"),
                            textAnim = Translator.tr("عادي"),
                            visualEffect = Translator.tr("لا يوجد")
                        )
                        val responseJson = JSONObject().apply {
                            put("status", "success")
                            put("videoPath", videoPath)
                        }
                        sendResponse(writer, 200, responseJson.toString())
                    }
                    "/api/analyze-style" -> {
                        val url = requestJson.optString("url", "").trim()
                        if (url.isBlank()) {
                            sendResponse(writer, 400, JSONObject().put("error", "Missing required field: url").toString())
                            return@launch
                        }
                        val analysis = AppServices.analyzeVideoStyle(url)
                        val responseJson = JSONObject().apply {
                            put("status", "success")
                            put("dominantColors", analysis.dominantColors)
                            put("transitionSpeed", analysis.transitionSpeed)
                            put("overallRhythm", analysis.overallRhythm)
                        }
                        sendResponse(writer, 200, responseJson.toString())
                    }
                    else -> {
                        sendResponse(writer, 404, JSONObject().put("error", "Not Found").toString())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun sendResponse(writer: OutputStreamWriter, statusCode: Int, body: String) {
        val statusMessage = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            503 -> "Service Unavailable"
            else -> "Internal Server Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.write("HTTP/1.1 $statusCode $statusMessage\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Access-Control-Allow-Headers: Authorization, Content-Type\r\n")
        writer.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.write("Content-Type: application/json; charset=UTF-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }
}
