package com.safeguard.vpn

enum class VpnStatus {
    ACTIVE,
    INACTIVE,
    ERROR
}

/**
 * Granular error classification for VPN connection and permission states.
 */
enum class VpnErrorCode {
    NONE,
    PERMISSION_DENIED,
    ALREADY_ACTIVE,
    SERVICE_STOPPED,
    ANOTHER_VPN_ACTIVE,
    NETWORK_UNAVAILABLE,
    CONNECTION_FAILED
}

data class VpnState(
    val status: VpnStatus = VpnStatus.INACTIVE,
    val errorMessage: String? = null,
    val errorCode: VpnErrorCode = VpnErrorCode.NONE,
    val connectedAtMillis: Long = 0L,
    val bytesFiltered: Long = 0L,
    val dnsQueriesChecked: Long = 0L
)
