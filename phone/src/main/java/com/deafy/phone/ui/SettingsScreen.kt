package com.deafy.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deafy.phone.CaptionLanguage
import com.deafy.phone.HoldTime
import com.deafy.phone.ModelFiles
import com.deafy.phone.Settings
import com.deafy.phone.UiState
import com.deafy.phone.WatchLink
import com.deafy.phone.Alerter
import com.deafy.phone.reloadIfRunning
import com.deafy.shared.SoundLabels
import com.deafy.shared.VibePattern

@Composable
fun SettingsScreen(ui: UiState, onSettingsChanged: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }

        // ---------------- Captions ----------------
        item {
            SettingsSection("Captions", "How speech is shown on the phone and watch") {
                var language by remember { mutableStateOf(Settings.captionLanguage) }
                Text("Language", style = MaterialTheme.typography.titleSmall)
                CaptionLanguage.entries.forEach { option ->
                    RadioRow(
                        selected = language == option,
                        title = option.title,
                        subtitle = option.hint,
                        onClick = {
                            language = option
                            Settings.captionLanguage = option
                            onSettingsChanged()
                        },
                    )
                }
                Text(
                    "Speakers who mix Swahili and English: pick the language they use most. " +
                        "Kenyan English is the default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.padding(4.dp))

                var scale by remember { mutableFloatStateOf(Settings.captionTextScale) }
                Text("Text size: ${(scale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = {
                        Settings.captionTextScale = scale
                        WatchLink.pushStyle()
                    },
                    valueRange = 0.8f..1.8f,
                )
                Text(
                    "Habari yako? Sawa, twende.",
                    fontSize = (18 * scale).sp,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(Modifier.padding(4.dp))

                var hold by remember { mutableStateOf(Settings.holdTime) }
                Text("Fade older lines", style = MaterialTheme.typography.titleSmall)
                Text("Faded lines stay in the conversation. Scroll up to read them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HoldTime.entries.forEach { option ->
                    RadioRow(
                        selected = hold == option,
                        title = option.title,
                        onClick = {
                            hold = option
                            Settings.holdTime = option
                            WatchLink.pushStyle()
                        },
                    )
                }

                Spacer(Modifier.padding(4.dp))

                var rtl by remember { mutableStateOf(Settings.captionRightToLeft) }
                ToggleRow(
                    title = "Right-to-left text",
                    subtitle = "Swahili and English read left to right. Turn on only if you prefer it reversed.",
                    checked = rtl,
                    onChecked = {
                        rtl = it
                        Settings.captionRightToLeft = it
                        WatchLink.pushStyle()
                    },
                )

                Spacer(Modifier.padding(4.dp))
                Text("Microphone", style = MaterialTheme.typography.titleSmall)
                val installed = ModelFiles.isInstalled(context)
                Text(
                    if (installed) "Offline speech model: installed. Captions work with no internet."
                    else "Offline speech model: not installed. Using Android speech (needs internet for some languages).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var boost by remember { mutableFloatStateOf(Settings.micBoost) }
                Text("Sensitivity: ${boost.toInt()}x", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = boost,
                    onValueChange = { boost = it },
                    onValueChangeFinished = {
                        Settings.micBoost = boost
                        reloadIfRunning(context, ui)
                    },
                    valueRange = 1f..8f,
                    steps = 6,
                )
                Text(
                    "Raises quiet speech, for example across a room. Very high also raises background noise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var clean by remember { mutableStateOf(Settings.cleanAudio) }
                ToggleRow(
                    title = "Clean background noise",
                    subtitle = if (ModelFiles.isDenoiserInstalled(context)) {
                        "Removes hum and background noise before recognizing speech. Turn off if voices sound thin."
                    } else {
                        "Needs the noise model on the phone (denoiser.onnx). Not installed."
                    },
                    checked = clean,
                    onChecked = {
                        clean = it
                        Settings.cleanAudio = it
                        reloadIfRunning(context, ui)
                    },
                )
                var allMics by remember { mutableStateOf(Settings.useAllMics) }
                ToggleRow(
                    title = "Use all microphones",
                    subtitle = "Combines the phone's microphones for clearer speech. Falls back to one mic if the phone cannot.",
                    checked = allMics,
                    onChecked = {
                        allMics = it
                        Settings.useAllMics = it
                        reloadIfRunning(context, ui)
                    },
                )

                Spacer(Modifier.padding(4.dp))
                Text("Show these sounds in captions", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Shown as tags such as [Clapping] in the caption list and on the watch. They do not vibrate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EventRows(ui)

                var network by remember { mutableStateOf(Settings.allowNetworkCaptions) }
                ToggleRow(
                    title = "Allow network captions",
                    subtitle = "Used only when this phone has no offline speech model. Audio is sent to Google.",
                    checked = network,
                    onChecked = {
                        network = it
                        Settings.allowNetworkCaptions = it
                        onSettingsChanged()
                    },
                )
            }
        }

        // ---------------- Sound alerts ----------------
        item {
            SettingsSection("Sound alerts", "Sounds that trigger a vibration and an alert") {
                var threshold by remember { mutableFloatStateOf(Settings.threshold) }
                Text(
                    "Sensitivity: ${(threshold * 100).toInt()}% minimum confidence",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = {
                        Settings.threshold = threshold
                        reloadIfRunning(context, ui)
                    },
                    valueRange = 0.2f..0.9f,
                )
                Text(
                    "Lower catches more sounds but may give false alerts. Higher is quieter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.padding(4.dp))
                Text("Sounds", style = MaterialTheme.typography.titleSmall)
                SoundRows(ui)
            }
        }

        // ---------------- Vibration ----------------
        item {
            SettingsSection("Vibration", "Choose where alerts vibrate") {
                var vPhone by remember { mutableStateOf(Settings.vibratePhone) }
                ToggleRow(
                    title = "Vibrate this phone",
                    subtitle = "Alerts buzz this phone.",
                    checked = vPhone,
                    onChecked = {
                        vPhone = it
                        Settings.vibratePhone = it
                    },
                )
                var vWatch by remember { mutableStateOf(Settings.vibrateWatch) }
                ToggleRow(
                    title = "Vibrate my watch",
                    subtitle = if (ui.watchConnected) {
                        "Watch connected. Alerts buzz your watch over Bluetooth."
                    } else {
                        "No watch connected right now. Pair your watch in the Galaxy Wearable app."
                    },
                    checked = vWatch,
                    onChecked = {
                        vWatch = it
                        Settings.vibrateWatch = it
                    },
                )
            }
        }

        // ---------------- Privacy ----------------
        item {
            SettingsSection("Privacy", "What stays on your devices") {
                Text(
                    "Audio is analysed on this phone and is not saved. " +
                        "Only short text and sound names are sent to your watch.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EventRows(ui: UiState) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(Settings.captionEvents) }
    SoundLabels.EVENTS.forEach { (raw, short) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = raw in events,
                onCheckedChange = { checked ->
                    events = if (checked) events + raw else events - raw
                    Settings.captionEvents = events
                    reloadIfRunning(context, ui)
                },
            )
            Text(short, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SoundRows(ui: UiState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Settings.enabledLabels) }
    var patternDialogFor by remember { mutableStateOf<String?>(null) }
    var patternVersion by remember { mutableStateOf(0) }

    SoundLabels.ALL.forEach { (raw, short) ->
        val isOn = raw in enabled
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isOn,
                onCheckedChange = { checked ->
                    enabled = if (checked) enabled + raw else enabled - raw
                    Settings.enabledLabels = enabled
                    reloadIfRunning(context, ui)
                },
            )
            Column(Modifier.weight(1f)) {
                Text(short, style = MaterialTheme.typography.bodyLarge)
                // Reads patternVersion so the label refreshes after a change.
                val pattern = remember(patternVersion) { Settings.patternFor(raw) }
                Text(
                    "Vibration: ${pattern.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(role = Role.Button) { patternDialogFor = raw },
                )
            }
            TextButton(onClick = {
                Alerter.test(context, raw, Settings.patternFor(raw))
            }) { Text("Test") }
        }
    }

    patternDialogFor?.let { raw ->
        val current = Settings.patternFor(raw)
        AlertDialog(
            onDismissRequest = { patternDialogFor = null },
            title = { Text("Vibration for ${SoundLabels.shortName(raw)}") },
            text = {
                Column {
                    VibePattern.entries.forEach { option ->
                        RadioRow(
                            selected = option == current,
                            title = option.title,
                            onClick = {
                                Settings.setPattern(raw, option)
                                patternVersion++
                                Alerter.test(context, raw, option)
                                patternDialogFor = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { patternDialogFor = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
