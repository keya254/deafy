package com.deafy.phone

import android.app.Application

import kotlinx.coroutines.launch

class DeafyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        History.init(this)
        // Show the most recent captions as soon as the app opens.
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            AppState.restoreCaptions(History.latest(300).map { CaptionLine(it.text, it.ts) })
        }
        WatchLink.init(this)
    }
}
