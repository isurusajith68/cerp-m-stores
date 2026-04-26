package com.ceyinfo.cerpstores

import android.app.Application

class StoresApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: StoresApp
            private set
    }
}
