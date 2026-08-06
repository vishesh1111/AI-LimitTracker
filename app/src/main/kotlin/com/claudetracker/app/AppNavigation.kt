package com.claudetracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.claudetracker.app.data.model.Platform

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        // ── First-time Claude login (legacy entry point) ──
        composable("login") {
            LoginScreen(
                platform = Platform.CLAUDE,
                clearAllCookies = false,
                onLoginSuccess = {
                    navController.navigate("status") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ── Platform picker — choose Claude or Codex ──
        composable("platform_picker") {
            PlatformPickerScreen(
                onPlatformSelected = { platform ->
                    navController.navigate("add_account/${platform.name}")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ── Add account — login for a specific platform ──
        composable("add_account/{platform}") { backStackEntry ->
            val platformName = backStackEntry.arguments?.getString("platform") ?: "CLAUDE"
            val platform = remember(platformName) {
                try { Platform.valueOf(platformName) } catch (_: Exception) { Platform.CLAUDE }
            }

            LoginScreen(
                platform = platform,
                clearAllCookies = true,
                onLoginSuccess = {
                    navController.navigate("status") {
                        popUpTo("status") { inclusive = false }
                    }
                }
            )
        }

        // ── Status dashboard ──
        composable("status") {
            StatusScreen(
                onLogout = {
                    navController.navigate("platform_picker") {
                        popUpTo("status") { inclusive = true }
                    }
                },
                onAddAccount = {
                    navController.navigate("platform_picker")
                }
            )
        }
    }
}
