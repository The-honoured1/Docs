package com.ceo3.docs.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.ceo3.docs.ui.screens.ToolsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Tools : Screen("tools")
    object Files : Screen("files")
    object Editor : Screen("editor/{docId}") {
        fun createRoute(docId: String) = "editor/$docId"
    }
    object Scanner : Screen("scanner")
}

@Composable
fun DocsApp() {
    val navController = rememberNavController()
    
    Scaffold(
        containerColor = Color(0xFFF7F8F8),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            val showBottomBar = currentRoute in listOf(Screen.Home.route, Screen.Files.route, Screen.Tools.route)
            
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    // Left Pill (Nav items)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = "Home",
                            tint = if (currentRoute == Screen.Home.route) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp).clickable {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Files",
                            tint = if (currentRoute == Screen.Files.route) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp).clickable {
                                navController.navigate(Screen.Files.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Tools",
                            tint = if (currentRoute == Screen.Tools.route) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp).clickable {
                                navController.navigate(Screen.Tools.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    // Right FAB
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8E0FF))
                            .clickable {
                                navController.navigate(Screen.Scanner.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8E0FF))
                                .clickable { navController.navigate(Screen.Scanner.route) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(28.dp))
                        }
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
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                onNavigateToTools = { navController.navigate(Screen.Tools.route) }
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
