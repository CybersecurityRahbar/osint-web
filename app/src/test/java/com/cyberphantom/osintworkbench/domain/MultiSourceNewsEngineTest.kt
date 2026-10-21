package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceNewsEngineTest {
    private val engine = MultiSourceNewsEngine()

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Public security update - Reuters</title>
              <link>https://www.reuters.com/world/example-story/</link>
              <pubDate>Wed, 21 Oct 2026 10:00:00 GMT</pubDate>
              <source url="https://www.reuters.com/">Reuters</source>
              <thumbnail url="https://images.example.org/story.jpg" />
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parsesRssStoryAndKeepsPublisherAttribution() {
        val stories = engine.parseRss(rss, null, "en", "US")

        assertEquals(1, stories.size)
        assertEquals("Public security update", stories.single().title)
        assertEquals("Reuters", stories.single().publisherName)
        assertEquals("reuters.com", stories.single().domain)
        assertEquals("https://www.reuters.com/world/example-story/", stories.single().url)
        assertEquals("https://images.example.org/story.jpg", stories.single().imageUrl)
    }

    @Test
    fun imageToggleRemovesImageMetadataWhileKeepingNewsText() {
        val stories = engine.parseRss(rss, null, "en", "US", includeImages = false)

        assertEquals(1, stories.size)
        assertEquals("Public security update", stories.single().title)
        assertNull(stories.single().imageUrl)
    }

    @Test
    fun rssParserDropsItemsWithoutValidHttpsStoryLinks() {
        val invalid = rss.replace(
            "https://www.reuters.com/world/example-story/",
            "javascript:alert(1)"
        )

        val stories = engine.parseRss(invalid, null, "en", "US")
        assertTrue(stories.isEmpty())
    }
}
