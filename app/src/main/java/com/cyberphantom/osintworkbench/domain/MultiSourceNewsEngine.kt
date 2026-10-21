package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.w3c.dom.Element

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

        var outcomes = coroutineScope {
            val semaphore = Semaphore(4)
            val tasks = mutableListOf<kotlinx.coroutines.Deferred<SourceOutcome>>()
            tasks += async { runSource(semaphore, "GDELT DOC API") { queryGdelt(clean, limit, includeImages) } }
            tasks += async {
                runSource(semaphore, "Google News RSS · Arabic") {
                    queryGoogleNews(clean, limit, null, includeImages, "ar", "SA", "SA:ar", "Google News RSS · Arabic")
                }
            }
            tasks += async {
                runSource(semaphore, "Google News RSS · English") {
                    queryGoogleNews(clean, limit, null, includeImages, "en-US", "US", "US:en", "Google News RSS · English")
                }
            }
            tasks += async { runSource(semaphore, "Bing News RSS") { queryBingNews(clean, limit, includeImages) } }
            tasks += async { runSource(semaphore, "SearXNG News") { querySearxNews(clean, limit, includeImages) } }
            tasks.awaitAll()
        }

        if (outcomes.none { it.articles.isNotEmpty() }) {
            val fallbacks = listOf(publishers[0], publishers[2], publishers[4], publishers[7])
            val extra = coroutineScope {
                val semaphore = Semaphore(3)
                fallbacks.map { publisher ->
                    async {
                        runSource(semaphore, "Google News RSS · " + publisher.name) {
                            queryGoogleNews(clean, maxOf(6, limit / 2), publisher, includeImages)
                        }
                    }
                }.awaitAll()
            }
            outcomes = outcomes + extra
        }

        val articles = outcomes.flatMap { it.articles }
            .filter { it.title.isNotBlank() && it.url.startsWith("https://") }
            .distinctBy {
                it.title.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim() + "|" +
                    it.publisherName.ifBlank { it.domain }.lowercase(Locale.ROOT)
            }
            .take(limit)

        val failed = outcomes.filter { it.error != null }
        val emptySources = outcomes.filter { it.error == null && it.articles.isEmpty() }.map { it.source }
        val failureText = failed.take(6).joinToString(" · ") { it.source + ": " + it.error }
        val warning = when {
            articles.isEmpty() -> {
                "لم تُرجع مصادر الأخبار مقالات. " +
                    (if (failureText.isBlank()) "لم يبلغ أي مصدر عن خطأ صريح." else "أخطاء المصادر: " + failureText) +
                    (if (emptySources.isEmpty()) "" else " مصادر استجابت دون مقالات: " + emptySources.take(5).joinToString("، "))
            }
            failed.isNotEmpty() -> "نتائج جزئية من " + outcomes.count { it.articles.isNotEmpty() } +
                " مصدر. تعذرت بعض المصادر: " + failureText
            else -> null
        }
        NewsSearchResult(clean, articles, warning)
    }

    private suspend fun runSource(
        semaphore: Semaphore,
        source: String,
        fetch: suspend () -> List<NewsArticle>
    ): SourceOutcome {
        semaphore.acquire()
        try {
            return try {
                SourceOutcome(source, fetch(), null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceOutcome(source, emptyList(), error.message ?: "تعذر الوصول")
            }
        } finally {
            semaphore.release()
        }
    }

    private suspend fun queryGdelt(query: String, maxRecords: Int, includeImages: Boolean): List<NewsArticle> = withContext(Dispatchers.IO) {
        val limit = maxRecords.coerceIn(1, 50)
        val cleanQuery = query.trim().replace(Regex("\\s+"), " ")
        // GDELT requires OR groups to be parenthesized; ungrouped user text can return HTTP 200 HTML errors.
        val apiQuery = if (Regex("(?i)\\bOR\\b").containsMatchIn(cleanQuery) &&
            !cleanQuery.trim().startsWith("(")) "($cleanQuery)" else cleanQuery
        // First query is a fast recent-news request; the fallback widens the period and lowers the cap.
        val endpoints = listOf(
            "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(apiQuery)}&mode=artlist&format=json&sort=datedesc&maxrecords=$limit&timespan=1week",
            "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(apiQuery)}&mode=artlist&format=json&maxrecords=${minOf(limit, 20)}"
        )
        var lastFailure: Exception? = null
        for ((endpointIndex, endpoint) in endpoints.withIndex()) {
            val maxAttempts = if (endpointIndex == 0) 2 else 1
            for (attempt in 0 until maxAttempts) {
                try {
                    val raw = getText(endpoint, 8_000, "application/json, text/plain, */*")
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
                    if (!retryable || attempt == maxAttempts - 1) break
                    // Respect transient rate limits; short tight retry loops tend to worsen HTTP 429/503 responses.
                    Thread.sleep(2_500L)
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
        includeImages: Boolean,
        languageOverride: String? = null,
        countryOverride: String? = null,
        editionOverride: String? = null,
        providerOverride: String? = null
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val queryText = if (publisher == null) query else query + " site:" + publisher.domain
        val language = publisher?.language ?: languageOverride ?: "ar"
        val country = publisher?.country ?: countryOverride ?: "YE"
        val edition = publisher?.edition ?: editionOverride ?: "YE:ar"
        val q = enc(queryText)
        val hl = enc(language)
        val gl = enc(country)
        val ceid = enc(edition)
        val endpoint = "https://news.google.com/rss/search?q=$q&hl=$hl&gl=$gl&ceid=$ceid"
        val xml = getText(endpoint, 8_500, "application/rss+xml, application/xml, text/xml")
        val label = providerOverride ?: if (publisher == null) "Google News RSS" else "Google News RSS · " + publisher.name
        parseRss(xml, publisher, language, country, includeImages, label)
            .take(maxRecords.coerceIn(1, 60))
    }

    private suspend fun queryBingNews(query: String, maxRecords: Int, includeImages: Boolean): List<NewsArticle> = withContext(Dispatchers.IO) {
        val q = enc(query)
        val endpoint = "https://www.bing.com/news/search?q=$q&format=rss&setmkt=en-US"
        val xml = getText(endpoint, 8_000, "application/rss+xml, application/xml, text/xml")
        parseRss(xml, null, "en", "US", includeImages, "Bing News RSS")
            .take(maxRecords.coerceIn(1, 60))
    }

    private suspend fun querySearxNews(query: String, maxRecords: Int, includeImages: Boolean): List<NewsArticle> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (instance in listOf("https://searx.be", "https://search.disroot.org")) {
            try {
                val q = enc(query)
                val endpoint = instance + "/search?q=$q&categories=news&format=json"
                val root = JSONObject(getText(endpoint, 4_500, "application/json"))
                val rows = root.optJSONArray("results") ?: JSONArray()
                val articles = buildList {
                    for (i in 0 until minOf(rows.length(), maxRecords * 3)) {
                        val row = rows.optJSONObject(i) ?: continue
                        val url = row.optString("url").takeIf { it.startsWith("https://") } ?: continue
                        val title = row.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                        val domain = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")
                        val image = sequenceOf("thumbnail_src", "img_src", "thumbnail")
                            .map { key -> row.optString(key).takeIf { it.startsWith("https://") } }
                            .firstOrNull { it != null }
                        add(
                            NewsArticle(
                                title = title,
                                url = url,
                                domain = domain,
                                publishedAt = row.optString("publishedDate").ifBlank { row.optString("published_date") },
                                language = "",
                                sourceCountry = "",
                                publisherName = row.optString("source").ifBlank { publisherLabel(domain) },
                                imageUrl = image.takeIf { includeImages },
                                provider = "SearXNG News"
                            )
                        )
                        if (size >= maxRecords) break
                    }
                }
                if (articles.isNotEmpty()) return@withContext articles.distinctBy { it.url }
                lastError = IllegalStateException("أعاد SearXNG استجابة JSON دون مقالات")
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("تعذر الوصول إلى مصادر SearXNG News")
    }

    internal fun parseRss(
        xml: String,
        requestedPublisher: Publisher?,
        language: String,
        country: String,
        includeImages: Boolean = true,
        providerLabelOverride: String? = null
    ): List<NewsArticle> {
        require(xml.length <= 2_000_000) { "RSS response exceeds the parser size limit." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val items = document.getElementsByTagName("item")
        return buildList {
            for (index in 0 until items.length) {
                val item = items.item(index) as? Element ?: continue
                var title = ""
                var link = ""
                var publishedAt = ""
                var sourceName = ""
                var sourceUrl = ""
                var imageUrl = ""

                val children = item.childNodes
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex) as? Element ?: continue
                    when (child.tagName.substringAfter(':').lowercase(Locale.ROOT)) {
                        "title" -> title = child.textContent.orEmpty().trim()
                        "link" -> link = normalizeNewsUrl(child.textContent.orEmpty().trim())
                        "pubdate" -> publishedAt = child.textContent.orEmpty().trim()
                        "source" -> {
                            sourceUrl = child.getAttribute("url").orEmpty()
                            sourceName = child.textContent.orEmpty().trim()
                        }
                        "thumbnail", "content", "enclosure" -> {
                            val candidate = child.getAttribute("url").orEmpty()
                            if (imageUrl.isBlank()) imageUrl = candidate
                        }
                    }
                }
                if (title.isBlank() || !link.startsWith("https://")) continue

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
                add(
                    NewsArticle(
                        title = stripPublisherSuffix(title, actualPublisher),
                        url = link,
                        domain = attributedDomain,
                        publishedAt = publishedAt,
                        language = language,
                        sourceCountry = country,
                        publisherName = actualPublisher,
                        imageUrl = imageUrl.takeIf { includeImages && it.startsWith("https://") },
                        provider = providerLabelOverride ?: if (requestedPublisher == null) "Google News RSS" else "Google News RSS · " + requestedPublisher.name
                    )
                )
            }
        }.distinctBy { "${it.title.lowercase(Locale.ROOT)}|${it.publisherName.lowercase(Locale.ROOT)}" }
    }

    private fun normalizeNewsUrl(raw: String): String {
        if (!raw.startsWith("https://")) return raw
        return runCatching {
            val uri = URL(raw)
            if (!uri.host.endsWith("bing.com") || !uri.path.contains("apiclick")) return@runCatching raw
            val params = uri.query.orEmpty().split('&').mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }.toMap()
            val encoded = params["url"] ?: params["u"]
            val decoded = encoded?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            decoded?.takeIf { it.startsWith("https://") } ?: raw
        }.getOrDefault(raw)
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
