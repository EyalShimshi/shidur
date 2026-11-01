package io.github.eyalshimshi.shidur

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi

class ShidurApplication : Application() {

    lateinit var container: AppContainer
        private set

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        try {
            // Must run on the main thread before Cast widgets or the CastPlayer are created.
            Cast.getSingletonInstance(this).initialize()
        } catch (t: Throwable) {
            Log.w(TAG, "Google Cast is not available on this device", t)
        }
    }

    private companion object {
        const val TAG = "Shidur"
    }
}
