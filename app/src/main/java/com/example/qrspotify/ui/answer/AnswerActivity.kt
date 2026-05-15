package com.example.qrspotify.ui.answer

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.R
import com.example.qrspotify.classic.ClassicModeConfig
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivityClassicAnswerBinding

class AnswerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassicAnswerBinding

    private var classicRole: String = ClassicModeConfig.ROLE_PARTY
    private var classicVariant: String = ClassicModeConfig.VARIANT_STANDARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        classicRole = ClassicModeConfig.sanitizeRole(
            intent.getStringExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE)
        )
        classicVariant = ClassicModeConfig.sanitizeVariant(
            intent.getStringExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT)
        )

        if (!ClassicModeConfig.isSolo(classicRole)) {
            finish()
            return
        }

        binding = ActivityClassicAnswerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        applyModeTheme()
        applyModeText()
        prepareFreshState()
        renderCurrentRound()

        binding.buttonBackToPlayer.setOnClickListener {
            finish()
        }

        binding.buttonRevealAnswer.setOnClickListener {
            revealAnswer()
        }
    }

    private fun applyInsets() {
        val initialLeft = binding.answerRoot.paddingLeft
        val initialTop = binding.answerRoot.paddingTop
        val initialRight = binding.answerRoot.paddingRight
        val initialBottom = binding.answerRoot.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.answerRoot) { view, insets ->
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

    private fun prepareFreshState() {
        binding.inputYear.setText("")
        binding.inputTitle.setText("")
        binding.inputArtist.setText("")
        binding.layoutRevealCard.visibility = View.GONE

        binding.textRevealSong.text = "Nummer:"
        binding.textRevealArtist.text = "Artiest:"
        binding.textRevealYear.text = "Jaartal:"
    }

    private fun applyModeTheme() {
        val isHighPressure = ClassicModeConfig.isHighPressure(classicVariant)

        if (isHighPressure) {
            val white = Color.parseColor("#FFF3F3")
            val softWhite = Color.parseColor("#E7D7D9")
            val red = Color.parseColor("#FF5A66")

            binding.answerRoot.setBackgroundResource(R.drawable.bg_high_pressure_screen)
            binding.textAnswerEyebrow.background =
                ContextCompat.getDrawable(this, R.drawable.bg_button_mode_outline_high_pressure)

            binding.layoutGuessCard.setBackgroundResource(R.drawable.bg_scanner_panel_high_pressure)
            binding.layoutRevealCard.setBackgroundResource(R.drawable.bg_high_pressure_status)

            binding.inputYear.setBackgroundResource(R.drawable.bg_answer_input_high_pressure)
            binding.inputTitle.setBackgroundResource(R.drawable.bg_answer_input_high_pressure)
            binding.inputArtist.setBackgroundResource(R.drawable.bg_answer_input_high_pressure)

            binding.buttonRevealAnswer.setBackgroundResource(R.drawable.bg_button_high_pressure_launch)
            binding.buttonBackToPlayer.setBackgroundResource(R.drawable.bg_button_high_pressure_secondary)

            binding.textAnswerEyebrow.setTextColor(white)
            binding.textAnswerLineOne.setTextColor(white)
            binding.textAnswerLineTwo.setTextColor(red)
            binding.textAnswerHint.setTextColor(softWhite)
            binding.textAnswerStatus.setTextColor(softWhite)

            binding.textGuessLabel.setTextColor(white)
            binding.textRevealTitle.setTextColor(white)
            binding.textRevealSong.setTextColor(softWhite)
            binding.textRevealArtist.setTextColor(softWhite)
            binding.textRevealYear.setTextColor(softWhite)

            binding.inputYear.setTextColor(white)
            binding.inputTitle.setTextColor(white)
            binding.inputArtist.setTextColor(white)

            binding.inputYear.setHintTextColor(Color.parseColor("#C9B0B3"))
            binding.inputTitle.setHintTextColor(Color.parseColor("#C9B0B3"))
            binding.inputArtist.setHintTextColor(Color.parseColor("#C9B0B3"))

            binding.buttonRevealAnswer.setTextColor(white)
            binding.buttonBackToPlayer.setTextColor(white)
        } else {
            binding.answerRoot.setBackgroundResource(R.drawable.bg_screen)
            binding.textAnswerEyebrow.background =
                ContextCompat.getDrawable(this, R.drawable.bg_home_eyebrow)

            binding.layoutGuessCard.setBackgroundResource(R.drawable.bg_hero_panel)
            binding.layoutRevealCard.setBackgroundResource(R.drawable.bg_card)

            binding.inputYear.setBackgroundResource(R.drawable.bg_answer_input)
            binding.inputTitle.setBackgroundResource(R.drawable.bg_answer_input)
            binding.inputArtist.setBackgroundResource(R.drawable.bg_answer_input)

            binding.buttonRevealAnswer.setBackgroundResource(R.drawable.bg_button_primary)
            binding.buttonBackToPlayer.setBackgroundResource(R.drawable.bg_button_secondary)

            binding.textAnswerEyebrow.setTextColor(
                ContextCompat.getColor(this, R.color.accent_amber)
            )
            binding.textAnswerLineOne.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textAnswerLineTwo.setTextColor(
                ContextCompat.getColor(this, R.color.accent_amber)
            )
            binding.textAnswerHint.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textAnswerStatus.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )

            binding.textGuessLabel.setTextColor(
                ContextCompat.getColor(this, R.color.accent_amber)
            )
            binding.textRevealTitle.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textRevealSong.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textRevealArtist.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textRevealYear.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )

            binding.inputYear.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.inputTitle.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.inputArtist.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )

            binding.inputYear.setHintTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.inputTitle.setHintTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.inputArtist.setHintTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )

            binding.buttonRevealAnswer.setTextColor(
                ContextCompat.getColor(this, R.color.button_primary_text)
            )
            binding.buttonBackToPlayer.setTextColor(
                ContextCompat.getColor(this, R.color.button_secondary_text)
            )
        }
    }

    private fun applyModeText() {
        binding.textAnswerEyebrow.text =
            if (ClassicModeConfig.isHighPressure(classicVariant)) {
                "• SOLO HIGH PRESSURE"
            } else {
                "• SOLO MODE"
            }

        binding.textAnswerHint.text =
            if (ClassicModeConfig.isHighPressure(classicVariant)) {
                "High Pressure blijft streng. Je hoort in die mode alleen korte starts van 10 seconden."
            } else {
                "Vul je gok in en toon daarna het echte antwoord wanneer je klaar bent."
            }
    }

    private fun renderCurrentRound() {
        val state = AppStateStore.state.value
        val hasTrack = state.lastResolvedSpotifyUri.isNotBlank()

        binding.textAnswerStatus.text = if (hasTrack) {
            "Vul hieronder je jaartal, nummer en artiest in."
        } else {
            "Er is nog geen actieve solo-ronde."
        }

        binding.buttonRevealAnswer.isEnabled = hasTrack
        binding.buttonRevealAnswer.alpha = if (hasTrack) 1f else 0.55f

        binding.inputYear.isEnabled = hasTrack
        binding.inputTitle.isEnabled = hasTrack
        binding.inputArtist.isEnabled = hasTrack
    }

    private fun revealAnswer() {
        val state = AppStateStore.state.value

        binding.layoutRevealCard.visibility = View.VISIBLE
        binding.textRevealSong.text =
            "Nummer: ${state.currentTrackName.ifBlank { "Onbekend nummer" }}"
        binding.textRevealArtist.text =
            "Artiest: ${state.currentArtistName.ifBlank { "Onbekende artiest" }}"
        binding.textRevealYear.text =
            "Jaartal: nog niet automatisch beschikbaar in Classic Mode"
    }
}
