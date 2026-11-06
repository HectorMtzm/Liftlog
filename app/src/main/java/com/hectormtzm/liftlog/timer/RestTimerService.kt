package com.hectormtzm.liftlog.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.hectormtzm.liftlog.MainActivity
import com.hectormtzm.liftlog.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that shows the rest countdown in a notification and alerts with a
 * vibration + notification when rest is over. Holds a partial wake lock only while counting,
 * so the alert fires on time with the screen off.
 */
class RestTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = RestTimer.state.value
        // Must call startForeground promptly after startForegroundService.
        startInForeground(buildCountdownNotification(state))
        if (state == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                RestTimer.state.collectLatest { s ->
                    if (s == null) {
                        stopSelfCleanly()
                        return@collectLatest
                    }
                    NotificationManagerCompat.from(this@RestTimerService).cancel(DONE_ID)
                    notifySafe(COUNTDOWN_ID, buildCountdownNotification(s))
                    acquireWakeLock(s.remainingMillis() + 10_000)
                    delay(s.remainingMillis())
                    onFinished(s)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun onFinished(s: RestTimerState) {
        vibrate()
        notifySafe(DONE_ID, buildDoneNotification(s))
        RestTimer.clearFinished(s)
        stopSelfCleanly()
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, COUNTDOWN_ID, n, type)
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildCountdownNotification(s: RestTimerState?): Notification {
        val remaining = s?.remainingMillis() ?: 0L
        return NotificationCompat.Builder(this, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Resting")
            .setContentText(s?.label?.takeIf { it.isNotBlank() }?.let { "Next: $it" } ?: "Last set done")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remaining)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun buildDoneNotification(s: RestTimerState): Notification =
        NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest over")
            .setContentText(if (s.label.isNotBlank()) "Next: ${s.label}" else "All sets done. Finish your workout.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(contentIntent())
            .build()

    private fun notifySafe(id: Int, n: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
        }
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Liftlog:RestTimer").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_COUNTDOWN = "rest_countdown"
        private const val CHANNEL_DONE = "rest_done"
        private const val COUNTDOWN_ID = 1001
        private const val DONE_ID = 1002

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_COUNTDOWN, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the countdown while you rest between sets"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts you when it is time for the next set"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    setSound(
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
            )
        }

    }
}
