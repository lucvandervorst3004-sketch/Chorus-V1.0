package com.example.qrspotify

import android.app.Application
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.practice.PracticeStatsStore

class QrSpotifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStateStore.init(this)
        PracticeStatsStore.init(this)
    }
}