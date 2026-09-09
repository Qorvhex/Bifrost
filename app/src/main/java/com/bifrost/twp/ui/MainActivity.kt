package com.bifrost.twp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bifrost.twp.data.ProxyRepository
import com.bifrost.twp.ui.components.MainScreen
import com.bifrost.twp.ui.theme.BifrostTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: ProxyRepository

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission granted/denied
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = ProxyRepository.getInstance(applicationContext)

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            BifrostTheme {
                MainScreen(
                    repository = repository,
                    onLaunchQrScanner = {
                        val intent = Intent(this, QrScannerActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
