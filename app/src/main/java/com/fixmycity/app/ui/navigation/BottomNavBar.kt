package com.fixmycity.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmycity.app.ui.theme.NavActive
import com.fixmycity.app.ui.theme.NavInactive

data class BottomNavDestination(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
)

val bottomNavDestinations = listOf(
    BottomNavDestination(Screen.Home.route, Icons.Default.Home, Icons.Filled.Home, "Home"),
    BottomNavDestination(Screen.Report.route, Icons.Default.AddCircle, Icons.Filled.AddCircle, "Report"),
    BottomNavDestination(Screen.MyReports.route, Icons.Default.List, Icons.Filled.List, "My Reports"),
    BottomNavDestination(Screen.Notifications.route, Icons.Default.Notifications, Icons.Filled.Notifications, "Alerts"),
    BottomNavDestination(Screen.Profile.route, Icons.Default.Person, Icons.Filled.Person, "Profile")
)

@Composable
fun FixMyCityBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        bottomNavDestinations.forEach { destination ->
            val selected = currentRoute == destination.route ||
                currentRoute?.startsWith(destination.route) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NavActive,
                    unselectedIconColor = NavInactive,
                    selectedTextColor = NavActive,
                    unselectedTextColor = NavInactive,
                    indicatorColor = NavActive.copy(alpha = 0.12f)
                )
            )
        }
    }
}
