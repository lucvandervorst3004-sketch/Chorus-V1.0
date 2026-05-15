package com.example.qrspotify.duel

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.MainActivity
import com.example.qrspotify.R
import com.example.qrspotify.databinding.ActivityDuelWinnerBinding
import com.example.qrspotify.spotify.SpotifyManager

class DuelWinnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuelWinnerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDuelWinnerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        val snapshot = DuelScoreStore.getSnapshot()
        if (!snapshot.isInitialized) {
            finish()
            return
        }

        val winnerIndex = intent.getIntExtra(
            EXTRA_WINNER_INDEX,
            DuelScoreStore.getWinnerIndex() ?: DuelScoreStore.PLAYER_ONE_INDEX
        )
        val winnerName = snapshot.playerNameFor(winnerIndex)

        binding.textWinnerTitle.text = getString(R.string.duel_winner_title, winnerName)
        binding.textWinnerSubtitle.text = getString(R.string.duel_winner_subtitle)
        binding.textWinnerPlayerOne.text = getString(
            R.string.duel_score_line,
            snapshot.playerOneName,
            snapshot.playerOneScore
        )
        binding.textWinnerPlayerTwo.text = getString(
            R.string.duel_score_line,
            snapshot.playerTwoName,
            snapshot.playerTwoScore
        )

        binding.buttonNewDuelGame.setOnClickListener {
            DuelScoreStore.resetSession()
            startActivity(
                Intent(this, DuelSetupActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }

        binding.buttonWinnerHome.setOnClickListener {
            DuelScoreStore.resetSession()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private fun applyInsets() {
        val initialLeft = binding.contentDuelWinner.paddingLeft
        val initialTop = binding.contentDuelWinner.paddingTop
        val initialRight = binding.contentDuelWinner.paddingRight
        val initialBottom = binding.contentDuelWinner.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootDuelWinner) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentDuelWinner.setPadding(
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

    companion object {
        const val EXTRA_WINNER_INDEX = "extra_winner_index"
    }
}
