package com.notova.integrations.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM parsing of the `notova://oauth/<provider>?status=...` return deep link. */
class OAuthRedirectTest {
    @Test
    fun `parses a connected redirect`() {
        val redirect = OAuthRedirect.parse("notova://oauth/notion?status=connected")

        assertEquals("notion", redirect?.provider)
        assertEquals("connected", redirect?.status)
        assertTrue(redirect?.isConnected == true)
        assertNull(redirect?.errorMessage)
    }

    @Test
    fun `lowercases the provider and is case-insensitive on connected status`() {
        val redirect = OAuthRedirect.parse("notova://oauth/Notion?status=Connected")

        assertEquals("notion", redirect?.provider)
        assertTrue(redirect?.isConnected == true)
    }

    @Test
    fun `parses an error redirect`() {
        val redirect =
            OAuthRedirect.parse("notova://oauth/slack?status=error&error=access_denied")

        assertEquals("slack", redirect?.provider)
        assertEquals("error", redirect?.status)
        assertFalse(redirect?.isConnected == true)
        assertEquals("access_denied", redirect?.errorMessage)
    }

    @Test
    fun `url-decodes the error message`() {
        val redirect =
            OAuthRedirect.parse("notova://oauth/notion?status=error&error=not%20configured")

        assertEquals("not configured", redirect?.errorMessage)
    }

    @Test
    fun `tolerates a missing status`() {
        val redirect = OAuthRedirect.parse("notova://oauth/notion")

        assertEquals("notion", redirect?.provider)
        assertEquals("", redirect?.status)
        assertFalse(redirect?.isConnected == true)
    }

    @Test
    fun `returns null for a non-oauth uri`() {
        assertNull(OAuthRedirect.parse("notova://record/start"))
        assertNull(OAuthRedirect.parse("https://notova.app/oauth/notion?status=connected"))
        assertNull(OAuthRedirect.parse(null))
    }

    @Test
    fun `returns null when the provider segment is empty`() {
        assertNull(OAuthRedirect.parse("notova://oauth/?status=connected"))
        assertNull(OAuthRedirect.parse("notova://oauth/"))
    }
}
