package com.notova.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notova.app.ui.auth.AuthRoute
import com.notova.app.ui.auth.AuthViewModel
import com.notova.app.ui.auth.SignInScreen
import com.notova.app.ui.export.NoteDetailWithExport
import com.notova.app.ui.integrations.IntegrationsScreen
import com.notova.feature.notes.NotesListScreen
import com.notova.feature.record.RecordScreen

private const val ARG_RECORDING_ID = "recordingId"

private sealed class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Record : TopDestination("record", "Record", Icons.Filled.Mic)

    data object Notes : TopDestination("notes", "Notes", Icons.AutoMirrored.Filled.List)

    data object Integrations : TopDestination("integrations", "Connect", Icons.Filled.Hub)

    data object Settings : TopDestination("settings", "Settings", Icons.Filled.Settings)
}

private val topDestinations =
    listOf(
        TopDestination.Record,
        TopDestination.Notes,
        TopDestination.Integrations,
        TopDestination.Settings,
    )

/**
 * App root. Gates on the auth route: while the token store is being read it shows a spinner; signed
 * out it shows [SignInScreen]; signed in it shows the tabbed main app. Routing advances reactively
 * when the token store changes (sign-in / sign-out).
 */
@Composable
fun NotovaAppRoot(authViewModel: AuthViewModel = hiltViewModel()) {
    val route by authViewModel.route.collectAsStateWithLifecycle()

    when (route) {
        AuthRoute.LOADING ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        AuthRoute.SIGNED_OUT -> SignInScreen()
        AuthRoute.SIGNED_IN -> MainAppScaffold(onSignOut = authViewModel::signOut)
    }
}

@Composable
private fun MainAppScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                topDestinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Record.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDestination.Record.route) {
                RecordScreen()
            }
            composable(TopDestination.Notes.route) {
                NotesListScreen(onOpenNote = { id -> navController.navigate("note/$id") })
            }
            composable(TopDestination.Integrations.route) {
                IntegrationsScreen()
            }
            composable(TopDestination.Settings.route) {
                SettingsScreen(onSignOut = onSignOut)
            }
            composable("note/{$ARG_RECORDING_ID}") { entry ->
                val id = entry.arguments?.getString(ARG_RECORDING_ID).orEmpty()
                NoteDetailWithExport(recordingId = id)
            }
        }
    }
}
