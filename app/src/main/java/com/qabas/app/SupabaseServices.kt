package com.qabas.app

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest

object SupabaseServices {
    val client: SupabaseClient? = try {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
        }
    } catch (e: Exception) {
        Log.e("SupabaseServices", "Supabase init failed: ${e.message}")
        null
    }

    object Database {
        suspend fun getLatestActiveUpdate(): ActiveUpdate? = try {
            val result = client?.postgrest
                ?.from("app_updates")
                ?.select()
                ?.limit(1)
                ?.decodeList<ActiveUpdate>()

            result?.firstOrNull()
        } catch (e: Exception) {
            Log.e("SupabaseServices", "getLatestActiveUpdate failed: ${e.message}")
            null
        }
    }
}

@kotlinx.serialization.Serializable
data class ActiveUpdate(
    val id: Long = 0,
    val version_name: String = "",
    val version_code: Int = 0,
    val apk_url: String = "",
    val release_notes: String = "",
    val file_size_bytes: Long = 0L,
    val is_active: Boolean = true
) {
    val versionName: String get() = version_name
    val versionCode: Int get() = version_code
    val apkUrl: String get() = apk_url
    val releaseNotes: String get() = release_notes
    val fileSizeBytes: Long get() = file_size_bytes
    val isActive: Boolean get() = is_active
}
