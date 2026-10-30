package com.flux.relay

import android.app.Application

/**
 * Process-wide entry point. Holds the lazily-built [ServiceLocator] so the mesh engine,
 * database and crypto identity live for the whole process rather than per-Activity.
 */
open class RelayApp : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)
    }
}
