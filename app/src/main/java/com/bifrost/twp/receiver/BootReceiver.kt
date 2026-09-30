package com.bifrost.twp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bifrost.twp.core.BifrostBridgeService
import com.bifrost.twp.data.ProxyRepository

/**
 * Automatically starts the Bifrost bridge upon device reboot or user unlock
 * if an active proxy configuration exists and "Run on Startup" is enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isBootOrUnlock = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                action == Intent.ACTION_USER_PRESENT

        if (!isBootOrUnlock) return

        try {
            val repository = ProxyRepository.getInstance(context)
            val isRunOnStartup = repository.runOnStartupFlow.value
            val hasActiveProxy = repository.activeProxyFlow.value != null

            if (isRunOnStartup && hasActiveProxy) {
                if (!BifrostBridgeService.serviceRunning.value) {
                    repository.setBridgeEnabled(true)
                    BifrostBridgeService.start(context)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
