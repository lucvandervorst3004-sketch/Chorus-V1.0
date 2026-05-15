package com.example.qrspotify.ui.nowplaying

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.qrspotify.R
import com.example.qrspotify.classic.ClassicModeConfig
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivityNowPlayingBinding
import com.example.qrspotify.model.AppUiState
import com.example.qrspotify.spotify.SpotifyManager
import com.example.qrspotify.ui.answer.AnswerActivity
import com.example.qrspotify.ui.scanner.ScannerActivity
import com.example.qrspotify.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNowPlayingBinding
    private var currentState: AppUiState = AppStateStore.state.value
    private val runningAnimators = mutableListOf<Animator>()

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var classicRole: String = ClassicModeConfig.ROLE_PARTY
    private var classicVariant: String = ClassicModeConfig.VARIANT_STANDARD
    private var pendingAutoStartStandard = false
    private var pendingAutoStartHighPressure = false
    private var initialTrackUri: String = ""
    private var standardAutoStartAttempts = 0
    private var standardAutoStartInFlight = false
    private var standardAutoStartRetryRunnable: Runnable? = null

    private var highPressurePlayCount = 0
    private var highPressureWindowActive = false
    private var highPressureEndsAtMs = 0L

    private var autoPauseRunnable: Runnable? = null
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        classicRole = ClassicModeConfig.sanitizeRole(
            intent.getStringExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE)
        )
        classicVariant = ClassicModeConfig.sanitizeVariant(
            intent.getStringExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT)
        )
        pendingAutoStartHighPressure =
            intent.getBooleanExtra(EXTRA_AUTO_START_HIGH_PRESSURE, false)
        pendingAutoStartStandard =
            intent.getBooleanExtra(EXTRA_AUTO_START_STANDARD, false)
        initialTrackUri = intent.getStringExtra(EXTRA_TRACK_URI).orEmpty()

        applyKeepScreenOnSetting()
        applyModeTheme()

        binding.buttonOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.buttonOpenAnswer.setOnClickListener {
            startActivity(
                Intent(this, AnswerActivity::class.java)
                    .putExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE, classicRole)
                    .putExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT, classicVariant)
            )
        }

        binding.buttonTogglePlayback.setOnClickListener {
            val uri = resolvePlayableUri()
            if (uri.isBlank()) return@setOnClickListener

            pendingAutoStartStandard = false
            cancelStandardAutoStartRetry()

            if (ClassicModeConfig.isHighPressure(classicVariant)) {
                handleHighPressureToggle(uri)
            } else {
                handleStandardToggle(uri)
            }
        }

        binding.buttonNextCard.setOnClickListener {
            cancelHighPressureWindow()
            startActivity(
                Intent(this, ScannerActivity::class.java)
                    .putExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE, classicRole)
                    .putExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT, classicVariant)
            )
            finish()
        }

        observeState()
    }

    private fun applyInsets() {
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        SpotifyManager.refreshPlayerState()
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOnSetting()
        applyModeTheme()
        render(currentState)
        maybeStartInitialStandard()
        maybeStartInitialHighPressure()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelStandardAutoStartRetry()
        cancelHighPressureWindow()
        stopVisualizer()

        if (isFinishing) {
            SpotifyManager.stopPlayback()
        }
    }

    private fun maybeStartInitialHighPressure() {
        if (!ClassicModeConfig.isHighPressure(classicVariant)) return
        if (!pendingAutoStartHighPressure) return
        if (!currentState.isConnected) return

        val uri = resolvePlayableUri(preferInitialTrack = true)
        if (uri.isBlank()) return

        pendingAutoStartHighPressure = false
        initialTrackUri = ""

        SpotifyManager.playUri(uri) { success, _ ->
            runOnUiThread {
                if (success) {
                    beginHighPressureWindow()
                }
            }
        }
    }

    private fun maybeStartInitialStandard() {
        if (ClassicModeConfig.isHighPressure(classicVariant)) return
        if (!pendingAutoStartStandard) return
        if (standardAutoStartInFlight) return
        if (!currentState.isConnected) return

        val uri = resolvePlayableUri(preferInitialTrack = true)
        if (uri.isBlank()) return

        if (currentState.isPlaying) {
            pendingAutoStartStandard = false
            initialTrackUri = ""
            cancelStandardAutoStartRetry()
            return
        }

        if (standardAutoStartAttempts >= MAX_STANDARD_AUTOSTART_ATTEMPTS) {
            pendingAutoStartStandard = false
            AppStateStore.setError(
                "Spotify startte niet automatisch. Tik op Start om het nummer opnieuw te starten."
            )
            return
        }

        standardAutoStartAttempts += 1
        standardAutoStartInFlight = true
        AppStateStore.setPlayback(false, false, "Track starten…")

        SpotifyManager.playUri(uri) { success, message ->
            runOnUiThread {
                standardAutoStartInFlight = false

                if (success) {
                    pendingAutoStartStandard = false
                    initialTrackUri = ""
                    SpotifyManager.refreshPlayerState()
                } else {
                    AppStateStore.setError(message)
                    scheduleStandardAutoStartRetry()
                }
            }
        }
    }

    private fun scheduleStandardAutoStartRetry() {
        cancelStandardAutoStartRetry()

        if (!pendingAutoStartStandard) return
        if (standardAutoStartAttempts >= MAX_STANDARD_AUTOSTART_ATTEMPTS) return

        val runnable = Runnable {
            maybeStartInitialStandard()
        }

        standardAutoStartRetryRunnable = runnable
        mainHandler.postDelayed(runnable, STANDARD_AUTOSTART_RETRY_DELAY_MS)
    }

    private fun cancelStandardAutoStartRetry() {
        standardAutoStartRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        standardAutoStartRetryRunnable = null
    }

    private fun resolvePlayableUri(preferInitialTrack: Boolean = false): String {
        if (preferInitialTrack && initialTrackUri.isNotBlank()) {
            return initialTrackUri
        }

        if (currentState.lastResolvedSpotifyUri.isNotBlank()) {
            return currentState.lastResolvedSpotifyUri
        }

        return initialTrackUri
    }

    private fun handleStandardToggle(uri: String) {
        if (currentState.isPlaying) {
            SpotifyManager.pause { _, _ -> }
        } else {
            SpotifyManager.playUri(uri) { _, _ -> }
        }
    }

    private fun handleHighPressureToggle(uri: String) {
        if (currentState.isPlaying) {
            cancelHighPressureWindow()
            SpotifyManager.pause { _, _ -> }
            return
        }

        if (highPressurePlayCount >= MAX_HIGH_PRESSURE_PLAYS) {
            return
        }

        SpotifyManager.playUri(uri) { success, _ ->
            runOnUiThread {
                if (success) {
                    beginHighPressureWindow()
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppStateStore.state.collect { state ->
                    currentState = state
                    render(state)
                    maybeStartInitialStandard()
                    maybeStartInitialHighPressure()
                }
            }
        }
    }

    private fun render(state: AppUiState) {
        val isSoloMode = ClassicModeConfig.isSolo(classicRole)
        val isHighPressureMode = ClassicModeConfig.isHighPressure(classicVariant)
        val hasTrack = state.isConnected && resolvePlayableUri().isNotBlank()

        binding.buttonOpenAnswer.visibility = if (isSoloMode) View.VISIBLE else View.GONE
        binding.buttonOpenAnswer.isEnabled = hasTrack
        binding.buttonOpenAnswer.alpha = if (hasTrack) 1f else 0.55f

        if (isHighPressureMode) {
            renderHighPressureState(state, hasTrack, isSoloMode)
        } else {
            renderStandardState(state, hasTrack, isSoloMode)
        }

        binding.buttonTogglePlayback.alpha =
            if (binding.buttonTogglePlayback.isEnabled) 1f else 0.55f
    }

    private fun renderStandardState(
        state: AppUiState,
        hasTrack: Boolean,
        isSoloMode: Boolean
    ) {
        binding.textPressureFooter.visibility = View.GONE
        binding.buttonTogglePlayback.isEnabled = hasTrack
        binding.textPlaybackStateBadge.visibility = View.VISIBLE

        when {
            state.isPlaying -> {
                binding.textPlaybackStateBadge.text = "LIVE AUDIO"
                binding.textPlaybackEyebrow.text =
                    if (isSoloMode) "SOLO CASUAL" else "PARTY CASUAL"
                binding.textPlaybackHeadline.text = "Nummer speelt"
                binding.textPlaybackMessage.text =
                    if (isSoloMode) "De playback is live. Luister goed en vul daarna je antwoord in."
                    else "De playback is live. Laat iedereen meeluisteren en raden."

                binding.textSessionTitle.text =
                    if (isSoloMode) "Solo Classic loopt" else "Party Classic loopt"

                binding.textSessionBody.text =
                    if (isSoloMode) {
                        "Je ziet bewust geen trackinfo. Gebruik het antwoordscherm als je jouw gok in de app wilt invullen."
                    } else {
                        "Je ziet bewust geen trackinfo en er is geen antwoordscherm in de app. Speel Classic zoals voorheen."
                    }

                binding.buttonTogglePlayback.text = "Pauze"
                startVisualizer()
            }

            state.isPaused && hasTrack -> {
                binding.textPlaybackStateBadge.text = "GEPAUZEERD"
                binding.textPlaybackEyebrow.text =
                    if (isSoloMode) "SOLO CASUAL" else "PARTY CASUAL"
                binding.textPlaybackHeadline.text = "Luister en raad"
                binding.textPlaybackMessage.text = "Neem je moment en luister daarna verder"
                binding.textSessionTitle.text = "Even gepauzeerd"

                binding.textSessionBody.text =
                    if (isSoloMode) {
                        "Je kunt naar het antwoordscherm gaan, je gok invullen en daarna weer verder luisteren."
                    } else {
                        "Je kunt direct verder luisteren of meteen de volgende kaart scannen."
                    }

                binding.buttonTogglePlayback.text = "Verder"
                stopVisualizer()
            }

            hasTrack -> {
                binding.textPlaybackStateBadge.text =
                    if (pendingAutoStartStandard || standardAutoStartInFlight) "STARTING" else "KLAAR"
                binding.textPlaybackEyebrow.text =
                    if (isSoloMode) "SOLO CASUAL" else "PARTY CASUAL"
                binding.textPlaybackHeadline.text =
                    if (pendingAutoStartStandard || standardAutoStartInFlight) "Nummer start" else "Klaar om te starten"
                binding.textPlaybackMessage.text =
                    if (pendingAutoStartStandard || standardAutoStartInFlight) {
                        "Spotify wordt automatisch gestart. De visual wordt live zodra audio loopt."
                    } else {
                        "Je kaart is geladen en wacht op playback"
                    }
                binding.textSessionTitle.text =
                    if (pendingAutoStartStandard || standardAutoStartInFlight) "Playback wordt gestart" else "Track staat klaar"

                binding.textSessionBody.text =
                    if (pendingAutoStartStandard || standardAutoStartInFlight) {
                        "Als Spotify niet direct reageert probeert Chorus het automatisch opnieuw."
                    } else if (isSoloMode) {
                        "Druk op start om te luisteren en vul daarna je antwoord in."
                    } else {
                        "Druk op start en speel direct verder met de groep."
                    }

                binding.buttonTogglePlayback.text = "Start"
                stopVisualizer()
            }

            else -> {
                binding.textPlaybackStateBadge.text = "GEEN KAART"
                binding.textPlaybackEyebrow.text =
                    if (isSoloMode) "SOLO CASUAL" else "PARTY CASUAL"
                binding.textPlaybackHeadline.text = "Nog geen kaart"
                binding.textPlaybackMessage.text = "Open eerst de scanner om een track te laden"
                binding.textSessionTitle.text = "Wachten op een ronde"
                binding.textSessionBody.text =
                    if (isSoloMode) {
                        "Open de scanner, start een kaart en vul daarna desgewenst je antwoord in via het antwoordscherm."
                    } else {
                        "Open de scanner en speel Party Classic zonder antwoordscherm in de app."
                    }

                binding.buttonTogglePlayback.text = "Start"
                stopVisualizer()
            }
        }
    }

    private fun renderHighPressureState(
        state: AppUiState,
        hasTrack: Boolean,
        isSoloMode: Boolean
    ) {
        binding.textPressureFooter.visibility = View.VISIBLE
        binding.textPlaybackStateBadge.visibility = View.VISIBLE
        binding.buttonTogglePlayback.isEnabled =
            hasTrack && (state.isPlaying || highPressurePlayCount < MAX_HIGH_PRESSURE_PLAYS)

        binding.textPlaybackStateBadge.text =
            if (state.isPlaying) "LIVE 10 SEC" else if (hasTrack) "PRESSURE" else "GEEN KAART"

        binding.textPlaybackEyebrow.text =
            if (isSoloMode) "SOLO HIGH PRESSURE" else "PARTY HIGH PRESSURE"
        binding.textPlaybackHeadline.text = "Hoor de eerste 10 seconden"
        binding.textPlaybackMessage.text =
            if (highPressureWindowActive && state.isPlaying) {
                "De klok loopt. Luister scherp en reageer snel."
            } else if (highPressurePlayCount >= MAX_HIGH_PRESSURE_PLAYS) {
                "Je hebt deze kaart al 2 keer gestart."
            } else {
                "Je mag deze kaart maximaal 2 keer starten."
            }

        binding.textSessionTitle.text =
            if (isSoloMode) "Solo High Pressure" else "Party High Pressure"

        binding.textSessionBody.text =
            when {
                !hasTrack -> {
                    if (isSoloMode) {
                        "Scan eerst een kaart. Daarna start de 10-secondenronde automatisch en kun je je antwoord invullen in de app."
                    } else {
                        "Scan eerst een kaart. Daarna start de 10-secondenronde automatisch voor de groep."
                    }
                }
                highPressureWindowActive && state.isPlaying -> {
                    if (isSoloMode) {
                        "Je bent live bezig. Na 10 seconden stopt de playback automatisch en kun je jouw antwoord noteren."
                    } else {
                        "Je bent live bezig. Na 10 seconden stopt de playback automatisch."
                    }
                }
                highPressurePlayCount >= MAX_HIGH_PRESSURE_PLAYS -> {
                    if (isSoloMode) {
                        "Deze kaart is op. Ga naar de volgende kaart of vul je antwoord in."
                    } else {
                        "Deze kaart is op. Ga naar de volgende kaart."
                    }
                }
                else -> {
                    if (isSoloMode) {
                        "Je kunt nog een korte start doen of je antwoord invullen."
                    } else {
                        "Je kunt nog een korte start doen of meteen doorgaan naar de volgende kaart."
                    }
                }
            }

        binding.textPressureFooter.text =
            "Starts gebruikt: $highPressurePlayCount / $MAX_HIGH_PRESSURE_PLAYS"

        binding.buttonTogglePlayback.text =
            when {
                state.isPlaying -> formatHighPressureRemainingLabel()
                highPressurePlayCount >= MAX_HIGH_PRESSURE_PLAYS -> "Limiet bereikt"
                highPressurePlayCount == 0 -> "Start 10 sec"
                else -> "Nog 1 keer"
            }

        if (state.isPlaying) {
            startVisualizer()
        } else {
            stopVisualizer()
        }
    }

    private fun beginHighPressureWindow() {
        cancelHighPressureWindow()

        highPressurePlayCount += 1
        highPressureWindowActive = true
        highPressureEndsAtMs = SystemClock.elapsedRealtime() + HIGH_PRESSURE_WINDOW_MS

        startHighPressureCountdown()

        autoPauseRunnable = Runnable {
            SpotifyManager.pause { _, _ -> }
            highPressureWindowActive = false
            render(currentState)
        }
        mainHandler.postDelayed(autoPauseRunnable!!, HIGH_PRESSURE_WINDOW_MS)

        render(currentState)
    }

    private fun cancelHighPressureWindow() {
        highPressureWindowActive = false
        highPressureEndsAtMs = 0L

        autoPauseRunnable?.let { mainHandler.removeCallbacks(it) }
        autoPauseRunnable = null

        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun startHighPressureCountdown() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = object : Runnable {
            override fun run() {
                if (!highPressureWindowActive) {
                    render(currentState)
                    return
                }

                render(currentState)

                val remaining = highPressureEndsAtMs - SystemClock.elapsedRealtime()
                if (remaining > 0L) {
                    mainHandler.postDelayed(this, 100L)
                }
            }
        }

        countdownRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun formatHighPressureRemainingLabel(): String {
        if (!highPressureWindowActive) {
            return "Start 10 sec"
        }

        val remainingMs = (highPressureEndsAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val remainingSeconds = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%d sec", remainingSeconds)
    }

    private fun applyKeepScreenOnSetting() {
        val keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyModeTheme() {
        val isHighPressure = ClassicModeConfig.isHighPressure(classicVariant)
        val isSolo = ClassicModeConfig.isSolo(classicRole)

        if (isHighPressure) {
            val white = Color.parseColor("#FFF3F3")
            val softWhite = Color.parseColor("#E7D7D9")
            val red = Color.parseColor("#FF5A66")

            binding.root.setBackgroundResource(R.drawable.bg_high_pressure_screen)
            binding.playbackHeaderCard.setBackgroundResource(R.drawable.bg_now_playing_header_card_pressure)
            binding.playerDeck.setBackgroundResource(R.drawable.bg_now_playing_deck_pressure)
            binding.visualizerGlow.setBackgroundResource(R.drawable.bg_now_playing_glow_pressure)
            binding.visualizerAccentGlow.setBackgroundResource(R.drawable.bg_now_playing_glow_magenta)
            binding.visualizerAccentGlowAlt.setBackgroundResource(R.drawable.bg_now_playing_glow_pressure)
            binding.visualizerRecord.setBackgroundResource(R.drawable.bg_now_playing_record_pressure)
            binding.cardSessionInfo.setBackgroundResource(R.drawable.bg_now_playing_session_card_pressure)
            binding.textPlaybackEyebrow.setBackgroundResource(R.drawable.bg_now_playing_mode_badge_pressure)
            binding.textHiddenTrackBadge.setBackgroundResource(R.drawable.bg_now_playing_live_badge_pressure)
            binding.textPlaybackStateBadge.setBackgroundResource(R.drawable.bg_now_playing_state_badge_pressure)
            binding.sessionIconShell.setBackgroundResource(R.drawable.bg_scanner_state_icon_pressure)
            binding.playbackIconShell.setBackgroundResource(R.drawable.bg_classic_option_icon_red)
            binding.waveBarOne.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_red)
            binding.waveBarTwo.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_gold)
            binding.waveBarThree.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_red)
            binding.waveBarFour.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_gold)
            binding.waveBarFive.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_red)

            binding.buttonTogglePlayback.setBackgroundResource(R.drawable.bg_button_high_pressure_launch)
            binding.buttonNextCard.setBackgroundResource(R.drawable.bg_button_high_pressure_secondary)
            binding.buttonOpenAnswer.setBackgroundResource(R.drawable.bg_button_high_pressure_secondary)
            binding.buttonOpenSettings.setBackgroundResource(R.drawable.bg_button_mode_outline_high_pressure)

            binding.imagePlaybackModeIcon.setImageResource(R.drawable.ic_classic_flame)
            binding.imagePlaybackModeIcon.setColorFilter(red)
            binding.imageSessionIcon.setColorFilter(red)

            binding.textPlaybackEyebrow.setTextColor(white)
            binding.textPlaybackHeadline.setTextColor(red)
            binding.textPlaybackMessage.setTextColor(softWhite)
            binding.textSessionTitle.setTextColor(white)
            binding.textSessionBody.setTextColor(softWhite)
            binding.textPressureFooter.setTextColor(softWhite)
            binding.textHiddenTrackBadge.setTextColor(red)
            binding.textPlaybackStateBadge.setTextColor(red)

            binding.buttonTogglePlayback.setTextColor(white)
            binding.buttonNextCard.setTextColor(white)
            binding.buttonOpenAnswer.setTextColor(white)
            binding.buttonOpenSettings.setTextColor(white)

            binding.chipYear.setBackgroundResource(R.drawable.bg_now_playing_chip_pressure)
            binding.chipTitle.setBackgroundResource(R.drawable.bg_now_playing_chip_pressure)
            binding.chipArtist.setBackgroundResource(R.drawable.bg_now_playing_chip_pressure)
            binding.textPressureFooter.setBackgroundResource(R.drawable.bg_now_playing_chip_pressure)
        } else {
            binding.root.setBackgroundResource(R.drawable.bg_stage_screen)
            binding.playbackHeaderCard.setBackgroundResource(R.drawable.bg_now_playing_header_card)
            binding.playerDeck.setBackgroundResource(R.drawable.bg_now_playing_deck)
            binding.visualizerGlow.setBackgroundResource(R.drawable.bg_now_playing_glow)
            binding.visualizerAccentGlow.setBackgroundResource(R.drawable.bg_now_playing_glow_cyan)
            binding.visualizerAccentGlowAlt.setBackgroundResource(R.drawable.bg_now_playing_glow_magenta)
            binding.visualizerRecord.setBackgroundResource(R.drawable.bg_now_playing_record)
            binding.cardSessionInfo.setBackgroundResource(R.drawable.bg_now_playing_session_card)
            binding.textPlaybackEyebrow.setBackgroundResource(R.drawable.bg_now_playing_mode_badge)
            binding.textHiddenTrackBadge.setBackgroundResource(R.drawable.bg_now_playing_live_badge)
            binding.textPlaybackStateBadge.setBackgroundResource(R.drawable.bg_now_playing_state_badge)
            binding.sessionIconShell.setBackgroundResource(R.drawable.bg_scanner_state_icon)
            binding.playbackIconShell.setBackgroundResource(R.drawable.bg_classic_option_icon_gold)
            binding.waveBarOne.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_gold)
            binding.waveBarTwo.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_cyan)
            binding.waveBarThree.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_gold)
            binding.waveBarFour.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_cyan)
            binding.waveBarFive.setBackgroundResource(R.drawable.bg_now_playing_wave_bar_gold)

            binding.buttonTogglePlayback.setBackgroundResource(R.drawable.bg_now_playing_play_button)
            binding.buttonNextCard.setBackgroundResource(R.drawable.bg_button_primary)
            binding.buttonOpenAnswer.setBackgroundResource(R.drawable.bg_button_secondary)
            binding.buttonOpenSettings.setBackgroundResource(R.drawable.bg_button_mode_outline)

            binding.imagePlaybackModeIcon.setImageResource(R.drawable.ic_classic_lp)
            binding.imagePlaybackModeIcon.clearColorFilter()
            binding.imageSessionIcon.setColorFilter(
                ContextCompat.getColor(this, R.color.gold_bright)
            )

            binding.textPlaybackEyebrow.setTextColor(
                ContextCompat.getColor(this, R.color.accent_amber)
            )
            binding.textPlaybackHeadline.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textPlaybackMessage.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textSessionTitle.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textSessionBody.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textPressureFooter.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textHiddenTrackBadge.setTextColor(
                ContextCompat.getColor(this, R.color.gold_bright)
            )
            binding.textPlaybackStateBadge.setTextColor(
                ContextCompat.getColor(this, R.color.neon_cyan)
            )

            binding.buttonTogglePlayback.setTextColor(
                ContextCompat.getColor(this, R.color.text_dark)
            )
            binding.buttonNextCard.setTextColor(
                ContextCompat.getColor(this, R.color.button_primary_text)
            )
            binding.buttonOpenAnswer.setTextColor(
                ContextCompat.getColor(this, R.color.button_secondary_text)
            )
            binding.buttonOpenSettings.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )

            binding.chipYear.setBackgroundResource(R.drawable.bg_now_playing_chip)
            binding.chipTitle.setBackgroundResource(R.drawable.bg_now_playing_chip)
            binding.chipArtist.setBackgroundResource(R.drawable.bg_now_playing_chip)
            binding.textPressureFooter.setBackgroundResource(R.drawable.bg_now_playing_chip)
        }

        val roleText = if (isSolo) "SOLO MODE" else "PARTY MODE"
        binding.textPlaybackEyebrow.text =
            if (isHighPressure) "$roleText · PRESSURE" else "$roleText · CASUAL"
    }

    private fun startVisualizer() {
        if (runningAnimators.isNotEmpty()) return

        val animators = mutableListOf<Animator>()
        animators += createPulseAnimators(binding.visualizerGlow, 1.0f, 1.10f, 0.24f, 0.10f, 1400L)
        animators += createPulseAnimators(binding.visualizerAccentGlow, 0.96f, 1.15f, 0.34f, 0.08f, 1250L)
        animators += createPulseAnimators(binding.visualizerAccentGlowAlt, 0.92f, 1.18f, 0.26f, 0.06f, 1600L)
        animators += createPulseAnimators(binding.visualizerOuterRing, 1.0f, 1.16f, 0.44f, 0.14f, 1100L)
        animators += createBreathingAnimators(binding.visualizerInnerRing, 1.0f, 1.10f, 900L)
        animators += createRotationAnimator(binding.visualizerRecord, 9000L)
        animators += createWaveBarAnimator(binding.waveBarOne, 0.35f, 1.0f, 520L)
        animators += createWaveBarAnimator(binding.waveBarTwo, 0.5f, 1.0f, 680L)
        animators += createWaveBarAnimator(binding.waveBarThree, 0.28f, 1.0f, 460L)
        animators += createWaveBarAnimator(binding.waveBarFour, 0.62f, 1.0f, 740L)
        animators += createWaveBarAnimator(binding.waveBarFive, 0.42f, 1.0f, 580L)

        runningAnimators += animators
        runningAnimators.forEach { it.start() }
    }

    private fun stopVisualizer() {
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()

        binding.visualizerGlow.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 0.24f
        }
        binding.visualizerAccentGlow.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 0.12f
        }
        binding.visualizerAccentGlowAlt.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 0.10f
        }
        binding.visualizerRecord.rotation = 0f
        binding.visualizerOuterRing.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 0.22f
        }
        binding.visualizerInnerRing.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 0.24f
        }
        resetWaveBars()
    }

    private fun createPulseAnimators(
        view: View,
        startScale: Float,
        endScale: Float,
        startAlpha: Float,
        endAlpha: Float,
        duration: Long
    ): List<Animator> {
        view.scaleX = startScale
        view.scaleY = startScale
        view.alpha = startAlpha

        val interpolator = AccelerateDecelerateInterpolator()

        return listOf(
            ObjectAnimator.ofFloat(view, View.SCALE_X, startScale, endScale).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                this.interpolator = interpolator
            },
            ObjectAnimator.ofFloat(view, View.SCALE_Y, startScale, endScale).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                this.interpolator = interpolator
            },
            ObjectAnimator.ofFloat(view, View.ALPHA, startAlpha, endAlpha).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                this.interpolator = interpolator
            }
        )
    }

    private fun createBreathingAnimators(
        view: View,
        startScale: Float,
        endScale: Float,
        duration: Long
    ): List<Animator> {
        view.scaleX = startScale
        view.scaleY = startScale

        val interpolator = AccelerateDecelerateInterpolator()

        return listOf(
            ObjectAnimator.ofFloat(view, View.SCALE_X, startScale, endScale).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                this.interpolator = interpolator
            },
            ObjectAnimator.ofFloat(view, View.SCALE_Y, startScale, endScale).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                this.interpolator = interpolator
            }
        )
    }

    private fun createRotationAnimator(view: View, duration: Long): Animator {
        view.rotation = 0f
        return ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
        }
    }

    private fun createWaveBarAnimator(
        view: View,
        startScale: Float,
        endScale: Float,
        duration: Long
    ): Animator {
        view.pivotY = view.height.toFloat()
        view.scaleY = startScale
        view.alpha = 0.72f

        return ObjectAnimator.ofFloat(view, View.SCALE_Y, startScale, endScale).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun resetWaveBars() {
        listOf(
            binding.waveBarOne,
            binding.waveBarTwo,
            binding.waveBarThree,
            binding.waveBarFour,
            binding.waveBarFive
        ).forEachIndexed { index, view ->
            view.scaleY = listOf(0.42f, 0.64f, 0.36f, 0.72f, 0.5f)[index]
            view.alpha = 0.38f
        }
    }

    companion object {
        const val EXTRA_AUTO_START_STANDARD = "extra_auto_start_standard"
        const val EXTRA_AUTO_START_HIGH_PRESSURE = "extra_auto_start_high_pressure"
        const val EXTRA_TRACK_URI = "extra_track_uri"

        private const val PREFS_NAME = "qrspotify_prefs"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_during_playback"
        private const val MAX_HIGH_PRESSURE_PLAYS = 2
        private const val HIGH_PRESSURE_WINDOW_MS = 10_000L
        private const val MAX_STANDARD_AUTOSTART_ATTEMPTS = 2
        private const val STANDARD_AUTOSTART_RETRY_DELAY_MS = 900L
    }
}
