package com.cyberphantom.osintworkbench.domain

import com.cyberphantom.osintworkbench.data.LocalStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HtmlReportBuilder {
    fun scanReport(report: ScanReport): String {
        val groups = report.records.groupBy { it.kind }
        val rows = groups.entries.joinToString("") { (kind, items) ->
            val heading = "<tr class=\"section\"><th colspan=\"4\">${escape(label(kind))} <span class=\"count\">${items.size}</span></th></tr>"
            heading + items.joinToString("") { item ->
                "<tr><td>${escape(label(item.kind))}</td>" +
                    "<td class=\"value\">${escape(item.value)}</td>" +
                    "<td>${escape(item.source)}<br><small>${link(item.sourceUrl)}</small></td>" +
                    "<td><b>${item.confidence}%</b><br><small>ثقة إرشادية</small></td></tr>"
            }
        }
        val warningHtml = if (report.warnings.isEmpty()) {
            "<div class=\"ok\">لم تُسجل أخطاء مصادر خلال هذه العملية.</div>"
        } else report.warnings.joinToString("") { "<div class=\"warning\">${escape(it)}</div>" }

        return shell("تقرير استخبارات النطاق", report.target, report.finishedAt) {
            """
            <section class="hero">
              <div class="eyebrow">OSINT WORKBENCH · PASSIVE RECON</div>
              <h1>تقرير استخبارات النطاق</h1>
              <p>الأصل محل البحث: <bdi>${escape(report.target)}</bdi></p>
              <div class="chips"><span>${report.records.size} نتيجة</span><span>${report.sourceCount} مصادر</span><span>${report.warnings.size} ملاحظات</span><span>${report.elapsedMs / 1000} ثانية</span></div>
            </section>
            <section class="grid">
              <div class="metric"><span>سجلات مرصودة</span><b>${report.records.size}</b></div>
              <div class="metric"><span>مصادر مختلفة</span><b>${report.sourceCount}</b></div>
              <div class="metric"><span>تاريخ الجمع</span><b>${dateTime(report.finishedAt)}</b></div>
            </section>
            <section class="panel">
              <h2>الأدلة والنتائج</h2>
              <p>هذه النتائج تخص عملية البحث المحددة ولا تتضمن عمليات أخرى محفوظة في الهاتف. قد تكون البيانات عامة أو مؤرشفة أو ناقصة، وتحتاج الاستنتاجات التقنية إلى تحقق مستقل.</p>
              <div class="table-wrap"><table>
                <thead><tr><th>نوع المعلومة</th><th>القيمة المرصودة</th><th>المصدر</th><th>الثقة</th></tr></thead>
                <tbody>${rows.ifBlank { "<tr><td colspan=\"4\" class=\"empty\">لم تسجل هذه العملية نتائج.</td></tr>" }}</tbody>
              </table></div>
            </section>
            <section class="panel"><h2>حالة المصادر</h2>$warningHtml</section>
            <section class="panel caution">
              <h2>حدود التفسير</h2>
              <ul>
                <li>درجة الثقة رقم إرشادي مبني على نوع المصدر، وليست احتمالاً إحصائياً معايراً.</li>
                <li>ظهور اسم نطاق في شهادة أو أرشيف لا يثبت أن الخدمة نشطة حالياً أو أن جهة معينة ما زالت تتحكم به.</li>
                <li>غياب ترويسة أمان من استجابة واحدة ليس وحده إثباتاً لوجود ثغرة.</li>
                <li>الفحص سلبي وخفيف؛ لا ينفذ فحص منافذ مثل Nmap ولا يغطي منظومة مصادر SpiderFoot.</li>
              </ul>
            </section>
            <footer>أُنشئ محلياً بواسطة OSINT Workbench · تقرير مستقل للعملية المحددة.</footer>
            """
        }
    }

    fun workspaceReport(store: LocalStore): String {
        val stats = store.stats()
        val entities = store.entities(50_000)
        val relations = store.relations(100_000)
        val findings = store.allFindings(50_000)
        val scans = store.scans(10_000)
        val cases = store.cases(10_000)
        return shell("تقرير مساحة العمل", "البيانات المحلية", System.currentTimeMillis()) {
            """
            <section class="hero">
              <div class="eyebrow">OSINT WORKBENCH · LOCAL WORKSPACE</div><h1>تقرير مساحة العمل</h1>
              <p>ملخص للبيانات المحفوظة محلياً على هذا الجهاز وقت التصدير.</p>
              <div class="chips"><span>${stats.entities} كيانات</span><span>${stats.relationships} علاقات</span><span>${stats.findings} أدلة</span><span>${stats.scans} عمليات</span><span>${stats.cases} حالات</span></div>
            </section>
            <section class="grid">
              <div class="metric"><span>الكيانات</span><b>${stats.entities}</b></div>
              <div class="metric"><span>العلاقات</span><b>${stats.relationships}</b></div>
              <div class="metric"><span>الأدلة</span><b>${stats.findings}</b></div>
              <div class="metric"><span>عمليات الجمع</span><b>${stats.scans}</b></div>
              <div class="metric"><span>الحالات</span><b>${stats.cases}</b></div>
            </section>
            <section class="panel"><h2>عمليات الجمع المحفوظة</h2><div class="table-wrap"><table><thead><tr><th>الهدف</th><th>الحالة</th><th>النتائج</th><th>التاريخ</th></tr></thead><tbody>
              ${scans.joinToString("") { "<tr><td><bdi>${escape(it.target)}</bdi></td><td>${escape(it.status)}</td><td>${it.recordsCount}</td><td>${dateTime(it.startedAt)}</td></tr>" }.ifBlank { "<tr><td colspan=\"4\">لا توجد عمليات محفوظة.</td></tr>" }}
            </tbody></table></div></section>
            <section class="panel"><h2>الكيانات</h2><div class="table-wrap"><table><thead><tr><th>النوع</th><th>القيمة</th><th>المصدر</th><th>الثقة</th></tr></thead><tbody>
              ${entities.joinToString("") { "<tr><td>${escape(label(it.kind))}</td><td class=\"value\">${escape(it.value)}</td><td>${escape(it.source)}</td><td>${it.confidence}%</td></tr>" }.ifBlank { "<tr><td colspan=\"4\">لا توجد كيانات.</td></tr>" }}
            </tbody></table></div></section>
            <section class="panel"><h2>العلاقات المسجلة</h2><div class="table-wrap"><table><thead><tr><th>من</th><th>العلاقة</th><th>إلى</th><th>المصدر والثقة</th></tr></thead><tbody>
              ${relations.joinToString("") { "<tr><td class=\"value\">${escape(it.fromValue)}</td><td>${escape(label(it.relation))}</td><td class=\"value\">${escape(it.toValue)}</td><td>${escape(it.source)} · ${it.confidence}%</td></tr>" }.ifBlank { "<tr><td colspan=\"4\">لا توجد علاقات.</td></tr>" }}
            </tbody></table></div></section>
            <section class="panel"><h2>الأدلة الأخيرة</h2><div class="table-wrap"><table><thead><tr><th>النتيجة</th><th>التفاصيل</th><th>المصدر</th><th>الوقت</th></tr></thead><tbody>
              ${findings.joinToString("") { "<tr><td>${escape(it.title)}</td><td>${escape(it.detail)}</td><td>${escape(it.source)}</td><td>${dateTime(it.observedAt)}</td></tr>" }.ifBlank { "<tr><td colspan=\"4\">لا توجد أدلة.</td></tr>" }}
            </tbody></table></div></section>
            <section class="panel"><h2>الحالات</h2><div class="table-wrap"><table><thead><tr><th>الاسم</th><th>الهدف</th><th>النطاق</th><th>تاريخ الإنشاء</th></tr></thead><tbody>
              ${cases.joinToString("") { "<tr><td>${escape(it.title)}</td><td><bdi>${escape(it.target)}</bdi></td><td>${escape(it.scope)}</td><td>${dateTime(it.createdAt)}</td></tr>" }.ifBlank { "<tr><td colspan=\"4\">لا توجد حالات.</td></tr>" }}
            </tbody></table></div></section>
            <section class="panel caution"><h2>الخصوصية</h2><p>قد يتضمن هذا الملف أسماء أو معرّفات أو ملاحظات أدخلتها. راجعه قبل مشاركته. الملف يتضمن البيانات المصدرة فقط ولا يحتوي على قاعدة SQLite الأصلية.</p></section>
            <footer>أُنشئ محلياً بواسطة OSINT Workbench · تقرير مساحة العمل.</footer>
            """
        }
    }

    private fun shell(title: String, target: String, generatedAt: Long, body: () -> String): String =
        """<!doctype html>
<html lang="ar" dir="rtl"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><title>${escape(title)} — OSINT Workbench</title>
<style>
:root{--ink:#142239;--muted:#65758a;--line:#dbe4ef;--paper:#f3f7fb;--teal:#087f76;--blue:#2d66c3}
*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font-family:Tahoma,Arial,sans-serif;font-size:14px;line-height:1.8}
main{max-width:1180px;margin:auto;padding:28px 20px 50px}.hero{background:linear-gradient(125deg,#101b32,#1e3b62 62%,#087f76);color:white;border-radius:22px;padding:30px;box-shadow:0 12px 34px #1b365020}
.eyebrow{font-size:11px;letter-spacing:1.7px;color:#85f0df;font-weight:bold;direction:ltr}.hero h1{margin:8px 0;font-size:clamp(25px,4vw,40px);line-height:1.35}.hero p{color:#e1ecf8}.chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}.chips span{border:1px solid #ffffff3b;background:#ffffff12;padding:5px 12px;border-radius:30px;font-size:12px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:18px 0}.metric,.panel{background:white;border:1px solid var(--line);border-radius:16px}.metric{padding:15px 17px}.metric span{display:block;color:var(--muted);font-size:12px}.metric b{display:block;font-size:25px;color:var(--teal);overflow-wrap:anywhere}
.panel{padding:22px;margin:16px 0}.panel h2{font-size:18px;margin:0 0 13px;padding-bottom:10px;border-bottom:1px solid var(--line)}.panel p{color:#526277}.table-wrap{overflow-x:auto}table{border-collapse:collapse;width:100%;min-width:680px}th,td{border-bottom:1px solid #e6edf5;text-align:right;vertical-align:top;padding:10px 9px;overflow-wrap:anywhere}thead th{background:#f0f5fa;color:#34465c;font-size:12px}.section th{background:#eaf8f6;color:#087f76}.value{font-family:ui-monospace,Consolas,monospace;direction:ltr;text-align:left}.count{background:white;border-radius:15px;padding:0 8px}.tag{background:#eaf8f6;color:#087f76;padding:2px 8px;border-radius:8px;font-size:11px}small{font-size:11px;color:var(--muted)}a{color:#2067b8;text-decoration:none}.ok,.warning{padding:11px 13px;margin:8px 0;border-radius:10px}.ok{background:#e7f7ee;color:#17663c}.warning{background:#fff5df;border:1px solid #f1dda9;color:#7b5718}.caution{background:#f8fbff;border-right:4px solid var(--blue)}.empty{text-align:center;color:var(--muted);padding:24px}footer{color:var(--muted);text-align:center;font-size:11px;padding:15px 0}bdi{font-family:ui-monospace,Consolas,monospace;overflow-wrap:anywhere}
@media(max-width:640px){main{padding:14px 10px 25px}.hero{padding:20px 17px}.panel{padding:14px}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media print{body{background:white}.hero{box-shadow:none;print-color-adjust:exact}.panel,.metric{break-inside:avoid}main{max-width:none;padding:0}table{min-width:0;font-size:10px}}
</style></head><body><main>
<header style="display:flex;justify-content:space-between;gap:12px;color:#65758a;font-size:11px;margin-bottom:12px"><span>OSINT WORKBENCH</span><span>تاريخ التصدير: ${dateTime(generatedAt)}</span></header>
${body()}
</main></body></html>"""

    private fun link(url: String): String =
        if (url.startsWith("https://")) "<a href=\"${escape(url)}\">فتح المصدر</a>" else "—"

    private fun label(value: String): String = when (value) {
        "DOMAIN" -> "نطاق"
        "DNS_A" -> "سجل A"
        "DNS_AAAA" -> "سجل AAAA"
        "DNS_NS" -> "خوادم أسماء NS"
        "DNS_MX" -> "خوادم بريد MX"
        "DNS_TXT" -> "سجلات TXT"
        "DNS_CAA" -> "سياسة CAA"
        "SUBDOMAIN" -> "نطاق فرعي"
        "CERTIFICATE_NAME" -> "اسم في شهادة"
        "RDAP_STATUS" -> "حالة التسجيل"
        "RDAP_REGISTERED" -> "تاريخ التسجيل"
        "RDAP_EXPIRES" -> "تاريخ الانتهاء"
        "RDAP_UPDATED" -> "آخر تحديث"
        "RDAP_REGISTRAR" -> "جهة التسجيل"
        "WAYBACK_SNAPSHOT" -> "لقطة أرشيفية"
        "HTTPS_STATUS" -> "حالة HTTPS"
        "HTTPS_REDIRECT" -> "إعادة توجيه"
        "HTTP_SERVER_HEADER" -> "ترويسة الخادم"
        "TECH_X_POWERED_BY" -> "مؤشر تقني"
        "TECH_META_GENERATOR" -> "مولّد الصفحة"
        "WEB_TITLE" -> "عنوان الصفحة"
        "SECURITY_HEADER_NOT_OBSERVED" -> "ترويسة أمان غير مرصودة"
        "SUBDOMAIN_OF" -> "نطاق فرعي تابع لـ"
        "ARCHIVED_VERSION_OF" -> "لقطة أرشيفية"
        "REGISTRATION_DATA_FOR" -> "بيانات تسجيل عامة للنطاق"
        "WEB_METADATA_FOR" -> "بيانات الصفحة العامة"
        "MATCHED_NEWS" -> "مقالة مطابقة لعبارة البحث"
        "PUBLIC_PROFILE_RESULT_FOR" -> "نتيجة حساب عام لعبارة البحث"
        "OBSERVED_FOR" -> "سجل مرصود للنطاق"
        else -> value.replace('_', ' ')
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun dateTime(value: Long): String = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(value))
    }.getOrDefault("—")
}
