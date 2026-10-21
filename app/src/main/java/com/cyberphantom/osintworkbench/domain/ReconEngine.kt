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
        includeArchiveAndMetadata: Boolean = true,
        includeActiveChecks: Boolean = false
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

        val geoResults = if (includeArchiveAndMetadata) {
            runCatching { queryIpGeolocation(dnsResults) }
                .onFailure { warnings += "تحديد موقع IP: ${it.message ?: "تعذر جلب بيانات الموقع التقريبي"}" }
                .getOrDefault(emptyList())
        } else emptyList()

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

        val activeResults = if (includeActiveChecks) {
            runCatching { runLimitedActiveChecks(domain) }
                .onFailure { warnings += "الفحوص النشطة المحدودة: ${it.message ?: "تعذر إكمال فحص الاتصال"}" }
                .getOrDefault(emptyList())
        } else emptyList()

        val records = (dnsResults + geoResults + extraResults + certificateResults + activeResults)
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
        val endpoints = listOf(
            "https://web.archive.org/cdx/search/cdx?url=${enc(domain)}&matchType=domain&output=json&fl=timestamp,original,statuscode,mimetype&filter=statuscode:200&filter=mimetype:text/html&collapse=urlkey&limit=40",
            "https://web.archive.org/cdx/search/cdx?url=${enc("https://$domain/")}&output=json&fl=timestamp,original,statuscode,mimetype&filter=statuscode:200&collapse=urlkey&limit=20"
        )
        val captures = mutableListOf<ArchiveCapture>()
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val json = JSONArray(getText(endpoint, timeoutMs = 12_000))
                for (i in 0 until json.length()) {
                    val row = json.optJSONArray(i) ?: continue
                    if (row.length() < 3 || row.optString(0).equals("timestamp", true)) continue
                    val timestamp = row.optString(0)
                    val original = row.optString(1)
                    val statusCode = row.optString(2)
                    if (timestamp.length >= 8 && original.startsWith("http")) {
                        captures += ArchiveCapture(timestamp, original, statusCode)
                    }
                }
                // Even a valid empty response means this query was understood; try the second
                // query for better coverage, but don't treat empty JSON as a service failure.
                if (captures.isNotEmpty()) break
            } catch (error: Exception) {
                lastError = error
            }
        }

        // The CDX index can temporarily return HTTP 503. Internet Archive's Availability API
        // provides a smaller alternative when the index is overloaded.
        if (captures.isEmpty()) {
            val fallbackEndpoint = "https://archive.org/wayback/available?url=${enc("https://$domain/")}"
            try {
                val root = JSONObject(getText(fallbackEndpoint, timeoutMs = 10_000))
                val closest = root.optJSONObject("archived_snapshots")?.optJSONObject("closest")
                if (closest != null && closest.optBoolean("available", false)) {
                    val timestamp = closest.optString("timestamp")
                    val original = closest.optString("url").takeIf { it.startsWith("http") } ?: "https://$domain/"
                    if (timestamp.length >= 8) captures += ArchiveCapture(timestamp, original, closest.optString("status", "200"))
                }
            } catch (error: Exception) {
                if (lastError == null) lastError = error
            }
        }

        if (captures.isEmpty() && lastError != null) {
            throw IllegalStateException(
                "Wayback لم يستجب بعد محاولات بديلة؛ قد يكون HTTP 503 مؤقتاً. أعد المحاولة لاحقاً. السبب: " +
                    (lastError?.message ?: "مصدر الأرشيف غير متاح"),
                lastError
            )
        }
        if (captures.isEmpty()) {
            return@withContext listOf(
                ReconRecord(
                    kind = "WAYBACK_STATUS",
                    value = "لم تُرجع واجهة الأرشيف لقطات متاحة لهذا النطاق",
                    source = "Internet Archive Wayback Availability API",
                    sourceUrl = "https://archive.org/wayback/available?url=${enc("https://$domain/")}",
                    confidence = 35,
                    detail = "هذا يعني أن الاستعلامات الحالية لم تُرجع لقطة؛ لا يثبت ذلك أن النطاق لم يُؤرشف قط. يمكن إعادة المحاولة أو فتح الأرشيف يدوياً."
                )
            )
        }

        val ordered = captures.distinctBy { "${it.timestamp}|${it.original}" }
            .sortedByDescending { it.timestamp }
            .take(18)

        val previewCapture = ordered.firstOrNull()
        val previewImage = previewCapture?.let {
            runCatching { extractPreviewImage(it.timestamp, it.original) }.getOrNull()
        }
        ordered.map { capture ->
            val date = capture.timestamp.take(4) + "-" + capture.timestamp.substring(4, 6) + "-" + capture.timestamp.substring(6, 8)
            val snapshotUrl = "https://web.archive.org/web/${capture.timestamp}/${capture.original}"
            ReconRecord(
                kind = "WAYBACK_SNAPSHOT",
                value = "$date · ${capture.original}",
                source = "Internet Archive Wayback Machine",
                sourceUrl = snapshotUrl,
                confidence = 90,
                detail = "لقطة HTML مؤرشفة. قد لا تعمل كل الموارد المضمّنة، ولا تثبت اللقطة أن الصفحة نشطة الآن. حالة الأرشيف: ${capture.statusCode}. فهرس اللقطات: ${endpoints.first()}",
                previewImageUrl = if (capture == previewCapture) previewImage else null
            )
        }
    }

    private suspend fun extractPreviewImage(timestamp: String, originalUrl: String): String? = withContext(Dispatchers.IO) {
        val snapshotUrl = "https://web.archive.org/web/${timestamp}/${originalUrl}"
        val connection = (URL(snapshotUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 6_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Range", "bytes=0-98303")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("User-Agent", "OSINT-Workbench/0.3 (public archive preview)")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..399) return@withContext null
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val html = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (total < 98_304) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 98_304 - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            val metaTags = Regex("(?is)<meta\\b[^>]*>").findAll(html).map { it.value }
            val imageValue = metaTags.firstNotNullOfOrNull { tag ->
                val property = Regex("""(?is)\b(?:property|name)\s*=\s*["']([^"']+)["']""")
                    .find(tag)?.groupValues?.getOrNull(1).orEmpty()
                if (!property.equals("og:image", true) && !property.equals("twitter:image", true)) return@firstNotNullOfOrNull null
                Regex("""(?is)\bcontent\s*=\s*["']([^"']+)["']""")
                    .find(tag)?.groupValues?.getOrNull(1)?.trim()
            } ?: return@withContext null
            val resolved = URL(URL(originalUrl), imageValue).toString()
            resolved.takeIf { it.startsWith("https://") && it.length <= 2_048 }
        } finally {
            connection.disconnect()
        }
    }

    private data class ArchiveCapture(
        val timestamp: String,
        val original: String,
        val statusCode: String
    )

    private suspend fun queryIpGeolocation(dnsRecords: List<ReconRecord>): List<ReconRecord> = withContext(Dispatchers.IO) {
        val ips = dnsRecords.asSequence()
            .filter { it.kind == "DNS_A" || it.kind == "DNS_AAAA" }
            .map { it.value.trim().trimEnd('.') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
            .toList()
        buildList {
            for (ip in ips) {
                val providers = listOf(
                    "ipwho.is" to "https://ipwho.is/${enc(ip)}",
                    "ipapi.co" to "https://ipapi.co/${enc(ip)}/json/"
                )
                var location: String? = null
                var isp: String? = null
                var source = ""
                var sourceUrl = ""
                var failure: String? = null
                for ((provider, endpoint) in providers) {
                    try {
                        val root = JSONObject(getText(endpoint, timeoutMs = 7_000))
                        if (provider == "ipwho.is" && !root.optBoolean("success", true)) {
                            failure = root.optString("message").ifBlank { "provider marked result unsuccessful" }
                            continue
                        }
                        val country = (root.optString("country").takeIf { it.isNotBlank() && it != "null" }
                            ?: root.optString("country_name").takeIf { it.isNotBlank() && it != "null" })
                        if (country.isNullOrBlank()) continue
                        val region = root.optString("region").takeIf { it.isNotBlank() && it != "null" }
                        val city = root.optString("city").takeIf { it.isNotBlank() && it != "null" }
                        location = listOfNotNull(city, region, country).distinct().joinToString(", ")
                        isp = root.optJSONObject("connection")?.optString("isp")?.takeIf { it.isNotBlank() && it != "null" }
                            ?: root.optString("org").takeIf { it.isNotBlank() && it != "null" }
                        source = "$provider public IP geolocation"
                        sourceUrl = endpoint
                        break
                    } catch (error: Exception) {
                        failure = error.message
                    }
                }
                if (location != null) {
                    add(
                        ReconRecord(
                            kind = "IP_GEOLOCATION",
                            value = "$ip · $location" + (isp?.let { " · $it" } ?: ""),
                            source = source,
                            sourceUrl = sourceUrl,
                            confidence = 42,
                            detail = "موقع تقريبي لعنوان IP وليس عنواناً مادياً مؤكداً للخادم. قد يشير إلى مركز بيانات أو CDN أو استضافة مشتركة أو وكيل؛ لا يحدد مكان شخص."
                        )
                    )
                } else {
                    add(
                        ReconRecord(
                            kind = "IP_GEOLOCATION_UNAVAILABLE",
                            value = "تعذر تحديد الموقع التقريبي لـ $ip",
                            source = "IP geolocation fallback providers",
                            sourceUrl = providers.first().second,
                            confidence = 10,
                            detail = "تعذر الوصول إلى مزودي تحديد الموقع أو لم يعيدوا موقعاً. لا يمكن استنتاج مكان الخادم من عنوان IP وحده دون بيانات قابلة للتحقق. ${failure.orEmpty()}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Optional low-impact checks for targets the user owns or is authorized to test.
     * This does not try default credentials, authenticate, inject payloads, bypass controls,
     * exploit services, or retain sensitive response bodies.
     */
    private suspend fun runLimitedActiveChecks(domain: String): List<ReconRecord> = withContext(Dispatchers.IO) {
        val addresses = java.net.InetAddress.getAllByName(domain).distinctBy { it.hostAddress }.take(2)
        if (addresses.isEmpty()) return@withContext emptyList()
        val ports = listOf(
            21 to "FTP", 22 to "SSH", 25 to "SMTP", 53 to "DNS", 80 to "HTTP",
            110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB", 587 to "SMTP submission",
            993 to "IMAPS", 995 to "POP3S", 1433 to "MS SQL", 2375 to "Docker API",
            3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL", 5900 to "VNC",
            6379 to "Redis", 8080 to "HTTP alternate", 8443 to "HTTPS alternate",
            9200 to "Elasticsearch", 27017 to "MongoDB"
        )
        val openPorts = coroutineScope {
            addresses.flatMap { address ->
                ports.map { (port, service) ->
                    async(Dispatchers.IO) {
                        val isOpen = runCatching {
                            java.net.Socket().use { socket ->
                                socket.connect(java.net.InetSocketAddress(address, port), 650)
                                true
                            }
                        }.getOrDefault(false)
                        if (isOpen) ReconRecord(
                            kind = "OPEN_TCP_PORT",
                            value = "${address.hostAddress}:$port ($service)",
                            source = "Limited TCP connect check",
                            sourceUrl = "https://$domain/",
                            confidence = 72,
                            detail = "نجح اتصال TCP فقط؛ هذا لا يثبت وجود ثغرة أو سوء إعداد. قد يكون العنوان بنية استضافة مشتركة."
                        ) else null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val exposureResults = coroutineScope {
            listOf(
                "/.env" to Regex("(?im)^(APP_|DB_|DATABASE_URL=|AWS_|SECRET_KEY=)"),
                "/.git/HEAD" to Regex("(?i)^ref:\\s*refs/"),
                "/.DS_Store" to Regex("(?s)^Bud1"),
                "/server-status" to Regex("(?i)Apache Server Status"),
                "/actuator/env" to Regex("(?i)propertySources|activeProfiles"),
                "/phpinfo.php" to Regex("(?i)PHP Version"),
                "/config.php.bak" to Regex("(?i)<\\?php|DB_HOST|DB_NAME")
            ).map { (path, signature) ->
                async(Dispatchers.IO) {
                    runCatching { probeExposurePath(domain, path, signature) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }

        val portSummary = ReconRecord(
            kind = "PORT_SCAN_SUMMARY",
            value = "تم اختبار ${ports.size} منفذاً شائعاً على ${addresses.size} عنوان IP كحد أقصى",
            source = "Limited TCP connect check",
            sourceUrl = "https://$domain/",
            confidence = if (openPorts.isEmpty()) 40 else 65,
            detail = if (openPorts.isEmpty()) {
                "لم يُؤكد أي منفذ مفتوح من القائمة المحدودة. قد تكون المنافذ مغلقة أو مفلترة أو أن المهلة قصيرة؛ هذه ليست نتيجة مسح شامل."
            } else {
                "تم تأكيد ${openPorts.size} اتصال TCP مفتوحاً من قائمة محدودة. راجع الخدمة يدوياً وبموافقة مالك الأصل."
            }
        )
        val exposureSummary = if (exposureResults.isEmpty()) listOf(
            ReconRecord(
                kind = "EXPOSURE_CHECK_SUMMARY",
                value = "لم تُكتشف بصمات مطابقة للمسارات الحساسة المختبرة",
                source = "Bounded public exposure checks",
                sourceUrl = "https://$domain/",
                confidence = 45,
                detail = "اختُبرت مجموعة صغيرة من المؤشرات الشائعة فقط؛ لا يثبت هذا عدم وجود ملفات أخرى أو أبواب خلفية."
            )
        ) else exposureResults
        listOf(portSummary) + openPorts + exposureSummary
    }

    private fun probeExposurePath(domain: String, path: String, signature: Regex): ReconRecord? {
        val endpoint = "https://$domain$path"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2_500
            readTimeout = 2_500
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Range", "bytes=0-4095")
            setRequestProperty("Accept", "text/plain,text/html,application/json,*/*")
            setRequestProperty("User-Agent", "OSINTWorkbench/0.4 (authorized low-impact exposure check)")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) return null
            val body = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1_024)
                var total = 0
                while (total < 4_096) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 4_096 - total))
                    if (count <= 0) break
                    out.write(buffer, 0, count)
                    total += count
                }
                out.toString(StandardCharsets.UTF_8.name())
            }
            if (!signature.containsMatchIn(body)) return null
            return ReconRecord(
                kind = "POTENTIAL_SENSITIVE_EXPOSURE",
                value = "$path returned HTTP $status with a matching content signature",
                source = "Bounded public exposure checks",
                sourceUrl = endpoint,
                confidence = 78,
                detail = "تم رصد توقيع محتوى قد يشير إلى ملف/واجهة حساسة مكشوفة. لم يُحفظ محتوى الاستجابة؛ تحقق يدوياً قبل تصنيفها كثغرة."
            )
        } finally {
            connection.disconnect()
        }
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

            fun htmlAttribute(tag: String, attribute: String): String? =
                Regex("""(?is)\b$attribute\s*=\s*["']([^"']+)["']""")
                    .find(tag)?.groupValues?.getOrNull(1)?.trim()

            val imageMetaTags = Regex("(?is)<meta\\b[^>]*>").findAll(html).map { it.value }
            val socialImage = imageMetaTags.firstNotNullOfOrNull { tag ->
                val key = (htmlAttribute(tag, "property") ?: htmlAttribute(tag, "name")).orEmpty()
                if (!key.equals("og:image", true) && !key.equals("twitter:image", true)) return@firstNotNullOfOrNull null
                htmlAttribute(tag, "content")
            }?.let { image -> runCatching { URL(URL(finalUrl), image).toString() }.getOrNull() }
                ?.takeIf { it.startsWith("https://") && it.length <= 2_048 }

            if (!socialImage.isNullOrBlank()) {
                result += ReconRecord(
                    "WEB_PREVIEW_IMAGE",
                    socialImage,
                    "Open Graph / social preview metadata",
                    finalUrl,
                    68,
                    "صورة معاينة عرّفتها الصفحة في HTML العام؛ قد تكون الصورة قديمة أو تتغير لاحقاً.",
                    previewImageUrl = socialImage
                )
            }

            val seenImages = linkedSetOf<String>()
            val imageTags = Regex("(?is)<img\\b[^>]*>").findAll(html).map { it.value }
            for (tag in imageTags) {
                if (seenImages.size >= 4) break
                val raw = htmlAttribute(tag, "src") ?: htmlAttribute(tag, "data-src") ?: continue
                if (raw.startsWith("data:", true) || raw.startsWith("javascript:", true)) continue
                val image = runCatching { URL(URL(finalUrl), raw).toString() }.getOrNull()
                    ?.takeIf { it.startsWith("https://") && it.length <= 2_048 } ?: continue
                if (!seenImages.add(image)) continue
                val width = htmlAttribute(tag, "width")?.toIntOrNull()
                val height = htmlAttribute(tag, "height")?.toIntOrNull()
                if ((width != null && width < 48) || (height != null && height < 48)) continue
                val alt = htmlAttribute(tag, "alt").orEmpty().take(140)
                result += ReconRecord(
                    "WEB_IMAGE",
                    alt.ifBlank { image.substringAfterLast('/').take(120).ifBlank { "Image resource" } },
                    "HTML image resource",
                    finalUrl,
                    58,
                    "رابط صورة مرصود في HTML العام؛ قد تكون الصورة محمّلة ديناميكياً أو غير متاحة لاحقاً.",
                    previewImageUrl = image
                )
            }

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
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return getTextOnce(endpoint, timeoutMs, accept)
            } catch (error: Exception) {
                lastError = error
                val message = error.message.orEmpty()
                val retryable = listOf("HTTP 429", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504", "timed out", "timeout")
                    .any { message.contains(it, ignoreCase = true) }
                if (!retryable || attempt == 2) throw error
                Thread.sleep(if (attempt == 0) 400L else 900L)
            }
        }
        throw lastError ?: IllegalStateException("Request failed")
    }

    private fun getTextOnce(endpoint: String, timeoutMs: Int, accept: String): String {
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

    private data class DnsQuery(val label: String, val number: Int)
}
