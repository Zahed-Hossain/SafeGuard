package com.safeguard.ui.navigation

sealed class SafeGuardDestination(val route: String) {
    object Onboarding : SafeGuardDestination("onboarding")
    object Home : SafeGuardDestination("home")
    object Settings : SafeGuardDestination("settings")
    object Statistics : SafeGuardDestination("statistics")
    object Privacy : SafeGuardDestination("privacy")
    object Blocklist : SafeGuardDestination("blocklist")
    object Whitelist : SafeGuardDestination("whitelist")
    object DatabaseUpdate : SafeGuardDestination("database_update")
    object BlockedWebsite : SafeGuardDestination("blocked_website?domain={domain}&category={category}") {
        fun createRoute(domain: String = "example-inappropriate-site.com", category: String = "Adult Content"): String {
            return "blocked_website?domain=$domain&category=$category"
        }
    }
}
