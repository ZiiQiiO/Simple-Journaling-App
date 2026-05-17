package com.ziiqiio.simplejournalingapp

import android.app.Application

class JournalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
