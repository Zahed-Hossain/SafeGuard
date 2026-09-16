package com.safeguard.blocklist.parser

import com.safeguard.blocklist.DomainMatcher
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedBlocklist(
    val version: String,
    val updatedAt: String,
    val validDomains: List<String>,
    val malformedDomains: List<String> = emptyList(),
    val duplicateCount: Int = 0
)

class BlocklistValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BlocklistJsonParser {

    private val VERSION_REGEX = Regex("""^\d+(\.\d+)*(-[a-zA-Z0-9.]+)?$""")
    private val DOMAIN_LABEL_REGEX = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$""")

    /**
     * Parses and strictly validates a blocklist JSON payload according to SafeGuard specifications.
     *
     * Expected format:
     * {
     *   "version": "1.0.0",
     *   "updatedAt": "2026-01-01T00:00:00Z",
     *   "domains": [
     *     "example.com"
     *   ]
     * }
     *
     * @throws BlocklistValidationException if JSON syntax is invalid, required root fields are missing,
     * or no valid domains could be parsed.
     */
    @Throws(BlocklistValidationException::class)
    fun parse(jsonString: String): ParsedBlocklist {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw BlocklistValidationException("Blocklist JSON is empty")
        }

        val root = try {
            JSONObject(trimmed)
        } catch (e: JSONException) {
            throw BlocklistValidationException("Malformed JSON syntax: ${e.message}", e)
        }

        // 1. Validate version
        if (!root.has("version")) {
            throw BlocklistValidationException("Missing required 'version' field in blocklist")
        }
        val version = root.optString("version", "").trim()
        if (version.isEmpty() || !VERSION_REGEX.matches(version)) {
            throw BlocklistValidationException("Invalid version format '$version'. Must be semantic version (e.g. 1.0.0)")
        }

        // 2. Validate updatedAt
        if (!root.has("updatedAt")) {
            throw BlocklistValidationException("Missing required 'updatedAt' field in blocklist")
        }
        val updatedAt = root.optString("updatedAt", "").trim()
        if (updatedAt.isEmpty()) {
            throw BlocklistValidationException("Field 'updatedAt' cannot be empty")
        }
        validateDateString(updatedAt)

        // 3. Validate domains array
        if (!root.has("domains")) {
            throw BlocklistValidationException("Missing required 'domains' array in blocklist")
        }
        val domainsArray: JSONArray = try {
            root.getJSONArray("domains")
        } catch (e: JSONException) {
            throw BlocklistValidationException("'domains' must be a JSON array", e)
        }

        val seenDomains = LinkedHashSet<String>()
        val malformedList = mutableListOf<String>()
        var duplicateCount = 0

        for (i in 0 until domainsArray.length()) {
            val raw = domainsArray.optString(i, "")
            val normalized = normalizeDomainInput(raw)

            if (isValidDomainFormat(normalized)) {
                if (!seenDomains.add(normalized)) {
                    duplicateCount++
                }
            } else {
                malformedList.add(raw)
            }
        }

        if (seenDomains.isEmpty() && malformedList.isNotEmpty()) {
            throw BlocklistValidationException("Blocklist contains no valid domains ($duplicateCount duplicates, ${malformedList.size} malformed)")
        }

        return ParsedBlocklist(
            version = version,
            updatedAt = updatedAt,
            validDomains = seenDomains.toList(),
            malformedDomains = malformedList,
            duplicateCount = duplicateCount
        )
    }

    /**
     * Serializes a domain list into the official SafeGuard blocklist JSON format.
     */
    fun toJson(
        version: String,
        updatedAt: String,
        domains: List<String>
    ): String {
        val json = JSONObject()
        json.put("version", version)
        json.put("updatedAt", updatedAt)

        val array = JSONArray()
        domains.forEach { array.put(it) }
        json.put("domains", array)

        return json.toString(2)
    }

    /**
     * Normalizes user input or URL strings into a clean domain name.
     * Example: "https://www.example.com/page" -> "example.com"
     */
    fun normalizeDomainInput(raw: String): String {
        var clean = DomainMatcher.normalize(raw)
        // Also strip "www." prefix for uniform root matching
        if (clean.startsWith("www.") && clean.length > 4) {
            clean = clean.substring(4)
        }
        return clean
    }

    /**
     * Validates domain syntax.
     */
    fun isValidDomainFormat(domain: String): Boolean {
        if (domain.isBlank() || domain.length > 253) return false
        if (domain.contains(" ") || domain.contains("/") || domain.contains(":") || domain.contains("?")) return false

        val labels = domain.split('.')
        // Must contain at least two labels (e.g. example.com or blocked.test)
        if (labels.size < 2) return false

        for (label in labels) {
            if (label.isEmpty() || label.length > 63) return false
            if (!DOMAIN_LABEL_REGEX.matches(label)) return false
        }

        // TLD check (last label must not be purely numeric)
        val tld = labels.last()
        if (tld.all { it.isDigit() }) return false

        return true
    }

    private fun validateDateString(dateStr: String) {
        val isoFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd"
        )

        var parsed = false
        for (pattern in isoFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.isLenient = false
                sdf.parse(dateStr)
                parsed = true
                break
            } catch (e: Exception) {
                // Try next pattern
            }
        }

        if (!parsed) {
            // If none matched, check basic ISO 8601 layout
            if (!dateStr.contains("-")) {
                throw BlocklistValidationException("Invalid date format '$dateStr'. Expected ISO-8601 string (e.g. 2026-01-01T00:00:00Z)")
            }
        }
    }
}
