package com.hectormtzm.liftlog.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hectormtzm.liftlog.timer.RestTimerState
import com.hectormtzm.liftlog.ui.formatClock
import com.hectormtzm.liftlog.ui.theme.TimerTextStyle
import kotlinx.coroutines.delay

/** Slim countdown bar pinned to the bottom of the workout screen. */
@Composable
fun RestTimerBar(
    state: RestTimerState,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val remaining by produceState(initialValue = state.remainingMillis(), state) {
        while (true) {
            value = state.remainingMillis()
            if (value <= 0) break
            delay(100)
        }
    }
    val progress = (remaining / (state.totalSeconds * 1000f)).coerceIn(0f, 1f)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                strokeCap = StrokeCap.Butt,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { onAdjust(-15) }) { Text("-15s") }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock((remaining + 999) / 1000), style = TimerTextStyle)
                    Text(
                        if (state.label.isNotBlank()) "Next: ${state.label}" else "Last set done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onAdjust(15) }) { Text("+15s") }
                IconButton(onClick = onSkip) { Icon(Icons.Filled.SkipNext, "Skip rest") }
            }
        }
    }
}
