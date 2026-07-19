package dev.vinny.gsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vinny.gsd.nav.Destination
import dev.vinny.gsd.nav.GsdNavHost
import dev.vinny.gsd.prefs.ThemePref
import dev.vinny.gsd.theme.GsdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GsdApp).container
        setContent {
            val themePref by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
            val darkTheme = when (themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            GsdTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Destination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            launchSingleTop = true
                                            popUpTo(Destination.MATRIX.route) { saveState = true }
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(stringResource(destination.label)) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    GsdNavHost(
                        navController = navController,
                        container = container,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
