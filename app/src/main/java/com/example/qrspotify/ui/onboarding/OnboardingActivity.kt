package com.example.qrspotify.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.MainActivity
import com.example.qrspotify.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            openHome()
            return
        }

        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()

        binding.buttonSkipOnboarding.setOnClickListener {
            completeOnboarding()
        }

        binding.buttonStartOnboarding.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun applyInsets() {
        val initialLeft = binding.contentOnboarding.paddingLeft
        val initialTop = binding.contentOnboarding.paddingTop
        val initialRight = binding.contentOnboarding.paddingRight
        val initialBottom = binding.contentOnboarding.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentOnboarding.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    private fun completeOnboarding() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()

        openHome()
    }

    private fun openHome() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }

    companion object {
        const val PREFS_NAME = "qrspotify_prefs"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
