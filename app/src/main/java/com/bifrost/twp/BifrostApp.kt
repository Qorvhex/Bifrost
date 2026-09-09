package com.bifrost.twp

import android.app.Application
import com.bifrost.twp.core.BifrostBridgeService
import com.bifrost.twp.data.ProxyRepository

class BifrostApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val repository = ProxyRepository.getInstance(this)
        // Only start if the user had explicitly turned on the bridge
        if (repository.isBridgeEnabledFlow.value && repository.activeProxyFlow.value != null) {
            BifrostBridgeService.start(this)
        }
    }
}
