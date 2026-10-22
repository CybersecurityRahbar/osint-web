package com.cyberphantom.osintworkbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.LocalStore
import coil.compose.AsyncImage
import com.cyberphantom.osintworkbench.domain.AdvancedPublicSearchEngine
import com.cyberphantom.osintworkbench.domain.MultiSourceNewsEngine
import com.cyberphantom.osintworkbench.domain.IdentifierKind
import com.cyberphantom.osintworkbench.domain.NewsArticle
import com.cyberphantom.osintworkbench.domain.NewsSearchResult
import com.cyberphantom.osintworkbench.domain.PublicProfileResult
import com.cyberphantom.osintworkbench.domain.PublicResearchEngine
import com.cyberphantom.osintworkbench.domain.ReconRecord
import com.cyberphantom.osintworkbench.domain.ScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ResearchMode { IDENTIFIERS, NEWS, PRIVATE_SOURCES }

@Composable
fun PublicResearchScreen(store: LocalStore, onSaved: () -> Unit) {
    val engine = remember { PublicResearchEngine() }
    val automatedSearch = remember { AdvancedPublicSearchEngine() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var mode by rememberSaveable { mutableStateOf(ResearchMode.IDENTIFIERS) }
    var kind by rememberSaveable { mutableStateOf(IdentifierKind.NAME) }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf(emptyList<com.cyberphantom.osintworkbench.domain.PublicSearchLink>()) }
    var profiles by remember { mutableStateOf(emptyList<PublicProfileResult>()) }
    var news by remember { mutableStateOf<NewsSearchResult?>(null) }
    var searchReport by remember { mutableStateOf<ScanReport?>(null) }
    var phoneMetadata by remember { mutableStateOf<String?>(null) }
    var sourceWarnings by remember { mutableStateOf(emptyList<String>()) }
    var includeImages by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        ResearchScreenHeading(
            "محرك البحث المفتوح",
            "معرّفات عامة وأخبار ومصادر خاصة عبر Telegram",
            Icons.Filled.PersonSearch
        )

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(WorkbenchColors.SurfaceElevated).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = mode == ResearchMode.IDENTIFIERS,
                label = "المعرّفات",
                onClick = { mode = ResearchMode.IDENTIFIERS; error = null; news = null; phoneMetadata = null; sourceWarnings = emptyList() }
            )
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = mode == ResearchMode.NEWS,
                label = "الأخبار",
                onClick = { mode = ResearchMode.NEWS; error = null; profiles = emptyList(); links = emptyList(); phoneMetadata = null; sourceWarnings = emptyList() }
            )
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = mode == ResearchMode.PRIVATE_SOURCES,
                label = "المصادر الخاصة",
                onClick = { mode = ResearchMode.PRIVATE_SOURCES; error = null; profiles = emptyList(); links = emptyList(); news = null; phoneMetadata = null; sourceWarnings = emptyList() }
            )
        }

        if (mode != ResearchMode.PRIVATE_SOURCES) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(WorkbenchColors.SurfaceElevated).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = includeImages, onCheckedChange = { includeImages = it })
            Column(Modifier.weight(1f)) {
                Text("إظهار الصور في النتائج", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "تشغيل/إيقاف الصور المصغرة في بحث الويب والأخبار؛ يحاول Bing Images وSearXNG وCommons.",
                    fontSize = 10.sp, color = WorkbenchColors.TextSecondary, lineHeight = 15.sp
                )
            }
        }

        }
        if (mode != ResearchMode.PRIVATE_SOURCES) {
        Surface(
            color = WorkbenchColors.Surface,
            shape = RoundedCornerShape(21.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mode == ResearchMode.IDENTIFIERS) {
                    Text("نوع المعرّف", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        IdentifierKind.entries.forEach { item ->
                            FilterChip(
                                selected = kind == item,
                                onClick = { kind = item; error = null; profiles = emptyList(); links = emptyList() },
                                label = { Text(item.label, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(inputLabel(kind)) },
                        placeholder = { Text(inputPlaceholder(kind)) },
                        colors = researchFieldColors()
                    )
                    Text(
                        when (kind) {
                            IdentifierKind.NAME -> "بحث تلقائي متعدد المحركات والواجهات العامة، ثم روابط بحث إضافية عند الحاجة. تختلف النتائج بحسب ما تسمح به المصادر."
                            IdentifierKind.USERNAME -> "تظهر نتائج GitHub العامة داخل التطبيق، مع روابط بحث مفهرس لمنصات أخرى. الروابط لا تثبت وحدها ملكية الحساب."
                            IdentifierKind.PHONE -> "عند الضغط على البحث، يحاول التطبيق جلب نتائج عامة مفهرسة من عدة محركات، ويعرض مصدر كل نتيجة وصوراً اختيارية. قد تُحجب محركات أو تغيّر صفحاتها؛ لا يكشف البحث مالك الرقم أو نشاطه يقيناً."
                            IdentifierKind.EMAIL -> "البحث المباشر يجلب الصفحات العامة المفهرسة تلقائياً. تتم مشاركة عبارة البحث مع مزود البحث العام؛ لا نبحث في قواعد كلمات المرور أو التسريبات الخاصة."
                        },
                        color = WorkbenchColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Text("ابحث عن أخبار وتقارير منشورة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("كلمات البحث أو العبارة") },
                        placeholder = { Text("مثال: cybersecurity Yemen") },
                        colors = researchFieldColors()
                    )
                    Text(
                        "يجمع البحث بين GDELT DOC API ونسخ Google News RSS العربية والإنجليزية وBing News وSearXNG، مع محاولة بديلة لناشرين معروفين إذا كانت النتائج الأساسية فارغة. يُعرض اسم المصدر وتُذكر أخطاء المصادر عند توفرها.",
                        color = WorkbenchColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }

                if (error != null) {
                    Text(error.orEmpty(), color = WorkbenchColors.Red, fontSize = 12.sp, lineHeight = 18.sp)
                }

                Button(
                    onClick = {
                        if (query.isBlank()) {
                            error = "أدخل قيمة البحث أولاً."
                        } else {
                            scope.launch {
                                loading = true
                                error = null
                                profiles = emptyList()
                                links = emptyList()
                                news = null
                                phoneMetadata = null
                                sourceWarnings = emptyList()
                                searchReport = null
                                val startedAt = System.currentTimeMillis()
                                try {
                                    if (mode == ResearchMode.NEWS) {
                                        val result = MultiSourceNewsEngine().search(query, includeImages = includeImages)
                                        news = result
                                        val records = result.articles.map { article ->
                                            ReconRecord(
                                                kind = "NEWS_ARTICLE",
                                                value = article.title,
                                                source = article.publisherName.ifBlank { article.domain.ifBlank { article.provider } },
                                                sourceUrl = article.url,
                                                confidence = 78,
                                                detail = "${article.provider} · ${article.publishedAt} · ${article.sourceCountry} · ${article.language}".trim(),
                                                previewImageUrl = article.imageUrl
                                            )
                                        }
                                        val reportTarget = "news: ${query.trim()}"
                                        val report = ScanReport(
                                            target = reportTarget,
                                            records = records,
                                            warnings = listOfNotNull(result.warning),
                                            startedAt = startedAt,
                                            finishedAt = System.currentTimeMillis()
                                        )
                                        searchReport = report
                                        withContext(Dispatchers.IO) {
                                            val queryId = store.upsertEntity(reportTarget, "NEWS_QUERY", "Multi-source news search", 70)
                                            result.articles.forEach { article ->
                                                val publisher = article.publisherName.ifBlank { article.domain.ifBlank { article.provider } }
                                                val articleId = store.upsertEntity("${article.title} · $publisher", "NEWS_ARTICLE", publisher, 78)
                                                store.addEdge(articleId, queryId, "MATCHED_NEWS", article.provider, 78)
                                                store.addFinding(
                                                    queryId,
                                                    article.title,
                                                    "${publisher} · ${article.publishedAt} · ${article.url}",
                                                    article.provider,
                                                    "info"
                                                )
                                            }
                                            store.addScan(
                                                target = report.target,
                                                status = if (report.records.isEmpty()) "no_results" else "completed",
                                                startedAt = report.startedAt,
                                                summary = "${report.records.size} articles · ${result.articles.map { it.publisherName }.distinct().size} publishers",
                                                records = report.records.size,
                                                payloadJson = report.toJson()
                                            )
                                        }
                                        onSaved()
                                    } else {
                                        links = engine.buildSearchLinks(query, kind)
                                        val webResponse = withContext(Dispatchers.IO) {
                                            automatedSearch.search(query, kind, maxResults = 30, includeImages = includeImages)
                                        }
                                        phoneMetadata = if (kind == IdentifierKind.PHONE) automatedSearch.phoneMetadata(query) else null
                                        sourceWarnings = webResponse.warnings
                                        val apiResults = withContext(Dispatchers.IO) {
                                            when (kind) {
                                                IdentifierKind.NAME -> runCatching { engine.searchGitHubUsers(query) }.getOrDefault(emptyList()) +
                                                    runCatching { engine.searchWikidata(query) }.getOrDefault(emptyList())
                                                IdentifierKind.USERNAME -> runCatching { engine.searchGitHubUsers(query) }.getOrDefault(emptyList())
                                                IdentifierKind.PHONE, IdentifierKind.EMAIL -> emptyList()
                                            }
                                        }
                                        val actualResults = (webResponse.results + apiResults)
                                            .distinctBy { it.url.trimEnd('/').lowercase() }
                                            .take(40)
                                        profiles = actualResults
                                        val warningList = buildList {
                                            addAll(webResponse.warnings)
                                            if (actualResults.isEmpty()) add(
                                                "لم تُعد المصادر المباشرة نتائج. الروابط البديلة أدناه ما زالت متاحة، والنتيجة الصفرية لا تثبت عدم وجود معلومات."
                                            )
                                        }.distinct()
                                        val reportTarget = "${kind.label}: ${query.trim()}"
                                        val records = actualResults.map { profile ->
                                            ReconRecord(
                                                kind = "PUBLIC_PROFILE",
                                                value = "${profile.title} — ${profile.description}",
                                                source = profile.provider,
                                                sourceUrl = profile.url,
                                                confidence = profile.confidence,
                                                detail = "${profile.description} · ${profile.provider}"
                                            )
                                        }
                                        val report = ScanReport(
                                            target = reportTarget,
                                            records = records,
                                            warnings = warningList,
                                            startedAt = startedAt,
                                            finishedAt = System.currentTimeMillis()
                                        )
                                        searchReport = report
                                        withContext(Dispatchers.IO) {
                                            val queryId = store.upsertEntity(reportTarget, "IDENTIFIER_QUERY", "User supplied query", 100)
                                            actualResults.forEach { profile ->
                                                val profileId = store.upsertEntity("${profile.title} · ${profile.provider}", "PUBLIC_PROFILE", profile.provider, profile.confidence)
                                                store.addEdge(profileId, queryId, "PUBLIC_PROFILE_RESULT_FOR", profile.provider, profile.confidence)
                                                store.addFinding(
                                                    queryId,
                                                    profile.title,
                                                    "${profile.description}\n${profile.url}",
                                                    profile.provider,
                                                    "info"
                                                )
                                            }
                                            store.addScan(
                                                target = report.target,
                                                status = if (report.records.isEmpty()) "no_results" else "completed",
                                                startedAt = report.startedAt,
                                                summary = "${report.records.size} public results · ${report.warnings.size} notes",
                                                records = report.records.size,
                                                payloadJson = report.toJson()
                                            )
                                        }
                                        onSaved()
                                    }
                                } catch (exception: Exception) {
                                    error = exception.message ?: "تعذر إكمال البحث. قد يكون المصدر محجوباً أو يحدّ الطلبات."
                                    if (mode == ResearchMode.IDENTIFIERS) {
                                        links = runCatching { engine.buildSearchLinks(query, kind) }.getOrDefault(emptyList())
                                    }
                                    val failureReport = ScanReport(
                                        target = if (mode == ResearchMode.NEWS) "news: ${query.trim()}" else "${kind.label}: ${query.trim()}",
                                        records = emptyList(),
                                        warnings = listOf(error ?: "فشل البحث"),
                                        startedAt = startedAt,
                                        finishedAt = System.currentTimeMillis()
                                    )
                                    searchReport = failureReport
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    enabled = !loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF06231F))
                        Spacer(Modifier.width(9.dp))
                        Text("يجري البحث...")
                    } else {
                        androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (mode == ResearchMode.NEWS) "بحث في الأخبار" else "تحليل وبناء مصادر البحث", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        }
        if (mode != ResearchMode.PRIVATE_SOURCES) searchReport?.let { ReportQuickActions(it) }

        if (mode == ResearchMode.IDENTIFIERS) {
            if (phoneMetadata != null) {
                Surface(color = Color(0xFF10282D), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24534F))) {
                    Text(phoneMetadata.orEmpty(), modifier = Modifier.padding(12.dp), color = WorkbenchColors.TextSecondary, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
            if (sourceWarnings.isNotEmpty()) {
                sourceWarnings.forEach { warning -> Text(warning, color = WorkbenchColors.Amber, fontSize = 11.sp, lineHeight = 17.sp) }
            }
            if (profiles.isNotEmpty()) {
                ResearchSectionHeader("نتائج مباشرة من الويب", "${profiles.size} نتيجة مفهرسة من محركات ويب متعددة")
                profiles.forEach { profile ->
                    PublicProfileCard(profile = profile, showImage = includeImages, onOpen = { uriHandler.openUri(profile.url) })
                }
            }
            if (links.isNotEmpty()) {
                ResearchSectionHeader("مصادر بحث إضافية", "تفتح النتائج في المتصفح عند اختيارك")
                Surface(
                    color = Color(0xFF282419),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF51442A))
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                        androidx.compose.material3.Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = WorkbenchColors.Amber, modifier = Modifier.size(18.dp))
                        Text(
                            "فتح أي رابط يرسل عبارة البحث إلى مزود البحث المحدد وفق سياساته. لا تضغط إلا على المصادر التي توافق على مشاركتها معها.",
                            color = Color(0xFFE8D6B3),
                            fontSize = 10.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                links.forEach { link ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(link.url) },
                        color = WorkbenchColors.Surface,
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(WorkbenchColors.Blue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(Icons.Filled.Public, contentDescription = null, tint = WorkbenchColors.Blue, modifier = Modifier.size(19.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(link.provider, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(link.description, fontSize = 10.sp, color = WorkbenchColors.TextSecondary, lineHeight = 15.sp)
                            }
                            androidx.compose.material3.Icon(Icons.Filled.OpenInNew, contentDescription = "فتح", tint = WorkbenchColors.Teal, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else if (mode == ResearchMode.NEWS) {
            news?.let { result ->
                ResearchSectionHeader("نتائج الأخبار", "${result.articles.size} مقالة من GDELT وموجّهات RSS لناشرين متعددين")
                if (result.warning != null) {
                    Text(result.warning, color = WorkbenchColors.Amber, fontSize = 11.sp, lineHeight = 18.sp)
                }
                result.articles.forEach { article ->
                    NewsArticleCard(article = article, showImage = includeImages, onOpen = { uriHandler.openUri(article.url) })
                }
            }
        } else {
            PrivateSourcesSearchPanel()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModeButton(modifier: Modifier, selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF1D4145) else Color.Transparent
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) WorkbenchColors.Teal else WorkbenchColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PublicProfileCard(profile: PublicProfileResult, showImage: Boolean = true, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = WorkbenchColors.Surface,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showImage && !profile.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.imageUrl,
                    contentDescription = "صورة من ${profile.provider}",
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(Modifier.size(36.dp).clip(CircleShape).background(WorkbenchColors.Violet.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.PersonSearch, contentDescription = null, tint = WorkbenchColors.Violet, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profile.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(profile.description, fontSize = 10.sp, color = WorkbenchColors.TextSecondary, lineHeight = 15.sp)
                Text(profile.provider, fontSize = 9.sp, color = WorkbenchColors.Violet)
                if (profile.relevanceScore > 0) {
                    Text(
                        "ملاءمة نصية تقريبية: ${profile.relevanceScore}/100",
                        fontSize = 9.sp,
                        color = WorkbenchColors.TextSecondary
                    )
                }
            }
            androidx.compose.material3.Icon(Icons.Filled.OpenInNew, contentDescription = "فتح المصدر", tint = WorkbenchColors.Teal)
        }
    }
}

@Composable
private fun NewsArticleCard(article: NewsArticle, showImage: Boolean = true, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = WorkbenchColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.Article, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(17.dp))
                Text(article.publisherName.ifBlank { article.domain.ifBlank { "مصدر إخباري" } }, fontSize = 10.sp, color = WorkbenchColors.Teal, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(article.publishedAt.ifBlank { "وقت النشر غير متاح" }, fontSize = 9.sp, color = WorkbenchColors.TextSecondary)
            }
            Text(article.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 19.sp)
            if (showImage && !article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = "صورة خبر من ${article.publisherName}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Text(article.provider, fontSize = 9.sp, color = WorkbenchColors.TextSecondary)
            if (article.sourceCountry.isNotBlank() || article.language.isNotBlank()) {
                Text(listOf(article.sourceCountry, article.language).filter(String::isNotBlank).joinToString(" · "), fontSize = 10.sp, color = WorkbenchColors.TextSecondary)
            }
        }
    }
}

private fun inputLabel(kind: IdentifierKind): String = when (kind) {
    IdentifierKind.NAME -> "الاسم أو العبارة"
    IdentifierKind.USERNAME -> "اسم المستخدم"
    IdentifierKind.PHONE -> "رقم الهاتف بصيغته المعروفة"
    IdentifierKind.EMAIL -> "عنوان البريد الإلكتروني"
}

private fun inputPlaceholder(kind: IdentifierKind): String = when (kind) {
    IdentifierKind.NAME -> "مثال: Alan Turing"
    IdentifierKind.USERNAME -> "مثال: octocat"
    IdentifierKind.PHONE -> "أدخل رمز الدولة إن كان معروفاً"
    IdentifierKind.EMAIL -> "name@example.com"
}

@Composable
private fun researchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WorkbenchColors.Teal,
    unfocusedBorderColor = WorkbenchColors.Border,
    focusedLabelColor = WorkbenchColors.Teal,
    unfocusedLabelColor = WorkbenchColors.TextSecondary,
    focusedTextColor = WorkbenchColors.TextPrimary,
    unfocusedTextColor = WorkbenchColors.TextPrimary,
    cursorColor = WorkbenchColors.Teal,
    focusedContainerColor = WorkbenchColors.Background.copy(alpha = 0.40f),
    unfocusedContainerColor = WorkbenchColors.Background.copy(alpha = 0.40f)
)


@Composable
private fun ResearchScreenHeading(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF193E47), Color(0xFF29294D)))
            ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(25.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = WorkbenchColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResearchSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 10.sp, color = WorkbenchColors.TextSecondary)
    }
}

