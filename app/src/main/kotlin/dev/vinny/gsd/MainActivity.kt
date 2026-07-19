package dev.vinny.gsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vinny.gsd.nav.Destination
import dev.vinny.gsd.nav.GsdNavHost
import dev.vinny.gsd.prefs.ThemePref
import dev.vinny.gsd.theme.GsdTheme

class MainActivity : ComponentActivity() {
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val container = (application as GsdApp).container
        if (event.isCtrlPressed) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_F -> {
                    container.palette.requestOpen(); return true
                }
                android.view.KeyEvent.KEYCODE_N -> {
                    container.palette.requestNewTask(); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(dev.vinny.gsd.notifications.ReminderScheduler.EXTRA_TASK_ID)
            ?.let { (application as GsdApp).container.palette.requestOpenTask(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GsdApp).container
        intent.getStringExtra(dev.vinny.gsd.notifications.ReminderScheduler.EXTRA_TASK_ID)
            ?.let { container.palette.requestOpenTask(it) }
        setContent {
            val themePref by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
            val darkTheme = when (themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            GsdTheme(darkTheme = darkTheme) {
                val hasOnboarded by container.prefs.hasOnboarded.collectAsStateWithLifecycle(initialValue = true)
                if (!hasOnboarded) {
                    val scope = rememberCoroutineScope()
                    dev.vinny.gsd.onboarding.OnboardingFlow(
                        onFinished = { scope.launch { container.prefs.setHasOnboarded(true) } },
                    )
                    return@GsdTheme
                }
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                // Window size classes per spec §6: <600 compact, <840 medium, else expanded
                val widthDp = LocalConfiguration.current.screenWidthDp
                val navigate: (Destination) -> Unit = { destination ->
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                        popUpTo(Destination.MATRIX.route) { saveState = true }
                        restoreState = true
                    }
                }
                when {
                    widthDp < 600 -> Scaffold(
                        bottomBar = {
                            NavigationBar {
                                Destination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentRoute == destination.route,
                                        onClick = { navigate(destination) },
                                        icon = { Icon(destination.icon, contentDescription = null) },
                                        label = { Text(stringResource(destination.label)) },
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        GsdNavHost(navController, container, Modifier.padding(innerPadding))
                    }

                    widthDp < 840 -> Row {
                        NavigationRail {
                            Destination.entries.forEach { destination ->
                                NavigationRailItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { navigate(destination) },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(stringResource(destination.label)) },
                                )
                            }
                        }
                        GsdNavHost(navController, container, Modifier.weight(1f))
                    }

                    else -> PermanentNavigationDrawer(
                        drawerContent = {
                            PermanentDrawerSheet(modifier = Modifier.width(220.dp)) {
                                Destination.entries.forEach { destination ->
                                    NavigationDrawerItem(
                                        selected = currentRoute == destination.route,
                                        onClick = { navigate(destination) },
                                        icon = { Icon(destination.icon, contentDescription = null) },
                                        label = { Text(stringResource(destination.label)) },
                                    )
                                }
                            }
                        },
                    ) {
                        GsdNavHost(navController, container, expanded = true)
                    }
                }
            }
        }
    }
}
