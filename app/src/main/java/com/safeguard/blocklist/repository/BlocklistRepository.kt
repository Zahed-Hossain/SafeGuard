package com.safeguard.blocklist.repository

import android.content.Context
import android.util.Log
import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.DomainFilter
import com.safeguard.blocklist.parser.BlocklistJsonParser
import com.safeguard.blocklist.parser.BlocklistValidationException
import com.safeguard.blocklist.parser.ParsedBlocklist
import com.safeguard.data.db.BlockEvent
import com.safeguard.data.db.BlockedDomain
import com.safeguard.data.db.SafeGuardDatabase
import com.safeguard.data.db.SettingsEntity
import com.safeguard.data.db.WhitelistDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

sealed class UpdateResult {
    data class Success(val version: String, val domainCount: Int, val duplicateCount: Int) : UpdateResult()
    data class Failure(val errorMessage: String, val preservedPreviousDb: Boolean = true) : UpdateResult()
}

data class ImportResult(
    val addedCount: Int,
    val skippedCount: Int,
    val malformedCount: Int
)

/**
 * BlocklistRepository coordinates Room database persistence, remote HTTPS updates,
 * custom blacklist/whitelist operations, and keeps the high-performance in-memory
 * [DomainFilter] synchronized for real-time packet inspection.
 */
