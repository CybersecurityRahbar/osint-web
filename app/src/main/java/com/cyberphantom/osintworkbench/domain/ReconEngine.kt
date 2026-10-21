package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ReconRecord(
    val kind: String,
    val value: String,
    val source: String,
    val sourceUrl: String,
    val confidence: Int,
    val detail: String = "",
    val previewImageUrl: String? = null
)

data class ScanReport(
    val target: String,
    val records: List<ReconRecord>,
    val warnings: List<String>,
    val startedAt: Long,
    val finishedAt: Long
) {
    val elapsedMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0L)
    val sourceCount: Int get() = records.map { it.source }.distinct().size

    fun toJson(): String {
        val root = org.json.JSONObject()
            .put("schemaVersion", 1)
            .put("target", target)
            .put("startedAt", startedAt)
            .put("finishedAt", finishedAt)
            .put("warnings", org.json.JSONArray(warnings))
        val items = org.json.JSONArray()
        records.forEach { record ->
            items.put(
                org.json.JSONObject()
                    .put("kind", record.kind)
                    .put("value", record.value)
                    .put("source", record.source)
                    .put("sourceUrl", record.sourceUrl)
                    .put("confidence", record.confidence)
                    .put("detail", record.detail)
                    .put("previewImageUrl", record.previewImageUrl ?: "")
            )
        }
        root.put("records", items)
        return root.toString(2)
    }

    companion object {
        fun fromJson(json: String): ScanReport? = runCatching {
            val root = org.json.JSONObject(json)
            val recordsJson = root.optJSONArray("records") ?: org.json.JSONArray()
            val records = buildList {
                for (index in 0 until recordsJson.length()) {
                    val item = recordsJson.optJSONObject(index) ?: continue
                    add(
                        ReconRecord(
                            kind = item.optString("kind"),
                            value = item.optString("value"),
                            source = item.optString("source"),
                            sourceUrl = item.optString("sourceUrl"),
                            confidence = item.optInt("confidence", 50).coerceIn(0, 100),
                            detail = item.optString("detail"),
                            previewImageUrl = item.optString("previewImageUrl").takeIf { it.startsWith("https://") }
                        )
                    )
                }
            }
            val warningsJson = root.optJSONArray("warnings") ?: org.json.JSONArray()
            val warnings = (0 until warningsJson.length()).map { warningsJson.optString(it) }
            ScanReport(
                target = root.optString("target"),
                records = records,
                warnings = warnings,
                startedAt = root.optLong("startedAt"),
                finishedAt = root.optLong("finishedAt")
            )
        }.getOrNull()
    }
}

