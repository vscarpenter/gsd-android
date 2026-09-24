package dev.vinny.gsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import dev.vinny.gsd.theme.LocalGsdColors
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
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import dev.vinny.gsd.nav.NavLayout
import dev.vinny.gsd.nav.navLayoutFor
import dev.vinny.gsd.nav.navigateToTab
import dev.vinny.gsd.nav.owningTab
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.prefs.ThemePref
import dev.vinny.gsd.theme.GsdTheme

class MainActivity : ComponentActivity() {
    /**
     * The §6.6 keys. Each one goes through the deep-link router, so pressed on any tab it switches to
     * Matrix and acts at once (review item S4). Ctrl+N is `gsd://capture`, and Ctrl+1 to Ctrl+4 are
     * `gsd://quadrant/<id>`. Before, a key pressed off Matrix waited for the next visit there.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val route = if (event.isCtrlPressed) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_F -> dev.vinny.gsd.nav.GsdRoute.Palette
                android.view.KeyEvent.KEYCODE_N -> dev.vinny.gsd.nav.GsdRoute.Capture
                android.view.KeyEvent.KEYCODE_1 -> dev.vinny.gsd.nav.GsdRoute.QuadrantLink(Quadrant.DO_FIRST)
                android.view.KeyEvent.KEYCODE_2 -> dev.vinny.gsd.nav.GsdRoute.QuadrantLink(Quadrant.SCHEDULE)
                android.view.KeyEvent.KEYCODE_3 -> dev.vinny.gsd.nav.GsdRoute.QuadrantLink(Quadrant.DELEGATE)
                android.view.KeyEvent.KEYCODE_4 -> dev.vinny.gsd.nav.GsdRoute.QuadrantLink(Quadrant.ELIMINATE)
                else -> null
            }
        } else {
            null
        }
        if (route == null) return super.onKeyDown(keyCode, event)
        (application as GsdApp).container.palette.pendingRoute.value = route
        return true
    }

    private fun handleIncomingIntent(intent: android.content.Intent) {
        val container = (application as GsdApp).container
        val data = intent.data
        if (data?.scheme == "gsd" && data.host != "oauth-callback") {
            dev.vinny.gsd.nav.GsdRoute.parse(data.toString())
                ?.let { container.palette.pendingRoute.value = it }
        }
        if (data?.scheme == "gsd" && data.host == "oauth-callback") {
            // The app's scope: a rotation during the token exchange must not cancel the sign-in (finding 40)
            val appContext = applicationContext
            container.appScope.launch {
                try {
                    container.syncCoordinator.completeOAuthCallback(data.toString())
                } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("GsdAuth", "oauth callback failed: ${failure.javaClass.simpleName}")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext,
                            appContext.getString(R.string.sign_in_error),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
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
        // A rotation recreates the activity with the same launch intent, and Recents relaunches it with
        // the task's last link. Either way the link already ran once (§6.4).
        if (dev.vinny.gsd.nav.runsLaunchLink(savedInstanceState != null, intent.flags)) handleIncomingIntent(intent)
        setContent {
            val themePref by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
            val darkTheme = when (themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            // The bars' icons follow the in-app theme, not the system's: dark on dark paper was unreadable (finding 38)
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            GsdTheme(darkTheme = darkTheme) {
                // Paper behind every layout, in the in-app theme; only the phone's Scaffold painted it (finding 37)
                Surface(color = LocalGsdColors.current.paper, modifier = Modifier.fillMaxSize()) {
                    val switchPrompt by container.syncCoordinator.switchPrompt.collectAsStateWithLifecycle()
                    switchPrompt?.let { pendingAuth ->
                        // The app's scope, so a rotation cannot cancel an account switch midway (finding 40)
                        val scope = container.appScope
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
                    // Unknown until the pref loads. Assuming "done" composed the nav host for a moment on a
                    // first launch, and its router could take a deep link that onboarding then replaced.
                    val onboardedOrUnknown = androidx.compose.runtime.remember {
                        container.prefs.hasOnboarded.map<Boolean, Boolean?> { it }
                    }
                    val hasOnboarded by onboardedOrUnknown.collectAsStateWithLifecycle(initialValue = null)
                    when (hasOnboarded) {
                        null -> return@Surface // paper, for the moment the pref takes to load
                        false -> {
                            val scope = rememberCoroutineScope()
                            dev.vinny.gsd.onboarding.OnboardingFlow(
                                onFinished = { scope.launch { container.prefs.setHasOnboarded(true) } },
                            )
                            return@Surface
                        }
                        true -> Unit
                    }
                    val navController = rememberNavController()
                    val backStack by navController.currentBackStackEntryAsState()
                    // The tab that owns the stack stays selected on its child screens (finding 21)
                    val selectedTab = androidx.compose.runtime.remember(backStack) { navController.owningTab() }
                    // A shortcut's CLEAR_TASK pauses this activity and then creates a fresh one, and the two
                    // share pendingRoute. Only the resumed copy may take a route (review finding 46).
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
                        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                            container.palette.pendingRoute.collect { route ->
                                if (route == null) return@collect
                                when (route) {
                                    is dev.vinny.gsd.nav.GsdRoute.Capture -> {
                                        navController.navigateToTab(Destination.MATRIX)
                                        container.palette.requestNewTask()
                                    }
                                    is dev.vinny.gsd.nav.GsdRoute.Palette -> {
                                        navController.navigateToTab(Destination.MATRIX)
                                        container.palette.requestOpen()
                                    }
                                    is dev.vinny.gsd.nav.GsdRoute.Focus -> navController.navigateToTab(Destination.MATRIX)
                                    is dev.vinny.gsd.nav.GsdRoute.QuadrantLink -> {
                                        navController.navigateToTab(Destination.MATRIX)
                                        container.palette.requestQuadrant(route.quadrant)
                                    }
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
                    }
                    val configuration = LocalConfiguration.current
                    val navigate: (Destination) -> Unit = navController::navigateToTab
                    // Only the phone's Scaffold applied insets; the rail and drawer layouts drew under the bars (finding 11)
                    val navInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
                    val contentInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    when (navLayoutFor(configuration.screenWidthDp, configuration.screenHeightDp)) {
                        NavLayout.BAR -> Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    Destination.entries.forEach { destination ->
                                        NavigationBarItem(
                                            selected = selectedTab == destination,
                                            onClick = { navigate(destination) },
                                            icon = { Icon(destination.icon, contentDescription = null) },
                                            // One line: at font scale 2.0 "Dashboard" split mid-word (finding 35)
                                            label = {
                                                Text(
                                                    stringResource(destination.label),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                        ) { innerPadding ->
                            GsdNavHost(navController, container, Modifier.padding(innerPadding))
                        }

                        NavLayout.RAIL -> Row {
                            NavigationRail(windowInsets = navInsets) {
                                Destination.entries.forEach { destination ->
                                    NavigationRailItem(
                                        selected = selectedTab == destination,
                                        onClick = { navigate(destination) },
                                        icon = { Icon(destination.icon, contentDescription = null) },
                                        label = { Text(stringResource(destination.label), maxLines = 1) },
                                    )
                                }
                            }
                            // Phone-style content (§6.2), kept to a readable width instead of stretching (finding 36)
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(contentInsets),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                GsdNavHost(navController, container, Modifier.widthIn(max = 640.dp).fillMaxSize())
                            }
                        }

                        NavLayout.DRAWER -> PermanentNavigationDrawer(
                            drawerContent = {
                                PermanentDrawerSheet(modifier = Modifier.width(220.dp), windowInsets = navInsets) {
                                    Destination.entries.forEach { destination ->
                                        NavigationDrawerItem(
                                            selected = selectedTab == destination,
                                            onClick = { navigate(destination) },
                                            icon = { Icon(destination.icon, contentDescription = null) },
                                            // One line: beside a cutout, "Browse" broke into "Brows" and "e" (finding 36)
                                            label = {
                                                Text(
                                                    stringResource(destination.label),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                        ) {
                            GsdNavHost(
                                navController,
                                container,
                                Modifier.windowInsetsPadding(contentInsets),
                                expanded = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** The scrims enableEdgeToEdge uses by default for three-button navigation. */
        val LIGHT_SCRIM = android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
