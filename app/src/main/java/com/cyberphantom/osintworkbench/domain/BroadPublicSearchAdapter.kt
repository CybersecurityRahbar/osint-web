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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Public search adapters inspired by the provider-specific engine strategy in ALKASER_OSINT.py. */
internal class BroadPublicSearchAdapter {
    internal data class Outcome(val results: List<PublicProfileResult>, val warnings: List<String>, val attempted: Int)
    private data class Provider(val name: String, val url: String, val type: String = "html")
    private data class Attempt(val name: String, val results: List<PublicProfileResult>, val error: String?)
    private data class ImageBatch(val results: List<PublicProfileResult>, val warnings: List<String>)

    suspend fun search(queries: List<String>, maxResults: Int, includeImages: Boolean): Outcome {
        val terms = queries.map(String::trim).filter(String::isNotBlank).distinct().take(2)
        if (terms.isEmpty()) return Outcome(emptyList(), listOf("لا توجد عبارة صالحة للبحث."), 0)
        val limit = maxResults.coerceIn(5, 40)
        val semaphore = Semaphore(8)
        val (attempts, images) = coroutineScope {
            val webTask = async {
                terms.flatMapIndexed { index, query ->
                    providers(query, index == 0).map { provider -> async { runProvider(semaphore, provider, limit) } }
                }.awaitAll()
            }
            val imageTask = async {
                if (includeImages) searchImages(terms.first(), limit, semaphore)
                else ImageBatch(emptyList(), emptyList())
            }
            webTask.await() to imageTask.await()
        }
        val allWeb = attempts.flatMap { it.results }
        val merged = (allWeb + images.results).distinctBy {
            if (!it.imageUrl.isNullOrBlank()) "image:" + canonical(it.imageUrl) else canonical(it.url)
        }.take(limit)
        val warnings = buildList {
            attempts.filter { it.error != null }.take(8).forEach { add(it.name + ": " + it.error) }
            addAll(images.warnings.take(4))
            if (allWeb.isEmpty()) add("لم تُستخرج صفحات ويب من المصادر التي أمكن الوصول إليها. قد تكون المصادر محجوبة أو تغير قالبها؛ النتيجة الصفرية لا تثبت غياب المعلومات.")
            if (includeImages && images.results.isEmpty()) add("لم تُرجع مصادر الصور روابط صور قابلة للعرض في هذا البحث.")
        }.distinct()
        return Outcome(merged, warnings, attempts.size + if (includeImages) 3 else 0)
    }

    private fun providers(query: String, primary: Boolean): List<Provider> {
        val q = encode(query)
        return if (primary) listOf(
            Provider("DuckDuckGo", "https://html.duckduckgo.com/html/?q=$q&kl=wt-wt"),
            Provider("Bing", "https://www.bing.com/search?q=$q&count=10"),
            Provider("Brave", "https://search.brave.com/search?q=$q"),
            Provider("Mojeek", "https://www.mojeek.com/search?q=$q&si=1"),
            Provider("Qwant JSON", "https://api.qwant.com/v3/search/web?q=$q&count=10&locale=en_us&safesearch=0", "qwant"),
            Provider("SearXNG · searx.be", "https://searx.be/search?q=$q&format=json", "searx"),
            Provider("Startpage", "https://www.startpage.com/do/search?q=$q&cat=web"),
            Provider("Yandex", "https://yandex.com/search/?text=$q&lr=84")
        ) else listOf(
            Provider("DuckDuckGo Lite", "https://lite.duckduckgo.com/lite/?q=$q"),
            Provider("Bing", "https://www.bing.com/search?q=$q&count=10"),
            Provider("Ecosia", "https://www.ecosia.org/search?q=$q"),
            Provider("SearXNG · disroot", "https://search.disroot.org/search?q=$q&format=json", "searx")
        )
    }

