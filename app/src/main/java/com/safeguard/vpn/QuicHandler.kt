package com.safeguard.vpn

import android.util.Log
import com.safeguard.blocklist.BlocklistManager
import com.safeguard.blocklist.DomainMatcher

/**
 * QUIC / HTTP/3 (UDP Port 443) Network Protection Handler.
 *
 * Modern browsers (Chrome, Firefox, Edge, Brave, Opera, Samsung Internet)
 * may attempt to use HTTP/3 over QUIC (UDP Port 443).
 *
 * SafeGuard Architecture for QUIC:
 * 1. Inspects UDP port 443 traffic for QUIC signatures.
 * 2. Attempts to extract cleartext SNI from initial CRYPTO/TLS frames where possible.
 * 3. Drops or rejects QUIC packets for blocked domains or when enforcing strict
 *    DNS/TCP filtering.
 * 4. RFC 9000 Graceful Fallback: When QUIC UDP packets are dropped or unanswered,
 *    all compliant browsers immediately and seamlessly fall back to standard HTTP/2
 *    or HTTP/1.1 over TCP port 443, where SafeGuard's DNS and SNI filtering operates.
 * 5. Normal Internet traffic on allowed domains is unaffected.
 */
object QuicHandler {

    private const val TAG = "QuicHandler"
    const val PORT_QUIC = 443

    /**
     * Determines whether a packet on UDP is targeted at the QUIC/HTTP3 port.
     */
    fun isQuicPort(port: Int): Boolean {
        return port == PORT_QUIC
    }

    /**
     * Checks if a UDP packet is a QUIC Long Header packet (used during handshake).
     * RFC 9000: Long header packet starts with the header form bit (0x80) set.
     */
    fun isQuicLongHeader(data: ByteArray, offset: Int, length: Int): Boolean {
        if (length < 1) return false
        val firstByte = data[offset].toInt() and 0xFF
        return (firstByte and 0x80) != 0
    }

    /**
     * Inspects QUIC Initial packets for embedded TLS 1.3 ClientHello and attempts
     * to extract SNI.
     */
    fun extractQuicSni(data: ByteArray, offset: Int, length: Int): String? {
        if (length < 20 || !isQuicLongHeader(data, offset, length)) return null

        try {
            // Search for TLS Handshake byte (0x16) and ClientHello (0x01) within the QUIC Initial payload
            val searchLimit = minOf(offset + length - 40, offset + 300)
            for (i in offset + 6 until searchLimit) {
                if (data[i] == 0x16.toByte() && data[i + 5] == 0x01.toByte()) {
                    val sni = TlsSniExtractor.extractTlsSni(data, i, length - (i - offset))
                    if (sni != null) return sni
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "QUIC SNI inspection skipped: ${e.message}")
        }
        return null
    }

    /**
     * Evaluates whether a QUIC flow should be blocked.
     */
    fun shouldBlockQuic(domain: String?, blocklistManager: BlocklistManager): Boolean {
        if (domain == null) return false
        val normalized = DomainMatcher.normalize(domain)
        val check = blocklistManager.checkDomain(normalized)
        return check.isBlocked
    }
}
