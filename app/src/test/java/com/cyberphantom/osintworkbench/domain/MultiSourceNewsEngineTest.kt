package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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
    fun parsesGdeltJsonIntoPublisherAttributedStories() {
        val json = JSONObject(
            """
            {
              "articles": [{
                "url": "https://www.reuters.com/world/example-story/",
                "title": "Global security update",
                "seendate": "20261021T100000Z",
                "domain": "www.reuters.com",
                "language": "English",
                "sourcecountry": "United States",
                "socialimage": "https://images.example.org/gdelt.jpg"
              }]
            }
            """.trimIndent()
        )

        val stories = engine.parseGdeltResponse(json)

        assertEquals(1, stories.size)
        assertEquals("Reuters", stories.single().publisherName)
        assertEquals("reuters.com", stories.single().domain)
        assertEquals("GDELT DOC API", stories.single().provider)
        assertEquals("https://images.example.org/gdelt.jpg", stories.single().imageUrl)
    }

    @Test
    fun gdeltImageTogglePreservesStoryMetadataButHidesImageUrl() {
        val json = JSONObject(
            """
            {"articles":[{
              "url":"https://www.reuters.com/world/example-story/",
              "title":"Global security update",
              "domain":"www.reuters.com",
              "socialimage":"https://images.example.org/gdelt.jpg"
            }]}
            """.trimIndent()
        )

        val stories = engine.parseGdeltResponse(json, includeImages = false)

        assertEquals(1, stories.size)
        assertEquals("Global security update", stories.single().title)
        assertNull(stories.single().imageUrl)
    }

    @Test
    fun bingRssRedirectIsResolvedToThePublisherStoryUrl() {
        val bingRss = """
            <rss version="2.0"><channel><item>
              <title>Public security update - Reuters</title>
              <link>https://www.bing.com/news/apiclick.aspx?url=https%3A%2F%2Fwww.reuters.com%2Fworld%2Fstory&amp;aid=123</link>
              <pubDate>Wed, 21 Oct 2026 10:00:00 GMT</pubDate>
              <source url="https://www.reuters.com/">Reuters</source>
            </item></channel></rss>
        """.trimIndent()

        val stories = engine.parseRss(
            bingRss,
            null,
            "en",
            "US",
            providerLabelOverride = "Bing News RSS"
        )

        assertEquals(1, stories.size)
        assertEquals("https://www.reuters.com/world/story", stories.single().url)
        assertEquals("Reuters", stories.single().publisherName)
        assertEquals("Bing News RSS", stories.single().provider)
    }

    @Test
    fun rssProviderLabelCanDistinguishIndependentFeeds() {
        val stories = engine.parseRss(rss, null, "en", "US", providerLabelOverride = "Bing News RSS")
        assertEquals("Bing News RSS", stories.single().provider)
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
