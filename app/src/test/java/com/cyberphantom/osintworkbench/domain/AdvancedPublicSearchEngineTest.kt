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
    fun exactPhoneDigitsRankAboveVagueResults() {
        val exact = PublicProfileResult(
            provider = "DuckDuckGo · Tellows",
            title = "Reports for +967 71 234 5678",
            description = "Public caller reports mention this exact number.",
            url = "https://www.tellows.com/num/967712345678",
            confidence = 72
        )
        val vague = PublicProfileResult(
            provider = "DuckDuckGo",
            title = "How to identify unwanted calls",
            description = "General information about unknown phone calls.",
            url = "https://example.org/phone-safety",
            confidence = 72
        )

        val exactScore = engine.calculateRelevance("+967 71 234 5678", IdentifierKind.PHONE, exact)
        val vagueScore = engine.calculateRelevance("+967 71 234 5678", IdentifierKind.PHONE, vague)
        assertTrue(exactScore > vagueScore)
    }

    @Test
    fun exactNamePhraseRanksAboveBroadTokenOverlap() {
        val exact = PublicProfileResult("DuckDuckGo", "Ada Lovelace - Biography", "", "https://example.org/ada-lovelace", 70)
        val partial = PublicProfileResult("DuckDuckGo", "Ada and Early Computing", "A separate topic mentions Lovelace in passing.", "https://example.org/ada", 70)

        assertTrue(
            engine.calculateRelevance("Ada Lovelace", IdentifierKind.NAME, exact) >
                engine.calculateRelevance("Ada Lovelace", IdentifierKind.NAME, partial)
        )
    }

    @Test
    fun internationalPhoneMetadataIsPlanValidationNotIdentity() {
        val metadata = engine.phoneMetadata("+12025550123")
        assertNotNull(metadata)
        assertTrue(metadata!!.contains("لا تثبت"))
        assertFalse(metadata.contains("مالك الرقم"))
    }
}
