package com.bifrost.twp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bifrost.twp.core.BifrostBridgeService
import com.bifrost.twp.data.ProxyRepository

/**
 * Automatically starts the Bifrost bridge upon device reboot
 * if an active proxy configuration exists.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val repository = ProxyRepository.getInstance(context)
            if (repository.activeProxyFlow.value != null) {
                BifrostBridgeService.start(context)
            }
        }
    }
}
