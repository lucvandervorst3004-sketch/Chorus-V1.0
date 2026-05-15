package com.example.qrspotify.spotify

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.qrspotify.BuildConfig
import com.example.qrspotify.data.AppStateStore
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState

object SpotifyManager {

    private const val TAG = "SpotifyManager"
    private const val CONNECT_TIMEOUT_MS = 60000L
    private const val SHUFFLE_AFTER_CONTEXT_DELAY_MS = 450L
    private const val PLAYBACK_VERIFY_DELAY_MS = 900L
    private const val MAX_PLAYBACK_START_ATTEMPTS = 3

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var isConnecting: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectTimeoutRunnable: Runnable? = null
    private var playbackVerifyRunnable: Runnable? = null
    private var playbackStartGeneration: Int = 0

    fun connect(activity: Activity, onFinished: ((Boolean, String) -> Unit)? = null) {
        Log.d(TAG, "connect() gestart")

        if (!SpotifyAppRemote.isSpotifyInstalled(activity)) {
            val message = "Spotify is niet geïnstalleerd op dit toestel."
            Log.e(TAG, message)
            AppStateStore.setConnection(false, "Spotify ontbreekt")
            AppStateStore.setPlayback(false, true, "Geen playback")
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        val currentRemote = spotifyAppRemote
        if (currentRemote != null && currentRemote.isConnected) {
            Log.d(TAG, "Spotify was al verbonden")
            AppStateStore.setConnection(true, "Verbonden met Spotify")
            AppStateStore.clearError()
            subscribeToPlayerState()
            refreshPlayerState()
            onFinished?.invoke(true, "Al verbonden")
            return
        }

        if (isConnecting) {
            val message = "Spotify is al aan het verbinden."
            Log.d(TAG, message)
            onFinished?.invoke(false, message)
            return
        }

        resetConnectionState()

        isConnecting = true
        AppStateStore.setConnection(false, "Verbinden met Spotify…")
        AppStateStore.setPlayback(false, true, "Geen playback")
        AppStateStore.clearError()

        startConnectTimeout(onFinished)

        val connectionParams = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                Log.d(TAG, "onConnected()")

                clearConnectTimeout()
                isConnecting = false
                spotifyAppRemote = appRemote

                AppStateStore.setConnection(true, "Verbonden met Spotify")
                AppStateStore.clearError()

                subscribeToPlayerState()
                switchToLocalDevice()
                refreshPlayerState()

                onFinished?.invoke(true, "Verbonden")
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "onFailure()", throwable)

                clearConnectTimeout()
                isConnecting = false
                spotifyAppRemote = null
                playerStateSubscription?.cancel()
                playerStateSubscription = null

                val message = mapConnectionError(throwable)
                AppStateStore.setConnection(false, "Niet verbonden")
                AppStateStore.setPlayback(false, true, "Geen playback")
                AppStateStore.setError(message)

                onFinished?.invoke(false, message)
            }
        })
    }

    fun handleAuthCallbackReturn(activity: Activity) {
        Log.d(TAG, "handleAuthCallbackReturn()")

        val currentRemote = spotifyAppRemote
        if (currentRemote != null && currentRemote.isConnected) {
            AppStateStore.setConnection(true, "Verbonden met Spotify")
            AppStateStore.clearError()
            subscribeToPlayerState()
            refreshPlayerState()
            return
        }

        AppStateStore.setConnection(false, "Spotify-toestemming ontvangen…")
        AppStateStore.clearError()

        if (isConnecting) {
            startConnectTimeout(null)
            return
        }

        connect(activity)
    }

    fun connectAndPlay(
        activity: Activity,
        spotifyUri: String,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        Log.d(TAG, "connectAndPlay() met uri=$spotifyUri")

        AppStateStore.setPlayback(false, true, "Track voorbereiden…")

        connect(activity) { success, message ->
            if (!success) {
                onFinished?.invoke(false, message)
                return@connect
            }

            playUri(spotifyUri, onFinished)
        }
    }

    fun connectAndPlayContext(
        activity: Activity,
        spotifyContextUri: String,
        enableShuffle: Boolean = true,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        Log.d(TAG, "connectAndPlayContext() met context=$spotifyContextUri")

        AppStateStore.setPlayback(false, true, "Playlist voorbereiden…")

        connect(activity) { success, message ->
            if (!success) {
                onFinished?.invoke(false, message)
                return@connect
            }

            playContextUri(
                spotifyContextUri = spotifyContextUri,
                enableShuffle = enableShuffle,
                onFinished = onFinished
            )
        }
    }

    fun playUri(
        spotifyUri: String,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        Log.d(TAG, "playUri() met uri=$spotifyUri")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            val message = "Spotify remote is niet verbonden."
            Log.e(TAG, message)
            AppStateStore.setConnection(false, "Niet verbonden")
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        AppStateStore.setPlayback(false, false, "Track starten…")
        AppStateStore.clearError()

        remote.getConnectApi().connectSwitchToLocalDevice()
            .setResultCallback {
                Log.d(TAG, "Lokale device-switch gelukt")
                startPlayback(
                    remote = remote,
                    spotifyUri = spotifyUri,
                    enableShuffleAfterStart = false,
                    successStatus = "Playback actief",
                    onFinished = onFinished
                )
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "Lokale device-switch mislukt, probeer toch te spelen", throwable)
                startPlayback(
                    remote = remote,
                    spotifyUri = spotifyUri,
                    enableShuffleAfterStart = false,
                    successStatus = "Playback actief",
                    onFinished = onFinished
                )
            }
    }

    fun playContextUri(
        spotifyContextUri: String,
        enableShuffle: Boolean = true,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        Log.d(TAG, "playContextUri() met context=$spotifyContextUri, shuffle=$enableShuffle")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            val message = "Spotify remote is niet verbonden."
            Log.e(TAG, message)
            AppStateStore.setConnection(false, "Niet verbonden")
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        AppStateStore.setPlayback(false, false, "Playlist starten…")
        AppStateStore.clearError()

        remote.getConnectApi().connectSwitchToLocalDevice()
            .setResultCallback {
                Log.d(TAG, "Lokale device-switch gelukt")
                startPlayback(
                    remote = remote,
                    spotifyUri = spotifyContextUri,
                    enableShuffleAfterStart = enableShuffle,
                    successStatus = "Playlist actief",
                    onFinished = onFinished
                )
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "Lokale device-switch mislukt, probeer toch context te spelen", throwable)
                startPlayback(
                    remote = remote,
                    spotifyUri = spotifyContextUri,
                    enableShuffleAfterStart = enableShuffle,
                    successStatus = "Playlist actief",
                    onFinished = onFinished
                )
            }
    }

    fun skipNext(onFinished: ((Boolean, String) -> Unit)? = null) {
        Log.d(TAG, "skipNext()")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            val message = "Spotify remote is niet verbonden."
            Log.e(TAG, message)
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        remote.playerApi.skipNext()
            .setResultCallback {
                Log.d(TAG, "skipNext() gelukt")
                refreshPlayerState()
                onFinished?.invoke(true, "Volgend nummer gestart")
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "skipNext() mislukt", throwable)
                val message = "Volgend nummer starten mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                AppStateStore.setError(message)
                onFinished?.invoke(false, message)
            }
    }

    fun setShuffleEnabled(
        enabled: Boolean,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        Log.d(TAG, "setShuffleEnabled($enabled)")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            val message = "Spotify remote is niet verbonden."
            Log.e(TAG, message)
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        remote.playerApi.setShuffle(enabled)
            .setResultCallback {
                Log.d(TAG, "setShuffleEnabled() gelukt")
                refreshPlayerState()
                onFinished?.invoke(true, if (enabled) "Shuffle aan" else "Shuffle uit")
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "setShuffleEnabled() mislukt", throwable)
                val message = "Shuffle aanpassen mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                AppStateStore.setError(message)
                onFinished?.invoke(false, message)
            }
    }

    fun resume(onFinished: ((Boolean, String) -> Unit)? = null) {
        Log.d(TAG, "resume()")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            val message = "Spotify remote is niet verbonden."
            Log.e(TAG, message)
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        remote.playerApi.resume()
            .setResultCallback {
                Log.d(TAG, "resume() gelukt")
                AppStateStore.setPlayback(true, false, "Playback actief")
                refreshPlayerState()
                onFinished?.invoke(true, "Playback hervat")
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "resume() mislukt", throwable)
                val message = "Resume mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                AppStateStore.setError(message)
                onFinished?.invoke(false, message)
            }
    }

    fun pause(onFinished: ((Boolean, String) -> Unit)? = null) {
        Log.d(TAG, "pause()")

        cancelPlaybackVerification()

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            val message = "Spotify remote is niet verbonden."
            Log.e(TAG, message)
            AppStateStore.setError(message)
            onFinished?.invoke(false, message)
            return
        }

        remote.playerApi.pause()
            .setResultCallback {
                Log.d(TAG, "pause() gelukt")
                AppStateStore.setPlayback(false, true, "Gepauzeerd")
                refreshPlayerState()
                onFinished?.invoke(true, "Playback gepauzeerd")
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "pause() mislukt", throwable)
                val message = "Pause mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                AppStateStore.setError(message)
                onFinished?.invoke(false, message)
            }
    }

    fun stopPlayback(onFinished: ((Boolean, String) -> Unit)? = null) {
        Log.d(TAG, "stopPlayback()")

        cancelPlaybackVerification()

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            AppStateStore.setPlayback(false, true, "Geen playback")
            onFinished?.invoke(true, "Geen actieve playback")
            return
        }

        remote.playerApi.pause()
            .setResultCallback {
                Log.d(TAG, "stopPlayback() gelukt")
                AppStateStore.setPlayback(false, true, "Gestopt")
                refreshPlayerState()
                onFinished?.invoke(true, "Playback gestopt")
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "stopPlayback() mislukt", throwable)
                val message = "Playback stoppen mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                AppStateStore.setPlayback(false, true, "Gestopt")
                AppStateStore.setError(message)
                onFinished?.invoke(false, message)
            }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect()")

        clearConnectTimeout()
        cancelPlaybackVerification()
        isConnecting = false

        playerStateSubscription?.cancel()
        playerStateSubscription = null

        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null

        AppStateStore.setConnection(false, "Niet verbonden")
        AppStateStore.setPlayback(false, true, "Geen playback")
    }

    fun refreshPlayerState() {
        Log.d(TAG, "refreshPlayerState()")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            Log.d(TAG, "refreshPlayerState() overgeslagen: geen verbinding")
            return
        }

        remote.playerApi.getPlayerState()
            .setResultCallback { playerState ->
                Log.d(TAG, "getPlayerState() gelukt")
                updateFromPlayerState(playerState)
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "getPlayerState() mislukt", throwable)
                AppStateStore.setError(
                    "Player state ophalen mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                )
            }
    }

    private fun subscribeToPlayerState() {
        Log.d(TAG, "subscribeToPlayerState()")

        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            Log.d(TAG, "subscribeToPlayerState() overgeslagen: geen verbinding")
            return
        }

        playerStateSubscription?.cancel()

        val subscription = remote.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { playerState ->
            Log.d(TAG, "PlayerState event ontvangen")
            updateFromPlayerState(playerState)
        }
        subscription.setErrorCallback { throwable ->
            Log.e(TAG, "PlayerState subscription fout", throwable)
            AppStateStore.setError(
                "Player state subscription fout: ${throwable.message ?: throwable.javaClass.simpleName}"
            )
        }

        playerStateSubscription = subscription
    }

    private fun startPlayback(
        remote: SpotifyAppRemote,
        spotifyUri: String,
        enableShuffleAfterStart: Boolean,
        successStatus: String,
        onFinished: ((Boolean, String) -> Unit)? = null,
        attempt: Int = 1,
        generation: Int = nextPlaybackStartGeneration()
    ) {
        Log.d(
            TAG,
            "startPlayback() met uri=$spotifyUri, shuffle=$enableShuffleAfterStart, attempt=$attempt"
        )

        remote.playerApi.play(spotifyUri)
            .setResultCallback {
                Log.d(TAG, "play() gelukt")
                AppStateStore.setPlayback(true, false, successStatus)
                AppStateStore.clearError()
                refreshPlayerState()
                schedulePlaybackVerification(
                    remote = remote,
                    spotifyUri = spotifyUri,
                    successStatus = successStatus,
                    attempt = attempt,
                    generation = generation
                )

                if (enableShuffleAfterStart) {
                    mainHandler.postDelayed({
                        setShuffleEnabled(true) { shuffleSuccess, shuffleMessage ->
                            if (shuffleSuccess) {
                                onFinished?.invoke(true, "Playback gestart met shuffle")
                            } else {
                                onFinished?.invoke(true, "Playback gestart, maar shuffle lukte niet: $shuffleMessage")
                            }
                        }
                    }, SHUFFLE_AFTER_CONTEXT_DELAY_MS)
                } else {
                    onFinished?.invoke(true, "Playback gestart")
                }
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "play() mislukt", throwable)
                val message =
                    "Track of context kon niet gestart worden: ${throwable.message ?: throwable.javaClass.simpleName}"
                AppStateStore.setPlayback(false, true, "Niet gestart")
                AppStateStore.setError(message)
                onFinished?.invoke(false, message)
            }
    }

    private fun schedulePlaybackVerification(
        remote: SpotifyAppRemote,
        spotifyUri: String,
        successStatus: String,
        attempt: Int,
        generation: Int
    ) {
        playbackVerifyRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            if (generation != playbackStartGeneration) {
                return@Runnable
            }

            if (!remote.isConnected) {
                return@Runnable
            }

            remote.playerApi.getPlayerState()
                .setResultCallback { playerState ->
                    if (generation != playbackStartGeneration) {
                        return@setResultCallback
                    }

                    updateFromPlayerState(playerState)

                    if (!playerState.isPaused) {
                        Log.d(TAG, "Playback verificatie gelukt")
                        return@setResultCallback
                    }

                    if (attempt >= MAX_PLAYBACK_START_ATTEMPTS) {
                        Log.e(TAG, "Playback bleef gepauzeerd na $attempt pogingen")
                        AppStateStore.setPlayback(false, true, "Niet automatisch gestart")
                        AppStateStore.setError(
                            "Spotify bleef gepauzeerd. Tik op Start om het nummer opnieuw te starten."
                        )
                        return@setResultCallback
                    }

                    Log.d(TAG, "Playback staat nog gepauzeerd; start opnieuw, poging ${attempt + 1}")
                    AppStateStore.setPlayback(false, false, "Start opnieuw bevestigen…")
                    startPlayback(
                        remote = remote,
                        spotifyUri = spotifyUri,
                        enableShuffleAfterStart = false,
                        successStatus = successStatus,
                        onFinished = null,
                        attempt = attempt + 1,
                        generation = generation
                    )
                }
                .setErrorCallback { throwable ->
                    Log.e(TAG, "Playback verificatie mislukt", throwable)
                    refreshPlayerState()
                }
        }

        playbackVerifyRunnable = runnable
        mainHandler.postDelayed(runnable, PLAYBACK_VERIFY_DELAY_MS)
    }

    private fun updateFromPlayerState(playerState: PlayerState) {
        val track = playerState.track
        val trackName = track?.name ?: "Geen track geladen"
        val artistName = track?.artist?.name ?: ""
        val trackUri = track?.uri

        Log.d(
            TAG,
            "updateFromPlayerState(): track=$trackName, artist=$artistName, paused=${playerState.isPaused}"
        )

        AppStateStore.setCurrentTrack(trackName, artistName, trackUri)
        AppStateStore.setPlayback(
            isPlaying = !playerState.isPaused,
            isPaused = playerState.isPaused,
            status = if (playerState.isPaused) "Gepauzeerd" else "Playback actief"
        )
    }

    private fun switchToLocalDevice() {
        Log.d(TAG, "switchToLocalDevice()")

        spotifyAppRemote?.getConnectApi()?.connectSwitchToLocalDevice()
            ?.setResultCallback {
                Log.d(TAG, "switchToLocalDevice() gelukt")
            }
            ?.setErrorCallback { throwable ->
                Log.e(TAG, "switchToLocalDevice() mislukt", throwable)
            }
    }

    private fun startConnectTimeout(onFinished: ((Boolean, String) -> Unit)?) {
        clearConnectTimeout()

        connectTimeoutRunnable = Runnable {
            if (!isConnecting) {
                return@Runnable
            }

            Log.e(TAG, "connect() timeout na $CONNECT_TIMEOUT_MS ms")

            isConnecting = false
            spotifyAppRemote = null
            playerStateSubscription?.cancel()
            playerStateSubscription = null

            val message =
                "Spotify verbinden duurde te lang. Open Spotify op je telefoon, controleer of je bent ingelogd en probeer opnieuw."

            AppStateStore.setConnection(false, "Niet verbonden")
            AppStateStore.setPlayback(false, true, "Geen playback")
            AppStateStore.setError(message)

            onFinished?.invoke(false, message)
        }

        mainHandler.postDelayed(connectTimeoutRunnable!!, CONNECT_TIMEOUT_MS)
    }

    private fun clearConnectTimeout() {
        connectTimeoutRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
        }
        connectTimeoutRunnable = null
    }

    private fun nextPlaybackStartGeneration(): Int {
        playbackStartGeneration += 1
        playbackVerifyRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackVerifyRunnable = null
        return playbackStartGeneration
    }

    private fun cancelPlaybackVerification() {
        playbackStartGeneration += 1
        playbackVerifyRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackVerifyRunnable = null
    }

    private fun resetConnectionState() {
        clearConnectTimeout()
        cancelPlaybackVerification()

        playerStateSubscription?.cancel()
        playerStateSubscription = null

        spotifyAppRemote?.let {
            try {
                SpotifyAppRemote.disconnect(it)
            } catch (_: Exception) {
            }
        }
        spotifyAppRemote = null
    }

    private fun mapConnectionError(throwable: Throwable): String {
        return when (throwable.javaClass.simpleName) {
            "CouldNotFindSpotifyApp" -> "Spotify is niet geïnstalleerd op dit toestel."
            "NotLoggedInException" -> "Log eerst in in de Spotify-app op dit toestel."
            "UserNotAuthorizedException" -> "Geef deze app Spotify-toegang en probeer het daarna opnieuw."
            "LoggedOutException" -> "De Spotify-sessie is uitgelogd. Log opnieuw in in Spotify en probeer het opnieuw."
            "SpotifyConnectionTerminatedException" -> "De Spotify-verbinding is beëindigd. Probeer opnieuw te verbinden."
            "SpotifyRemoteServiceException" -> "Spotify kon niet als achtergrondservice starten. Open Spotify één keer handmatig en probeer opnieuw."
            else -> "Spotify-verbinding mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
        }
    }
}
