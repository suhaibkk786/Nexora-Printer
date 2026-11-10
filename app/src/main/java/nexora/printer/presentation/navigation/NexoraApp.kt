package nexora.printer.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nexora.printer.presentation.dashboard.DashboardScreen
import nexora.printer.presentation.diagnostics.DiagnosticsScreen
import nexora.printer.presentation.history.HistoryScreen
import nexora.printer.presentation.print.PrintScreen
import nexora.printer.presentation.printers.PrintersScreen
import nexora.printer.presentation.settings.SettingsScreen
import nexora.printer.presentation.theme.NexoraTheme

object Routes {
    const val DASHBOARD = "dashboard"
    const val PRINTERS = "printers"
    const val PRINT = "print?tab={tab}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    fun print(tab: Int = 0) = "print?tab=$tab"
}

private data class NavItem(val pattern: String, val go: String, val label: String, val icon: ImageVector)

private val items = listOf(
    NavItem(Routes.DASHBOARD, Routes.DASHBOARD, "Home", Icons.Default.Dashboard),
    NavItem(Routes.PRINTERS, Routes.PRINTERS, "Printers", Icons.Default.Print),
    NavItem(Routes.PRINT, Routes.print(0), "Print", Icons.Default.Description),
    NavItem(Routes.HISTORY, Routes.HISTORY, "History", Icons.Default.History),
    NavItem(Routes.SETTINGS, Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun NexoraApp(appVm: AppViewModel = hiltViewModel()) {
    val theme by appVm.theme.collectAsStateWithLifecycle()
    val pending by appVm.pendingShare.collectAsStateWithLifecycle()
    NexoraTheme(theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val destination = entry?.destination

        // Content arriving from the Android share sheet opens the Print screen.
        LaunchedEffect(pending) {
            if (pending != null) {
                nav.navigate(Routes.print(0)) { launchSingleTop = true }
            }
        }

        Scaffold(
            bottomBar = {
                if (destination?.route != Routes.DIAGNOSTICS) {
                    NavigationBar {
                        items.forEach { item ->
                            NavigationBarItem(
                                selected = destination?.hierarchy?.any { it.route == item.pattern } == true,
                                onClick = {
                                    nav.navigate(item.go) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) }
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenPrint = { nav.navigate(Routes.print(it)) { launchSingleTop = true } },
                        onOpenPrinters = { nav.navigate(Routes.PRINTERS) { launchSingleTop = true } },
                        onOpenHistory = { nav.navigate(Routes.HISTORY) { launchSingleTop = true } }
                    )
                }
                composable(Routes.PRINTERS) { PrintersScreen() }
                composable(
                    Routes.PRINT,
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
                ) { backStack ->
                    PrintScreen(
                        initialTab = backStack.arguments?.getInt("tab") ?: 0,
                        onOpenPrinters = { nav.navigate(Routes.PRINTERS) { launchSingleTop = true } }
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(onOpenPrinters = { nav.navigate(Routes.PRINTERS) { launchSingleTop = true } })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) })
                }
                composable(Routes.DIAGNOSTICS) {
                    DiagnosticsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenPrinters = { nav.navigate(Routes.PRINTERS) }
                    )
                }
            }
        }
    }
}
