package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainToolsTest {
    @Test
    fun normalizesUrlToAsciiHost() {
        assertEquals("example.com", DomainTools.normalizeHost(" HTTPS://Example.com/path?q=1 "))
    }

    @Test
    fun rejectsBareWordsAndUrlsWithoutDomainSuffix() {
        assertFalse(DomainTools.isValidDomain("example"))
        assertFalse(DomainTools.isValidDomain("https://"))
        assertTrue(DomainTools.isValidDomain("example.com"))
    }

    @Test
    fun rejectsInvalidLabels() {
        assertFalse(DomainTools.isValidDomain("-bad.example"))
        assertFalse(DomainTools.isValidDomain("bad..example"))
    }
}
