package com.cyberphantom.osintworkbench.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.ScanRow
import com.cyberphantom.osintworkbench.domain.HtmlReportBuilder
import com.cyberphantom.osintworkbench.domain.ScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataManagementPanel(store: LocalStore) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val scans = remember { store.scans(15) }
    var pendingHtml by remember { mutableStateOf<String?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var shareAfterHtml by remember { mutableStateOf(false) }

    val htmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        val data = pendingHtml
        if (uri != null && data != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف التصدير")
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(data) }
                        true
                    }.getOrDefault(false)
                }
                if (saved) {
                    Toast.makeText(context, "تم حفظ تقرير HTML بنجاح", Toast.LENGTH_LONG).show()
                    if (shareAfterHtml) {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/html"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(share, "مشاركة تقرير OSINT")) }
                    }
                } else {
                    Toast.makeText(context, "تعذر حفظ التقرير. اختر مجلداً آخر وحاول مجدداً.", Toast.LENGTH_LONG).show()
                }
                shareAfterHtml = false
            }
        } else {
            shareAfterHtml = false
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val data = pendingJson
        if (uri != null && data != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف التصدير")
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(data) }
                        true
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (saved) "تم حفظ ملف JSON" else "تعذر حفظ JSON",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val dbPath = context.getDatabasePath("osint_workbench.db").absolutePath

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DataSectionHeader("أين تُحفظ البيانات؟", "قاعدة SQLite خاصة بالتطبيق ولا تظهر عادةً في مدير الملفات")
        Surface(
            color = WorkbenchColors.Surface,
            shape = RoundedCornerShape(17.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Archive, contentDescription = null, tint = WorkbenchColors.Teal)
                    Text("قاعدة بيانات محلية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(dbPath, color = WorkbenchColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
                Text(
                    "لا تحتاج لفتح هذا المسار يدوياً. استخدم التصدير بالأسفل لحفظ تقرير أو نسخة JSON في مجلد التنزيلات أو أي مكان تختاره.",
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(dbPath))
                        Toast.makeText(context, "تم نسخ مسار قاعدة البيانات", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(11.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("نسخ مسار قاعدة البيانات", fontSize = 11.sp)
                }
            }
        }

        DataSectionHeader("تصدير البيانات", "التقرير المنفرد أو كامل مساحة العمل")
        ExportActionCard(
            iconLabel = "HTML",
            title = "تقرير مساحة العمل الكامل",
            description = "صفحة HTML منسقة تتضمن الكيانات والعلاقات والأدلة والحالات وآخر عمليات الجمع.",
            buttonLabel = "حفظ HTML",
            onClick = {
                scope.launch {
                    val html = withContext(Dispatchers.IO) { HtmlReportBuilder.workspaceReport(store) }
                    pendingHtml = html
                    shareAfterHtml = false
                    htmlLauncher.launch("osint-workbench-workspace-report.html")
                }
            }
        )
        ExportActionCard(
            iconLabel = "JSON",
            title = "نسخة بيانات قابلة للمعالجة",
            description = "كيانات وعلاقات ونتائج وعمليات بحث وحالات بصيغة JSON لاستيرادها أو تحليلها لاحقاً.",
            buttonLabel = "حفظ JSON",
            onClick = {
                scope.launch {
                    pendingJson = withContext(Dispatchers.IO) { store.exportSnapshotJson() }
                    jsonLauncher.launch("osint-workbench-export.json")
                }
            }
        )

        DataSectionHeader("التقارير المنفردة", "كل تقرير يعرض عملية بحث واحدة فقط، لا بقية الأهداف")
        if (scans.isEmpty()) {
            Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
                Text(
                    "لا توجد عمليات جمع محفوظة بعد. نفذ استطلاعاً أولاً، ثم ارجع إلى هذا القسم لحفظ تقريره أو مشاركته.",
                    modifier = Modifier.padding(15.dp),
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            scans.forEach { scan ->
                ScanExportCard(
                    scan = scan,
                    onExportHtml = { share ->
                        val report = ScanReport.fromJson(scan.payloadJson)
                        if (report == null) {
                            Toast.makeText(context, "هذا التقرير من إصدار قديم ولا يحتوي بيانات تفصيلية. نفذ البحث مجدداً لإنشاء تقرير كامل.", Toast.LENGTH_LONG).show()
                        } else {
                            pendingHtml = HtmlReportBuilder.scanReport(report)
                            shareAfterHtml = share
                            htmlLauncher.launch("${safeName(scan.target)}-osint-report.html")
                        }
                    },
                    onExportJson = {
                        pendingJson = scan.payloadJson.takeIf(String::isNotBlank) ?: "{}"
                        jsonLauncher.launch("${safeName(scan.target)}-osint-report.json")
                    }
                )
            }
        }

        Surface(
            color = Color(0xFF30291A),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5A4A2B))
        ) {
            Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Icon(Icons.Filled.Description, contentDescription = null, tint = WorkbenchColors.Amber, modifier = Modifier.size(18.dp))
                Text(
                    "تنبيه: قاعدة البيانات غير مشفرة عند السكون في هذا الإصدار الأولي. راجع HTML/JSON قبل مشاركتهما؛ فقد يحتوي التصدير الكامل على كل الأهداف والملاحظات المحفوظة.",
                    color = Color(0xFFE8D6B3),
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ScanExportCard(
    scan: ScanRow,
    onExportHtml: (Boolean) -> Unit,
    onExportJson: () -> Unit
) {
    Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(WorkbenchColors.Blue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = WorkbenchColors.Blue)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(scan.target, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatScanTime(scan.startedAt), color = WorkbenchColors.TextSecondary, fontSize = 9.sp)
                }
                Text("${scan.recordsCount} نتيجة", color = WorkbenchColors.Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(scan.summary, color = WorkbenchColors.TextSecondary, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onExportHtml(false) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("حفظ HTML", fontSize = 10.sp)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onExportHtml(true) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("حفظ ومشاركة", fontSize = 10.sp)
                }
                OutlinedButton(onClick = onExportJson, shape = RoundedCornerShape(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Code, contentDescription = "JSON", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ExportActionCard(
    iconLabel: String,
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkbenchColors.Border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WorkbenchColors.Teal.copy(alpha = 0.11f)), contentAlignment = Alignment.Center) {
                Text(iconLabel, color = WorkbenchColors.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(description, color = WorkbenchColors.TextSecondary, fontSize = 10.sp, lineHeight = 15.sp)
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
                    Text(buttonLabel, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun DataSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 10.sp, color = WorkbenchColors.TextSecondary, lineHeight = 16.sp)
    }
}

private fun safeName(raw: String): String =
    raw.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(50)
        .ifBlank { "osint" }

private fun formatScanTime(timestamp: Long): String = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}.getOrDefault("—")
