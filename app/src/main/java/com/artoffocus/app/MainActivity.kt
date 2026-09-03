package com.artoffocus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artoffocus.app.ui.focus.FocusScreen
import com.artoffocus.app.ui.focus.FocusViewModel
import com.artoffocus.app.ui.museum.MuseumScreen
import com.artoffocus.app.ui.museum.MuseumViewModel
import com.artoffocus.app.ui.profile.ProfileScreen
import com.artoffocus.app.ui.profile.ProfileViewModel
import com.artoffocus.app.ui.common.TabBar
import com.artoffocus.app.ui.common.TabBarItem
import com.artoffocus.app.ui.theme.ArtOfFocusTheme
import com.artoffocus.app.ui.theme.AppColors

private enum class Tab(val label: String, val glyph: String) {
    FOCUS("Focus", "◈"),
    MUSEUM("Museum", "▤"),
    PROFILE("Profile", "◍"),
    FRIENDS("Friends", "◎"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArtOfFocusTheme {
                val container = (application as ArtOfFocusApp).container
                AppShell(container)
            }
        }
    }
}

@Composable
private fun AppShell(container: AppContainer) {
    var tab by remember { mutableStateOf(Tab.FOCUS) }

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val focusViewModel: FocusViewModel = viewModel(
        factory = FocusViewModel.Factory(
            container.focusSetupRepository,
            container.sessionRepository,
            container.artworkRepository,
            container.museumRepository,
            appContext,
        ),
    )
    val museumViewModel: MuseumViewModel = viewModel(factory = MuseumViewModel.Factory(container.museumRepository))
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(
            container.profileRepository,
            container.focusSetupRepository,
            container.museumRepository,
            container.accountRepository,
            container.authRepository,
        ),
    )

    // I7 amendment — record when the app leaves the foreground, and check the away gap on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> focusViewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> focusViewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Bg)) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 78.dp)) {
            when (tab) {
                Tab.FOCUS -> FocusScreen(focusViewModel)
                Tab.MUSEUM -> MuseumScreen(museumViewModel)
                Tab.PROFILE -> ProfileScreen(profileViewModel)
                Tab.FRIENDS -> PlaceholderScreen("Friends")
            }
        }

        TabBar(
            items = Tab.entries.map { entry -> TabBarItem(entry.label, entry.glyph, tab == entry) { tab = entry } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.padding(24.dp),
    ) {
        Text("$name — coming soon")
    }
}
