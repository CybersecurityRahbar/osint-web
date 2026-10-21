package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconEngineTest {
    private val engine = ReconEngine()

    @Test
    fun normalizesAWebsiteUrlToItsHost() {
        assertEquals("sub.example.com", DomainTools.normalizeHost("https://Sub.Example.com/path?q=1"))
        assertTrue(DomainTools.isValidDomain("https://sub.example.com/path"))
    }

    @Test
    fun robotsTxtShowsPublicCrawlHintsWithoutTreatingThemAsAccessControls() {
        val body = """
            User-agent: *
            Disallow: /admin/
            Disallow: /internal-preview/
            Sitemap: https://example.com/sitemap.xml
        """.trimIndent()

        val records = engine.parsePublicDiscoveryFile("/robots.txt", body, "https://example.com/robots.txt")

        assertTrue(records.any { it.kind == "ROBOTS_TXT" })
        assertTrue(records.any { it.kind == "ROBOTS_DISALLOW" && it.value == "/admin/" })
        assertTrue(records.any { it.kind == "SITEMAP_URL" && it.value == "https://example.com/sitemap.xml" })
        assertTrue(records.all { it.detail.contains("لا يفرض صلاحيات") || it.detail.contains("توجيه") || it.detail.contains("رابط sitemap") })
    }

    @Test
    fun sitemapParserExtractsOnlyHttpLinksAndDecodesXmlEntities() {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset>
              <url><loc>https://example.com/about</loc></url>
              <url><loc>https://example.com/search?q=one&amp;page=2</loc></url>
              <url><loc>javascript:alert(1)</loc></url>
            </urlset>
        """.trimIndent()

        val records = engine.parsePublicDiscoveryFile("/sitemap.xml", body, "https://example.com/sitemap.xml")
        val links = records.filter { it.kind == "SITEMAP_URL" }.map { it.value }

        assertEquals(2, links.size)
        assertTrue(links.contains("https://example.com/about"))
        assertTrue(links.contains("https://example.com/search?q=one&page=2"))
        assertFalse(links.any { it.startsWith("javascript:") })
    }

    @Test
    fun securityTxtKeepsPublicDisclosureMetadata() {
        val body = """
            Contact: mailto:security@example.com
            Expires: 2027-01-01T00:00:00Z
            Policy: https://example.com/security-policy
            Ignored-Field: not retained
        """.trimIndent()

        val records = engine.parsePublicDiscoveryFile(
            "/.well-known/security.txt",
            body,
            "https://example.com/.well-known/security.txt"
        )

        assertTrue(records.any { it.kind == "SECURITY_TXT_METADATA" && it.value.startsWith("Contact:") })
        assertTrue(records.any { it.value.startsWith("Expires:") })
        assertFalse(records.any { it.value.startsWith("Ignored-Field:") })
    }

    @Test
    fun scanReportRoundTripPreservesPreviewImagesAndWarnings() {
        val report = ScanReport(
            target = "example.com",
            records = listOf(
                ReconRecord(
                    kind = "WAYBACK_SNAPSHOT",
                    value = "2020-01-01",
                    source = "Wayback",
                    sourceUrl = "https://web.archive.org/web/20200101000000/https://example.com/",
                    confidence = 90,
                    previewImageUrl = "https://example.com/preview.jpg"
                )
            ),
            warnings = listOf("One source was temporarily unavailable"),
            startedAt = 100,
            finishedAt = 150
        )

        val decoded = ScanReport.fromJson(report.toJson())!!

        assertEquals("example.com", decoded.target)
        assertEquals("https://example.com/preview.jpg", decoded.records.single().previewImageUrl)
        assertEquals(report.warnings, decoded.warnings)
    }
}
