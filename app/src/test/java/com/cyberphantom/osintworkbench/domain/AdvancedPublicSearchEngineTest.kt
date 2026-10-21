package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedPublicSearchEngineTest {
    private val engine = AdvancedPublicSearchEngine()

    @Test
    fun nameAndUsernameSearchExpandToIndexedCommunitySources() {
        val nameQueries = engine.buildSearchQueries("Ada Lovelace", IdentifierKind.NAME)
        assertTrue(nameQueries.any { it.contains("reddit.com") })
        val usernameQueries = engine.buildSearchQueries("octocat", IdentifierKind.USERNAME)
        assertTrue(usernameQueries.any { it.contains("github.com") || it.contains("reddit.com") })
    }

    @Test
    fun phoneSearchUsesRawAndDigitsOnlyFormsWithoutGuessingCountryCode() {
        val queries = engine.buildSearchQueries("+967 71 234 5678", IdentifierKind.PHONE)
        assertTrue(queries.any { it.contains("+967 71 234 5678") })
        assertTrue(queries.any { it.contains("967712345678") })
        assertTrue(queries.any { it.contains("reddit.com") })
        assertTrue(queries.any { it.contains("tellows.com") || it.contains("shouldianswer.com") })
    }

    @Test
    fun emailSearchIncludesPublicWebAndCommunityIndexQueries() {
        val queries = engine.buildSearchQueries("research@example.org", IdentifierKind.EMAIL)
        assertTrue(queries.first().contains("research@example.org"))
        assertTrue(queries.any { it.contains("reddit.com") })
    }

    @Test
    fun internationalPhoneMetadataIsPlanValidationNotIdentity() {
        val metadata = engine.phoneMetadata("+12025550123")
        assertNotNull(metadata)
        assertTrue(metadata!!.contains("لا تثبت"))
        assertFalse(metadata.contains("مالك الرقم"))
    }
}
