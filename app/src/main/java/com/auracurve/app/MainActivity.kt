package com.auracurve.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.auracurve.app.data.preset.PresetShareCodec
import com.auracurve.app.player.PlayerLaunch
import com.auracurve.app.quick.QuickCommandIntents
import com.auracurve.app.ui.main.MainViewModel
import com.auracurve.app.ui.navigation.AppNavHost
import com.auracurve.app.ui.player.PlayerViewModel
import com.auracurve.app.ui.theme.AuraCurveTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Activity-scoped, handed to AppNavHost explicitly: a tap on the playback notification
    // (onNewIntent below) has to reach the same instance the player screen shows.
    private val playerViewModel: PlayerViewModel by viewModels()

    // AppNavHost reads MainViewModel from the same activity scope (hiltViewModel() outside a nav
    // entry), so a preset link handed in here reaches the instance the equalizer screen shows.
    private val mainViewModel: MainViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        // Only on a fresh start: after a rotation the same intent is delivered again
        // and must not import the preset link a second time.
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
            handleOpenPlayerIntent(intent)
        }
        setContent {
            AuraCurveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost(playerViewModel = playerViewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // See AuraCurveApplication.startSessionService: a process created in the background could not start it.
        (application as? AuraCurveApplication)?.startSessionService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    // The playback notification, the lock screen card and the car's session open the Compose
    // player screen here (they used to start the old View-based PlayerActivity).
    private fun handleOpenPlayerIntent(intent: Intent?) {
        if (intent?.action == PlayerLaunch.ACTION_OPEN_PLAYER) playerViewModel.requestNowPlaying()
    }

    // A quick-settings tile, the widget or a shared preset link.
    private fun handleIncomingIntent(intent: Intent?) {
        // A tile or the widget asked for something while no screen of the app was alive.
        QuickCommandIntents.commandOf(intent)?.let { command ->
            mainViewModel.queueQuickCommand(command)
            return
        }
        // A scanned QR code or tapped link: auracurve://preset?... (also the old hardbasseq://, roadmap 06, P5-4).
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        if (link != null && PresetShareCodec.isShareLink(link)) {
            mainViewModel.importPresetLink(link)
        }
    }

    // Without this, the AudioSessionForegroundService's notification (explaining why
    // AuraCurve keeps running) stays invisible on API 33+ - the service itself still
    // works, but the user has no way to know it's there.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
