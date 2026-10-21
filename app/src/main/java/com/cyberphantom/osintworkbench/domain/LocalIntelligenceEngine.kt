package com.cyberphantom.osintworkbench.domain

import com.cyberphantom.osintworkbench.data.EntityRow
import com.cyberphantom.osintworkbench.data.RelationRow
import java.util.Locale

data class LocalIntelligenceInsight(
    val title: String,
    val explanation: String,
    val recommendation: String,
    val priority: Int,
    val evidenceCount: Int = 0
)

/**
 * A transparent, offline rule engine. It does not guess identity from names or claim ownership
 * from shared infrastructure. Every correlation is based on stored, source-backed edges.
 */
object LocalIntelligenceEngine {
    fun analyze(
        entities: List<EntityRow>,
        relations: List<RelationRow>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<LocalIntelligenceInsight> {
        val insights = mutableListOf<LocalIntelligenceInsight>()
        val kinds = entities.map { it.kind.substringBefore('@').uppercase(Locale.ROOT) }.toSet()

        val resolvedToSameIp = relations
            .filter { it.relation == "RESOLVES_TO" && it.toKind in setOf("DNS_A", "DNS_AAAA") }
            .groupBy { it.toValue }
            .filterValues { rows -> rows.map { it.fromValue }.distinct().size >= 2 }

        resolvedToSameIp.forEach { (ip, rows) ->
            val domains = rows.map { it.fromValue }.distinct()
            insights += LocalIntelligenceInsight(
                title = "عنوان IP مشترك مرصود",
                explanation = "${domains.size} نطاقات من النتائج المحفوظة تحلّ إلى ${ip}. هذا قد يشير إلى استضافة مشتركة أو CDN؛ ولا يثبت أن النطاقات لمالك واحد.",
                recommendation = "قارن سجلات DNS والشهادات وتواريخ الجمع قبل استنتاج أي علاقة تنظيمية.",
                priority = 2,
                evidenceCount = rows.size
            )
        }

        val childRelations = relations.filter { it.relation == "SUBDOMAIN_OF" }
        if (childRelations.isNotEmpty()) {
            insights += LocalIntelligenceInsight(
                title = "علاقات نطاقات فرعية مدعومة بمصدر",
                explanation = "توجد ${childRelations.size} علاقات نطاق فرعي مأخوذة من نتائج عامة محفوظة؛ يمكن مراجعة مصدر كل علاقة ووقت جمعها.",
                recommendation = "افحص السجلات الحالية للنطاقات الفرعية المهمة للتأكد من أنها لا تزال فعالة.",
                priority = 3,
                evidenceCount = childRelations.size
            )
        }

        val weakEdges = relations.filter { it.confidence < 60 }
        if (weakEdges.isNotEmpty()) {
            insights += LocalIntelligenceInsight(
                title = "علاقات تحتاج إلى تحقق إضافي",
                explanation = "هناك ${weakEdges.size} علاقات بدرجة ثقة أولية أقل من 60%. هذه الدرجات إرشادية وليست احتمالات إحصائية.",
                recommendation = "راجع المصدر والتفاصيل، ثم اجمع دليلاً مستقلاً قبل الاعتماد على هذه العلاقة.",
                priority = 1,
                evidenceCount = weakEdges.size
            )
        }

        val oldestObservation = relations.minOfOrNull { it.observedAt }
        if (oldestObservation != null && nowMillis - oldestObservation > 30L * 24 * 60 * 60 * 1000) {
            insights += LocalIntelligenceInsight(
                title = "بعض العلاقات قديمة",
                explanation = "أقدم علاقة محفوظة مرّ عليها أكثر من 30 يوماً. سجلات DNS والشهادات والصفحات تتغير، لذلك قد لا تصف الوضع الحالي.",
                recommendation = "أعد جمع البيانات للنطاقات المهمة وقارن النتائج بدلاً من استبدال السجل القديم.",
                priority = 2,
                evidenceCount = relations.count { nowMillis - it.observedAt > 30L * 24 * 60 * 60 * 1000 }
            )
        }

        if ("DNS_MX" !in kinds) {
            insights += LocalIntelligenceInsight(
                title = "بيانات البريد غير مكتملة",
                explanation = "لا توجد سجلات MX محفوظة حالياً في الكيانات التي عُرضت للمحلل المحلي.",
                recommendation = "نفّذ استطلاع النطاق مع اتصال يسمح بطلبات DNS، ثم افحص سجلات MX وSPF وDMARC الناتجة.",
                priority = 4
            )
        }
        if ("WAYBACK_SNAPSHOT" !in kinds) {
            insights += LocalIntelligenceInsight(
                title = "لا توجد لقطات أرشيف محفوظة",
                explanation = "لم يُحفظ أي سجل WAYBACK_SNAPSHOT ضمن النتائج الحالية.",
                recommendation = "شغّل خيار RDAP والأرشيف وبيانات HTTPS في استطلاع النطاق، ثم راجع روابط اللقطات ومصادرها.",
                priority = 4
            )
        }
        if (relations.isEmpty()) {
            insights += LocalIntelligenceInsight(
                title = "الأدلة غير كافية لبناء علاقات",
                explanation = "لا توجد علاقات محفوظة يمكن للمحلل الاستناد إليها. لن يُنشئ النظام روابط اعتماداً على تشابه الأسماء فقط.",
                recommendation = "ابدأ باستطلاع نطاق، أو اجمع نتائج عامة من البحث، ثم راجع كل علاقة ومصدرها.",
                priority = 1
            )
        } else if (insights.isEmpty()) {
            insights += LocalIntelligenceInsight(
                title = "لا توجد إشارة إضافية واضحة",
                explanation = "تمت مراجعة العلاقات المتاحة ولم تظهر قاعدة أخرى ذات دلالة كافية.",
                recommendation = "أضف نتائج من مصادر مستقلة أو أعد جمع الأدلة لاحقاً لمقارنة التغييرات.",
                priority = 5
            )
        }

        return insights.sortedWith(compareBy<LocalIntelligenceInsight> { it.priority }.thenByDescending { it.evidenceCount }).take(8)
    }
}
