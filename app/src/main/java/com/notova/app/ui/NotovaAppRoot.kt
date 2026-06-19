package com.notova.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notova.feature.notes.NoteDetailScreen
import com.notova.feature.notes.NotesListScreen
import com.notova.feature.record.RecordScreen

private const val ARG_RECORDING_ID = "recordingId"

private sealed class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Record : TopDestination("record", "Record", Icons.Filled.Mic)

    data object Notes : TopDestination("notes", "Notes", Icons.AutoMirrored.Filled.List)

    data object Settings : TopDestination("settings", "Settings", Icons.Filled.Settings)
}

private val topDestinations = listOf(TopDestination.Record, TopDestination.Notes, TopDestination.Settings)

@Composable
fun NotovaAppRoot() {
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
            composable(TopDestination.Settings.route) {
                SettingsScreen()
            }
            composable("note/{$ARG_RECORDING_ID}") { entry ->
                val id = entry.arguments?.getString(ARG_RECORDING_ID).orEmpty()
                NoteDetailScreen(recordingId = id)
            }
        }
    }
}
