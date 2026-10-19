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
import com.cyberphantom.osintworkbench.domain.IdentifierKind
import com.cyberphantom.osintworkbench.domain.NewsArticle
import com.cyberphantom.osintworkbench.domain.NewsSearchResult
import com.cyberphantom.osintworkbench.domain.PublicProfileResult
import com.cyberphantom.osintworkbench.domain.PublicResearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ResearchMode { IDENTIFIERS, NEWS }

@Composable
fun PublicResearchScreen(store: LocalStore, onSaved: () -> Unit) {
    val engine = remember { PublicResearchEngine() }
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        ScreenHeading(
            "محرك البحث المفتوح",
            "أسماء ومعرّفات عامة وأخبار متعددة المصادر",
            Icons.Filled.PersonSearch
        )

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(WorkbenchColors.SurfaceElevated).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = mode == ResearchMode.IDENTIFIERS,
                label = "الأسماء والمعرّفات",
                onClick = { mode = ResearchMode.IDENTIFIERS; error = null; news = null }
            )
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = mode == ResearchMode.NEWS,
                label = "الأخبار",
                onClick = { mode = ResearchMode.NEWS; error = null; profiles = emptyList(); links = emptyList() }
            )
        }

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
                            IdentifierKind.NAME -> "سيبحث التطبيق في قواعد بيانات عامة محددة، ويجهز روابط بحث إضافية في محركات الويب."
                            IdentifierKind.USERNAME -> "تظهر نتائج GitHub العامة داخل التطبيق، مع روابط بحث مفهرس لمنصات أخرى. الروابط لا تثبت وحدها ملكية الحساب."
                            IdentifierKind.PHONE -> "لا نفترض الدولة أو مالك الرقم. ستُفتح نتائج البحث العام فقط، دون الاتصال بالرقم أو استخراج موقعه."
                            IdentifierKind.EMAIL -> "لا نبحث في قواعد بيانات تسريبات أو كلمات مرور. لن يُرسل البريد لأي محرك إلا عند ضغطك على رابط ذلك المحرك."
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
                        "المصدر الحالي هو GDELT DOC API. تغطيته ولغاته وتوفره تختلف، لذلك لن تعني النتائج الصفرية عدم وجود أخبار.",
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
                                try {
                                    if (mode == ResearchMode.NEWS) {
                                        val result = engine.searchNews(query)
                                        news = result
                                        if (result.articles.isNotEmpty()) {
                                            withContext(Dispatchers.IO) {
                                                val queryId = store.upsertEntity(query.trim(), "NEWS_QUERY", "GDELT DOC API", 65)
                                                result.articles.forEach { article ->
                                                    val articleId = store.upsertEntity(article.url, "NEWS_ARTICLE", article.domain.ifBlank { "GDELT" }, 75)
                                                    store.addEdge(articleId, queryId, "MATCHED_NEWS", "GDELT DOC API", 75)
                                                    store.addFinding(queryId, article.title, "${article.publishedAt} · ${article.url}", "GDELT · ${article.domain}", "info")
                                                }
                                            }
                                            onSaved()
                                        }
                                        if (result.warning != null) error = result.warning
                                    } else {
                                        links = engine.buildSearchLinks(query, kind)
                                        val actualResults = withContext(Dispatchers.IO) {
                                            when (kind) {
                                                IdentifierKind.NAME -> engine.searchGitHubUsers(query) + engine.searchWikidata(query)
                                                IdentifierKind.USERNAME -> engine.searchGitHubUsers(query)
                                                IdentifierKind.PHONE, IdentifierKind.EMAIL -> emptyList()
                                            }
                                        }
                                        profiles = actualResults.distinctBy { it.url }
                                        if (actualResults.isEmpty() && kind in listOf(IdentifierKind.NAME, IdentifierKind.USERNAME)) {
                                            error = "لم تُعد واجهات البحث العامة نتائج مباشرة. ما زالت روابط محركات البحث متاحة أدناه؛ عدم ظهور نتيجة لا يعني عدم وجود حساب."
                                        }
                                    }
                                } catch (exception: Exception) {
                                    error = exception.message ?: "تعذر إكمال البحث. قد يكون المصدر محجوباً أو يحدّ الطلبات."
                                    if (mode == ResearchMode.IDENTIFIERS) {
                                        links = runCatching { engine.buildSearchLinks(query, kind) }.getOrDefault(emptyList())
                                    }
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

        if (mode == ResearchMode.IDENTIFIERS) {
            if (profiles.isNotEmpty()) {
                SectionHeader("نتائج APIs العامة", "${profiles.size} نتيجة من GitHub وWikidata")
                profiles.forEach { profile ->
                    PublicProfileCard(profile = profile, onOpen = { uriHandler.openUri(profile.url) })
                }
            }
            if (links.isNotEmpty()) {
                SectionHeader("مصادر بحث إضافية", "تفتح النتائج في المتصفح عند اختيارك")
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
        } else {
            news?.let { result ->
                SectionHeader("نتائج الأخبار", "${result.articles.size} مقالة أعادها GDELT")
                if (result.warning != null) {
                    Text(result.warning, color = WorkbenchColors.Amber, fontSize = 11.sp, lineHeight = 18.sp)
                }
                result.articles.forEach { article ->
                    NewsArticleCard(article = article, onOpen = { uriHandler.openUri(article.url) })
                }
            }
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
private fun PublicProfileCard(profile: PublicProfileResult, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = WorkbenchColors.Surface,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(WorkbenchColors.Violet.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Filled.PersonSearch, contentDescription = null, tint = WorkbenchColors.Violet, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profile.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(profile.description, fontSize = 10.sp, color = WorkbenchColors.TextSecondary, lineHeight = 15.sp)
                Text(profile.provider, fontSize = 9.sp, color = WorkbenchColors.Violet)
            }
            androidx.compose.material3.Icon(Icons.Filled.OpenInNew, contentDescription = "فتح المصدر", tint = WorkbenchColors.Teal)
        }
    }
}

@Composable
private fun NewsArticleCard(article: NewsArticle, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = WorkbenchColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.Article, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(17.dp))
                Text(article.domain.ifBlank { "مصدر إخباري" }, fontSize = 10.sp, color = WorkbenchColors.Teal, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(article.publishedAt.ifBlank { "وقت النشر غير متاح" }, fontSize = 9.sp, color = WorkbenchColors.TextSecondary)
            }
            Text(article.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 19.sp)
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
