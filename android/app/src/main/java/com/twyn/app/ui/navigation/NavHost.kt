package com.twyn.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twyn.app.ui.welcome.WelcomeScreen
import com.twyn.app.ui.pairing.PairingScreen
import com.twyn.app.ui.pairing.QrScanScreen
import com.twyn.app.ui.chatlist.ChatListScreen
import com.twyn.app.ui.chat.ChatScreen
import com.twyn.app.ui.media.MediaLibraryScreen
import com.twyn.app.ui.location.LocationShareScreen
import com.twyn.app.ui.calling.CallingScreen
import com.twyn.app.ui.settings.SettingsScreen
import com.twyn.app.util.PreferencesManager

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object ChatList : Screen("chat_list")
    object Pairing : Screen("pairing")
    object QrScan : Screen("qr_scan")
    object Chat : Screen("chat/{pairingId}") {
        fun createRoute(pairingId: String) = "chat/$pairingId"
    }
    object MediaLibrary : Screen("media/{pairingId}") {
        fun createRoute(pairingId: String) = "media/$pairingId"
    }
    object LocationShare : Screen("location/{pairingId}") {
        fun createRoute(pairingId: String) = "location/$pairingId"
    }
    object Calling : Screen("calling/{pairingId}?type={type}") {
        fun createRoute(pairingId: String, type: String = "voice") = "calling/$pairingId?type=$type"
    }
    object Settings : Screen("settings")
}

@Composable
fun TwynNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val startDestination = if (prefs.isSignedIn) Screen.ChatList.route else Screen.Welcome.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(200))
        }
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onSignInComplete = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onPairNewContact = { navController.navigate(Screen.Pairing.route) },
                onOpenChat = { pairingId -> navController.navigate(Screen.Chat.createRoute(pairingId)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Pairing.route) {
            PairingScreen(
                onScanQrCode = { navController.navigate(Screen.QrScan.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.QrScan.route) {
            QrScanScreen(
                onPairingComplete = { navController.popBackStack(Screen.ChatList.route, false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("pairingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pairingId = backStackEntry.arguments?.getString("pairingId") ?: return@composable
            ChatScreen(
                pairingId = pairingId,
                onOpenMedia = { navController.navigate(Screen.MediaLibrary.createRoute(pairingId)) },
                onOpenLocation = { navController.navigate(Screen.LocationShare.createRoute(pairingId)) },
                onOpenCall = { type -> navController.navigate(Screen.Calling.createRoute(pairingId, type)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MediaLibrary.route,
            arguments = listOf(navArgument("pairingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pairingId = backStackEntry.arguments?.getString("pairingId") ?: return@composable
            MediaLibraryScreen(pairingId = pairingId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.LocationShare.route,
            arguments = listOf(navArgument("pairingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pairingId = backStackEntry.arguments?.getString("pairingId") ?: return@composable
            LocationShareScreen(pairingId = pairingId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Calling.route,
            arguments = listOf(
                navArgument("pairingId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType; defaultValue = "voice" }
            )
        ) { backStackEntry ->
            val pairingId = backStackEntry.arguments?.getString("pairingId") ?: return@composable
            val callType = backStackEntry.arguments?.getString("type") ?: "voice"
            CallingScreen(
                pairingId = pairingId,
                callType = callType,
                onCallEnd = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
