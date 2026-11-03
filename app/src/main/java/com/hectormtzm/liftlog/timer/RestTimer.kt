package com.hectormtzm.liftlog.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A running rest countdown. Times are based on [SystemClock.elapsedRealtime]. */
data class RestTimerState(
    val endAtElapsed: Long,
    val totalSeconds: Int,
    val label: String,
) {
    fun remainingMillis(now: Long = SystemClock.elapsedRealtime()): Long = (endAtElapsed - now).coerceAtLeast(0)
}

/** Process-wide rest timer. The UI reads [state]; [RestTimerService] keeps it alive in the background. */
object RestTimer {
    private val _state = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    fun start(context: Context, seconds: Int, label: String) {
        if (seconds <= 0) {
            stop(context)
            return
        }
        _state.value = RestTimerState(
            endAtElapsed = SystemClock.elapsedRealtime() + seconds * 1000L,
            totalSeconds = seconds,
            label = label,
        )
        ensureService(context)
    }

    fun adjust(context: Context, deltaSeconds: Int) {
        val s = _state.value ?: return
        val newEnd = s.endAtElapsed + deltaSeconds * 1000L
        if (newEnd <= SystemClock.elapsedRealtime()) {
            stop(context)
            return
        }
        _state.value = s.copy(endAtElapsed = newEnd, totalSeconds = (s.totalSeconds + deltaSeconds).coerceAtLeast(1))
        ensureService(context)
    }

    fun stop(context: Context) {
        _state.value = null
        context.stopService(Intent(context, RestTimerService::class.java))
    }

    /** Called by the service when the countdown reaches zero. */
    internal fun clearFinished(state: RestTimerState) {
        _state.compareAndSet(state, null)
    }

    private fun ensureService(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, RestTimerService::class.java))
        }
    }
}
