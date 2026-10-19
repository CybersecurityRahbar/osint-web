package com.cyberphantom.osintworkbench.domain

import java.net.IDN
import java.util.Locale

object DomainTools {
    fun normalizeHost(raw: String): String {
        var value = raw.trim().lowercase(Locale.ROOT)
        value = value.removePrefix("https://").removePrefix("http://")
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (value.contains('@')) value = value.substringAfterLast('@')
        if (value.startsWith("[")) return value.substringAfter('[').substringBefore(']')
        if (value.count { it == ':' } == 1) value = value.substringBefore(':')
        value = value.trim().trimEnd('.')
        return if (value.isBlank()) "" else runCatching { IDN.toASCII(value) }.getOrDefault(value)
    }

    fun isValidDomain(raw: String): Boolean {
        val host = normalizeHost(raw)
        if (host.length !in 1..253 || host.startsWith('.') || host.endsWith('.')) return false
        if (host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        return labels.all { label ->
            label.length in 1..63 &&
                label.firstOrNull()?.isLetterOrDigit() == true &&
                label.lastOrNull()?.isLetterOrDigit() == true &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
