package com.ceo3.docs.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.ceo3.docs.ui.screens.DonateScreen
import com.ceo3.docs.ui.screens.EditorScreen
import com.ceo3.docs.ui.screens.FilesScreen
import com.ceo3.docs.ui.screens.HomeScreen
import com.ceo3.docs.ui.screens.ScannerScreen
import com.ceo3.docs.ui.screens.ToolsScreen
import com.ceo3.docs.ui.theme.BrandAccent
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

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Home.route,    "Home",    Icons.Filled.Home,            Icons.Outlined.Home),
    NavItem(Screen.Tools.route,   "Tools",   Icons.Filled.GridView,        Icons.Outlined.GridView),
    NavItem(Screen.Scanner.route, "Scan",    Icons.Filled.DocumentScanner, Icons.Outlined.QrCodeScanner),
    NavItem(Screen.Files.route,   "Files",   Icons.Filled.Folder,          Icons.Outlined.FolderOpen)
)

@Composable
fun DocsApp() {
    val navController = rememberNavController()

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
            modifier = Modifier.fillMaxSize()
        )

        if (showBottomBar) {
            PillNavigationBar(
                currentRoute = currentRoute,
                navController = navController,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
            )
        }
    }
}

private @Composable
fun PillNavigationBar(
    currentRoute: String?,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(50),
                ambientColor = BrandAccent.copy(alpha = 0.25f),
                spotColor = BrandAccent.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                PillNavItem(
                    item = item,
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

private @Composable
fun PillNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) BrandAccent else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "navBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 120),
        label = "navContent"
    )
    val itemWidth by animateDpAsState(
        targetValue = if (selected) 110.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 1200f),
        label = "navWidth"
    )

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(44.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            if (selected) {
                Text(
                    text = item.label,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DocsNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(120)) },
        exitTransition = { fadeOut(animationSpec = tween(120)) },
        popEnterTransition = { fadeIn(animationSpec = tween(120)) },
        popExitTransition = { fadeOut(animationSpec = tween(120)) }
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
