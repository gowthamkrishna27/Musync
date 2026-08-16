package com.musync.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.musync.app.ui.navigation.MainApp
import com.musync.app.ui.theme.MusyncTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()
        handleDeepLink(intent)

        setContent {
            MusyncTheme {
                val app = application as MusyncApplication
                val navController = rememberNavController()
                MainApp(app = app, navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val scheme = data.scheme
            val host = data.host
            val pathSegments = data.pathSegments

            if ((scheme == "missingcore" || scheme == "musync") && host == "track" && pathSegments.isNotEmpty()) {
                val trackId = pathSegments[0]
                val app = application as MusyncApplication
                CoroutineScope(Dispatchers.IO).launch {
                    val track = app.container.musicRepository.getTrack(trackId).getOrNull()
                    if (track != null) {
                        launch(Dispatchers.Main) {
                            app.container.playbackManager.play(track)
                        }
                    }
                }
            }
        }
    }
}

