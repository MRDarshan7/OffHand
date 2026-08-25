package com.offhand

import android.app.Application

class OffhandApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.connectivityObserver.start()
    }
}