    private suspend fun runProvider(semaphore: Semaphore, provider: Provider, limit: Int): Attempt {
        semaphore.acquire()
        try {
            return try {
                val results = withContext(Dispatchers.IO) {
                    when (provider.type) {
                        "qwant" -> parseQwant(JSONObject(fetch(provider.url, 4_500, "application/json")), provider.name, limit)
                        "searx" -> parseSearx(JSONObject(fetch(provider.url, 4_500, "application/json")), provider.name, limit)
                        else -> parseHtml(fetch(provider.url, 4_500), provider.name, limit)
                    }
                }
                if (results.isEmpty()) Attempt(provider.name, emptyList(), "لم يتعرف المحلل على نتائج خارجية؛ قد تكون الصفحة محجوبة أو تغير قالبها.")
                else Attempt(provider.name, results, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Attempt(provider.name, emptyList(), error.message ?: "تعذر الوصول إلى المصدر")
            }
        } finally {
            semaphore.release()
        }
    }

    internal fun parseHtml(html: String, provider: String, limit: Int = 20): List<PublicProfileResult> {
        val hits = mutableListOf<Triple<String, String, String>>()
        fun add(attrs: String, rawTitle: String, snippet: String = "") {
            val href = attr(attrs, "href") ?: return
            val title = clean(rawTitle)
            if (title.length >= 3) hits += Triple(href, title, clean(snippet))
        }
        val key = provider.lowercase(Locale.ROOT)
        when {
            key.contains("duckduckgo") -> Regex("""(?is)<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*(?:result__a|result-link)[^"']*["'])[^>]*>(.*?)</a>""").findAll(html)
                .forEach { add(it.value.substringBefore('>'), it.groupValues[1]) }
            key == "bing" -> {
                for (block in Regex("""(?is)<li\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bb_algo\b[^"']*["'])[^>]*>(.*?)</li>""").findAll(html)) {
                    val body = block.groupValues[1]
                    val a = Regex("""(?is)<h2\b[^>]*>.*?<a\b([^>]*)>(.*?)</a>.*?</h2>""").find(body) ?: continue
                    val snippet = Regex("""(?is)<p\b[^>]*>(.*?)</p>""").find(body)?.groupValues?.getOrNull(1).orEmpty()
                    add(a.groupValues[1], a.groupValues[2], snippet)
                }
            }
            key.contains("brave") -> Regex("""(?is)<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*(?:result-header|heading)[^"']*["'])[^>]*>(.*?)</a>""").findAll(html)
                .forEach { add(it.value.substringBefore('>'), it.groupValues[1]) }
            key.contains("mojeek") -> Regex("""(?is)<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bob\b[^"']*["'])[^>]*>(.*?)</a>""").findAll(html)
                .forEach { add(it.value.substringBefore('>'), it.groupValues[1]) }
            key.contains("yandex") -> {
                for (block in Regex("""(?is)<(?:li|div)\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bserp-item\b[^"']*["'])[^>]*>(.*?)</(?:li|div)>""").findAll(html)) {
                    val a = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").find(block.groupValues[1]) ?: continue
                    add(a.groupValues[1], a.groupValues[2])
                }
            }
            else -> {
                for (block in Regex("""(?is)<(?:div|article)\b(?=[^>]*\bclass\s*=\s*["'][^"']*(?:w-gl__result|result)[^"']*["'])[^>]*>(.*?)</(?:div|article)>""").findAll(html)) {
                    val body = block.groupValues[1]
                    val a = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").find(body) ?: continue
                    val snippet = Regex("""(?is)<p\b[^>]*>(.*?)</p>""").find(body)?.groupValues?.getOrNull(1).orEmpty()
                    add(a.groupValues[1], a.groupValues[2], snippet)
                }
            }
        }
        if (hits.isEmpty()) Regex("""(?is)<h2\b[^>]*>.*?<a\b([^>]*)>(.*?)</a>.*?</h2>""").findAll(html)
            .forEach { add(it.groupValues[1], it.groupValues[2]) }
        if (hits.isEmpty()) Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").findAll(html)
            .filter { it.groupValues[2].contains("<h3", true) }
            .forEach { add(it.groupValues[1], it.groupValues[2]) }

        val rejected = setOf("google.com", "bing.com", "microsoft.com", "duckduckgo.com", "brave.com", "mojeek.com", "yahoo.com", "startpage.com", "ecosia.org", "yandex.com")
        return hits.mapNotNull { (attrs, title, snippet) ->
            val url = unwrap(attrs) ?: return@mapNotNull null
            val host = host(url)
            if (host.isBlank() || rejected.any { host == it || host.endsWith(".$it") }) return@mapNotNull null
            PublicProfileResult("$provider · $host", clean(title).take(240), clean(snippet).ifBlank { "نتيجة ويب عامة مفهرسة؛ افتح المصدر للتحقق من النص والسياق." }.take(500), url, if (snippet.isBlank()) 60 else 70)
        }.distinctBy { canonical(it.url) }.take(limit)
    }

    private fun parseQwant(root: JSONObject, provider: String, limit: Int): List<PublicProfileResult> {
        val sections = root.optJSONObject("data")?.optJSONObject("result")?.optJSONObject("items")?.optJSONArray("mainline") ?: JSONArray()
        val hits = mutableListOf<JSONObject>()
        for (i in 0 until sections.length()) {
            val rows = sections.optJSONObject(i)?.optJSONArray("items") ?: continue
            for (j in 0 until rows.length()) rows.optJSONObject(j)?.let(hits::add)
        }
        return hits.mapNotNull { row ->
            val url = row.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val title = clean(row.optString("title")).takeIf { it.length >= 3 } ?: return@mapNotNull null
            PublicProfileResult(provider, title.take(240), clean(row.optString("desc")).take(500), url, 70)
        }.distinctBy { canonical(it.url) }.take(limit)
    }

    private fun parseSearx(root: JSONObject, provider: String, limit: Int): List<PublicProfileResult> {
        val rows = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until minOf(rows.length(), limit * 3)) {
                val row = rows.optJSONObject(i) ?: continue
                val url = row.optString("url").takeIf { it.startsWith("https://") } ?: continue
                val title = clean(row.optString("title")).takeIf { it.length >= 3 } ?: continue
                add(PublicProfileResult(provider, title.take(240), clean(row.optString("content")).take(500), url, 68))
                if (size >= limit) break
            }
        }.distinctBy { canonical(it.url) }
    }

    private suspend fun searchImages(query: String, limit: Int, semaphore: Semaphore): ImageBatch {
        val attempts = coroutineScope {
            listOf(
                async { imageAttempt(semaphore, "Bing Images") { bingImages(query, minOf(12, limit)) } },
                async { imageAttempt(semaphore, "SearXNG Images") { searxImages(query, minOf(10, limit)) } },
                async { imageAttempt(semaphore, "Wikimedia Commons") { commonsImages(query, minOf(10, limit)) } }
            ).awaitAll()
        }
        return ImageBatch(attempts.flatMap { it.results }.distinctBy { canonical(it.imageUrl ?: it.url) }.take(limit),
            attempts.mapNotNull { if (it.error == null) null else it.name + ": " + it.error })
    }

    private suspend fun imageAttempt(semaphore: Semaphore, provider: String, action: suspend () -> List<PublicProfileResult>): Attempt {
        semaphore.acquire()
        try {
            return try { Attempt(provider, action(), null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Attempt(provider, emptyList(), error.message ?: "تعذر الوصول إلى المصدر") }
        } finally {
            semaphore.release()
        }
    }

    private suspend fun bingImages(query: String, limit: Int): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val q = encode(query)
        val html = fetch("https://www.bing.com/images/search?q=$q&form=HDRSC3", 4_500, "text/html")
        val tags = Regex("""(?is)<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*\biusc\b[^"']*["'])[^>]*>""").findAll(html)
        buildList {
            for (match in tags) {
                val metadataRaw = attr(match.value, "m") ?: continue
                val metadata = runCatching { JSONObject(clean(metadataRaw)) }.getOrNull() ?: continue
                val original = metadata.optString("murl").takeIf { it.startsWith("https://") } ?: continue
                val thumb = metadata.optString("turl").takeIf { it.startsWith("https://") } ?: original
                val page = metadata.optString("purl").takeIf { it.startsWith("https://") } ?: original
                add(PublicProfileResult("Bing Images", clean(metadata.optString("t")).ifBlank { "صورة مفهرسة من Bing" }.take(240), "صورة مفهرسة عامة؛ تحقق من المصدر الأصلي قبل نسبتها إلى شخص أو موضوع.", page, 70, 50, thumb))
                if (size >= limit) break
            }
        }.distinctBy { canonical(it.imageUrl ?: it.url) }
    }

    private suspend fun searxImages(query: String, limit: Int): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (instance in listOf("https://searx.be", "https://search.disroot.org")) {
            try {
                val q = encode(query)
                val root = JSONObject(fetch("$instance/search?q=$q&categories=images&format=json", 4_500, "application/json"))
                val rows = root.optJSONArray("results") ?: JSONArray()
                val found = buildList {
                    for (i in 0 until minOf(rows.length(), limit * 3)) {
                        val row = rows.optJSONObject(i) ?: continue
                        val image = sequenceOf("thumbnail_src", "img_src", "thumbnail", "image").map { row.optString(it).takeIf { value -> value.startsWith("https://") } }.firstOrNull { it != null } ?: continue
                        val page = row.optString("url").takeIf { it.startsWith("https://") } ?: image
                        add(PublicProfileResult("SearXNG Images", clean(row.optString("title")).ifBlank { "صورة مفهرسة" }.take(240), clean(row.optString("content")).take(400), page, 64, 45, image))
                        if (size >= limit) break
                    }
                }
                if (found.isNotEmpty()) return@withContext found.distinctBy { canonical(it.imageUrl ?: it.url) }
            } catch (error: Exception) { lastError = error }
        }
        throw lastError ?: IllegalStateException("لم تُرجع مصادر SearXNG صوراً بصيغة JSON")
    }

    private suspend fun commonsImages(query: String, limit: Int): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val q = encode(query)
        val bounded = limit.coerceIn(1, 12)
        val endpoint = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$q&gsrnamespace=6&gsrlimit=$bounded&prop=info%7Cimageinfo&inprop=url&iiprop=url&iiurlwidth=560&format=json"
        val root = JSONObject(fetch(endpoint, 5_500, "application/json"))
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
        val keys = pages.keys()
        buildList {
            while (keys.hasNext() && size < limit) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val title = page.optString("title").removePrefix("File:")
                if (title.isBlank()) continue
                val titleQuery = encode("File:" + title)
                val pageUrl = page.optString("fullurl").takeIf { it.startsWith("https://") } ?: "https://commons.wikimedia.org/wiki/$titleQuery"
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val image = info.optString("thumburl").ifBlank { info.optString("url") }.takeIf { it.startsWith("https://") } ?: continue
                add(PublicProfileResult("Wikimedia Commons", title, "ملف صورة عام؛ راجع صفحة المصدر والترخيص قبل إعادة الاستخدام.", pageUrl, 72, 45, image))
            }
        }
    }

    private fun fetch(url: String, timeout: Int, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeout
            readTimeout = timeout
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9,ar;q=0.7")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (total < 1_200_000) {
                    val n = input.read(buffer, 0, minOf(buffer.size, 1_200_000 - total))
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                    total += n
                }
                output.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP " + status + " from " + URL(url).host)
            if (body.isBlank()) throw IllegalStateException("استجابة فارغة من " + URL(url).host)
            if (body.contains("captcha", true) && body.length < 250_000) throw IllegalStateException("أعاد المصدر صفحة CAPTCHA بدلاً من النتائج")
            return body
        } finally { connection.disconnect() }
    }

    private fun unwrap(attrs: String): String? {
        val raw = attr(attrs, "href") ?: attrs
        return runCatching {
            val absolute = when {
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/url?") -> "https://www.google.com$raw"
                raw.startsWith("/") -> "https://duckduckgo.com$raw"
                else -> raw
            }
            val uri = URL(absolute)
            val h = uri.host.lowercase(Locale.ROOT)
            val params = uri.query.orEmpty().split('&').mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }.toMap()
            val encoded = when {
                h.endsWith("duckduckgo.com") -> params["uddg"]
                h.startsWith("google.") || (h.contains(".google.") && uri.path == "/url") -> params["q"] ?: params["url"]
                h == "r.search.yahoo.com" || h == "search.yahoo.com" -> params["url"] ?: params["u"] ?: params["RU"]
                else -> null
            }
            val target = encoded?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            val finalUrl = target?.takeIf { it.startsWith("https://") } ?: absolute
            val finalHost = URL(finalUrl).host.lowercase(Locale.ROOT)
            if (!finalUrl.startsWith("https://") || finalUrl.length > 2_048) return@runCatching null
            if (finalHost == "duckduckgo.com" || finalHost.endsWith(".duckduckgo.com") ||
                finalHost == "google.com" || finalHost.endsWith(".google.com") ||
                finalHost == "bing.com" || finalHost.endsWith(".bing.com") ||
                finalHost == "search.yahoo.com" || finalHost == "r.search.yahoo.com") return@runCatching null
            finalUrl
        }.getOrNull()
    }

    private fun attr(tag: String, name: String): String? =
        Regex("(?is)\\b" + Regex.escape(name) + "\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']").find(tag)?.groupValues?.getOrNull(1)

    private fun clean(value: String): String = value
        .replace(Regex("(?is)<script\\b.*?</script>"), " ")
        .replace(Regex("(?is)<style\\b.*?</style>"), " ")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#x27;", "'").replace("&#x2F;", "/")
        .replace(Regex("\\\\s+"), " ").trim()

    private fun canonical(raw: String): String = runCatching {
        URL(raw).run { protocol.lowercase(Locale.ROOT) + "://" + host.lowercase(Locale.ROOT) + path.orEmpty().trimEnd('/') }
    }.getOrDefault(raw.substringBefore('#').substringBefore('?').trimEnd('/')).lowercase(Locale.ROOT)
    private fun host(raw: String): String = runCatching { URL(raw).host.lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault("")
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}