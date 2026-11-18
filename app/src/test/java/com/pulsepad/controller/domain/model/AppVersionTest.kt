package com.pulsepad.controller.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses a plain semantic version`() {
        val version = AppVersion.parse("1.2.3")
        assertEquals(AppVersion(1, 2, 3), version)
    }

    @Test
    fun `parses a tag with a leading v`() {
        assertEquals(AppVersion(2, 0, 1), AppVersion.parse("v2.0.1"))
        assertEquals(AppVersion(2, 0, 1), AppVersion.parse("V2.0.1"))
    }

    @Test
    fun `missing components default to zero`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("1.2"))
        assertEquals(AppVersion(3, 0, 0), AppVersion.parse("3"))
    }

    @Test
    fun `captures a prerelease suffix`() {
        assertEquals(AppVersion(1, 0, 0, "rc.1"), AppVersion.parse("1.0.0-rc.1"))
    }

    @Test
    fun `rejects text that is not a version`() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("release-notes"))
        assertNull(AppVersion.parse("v"))
    }

    @Test
    fun `orders by numeric component first`() {
        assertTrue(AppVersion(1, 2, 0).isNewerThan(AppVersion(1, 1, 9)))
        assertTrue(AppVersion(2, 0, 0).isNewerThan(AppVersion(1, 99, 99)))
        assertTrue(AppVersion(1, 0, 1).isNewerThan(AppVersion(1, 0, 0)))
        assertFalse(AppVersion(1, 0, 0).isNewerThan(AppVersion(1, 0, 0)))
    }

    @Test
    fun `a final release outranks a prerelease of the same numbers`() {
        assertTrue(AppVersion(1, 0, 0).isNewerThan(AppVersion(1, 0, 0, "rc.1")))
        assertFalse(AppVersion(1, 0, 0, "rc.1").isNewerThan(AppVersion(1, 0, 0)))
    }

    @Test
    fun `compares two-digit components numerically not lexically`() {
        assertTrue(AppVersion(1, 10, 0).isNewerThan(AppVersion(1, 9, 0)))
    }

    @Test
    fun `toString round-trips through parse`() {
        val original = AppVersion(1, 4, 2, "beta.3")
        assertEquals(original, AppVersion.parse(original.toString()))
    }
}
