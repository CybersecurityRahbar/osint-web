package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadPublicSearchAdapterTest {
    private val adapter = BroadPublicSearchAdapter()

    @Test
    fun parsesBingOrganicResultBlocksAndSnippets() {
        val html = """
            <ol id="b_results">
              <li class="b_algo">
                <h2><a href="https://example.org/research">Indexed research page</a></h2>
                <div class="b_caption"><p>A specific public research record.</p></div>
              </li>
            </ol>
        """.trimIndent()

        val results = adapter.parseHtml(html, "Bing", 10)

        assertEquals(1, results.size)
        assertEquals("Indexed research page", results.single().title)
        assertEquals("https://example.org/research", results.single().url)
        assertTrue(results.single().description.contains("specific public research"))
    }

    @Test
    fun parsesDuckDuckGoResultAnchors() {
        val html = """
            <div class="result">
              <a class="result__a" href="https://example.net/page">Public web result</a>
            </div>
        """.trimIndent()

        val results = adapter.parseHtml(html, "DuckDuckGo", 10)

        assertEquals(1, results.size)
        assertEquals("https://example.net/page", results.single().url)
        assertEquals("Public web result", results.single().title)
    }

    @Test
    fun parsesMojeekResultAnchorTemplate() {
        val html = """
            <ul class="results-standard">
              <li><a class="ob" href="https://example.com/item">Result from Mojeek</a><p class="s">An indexed result snippet.</p></li>
            </ul>
        """.trimIndent()

        val results = adapter.parseHtml(html, "Mojeek", 10)

        assertEquals(1, results.size)
        assertEquals("https://example.com/item", results.single().url)
        assertEquals("Result from Mojeek", results.single().title)
    }

    @Test
    fun dropsSearchEngineSelfLinksAndNonHttpsResults() {
        val html = """
            <div class="result">
              <a class="result__a" href="https://duckduckgo.com/about">Search page itself</a>
              <a class="result__a" href="http://example.org/insecure">Non-HTTPS page</a>
            </div>
        """.trimIndent()

        val results = adapter.parseHtml(html, "DuckDuckGo", 10)

        assertTrue(results.isEmpty())
    }
}