class ReconEngine {
    suspend fun scanDomain(
        rawDomain: String,
        includeCertificateTransparency: Boolean,
        includeArchiveAndMetadata: Boolean = true
    ): ScanReport {
        val domain = DomainTools.normalizeHost(rawDomain)
        require(DomainTools.isValidDomain(domain)) { "أدخل نطاقاً صالحاً مثل example.com" }

        val startedAt = System.currentTimeMillis()
        val warnings = java.util.Collections.synchronizedList(mutableListOf<String>())
        val types = listOf(
            DnsQuery("A", 1),
            DnsQuery("AAAA", 28),
            DnsQuery("NS", 2),
            DnsQuery("MX", 15),
            DnsQuery("TXT", 16),
            DnsQuery("CAA", 257)
        )

        val dnsResults = coroutineScope {
            types.map { query ->
                async {
                    runCatching { queryDns(domain, query) }
                        .onFailure { warnings += "${query.label}: ${it.message ?: "تعذر جلب السجل من مزودي DNS"}" }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }

        val extraResults = if (includeArchiveAndMetadata) {
            coroutineScope {
                listOf(
                    async {
                        runCatching { queryRegistration(domain) }
                            .onFailure { warnings += "RDAP: ${it.message ?: "تعذر جلب بيانات التسجيل"}" }
                            .getOrDefault(emptyList())
                    },
                    async {
                        runCatching { queryWayback(domain) }
                            .onFailure { warnings += "Wayback: ${it.message ?: "تعذر جلب الأرشيف"}" }
                            .getOrDefault(emptyList())
                    },
                    async {
                        runCatching { inspectHttps(domain) }
                            .onFailure { warnings += "تحليل HTTPS: ${it.message ?: "تعذر الوصول إلى الموقع"}" }
                            .getOrDefault(emptyList())
                    }
                ).awaitAll().flatten()
            }
        } else emptyList()

        val certificateResults = if (includeCertificateTransparency) {
            runCatching { queryCertificateTransparency(domain) }
                .onFailure { warnings += "Certificate Transparency: ${it.message ?: "تعذر إكمال البحث"}" }
                .getOrDefault(emptyList())
        } else emptyList()

        val records = (dnsResults + extraResults + certificateResults)
            .distinctBy { "${it.kind}|${it.value.lowercase(Locale.ROOT)}" }

        return ScanReport(
            target = domain,
            records = records,
            warnings = warnings.distinct(),
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis()
        )
    }

    private suspend fun queryDns(domain: String, query: DnsQuery): List<ReconRecord> = withContext(Dispatchers.IO) {
        val encoded = enc(domain)
        val providers = listOf(
            "Cloudflare DNS" to "https://cloudflare-dns.com/dns-query?name=$encoded&type=${query.number}",
            "Google Public DNS" to "https://dns.google/resolve?name=$encoded&type=${query.number}"
        )
        var lastError: Exception? = null
        for ((provider, endpoint) in providers) {
            try {
                val json = getText(endpoint, timeoutMs = 7_000, accept = "application/dns-json")
                val root = JSONObject(json)
                val answers = root.optJSONArray("Answer") ?: JSONArray()
                val found = buildList {
                    for (index in 0 until answers.length()) {
                        val answer = answers.optJSONObject(index) ?: continue
                        val value = answer.optString("data").trim()
                        if (value.isNotBlank()) {
                            add(
                                ReconRecord(
                                    kind = "DNS_${query.label}",
                                    value = value,
                                    source = provider,
                                    sourceUrl = endpoint,
                                    confidence = 91,
                                    detail = "DNS response type ${query.label}; TTL ${answer.optLong("TTL", 0)} seconds"
                                )
                            )
                        }
                    }
                }
                if (found.isNotEmpty() || root.optInt("Status", -1) == 3 || root.optInt("Status", -1) == 0) {
                    if (found.isNotEmpty()) return@withContext found
                    // Retry the next resolver for an empty answer unless the authoritative response
                    // explicitly reports a successful empty result; resolver behavior can differ.
                    if (provider == providers.last().first) return@withContext emptyList()
                }
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IllegalStateException(
            "تعذر الوصول إلى Cloudflare DNS وGoogle DNS. تحقق من الاتصال أو قيود الشبكة." +
                (lastError?.message?.let { " ($it)" }?.take(180).orEmpty())
        )
    }

    private suspend fun queryCertificateTransparency(domain: String): List<ReconRecord> = withContext(Dispatchers.IO) {
        val query = enc("%.$domain")
        val endpoint = "https://crt.sh/?q=$query&output=json"
        val body = getText(endpoint, timeoutMs = 12_000)
        val array = JSONArray(body)
        val names = linkedSetOf<String>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            item.optString("name_value")
                .split('\n', '\r', ',', ' ')
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { raw ->
                    val host = DomainTools.normalizeHost(raw.removePrefix("*."))
                    if (host.isNotBlank() &&
                        (host == domain || host.endsWith(".$domain")) &&
                        !host.contains('*') &&
                        DomainTools.isValidDomain(host)
                    ) names += host
                }
        }

        names.map { host ->
            ReconRecord(
                kind = if (host == domain) "CERTIFICATE_NAME" else "SUBDOMAIN",
                value = host,
                source = "Certificate Transparency (crt.sh)",
                sourceUrl = endpoint,
                confidence = 84,
                detail = "اسم ظاهر في بيانات شهادات عامة؛ لا يثبت أن الخدمة نشطة حالياً."
            )
        }
    }

    private suspend fun queryRegistration(domain: String): List<ReconRecord> = withContext(Dispatchers.IO) {
        val endpoint = "https://rdap.org/domain/${enc(domain)}"
        val root = JSONObject(getText(endpoint, timeoutMs = 10_000, accept = "application/rdap+json, application/json"))
        val output = mutableListOf<ReconRecord>()
        val source = "RDAP public registration data"

        val status = root.optJSONArray("status")?.let { array ->
            (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf(String::isNotBlank) }.distinct().joinToString(", ")
        }.orEmpty()
        if (status.isNotBlank()) output += ReconRecord("RDAP_STATUS", status, source, endpoint, 90, "حالة تسجيل النطاق كما يعرضها خادم RDAP.")

        val events = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val action = event.optString("eventAction").lowercase(Locale.ROOT)
            val date = event.optString("eventDate")
            val label = when {
                "registration" in action -> "RDAP_REGISTERED"
                "expiration" in action -> "RDAP_EXPIRES"
                "last changed" in action || "last update" in action -> "RDAP_UPDATED"
                else -> ""
            }
            if (label.isNotBlank() && date.isNotBlank()) {
                output += ReconRecord(label, date, source, endpoint, 90, "تاريخ وارد في بيانات التسجيل العامة.")
            }
        }

        val entities = root.optJSONArray("entities") ?: JSONArray()
        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val roles = entity.optJSONArray("roles") ?: JSONArray()
            val isRegistrar = (0 until roles.length()).any { roles.optString(it).equals("registrar", true) }
            if (!isRegistrar) continue
            val vcard = entity.optJSONArray("vcardArray")?.optJSONArray(1) ?: continue
            for (j in 0 until vcard.length()) {
                val property = vcard.optJSONArray(j) ?: continue
                if (property.optString(0).equals("fn", true)) {
                    val registrar = property.optString(3).trim()
                    if (registrar.isNotBlank()) {
                        output += ReconRecord("RDAP_REGISTRAR", registrar, source, endpoint, 88, "جهة التسجيل؛ لا تتضمن هذه الواجهة جمع بيانات اتصال شخصية.")
                    }
                }
            }
        }
        output
    }

    private suspend fun queryWayback(domain: String): List<ReconRecord> = withContext(Dispatchers.IO) {
        val endpoint = "https://web.archive.org/cdx/search/cdx?url=${enc(domain)}&matchType=domain&output=json&fl=timestamp,original,statuscode&filter=statuscode:200&collapse=timestamp:4&limit=24"
        val json = JSONArray(getText(endpoint, timeoutMs = 12_000))
        val result = mutableListOf<ReconRecord>()
        for (i in 0 until json.length()) {
            val row = json.optJSONArray(i) ?: continue
            // The first JSON row is usually the requested column names.
            if (row.length() < 3 || row.optString(0).equals("timestamp", true)) continue
            val timestamp = row.optString(0)
            val original = row.optString(1)
            val statusCode = row.optString(2)
            if (timestamp.length >= 8 && original.startsWith("http")) {
                val date = timestamp.take(4) + "-" + timestamp.substring(4, 6) + "-" + timestamp.substring(6, 8)
                result += ReconRecord(
                    "WAYBACK_SNAPSHOT",
                    "$date · $original",
                    "Internet Archive Wayback Machine",
                    endpoint,
                    88,
                    "لقطة مؤرشفة، وليست دليلاً على أن الصفحة ما زالت موجودة الآن. HTTP archive status: $statusCode"
                )
            }
        }
        result
    }

    private suspend fun inspectHttps(domain: String): List<ReconRecord> = withContext(Dispatchers.IO) {
        val endpoint = "https://$domain/"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "OSINT-Workbench/0.2 (passive public metadata research)")
            setRequestProperty("Range", "bytes=0-131071")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        try {
            val status = connection.responseCode
            val result = mutableListOf<ReconRecord>()
            val finalUrl = connection.url.toString()
            result += ReconRecord("HTTPS_STATUS", status.toString(), "HTTPS response", endpoint, 95, "رمز استجابة الصفحة العامة. لا يمثل اختبار ثغرات.")
            if (finalUrl != endpoint) {
                result += ReconRecord("HTTPS_REDIRECT", finalUrl, "HTTPS response", endpoint, 90, "العنوان النهائي بعد إعادة التوجيه.")
            }
            connection.getHeaderField("Server")?.takeIf(String::isNotBlank)?.let {
                result += ReconRecord("HTTP_SERVER_HEADER", it.take(180), "HTTP response headers", finalUrl, 62, "ترويسة اختيارية؛ قد تكون محجوبة أو مضللة.")
            }
            connection.getHeaderField("X-Powered-By")?.takeIf(String::isNotBlank)?.let {
                result += ReconRecord("TECH_X_POWERED_BY", it.take(180), "HTTP response headers", finalUrl, 55, "مؤشر تقني ضعيف وليس إثباتاً نهائياً للـ stack.")
            }

            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val html = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (total < 131_072) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 131_072 - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()

            val title = Regex("(?is)<title\\b[^>]*>(.*?)</title>").find(html)?.groupValues?.getOrNull(1)
                ?.replace(Regex("<[^>]+>"), " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(220)
            if (!title.isNullOrBlank()) result += ReconRecord("WEB_TITLE", title, "HTTPS page metadata", finalUrl, 82, "عنوان الصفحة المقروء من HTML العام.")

            val generator = Regex("(?is)<meta[^>]+name=[\"']generator[\"'][^>]+content=[\"']([^\"']+)")
                .find(html)?.groupValues?.getOrNull(1)?.trim()?.take(160)
            if (!generator.isNullOrBlank()) result += ReconRecord("TECH_META_GENERATOR", generator, "HTML metadata", finalUrl, 66, "ترويسة مولّد قابلة للتعديل من مالك الصفحة.")

            val headers = listOf(
                "Strict-Transport-Security" to "HSTS",
                "Content-Security-Policy" to "CSP",
                "X-Content-Type-Options" to "X-Content-Type-Options",
                "X-Frame-Options" to "X-Frame-Options",
                "Referrer-Policy" to "Referrer-Policy"
            )
            for ((header, label) in headers) {
                if (connection.getHeaderField(header).isNullOrBlank()) {
                    result += ReconRecord(
                        "SECURITY_HEADER_NOT_OBSERVED",
                        label,
                        "HTTPS response headers",
                        finalUrl,
                        45,
                        "لم ترصد الترويسة في هذه الاستجابة؛ هذا وحده لا يثبت وجود ثغرة ويحتاج إلى تحقق يدوي."
                    )
                }
            }
            result
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(endpoint: String, timeoutMs: Int, accept: String = "application/json"): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "OSINT-Workbench/0.2 (passive research tool)")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status from ${URL(endpoint).host}")
            }
            if (body.isBlank()) throw IllegalStateException("Empty response from ${URL(endpoint).host}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class DnsQuery(val label: String, val number: Int)
}
