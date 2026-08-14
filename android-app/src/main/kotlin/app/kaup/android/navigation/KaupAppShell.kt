package app.kaup.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import app.kaup.feature.auth.ui.LockScreen
import app.kaup.feature.auth.ui.hotp.HotpProvisioningRoute
import app.kaup.feature.auth.ui.hotp.OverrideCodeGenerationScreen
import app.kaup.feature.auth.ui.onboarding.OnboardingScreen
import app.kaup.core.ui.auth.LocalPermissions

@Composable
fun KaupAppShell(
    shellViewModel: ShellViewModel = hiltViewModel()
) {
    val rootNavController = rememberNavController()
    val startDest by shellViewModel.startDestination.collectAsState()
    val currentUser by shellViewModel.currentUser.collectAsState()
    val permissions by shellViewModel.permissions.collectAsState()

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            val route = rootNavController.currentDestination?.route
            if (route != "lock_screen" && route != "onboarding" && route != null) {
                rootNavController.navigate("lock_screen") {
                    popUpTo(0)
                }
            }
        }
    }

    CompositionLocalProvider(LocalPermissions provides permissions) {
        if (startDest == null) {
            // Loading state while querying Room
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            NavHost(navController = rootNavController, startDestination = startDest!!) {
                composable("onboarding") {
                    OnboardingScreen(
                        onOnboardingComplete = {
                            rootNavController.navigate("lock_screen") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }
                composable("lock_screen") {
                    LockScreen(
                        // Reached only after PinAuthenticator has verified the
                        // PIN and SessionManager holds the session.
                        onUserSelected = { userId ->
                            rootNavController.navigate("main") {
                                popUpTo("lock_screen") { inclusive = true }
                            }
                        }
                    )
                }
                composable("main") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        shellViewModel.onUserInteraction()
                                    }
                                }
                            }
                    ) {
                        MainShell()
                    }
                }
            }
        }
    }
}

@Composable
fun MainShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "pos"

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentRoute == "pos",
                onClick = { navController.navigate("pos") },
                icon = { Text("P") },
                label = { Text("Register") }
            )
            item(
                selected = currentRoute == "inventory",
                onClick = { navController.navigate("inventory") },
                icon = { Text("I") },
                label = { Text("Inventory") }
            )
            item(
                selected = currentRoute == "reports",
                onClick = { navController.navigate("reports") },
                icon = { Text("R") },
                label = { Text("Reports") }
            )
            item(
                selected = currentRoute == "settings",
                onClick = { navController.navigate("settings") },
                icon = { Text("S") },
                label = { Text("Settings") }
            )
        }
    ) {
        NavHost(navController = navController, startDestination = "pos") {
            composable("pos") { DummyScreen("POS Register Placeholder") }
            composable("inventory") { DummyScreen("Inventory Placeholder") }
            composable("reports") { DummyScreen("Reports Placeholder") }
            composable("settings") {
                SecuritySettingsScreen(
                    onProvisionHotp = { navController.navigate("hotp_provisioning") },
                    onGenerateOverrideCode = { navController.navigate("override_code") }
                )
            }
            // Both of these screens existed with nothing routing to them, so
            // manager authorization could not be set up or used at all.
            composable("hotp_provisioning") {
                HotpProvisioningRoute(
                    onComplete = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable("override_code") {
                OverrideCodeGenerationScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun DummyScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}
