package com.safeguard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safeguard.ui.blocked.BlockedWebsiteScreen
import com.safeguard.ui.blocklist.BlocklistScreen
import com.safeguard.ui.blocklist.BlocklistViewModel
import com.safeguard.ui.database.DatabaseUpdateScreen
import com.safeguard.ui.database.DatabaseUpdateViewModel
import com.safeguard.ui.home.HomeScreen
import com.safeguard.ui.home.HomeViewModel
import com.safeguard.ui.onboarding.OnboardingScreen
import com.safeguard.ui.privacy.PrivacyScreen
import com.safeguard.ui.privacy.PrivacyViewModel
import com.safeguard.ui.settings.SettingsScreen
import com.safeguard.ui.settings.SettingsViewModel
import com.safeguard.ui.statistics.StatisticsScreen
import com.safeguard.ui.statistics.StatisticsViewModel
import com.safeguard.ui.whitelist.WhitelistScreen
import com.safeguard.ui.whitelist.WhitelistViewModel

@Composable
fun SafeGuardNavHost(
    isOnboardingCompleted: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (isOnboardingCompleted) {
        SafeGuardDestination.Home.route
    } else {
        SafeGuardDestination.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(SafeGuardDestination.Onboarding.route) {
            OnboardingScreen(
                onOnboardingFinished = {
                    navController.navigate(SafeGuardDestination.Home.route) {
                        popUpTo(SafeGuardDestination.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(SafeGuardDestination.Home.route) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToStatistics = {
                    navController.navigate(SafeGuardDestination.Statistics.route)
                },
                onNavigateToSettings = {
                    navController.navigate(SafeGuardDestination.Settings.route)
                },
                onNavigateToBlockedPreview = {
                    navController.navigate(SafeGuardDestination.BlockedWebsite.createRoute())
                }
            )
        }

        composable(SafeGuardDestination.Statistics.route) {
            val statsViewModel: StatisticsViewModel = viewModel()
            StatisticsScreen(
                viewModel = statsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(SafeGuardDestination.Privacy.route) {
            val privacyViewModel: PrivacyViewModel = viewModel()
            PrivacyScreen(
                viewModel = privacyViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(SafeGuardDestination.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBlocklist = {
                    navController.navigate(SafeGuardDestination.Blocklist.route)
                },
                onNavigateToWhitelist = {
                    navController.navigate(SafeGuardDestination.Whitelist.route)
                },
                onNavigateToPrivacy = {
                    navController.navigate(SafeGuardDestination.Privacy.route)
                },
                onNavigateToDatabaseUpdate = {
                    navController.navigate(SafeGuardDestination.DatabaseUpdate.route)
                }
            )
        }

        composable(SafeGuardDestination.Blocklist.route) {
            val blocklistViewModel: BlocklistViewModel = viewModel()
            BlocklistScreen(
                viewModel = blocklistViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(SafeGuardDestination.Whitelist.route) {
            val whitelistViewModel: WhitelistViewModel = viewModel()
            WhitelistScreen(
                viewModel = whitelistViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(SafeGuardDestination.DatabaseUpdate.route) {
            val dbViewModel: DatabaseUpdateViewModel = viewModel()
            DatabaseUpdateScreen(
                viewModel = dbViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = SafeGuardDestination.BlockedWebsite.route,
            arguments = listOf(
                navArgument("domain") {
                    type = NavType.StringType
                    defaultValue = "inappropriate-adult-site.xxx"
                },
                navArgument("category") {
                    type = NavType.StringType
                    defaultValue = "Adult Content"
                }
            )
        ) { backStackEntry ->
            val domain = backStackEntry.arguments?.getString("domain") ?: "inappropriate-adult-site.xxx"
            val category = backStackEntry.arguments?.getString("category") ?: "Adult Content"
            BlockedWebsiteScreen(
                domain = domain,
                category = category,
                onReturnToHome = {
                    navController.navigate(SafeGuardDestination.Home.route) {
                        popUpTo(SafeGuardDestination.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
