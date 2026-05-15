package com.example.qrspotify.qr

object LocalTrackMapper {

    private val localMap = mapOf(
        "HITSTER_DEMO_1" to "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
        "HITSTER_DEMO_2" to "spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
        "HITSTER_DEMO_3" to "spotify:track:7ouMYWpwJ422jRcDASZB7P"
    )

    fun resolve(rawCode: String): String? {
        return localMap[rawCode.trim().uppercase()]
    }
}
