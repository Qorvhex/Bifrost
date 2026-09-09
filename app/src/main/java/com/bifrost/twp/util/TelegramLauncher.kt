package com.bifrost.twp.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Utility for opening Telegram with the local SOCKS5 proxy configuration.
 *
 * Utilizes https://t.me/socks?server=127.0.0.1&port=<PORT> and tg://socks?server=127.0.0.1&port=<PORT>
 * with explicit package targeting to avoid opening the browser or hitting ISP domain blocks on t.me.
 */
object TelegramLauncher {

    val KNOWN_TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "nekox.messenger",
        "org.forkclient.messenger"
    )

    fun getTelegramSocksUri(port: Int): Uri {
        return Uri.parse("https://t.me/socks?server=127.0.0.1&port=$port")
    }

    fun getTelegramSchemeUri(port: Int): Uri {
        return Uri.parse("tg://socks?server=127.0.0.1&port=$port")
    }

    fun openTelegramSocks(context: Context, port: Int): Boolean {
        val appContext = context.applicationContext
        val httpsUri = getTelegramSocksUri(port)
        val schemeUri = getTelegramSchemeUri(port)

        // 1. Try explicit package targeting with https://t.me/socks (bypasses browser)
        for (pkg in KNOWN_TELEGRAM_PACKAGES) {
            try {
                appContext.packageManager.getPackageInfo(pkg, 0)
                val intent = Intent(Intent.ACTION_VIEW, httpsUri).apply {
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                appContext.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // 2. Try explicit package targeting with tg://socks
        for (pkg in KNOWN_TELEGRAM_PACKAGES) {
            try {
                appContext.packageManager.getPackageInfo(pkg, 0)
                val intent = Intent(Intent.ACTION_VIEW, schemeUri).apply {
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                appContext.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // 3. Fallback: Generic ACTION_VIEW with tg://socks (handled natively by Telegram apps)
        try {
            val intent = Intent(Intent.ACTION_VIEW, schemeUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
            return true
        } catch (_: Exception) {}

        // 4. Final fallback: Generic ACTION_VIEW with https://t.me/socks
        try {
            val intent = Intent(Intent.ACTION_VIEW, httpsUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
            return true
        } catch (_: Exception) {
            Toast.makeText(appContext, "Telegram app not found", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun createPendingIntent(context: Context, port: Int, requestCode: Int = 1): PendingIntent {
        val httpsUri = getTelegramSocksUri(port)
        val intent = Intent(Intent.ACTION_VIEW, httpsUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        for (pkg in KNOWN_TELEGRAM_PACKAGES) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                intent.setPackage(pkg)
                break
            } catch (_: Exception) {}
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
