package com.bifrost.twp

import android.app.Application
import com.bifrost.twp.core.BifrostBridgeService
import com.bifrost.twp.data.ProxyRepository

class BifrostApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val repository = ProxyRepository.getInstance(this)
        // If an active proxy is already selected, warm up and start the local bridge service
        if (repository.activeProxyFlow.value != null) {
            BifrostBridgeService.start(this)
        }
    }
}
