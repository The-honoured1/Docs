package com.ceo3.docs.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ceo3.docs.ui.screens.EditorScreen
import com.ceo3.docs.ui.screens.FilesScreen
import com.ceo3.docs.ui.screens.HomeScreen
import com.ceo3.docs.ui.screens.ScannerScreen


sealed class Screen(val route: String) {
    object Home    : Screen("home")
    object Files   : Screen("files")
    object Editor  : Screen("editor/{docId}") {
        fun createRoute(docId: String) = "editor/$docId"
    }
    object Scanner : Screen("scanner")
}

@Composable
fun DocsApp() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val showBottomBar = currentRoute in listOf(
                Screen.Home.route, Screen.Files.route
            )

            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Pill nav
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.onBackground)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavIcon(
                            icon = Icons.Outlined.Layers,
                            label = "Home",
                            selected = currentRoute == Screen.Home.route,
                            onClick = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            }
                        )
                        NavIcon(
                            icon = Icons.Filled.Folder,
                            label = "Files",
                            selected = currentRoute == Screen.Files.route,
                            onClick = {
                                navController.navigate(Screen.Files.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            }
                        )
                    }

                    // Scan FAB
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { navController.navigate(Screen.Scanner.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Scan / Add",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        DocsNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun NavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier
            .size(26.dp)
            .clickable(onClick = onClick)
    )
}

@Composable
fun DocsNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToEditor  = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) },
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) }
            )
        }
        composable(Screen.Files.route) {
            FilesScreen(
                onDocumentClick = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) }
            )
        }
        composable(Screen.Editor.route) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: ""
            EditorScreen(
                documentId     = docId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onScanComplete = { navController.popBackStack() },
                onCancel       = { navController.popBackStack() }
            )
        }
    }
}
