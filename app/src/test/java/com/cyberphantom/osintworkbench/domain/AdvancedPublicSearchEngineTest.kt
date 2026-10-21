package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
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
    fun parsesDuckDuckGoExternalResultsAndUnwrapsDestinations() {
        val html = """
            <div class="result results_links results_links_deep">
              <a class="result__a" href="https://example.org/research">Public research page</a>
              <a class="result__snippet">A useful public record about research.</a>
            </div>
        """.trimIndent()

        val results = engine.parseResults(html, limit = 10, provider = "DDG test")

        assertEquals(1, results.size)
        assertEquals("Public research page", results.single().title)
        assertEquals("https://example.org/research", results.single().url)
        assertTrue(results.single().description.contains("useful public record"))
        assertTrue(results.single().provider.startsWith("DDG test"))
    }

    @Test
    fun parsesBingResultBlocksWhenDuckDuckGoMarkupIsAbsent() {
        val html = """
            <ol id="b_results">
              <li class="b_algo">
                <h2><a href="https://example.net/profile">Public profile result</a></h2>
                <div class="b_caption"><p>Publicly indexed profile description.</p></div>
              </li>
            </ol>
        """.trimIndent()

        val results = engine.parseResults(html, limit = 10, provider = "Bing test")

        assertEquals(1, results.size)
        assertEquals("Public profile result", results.single().title)
        assertEquals("https://example.net/profile", results.single().url)
        assertTrue(results.single().description.contains("indexed profile"))
    }

    @Test
    fun unwrapsBingTrackingRedirectToTheExternalSource() {
        val html = """
            <ol id="b_results">
              <li class="b_algo">
                <h2><a href="https://www.bing.com/ck/a?u=a1aHR0cHM6Ly9leGFtcGxlLmNvbS9uZXdz&ntb=1">Public report</a></h2>
              </li>
            </ol>
        """.trimIndent()

        val results = engine.parseResults(html, limit = 10, provider = "Bing test")

        assertEquals(1, results.size)
        assertEquals("Public report", results.single().title)
        assertEquals("https://example.com/news", results.single().url)
    }

    @Test
    fun parsesGoogleStyleH3ResultsAndUnwrapsTheRedirectToTheSource() {
        val html = """
            <div class="MjjYud">
              <a href="https://www.google.com/url?q=https%3A%2F%2Fexample.org%2Freport&sa=U">
                <h3 class="LC20lb">Public report result</h3>
              </a>
            </div>
        """.trimIndent()

        val results = engine.parseResults(html, limit = 10, provider = "Google test")

        assertEquals(1, results.size)
        assertEquals("Public report result", results.single().title)
        assertEquals("https://example.org/report", results.single().url)
    }

    @Test
    fun parsesYahooStyleH3Links() {
        val html = """
            <div class="compTitle">
              <h3><a class="ac-algo" href="https://example.org/yahoo-result">Yahoo public result</a></h3>
            </div>
        """.trimIndent()

        val results = engine.parseResults(html, limit = 10, provider = "Yahoo test")

        assertEquals(1, results.size)
        assertEquals("Yahoo public result", results.single().title)
        assertEquals("https://example.org/yahoo-result", results.single().url)
    }

    @Test
    fun expandedNameSearchIncludesSeveralPublicIndexSurfaces() {
        val queries = engine.buildSearchQueries("Ada Lovelace", IdentifierKind.NAME)
        assertTrue(queries.any { it.contains("reddit.com") })
        assertTrue(queries.any { it.contains("github.com") })
        assertTrue(queries.any { it.contains("instagram.com") || it.contains("tiktok.com") })
    }

    @Test
    fun internationalPhoneMetadataIsPlanValidationNotIdentity() {
        val metadata = engine.phoneMetadata("+12025550123")
        assertNotNull(metadata)
        assertTrue(metadata!!.contains("لا تثبت"))
        assertFalse(metadata.contains("مالك الرقم"))
    }
}
