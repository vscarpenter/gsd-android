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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vinny.gsd.nav.Destination
import dev.vinny.gsd.nav.GsdNavHost
import dev.vinny.gsd.nav.navigateToTab
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

    private fun handleIncomingIntent(intent: android.content.Intent) {
        val container = (application as GsdApp).container
        val data = intent.data
        if (data?.scheme == "gsd" && data.host != "oauth-callback") {
            dev.vinny.gsd.nav.GsdRoute.parse(data.toString())
                ?.let { container.palette.pendingRoute.value = it }
        }
        if (data?.scheme == "gsd" && data.host == "oauth-callback") {
            lifecycleScope.launch {
                try {
                    container.syncCoordinator.completeOAuthCallback(data.toString())
                } catch (failure: Exception) {
                    android.util.Log.w("GsdAuth", "oauth callback failed: ${failure.javaClass.simpleName}")
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.sign_in_error),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as GsdApp).container.syncCoordinator.onForeground()
    }

    override fun onStop() {
        (application as GsdApp).container.syncCoordinator.onBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GsdApp).container
        // A rotation recreates the activity with the same launch intent. It already ran once (§6.4).
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            val themePref by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
            val darkTheme = when (themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            GsdTheme(darkTheme = darkTheme) {
                val switchPrompt by container.syncCoordinator.switchPrompt.collectAsStateWithLifecycle()
                switchPrompt?.let { pendingAuth ->
                    val scope = rememberCoroutineScope()
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { scope.launch { container.syncCoordinator.cancelSwitch() } },
                        title = { Text(stringResource(R.string.switch_title)) },
                        text = { Text(stringResource(R.string.switch_body)) },
                        confirmButton = {
                            androidx.compose.foundation.layout.Column {
                                androidx.compose.material3.TextButton(onClick = {
                                    scope.launch {
                                        container.syncCoordinator.confirmOwner(pendingAuth, eraseLocalFirst = false)
                                    }
                                }) { Text(stringResource(R.string.switch_keep)) }
                                androidx.compose.material3.TextButton(onClick = {
                                    scope.launch {
                                        container.syncCoordinator.confirmOwner(pendingAuth, eraseLocalFirst = true)
                                    }
                                }) { Text(stringResource(R.string.switch_fresh)) }
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                scope.launch { container.syncCoordinator.cancelSwitch() }
                            }) { Text(stringResource(R.string.dialog_cancel)) }
                        },
                    )
                }
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
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    container.palette.pendingRoute.collect { route ->
                        if (route == null) return@collect
                        when (route) {
                            is dev.vinny.gsd.nav.GsdRoute.Capture -> {
                                navController.navigateToTab(Destination.MATRIX)
                                container.palette.requestNewTask()
                            }
                            is dev.vinny.gsd.nav.GsdRoute.Focus,
                            is dev.vinny.gsd.nav.GsdRoute.QuadrantLink,
                            -> navController.navigateToTab(Destination.MATRIX)
                            is dev.vinny.gsd.nav.GsdRoute.TaskLink -> {
                                navController.navigateToTab(Destination.MATRIX)
                                container.palette.requestOpenTask(route.id)
                            }
                            is dev.vinny.gsd.nav.GsdRoute.SmartViewLink ->
                                navController.navigate("browse/view/${route.id}")
                            is dev.vinny.gsd.nav.GsdRoute.Dashboard ->
                                navController.navigateToTab(Destination.DASHBOARD)
                            is dev.vinny.gsd.nav.GsdRoute.Settings ->
                                navController.navigateToTab(Destination.SETTINGS)
                            is dev.vinny.gsd.nav.GsdRoute.Archive -> navController.navigate("archive")
                            is dev.vinny.gsd.nav.GsdRoute.Trash -> navController.navigate("trash")
                        }
                        container.palette.pendingRoute.value = null
                    }
                }
                // Window size classes per spec §6: <600 compact, <840 medium, else expanded
                val widthDp = LocalConfiguration.current.screenWidthDp
                val navigate: (Destination) -> Unit = navController::navigateToTab
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
