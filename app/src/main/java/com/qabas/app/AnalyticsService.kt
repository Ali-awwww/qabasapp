package com.qabas.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

interface AnalyticsService {
    fun logScreenView(screenName: String)
    fun logEvent(eventName: String, parameters: Map<String, Any>?)
    fun logProjectCreated(projectId: String, template: String, duration: String)
    fun logVideoGenerated(projectId: String, cost: Double, libraryCount: Int, generatedCount: Int)
    fun logPaymentCompleted(projectId: String, amount: Double)
    fun logError(errorMessage: String, stackTrace: String)
    fun setAnalyticsCollectionEnabled(enabled: Boolean)
}

class RealAnalyticsService(context: Context) : AnalyticsService {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

    // معرّف جلسة مجهولة ثابت لكل تثبيت: يميّز الجلسات المجهولة في
    // analytics_events دون دمجها مع DAU (المسجَّلين فقط).
    private fun analyticsSessionId(): String {
        val existing = prefs.getString("analytics_session_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("analytics_session_id", created).apply()
        return created
    }

    /**
     * تسجيل حدث في analytics_events بأفضل جهد (الدفعة 5 — الجزء أ).
     * يفشل بصمت إلى Logcat إن غاب الجدول (قبل تطبيق الترحيل) أو
     * انقطع الاتصال؛ لا يتعطل التطبيق ولا يُعاد المحاولة.
     * السلوك الافتراضي يبقى Logcat فقط حتى يُطبَّق الترحيل.
     */
    private fun recordEventCloud(eventName: String, parameters: Map<String, Any>?) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val client = SupabaseServices.client ?: return@launch
                val userId = runCatching { SupabaseServices.Auth.getCurrentUserId() }.getOrNull()
                val appVersion = runCatching { BuildConfig.VERSION_NAME }.getOrNull()
                val props = parameters?.entries?.associate { it.key to it.value.toString() } ?: emptyMap()
                client.postgrest.from("analytics_events").insert(
                    AnalyticsEventInsert(
                        userId = userId,
                        eventName = eventName,
                        properties = props,
                        sessionId = analyticsSessionId(),
                        appVersion = appVersion
                    )
                )
            }.onFailure { Log.d("Analytics", "Cloud event insert skipped: ${it.message}") }
        }
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("analytics_enabled", enabled).apply()
        Log.d("Analytics", "Local analytics collection enabled: $enabled")
    }

    override fun logScreenView(screenName: String) {
        val isEnabled = prefs.getBoolean("analytics_enabled", true)
        if (!isEnabled) return
        Log.d("Analytics", "Screen View: $screenName")
    }

    override fun logEvent(eventName: String, parameters: Map<String, Any>?) {
        val isEnabled = prefs.getBoolean("analytics_enabled", true)
        if (!isEnabled) return
        Log.d("Analytics", "Event: $eventName, Parameters: $parameters")
        recordEventCloud(eventName, parameters)
    }

    override fun logProjectCreated(projectId: String, template: String, duration: String) {
        logEvent("project_created", mapOf("pid" to projectId, "tpl" to template))
    }

    override fun logVideoGenerated(projectId: String, cost: Double, libraryCount: Int, generatedCount: Int) {
        logEvent("video_generated", mapOf("pid" to projectId, "cost" to cost))
    }

    override fun logPaymentCompleted(projectId: String, amount: Double) {
        logEvent("purchase_complete", mapOf("pid" to projectId, "amount" to amount))
    }

    override fun logError(errorMessage: String, stackTrace: String) {
        logEvent("app_error", mapOf("msg" to errorMessage))
    }
}

/** صف إدراج واحد في analytics_events (user_id فارغ للمجهولين). */
@Serializable
data class AnalyticsEventInsert(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("event_name") val eventName: String,
    @SerialName("properties") val properties: Map<String, String> = emptyMap(),
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("app_version") val appVersion: String? = null
)

