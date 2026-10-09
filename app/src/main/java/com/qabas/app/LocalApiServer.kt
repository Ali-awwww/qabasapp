package com.qabas.app

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

class LocalApiServer(private val context: Context, private val apiKey: String = "QABAS_API_KEY_123") {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun start(port: Int = 8080) {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept()
                    socket?.let { handleClient(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        scope.cancel()
    }

    private fun handleClient(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = OutputStreamWriter(socket.getOutputStream())

                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@launch

                val method = parts[0]
                val path = parts[1]

                var authHeader: String? = null
                var contentLength = 0
                while (true) {
                    val currentLine = reader.readLine() ?: break
                    if (currentLine.isEmpty()) break
                    if (currentLine.lowercase().startsWith("authorization:")) {
                        authHeader = currentLine.substringAfter(":").trim()
                    }
                    if (currentLine.lowercase().startsWith("content-length:")) {
                        contentLength = currentLine.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                if (authHeader != "Bearer $apiKey") {
                    sendResponse(writer, 401, JSONObject().put("error", "Unauthorized: Invalid API Key").toString())
                    return@launch
                }

                val bodyChars = CharArray(contentLength)
                reader.read(bodyChars)
                val bodyString = String(bodyChars)
                
                if (method != "POST") {
                    sendResponse(writer, 405, JSONObject().put("error", "Method Not Allowed").toString())
                    return@launch
                }

                val requestJson = try { JSONObject(bodyString) } catch (e: Exception) { JSONObject() }

                when (path) {
                    "/api/generate-script" -> {
                        val idea = requestJson.optString("idea", "")
                        val style = requestJson.optString("styleDescription", "")
                        val scenes = AppServices.generateScript(idea, style, "", "")
                        val responseJson = JSONObject()
                        val scenesArray = org.json.JSONArray()
                        scenes.forEach { scene ->
                            val sObj = JSONObject()
                            sObj.put("title", scene.title)
                            sObj.put("description", scene.description)
                            sObj.put("duration", scene.durationInSeconds)
                            scenesArray.put(sObj)
                        }
                        responseJson.put("status", "success")
                        responseJson.put("scenes", scenesArray)
                        sendResponse(writer, 200, responseJson.toString())
                    }
                    "/api/generate-video" -> {
                        val description = requestJson.optString("description", "")
                        val duration = requestJson.optInt("duration", 5)
                        val videoPath = AppServices.generateVideo(
                            sceneDescription = description,
                            durationInSeconds = duration,
                            tempo = Translator.tr("متوسط"),
                            colors = Translator.tr("افتراضي"),
                            transitions = Translator.tr("عادي"),
                            textAnim = Translator.tr("عادي"),
                            visualEffect = Translator.tr("لا يوجد")
                        )
                        val responseJson = JSONObject()
                        responseJson.put("status", "success")
                        responseJson.put("videoPath", videoPath)
                        sendResponse(writer, 200, responseJson.toString())
                    }
                    "/api/analyze-style" -> {
                        val url = requestJson.optString("url", "")
                        val analysis = AppServices.analyzeVideoStyle(url)
                        val responseJson = JSONObject()
                        responseJson.put("status", "success")
                        responseJson.put("dominantColors", analysis.dominantColors)
                        responseJson.put("transitionSpeed", analysis.transitionSpeed)
                        responseJson.put("overallRhythm", analysis.overallRhythm)
                        sendResponse(writer, 200, responseJson.toString())
                    }
                    else -> {
                        sendResponse(writer, 404, JSONObject().put("error", "Not Found").toString())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket.close()
            }
        }
    }

    private fun sendResponse(writer: OutputStreamWriter, statusCode: Int, body: String) {
        val statusMessage = when (statusCode) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Internal Server Error"
        }
        writer.write("HTTP/1.1 $statusCode $statusMessage\r\n")
        writer.write("Content-Type: application/json; charset=UTF-8\r\n")
        writer.write("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }
}
