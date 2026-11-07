package com.qabas.app

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedAuthRoleTest {

    @Test
    fun `server managed admin role is resolved from app metadata`() {
        val metadata = buildJsonObject { put("role", "ADMIN") }
        assertEquals("admin", trustedRoleFromAppMetadata(metadata))
    }

    @Test
    fun `missing app metadata role defaults to an ordinary user`() {
        assertEquals("user", trustedRoleFromAppMetadata(null))
        assertEquals("user", trustedRoleFromAppMetadata(buildJsonObject {}))
    }

    @Test
    fun `non string app metadata role cannot grant a privileged role`() {
        val metadata = buildJsonObject { put("role", true) }
        assertEquals("user", trustedRoleFromAppMetadata(metadata))
    }

    @Test
    fun `blank app metadata role defaults to ordinary user`() {
        val metadata = buildJsonObject { put("role", "  ") }
        assertEquals("user", trustedRoleFromAppMetadata(metadata))
    }
}
