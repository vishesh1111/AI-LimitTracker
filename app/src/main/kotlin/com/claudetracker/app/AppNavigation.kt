package com.claudetracker.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                clearAllCookies = false, // First login: just clear session cookie
                onLoginSuccess = {
                    navController.navigate("status") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("add_account") {
            LoginScreen(
                clearAllCookies = true, // Adding new account: clear ALL cookies for fresh login
                onLoginSuccess = {
                    // Go back to status after adding account
                    navController.navigate("status") {
                        popUpTo("status") { inclusive = false }
                    }
                }
            )
        }
        composable("status") {
            StatusScreen(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("status") { inclusive = true }
                    }
                },
                onAddAccount = {
                    navController.navigate("add_account")
                }
            )
        }
    }
}
