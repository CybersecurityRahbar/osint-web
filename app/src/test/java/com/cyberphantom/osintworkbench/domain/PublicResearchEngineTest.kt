package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicResearchEngineTest {
    private val engine = PublicResearchEngine()

    @Test
    fun usernameSearchProvidesMultipleExplicitSearchProviders() {
        val links = engine.buildSearchLinks("octocat", IdentifierKind.USERNAME)
        assertTrue(links.any { it.provider == "Google" })
        assertTrue(links.any { it.provider == "GitHub" })
        assertTrue(links.any { it.provider == "Reddit" })
        assertTrue(links.all { it.url.startsWith("https://") })
    }

    @Test
    fun phoneSearchDoesNotGuessCountryCodeAndRejectsTooShortNumbers() {
        val links = engine.buildSearchLinks("+967 771234567", IdentifierKind.PHONE)
        assertTrue(links.any { it.provider == "Bing" })
        val rejected = runCatching {
            engine.buildSearchLinks("1234", IdentifierKind.PHONE)
        }.isFailure
        assertTrue(rejected)
    }

    @Test
    fun emailSearchRejectsIncompleteAddress() {
        val rejected = runCatching {
            engine.buildSearchLinks("not-an-email", IdentifierKind.EMAIL)
        }.isFailure
        assertTrue(rejected)
        assertFalse(engine.buildSearchLinks("person@example.com", IdentifierKind.EMAIL).isEmpty())
    }
}
