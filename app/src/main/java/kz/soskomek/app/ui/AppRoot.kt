package kz.soskomek.app.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.sos.Intents
import kz.soskomek.app.sos.SosEffect
import kz.soskomek.app.sos.SosViewModel
import kz.soskomek.app.ui.map.MapScreen
import kz.soskomek.app.ui.map.ReportIncidentScreen
import kz.soskomek.app.ui.onboarding.LegalScreen
import kz.soskomek.app.ui.onboarding.OnboardingScreen
import kz.soskomek.app.ui.onboarding.PermissionsScreen
import kz.soskomek.app.ui.profile.ContactEditScreen
import kz.soskomek.app.ui.profile.HistoryScreen
import kz.soskomek.app.ui.profile.ProfileScreen
import kz.soskomek.app.ui.profile.VerifyPhoneScreen
import kz.soskomek.app.ui.sos.SosScreen
import kz.soskomek.app.ui.sos.SosStatusScreen

object Routes {
    const val SOS = "sos"
    const val MAP = "map"
    const val PROFILE = "profile"
    const val STATUS = "status"
    const val HISTORY = "history"
    const val REPORT = "report"
    const val VERIFY = "verify"
    const val PERMISSIONS = "permissions"
    const val CONTACT = "contact/{index}"
    const val LEGAL = "legal/{doc}"
    fun contact(index: Int) = "contact/$index"
    fun legal(doc: String) = "legal/$doc"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settings by context.container.settings.state.collectAsStateWithLifecycle()
    if (!settings.onboardingDone) {
        OnboardingScreen()
    } else {
        MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val context = LocalContext.current
    val nav = rememberNavController()
    // Activity-scoped so the SOS flow survives navigation between tabs
    val sosVm: SosViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)

    LaunchedEffect(Unit) {
        sosVm.effects.collect { effect ->
            when (effect) {
                SosEffect.OpenStatus -> nav.navigate(Routes.STATUS) { launchSingleTop = true }
                SosEffect.Finished -> nav.popBackStack(Routes.SOS, inclusive = false)
                is SosEffect.Call -> Intents.call(context, effect.number)
                is SosEffect.OpenSmsApp -> Intents.composeSms(context, effect.phones, effect.body)
                is SosEffect.Message -> Toast.makeText(context, effect.res, Toast.LENGTH_LONG).show()
            }
        }
    }

    val tabs = listOf(
        Tab(Routes.SOS, R.string.tab_sos, Icons.Filled.Sos, Icons.Filled.Sos),
        Tab(Routes.MAP, R.string.tab_map, Icons.Outlined.Map, Icons.Filled.Map),
        Tab(Routes.PROFILE, R.string.tab_profile, Icons.Outlined.Person, Icons.Filled.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.SOS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.SOS) {
                SosScreen(
                    vm = sosVm,
                    onOpenStatus = { nav.navigate(Routes.STATUS) { launchSingleTop = true } },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onOpenProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                )
            }
            composable(Routes.STATUS) { SosStatusScreen(vm = sosVm, onBack = { nav.popBackStack() }) }
            composable(Routes.MAP) { MapScreen(onReport = { nav.navigate(Routes.REPORT) }) }
            composable(Routes.REPORT) { ReportIncidentScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onEditContact = { nav.navigate(Routes.contact(it)) },
                    onVerifyPhone = { nav.navigate(Routes.VERIFY) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onOpenLegal = { nav.navigate(Routes.legal(it)) },
                )
            }
            composable(
                Routes.CONTACT,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                ContactEditScreen(index = entry.arguments?.getInt("index") ?: -1, onBack = { nav.popBackStack() })
            }
            composable(Routes.VERIFY) { VerifyPhoneScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LEGAL) { entry ->
                LegalScreen(doc = entry.arguments?.getString("doc") ?: "privacy", onBack = { nav.popBackStack() })
            }
        }
    }
}
