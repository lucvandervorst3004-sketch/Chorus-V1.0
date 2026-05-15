package com.example.qrspotify.data

import android.content.Context
import android.content.SharedPreferences
import com.example.qrspotify.model.AppUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppStateStore {

    private const val MAX_STORED_QR_LENGTH = 512

    private const val PREFS_NAME = "qrspotify_prefs"
    private const val KEY_LAST_RAW = "last_raw_qr"
    private const val KEY_LAST_URI = "last_resolved_uri"

    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRaw = prefs.getString(KEY_LAST_RAW, "").orEmpty()
        val lastUri = prefs.getString(KEY_LAST_URI, "").orEmpty()
        _state.update {
            it.copy(
                lastRawQr = lastRaw,
                lastResolvedSpotifyUri = lastUri
            )
        }
    }

    fun setScanStatus(status: String) {
        _state.update { it.copy(scanStatus = status) }
    }

    fun setScanResult(raw: String, resolvedUri: String) {
        val safeRaw = raw.trim().take(MAX_STORED_QR_LENGTH)

        prefs.edit()
            .putString(KEY_LAST_RAW, safeRaw)
            .putString(KEY_LAST_URI, resolvedUri)
            .apply()

        _state.update {
            it.copy(
                scanStatus = "QR succesvol gelezen",
                lastRawQr = safeRaw,
                lastResolvedSpotifyUri = resolvedUri,
                lastError = ""
            )
        }
    }

    fun setConnection(isConnected: Boolean, status: String, isConnecting: Boolean = false) {
        _state.update {
            it.copy(
                isConnected = isConnected,
                isConnecting = isConnecting,
                connectionStatus = status
            )
        }
    }

    fun setPlayback(isPlaying: Boolean, isPaused: Boolean, status: String) {
        _state.update {
            it.copy(
                isPlaying = isPlaying,
                isPaused = isPaused,
                playbackStatus = status
            )
        }
    }

    fun setCurrentTrack(name: String, artist: String, uri: String? = null) {
        _state.update {
            it.copy(
                currentTrackName = name,
                currentArtistName = artist
            )
        }
    }

    fun setError(message: String) {
        _state.update { it.copy(lastError = message) }
    }

    fun clearError() {
        _state.update { it.copy(lastError = "") }
    }
}
