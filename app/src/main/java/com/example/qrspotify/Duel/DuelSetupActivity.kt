package com.example.qrspotify.duel

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.R
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivityDuelSetupBinding
import com.example.qrspotify.practice.PracticeTrackPool
import com.example.qrspotify.spotify.SpotifyManager

class DuelSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuelSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!AppStateStore.state.value.isConnected) {
            Toast.makeText(this, getString(R.string.duel_connect_first), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityDuelSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        binding.inputPlayerOneName.setText(getString(R.string.duel_default_player_one))
        binding.inputPlayerTwoName.setText(getString(R.string.duel_default_player_two))
        binding.radioCategoryEnglish.isChecked = true

        setupWinningScorePicker()

        binding.buttonBackDuelSetup.setOnClickListener {
            finish()
        }

        binding.buttonStartDuel.setOnClickListener {
            startDuel()
        }
    }

    private fun applyInsets() {
        val initialLeft = binding.contentDuelSetup.paddingLeft
        val initialTop = binding.contentDuelSetup.paddingTop
        val initialRight = binding.contentDuelSetup.paddingRight
        val initialBottom = binding.contentDuelSetup.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootDuelSetup) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentDuelSetup.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            SpotifyManager.stopPlayback()
        }
    }

    private fun setupWinningScorePicker() {
        binding.seekWinningScore.min = DuelScoreStore.MIN_WINNING_SCORE
        binding.seekWinningScore.max = DuelScoreStore.MAX_WINNING_SCORE
        binding.seekWinningScore.progress = DuelScoreStore.DEFAULT_WINNING_SCORE
        updateWinningScoreLabel(binding.seekWinningScore.progress)

        binding.seekWinningScore.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateWinningScoreLabel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateWinningScoreLabel(score: Int) {
        binding.textWinningScoreValue.text = getString(R.string.duel_first_to_value, score)
    }

    private fun startDuel() {
        val playerOneName = binding.inputPlayerOneName.text
            ?.toString()
            .orEmpty()
            .trim()
            .ifBlank { getString(R.string.duel_default_player_one) }

        val playerTwoName = binding.inputPlayerTwoName.text
            ?.toString()
            .orEmpty()
            .trim()
            .ifBlank { getString(R.string.duel_default_player_two) }

        DuelScoreStore.initializeSession(
            playerOneName = playerOneName,
            playerTwoName = playerTwoName,
            categoryKey = getSelectedCategoryKey(),
            targetScore = binding.seekWinningScore.progress
        )

        startActivity(Intent(this, DuelGameActivity::class.java))
    }

    private fun getSelectedCategoryKey(): String {
        return when (binding.radioGroupCategories.checkedRadioButtonId) {
            R.id.radioCategoryDutch -> PracticeTrackPool.CATEGORY_DUTCH
            else -> PracticeTrackPool.CATEGORY_ENGLISH
        }
    }
}