class BlocklistRepository(
    private val context: Context,
    val database: SafeGuardDatabase = SafeGuardDatabase.getInstance(context),
    val domainFilter: DomainFilter = DomainFilter()
) {

    companion object {
        private const val TAG = "BlocklistRepository"

        /**
         * Documented clean placeholder for remote blocklist updates as mandated by project requirements.
         * Administrators or users can configure any verified HTTPS blocklist endpoint in the app settings.
         */
        const val BLOCKLIST_URL_PLACEHOLDER = "https://safeguard-filter.invalid/v1/adult-blocklist.json"
        const val SETTING_KEY_BLOCKLIST_URL = "remote_blocklist_url"
        const val SETTING_KEY_AUTO_UPDATE = "auto_update_enabled"
        const val SETTING_KEY_DB_VERSION = "database_version"
        const val SETTING_KEY_LAST_UPDATED = "database_last_updated"
        const val DEFAULT_VERSION = "1.0.0-builtin"
    }

    private val blockedDomainDao = database.blockedDomainDao()
    private val whitelistDao = database.whitelistDao()
    private val blockEventDao = database.blockEventDao()
    private val settingsDao = database.settingsDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val totalBlockedCount: Flow<Int> = blockedDomainDao.getTotalCount()
    val totalWhitelistCount: Flow<Int> = whitelistDao.getCount()
    val allBlockedDomains: Flow<List<BlockedDomain>> = blockedDomainDao.getAllBlockedDomains()
    val customBlockedDomains: Flow<List<BlockedDomain>> = blockedDomainDao.getBlockedDomainsBySource("CUSTOM")
    val allWhitelistDomains: Flow<List<WhitelistDomain>> = whitelistDao.getAllWhitelist()

    val databaseVersion: Flow<String> = settingsDao.getSettingFlow(SETTING_KEY_DB_VERSION)
        .map { it ?: DEFAULT_VERSION }

    val lastUpdated: Flow<String> = settingsDao.getSettingFlow(SETTING_KEY_LAST_UPDATED)
        .map { it ?: "Initial Installation" }

    val remoteUrl: Flow<String> = settingsDao.getSettingFlow(SETTING_KEY_BLOCKLIST_URL)
        .map { it ?: BLOCKLIST_URL_PLACEHOLDER }

    val autoUpdateEnabled: Flow<Boolean> = settingsDao.getSettingFlow(SETTING_KEY_AUTO_UPDATE)
        .map { it?.toBooleanStrictOrNull() ?: false }

    init {
        // Asynchronously initialize database and sync in-memory filter
        CoroutineScope(Dispatchers.IO).launch {
            initializeDatabaseIfNeeded()
            syncMemoryFilter()
        }
    }

    /**
     * Seeds the built-in database if this is a first run or empty database.
     */
    suspend fun initializeDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        val count = blockedDomainDao.getTotalCountSync()
        if (count == 0) {
            Log.d(TAG, "Database is empty. Seeding built-in blocklist and whitelist...")
            seedBuiltinDatabase()
        }
    }

    private suspend fun seedBuiltinDatabase() {
        val timestamp = System.currentTimeMillis()
        val builtinDomains = mutableListOf<BlockedDomain>()

        // 1. Safe Developer Test Domains (Mandated)
        builtinDomains.add(BlockedDomain(domain = "blocked.test", category = BlockedCategory.ADULT.name, source = "BUILTIN", createdAt = timestamp))
        builtinDomains.add(BlockedDomain(domain = "adult-test.local", category = BlockedCategory.PORNOGRAPHY.name, source = "BUILTIN", createdAt = timestamp))
        builtinDomains.add(BlockedDomain(domain = "example-blocked.test", category = BlockedCategory.EXPLICIT.name, source = "BUILTIN", createdAt = timestamp))

        // 2. Comprehensive built-in adult categories
        val adultDomains = listOf(
            "adult.test" to BlockedCategory.ADULT,
            "adultcontent.test" to BlockedCategory.ADULT,
            "mature-content.test" to BlockedCategory.ADULT,
            "pornhub.com" to BlockedCategory.PORNOGRAPHY,
            "xvideos.com" to BlockedCategory.PORNOGRAPHY,
            "xnxx.com" to BlockedCategory.PORNOGRAPHY,
            "redtube.com" to BlockedCategory.PORNOGRAPHY,
            "youporn.com" to BlockedCategory.PORNOGRAPHY,
            "tube8.com" to BlockedCategory.PORNOGRAPHY,
            "brazzers.com" to BlockedCategory.PORNOGRAPHY,
            "beeg.com" to BlockedCategory.PORNOGRAPHY,
            "spankbang.com" to BlockedCategory.PORNOGRAPHY,
            "porn.com" to BlockedCategory.PORNOGRAPHY,
            "badjojo.com" to BlockedCategory.EXPLICIT,
            "heavy-r.com" to BlockedCategory.EXPLICIT,
            "efukt.com" to BlockedCategory.EXPLICIT,
            "tnaflix.com" to BlockedCategory.EXPLICIT,
            "empflix.com" to BlockedCategory.EXPLICIT,
            "rule34.xxx" to BlockedCategory.NSFW,
            "gelbooru.com" to BlockedCategory.NSFW,
            "danbooru.donmai.us" to BlockedCategory.NSFW,
            "e-hentai.org" to BlockedCategory.NSFW,
            "nhentai.net" to BlockedCategory.NSFW,
            "chaturbate.com" to BlockedCategory.ADULT_STREAMING,
            "cam4.com" to BlockedCategory.ADULT_STREAMING,
            "stripchat.com" to BlockedCategory.ADULT_STREAMING,
            "livejasmin.com" to BlockedCategory.ADULT_STREAMING,
            "bongacams.com" to BlockedCategory.ADULT_STREAMING,
            "myfreecams.com" to BlockedCategory.ADULT_STREAMING,
            "camsoda.com" to BlockedCategory.ADULT_STREAMING,
            "flirt4free.com" to BlockedCategory.ADULT_STREAMING,
            "literotica.com" to BlockedCategory.ADULT_COMMUNITY,
            "lushstories.com" to BlockedCategory.ADULT_COMMUNITY,
            "asstr.org" to BlockedCategory.ADULT_COMMUNITY,
            "fetlife.com" to BlockedCategory.ADULT_COMMUNITY,
            "ashley-madison.com" to BlockedCategory.ADULT_DATING,
            "adultfriendfinder.com" to BlockedCategory.ADULT_DATING,
            "passion.com" to BlockedCategory.ADULT_DATING,
            "alt.com" to BlockedCategory.ADULT_DATING,
            "fling.com" to BlockedCategory.ADULT_DATING,
            "inappropriate-test.local" to BlockedCategory.INAPPROPRIATE,
            "unsafe-preview.test" to BlockedCategory.INAPPROPRIATE
        )

        adultDomains.forEach { (domain, category) ->
            builtinDomains.add(
                BlockedDomain(
                    domain = domain,
                    category = category.name,
                    source = "BUILTIN",
                    createdAt = timestamp
                )
            )
        }

        blockedDomainDao.insertAll(builtinDomains)

        // Seed Whitelist
        whitelistDao.insert(
            WhitelistDomain(
                domain = "allowed.test",
                createdAt = timestamp
            )
        )

        // Seed metadata
        val now = getCurrentIsoTimestamp()
        settingsDao.putString(SETTING_KEY_DB_VERSION, DEFAULT_VERSION)
        settingsDao.putString(SETTING_KEY_LAST_UPDATED, now)
        settingsDao.putString(SETTING_KEY_BLOCKLIST_URL, BLOCKLIST_URL_PLACEHOLDER)
        settingsDao.putString(SETTING_KEY_AUTO_UPDATE, "false")
    }

    /**
     * Synchronizes the in-memory O(k) DomainFilter from the Room database.
     */
    suspend fun syncMemoryFilter() = withContext(Dispatchers.IO) {
        domainFilter.clearAll()

        // 1. Populate Whitelist (Highest Priority)
        val whitelist = whitelistDao.getAllWhitelistList()
        whitelist.forEach { item ->
            domainFilter.addWhitelist(item.domain, isExactOnly = false)
        }

        // 2. Populate Blocked Domains
        val blockedList = blockedDomainDao.getAllBlockedDomainsList()
        blockedList.forEach { item ->
            val cat = try {
                BlockedCategory.valueOf(item.category)
            } catch (e: Exception) {
                BlockedCategory.ADULT
            }
            domainFilter.addBlockRule(item.domain, cat, isExactOnly = false)
        }

        Log.d(TAG, "Memory filter synchronized: ${domainFilter.getBlocklistSize()} blocked, ${domainFilter.getWhitelistSize()} whitelisted")
    }

    // ==========================================
    // CUSTOM BLACKLIST MANAGEMENT
    // ==========================================

    /**
     * Adds a custom domain.
     * Normalizes inputs such as "https://www.example.com/page" into "example.com".
     */
    suspend fun addCustomBlockedDomain(
        rawInput: String,
        category: BlockedCategory = BlockedCategory.ADULT
    ): Result<BlockedDomain> = withContext(Dispatchers.IO) {
        val normalized = BlocklistJsonParser.normalizeDomainInput(rawInput)
        if (!BlocklistJsonParser.isValidDomainFormat(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid domain format: '$rawInput' (normalized: '$normalized')")
            )
        }

        val existing = blockedDomainDao.findByDomain(normalized)
        if (existing != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Domain '$normalized' is already in the blocklist (${existing.source})")
            )
        }

        val entity = BlockedDomain(
            domain = normalized,
            category = category.name,
            source = "CUSTOM",
            createdAt = System.currentTimeMillis()
        )
        val id = blockedDomainDao.insert(entity)
        val inserted = entity.copy(id = id)

        domainFilter.addBlockRule(normalized, category, isExactOnly = false)
        Result.success(inserted)
    }

    suspend fun editBlockedDomain(
        id: Long,
        rawInput: String,
        category: BlockedCategory
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = BlocklistJsonParser.normalizeDomainInput(rawInput)
        if (!BlocklistJsonParser.isValidDomainFormat(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid domain format: '$rawInput'")
            )
        }

        val existing = blockedDomainDao.findByDomain(normalized)
        if (existing != null && existing.id != id) {
            return@withContext Result.failure(
                IllegalArgumentException("Domain '$normalized' already exists in blocklist")
            )
        }

        val updated = BlockedDomain(
            id = id,
            domain = normalized,
            category = category.name,
            source = "CUSTOM",
            createdAt = System.currentTimeMillis()
        )
        blockedDomainDao.update(updated)
        syncMemoryFilter()
        Result.success(Unit)
    }

    suspend fun deleteBlockedDomain(domain: BlockedDomain) = withContext(Dispatchers.IO) {
        blockedDomainDao.delete(domain)
        domainFilter.removeBlockRule(domain.domain)
    }

    suspend fun deleteBlockedDomainById(id: Long) = withContext(Dispatchers.IO) {
        blockedDomainDao.deleteById(id)
        syncMemoryFilter()
    }

    /**
     * Imports multiple domains from text (line-separated, comma-separated, or JSON).
     */
    suspend fun importDomains(
        rawText: String,
        category: BlockedCategory = BlockedCategory.ADULT
    ): ImportResult = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return@withContext ImportResult(0, 0, 0)

        val candidateDomains = mutableListOf<String>()

        if (trimmed.startsWith("{") && trimmed.contains("\"domains\"")) {
            // JSON format
            try {
                val parsed = BlocklistJsonParser.parse(trimmed)
                candidateDomains.addAll(parsed.validDomains)
            } catch (e: Exception) {
                Log.w(TAG, "Import: JSON parse failed, falling back to line parser: ${e.message}")
            }
        }

        if (candidateDomains.isEmpty()) {
            val lines = trimmed.split(Regex("[\r\n,;]+"))
            lines.forEach { line ->
                val lineTrimmed = line.trim()
                if (lineTrimmed.isNotEmpty() && !lineTrimmed.startsWith("#")) {
                    candidateDomains.add(lineTrimmed)
                }
            }
        }

        var addedCount = 0
        var skippedCount = 0
        var malformedCount = 0
        val timestamp = System.currentTimeMillis()
        val toInsert = mutableListOf<BlockedDomain>()

        candidateDomains.forEach { raw ->
            val normalized = BlocklistJsonParser.normalizeDomainInput(raw)
            if (!BlocklistJsonParser.isValidDomainFormat(normalized)) {
                malformedCount++
            } else if (blockedDomainDao.findByDomain(normalized) != null) {
                skippedCount++
            } else {
                toInsert.add(
                    BlockedDomain(
                        domain = normalized,
                        category = category.name,
                        source = "CUSTOM",
                        createdAt = timestamp
                    )
                )
                addedCount++
            }
        }

        if (toInsert.isNotEmpty()) {
            blockedDomainDao.insertAll(toInsert)
            syncMemoryFilter()
        }

        ImportResult(addedCount, skippedCount, malformedCount)
    }

    /**
     * Exports domains into the official SafeGuard JSON blocklist format.
     */
    suspend fun exportDomainsToJson(sourceFilter: String? = null): String = withContext(Dispatchers.IO) {
        val domains = if (sourceFilter != null) {
            blockedDomainDao.getBlockedDomainsListBySource(sourceFilter)
        } else {
            blockedDomainDao.getAllBlockedDomainsList()
        }
        val version = settingsDao.getSetting(SETTING_KEY_DB_VERSION) ?: DEFAULT_VERSION
        val now = getCurrentIsoTimestamp()
        BlocklistJsonParser.toJson(version, now, domains.map { it.domain })
    }

    // ==========================================
    // WHITELIST MANAGEMENT
    // ==========================================

    suspend fun addWhitelistDomain(rawInput: String): Result<WhitelistDomain> = withContext(Dispatchers.IO) {
        val normalized = BlocklistJsonParser.normalizeDomainInput(rawInput)
        if (!BlocklistJsonParser.isValidDomainFormat(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid domain format: '$rawInput' (normalized: '$normalized')")
            )
        }

        val existing = whitelistDao.findByDomain(normalized)
        if (existing != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Domain '$normalized' is already whitelisted")
            )
        }

        val entity = WhitelistDomain(
            domain = normalized,
            createdAt = System.currentTimeMillis()
        )
        val id = whitelistDao.insert(entity)
        val inserted = entity.copy(id = id)

        domainFilter.addWhitelist(normalized, isExactOnly = false)
        Result.success(inserted)
    }

    suspend fun editWhitelistDomain(id: Long, rawInput: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = BlocklistJsonParser.normalizeDomainInput(rawInput)
        if (!BlocklistJsonParser.isValidDomainFormat(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid domain format: '$rawInput'")
            )
        }

        val existing = whitelistDao.findByDomain(normalized)
        if (existing != null && existing.id != id) {
            return@withContext Result.failure(
                IllegalArgumentException("Domain '$normalized' is already in the whitelist")
            )
        }

        val updated = WhitelistDomain(
            id = id,
            domain = normalized,
            createdAt = System.currentTimeMillis()
        )
        whitelistDao.update(updated)
        syncMemoryFilter()
        Result.success(Unit)
    }

    suspend fun deleteWhitelistDomain(domain: WhitelistDomain) = withContext(Dispatchers.IO) {
        whitelistDao.delete(domain)
        domainFilter.removeWhitelist(domain.domain)
    }

    suspend fun deleteWhitelistById(id: Long) = withContext(Dispatchers.IO) {
        whitelistDao.deleteById(id)
        syncMemoryFilter()
    }

    // ==========================================
    // REMOTE HTTPS BLOCKLIST UPDATE
    // ==========================================

    /**
     * Downloads and atomically updates the remote blocklist via HTTPS.
     *
     * Invariant:
     * If the download, parsing, validation, or network fails, the LAST KNOWN GOOD DATABASE IS KEPT.
     * The database will never be replaced with an empty or corrupt list.
     */
    suspend fun updateFromRemote(overrideUrl: String? = null): UpdateResult = withContext(Dispatchers.IO) {
        val targetUrl = overrideUrl ?: settingsDao.getSetting(SETTING_KEY_BLOCKLIST_URL) ?: BLOCKLIST_URL_PLACEHOLDER

        // 1. Mandatory HTTPS enforcement
        if (!targetUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Insecure protocol rejected. Remote blocklist updates must use HTTPS only: $targetUrl",
                preservedPreviousDb = true
            )
        }

        // 2. Reject documented placeholder without making network call
        if (targetUrl.contains(".invalid") || targetUrl.contains("placeholder")) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Remote URL is set to documentation placeholder ($targetUrl). Please configure a verified HTTPS feed URL in settings.",
                preservedPreviousDb = true
            )
        }

        Log.d(TAG, "Fetching remote blocklist from: $targetUrl")

        // 3. Fetch remote payload
        val responseBody: String = try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "SafeGuard-Android-Defense/1.0")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Failure(
                        errorMessage = "Remote server returned HTTP error: ${response.code} ${response.message}",
                        preservedPreviousDb = true
                    )
                }
                response.body?.string() ?: ""
            }
        } catch (e: IOException) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Network connection failed (${e.localizedMessage ?: "Offline/Timeout"}). Preserving last known good database.",
                preservedPreviousDb = true
            )
        } catch (e: Exception) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Unexpected request failure: ${e.message}. Preserving last known good database.",
                preservedPreviousDb = true
            )
        }

        if (responseBody.isBlank()) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Remote server returned empty body. Preserving last known good database.",
                preservedPreviousDb = true
            )
        }

        // 4. Validate and parse payload
        val parsed: ParsedBlocklist = try {
            BlocklistJsonParser.parse(responseBody)
        } catch (e: BlocklistValidationException) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Corrupt blocklist rejected: ${e.message}. Preserving last known good database.",
                preservedPreviousDb = true
            )
        } catch (e: Exception) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Invalid JSON structure: ${e.message}. Preserving last known good database.",
                preservedPreviousDb = true
            )
        }

        if (parsed.validDomains.isEmpty()) {
            return@withContext UpdateResult.Failure(
                errorMessage = "Blocklist contains zero valid domains. Preserving last known good database.",
                preservedPreviousDb = true
            )
        }

        // 5. Atomic Update in Room
        try {
            val timestamp = System.currentTimeMillis()
            val remoteEntities = parsed.validDomains.map { domain ->
                BlockedDomain(
                    domain = domain,
                    category = BlockedCategory.ADULT.name,
                    source = "REMOTE",
                    createdAt = timestamp
                )
            }

            // Atomically replace all domains with source "REMOTE"
            blockedDomainDao.replaceDomainsForSource("REMOTE", remoteEntities)

            // Update metadata
            settingsDao.putString(SETTING_KEY_DB_VERSION, parsed.version)
            val updatedDate = parsed.updatedAt.ifBlank { getCurrentIsoTimestamp() }
            settingsDao.putString(SETTING_KEY_LAST_UPDATED, updatedDate)

            // Sync high-performance in-memory filter
            syncMemoryFilter()

            Log.i(TAG, "Successfully updated blocklist to version ${parsed.version} with ${remoteEntities.size} domains.")
            return@withContext UpdateResult.Success(
                version = parsed.version,
                domainCount = remoteEntities.size,
                duplicateCount = parsed.duplicateCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write atomic update into database: ${e.message}", e)
            return@withContext UpdateResult.Failure(
                errorMessage = "Database update transaction failed: ${e.message}. Preserving last known good database.",
                preservedPreviousDb = true
            )
        }
    }

    /**
     * Restores the default built-in blocklist database.
     */
    suspend fun resetToDefaultBuiltin() = withContext(Dispatchers.IO) {
        blockedDomainDao.deleteBySource("BUILTIN")
        blockedDomainDao.deleteBySource("REMOTE")
        seedBuiltinDatabase()
        syncMemoryFilter()
    }

    // ==========================================
    // SETTINGS CONFIGURATION
    // ==========================================

    suspend fun setBlocklistUrl(url: String) = withContext(Dispatchers.IO) {
        settingsDao.putString(SETTING_KEY_BLOCKLIST_URL, url.trim())
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        settingsDao.putString(SETTING_KEY_AUTO_UPDATE, enabled.toString())
    }

    // ==========================================
    // PRIVACY-PRESERVING EVENT LOGGING
    // ==========================================

    /**
     * Logs a safety block event using a cryptographic SHA-256 hash of the domain.
     * To protect user privacy, raw browsing history is NEVER stored or uploaded.
     */
    suspend fun logBlockEvent(category: String, rawDomain: String) = withContext(Dispatchers.IO) {
        val cleanDomain = BlocklistJsonParser.normalizeDomainInput(rawDomain)
        val hash = hashStringSha256(cleanDomain)
        blockEventDao.insert(
            BlockEvent(
                category = category,
                domainHash = hash
            )
        )
    }

    private fun hashStringSha256(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
