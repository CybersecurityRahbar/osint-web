package com.cyberphantom.osintworkbench.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.CaseRow
import com.cyberphantom.osintworkbench.data.EntityRow
import com.cyberphantom.osintworkbench.data.FindingRow
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.RelationRow
import com.cyberphantom.osintworkbench.data.StoreStats
import com.cyberphantom.osintworkbench.domain.ReconEngine
import com.cyberphantom.osintworkbench.domain.ScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DashboardSnapshot(
    val stats: StoreStats,
    val findings: List<FindingRow>,
    val entities: List<EntityRow>,
    val relations: List<RelationRow>,
    val cases: List<CaseRow>
)

private enum class WorkbenchTab(val title: String, val icon: ImageVector) {
    HOME("الرئيسية", Icons.Filled.Home),
    RECON("الاستطلاع", Icons.Filled.Search),
    GRAPH("العلاقات", Icons.Filled.AccountTree),
    CASES("الحالات", Icons.Filled.FolderOpen),
    SETTINGS("الإعدادات", Icons.Filled.Settings)
}

@Composable
fun WorkbenchApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(WorkbenchTab.HOME) }
    var stats by remember { mutableStateOf(StoreStats()) }
    var findings by remember { mutableStateOf(emptyList<FindingRow>()) }
    var entities by remember { mutableStateOf(emptyList<EntityRow>()) }
    var relations by remember { mutableStateOf(emptyList<RelationRow>()) }
    var cases by remember { mutableStateOf(emptyList<CaseRow>()) }

    fun refreshData() {
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                DashboardSnapshot(
                    stats = store.stats(),
                    findings = store.recentFindings(8),
                    entities = store.entities(100),
                    relations = store.relations(100),
                    cases = store.cases(100)
                )
            }
            stats = snapshot.stats
            findings = snapshot.findings
            entities = snapshot.entities
            relations = snapshot.relations
            cases = snapshot.cases
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WorkbenchColors.Background,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0B1525),
                contentColor = WorkbenchColors.TextSecondary,
                tonalElevation = 0.dp
            ) {
                WorkbenchTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(21.dp)
                            )
                        },
                        label = {
                            Text(tab.title, fontSize = 10.sp, maxLines = 1)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WorkbenchColors.Teal,
                            selectedTextColor = WorkbenchColors.Teal,
                            indicatorColor = Color(0xFF193D43),
                            unselectedIconColor = WorkbenchColors.TextSecondary,
                            unselectedTextColor = WorkbenchColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(WorkbenchColors.Background, Color(0xFF0B1424), WorkbenchColors.Background)
                    )
                )
                .padding(padding)
        ) {
            when (selectedTab) {
                WorkbenchTab.HOME -> DashboardScreen(
                    stats = stats,
                    findings = findings,
                    onOpenRecon = { selectedTab = WorkbenchTab.RECON },
                    onOpenGraph = { selectedTab = WorkbenchTab.GRAPH },
                    onOpenCases = { selectedTab = WorkbenchTab.CASES }
                )
                WorkbenchTab.RECON -> ReconScreen(
                    store = store,
                    onSaved = { refreshData() }
                )
                WorkbenchTab.GRAPH -> GraphScreen(entities = entities, relations = relations)
                WorkbenchTab.CASES -> CasesScreen(
                    cases = cases,
                    store = store,
                    onSaved = { refreshData() }
                )
                WorkbenchTab.SETTINGS -> SettingsScreen(stats = stats)
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    stats: StoreStats,
    findings: List<FindingRow>,
    onOpenRecon: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenCases: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandHeader()
        HeroCard(onOpenRecon = onOpenRecon)
        SectionHeader(title = "نظرة عامة", subtitle = "مساحة عملك المحلية")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Public,
                    value = stats.entities.toString(),
                    label = "كيانات محفوظة",
                    accent = WorkbenchColors.Teal
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AccountTree,
                    value = stats.relationships.toString(),
                    label = "علاقات موثقة",
                    accent = WorkbenchColors.Violet
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.History,
                    value = stats.scans.toString(),
                    label = "عمليات جمع",
                    accent = WorkbenchColors.Blue
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.FolderOpen,
                    value = stats.cases.toString(),
                    label = "مساحات عمل",
                    accent = WorkbenchColors.Amber
                )
            }
        }
        SectionHeader(title = "أدوات سريعة", subtitle = "وصول مباشر إلى المحركات")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Dns,
                title = "ذكاء النطاقات",
                subtitle = "DNS والشهادات",
                accent = WorkbenchColors.Teal,
                onClick = onOpenRecon
            )
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.AccountTree,
                title = "شبكة العلاقات",
                subtitle = "الكيانات والأدلة",
                accent = WorkbenchColors.Violet,
                onClick = onOpenGraph
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.FolderOpen,
                title = "الحالات",
                subtitle = "نطاق وملاحظات",
                accent = WorkbenchColors.Blue,
                onClick = onOpenCases
            )
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Shield,
                title = "محلي أولاً",
                subtitle = "بياناتك على الجهاز",
                accent = WorkbenchColors.Amber,
                onClick = onOpenCases
            )
        }
        SectionHeader(title = "آخر الأدلة", subtitle = "أحدث النتائج المحفوظة محلياً")
        if (findings.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Timeline,
                title = "مساحة العمل جاهزة",
                message = "لم تُجمع بيانات بعد. ابدأ باستطلاع سلبي لنطاق تملك صلاحية فحصه."
            )
        } else {
            findings.take(5).forEach { finding -> FindingCard(finding) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = WorkbenchColors.Teal.copy(alpha = 0.22f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF45E1C0), Color(0xFF4C8DFA)))),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = Color(0xFF061824),
                    modifier = Modifier.size(27.dp)
                )
            }
            Column {
                Text("OSINT WORKBENCH", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.1.sp)
                Text("مختبر الاستخبارات الرقمية", color = WorkbenchColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFF102A2B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24524F))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(WorkbenchColors.Teal))
                Text("محلي", color = WorkbenchColors.Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroCard(onOpenRecon: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF15354A), Color(0xFF23244D), Color(0xFF15223A))))
            .border(1.dp, Color(0xFF33506B), RoundedCornerShape(26.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * 0.12f, size.height * 0.18f)
            drawCircle(Color(0xFF49E0C0).copy(alpha = 0.07f), size.minDimension * 0.43f, center)
            drawCircle(Color(0xFF69A9FF).copy(alpha = 0.09f), size.minDimension * 0.29f, center, style = Stroke(2.dp.toPx()))
            drawCircle(Color(0xFFA88BFF).copy(alpha = 0.13f), size.minDimension * 0.17f, center, style = Stroke(1.dp.toPx()))
        }
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(30.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Bolt, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(15.dp))
                    Text("PASSIVE INTELLIGENCE", fontSize = 10.sp, letterSpacing = 1.0.sp, color = Color(0xFFD4E6F7))
                }
            }
            Text(
                "اربط الإشارات.
اكتشف الصورة الأكبر.",
                fontSize = 27.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WorkbenchColors.TextPrimary
            )
            Text(
                "اجمع بيانات عامة، وحوّلها إلى أدلة مترابطة يمكن مراجعتها — دون خادم خاص أو نموذج ضخم.",
                color = Color(0xFFC1D0E2),
                fontSize = 13.sp,
                lineHeight = 21.sp
            )
            Button(
                onClick = onOpenRecon,
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("ابدأ استطلاعاً", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, icon: ImageVector, value: String, label: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = WorkbenchColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier.size(35.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = WorkbenchColors.TextPrimary)
            Text(label, fontSize = 11.sp, color = WorkbenchColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = WorkbenchColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 10.sp, color = WorkbenchColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 11.sp, color = WorkbenchColors.TextSecondary)
    }
}

