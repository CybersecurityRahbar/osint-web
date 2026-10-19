package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class IdentifierKind(val label: String) {
    NAME("اسم"),
    USERNAME("اسم مستخدم"),
    PHONE("رقم هاتف"),
    EMAIL("بريد إلكتروني")
}

data class PublicSearchLink(
    val provider: String,
    val description: String,
    val url: String
)

data class PublicProfileResult(
    val provider: String,
    val title: String,
    val description: String,
    val url: String,
    val confidence: Int
)

data class NewsArticle(
    val title: String,
    val url: String,
    val domain: String,
    val publishedAt: String,
    val language: String = "",
    val sourceCountry: String = ""
)

data class NewsSearchResult(
    val query: String,
    val articles: List<NewsArticle>,
    val warning: String? = null
)

/**
 * Uses open public APIs where possible. Search engine links are opened by the user deliberately;
 * the app does not scrape logged-in social platforms or send identifiers to every provider silently.
 */
class PublicResearchEngine {
    fun buildSearchLinks(rawQuery: String, kind: IdentifierKind): List<PublicSearchLink> {
        val query = rawQuery.trim()
        require(query.isNotBlank()) { "أدخل قيمة للبحث." }
        require(query.length <= 240) { "العبارة طويلة جداً." }
        when (kind) {
            IdentifierKind.USERNAME -> require(query.removePrefix("@").matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
                "اسم المستخدم يجب أن يكون معرّفاً بسيطاً دون مسافات."
            }
            IdentifierKind.PHONE -> require(query.count(Char::isDigit) in 7..15) {
                "أدخل رقماً يحتوي من 7 إلى 15 رقماً؛ لا يستطيع التطبيق تخمين رمز الدولة."
            }
            IdentifierKind.EMAIL -> require(query.contains("@") && query.substringAfter("@").contains(".")) {
                "أدخل عنوان بريد إلكتروني كاملاً."
            }
            IdentifierKind.NAME -> Unit
        }

        val quoted = when (kind) {
            IdentifierKind.USERNAME -> query.removePrefix("@").trim()
            IdentifierKind.PHONE -> query.replace(Regex("[^+0-9() .-]"), "").trim()
            else -> "\"$query\""
        }
        val broadQuery = when (kind) {
            IdentifierKind.USERNAME -> "\"$quoted\""
            IdentifierKind.PHONE -> "\"$quoted\" OR \"${quoted.replace(Regex("\\s+"), "")}\""
            IdentifierKind.EMAIL -> "\"$quoted\""
            IdentifierKind.NAME -> "\"$query\""
        }

        val providers = mutableListOf(
            PublicSearchLink("Google", "بحث ويب عام", "https://www.google.com/search?q=${enc(broadQuery)}"),
            PublicSearchLink("Bing", "محرك بحث بديل", "https://www.bing.com/search?q=${enc(broadQuery)}"),
            PublicSearchLink("DuckDuckGo", "بحث ويب مستقل", "https://duckduckgo.com/?q=${enc(broadQuery)}")
        )

        when (kind) {
            IdentifierKind.NAME -> {
                providers += PublicSearchLink("Wikidata", "كيانات مرجعية عامة", "https://www.wikidata.org/w/index.php?search=${enc(query)}")
                providers += PublicSearchLink("Wikipedia", "مقالات موسوعية", "https://en.wikipedia.org/w/index.php?search=${enc(query)}")
                providers += PublicSearchLink("LinkedIn", "نتائج عامة مفهرسة", "https://www.google.com/search?q=${enc("site:linkedin.com/in \"$query\"")}")
                providers += PublicSearchLink("Social platforms", "نتائج عامة من عدة منصات", "https://www.google.com/search?q=${enc("(\"$query\") (site:instagram.com OR site:facebook.com OR site:tiktok.com OR site:x.com OR site:youtube.com)")}")
            }
            IdentifierKind.USERNAME -> {
                val username = quoted.removePrefix("@")
                providers += PublicSearchLink("GitHub", "حسابات ومستودعات عامة", "https://github.com/search?q=${enc(username)}&type=users")
                providers += PublicSearchLink("Reddit", "نتائج عامة", "https://www.reddit.com/search/?q=${enc(username)}")
                providers += PublicSearchLink("Instagram", "بحث مفهرس عام؛ ليس تأكيداً لوجود الحساب", "https://www.google.com/search?q=${enc("site:instagram.com \"$username\"")}")
                providers += PublicSearchLink("TikTok / X / YouTube", "بحث مفهرس عام عبر المنصات", "https://www.google.com/search?q=${enc("(\"$username\") (site:tiktok.com OR site:x.com OR site:youtube.com)")}")
            }
            IdentifierKind.PHONE -> {
                providers += PublicSearchLink("بحث الرقم بصيغته", "النتائج العامة فقط؛ لا يكشف مالك الرقم يقيناً", "https://www.google.com/search?q=${enc("\"$quoted\"")}")
                providers += PublicSearchLink("بحث بصيغة الأرقام", "قد يجد نسخاً منشورة بدون فواصل", "https://www.bing.com/search?q=${enc("\"${quoted.replace(Regex("\\D"), "")}\"")}")
            }
            IdentifierKind.EMAIL -> {
                providers += PublicSearchLink("بحث البريد في الويب", "النتائج المنشورة علناً فقط", "https://www.google.com/search?q=${enc("\"$query\"")}")
                providers += PublicSearchLink("GitHub", "ذكر البريد في صفحات عامة", "https://github.com/search?q=${enc("\"$query\"")}&type=code")
            }
        }
        return providers.distinctBy { it.url }
    }

    suspend fun searchGitHubUsers(query: String, limit: Int = 12): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val safeQuery = query.trim()
        require(safeQuery.isNotBlank()) { "أدخل اسماً أو اسم مستخدم للبحث." }
        val endpoint = "https://api.github.com/search/users?q=${enc(safeQuery)}&per_page=${limit.coerceIn(1, 20)}"
        val root = JSONObject(getText(endpoint, 10_000, "application/vnd.github+json"))
        val items = root.optJSONArray("items") ?: org.json.JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val login = item.optString("login").takeIf(String::isNotBlank) ?: continue
                val url = item.optString("html_url").takeIf(String::isNotBlank) ?: "https://github.com/$login"
                add(
                    PublicProfileResult(
                        provider = "GitHub public API",
                        title = login,
                        description = "حساب عام · ${item.optString("type", "User")} · نتيجة بحث وليست إثباتاً لهوية الشخص",
                        url = url,
                        confidence = 85
                    )
                )
            }
        }
    }

    suspend fun searchWikidata(query: String, limit: Int = 10): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val endpoint = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${enc(query.trim())}&language=en&uselang=en&format=json&limit=${limit.coerceIn(1, 15)}"
        val root = JSONObject(getText(endpoint, 10_000, "application/json"))
        val items = root.optJSONArray("search") ?: org.json.JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val title = item.optString("label").takeIf(String::isNotBlank) ?: continue
                val id = item.optString("id")
                val url = item.optString("concepturi").ifBlank { "https://www.wikidata.org/wiki/$id" }
                add(
                    PublicProfileResult(
                        provider = "Wikidata",
                        title = title,
                        description = item.optString("description").ifBlank { "كيان في قاعدة معرفة عامة" },
                        url = url,
                        confidence = 70
                    )
                )
            }
        }
    }

    suspend fun searchNews(query: String, maxRecords: Int = 25): NewsSearchResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        require(clean.isNotBlank()) { "أدخل كلمات البحث عن الأخبار." }
        require(clean.length <= 180) { "عبارة البحث طويلة جداً." }
        val endpoint = "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(clean)}&mode=artlist&format=json&sort=datedesc&maxrecords=${maxRecords.coerceIn(1, 50)}"
        val raw = try {
            getText(endpoint, 14_000, "application/json")
        } catch (error: Exception) {
            return@withContext NewsSearchResult(
                clean,
                emptyList(),
                "تعذر الوصول إلى مصدر الأخبار. قد تكون الشبكة أو المنطقة تحجب المصدر؛ جرّب الاتصال الآخر أو VPN. التفاصيل: ${error.message ?: "خطأ شبكة"}"
            )
        }

        val root = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return@withContext NewsSearchResult(clean, emptyList(), "أعاد مصدر الأخبار رداً غير متوقع؛ لا توجد نتائج موثوقة لعرضها.")
        }
        val items = root.optJSONArray("articles") ?: org.json.JSONArray()
        val articles = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url").takeIf { it.startsWith("https://") } ?: continue
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                add(
                    NewsArticle(
                        title = title,
                        url = url,
                        domain = item.optString("domain"),
                        publishedAt = item.optString("seendate"),
                        language = item.optString("language"),
                        sourceCountry = item.optString("sourcecountry")
                    )
                )
            }
        }.distinctBy { it.url }

        NewsSearchResult(
            clean,
            articles,
            if (articles.isEmpty()) "لم يعرض GDELT نتائج لهذه العبارة في الاستجابة الحالية؛ هذا لا يعني عدم وجود أخبار."
            else null
        )
    }

    private fun getText(endpoint: String, timeoutMs: Int, accept: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "OSINT-Workbench/0.2 (public research app)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status from ${URL(endpoint).host}")
            if (body.isBlank()) throw IllegalStateException("Empty response from ${URL(endpoint).host}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
