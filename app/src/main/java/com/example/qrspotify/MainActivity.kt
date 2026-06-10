package com.example.qrspotify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivityMainBinding
import com.example.qrspotify.duel.DuelSetupActivity
import com.example.qrspotify.model.AppUiState
import com.example.qrspotify.spotify.SpotifyManager
import com.example.qrspotify.ui.classic.ClassicModeChoiceActivity
import com.example.qrspotify.ui.practice.PracticeCategoryActivity
import com.example.qrspotify.ui.renelebak.ReneLeBakActivity
import com.example.qrspotify.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentState: AppUiState = AppStateStore.state.value

    private val prefs by lazy {
        getSharedPreferences("qrspotify_prefs", MODE_PRIVATE)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        prefs.edit()
            .putBoolean(KEY_CAMERA_PERMISSION_ASKED_ONCE, true)
            .apply()

        if (!granted) {
            showToast(getString(R.string.common_camera_needed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        askCameraPermissionOnlyFirstTime()
        bindClicks()
        observeState()
        handleSpotifyCallbackIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallbackIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!SpotifyManager.resumePendingConnectionAfterSpotifyReturn(this)) {
            SpotifyManager.refreshPlayerState()
        }
    }

    private fun applyInsets() {
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.home_screen_horizontal_padding)
        val topSpacing = resources.getDimensionPixelSize(R.dimen.home_screen_top_spacing)
        val bottomSpacing = resources.getDimensionPixelSize(R.dimen.home_screen_bottom_spacing)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootHome) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.contentHome.setPadding(
                horizontalPadding,
                systemBars.top + topSpacing,
                horizontalPadding,
                systemBars.bottom + bottomSpacing
            )

            view.setPadding(0, 0, 0, 0)
            insets
        }
    }

    private fun bindClicks() {
        binding.buttonOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.buttonConnectSpotify.setOnClickListener {
            binding.buttonConnectSpotify.isEnabled = false
            binding.buttonConnectSpotify.text = getString(R.string.home_connecting)

            SpotifyManager.connect(this) { success, message ->
                runOnUiThread {
                    if (!success) {
                        binding.buttonConnectSpotify.isEnabled = true
                        binding.buttonConnectSpotify.text = getString(R.string.home_connect_spotify)
                        showToast(message)
                    }
                }
            }
        }

        binding.buttonContinueClassic.setOnClickListener {
            if (currentState.isConnected) {
                startActivity(Intent(this, ClassicModeChoiceActivity::class.java))
            } else {
                showToast(getString(R.string.common_connect_first))
            }
        }

        binding.buttonOpenPractice.setOnClickListener {
            if (currentState.isConnected) {
                startActivity(Intent(this, PracticeCategoryActivity::class.java))
            } else {
                showToast(getString(R.string.common_connect_first))
            }
        }

        binding.buttonOpenDuel.setOnClickListener {
            if (currentState.isConnected) {
                startActivity(Intent(this, DuelSetupActivity::class.java))
            } else {
                showToast(getString(R.string.duel_connect_first))
            }
        }

        binding.buttonOpenReneLeBak.setOnClickListener {
            if (currentState.isConnected) {
                startActivity(Intent(this, ReneLeBakActivity::class.java))
            } else {
                showToast(getString(R.string.common_connect_first))
            }
        }
    }

    private fun handleSpotifyCallbackIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "qrspotify" && data.host == "spotify-auth-callback") {
            SpotifyManager.handleAuthCallbackReturn(this)
        }
    }

    private fun askCameraPermissionOnlyFirstTime() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) return

        val alreadyAskedOnce = prefs.getBoolean(KEY_CAMERA_PERMISSION_ASKED_ONCE, false)

        if (!alreadyAskedOnce) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppStateStore.state.collect { state ->
                    currentState = state
                    render(state)
                }
            }
        }
    }

    private fun render(state: AppUiState) {
        val isConnected = state.isConnected
        val isConnecting = state.isConnecting

        binding.textConnectionStatus.text =
            when {
                isConnected -> getString(R.string.home_connection_ready)
                isConnecting -> state.connectionStatus
                else -> getString(R.string.home_connection_not_ready)
            }

        binding.textStatusBadge.text =
            if (isConnected) getString(R.string.home_connected_badge) else getString(R.string.home_disconnected_badge)

        binding.textStatusBadge.setBackgroundResource(
            if (isConnected) {
                R.drawable.bg_home_connected_badge
            } else {
                R.drawable.bg_home_disconnected_badge
            }
        )

        binding.textStatusBadge.setTextColor(
            ContextCompat.getColor(
                this,
                if (isConnected) R.color.home_status_connected_text else R.color.home_status_disconnected_text
            )
        )

        binding.textError.text = state.lastError
        binding.textError.visibility = if (state.lastError.isNotBlank()) View.VISIBLE else View.GONE

        binding.buttonContinueClassic.isEnabled = isConnected
        binding.buttonOpenPractice.isEnabled = isConnected
        binding.buttonOpenDuel.isEnabled = isConnected
        binding.buttonOpenReneLeBak.isEnabled = isConnected
        binding.buttonConnectSpotify.isEnabled = !isConnected && !isConnecting

        binding.buttonConnectSpotify.text =
            when {
                isConnected -> getString(R.string.home_spotify_connected)
                isConnecting -> getString(R.string.home_connecting)
                else -> getString(R.string.home_connect_spotify)
            }

        val modeCardAlpha = if (isConnected) 1f else 0.58f
        binding.buttonContinueClassic.alpha = modeCardAlpha
        binding.buttonOpenPractice.alpha = modeCardAlpha
        binding.buttonOpenDuel.alpha = modeCardAlpha
        binding.buttonOpenReneLeBak.alpha = modeCardAlpha
        binding.buttonConnectSpotify.alpha = if (isConnected) 0.72f else 1f
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val KEY_CAMERA_PERMISSION_ASKED_ONCE = "camera_permission_asked_once"
    }
}
