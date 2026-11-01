package com.qabas.app

import android.util.Log
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val authInfoJson = JSONObject().apply {
        put("userId", SupabaseServices.Auth.getCurrentUserId())
        put("email", SupabaseServices.Auth.getCurrentUserEmail())
        put("provider", "supabase")
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Legacy Firestore Error: $jsonString")
    return jsonString
}
