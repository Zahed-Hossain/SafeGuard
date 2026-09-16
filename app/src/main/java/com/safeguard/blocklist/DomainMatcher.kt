package com.safeguard.blocklist

import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * DomainMatcher provides robust domain normalization, IDN/punycode conversion,
 * and secure domain hierarchy/suffix matching.
 *
 * Guarantees:
 * - Subdomain and wildcard matching: "example.com" matches "sub.example.com" and "www.example.com".
 * - Strict suffix protection: "example.com" will NEVER match "example.com.evil.com" or "notexample.com".
 * - IDN / Punycode: Unicode domains (e.g., IDN homographs) are safely converted to ASCII representation.
 */
object DomainMatcher {

    /**
     * Normalizes an input string (URL, domain, host with port/path, trailing dot, etc.)
     * into a canonical lowercase domain name.
     */
    fun normalize(rawInput: String): String {
        var input = rawInput.trim()
        if (input.isEmpty()) return ""

        // 1. Strip protocol scheme if present (e.g. "https://", "http://", "ftp://", "//")
        if (input.contains("://")) {
            val schemeEnd = input.indexOf("://") + 3
            input = input.substring(schemeEnd)
        } else if (input.startsWith("//")) {
            input = input.substring(2)
        }

        // 2. Strip path, query parameters, and fragments
        val pathIndex = input.indexOfAny(charArrayOf('/', '?', '#'))
        if (pathIndex != -1) {
            input = input.substring(0, pathIndex)
        }

        // 3. Strip userinfo (user:pass@host)
        val atIndex = input.lastIndexOf('@')
        if (atIndex != -1) {
            input = input.substring(atIndex + 1)
        }

        // 4. Strip port (e.g. "example.com:8080" -> "example.com")
        // Be careful with IPv6 literals like "[::1]:8080"
        if (input.startsWith("[")) {
            val closingBracket = input.indexOf(']')
            if (closingBracket != -1) {
                input = input.substring(1, closingBracket)
            }
        } else {
            val colonIndex = input.indexOf(':')
            if (colonIndex != -1) {
                input = input.substring(0, colonIndex)
            }
        }

        // 5. Lowercase normalization
        input = input.lowercase(Locale.ROOT)

        // 6. Strip leading wildcard (e.g. "*.example.com" -> "example.com")
        if (input.startsWith("*.")) {
            input = input.substring(2)
        } else if (input.startsWith(".")) {
            input = input.substring(1)
        }

        // 7. Strip trailing dots (e.g. "example.com." -> "example.com")
        while (input.endsWith(".")) {
            input = input.dropLast(1)
        }

        // 8. IDN / Punycode conversion for international domain safety
        return try {
            if (input.isNotEmpty()) {
                IDN.toASCII(input, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
            } else {
                ""
            }
        } catch (e: Exception) {
            // Graceful fallback if malformed IDN sequence
            input
        }
    }

    /**
     * Removes "www." prefix if present, returning the root domain candidate.
     */
    fun stripWww(domain: String): String {
        val normalized = normalize(domain)
        return if (normalized.startsWith("www.") && normalized.length > 4) {
            normalized.substring(4)
        } else {
            normalized
        }
    }

    /**
     * Checks if [domain] matches the [rulePattern].
     *
     * @param domain The queried domain (e.g., "sub.example.com")
     * @param rulePattern The filtering rule (e.g., "example.com")
     * @param isExactMatch If true, requires exact string equality.
     *                     If false, matches exact domain AND any subdomain (e.g., "*.example.com").
     */
    fun matches(domain: String, rulePattern: String, isExactMatch: Boolean = false): Boolean {
        val cleanDomain = normalize(domain)
        val cleanRule = normalize(rulePattern)

        if (cleanDomain.isEmpty() || cleanRule.isEmpty()) return false

        if (isExactMatch) {
            return cleanDomain == cleanRule
        }

        // Exact match
        if (cleanDomain == cleanRule) {
            return true
        }

        // Strict Subdomain / Suffix match:
        // Must end with ".cleanRule" to ensure "example.com.evil.com" or "notexample.com" does NOT match "example.com"
        val suffix = ".$cleanRule"
        return cleanDomain.endsWith(suffix)
    }

    /**
     * Splits a normalized domain into all its hierarchical parent suffixes.
     * Ordered from most specific to least specific.
     *
     * Example: "video.sub.example.com" -> ["video.sub.example.com", "sub.example.com", "example.com"]
     */
    fun getDomainHierarchy(domain: String): List<String> {
        val clean = normalize(domain)
        if (clean.isEmpty()) return emptyList()

        val labels = clean.split('.')
        if (labels.size <= 1) return listOf(clean)

        val hierarchy = ArrayList<String>(labels.size - 1)
        for (i in 0 until labels.size - 1) {
            hierarchy.add(labels.subList(i, labels.size).joinToString("."))
        }
        return hierarchy
    }
}
