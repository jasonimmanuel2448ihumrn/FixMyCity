package com.fixmycity.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.ui.screens.admin.*
import com.fixmycity.app.ui.screens.ai.*
import com.fixmycity.app.ui.screens.auth.*
import com.fixmycity.app.ui.screens.complaints.*
import com.fixmycity.app.ui.screens.home.*
import com.fixmycity.app.ui.screens.notifications.*
import com.fixmycity.app.ui.screens.profile.*
import com.fixmycity.app.ui.screens.report.*

private val mainScreenRoutes = setOf(
    Screen.Home.route,
    Screen.Report.route,
    Screen.MyReports.route,
    Screen.Notifications.route,
    Screen.Profile.route
)

@Composable
fun NavGraph(
    onThemeToggle: () -> Unit,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = mainScreenRoutes.any { route ->
        currentRoute == route || currentRoute?.startsWith(route) == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                FixMyCityBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Splash ────────────────────────────────────────────────────────
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToWelcome = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // ── Welcome ───────────────────────────────────────────────────────
            composable(Screen.Welcome.route) {
                val viewModel: AuthViewModel = viewModel()
                WelcomeScreen(
                    onLogin = { navController.navigate(Screen.Login.createRoute("Citizen")) },
                    onSignup = { navController.navigate(Screen.Signup.route) },
                    onGuestLogin = {
                        viewModel.loginAsGuest {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        }
                    },
                    onMunicipalLogin = {
                        navController.navigate(Screen.Login.createRoute("Municipal"))
                    },
                    onGoogleLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

            // ── Login ─────────────────────────────────────────────────────────
            composable(
                route = Screen.Login.route,
                arguments = listOf(
                    navArgument("role") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "Citizen"
                    }
                )
            ) { backStackEntry ->
                val role = backStackEntry.arguments?.getString("role") ?: "Citizen"
                LoginScreen(
                    initialRole = role,
                    onLogin = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onMunicipalLogin = {
                        navController.navigate(Screen.AdminDashboard.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onSignup = { navController.navigate(Screen.Signup.route) },
                    onForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
                )
            }

            // ── Signup ────────────────────────────────────────────────────────
            composable(Screen.Signup.route) {
                SignupScreen(
                    onSignup = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onLogin = { navController.popBackStack() }
                )
            }

            // ── Forgot Password ───────────────────────────────────────────────
            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Home ──────────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToReport = { category ->
                        val route = if (!category.isNullOrBlank())
                            "report?category=${category}"
                        else
                            Screen.Report.route
                        navController.navigate(route)
                    },
                    onNavigateToReportDetail = { reportId ->
                        navController.navigate(Screen.ReportDetail.createRoute(reportId))
                    },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToAllReports = { navController.navigate(Screen.AllReports.route) },
                    onNavigateToAiChat = { navController.navigate(Screen.AiChat.route) }
                )
            }

            // ── Report ────────────────────────────────────────────────────────
            composable(
                route = "report?category={category}",
                arguments = listOf(
                    navArgument("category") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category")
                ReportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSubmitSuccess = {
                        navController.navigate(Screen.ReportSuccess.route) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                    initialCategory = category
                )
            }

            // ── Report Success ────────────────────────────────────────────────
            composable(Screen.ReportSuccess.route) {
                ReportSuccessScreen(
                    onGoHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onReportAnother = {
                        navController.navigate(Screen.Report.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            // ── My Reports ────────────────────────────────────────────────────
            composable(Screen.MyReports.route) {
                MyReportsScreen(
                    onNavigateToDetail = { reportId ->
                        navController.navigate(Screen.ReportDetail.createRoute(reportId))
                    }
                )
            }

            // ── Report Detail ─────────────────────────────────────────────────
            composable(
                route = Screen.ReportDetail.route,
                arguments = listOf(
                    navArgument("reportId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
                ReportDetailScreen(
                    reportId = reportId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Notifications ─────────────────────────────────────────────────
            composable(Screen.Notifications.route) {
                NotificationsScreen()
            }

            // ── Profile ───────────────────────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onAbout = { navController.navigate(Screen.About.route) },
                    onThemeToggle = onThemeToggle,
                    isDarkTheme = isDarkTheme,
                    onOpenAdminDashboard = {
                        navController.navigate(Screen.AdminDashboard.route)
                    },
                    onOpenAiChat = {
                        navController.navigate(Screen.AiChat.route)
                    }
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            composable(Screen.About.route) {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── All Reports (Community Feed) ───────────────────────────────────
            composable(Screen.AllReports.route) {
                AllReportsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { reportId ->
                        navController.navigate(Screen.ReportDetail.createRoute(reportId))
                    }
                )
            }

            // ── Municipal Admin Dashboard ──────────────────────────────────────
            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    onLogout = {
                        FixMyCityRepository.getInstance().logout()
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToCitizenView = {
                        navController.navigate(Screen.Home.route)
                    }
                )
            }

            // ── AI Civic Chatbot ──────────────────────────────────────────────
            composable(Screen.AiChat.route) {
                AiChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReport = { category ->
                        val route = if (!category.isNullOrBlank() && category != "null")
                            "report?category=$category"
                        else
                            Screen.Report.route
                        navController.navigate(route)
                    },
                    onNavigateToMyReports = {
                        navController.navigate(Screen.MyReports.route)
                    },
                    onNavigateToReportDetail = { reportId ->
                        if (reportId.isNotBlank() && reportId != "null") {
                            navController.navigate(Screen.ReportDetail.createRoute(reportId))
                        } else {
                            navController.navigate(Screen.MyReports.route)
                        }
                    }
                )
            }
        }
    }
}