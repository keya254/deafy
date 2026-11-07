package com.deafy.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.deafy.shared.Command
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhoneLink.init(this)
        setContent {
            MaterialTheme {
                WatchScreen()
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

private val CaptionBg = Color(0xFF151515)
private val SoftWhite = Color(0xFFF2F2F2)
private val Muted = Color(0xFFB8C2C0)
private val AccentGreen = Color(0xFF7FE0C8)
private val AlertRed = Color(0xFFFF8A80)

@Composable
fun WatchScreen() {
    val ui by WearState.state.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        HistoryScreen(ui, onBack = { showHistory = false })
    } else {
        MainScreen(ui, onOpenHistory = { showHistory = true })
    }
}

@Composable
private fun MainScreen(ui: WearUi, onOpenHistory: () -> Unit) {
    val context = LocalContext.current

    // Ticks so caption lines past their hold time leave the window.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val visibleLines = ui.lines
        .filter { ui.holdSeconds == 0 || now - it.ts < ui.holdSeconds * 1000L }
        .takeLast(3)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = if (ui.phoneConnected) "Phone connected" else "Phone not connected",
                color = if (ui.phoneConnected) AccentGreen else Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }

        item { CaptionWindow(visibleLines, ui.partial, ui.textScale, ui.rightToLeft, ui.captionsOn) }

        ui.alertLabel?.let { label ->
            item { AlertCard(label, ui.alertConfidence, onDismiss = { WearState.update { it.copy(alertLabel = null) } }) }
        }

        if (!ui.phoneConnected) {
            item {
                Text(
                    text = ui.note,
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        item {
            Button(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("History · ${ui.lines.size}", fontSize = 13.sp) }
        }

        toggleItem(
            title = "Captions",
            on = ui.captionsOn,
            onTap = {
                WearState.update { it.copy(captionsOn = !ui.captionsOn) }
                PhoneLink.sendCommand(context, if (ui.captionsOn) Command.STOP_CAPTIONS else Command.START_CAPTIONS)
            },
        )
        toggleItem(
            title = "Sound alerts",
            on = ui.soundOn,
            onTap = {
                WearState.update { it.copy(soundOn = !ui.soundOn) }
                PhoneLink.sendCommand(context, if (ui.soundOn) Command.STOP_SOUND else Command.START_SOUND)
            },
        )
    }
}

/** Full conversation with timestamps. Opens at the newest line; scrolling up loads older pages from the phone. */
@Composable
private fun HistoryScreen(ui: WearUi, onBack: () -> Unit) {
    val context = LocalContext.current
    val lines = ui.lines
    val listState = rememberScalingLazyListState()
    val lastTs = lines.lastOrNull()?.ts
    val firstTs = lines.firstOrNull()?.ts

    // Opening the screen with nothing loaded: ask the phone for the latest captions.
    LaunchedEffect(Unit) {
        if (lines.isEmpty()) PhoneLink.requestHistory(context, null)
    }

    // New line at the bottom: follow it.
    LaunchedEffect(lastTs) {
        if (lastTs != null) listState.scrollToItem(lines.size)
    }

    // Older page prepended: keep the reader on the same line.
    var previousFirst by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(firstTs) {
        val before = previousFirst
        if (before != null && firstTs != null && firstTs < before) {
            val index = lines.indexOfFirst { it.ts == before }
            if (index >= 0) listState.scrollToItem(index + 1)
        }
        previousFirst = firstTs
    }

    // Near the top of the list: request the next older page.
    LaunchedEffect(listState.centerItemIndex) {
        if (listState.centerItemIndex <= 3 && ui.hasMoreOlder && !ui.loadingOlder && lines.isNotEmpty()) {
            PhoneLink.requestHistory(context, firstTs)
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Button(onClick = onBack, colors = ButtonDefaults.secondaryButtonColors()) { Text("Back", fontSize = 13.sp) }
        }
        if (ui.loadingOlder) {
            item { Text("Loading earlier captions…", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
        } else if (!ui.hasMoreOlder && lines.isNotEmpty()) {
            item { Text("Start of conversation", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        if (lines.isEmpty() && ui.partial.isBlank()) {
            item { Text("No captions yet", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
        items(lines.size) { index ->
            val line = lines[index]
            CompositionLocalProvider(
                LocalLayoutDirection provides if (ui.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Column {
                    Text(formatTime(line.ts), color = Muted, fontSize = 10.sp)
                    Text(line.text, color = SoftWhite, fontSize = (15 * ui.textScale).sp, textAlign = TextAlign.Start)
                }
            }
        }
        if (ui.partial.isNotBlank()) {
            item {
                Text("${ui.partial} …", color = AccentGreen, fontSize = (15 * ui.textScale).sp, textAlign = TextAlign.Start)
            }
        }
    }
}

/** "HH:mm" for today, "d MMM HH:mm" for older captions. */
private fun formatTime(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) timeFormat.format(Date(ts)) else dayFormat.format(Date(ts))
}

/**
 * Caption window: newest line at the bottom, older lines fade upward.
 * Right-to-left only when the phone's setting asks for it.
 */
@Composable
private fun CaptionWindow(
    lines: List<WatchLine>,
    partial: String,
    scale: Float,
    rtl: Boolean,
    captionsOn: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CaptionBg)
            .padding(12.dp),
    ) {
        if (lines.isEmpty() && partial.isBlank()) {
            Text(
                text = if (captionsOn) "Listening…" else "Captions are off",
                color = Muted,
                fontSize = (14 * scale).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val total = lines.size + (if (partial.isNotBlank()) 1 else 0)
                    lines.forEachIndexed { index, line ->
                        val alpha = 0.45f + 0.55f * (index + 1) / total.coerceAtLeast(1)
                        Text(
                            text = line.text,
                            color = SoftWhite.copy(alpha = alpha),
                            fontSize = (16 * scale).sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                    if (partial.isNotBlank()) {
                        Text(
                            text = "$partial …",
                            color = AccentGreen,
                            fontSize = (16 * scale).sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(label: String, confidence: Float, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF3A1A17))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Possible $label",
            color = AlertRed,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${(confidence * 100).toInt()}% confident",
            color = Muted,
            fontSize = 12.sp,
        )
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.heightIn(min = 40.dp),
        ) { Text("Got it", fontSize = 13.sp) }
    }
}

private fun ScalingLazyListScope.toggleItem(title: String, on: Boolean, onTap: () -> Unit) {
    item {
        Button(
            onClick = onTap,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = if (on) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 14.sp, textAlign = TextAlign.Center)
                Text(if (on) "On · tap to turn off" else "Off · tap to turn on", fontSize = 10.sp)
            }
        }
    }
}
