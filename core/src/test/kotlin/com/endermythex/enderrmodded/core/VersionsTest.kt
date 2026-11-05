package com.endermythex.enderrmodded.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionsTest {
    @Test
    fun compare() {
        assertTrue(Versions.compare("20.12.46", "20.12.4") > 0)
        assertTrue(Versions.compare("19.16.39", "20.1.0") < 0)
        assertEquals(0, Versions.compare("v5.2.0", "5.2.0"))
        assertTrue(Versions.isNewer("5.2.0", "5.2.0-dev.3"))
        assertTrue(Versions.isNewer("v0.2.0", "0.1.9"))
    }

    @Test
    fun recommended() {
        assertNull(Versions.recommended(listOf(null, null)))
        assertEquals(
            "20.12.46",
            Versions.recommended(listOf(setOf("20.10.38", "20.12.46"), setOf("20.12.46"), null)),
        )
        // Égalité : la plus récente gagne.
        assertEquals("20.14.43", Versions.recommended(listOf(setOf("20.12.46", "20.14.43"))))
    }
}
