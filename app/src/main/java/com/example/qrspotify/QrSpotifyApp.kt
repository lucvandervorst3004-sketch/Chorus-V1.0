package com.example.qrspotify

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.practice.PracticeStatsStore
import com.example.qrspotify.spotify.SpotifyManager

class QrSpotifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStateStore.init(this)
        PracticeStatsStore.init(this)
        registerActivityLifecycleCallbacks(SpotifyRetryLifecycleCallbacks)
    }

    private object SpotifyRetryLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            SpotifyManager.resumePendingConnectionAfterSpotifyReturn(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
