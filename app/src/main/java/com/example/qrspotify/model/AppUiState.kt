package com.example.qrspotify.model

data class AppUiState(
    val scanStatus: String = "Nog niet gescand",
    val connectionStatus: String = "Niet verbonden",
    val playbackStatus: String = "Geen playback",
    val lastRawQr: String = "",
    val lastResolvedSpotifyUri: String = "",
    val currentTrackName: String = "Geen track geladen",
    val currentArtistName: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = true,
    val lastError: String = ""
)