@Composable
private fun ReconScreen(store: LocalStore, onSaved: () -> Unit) {
    var domain by rememberSaveable { mutableStateOf("") }
    var includeCt by rememberSaveable { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading(
            title = "ذكاء النطاقات",
            subtitle = "استطلاع سلبي للأصول العامة",
            icon = Icons.Filled.Dns
        )
        Surface(
            color = WorkbenchColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("هدف الاستطلاع", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "أدخل نطاقاً تملكه أو لديك تصريح بفحصه. لا تنفذ هذه الوحدة فحص منافذ أو محاولات دخول.",
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("النطاق (example.com)") },
                    placeholder = { Text("example.com") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Language, contentDescription = null, tint = WorkbenchColors.Teal) },
                    colors = fieldColors()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WorkbenchColors.SurfaceElevated).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = includeCt, onCheckedChange = { includeCt = it })
                    Column(Modifier.weight(1f)) {
                        Text("شهادات TLS والنطاقات المرتبطة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("مصدر Certificate Transparency العام", fontSize = 10.sp, color = WorkbenchColors.TextSecondary)
                    }
                }
                if (error != null) {
                    Text(error.orEmpty(), color = WorkbenchColors.Red, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            scanning = true
                            error = null
                            report = null
                            try {
                                val result = ReconEngine().scanDomain(domain, includeCt)
                                withContext(Dispatchers.IO) {
                                    val rootId = store.upsertEntity(result.target, "DOMAIN", "User supplied", 100)
                                    result.records.forEach { item ->
                                        val nodeKind = item.kind
                                        val nodeId = store.upsertEntity(item.value, nodeKind, item.source, item.confidence)
                                        store.addEdge(
                                            fromId = nodeId,
                                            toId = rootId,
                                            relation = if (item.kind == "SUBDOMAIN") "SUBDOMAIN_OF" else "OBSERVED_FOR",
                                            source = item.source,
                                            confidence = item.confidence
                                        )
                                        store.addFinding(
                                            entityId = rootId,
                                            title = item.kind.replace('_', ' '),
                                            detail = "${item.value}\n${item.sourceUrl}",
                                            source = item.source,
                                            severity = "info"
                                        )
                                    }
                                    result.warnings.forEach { warning ->
                                        store.addFinding(rootId, "ملاحظة الاستطلاع", warning, "Recon engine", "warning")
                                    }
                                    store.addScan(
                                        target = result.target,
                                        status = if (result.records.isEmpty()) "no_results" else "completed",
                                        startedAt = result.startedAt,
                                        summary = "${result.records.size} records · ${result.warnings.size} warnings",
                                        records = result.records.size
                                    )
                                }
                                report = result
                                onSaved()
                            } catch (exception: Exception) {
                                error = exception.message ?: "تعذر إكمال الاستطلاع. تحقق من الاتصال والنطاق."
                            } finally {
                                scanning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !scanning,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                ) {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color(0xFF06231F))
                        Spacer(Modifier.width(10.dp))
                        Text("يجري جمع السجلات...")
                    } else {
                        androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تشغيل الاستطلاع السلبي", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (scanning) {
            Surface(color = Color(0xFF10282D), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = WorkbenchColors.Teal, strokeWidth = 2.dp)
                    Column {
                        Text("جلب المعلومات من المصادر العامة", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("تُرسل طلبات DNS بالتوازي لتقليل وقت الانتظار.", color = WorkbenchColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        report?.let { result ->
            ScanSummaryCard(result)
            if (result.records.isNotEmpty()) {
                SectionHeader("النتائج المكتشفة", "${result.records.size} نتيجة محفوظة في قاعدة البيانات المحلية")
                result.records.forEach { record ->
                    RecordCard(record.kind, record.value, record.source, record.confidence)
                }
            } else {
                EmptyState(Icons.Filled.Search, "لم تُعد المصادر سجلات", "قد يكون النطاق غير موجود أو أن المصدر حدّ من الطلبات. راجع الملاحظات أدناه.")
            }
            if (result.warnings.isNotEmpty()) {
                SectionHeader("ملاحظات المصدر", "تعذر جلب بعض أنواع السجلات")
                result.warnings.forEach { warning ->
                    Surface(color = Color(0xFF2B251A), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            androidx.compose.material3.Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = WorkbenchColors.Amber, modifier = Modifier.size(18.dp))
                            Text(warning, color = Color(0xFFE8D6B3), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ScanSummaryCard(report: ScanReport) {
    Surface(
        color = Color(0xFF10282D),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24534F))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("اكتمل جمع المعلومات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(report.target, color = WorkbenchColors.TextSecondary, fontSize = 12.sp)
                }
                Text("${report.elapsedMs / 1000}s", color = WorkbenchColors.Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Color(0xFF24534F))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryPill("${report.records.size} نتيجة", WorkbenchColors.Teal)
                SummaryPill("${report.warnings.size} ملاحظة", WorkbenchColors.Amber)
                SummaryPill("سلبي فقط", WorkbenchColors.Blue)
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.10f)) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecordCard(kind: String, value: String, source: String, confidence: Int) {
    Surface(
        color = WorkbenchColors.Surface,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(WorkbenchColors.Teal.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Filled.Dns, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind.replace('_', ' '), color = WorkbenchColors.Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                Text(source, color = WorkbenchColors.TextSecondary, fontSize = 10.sp)
            }
            Text("$confidence%", color = WorkbenchColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GraphScreen(entities: List<EntityRow>, relations: List<RelationRow>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading("شبكة العلاقات", "الكيانات المرتبطة بأدلة محفوظة", Icons.Filled.AccountTree)
        Surface(
            color = WorkbenchColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.AccountTree, contentDescription = null, tint = WorkbenchColors.Violet)
                    Text("خريطة الكيانات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "يُرسم الاتصال من العلاقات المخزنة فعلياً؛ كل خط أدناه يمثل علاقة تم تسجيلها من أحد المصادر.",
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
                if (relations.isEmpty()) {
                    EmptyState(Icons.Filled.AccountTree, "لا توجد علاقات بعد", "شغّل استطلاع نطاق أولاً كي يتمكن التطبيق من بناء الشبكة من النتائج.")
                } else {
                    GraphNetwork(relations = relations)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryPill("${relations.size} علاقة", WorkbenchColors.Violet)
                        SummaryPill("${relations.flatMap { listOf(it.fromValue, it.toValue) }.distinct().size} كياناً في الشبكة", WorkbenchColors.Teal)
                    }
                }
            }
        }
        SectionHeader("العلاقات المسجلة", "المصدر ودرجة الثقة محفوظان محلياً")
        if (relations.isEmpty()) {
            EmptyState(Icons.Filled.History, "بانتظار أول نتيجة", "لا توجد معلومات مصطنعة في الرسم؛ سيظهر هنا ما يدعمه دليل.")
        } else {
            relations.take(35).forEach { relation ->
                Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(relation.relation.replace('_', ' '), color = WorkbenchColors.Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(relation.fromValue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.width(22.dp).height(1.dp).background(WorkbenchColors.Violet))
                            Text(relation.toValue, color = WorkbenchColors.TextSecondary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(relation.source, color = WorkbenchColors.TextSecondary, fontSize = 10.sp)
                            Text("الثقة ${relation.confidence}%", color = WorkbenchColors.Teal, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (entities.isNotEmpty()) {
            SectionHeader("الكيانات المحفوظة", "${entities.size} كياناً في آخر مجموعة")
            entities.take(12).forEach { entity ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(WorkbenchColors.Surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Public, contentDescription = null, tint = WorkbenchColors.Blue, modifier = Modifier.size(19.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entity.value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entity.kind, color = WorkbenchColors.TextSecondary, fontSize = 10.sp)
                    }
                    Text("${entity.confidence}%", color = WorkbenchColors.TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun GraphNetwork(relations: List<RelationRow>) {
    val visibleRelations = relations.take(24)
    val nodeNames = visibleRelations.flatMap { listOf(it.fromValue, it.toValue) }.distinct().take(18)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF0B1525))
    ) {
        if (nodeNames.isEmpty()) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width * 0.39f, size.height * 0.38f)
        val positions = nodeNames.mapIndexed { index, _ ->
            val angle = (2.0 * Math.PI * index / nodeNames.size) - Math.PI / 2.0
            Offset(
                x = center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * radius).toFloat()
            )
        }
        visibleRelations.forEach { relation ->
            val from = nodeNames.indexOf(relation.fromValue)
            val to = nodeNames.indexOf(relation.toValue)
            if (from >= 0 && to >= 0) {
                drawLine(
                    color = WorkbenchColors.Violet.copy(alpha = 0.48f),
                    start = positions[from],
                    end = positions[to],
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        positions.forEachIndexed { index, point ->
            val nodeColor = if (index == 0) WorkbenchColors.Teal else WorkbenchColors.Blue
            drawCircle(color = nodeColor.copy(alpha = 0.12f), radius = 13.dp.toPx(), center = point)
            drawCircle(color = nodeColor, radius = 4.5.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun CasesScreen(cases: List<CaseRow>, store: LocalStore, onSaved: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var scopeText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading("مساحات العمل", "احتفظ بكل تحقيق وأدلته بشكل مستقل", Icons.Filled.FolderOpen)
        Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إنشاء مساحة جديدة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("اسم الحالة") },
                    placeholder = { Text("مثال: تقييم أمان الموقع") },
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("الهدف أو الأصل (اختياري)") },
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = scopeText,
                    onValueChange = { scopeText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("نطاق التفويض أو ملاحظات") },
                    placeholder = { Text("دوّن الأصول المسموح بفحصها وحدود العمل") },
                    colors = fieldColors()
                )
                if (error != null) Text(error.orEmpty(), color = WorkbenchColors.Red, fontSize = 12.sp)
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            error = "أدخل اسماً لمساحة العمل."
                        } else {
                            store.createCase(title, target, scopeText)
                            title = ""
                            target = ""
                            scopeText = ""
                            error = null
                            onSaved()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("إنشاء مساحة العمل", fontWeight = FontWeight.Bold)
                }
            }
        }
        SectionHeader("حالاتك", "${cases.size} مساحة محفوظة على هذا الجهاز")
        if (cases.isEmpty()) {
            EmptyState(Icons.Filled.FolderOpen, "لا توجد حالات بعد", "أنشئ مساحة لتسجيل الهدف وحدود الاختبار وملاحظاتك.")
        } else {
            cases.forEach { item ->
                Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(WorkbenchColors.Blue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = WorkbenchColors.Blue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(if (item.target.isBlank()) "لم يُحدد هدف" else item.target, color = WorkbenchColors.TextSecondary, fontSize = 11.sp)
                            }
                            androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = "محلي", tint = WorkbenchColors.Teal, modifier = Modifier.size(18.dp))
                        }
                        if (item.scope.isNotBlank()) Text(item.scope, color = WorkbenchColors.TextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                        Text("أُنشئت ${formatTime(item.createdAt)}", color = WorkbenchColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(stats: StoreStats) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading("الإعدادات والخصوصية", "شفافية البيانات وطريقة عمل النظام", Icons.Filled.Settings)
        Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                SettingRow(Icons.Filled.Storage, "قاعدة البيانات", "SQLite محلية داخل مساحة التطبيق", WorkbenchColors.Teal)
                HorizontalDivider(color = WorkbenchColors.Border)
                SettingRow(Icons.Filled.Lock, "الاتصال بالخادم", "لا يوجد خادم خاص أو حساب مستخدم", WorkbenchColors.Blue)
                HorizontalDivider(color = WorkbenchColors.Border)
                SettingRow(Icons.Filled.Security, "نمط الاستطلاع", "استعلامات عامة سلبية فقط في هذا الإصدار", WorkbenchColors.Violet)
                HorizontalDivider(color = WorkbenchColors.Border)
                SettingRow(Icons.Filled.Language, "المصادر الحالية", "Google Public DNS و crt.sh اختيارياً", WorkbenchColors.Amber)
            }
        }
        SectionHeader("حالة مساحة العمل", "إحصاءات قاعدة البيانات على الجهاز")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoLine("الكيانات", stats.entities.toString())
            InfoLine("العلاقات", stats.relationships.toString())
            InfoLine("الأدلة", stats.findings.toString())
            InfoLine("عمليات الجمع", stats.scans.toString())
            InfoLine("الحالات", stats.cases.toString())
        }
        Surface(color = Color(0xFF30291A), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5A4A2B))) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = WorkbenchColors.Amber)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ملاحظة أمنية", color = WorkbenchColors.Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "البيانات محفوظة محلياً، لكن تشفير قاعدة البيانات والنسخ الاحتياطي المشفّر لم يُنفّذا بعد. لا تخزّن أسراراً شديدة الحساسية في هذا الإصدار الأولي.",
                        color = Color(0xFFE4D6B5),
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        Text("OSINT Workbench · 0.1.0 · Prototype", color = WorkbenchColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, description: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 11.sp, color = WorkbenchColors.TextSecondary, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WorkbenchColors.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = WorkbenchColors.TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WorkbenchColors.TextPrimary)
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF193E47), Color(0xFF29294D)))),
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
private fun FindingCard(finding: FindingRow) {
    val accent = if (finding.severity == "warning") WorkbenchColors.Amber else WorkbenchColors.Teal
    Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Icon(
                if (finding.severity == "warning") Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(finding.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(finding.detail, color = WorkbenchColors.TextSecondary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                Text("${finding.source} · ${formatTime(finding.observedAt)}", color = accent, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String) {
    Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(WorkbenchColors.SurfaceElevated), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = WorkbenchColors.TextSecondary, modifier = Modifier.size(23.dp))
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(message, color = WorkbenchColors.TextSecondary, textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
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

private fun formatTime(timestamp: Long): String = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}.getOrDefault("—")
