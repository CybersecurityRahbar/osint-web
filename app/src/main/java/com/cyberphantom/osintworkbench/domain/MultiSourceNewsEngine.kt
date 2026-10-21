package com.cyberphantom.osintworkbench.domain

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Multi-source news collector. GDELT and Google News RSS are queried independently, with
 * publisher-scoped RSS feeds added for major international and Arabic-language outlets.
 * Each story retains its publisher label, source URL, publication time, and image URL when supplied.
 */
class MultiSourceNewsEngine {
    private val publishers = listOf(
        Publisher("Reuters", "reuters.com", "en-US", "US", "US:en"),
        Publisher("Associated Press", "apnews.com", "en-US", "US", "US:en"),
        Publisher("BBC News", "bbc.com", "en-US", "GB", "GB:en"),
        Publisher("Al Jazeera", "aljazeera.com", "en-US", "US", "US:en"),
        Publisher("الجزيرة", "aljazeera.net", "ar", "SA", "SA:ar"),
        Publisher("DW", "dw.com", "en-US", "DE", "DE:en"),
        Publisher("France 24", "france24.com", "en-US", "FR", "FR:en"),
        Publisher("The Guardian", "theguardian.com", "en-US", "GB", "GB:en"),
        Publisher("CNN", "cnn.com", "en-US", "US", "US:en"),
        Publisher("العربية", "alarabiya.net", "ar", "SA", "SA:ar")
    )

    suspend fun search(query: String, maxRecords: Int = 40, includeImages: Boolean = true): NewsSearchResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        require(clean.isNotBlank()) { "أدخل كلمات البحث عن الأخبار." }
        require(clean.length <= 180) { "عبارة البحث طويلة جداً." }
        val limit = maxRecords.coerceIn(1, 60)

        val outcomes = coroutineScope {
            // Keep concurrency bounded: a phone should not bombard GDELT and ten RSS feeds at once.
            val sourceSemaphore = Semaphore(4)
            val tasks = mutableListOf<kotlinx.coroutines.Deferred<SourceOutcome>>()
            tasks += async {
                runCatching { sourceSemaphore.withPermit { queryGdelt(clean, limit, includeImages) } }.fold(
                    { SourceOutcome("GDELT", it, null) },
                    { SourceOutcome("GDELT", emptyList(), it.message ?: "تعذر الوصول") }
                )
            }
            tasks += async {
                runCatching { sourceSemaphore.withPermit { queryGoogleNews(clean, limit, null, includeImages) } }.fold(
                    { SourceOutcome("Google News RSS", it, null) },
                    { SourceOutcome("Google News RSS", emptyList(), it.message ?: "تعذر الوصول") }
                )
            }
            publishers.forEach { publisher ->
                tasks += async {
                    runCatching { sourceSemaphore.withPermit { queryGoogleNews(clean, maxOf(5, limit / 2), publisher, includeImages) } }.fold(
                        { SourceOutcome(publisher.name, it, null) },
                        { SourceOutcome(publisher.name, emptyList(), it.message ?: "تعذر الوصول") }
                    )
                }
            }
            tasks.awaitAll()
        }

        val articles = outcomes.flatMap { it.articles }
            .filter { it.title.isNotBlank() && it.url.startsWith("https://") }
            .distinctBy {
                "${it.title.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()}|" +
                    it.publisherName.ifBlank { it.domain }.lowercase(Locale.ROOT)
            }
            .take(limit)

