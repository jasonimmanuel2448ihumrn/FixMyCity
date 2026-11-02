package com.fixmycity.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Login : Screen("login?role={role}") {
        fun createRoute(role: String = "Citizen") = "login?role=$role"
    }
    object Signup : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object Home : Screen("home")
    object Report : Screen("report")
    object ReportSuccess : Screen("report_success")
    object MyReports : Screen("my_reports")
    object ReportDetail : Screen("report_detail/{reportId}") {
        fun createRoute(reportId: String) = "report_detail/$reportId"
    }
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object About : Screen("about")
    object AllReports : Screen("all_reports")
    object AdminDashboard : Screen("admin_dashboard")
    object AiChat : Screen("ai_chat")
}

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", "Home"),
    BottomNavItem(Screen.Report, "Report", "AddCircle"),
    BottomNavItem(Screen.MyReports, "My Reports", "Assignment"),
    BottomNavItem(Screen.Notifications, "Alerts", "Notifications"),
    BottomNavItem(Screen.Profile, "Profile", "Person")
)

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val iconName: String
)