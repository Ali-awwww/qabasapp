package com.qabas.app

import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Resolves authorization roles only from server-managed Supabase app_metadata.
 * user_metadata is client-editable and must never grant privileges.
 */
internal fun trustedRoleFromAppMetadata(appMetadata: JsonObject?): String {
    val role = appMetadata?.get("role") as? JsonPrimitive ?: return "user"
    if (!role.isString) return "user"
    return role.content.trim().lowercase(Locale.ROOT).ifBlank { "user" }
}
