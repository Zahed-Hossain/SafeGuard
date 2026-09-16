package com.safeguard.vpn

import com.safeguard.blocklist.DomainMatcher
import java.util.Locale

/**
 * Encrypted DNS (DoT / DoH / Secure DNS) Mitigation Architecture.
 *
 * Modern Android browsers (Chrome, Edge, Firefox, Brave, Opera, Samsung Internet)
 * may attempt to use encrypted DNS protocols:
 * 1. DNS-over-TLS (DoT): Runs on TCP/UDP Port 853.
 * 2. DNS-over-HTTPS (DoH): Runs over HTTPS (Port 443) to known resolver endpoints.
 *
 * SafeGuard mitigates DNS bypass while preserving normal Internet connectivity:
 * - Drops/rejects Port 853 traffic so Android and browsers fall back to the system/VPN DNS.
 * - Sinks known DoH bootstrap domains during initial browser DNS resolution so browsers
 *   cannot establish DoH tunnels and seamlessly fall back to local VPN filtering.
 *
 * TECHNICAL LIMITATION NOTE:
 * SafeGuard does NOT claim 100% bypass prevention against all encrypted DNS mechanisms.
 * If a user or custom browser profile manually hardcodes an unlisted third-party DoH IP
 * or custom encrypted proxy directly in browser internals, local DNS filtering cannot
 * inspect the encrypted tunnel without full HTTPS/TLS decryption (which SafeGuard strictly
 * avoids to protect user privacy).
 */
object EncryptedDnsProtector {

    const val PORT_DOT = 853

    /**
     * Known DNS-over-HTTPS (DoH) bootstrap and resolver domains.
     * When browsers initiate Secure DNS in automatic/opportunistic mode, they first
     * attempt to resolve these domains via system DNS.
     */
    val KNOWN_DOH_PROVIDERS = setOf(
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "one.one.one.one",
        "dns.google",
        "dns.google.com",
        "dns.quad9.net",
        "quad9.net",
        "doh.opendns.com",
        "dns.adguard.com",
        "dns.adguard-dns.com",
        "doh.cleanbrowsing.org",
        "dns.nextdns.io"
    )

    /**
     * Checks if a network port is used for DNS-over-TLS (DoT).
     */
    fun isDoTPort(port: Int): Boolean {
        return port == PORT_DOT
    }

    /**
     * Checks if a domain is a known DoH bootstrap resolver domain.
     */
    fun isDoHBootstrapDomain(domain: String): Boolean {
        val normalized = DomainMatcher.normalize(domain).lowercase(Locale.ROOT)
        val clean = DomainMatcher.stripWww(normalized)

        if (KNOWN_DOH_PROVIDERS.contains(clean)) return true

        return KNOWN_DOH_PROVIDERS.any { provider ->
            clean == provider || clean.endsWith(".$provider")
        }
    }
}
