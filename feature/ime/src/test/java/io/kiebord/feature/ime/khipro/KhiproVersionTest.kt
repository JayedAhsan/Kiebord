package io.kiebord.feature.ime.khipro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KhiproVersionTest {

    @Test
    fun parsesClassicTag() {
        assertEquals(KhiproVersion(36, 2, 3), KhiproVersion.parse("v36.2.3"))
    }

    @Test
    fun parsesDarkTagWithDrkPrefix() {
        assertEquals(KhiproVersion(2, 0, 2), KhiproVersion.parse("drk-v2.0.2"))
    }

    @Test
    fun parsesDarkTagWithDarkPrefix() {
        assertEquals(KhiproVersion(1, 0, 0), KhiproVersion.parse("dark-v1.0.0"))
    }

    @Test
    fun parsesTagWithPreReleaseSuffix() {
        assertEquals(KhiproVersion(2, 0, 0), KhiproVersion.parse("v2.0.0-beta"))
    }

    @Test
    fun comparesByNumericParts() {
        assertTrue(KhiproVersion.parse("v2.0.2")!! > KhiproVersion.parse("v2.0.1")!!)
        assertTrue(KhiproVersion.parse("v36.2.10")!! > KhiproVersion.parse("v36.2.9")!!)
        assertTrue(KhiproVersion.parse("v37.0.3")!! > KhiproVersion.parse("v36.9.9")!!)
    }

    @Test
    fun ignoresBuildMetadata() {
        assertEquals(KhiproVersion(2, 0, 2), KhiproVersion.parse("drk-v2.0.2+build.5"))
    }

    @Test
    fun returnsNullWithoutDigits() {
        assertNull(KhiproVersion.parse("abc"))
        assertNull(KhiproVersion.parse(""))
    }
}
