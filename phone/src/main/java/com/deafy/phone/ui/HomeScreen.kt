package com.deafy.phone.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deafy.phone.AppState
import com.deafy.phone.HoldTime
import com.deafy.phone.Settings
import com.deafy.phone.SoundAlert
import com.deafy.phone.UiState
import com.deafy.phone.WatchLink
import com.deafy.shared.SoundLabels
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun HomeScreen(
    ui: UiState,
    onCaptionsToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onClearCaptions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(ui)
        CaptionCard(ui, onCaptionsToggle, onClearCaptions)
        SoundCard(ui, onSoundToggle)
        Text(
            text = ui.status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(ui: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Deafy", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { WatchLink.refresh() },
                label = {
                    Text(
                        if (ui.watchConnected) "Watch: ${ui.watchName ?: "connected"} (Bluetooth)"
                        else "No watch found",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            )
            if (!ui.watchConnected) {
                TextButton(onClick = { WatchLink.refresh() }) { Text("Retry") }
            }
        }
        if (!ui.watchConnected) {
            Text(
                "Phone-only mode. Make sure the watch is paired in Galaxy Wearable and Bluetooth is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaptionCard(ui: UiState, onToggle: (Boolean) -> Unit, onClear: () -> Unit) {
    val scale = Settings.captionTextScale
    val rtl = Settings.captionRightToLeft
    val hold = Settings.holdTime

    // Ticks once a second so older lines fade on time.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemCount = ui.captions.size + (if (ui.partial.isNotBlank()) 1 else 0)
    val lastIndex = itemCount - 1

    // True when the user is looking at the newest line (within one item of the bottom).
    val atBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            itemCount == 0 || lastVisible >= lastIndex - 1
        }
    }
    var unseenWhileAway by remember { mutableStateOf(0) }
    var lastSeenCount by remember { mutableStateOf(itemCount) }

    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        if (atBottom) {
            unseenWhileAway = 0
            listState.animateScrollToItem(lastIndex)
        } else if (itemCount > lastSeenCount) {
            unseenWhileAway += itemCount - lastSeenCount
        }
        lastSeenCount = itemCount
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Live captions", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (ui.captionsOn) "On · ${Settings.captionLanguage.title}" else "Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ui.captionsOn,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = "Live captions" },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (itemCount == 0) {
                    Text(
                        text = if (ui.captionsOn) "Listening… speech will appear here." else "Turn on live captions to read speech here.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    // Full conversation, oldest at top, newest at bottom. Scroll up to read earlier lines.
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(ui.captions, key = { it.ts }) { line ->
                                val faded = hold != HoldTime.KEEP && now - line.ts >= hold.seconds * 1000L
                                CaptionRow(
                                    time = timeFormat.format(Date(line.ts)),
                                    text = line.text,
                                    scale = scale,
                                    faded = faded,
                                    partial = false,
                                )
                            }
                            if (ui.partial.isNotBlank()) {
                                item {
                                    CaptionRow(
                                        time = timeFormat.format(Date(now)),
                                        text = ui.partial + " …",
                                        scale = scale,
                                        faded = false,
                                        partial = true,
                                    )
                                }
                            }
                        }
                    }

                    if (!atBottom && unseenWhileAway > 0) {
                        AssistChip(
                            onClick = {
                                unseenWhileAway = 0
                                scope.launch { listState.animateScrollToItem(lastIndex) }
                            },
                            label = { Text("↓ $unseenWhileAway new") },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${ui.captions.size} line${if (ui.captions.size == 1) "" else "s"} in this session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClear, enabled = ui.captions.isNotEmpty()) {
                    Text("Clear captions")
                }
            }
        }
    }
}

@Composable
private fun CaptionRow(time: String, text: String, scale: Float, faded: Boolean, partial: Boolean) {
    val alpha = if (faded) 0.5f else 1f
    Column {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
        Text(
            text = text,
            fontSize = (20 * scale).sp,
            lineHeight = (28 * scale).sp,
            fontStyle = if (partial) FontStyle.Italic else FontStyle.Normal,
            color = (if (partial) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                .copy(alpha = alpha),
        )
    }
}

@Composable
private fun SoundCard(ui: UiState, onToggle: (Boolean) -> Unit) {
    val enabled = Settings.enabledLabels
        .mapNotNull { SoundLabels.ALL[it] }
        .sorted()
    val listing = when {
        enabled.isEmpty() -> "No sounds selected. Choose them in Settings."
        enabled.size <= 3 -> enabled.joinToString(", ")
        else -> enabled.take(3).joinToString(", ") + " and ${enabled.size - 3} more"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sound alerts", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (ui.soundOn) "On · listening for sounds" else "Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ui.soundOn,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = "Sound alerts" },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
            Text("Alerts for: $listing", style = MaterialTheme.typography.bodyMedium)

            ui.alerts.firstOrNull()?.let { LastAlertRow(it) }
        }
    }
}

@Composable
private fun LastAlertRow(alert: SoundAlert) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                "Possible ${alert.label.lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                "${timeFormat.format(Date(alert.ts))} · ${(alert.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
