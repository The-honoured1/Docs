package com.ceo3.docs.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ceo3.docs.data.settings.SettingsManager
import com.ceo3.docs.ui.screens.DonateScreen
import com.ceo3.docs.ui.screens.EditorScreen
import com.ceo3.docs.ui.screens.FilesScreen
import com.ceo3.docs.ui.screens.HomeScreen
import com.ceo3.docs.ui.screens.ScannerScreen
import com.ceo3.docs.ui.screens.SharedScreen
import com.ceo3.docs.ui.screens.SettingsScreen
import com.ceo3.docs.ui.screens.AccountSettingsScreen
import com.ceo3.docs.ui.screens.CloudSyncBackupScreen
import com.ceo3.docs.ui.screens.ThemeSettingsScreen
import com.ceo3.docs.ui.screens.SecurityPasscodeScreen
import com.ceo3.docs.ui.screens.HelpSupportScreen
import com.ceo3.docs.ui.screens.AboutDocsScreen
import com.ceo3.docs.ui.screens.FAQScreen
import com.ceo3.docs.ui.screens.PrivacyPolicyScreen
import com.ceo3.docs.ui.screens.TermsOfServiceScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home    : Screen("home")
    object Files   : Screen("files")
    object Shared  : Screen("shared")
    object Settings: Screen("settings")
    object Editor  : Screen("editor/{docId}") {
        fun createRoute(docId: String): String {
            val encoded = URLEncoder.encode(docId, StandardCharsets.UTF_8.toString())
            return "editor/$encoded"
        }
    }
    object Scanner : Screen("scanner")
    object Donate  : Screen("donate")
    object Tools    : Screen("tools")
    object AccountSettings : Screen("settings/account")
    object CloudSyncBackup : Screen("settings/cloud")
    object ThemeSettings   : Screen("settings/theme")
    object SecurityPasscode: Screen("settings/security")
    object HelpSupport     : Screen("settings/help")
    object AboutDocs       : Screen("settings/about")
    object FAQ             : Screen("settings/help/faq")
    object PrivacyPolicy   : Screen("settings/about/privacy")
    object TermsOfService  : Screen("settings/about/terms")
}

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Home.route,    "Home",     Icons.Filled.Home,     Icons.Outlined.Home),
    NavItem(Screen.Files.route,   "Files",    Icons.Filled.Folder,   Icons.Outlined.Folder),
    NavItem(Screen.Tools.route,   "Tools",    Icons.Filled.Build,    Icons.Filled.Build),
    NavItem(Screen.Settings.route,"Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun DocsApp(settingsManager: SettingsManager? = null) {
    val navController = rememberNavController()

    // Passcode logic
    var isUnlocked by remember { mutableStateOf(false) }
    val requirePasscode = settingsManager?.requirePasscodeFlow?.collectAsState(initial = false)?.value ?: false
    val savedPasscode = settingsManager?.passcodeFlow?.collectAsState(initial = "")?.value ?: ""
    
    if (requirePasscode && !isUnlocked) {
        var enteredPin by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Settings, contentDescription = "Lock", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("App is locked", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))
                
                androidx.compose.foundation.text.BasicTextField(
                    value = enteredPin,
                    onValueChange = { 
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            enteredPin = it
                            isError = false
                            if (it.length == 4) {
                                if (it == savedPasscode || savedPasscode.isEmpty()) {
                                    isUnlocked = true
                                } else {
                                    isError = true
                                    enteredPin = ""
                                }
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { index ->
                            val isFilled = index < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
                
                if (isError) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in navItems.map { it.route }

        DocsNavHost(
            navController = navController,
            settingsManager = settingsManager,
            modifier = Modifier.fillMaxSize()
        )

        if (showBottomBar) {
            // Standard Bottom Navigation Bar matching the mockup
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Subtle divider at the top of bottom nav
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(64.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (!selected) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = false }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Mockup Floating Action Button (+) floating above bottom navigation on the right side
            FloatingActionButton(
                onClick = {
                    // Navigate directly to blank new document editor
                    navController.navigate(Screen.Editor.createRoute("new_blank_document"))
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp, end = 20.dp)
                    .size(56.dp)
                    .shadow(8.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New Document",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun DocsNavHost(navController: NavHostController, settingsManager: SettingsManager? = null, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(
            route = Screen.Home.route,
            enterTransition = { fadeIn(animationSpec = tween(120)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) }
        ) {
            HomeScreen(
                onNavigateToEditor    = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) },
                onNavigateToScanner   = { navController.navigate(Screen.Scanner.route) },
                onNavigateToDonate    = { navController.navigate(Screen.Donate.route) },
                onNavigateToTemplates = { navController.navigate(Screen.Editor.createRoute("new_blank_document")) },
                onNavigateToTools     = { navController.navigate(Screen.Tools.route) }
            )
        }
        composable(
            route = Screen.Files.route,
            enterTransition = { fadeIn(animationSpec = tween(120)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) }
        ) {
            FilesScreen(
                onDocumentClick = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) }
            )
        }
        composable(
            route = Screen.Shared.route,
            enterTransition = { fadeIn(animationSpec = tween(120)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) }
        ) {
            SharedScreen(
                onDocumentClick = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) }
            )
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = { fadeIn(animationSpec = tween(120)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) }
        ) {
            SettingsScreen(
                onNavigateToDonate = { navController.navigate(Screen.Donate.route) },
                onNavigateToTools  = { navController.navigate(Screen.Tools.route) },
                onNavigateToAccount = { navController.navigate(Screen.AccountSettings.route) },
                onNavigateToCloudSync = { navController.navigate(Screen.CloudSyncBackup.route) },
                onNavigateToTheme = { navController.navigate(Screen.ThemeSettings.route) },
                onNavigateToSecurity = { navController.navigate(Screen.SecurityPasscode.route) },
                onNavigateToHelp = { navController.navigate(Screen.HelpSupport.route) },
                onNavigateToAbout = { navController.navigate(Screen.AboutDocs.route) }
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
        composable(
            route = Screen.Tools.route,
            enterTransition = { fadeIn(animationSpec = tween(120)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) }
        ) {
            ToolsScreen(
                onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                onNavigateToEditor  = { docId -> navController.navigate(Screen.Editor.createRoute(docId)) },
                onNavigateToDonate  = { navController.navigate(Screen.Donate.route) }
            )
        }
        composable(Screen.AccountSettings.route) {
            AccountSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                settingsManager = settingsManager
            )
        }
        composable(Screen.CloudSyncBackup.route) {
            CloudSyncBackupScreen(
                onNavigateBack = { navController.popBackStack() },
                settingsManager = settingsManager
            )
        }
        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                settingsManager = settingsManager
            )
        }
        composable(Screen.SecurityPasscode.route) {
            SecurityPasscodeScreen(
                onNavigateBack = { navController.popBackStack() },
                settingsManager = settingsManager
            )
        }
        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFAQ = { navController.navigate(Screen.FAQ.route) }
            )
        }
        composable(Screen.AboutDocs.route) {
            AboutDocsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                onNavigateToTermsOfService = { navController.navigate(Screen.TermsOfService.route) }
            )
        }
        composable(Screen.FAQ.route) {
            FAQScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.TermsOfService.route) {
            TermsOfServiceScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
