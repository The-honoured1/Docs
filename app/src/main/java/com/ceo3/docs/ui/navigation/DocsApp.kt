package com.ceo3.docs.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ceo3.docs.ui.screens.EditorScreen
import com.ceo3.docs.ui.screens.FilesScreen
import com.ceo3.docs.ui.screens.HomeScreen
import com.ceo3.docs.ui.screens.ScannerScreen
import com.ceo3.docs.ui.screens.ToolsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Create : Screen("create", "Create", Icons.Filled.AddCircle)
    object Tools : Screen("tools", "Tools", Icons.Filled.Build)
    object Files : Screen("files", "Files", Icons.Filled.Folder)
    
    // Hidden from bottom bar
    object Editor : Screen("editor/{docId}", "Editor", Icons.Filled.Home) {
        fun createRoute(docId: String) = "editor/$docId"
    }
    object Scanner : Screen("scanner", "Scanner", Icons.Filled.Home)
}

val BottomNavItems = listOf(
    Screen.Home,
    Screen.Create,
    Screen.Tools,
    Screen.Files
)

@Composable
fun DocsApp() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            // Only show bottom bar on main screens
            if (currentRoute in BottomNavItems.map { it.route }) {
                NavigationBar {
                    BottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
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
fun DocsNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToEditor = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) },
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) }
            )
        }
        composable(Screen.Create.route) {
            // Reusing HomeScreen or could be a separate dialog/menu
            // For now, let's just make Create open the Scanner directly as an example,
            // or show a screen with options.
            ScannerScreen(
                onScanComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Screen.Tools.route) {
            ToolsScreen(
                onToolSelected = { toolId -> /* Navigate to specific tool wizard */ }
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
                documentId = docId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onScanComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}
