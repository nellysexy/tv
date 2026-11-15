package com.nexotv.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexotv.app.NexoApp
import com.nexotv.app.data.Channel
import com.nexotv.app.data.Repository
import com.nexotv.app.player.PlayerActivity

enum class Tab(val label: String, val icon: ImageVector) {
    CHANNELS("Canales", Icons.Filled.LiveTv),
    GUIDE("Guía", Icons.Filled.DateRange),
    FAVORITES("Favoritos", Icons.Filled.Favorite),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(NexoApp.repo, ::openPlayer)
                }
            }
        }
    }

    private fun openPlayer(channel: Channel, queue: List<Channel>) {
        NexoApp.repo.playQueue = queue
        startActivity(
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.id)
        )
    }
}

@Composable
fun MainScreen(repo: Repository, openPlayer: (Channel, List<Channel>) -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val wide = remember(config.screenWidthDp) { isTv(context) || config.screenWidthDp >= 720 }
    var tab by rememberSaveable { mutableStateOf(Tab.CHANNELS) }
    val busy by repo.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        repo.events.collect { snackbar.showSnackbar(it) }
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            if (busy != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    busy ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.CHANNELS -> ChannelsScreen(repo, wide, openPlayer, onGoToSettings = { tab = Tab.SETTINGS })
                    Tab.GUIDE -> GuideScreen(repo, wide, openPlayer, onGoToSettings = { tab = Tab.SETTINGS })
                    Tab.FAVORITES -> FavoritesScreen(repo, openPlayer)
                    Tab.SETTINGS -> SettingsScreen(repo, wide)
                }
            }
        }
    }

    if (wide) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            modifier = Modifier.focusBorder(),
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content() }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
    }
}
