package com.example.qrspotify.qr

object QrParser {

    private val spotifyTrackUriRegex = Regex("^spotify:track:[A-Za-z0-9]{22}$")
    private val spotifyTrackUrlRegex = Regex("^https?://open\\.spotify\\.com/track/([A-Za-z0-9]{22})(\\?.*)?$")

    fun resolve(rawValue: String): QrResolveResult {
        val trimmed = rawValue.trim()

        if (spotifyTrackUriRegex.matches(trimmed)) {
            return QrResolveResult(
                isValid = true,
                spotifyUri = trimmed,
                message = "Directe Spotify track URI gevonden.",
                source = QrSource.DIRECT_SPOTIFY_URI
            )
        }

        val urlMatch = spotifyTrackUrlRegex.find(trimmed)
        if (urlMatch != null) {
            val trackId = urlMatch.groupValues[1]
            return QrResolveResult(
                isValid = true,
                spotifyUri = "spotify:track:$trackId",
                message = "Spotify track-URL omgezet naar URI.",
                source = QrSource.SPOTIFY_URL
            )
        }

        val mappedTrack = LocalTrackMapper.resolve(trimmed)
        if (mappedTrack != null) {
            return QrResolveResult(
                isValid = true,
                spotifyUri = mappedTrack,
                message = "Lokale code succesvol vertaald naar Spotify URI.",
                source = QrSource.LOCAL_CODE
            )
        }

        return QrResolveResult(
            isValid = false,
            spotifyUri = null,
            message = "Ongeldige QR. Verwacht spotify:track:..., een open.spotify.com/track-link of een bekende lokale code.",
            source = QrSource.INVALID
        )
    }
}
