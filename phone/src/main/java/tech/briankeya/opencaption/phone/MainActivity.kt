package tech.briankeya.opencaption.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import tech.briankeya.opencaption.phone.ui.AlertsScreen
import tech.briankeya.opencaption.phone.ui.OpenCaptionTheme
import tech.briankeya.opencaption.phone.ui.HomeScreen
import tech.briankeya.opencaption.phone.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCaptionTheme { OpenCaptionRoot() }
        }
    }
}

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun OpenCaptionRoot() {
    val context = LocalContext.current
    val ui by AppState.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    // Runs the action that was waiting for microphone permission.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val action = pendingAction
        pendingAction = null
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            action?.invoke()
        } else {
            AppState.update { it.copy(status = "Microphone permission is needed to listen.") }
        }
    }

    val withMicrophone: (() -> Unit) -> Unit = { action ->
        if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            )
        }
    }

    // Starts captions on open when the user (or an imported profile) asked for it.
    LaunchedEffect(Unit) {
        if (Settings.startCaptionsOnOpen && !AppState.state.value.captionsOn) {
            withMicrophone { ListenService.request(context, true, AppState.state.value.soundOn) }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("Alerts") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    ui = ui,
                    onCaptionsToggle = { on -> withMicrophone { ListenService.request(context, on, ui.soundOn) } },
                    onSoundToggle = { on -> withMicrophone { ListenService.request(context, ui.captionsOn, on) } },
                    onClearCaptions = { AppState.clearCaptions() },
                )
                1 -> AlertsScreen(ui = ui, onClear = { AppState.clearAlerts() })
                else -> SettingsScreen(ui = ui, onSettingsChanged = { reloadIfRunning(context, ui) })
            }
        }
    }
}

/** Restarts running engines so changed language, labels or sensitivity take effect. */
fun reloadIfRunning(context: Context, ui: UiState) {
    if (ui.captionsOn || ui.soundOn) {
        ListenService.request(context, ui.captionsOn, ui.soundOn, reload = true)
    }
}