        val failed = outcomes.filter { it.error != null }
        val warning = when {
            articles.isEmpty() ->
                "لم تُرجع مصادر الأخبار أي مقالات. قد تكون المصادر محجوبة أو لا تغطي هذه العبارة؛ النتيجة الصفرية لا تثبت عدم وجود أخبار."
            failed.isNotEmpty() ->
                "نتائج جزئية من ${outcomes.count { it.articles.isNotEmpty() }} مصدر. تعذرت بعض المصادر: " +
                    failed.take(5).joinToString(" · ") { "${it.source}: ${it.error}" }
            else -> null
        }
        NewsSearchResult(clean, articles, warning)
    }

    private suspend fun queryGdelt(query: String, maxRecords: Int, includeImages: Boolean): List<NewsArticle> = withContext(Dispatchers.IO) {
        val limit = maxRecords.coerceIn(1, 50)
        // Retry a simpler request if the richer one returns an HTML error page or is rate-limited.
        val endpoints = listOf(
            "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(query)}&mode=artlist&format=json&sort=datedesc&maxrecords=$limit&timespan=1week",
            "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(query)}&mode=artlist&format=json&maxrecords=${minOf(limit, 20)}"
        )
        var lastFailure: Exception? = null
        for ((endpointIndex, endpoint) in endpoints.withIndex()) {
            for (attempt in 0..2) {
                try {
                    val raw = getText(endpoint, 12_000, "application/json, text/plain, */*")
                    val root = try {
                        JSONObject(raw)
                    } catch (error: Exception) {
                        val preview = raw.replace(Regex("\\s+"), " ").take(180)
                        throw IllegalStateException("GDELT returned non-JSON data: $preview", error)
                    }
                    // A successful JSON response clears any transient failure from an earlier variant.
                    lastFailure = null
                    val articles = parseGdeltResponse(root, includeImages)
                    if (articles.isNotEmpty()) return@withContext articles
                    // Valid empty data is not a network error; try the simpler query variant.
                    break
                } catch (error: Exception) {
                    lastFailure = error
                    val message = error.message.orEmpty()
                    val retryable = listOf("HTTP 429", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504", "timed out", "timeout")
                        .any { message.contains(it, ignoreCase = true) }
                    if (!retryable || attempt == 2) break
                    Thread.sleep(if (attempt == 0) 350L else 850L)
                }
            }
            if (endpointIndex == endpoints.lastIndex && lastFailure != null) {
                throw IllegalStateException("GDELT DOC API failed after retries: " + (lastFailure?.message ?: "unknown error"), lastFailure)
            }
        }
        emptyList()
    }

    internal fun parseGdeltResponse(root: JSONObject, includeImages: Boolean = true): List<NewsArticle> {
        val items = root.optJSONArray("articles") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url").takeIf { it.startsWith("https://") } ?: continue
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                val domain = item.optString("domain").removePrefix("www.")
                add(
                    NewsArticle(
                        title = title,
                        url = url,
                        domain = domain,
                        publishedAt = item.optString("seendate"),
                        language = item.optString("language"),
                        sourceCountry = item.optString("sourcecountry"),
                        publisherName = publisherLabel(domain),
                        imageUrl = item.optString("socialimage").takeIf { includeImages && it.startsWith("https://") },
                        provider = "GDELT DOC API"
                    )
                )
            }
        }.distinctBy { it.url }
    }

    private suspend fun queryGoogleNews(
        query: String,
        maxRecords: Int,
        publisher: Publisher?,
        includeImages: Boolean
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val queryText = if (publisher == null) query else "$query site:${publisher.domain}"
        val language = publisher?.language ?: "ar"
        val country = publisher?.country ?: "YE"
        val edition = publisher?.edition ?: "YE:ar"
        val endpoint = "https://news.google.com/rss/search?q=${enc(queryText)}&hl=${enc(language)}&gl=${enc(country)}&ceid=${enc(edition)}"
        val xml = getText(endpoint, 12_000, "application/rss+xml, application/xml, text/xml")
        parseRss(xml, publisher, language, country, includeImages)
            .take(maxRecords.coerceIn(1, 60))
    }

    internal fun parseRss(
        xml: String,
        requestedPublisher: Publisher?,
        language: String,
        country: String,
        includeImages: Boolean = true
    ): List<NewsArticle> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val articles = mutableListOf<NewsArticle>()
        var event = parser.eventType
        var inItem = false
        var title = ""
        var link = ""
        var publishedAt = ""
        var sourceName = ""
        var sourceUrl = ""
        var imageUrl = ""

        fun resetItem() {
            title = ""
            link = ""
            publishedAt = ""
            sourceName = ""
            sourceUrl = ""
            imageUrl = ""
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == "item") {
                        inItem = true
                        resetItem()
                    } else if (inItem) {
                        when (tag) {
                            "title" -> title = parser.nextText().trim()
                            "link" -> link = parser.nextText().trim()
                            "pubdate" -> publishedAt = parser.nextText().trim()
                            "source" -> {
                                sourceUrl = parser.getAttributeValue(null, "url").orEmpty()
                                sourceName = parser.nextText().trim()
                            }
                            "thumbnail", "media:thumbnail" -> imageUrl = parser.getAttributeValue(null, "url").orEmpty()
                            "content", "media:content", "enclosure" ->
                                if (imageUrl.isBlank()) imageUrl = parser.getAttributeValue(null, "url").orEmpty()
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("item", true) && inItem) {
                    if (title.isNotBlank() && link.startsWith("https://")) {
                        val sourceDomain = runCatching { URL(sourceUrl).host.removePrefix("www.") }.getOrDefault("")
                            .ifBlank { requestedPublisher?.domain.orEmpty() }
                        val fallbackDomain = runCatching { URL(link).host.removePrefix("www.") }.getOrDefault("")
                        val actualDomain = sourceDomain.ifBlank { fallbackDomain }
                        val inferredPublisher = inferPublisherFromTitle(title)
                        val actualPublisher = sourceName.ifBlank {
                            requestedPublisher?.name ?: inferredPublisher ?: publisherLabel(actualDomain)
                        }
                        val attributedDomain = sourceDomain.ifBlank {
                            requestedPublisher?.domain ?: knownPublisherDomain(actualPublisher) ?: actualDomain
                        }
                        articles += NewsArticle(
                            title = stripPublisherSuffix(title, actualPublisher),
                            url = link,
                            domain = attributedDomain,
                            publishedAt = publishedAt,
                            language = language,
                            sourceCountry = country,
                            publisherName = actualPublisher,
                            imageUrl = imageUrl.takeIf { includeImages && it.startsWith("https://") },
                            provider = if (requestedPublisher == null) "Google News RSS" else "Google News RSS · ${requestedPublisher.name}"
                        )
                    }
                    inItem = false
                }
            }
            event = parser.next()
        }
        return articles.distinctBy { "${it.title.lowercase(Locale.ROOT)}|${it.publisherName.lowercase(Locale.ROOT)}" }
    }

    private fun stripPublisherSuffix(title: String, publisher: String): String =
        title.removeSuffix(" - $publisher").removeSuffix(" – $publisher").trim().ifBlank { title }

    private fun inferPublisherFromTitle(title: String): String? {
        val suffix = title.substringAfterLast(" - ", "").substringAfterLast(" – ", "").trim()
        val known = setOf(
            "Reuters", "Associated Press", "AP News", "BBC", "BBC News", "Al Jazeera",
            "الجزيرة", "DW", "France 24", "The Guardian", "CNN", "العربية", "Sky News"
        )
        return suffix.takeIf { it in known }?.let { if (it == "AP News") "Associated Press" else it }
    }

    private fun knownPublisherDomain(name: String): String? = when (name) {
        "Reuters" -> "reuters.com"
        "Associated Press", "AP News" -> "apnews.com"
        "BBC", "BBC News" -> "bbc.com"
        "Al Jazeera" -> "aljazeera.com"
        "الجزيرة" -> "aljazeera.net"
        "DW" -> "dw.com"
        "France 24" -> "france24.com"
        "The Guardian" -> "theguardian.com"
        "CNN" -> "cnn.com"
        "العربية" -> "alarabiya.net"
        "Sky News" -> "news.sky.com"
        else -> null
    }

    private fun publisherLabel(domain: String): String {
        val host = domain.lowercase(Locale.ROOT).removePrefix("www.")
        return when {
            host == "reuters.com" || host.endsWith(".reuters.com") -> "Reuters"
            host == "apnews.com" || host.endsWith(".apnews.com") -> "Associated Press"
            host == "bbc.com" || host.endsWith(".bbc.com") || host == "bbc.co.uk" -> "BBC News"
            host == "aljazeera.com" || host.endsWith(".aljazeera.com") -> "Al Jazeera"
            host == "aljazeera.net" || host.endsWith(".aljazeera.net") -> "الجزيرة"
            host == "dw.com" || host.endsWith(".dw.com") -> "DW"
            host == "france24.com" || host.endsWith(".france24.com") -> "France 24"
            host == "theguardian.com" || host.endsWith(".theguardian.com") -> "The Guardian"
            host == "cnn.com" || host.endsWith(".cnn.com") -> "CNN"
            host == "alarabiya.net" || host.endsWith(".alarabiya.net") -> "العربية"
            else -> host.ifBlank { "مصدر غير معروف" }
        }
    }

    private fun getText(endpoint: String, timeoutMs: Int, accept: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "OSINTWorkbench/0.4 (public-source research; see project README)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (total < 512_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 512_000 - total))
                    if (count <= 0) break
                    out.write(buffer, 0, count)
                    total += count
                }
                out.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            if (status !in 200..299) {
                val preview = body.replace(Regex("\\s+"), " ").take(160)
                throw IllegalStateException("HTTP $status from ${URL(endpoint).host}" + if (preview.isBlank()) "" else " · $preview")
            }
            if (body.isBlank()) throw IllegalStateException("Empty response from ${URL(endpoint).host}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    internal data class Publisher(
        val name: String,
        val domain: String,
        val language: String,
        val country: String,
        val edition: String
    )

    private data class SourceOutcome(val source: String, val articles: List<NewsArticle>, val error: String?)
}
