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

data class ReconRecord(
    val kind: String,
    val value: String,
    val source: String,
    val sourceUrl: String,
    val confidence: Int
)

data class ScanReport(
    val target: String,
    val records: List<ReconRecord>,
    val warnings: List<String>,
    val startedAt: Long,
    val finishedAt: Long
) {
    val elapsedMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0L)
}

/**
 * Passive-first reconnaissance:
 * - DNS records are requested from Google Public DNS over HTTPS.
 * - Optional certificate names are read from the public crt.sh search endpoint.
 * This module does not perform port scanning, login attempts, brute forcing, or exploitation.
 */
class ReconEngine {
    suspend fun scanDomain(rawDomain: String, includeCertificateTransparency: Boolean): ScanReport {
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
                        .onFailure { warnings += "${query.label}: ${it.message ?: "تعذر جلب السجل"}" }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }

        val records = dnsResults.toMutableList()
        if (includeCertificateTransparency) {
            try {
                records += queryCertificateTransparency(domain)
            } catch (error: Exception) {
                warnings += "Certificate Transparency: ${error.message ?: "لم تكتمل عملية البحث"}"
            }
        }

        return ScanReport(
            target = domain,
            records = records.distinctBy { "${it.kind}|${it.value.lowercase()}" },
            warnings = warnings.distinct(),
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis()
        )
    }

    private suspend fun queryDns(domain: String, query: DnsQuery): List<ReconRecord> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(domain, StandardCharsets.UTF_8.name())
        val endpoint = "https://dns.google/resolve?name=$encoded&type=${query.number}"
        val json = getText(endpoint, timeoutMs = 8_000)
        val root = JSONObject(json)
        val answers = root.optJSONArray("Answer") ?: JSONArray()
        buildList {
            for (index in 0 until answers.length()) {
                val answer = answers.optJSONObject(index) ?: continue
                val value = answer.optString("data").trim()
                if (value.isNotBlank()) {
                    add(
                        ReconRecord(
                            kind = "DNS_${query.label}",
                            value = value,
                            source = "Google Public DNS",
                            sourceUrl = endpoint,
                            confidence = 92
                        )
                    )
                }
            }
        }
    }

    private suspend fun queryCertificateTransparency(domain: String): List<ReconRecord> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("%.$domain", StandardCharsets.UTF_8.name())
        val endpoint = "https://crt.sh/?q=$query&output=json"
        val body = getText(endpoint, timeoutMs = 14_000)
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
                    ) {
                        names += host
                    }
                }
        }

        names.map { host ->
            ReconRecord(
                kind = if (host == domain) "CERTIFICATE_NAME" else "SUBDOMAIN",
                value = host,
                source = "Certificate Transparency (crt.sh)",
                sourceUrl = endpoint,
                confidence = 82
            )
        }
    }

    private fun getText(endpoint: String, timeoutMs: Int): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OSINT-Workbench/0.1 (passive research tool)")
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
            return body
        } finally {
            connection.disconnect()
        }
    }

    private data class DnsQuery(val label: String, val number: Int)
}
