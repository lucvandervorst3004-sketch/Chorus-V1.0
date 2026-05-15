package com.example.qrspotify.ui.practice

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
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
import com.example.qrspotify.databinding.ActivityPracticeRoundBinding
import com.example.qrspotify.model.AppUiState
import com.example.qrspotify.practice.PracticeAnswerEvaluator
import com.example.qrspotify.practice.PracticeStatsStore
import com.example.qrspotify.practice.PracticeTrack
import com.example.qrspotify.practice.PracticeTrackPool
import com.example.qrspotify.spotify.SpotifyManager
import kotlinx.coroutines.launch

class PracticeRoundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticeRoundBinding

    private var currentState: AppUiState = AppStateStore.state.value
    private var currentCategoryKey: String = PracticeTrackPool.CATEGORY_ENGLISH
    private var currentTrack: PracticeTrack? = null
    private var isRoundReady = false
    private var hasSubmittedCurrentRound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        binding = ActivityPracticeRoundBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        currentCategoryKey = intent.getStringExtra(EXTRA_CATEGORY_KEY)
            ?: PracticeTrackPool.CATEGORY_ENGLISH

        binding.textPracticeLanguage.text = PracticeTrackPool.getLabel(currentCategoryKey)

        binding.buttonBackToPractice.setOnClickListener {
            finish()
        }

        binding.buttonTogglePlayback.setOnClickListener {
            val activeTrack = currentTrack ?: return@setOnClickListener
            if (!isRoundReady) return@setOnClickListener

            if (currentState.isPlaying) {
                SpotifyManager.pause { _, _ -> }
            } else {
                SpotifyManager.resume { success, _ ->
                    if (!success) {
                        SpotifyManager.playUri(activeTrack.spotifyUri) { _, _ -> }
                    }
                }
            }
        }

        binding.buttonNextPracticeTrack.setOnClickListener {
            startNewRound()
        }

        binding.buttonCheckAnswers.setOnClickListener {
            checkAnswers()
        }

        setupInputScrolling()
        renderScores()
        observeState()
        startNewRound()
    }

    private fun applyInsets() {
        val initialLeft = binding.practiceRoundScrollView.paddingLeft
        val initialTop = binding.practiceRoundScrollView.paddingTop
        val initialRight = binding.practiceRoundScrollView.paddingRight
        val initialBottom = binding.practiceRoundScrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.practiceRoundScrollView) { view, insets ->
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
        renderScores()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            SpotifyManager.stopPlayback()
        }
    }

    private fun setupInputScrolling() {
        setupScrollOnFocus(binding.inputYear)
        setupScrollOnFocus(binding.inputTitle)
        setupScrollOnFocus(binding.inputArtist)
    }

    private fun setupScrollOnFocus(target: View) {
        target.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.postDelayed({
                    binding.practiceRoundScrollView.smoothScrollTo(
                        0,
                        (view.bottom + dpToPx(220f)).toInt()
                    )
                }, 250)
            }
        }

        target.setOnClickListener { view ->
            view.postDelayed({
                binding.practiceRoundScrollView.smoothScrollTo(
                    0,
                    (view.bottom + dpToPx(220f)).toInt()
                )
            }, 250)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppStateStore.state.collect { state ->
                    currentState = state
                    renderPlaybackControls(state)
                }
            }
        }
    }

    private fun startNewRound() {
        val previousUri = currentTrack?.spotifyUri
        currentTrack = PracticeTrackPool.getRandomTrack(currentCategoryKey, previousUri)

        isRoundReady = false
        hasSubmittedCurrentRound = false

        binding.inputYear.text?.clear()
        binding.inputTitle.text?.clear()
        binding.inputArtist.text?.clear()

        binding.layoutResultCard.visibility = View.GONE
        binding.textRoundStatus.text = "Nieuw nummer wordt gestart..."
        binding.buttonCheckAnswers.isEnabled = false
        binding.buttonTogglePlayback.isEnabled = false
        binding.buttonTogglePlayback.text = "Laden..."

        binding.practiceRoundScrollView.post {
            binding.practiceRoundScrollView.smoothScrollTo(0, 0)
        }

        val trackToPlay = currentTrack ?: return

        SpotifyManager.connectAndPlay(this, trackToPlay.spotifyUri) { success, message ->
            runOnUiThread {
                if (success) {
                    isRoundReady = true
                    binding.textRoundStatus.text =
                        "Luister goed, vul je antwoord in en controleer wanneer je wilt."
                } else {
                    binding.textRoundStatus.text = message
                    isRoundReady = false
                }

                renderPlaybackControls(currentState)
            }
        }
    }

    private fun checkAnswers() {
        if (!isRoundReady || hasSubmittedCurrentRound) return

        val track = currentTrack ?: return

        val result = PracticeAnswerEvaluator.evaluate(
            track = track,
            yearGuess = binding.inputYear.text?.toString().orEmpty(),
            titleGuess = binding.inputTitle.text?.toString().orEmpty(),
            artistGuess = binding.inputArtist.text?.toString().orEmpty()
        )

        hasSubmittedCurrentRound = true
        PracticeStatsStore.recordRound(currentCategoryKey, result.correctAnswers)
        renderScores()

        binding.layoutResultCard.visibility = View.VISIBLE
        binding.textResultTitle.text = when (result.correctAnswers) {
            3 -> "Perfecte ronde"
            2 -> "Sterke poging"
            1 -> "Deels goed"
            else -> "Nog niet goed"
        }

        binding.textResultBody.text =
            "${result.correctAnswers} van de 3 antwoorden zijn goedgekeurd."

        renderAnswerLine(
            textView = binding.textResultYear,
            isCorrect = result.isYearCorrect,
            label = "Jaartal",
            correctAnswer = track.year
        )

        renderAnswerLine(
            textView = binding.textResultSong,
            isCorrect = result.isTitleCorrect,
            label = "Nummer",
            correctAnswer = track.title
        )

        renderAnswerLine(
            textView = binding.textResultArtist,
            isCorrect = result.isArtistCorrect,
            label = "Artiest",
            correctAnswer = track.artist
        )

        binding.practiceRoundScrollView.postDelayed({
            binding.practiceRoundScrollView.smoothScrollTo(
                0,
                binding.layoutResultCard.bottom + dpToPx(80f).toInt()
            )
        }, 150)

        renderPlaybackControls(currentState)
    }

    private fun renderAnswerLine(
        textView: TextView,
        isCorrect: Boolean,
        label: String,
        correctAnswer: String
    ) {
        if (isCorrect) {
            textView.text = "$label goed"
            textView.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
        } else {
            textView.text = "$label fout. Goed antwoord: $correctAnswer"
            textView.setTextColor(ContextCompat.getColor(this, R.color.error_red))
        }
    }

    private fun renderPlaybackControls(state: AppUiState) {
        val canControlPlayback = currentTrack != null && isRoundReady && state.isConnected

        binding.buttonTogglePlayback.isEnabled = canControlPlayback
        binding.buttonCheckAnswers.isEnabled = isRoundReady && !hasSubmittedCurrentRound

        binding.buttonTogglePlayback.alpha =
            if (canControlPlayback) 1f else 0.55f

        binding.buttonCheckAnswers.alpha =
            if (binding.buttonCheckAnswers.isEnabled) 1f else 0.55f

        binding.buttonTogglePlayback.text = when {
            !canControlPlayback -> "Niet klaar"
            state.isPlaying -> "Pauze"
            else -> "Luister"
        }
    }

    private fun renderScores() {
        val session = PracticeStatsStore.getSessionSnapshot()
        val categorySession = PracticeStatsStore.getCategorySessionStats(currentCategoryKey)
        val currentCategoryHighScore =
            PracticeStatsStore.getAllTimeHighScoreForCategory(currentCategoryKey)
        val currentCategoryHighestStreak =
            PracticeStatsStore.getAllTimeHighestStreakForCategory(currentCategoryKey)

        val totalCorrect = session.totalCorrectAnswers
        val totalAnswers = session.totalSubmittedAnswers

        binding.textPracticeScore.text = if (totalAnswers == 0) {
            "0/0"
        } else {
            "$totalCorrect/$totalAnswers"
        }

        binding.textPracticeRounds.text =
            "Rondes deze sessie: ${session.totalRounds}"

        binding.textCurrentStreak.text = if (categorySession.currentStreak > 0) {
            "${categorySession.currentStreak}🔥"
        } else {
            "0"
        }

        binding.textHighestStreak.text =
            "Beste streak: ${categorySession.highestSessionStreak} | Record: $currentCategoryHighestStreak"

        binding.textAllTimeHighScore.text = currentCategoryHighScore.toString()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    companion object {
        const val EXTRA_CATEGORY_KEY = "practice_category_key"
    }
}
