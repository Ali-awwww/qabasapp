package com.qabas.app

import android.content.Context
import android.util.Log

/**
 * Batch 6 Priority 3 — guest → account upgrade.
 *
 * A guest creates local content (reading positions and bookmarks, hadith
 * bookmarks, preferences) with no account. When they finally register or
 * sign in, that content must follow them to the new account id instead of
 * being stranded under the guest identity.
 *
 * What is migrated, exactly:
 * 1. The guest's account-scoped preferences file
 *    (`AccountScopedPreferences.forEmail(context, "guest@qabas.studio")`) —
 *    every entry is copied into the new account's file
 *    (`AccountScopedPreferences.forEmail(context, newEmail)`), following the
 *    existing per-account isolation pattern. Copy, not move: the guest file
 *    is left intact, so nothing is lost if the migration is interrupted.
 * 2. Library reading states (positions + bookmarks) stored device-locally in
 *    `qabas_library_reading` (`LibraryReadingStateStore`): each state is
 *    upserted to `library_reading_state` under the new user id — but only
 *    once the new address is confirmed, because cloud sync is blocked until
 *    confirmation. Until then the states stay local and untouched.
 * 3. Preferences JSON: uploaded to the new account's `user_settings` row via
 *    the existing `UserPreferencesManager.syncToSupabase` — again only when
 *    the address is confirmed.
 *
 * Device-local stores that are not user-keyed (`qabas_library_reading`,
 * `qabas_sahih_bookmarks`) keep working on the device as before; the
 * migration gives them a cloud home under the new account id instead of
 * re-keying them locally. No guest local data is ever deleted by this
 * migration, and it runs only after the new account's session exists.
 */
object GuestUpgradeManager {
    private const val TAG = "GuestUpgrade"
    private const val PREF_GUEST_UPGRADE_PENDING = "guest_upgrade_pending"
    private const val PREF_GUEST_DATA_MIGRATED_TO = "guest_data_migrated_to"
    const val GUEST_EMAIL = "guest@qabas.studio"

    data class GuestMigrationResult(
        val preferencesCopied: Int,
        val readingStatesUploaded: Int,
        val preferencesSyncedToCloud: Boolean,
        val cloudUploadDeferred: Boolean
    )

    /** The Settings guest card sets this before navigating to registration. */
    fun markGuestUpgradePending(context: Context) {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_GUEST_UPGRADE_PENDING, true).apply()
    }

    /** True when the user arrived from the guest upgrade entry point, or is still flagged as guest. */
    fun shouldMigrate(context: Context): Boolean {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_GUEST_UPGRADE_PENDING, false) ||
            prefs.getBoolean("is_guest", false)
    }

    private fun clearPending(context: Context, newUserId: String) {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_GUEST_UPGRADE_PENDING, false)
            .putString(PREF_GUEST_DATA_MIGRATED_TO, newUserId)
            .apply()
    }

    /**
     * Copies the guest's local data to the new account. Safe to call more
     * than once (copy + upsert are idempotent). Never deletes guest data.
     */
    suspend fun migrateGuestDataToAccount(
        context: Context,
        newUserId: String,
        newEmail: String,
        emailConfirmed: Boolean
    ): GuestMigrationResult {
        // 1. Account-scoped preferences: guest file -> new account file.
        var preferencesCopied = 0
        runCatching {
            val guestPrefs = AccountScopedPreferences.forEmail(context, GUEST_EMAIL)
            val newPrefs = AccountScopedPreferences.forEmail(context, newEmail)
            val entries = guestPrefs.all
            if (entries.isNotEmpty()) {
                val editor = newPrefs.edit()
                entries.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                    }
                }
                editor.apply()
                preferencesCopied = entries.size
            }
        }.onFailure { Log.w(TAG, "guest prefs copy failed: ${it.message}") }

        // 2 + 3. Cloud upload only once the address is confirmed; otherwise
        // the data stays local and the normal sync picks it up after
        // confirmation. Local use continues either way.
        var readingStatesUploaded = 0
        var preferencesSyncedToCloud = false
        if (emailConfirmed) {
            runCatching {
                val localStates = LibraryReadingStateStore.getAllStates(context)
                for (state in localStates) {
                    val uploaded = SupabaseServices.Database.upsertLibraryReadingState(
                        SupabaseServices.Database.LibraryReadingStateRow(
                            userId = newUserId,
                            bookId = state.bookId,
                            lastChapter = state.lastChapterIndex,
                            lastSection = state.lastSectionIndex,
                            progress = state.progressPercent,
                            bookmarked = state.bookmarked
                        )
                    )
                    if (uploaded) readingStatesUploaded++
                }
            }.onFailure { Log.w(TAG, "guest reading-states upload failed: ${it.message}") }
            runCatching {
                preferencesSyncedToCloud = UserPreferencesManager.syncToSupabase(context)
            }.onFailure { Log.w(TAG, "guest prefs cloud sync failed: ${it.message}") }
        }

        clearPending(context, newUserId)
        Log.d(
            TAG,
            "guest migration to $newUserId: prefsCopied=$preferencesCopied " +
                "readingStatesUploaded=$readingStatesUploaded " +
                "prefsSynced=$preferencesSyncedToCloud deferred=${!emailConfirmed}"
        )
        return GuestMigrationResult(
            preferencesCopied = preferencesCopied,
            readingStatesUploaded = readingStatesUploaded,
            preferencesSyncedToCloud = preferencesSyncedToCloud,
            cloudUploadDeferred = !emailConfirmed
        )
    }
}
