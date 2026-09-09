package com.bifrost.twp.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.bifrost.twp.core.BifrostBridgeService
import com.bifrost.twp.data.ProxyRepository
import com.bifrost.twp.model.TwpLinkParser

/**
 * Zero-Click Deep Link Handler for twp:// links.
 *
 * Intercepts twp:// links clicked in web browsers or messaging apps,
 * activates the bridge service in the background, and forwards the connection
 * directly to official Telegram (tg://socks?server=127.0.0.1&port=5050) without
 * opening the main application UI.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataString = intent?.dataString ?: intent?.data?.toString()

        if (!dataString.isNullOrBlank()) {
            val repository = ProxyRepository.getInstance(applicationContext)
            val parsedConfig = TwpLinkParser.parseLink(dataString)

            if (parsedConfig != null) {
                // 1. Save and activate proxy in repository
                repository.addOrUpdateProxy(parsedConfig, makeActive = true)

                // 2. Ensure local bridge service is running
                BifrostBridgeService.start(applicationContext)

                // 3. Immediately launch official Telegram with local SOCKS5 confirmation
                val localPort = repository.localPortFlow.value
                val tgUri = Uri.parse("tg://socks?server=127.0.0.1&port=$localPort")
                val tgIntent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                try {
                    startActivity(tgIntent)
                    Toast.makeText(this, "Bifrost: Connected -> ${parsedConfig.name}", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    // Fallback to web link if scheme fails
                    try {
                        val webUri = Uri.parse("https://t.me/socks?server=127.0.0.1&port=$localPort")
                        startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (_: Exception) {}
                }
            } else {
                Toast.makeText(this, "Bifrost: Invalid twp:// link format", Toast.LENGTH_SHORT).show()
            }
        }

        // Close immediately without showing any window
        finish()
    }
}
