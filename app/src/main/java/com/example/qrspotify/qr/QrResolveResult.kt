package com.example.qrspotify.qr

enum class QrSource {
    DIRECT_SPOTIFY_URI,
    SPOTIFY_URL,
    LOCAL_CODE,
    INVALID
}

data class QrResolveResult(
    val isValid: Boolean,
    val spotifyUri: String? = null,
    val message: String,
    val source: QrSource
)
