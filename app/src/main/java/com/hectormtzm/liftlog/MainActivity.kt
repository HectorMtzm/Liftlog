package com.hectormtzm.liftlog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import com.hectormtzm.liftlog.timer.RestTimerService
import com.hectormtzm.liftlog.ui.LiftlogRoot
import com.hectormtzm.liftlog.ui.theme.LiftlogTheme

class MainActivity : ComponentActivity() {
    /** Incremented whenever the app should jump to the active workout (e.g. notification tap). */
    private val openWorkoutRequests = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        RestTimerService.createChannels(this)
        handleIntent(intent)
        maybeRequestNotifications()
        setContent {
            LiftlogTheme {
                LiftlogRoot(openWorkoutRequests = openWorkoutRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) {
            intent.removeExtra(EXTRA_OPEN_WORKOUT)
            openWorkoutRequests.intValue += 1
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }
}
