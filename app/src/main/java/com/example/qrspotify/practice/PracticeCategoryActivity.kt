package com.example.qrspotify.ui.practice

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.R
import com.example.qrspotify.databinding.ActivityPracticeCategoryBinding
import com.example.qrspotify.practice.PracticeSessionSnapshot
import com.example.qrspotify.practice.PracticeStatsStore
import com.example.qrspotify.practice.PracticeTrackPool

class PracticeCategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticeCategoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPracticeCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitPracticeMode()
            }
        })

        binding.buttonDutch.setOnClickListener {
            openRound(PracticeTrackPool.CATEGORY_DUTCH)
        }

        binding.buttonEnglish.setOnClickListener {
            openRound(PracticeTrackPool.CATEGORY_ENGLISH)
        }

        binding.buttonBackToHome.setOnClickListener {
            exitPracticeMode()
        }

        renderScores()
    }

    private fun applyInsets() {
        val initialLeft = binding.contentPracticeCategory.paddingLeft
        val initialTop = binding.contentPracticeCategory.paddingTop
        val initialRight = binding.contentPracticeCategory.paddingRight
        val initialBottom = binding.contentPracticeCategory.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPracticeCategory) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentPracticeCategory.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        renderScores()
    }

    private fun openRound(categoryKey: String) {
        val intent = Intent(this, PracticeRoundActivity::class.java).apply {
            putExtra(PracticeRoundActivity.EXTRA_CATEGORY_KEY, categoryKey)
        }
        startActivity(intent)
    }

    private fun exitPracticeMode() {
        PracticeStatsStore.resetSession()
        PracticeTrackPool.resetSessionHistory()
        finish()
    }

    private fun renderScores() {
        val session = PracticeStatsStore.getSessionSnapshot()
        val dutchHighScore = PracticeStatsStore.getDutchHighScore()
        val englishHighScore = PracticeStatsStore.getEnglishHighScore()

        renderSessionScore(session)
        binding.textHighscoreDutch.text = getString(R.string.practice_highscore_dutch, dutchHighScore)
        binding.textHighscoreEnglish.text = getString(R.string.practice_highscore_english, englishHighScore)
    }

    private fun renderSessionScore(snapshot: PracticeSessionSnapshot) {
        if (snapshot.totalSubmittedAnswers == 0) {
            binding.textPracticeScore.text = getString(R.string.practice_session_score_empty)
            binding.textPracticeRounds.text = getString(R.string.practice_session_rounds_empty)
        } else {
            binding.textPracticeScore.text = getString(
                R.string.practice_session_score_format,
                snapshot.totalCorrectAnswers,
                snapshot.totalSubmittedAnswers
            )
            binding.textPracticeRounds.text = getString(
                R.string.practice_session_rounds_format,
                snapshot.totalRounds
            )
        }
    }
}
