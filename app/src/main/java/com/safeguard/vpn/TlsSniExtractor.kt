package com.safeguard.vpn

import android.util.Log

/**
 * Privacy-Preserving TLS Server Name Indication (SNI) Extractor.
 *
 * Adheres strictly to the HTTPS PRIVACY RULE:
 * - NO TLS decryption or MITM
 * - NO certificate installation
 * - NO inspection of cookies, headers, passwords, or page bodies
 *
 * Extracts ONLY the cleartext SNI extension from the initial TLS ClientHello
 * (RFC 6066) to verify if the accessed HTTPS domain is on the adult blocklist.
 */
object TlsSniExtractor {

    private const val TAG = "TlsSniExtractor"
    private const val CONTENT_TYPE_HANDSHAKE = 0x16
    private const val HANDSHAKE_TYPE_CLIENT_HELLO = 0x01
    private const val EXTENSION_SERVER_NAME = 0x0000
    private const val NAME_TYPE_HOST_NAME = 0x00

    /**
     * Attempts to extract the server name (SNI) from a TCP payload containing a TLS ClientHello.
     * Returns the host name if successfully parsed, or null otherwise.
     */
    fun extractSni(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): String? {
        return extractTlsSni(data, offset, length)
    }

    fun extractTlsSni(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): String? {
        if (length < 44) return null
        val end = offset + length

        try {
            // Check TLS Record Header
            val contentType = data[offset].toInt() and 0xFF
            if (contentType != CONTENT_TYPE_HANDSHAKE) return null

            // TLS record length (bytes 3..4)
            val recordLength = ((data[offset + 3].toInt() and 0xFF) shl 8) or
                    (data[offset + 4].toInt() and 0xFF)
            if (recordLength < 38) return null

            var pos = offset + 5
            if (pos >= end) return null

            // Handshake Type
            val handshakeType = data[pos].toInt() and 0xFF
            if (handshakeType != HANDSHAKE_TYPE_CLIENT_HELLO) return null

            // Skip Handshake Header (1 byte type + 3 bytes length)
            pos += 4
            // Skip Client Version (2 bytes) + Random (32 bytes)
            pos += 34
            if (pos >= end) return null

            // Session ID
            val sessionIdLen = data[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos + 2 > end) return null

            // Cipher Suites
            val cipherSuitesLen = ((data[pos].toInt() and 0xFF) shl 8) or
                    (data[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherSuitesLen
            if (pos + 1 > end) return null

            // Compression Methods
            val compressionMethodsLen = data[pos].toInt() and 0xFF
            pos += 1 + compressionMethodsLen
            if (pos + 2 > end) return null

            // Extensions Length
            val extensionsLen = ((data[pos].toInt() and 0xFF) shl 8) or
                    (data[pos + 1].toInt() and 0xFF)
            pos += 2
            val extensionsEnd = minOf(pos + extensionsLen, end)

            // Iterate over extensions
            while (pos + 4 <= extensionsEnd) {
                val extType = ((data[pos].toInt() and 0xFF) shl 8) or
                        (data[pos + 1].toInt() and 0xFF)
                val extLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or
                        (data[pos + 3].toInt() and 0xFF)
                pos += 4

                if (pos + extLen > extensionsEnd) break

                if (extType == EXTENSION_SERVER_NAME) {
                    // Parse Server Name extension
                    var sniPos = pos
                    if (sniPos + 2 > pos + extLen) break
                    val serverNameListLen = ((data[sniPos].toInt() and 0xFF) shl 8) or
                            (data[sniPos + 1].toInt() and 0xFF)
                    sniPos += 2

                    if (sniPos + 3 <= pos + extLen) {
                        val nameType = data[sniPos].toInt() and 0xFF
                        val nameLen = ((data[sniPos + 1].toInt() and 0xFF) shl 8) or
                                (data[sniPos + 2].toInt() and 0xFF)
                        sniPos += 3

                        if (nameType == NAME_TYPE_HOST_NAME && sniPos + nameLen <= pos + extLen) {
                            return String(data, sniPos, nameLen, Charsets.US_ASCII)
                        }
                    }
                }

                pos += extLen
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse TLS SNI: ${e.message}")
        }

        return null
    }
}
