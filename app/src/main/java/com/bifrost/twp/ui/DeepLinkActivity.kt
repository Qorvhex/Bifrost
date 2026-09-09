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
 * Zero-Click Deep Link Handler.
 *
 * Intercepts twp:// and tg://worker links clicked in any browser or messenger,
 * immediately activates the local bridge service in the background, and forwards
 * directly to the official Telegram proxy confirmation screen (tg://socks?server=127.0.0.1&port=5050)
 * without opening the main UI.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataString = intent?.dataString ?: intent?.data?.toString()

        if (!dataString.isNullOrBlank()) {
            val repository = ProxyRepository.getInstance(applicationContext)
            val parsedConfig = TwpLinkParser.parseLink(dataString)

            if (parsedConfig != null) {
                // 1. Save and activate proxy configuration immediately
                repository.addOrUpdateProxy(parsedConfig, makeActive = true)

                // 2. Warm up and start the local SOCKS5 bridge service
                BifrostBridgeService.start(applicationContext)

                // 3. Immediately launch official Telegram with local SOCKS5 proxy setup
                val localPort = repository.localPortFlow.value
                val tgUri = Uri.parse("tg://socks?server=127.0.0.1&port=$localPort")
                val tgIntent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                try {
                    startActivity(tgIntent)
                    Toast.makeText(this, "Bifrost: Connected -> ${parsedConfig.name}", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    // Fallback to web link if scheme is unhandled
                    try {
                        val webUri = Uri.parse("https://t.me/socks?server=127.0.0.1&port=$localPort")
                        startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        })
                    } catch (_: Exception) {}
                }
            } else {
                Toast.makeText(this, "Bifrost: Invalid link format", Toast.LENGTH_SHORT).show()
            }
        }

        // Close immediately without animation flicker
        finish()
        overridePendingTransition(0, 0)
    }
}
