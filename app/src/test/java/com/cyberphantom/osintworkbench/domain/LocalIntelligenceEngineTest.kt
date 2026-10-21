package com.cyberphantom.osintworkbench.domain

import com.cyberphantom.osintworkbench.data.EntityRow
import com.cyberphantom.osintworkbench.data.RelationRow
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntelligenceEngineTest {
    @Test
    fun sharedIpInsightWarnsThatItDoesNotProveCommonOwnership() {
        val entities = listOf(
            EntityRow(1, "a.example", "DOMAIN", "user", 100, 1, 1),
            EntityRow(2, "b.example", "DOMAIN", "user", 100, 1, 1),
            EntityRow(3, "203.0.113.8", "DNS_A", "DNS", 90, 1, 1)
        )
        val relations = listOf(
            RelationRow("a.example", "DOMAIN", "203.0.113.8", "DNS_A", "RESOLVES_TO", "DNS", 91, 1),
            RelationRow("b.example", "DOMAIN", "203.0.113.8", "DNS_A", "RESOLVES_TO", "DNS", 91, 1)
        )

        val insights = LocalIntelligenceEngine.analyze(entities, relations, nowMillis = 2)
        assertTrue(insights.any { it.title.contains("IP") && it.explanation.contains("لا يثبت") })
    }

    @Test
    fun differentSourcesAreReportedAsCorroborationWithoutClaimingIndependence() {
        val relations = listOf(
            RelationRow("a.example", "DOMAIN", "203.0.113.8", "DNS_A", "RESOLVES_TO", "Cloudflare DNS", 91, 10),
            RelationRow("a.example", "DOMAIN", "203.0.113.8", "DNS_A", "RESOLVES_TO", "Google Public DNS", 91, 11)
        )

        val insights = LocalIntelligenceEngine.analyze(emptyList(), relations, nowMillis = 20)
        assertTrue(insights.any {
            it.title.contains("أكثر من مصدر") && it.explanation.contains("لا يضمن استقلال")
        })
    }

    @Test
    fun noEvidenceDoesNotInventRelationships() {
        val insights = LocalIntelligenceEngine.analyze(emptyList(), emptyList(), nowMillis = 1)
        assertTrue(insights.any { it.title.contains("الأدلة غير كافية") })
    }

    @Test
    fun weakEvidenceProducesVerificationRecommendation() {
        val entities = listOf(
            EntityRow(1, "a.example", "DOMAIN", "input", 100, 1, 1),
            EntityRow(2, "archive record", "WAYBACK_SNAPSHOT", "Wayback", 45, 1, 1)
        )
        val relations = listOf(
            RelationRow("archive record", "WAYBACK_SNAPSHOT", "a.example", "DOMAIN", "ARCHIVED_VERSION_OF", "Wayback", 45, 1)
        )

        val insights = LocalIntelligenceEngine.analyze(entities, relations, nowMillis = 2)
        assertTrue(insights.any { it.title.contains("تحتاج إلى تحقق") })
    }
}
