package com.ceo3.docs.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ceo3.docs.ui.screens.DonateScreen
import com.ceo3.docs.ui.screens.EditorScreen
import com.ceo3.docs.ui.screens.FilesScreen
import com.ceo3.docs.ui.screens.HomeScreen
import com.ceo3.docs.ui.screens.ScannerScreen
import com.ceo3.docs.ui.screens.ToolsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home    : Screen("home")
    object Tools   : Screen("tools")
    object Files   : Screen("files")
    object Editor  : Screen("editor/{docId}") {
        fun createRoute(docId: String): String {
            val encoded = URLEncoder.encode(docId, StandardCharsets.UTF_8.toString())
            return "editor/$encoded"
        }
    }
    object Scanner : Screen("scanner")
    object Donate  : Screen("donate")
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
                Screen.Home.route, Screen.Tools.route, Screen.Files.route, Screen.Scanner.route
            )

            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Screen.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Tools.route,
                        onClick = {
                            navController.navigate(Screen.Tools.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Screen.Tools.route) Icons.Filled.GridOn else Icons.Outlined.GridOn,
                                contentDescription = "Tools"
                            )
                        },
                        label = { Text("Tools") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Scanner.route,
                        onClick = {
                            navController.navigate(Screen.Scanner.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = "Scan"
                            )
                        },
                        label = { Text("Scan") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Files.route,
                        onClick = {
                            navController.navigate(Screen.Files.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Screen.Files.route) Icons.Filled.Folder else Icons.Outlined.Folder,
                                contentDescription = "Files"
                            )
                        },
                        label = { Text("Files") }
                    )
                }
            }
        }
    ) { innerPadding ->
        DocsNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
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
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                onNavigateToDonate  = { navController.navigate(Screen.Donate.route) }
            )
        }
        composable(Screen.Tools.route) {
            ToolsScreen(
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                onNavigateToEditor  = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) },
                onNavigateToDonate  = { navController.navigate(Screen.Donate.route) }
            )
        }
        composable(Screen.Files.route) {
            FilesScreen(
                onDocumentClick = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) }
            )
        }
        composable(Screen.Editor.route) { backStackEntry ->
            val encodedDocId = backStackEntry.arguments?.getString("docId") ?: ""
            val docId = try {
                URLDecoder.decode(encodedDocId, StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                encodedDocId
            }
            EditorScreen(
                documentId     = docId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onScanComplete = { navController.navigate(Screen.Home.route) { popUpTo(0) } },
                onCancel       = { navController.popBackStack() }
            )
        }
        composable(Screen.Donate.route) {
            DonateScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

