package com.heartmonitor.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartmonitor.app.presentation.screens.analysis.AnalysisScreen
import com.heartmonitor.app.presentation.screens.home.HomeScreen
import com.heartmonitor.app.presentation.screens.profile.ProfileScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Analysis : Screen("analysis/{recordingId}") {
        fun createRoute(recordingId: Long) = "analysis/$recordingId"
    }
    data object Profile : Screen("profile")
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem(
        route = Screen.Home.route,
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    data object Profile : BottomNavItem(
        route = Screen.Profile.route,
        title = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartMonitorNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Profile
    )
    
    // Determine if we should show bottom nav
    val showBottomNav = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { 
                            it.route == item.route 
                        } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAnalysis = { recordingId ->
                        navController.navigate(Screen.Analysis.createRoute(recordingId))
                    }
                )
            }
            
            composable(
                route = Screen.Analysis.route,
                arguments = listOf(
                    navArgument("recordingId") { type = NavType.LongType }
                )
            ) {
                AnalysisScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToAnalysis = { recordingId ->
                        navController.navigate(Screen.Analysis.createRoute(recordingId))
                    }
                )
            }
        }
    }
}
