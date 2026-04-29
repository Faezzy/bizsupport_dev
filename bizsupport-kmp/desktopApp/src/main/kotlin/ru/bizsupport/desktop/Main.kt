package ru.bizsupport.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.bizsupport.desktop.ui.components.Screen
import ru.bizsupport.desktop.ui.components.Sidebar
import ru.bizsupport.desktop.ui.screens.*
import ru.bizsupport.desktop.ui.theme.BizSupportTheme
import ru.bizsupport.shared.AppDI
import ru.bizsupport.shared.viewmodel.*

fun main() = application {
    val di = remember { AppDI("http://localhost:8080") }
    val authViewModel = remember { di.authViewModel() }
    val dashboardViewModel = remember { di.dashboardViewModel() }
    val taxViewModel = remember { di.taxViewModel() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "BizSupport — ИС поддержки малого бизнеса",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        BizSupportTheme {
            val authState by authViewModel.state.collectAsState()

            if (!authState.isAuthenticated) {
                var showRegister by remember { mutableStateOf(false) }

                if (showRegister) {
                    RegisterScreenWrapper(
                        authViewModel = authViewModel,
                        authState = authState,
                        onSwitchToLogin = { showRegister = false }
                    )
                } else {
                    LoginScreen(
                        isLoading = authState.isLoading,
                        error = authState.error,
                        onLogin = { email, pwd -> authViewModel.login(email, pwd) },
                        onSwitchToRegister = { showRegister = true }
                    )
                }
            } else {
                MainApp(
                    di = di,
                    dashboardViewModel = dashboardViewModel,
                    taxViewModel = taxViewModel,
                    onLogout = { authViewModel.logout() }
                )
            }
        }
    }
}

@Composable
private fun RegisterScreenWrapper(
    authViewModel: AuthViewModel,
    authState: AuthState,
    onSwitchToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LoginScreen(
        isLoading = authState.isLoading,
        error = authState.error,
        onLogin = { e, p -> authViewModel.register(e, p, name) },
        onSwitchToRegister = onSwitchToLogin
    )
}

@Composable
private fun MainApp(
    di: AppDI,
    dashboardViewModel: DashboardViewModel,
    taxViewModel: TaxViewModel,
    onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var unreadCount by remember { mutableStateOf(0L) }

    // Load data on first compose
    LaunchedEffect(Unit) {
        dashboardViewModel.load()
        taxViewModel.loadRegimes()
        unreadCount = try { di.notificationUseCase.getUnreadCount().getOrDefault(0) } catch (_: Exception) { 0 }
    }

    val dashboardState by dashboardViewModel.state.collectAsState()
    val taxListState by taxViewModel.listState.collectAsState()
    val calcState by taxViewModel.calcState.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            currentScreen = currentScreen,
            unreadCount = unreadCount,
            onNavigate = { currentScreen = it },
            onLogout = onLogout
        )

        // Content area
        when (currentScreen) {
            Screen.DASHBOARD -> DashboardScreen(
                dashboard = dashboardState.dashboard,
                isLoading = dashboardState.isLoading,
                error = dashboardState.error,
                onNavigate = { currentScreen = it },
                onRetry = { dashboardViewModel.load() }
            )
            Screen.TAX -> TaxListScreen(
                regimes = taxListState.regimes,
                isLoading = taxListState.isLoading,
                error = taxListState.error,
                onRegimeClick = { /* TODO: detail screen */ },
                onRetry = { taxViewModel.loadRegimes() }
            )
            Screen.CALCULATOR -> CalculatorScreen(
                results = calcState.results,
                isLoading = calcState.isLoading,
                error = calcState.error,
                onCalculate = { rev, exp, emp, sal, ip ->
                    taxViewModel.calculate(rev, exp, emp, sal, ip)
                }
            )
            else -> {
                // Placeholder for other screens
                DashboardScreen(
                    dashboard = dashboardState.dashboard,
                    isLoading = false, error = null,
                    onNavigate = { currentScreen = it },
                    onRetry = {}
                )
            }
        }
    }
}
