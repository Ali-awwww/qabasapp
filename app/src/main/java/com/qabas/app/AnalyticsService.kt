package com.qabas.app

import android.content.Context
import android.os.Bundle
import android.util.Log

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

