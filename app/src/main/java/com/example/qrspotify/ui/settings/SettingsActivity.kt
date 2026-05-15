package com.example.qrspotify.ui.settings

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.qrspotify.R
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivitySettingsBinding
import com.example.qrspotify.model.AppUiState
import com.example.qrspotify.practice.PracticeStatsStore
import com.example.qrspotify.spotify.SpotifyManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        binding.textVersionValue.text = appVersionLabel()
        binding.textCameraPermissionValue.text = cameraPermissionLabel()
        binding.textModeValue.text = "Classic Mode"

        renderPracticeHighScores()

        binding.switchKeepScreenOn.isChecked = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(KEY_KEEP_SCREEN_ON, isChecked)
                .apply()
        }

        binding.buttonReconnect.setOnClickListener {
            SpotifyManager.connect(this)
        }

        binding.buttonResetPracticeHighscoreDutch.setOnClickListener {
            PracticeStatsStore.resetDutchHighScore()
            renderPracticeHighScores()
            Toast.makeText(this, "Nederlandse highscore is gereset.", Toast.LENGTH_SHORT).show()
        }

        binding.buttonResetPracticeHighscoreEnglish.setOnClickListener {
            PracticeStatsStore.resetEnglishHighScore()
            renderPracticeHighScores()
            Toast.makeText(this, "Wereldwijde highscore is gereset.", Toast.LENGTH_SHORT).show()
        }

        binding.buttonCloseSettings.setOnClickListener {
            finish()
        }

        observeState()
    }

    private fun applyInsets() {
        val initialLeft = binding.contentSettings.paddingLeft
        val initialTop = binding.contentSettings.paddingTop
        val initialRight = binding.contentSettings.paddingRight
        val initialBottom = binding.contentSettings.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootSettings) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentSettings.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppStateStore.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: AppUiState) {
        val connectionAccent =
            if (state.isConnected) R.color.neon_green else R.color.gold_bright

        binding.cardSpotifyConnection.setBackgroundResource(
            if (state.isConnected) {
                R.drawable.bg_settings_connection_card_connected
            } else {
                R.drawable.bg_settings_connection_card
            }
        )

        binding.settingsConnectionIconShell.setBackgroundResource(
            if (state.isConnected) {
                R.drawable.bg_settings_connection_icon_connected
            } else {
                R.drawable.bg_settings_connection_icon
            }
        )

        binding.imageSettingsConnectionIcon.setColorFilter(
            ContextCompat.getColor(this, connectionAccent)
        )

        binding.textConnectionValue.text =
            when {
                state.isConnected -> "Gekoppeld"
                state.isConnecting -> "Koppelen..."
                else -> "Niet gekoppeld"
            }

        binding.textConnectionValue.setTextColor(
            ContextCompat.getColor(this, connectionAccent)
        )

        binding.textConnectionHint.text =
            when {
                state.isConnected -> "Spotify is klaar voor Classic Mode."
                state.isConnecting -> state.connectionStatus
                else -> "Koppel Spotify vanaf het startscherm om direct te kunnen spelen."
            }

        binding.buttonReconnect.text =
            when {
                state.isConnecting -> "Bezig..."
                state.isConnected -> "Opnieuw"
                else -> "Koppel"
            }

        binding.buttonReconnect.isEnabled = !state.isConnecting

        binding.buttonReconnect.setBackgroundResource(
            if (state.isConnected) {
                R.drawable.bg_settings_secondary_button
            } else {
                R.drawable.bg_party_primary_button
            }
        )

        binding.buttonReconnect.setTextColor(
            ContextCompat.getColor(
                this,
                if (state.isConnected) R.color.button_secondary_text else R.color.text_dark
            )
        )

        binding.textErrorValue.text =
            if (state.lastError.isBlank()) "Geen meldingen" else state.lastError
    }

    private fun renderPracticeHighScores() {
        binding.textPracticeHighscoreDutchValue.text =
            PracticeStatsStore.getDutchHighScore().toString()

        binding.textPracticeHighscoreEnglishValue.text =
            PracticeStatsStore.getEnglishHighScore().toString()
    }

    private fun appVersionLabel(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0"
            "Versie $versionName"
        } catch (_: PackageManager.NameNotFoundException) {
            "Versie onbekend"
        }
    }

    private fun cameraPermissionLabel(): String {
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        return if (granted) "Toegestaan" else "Nog niet toegestaan"
    }

    companion object {
        private const val PREFS_NAME = "qrspotify_prefs"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_during_playback"
    }
}
