package com.example.qrspotify.duel

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.qrspotify.R
import com.example.qrspotify.databinding.ActivityDuelGameBinding
import com.example.qrspotify.practice.PracticeTrack
import com.example.qrspotify.practice.PracticeTrackPool
import com.example.qrspotify.spotify.SpotifyManager

class DuelGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuelGameBinding

    private var duelState: DuelGameState = DuelGameState.Idle
    private var currentTrack: PracticeTrack? = null
    private var lastBuzzedPlayerIndex: Int? = null
    private var lastMarkedCorrect: Boolean? = null
    private var shouldResetSessionOnDestroy: Boolean = false

    private var topBuzzerAnimator: ObjectAnimator? = null
    private var bottomBuzzerAnimator: ObjectAnimator? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var winnerLaunchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val snapshot = DuelScoreStore.getSnapshot()
        if (!snapshot.isInitialized) {
            finish()
            return
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        binding = ActivityDuelGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLeaveDialog()
            }
        })

        binding.buttonExitDuel.setOnClickListener {
            showLeaveDialog()
        }

        binding.buttonBuzzTop.setOnClickListener {
            handleBuzz(PLAYER_TWO_INDEX)
        }

        binding.buttonBuzzBottom.setOnClickListener {
            handleBuzz(PLAYER_ONE_INDEX)
        }

        binding.buttonRevealAnswer.setOnClickListener {
            revealAnswer()
        }

        binding.buttonMarkCorrect.setOnClickListener {
            applyManualRoundResult(true)
        }

        binding.buttonMarkWrong.setOnClickListener {
            applyManualRoundResult(false)
        }

        binding.buttonNextRound.setOnClickListener {
            DuelScoreStore.advanceRound()
            startNewRound()
        }

        render()
        startNewRound()
    }

    override fun onStart() {
        super.onStart()
        SpotifyManager.refreshPlayerState()
    }

    override fun onDestroy() {
        stopBuzzerPulse()
        clearPendingCallbacks()
        super.onDestroy()

        if (isFinishing) {
            SpotifyManager.stopPlayback()
            if (shouldResetSessionOnDestroy) {
                DuelScoreStore.resetSession()
            }
        }
    }

    private fun startNewRound() {
        stopBuzzerPulse()
        clearPendingCallbacks()

        val snapshot = DuelScoreStore.getSnapshot()
        val nextTrack = PracticeTrackPool.getRandomTrack(
            categoryKey = snapshot.categoryKey,
            previousUri = snapshot.lastTrackUri
        )

        currentTrack = nextTrack
        lastBuzzedPlayerIndex = null
        lastMarkedCorrect = null
        DuelScoreStore.setLastTrackUri(nextTrack.spotifyUri)

        duelState = DuelGameState.Loading
        render()

        SpotifyManager.connectAndPlay(this, nextTrack.spotifyUri) { success, message ->
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread

                if (success) {
                    duelState = DuelGameState.Listening
                    render()
                } else {
                    duelState = DuelGameState.Idle
                    render()
                    showLoadErrorDialog(message)
                }
            }
        }
    }

    private fun handleBuzz(playerIndex: Int) {
        if (duelState !is DuelGameState.Listening) return

        vibrateBuzz()
        stopBuzzerPulse()
        lastBuzzedPlayerIndex = playerIndex
        duelState = DuelGameState.Buzzed(playerIndex)
        render()

        SpotifyManager.pause { _, _ ->
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    render()
                }
            }
        }
    }

    private fun revealAnswer() {
        val buzzedPlayerIndex = lastBuzzedPlayerIndex ?: return
        duelState = DuelGameState.Revealing(buzzedPlayerIndex)
        render()
    }

    private fun applyManualRoundResult(wasCorrect: Boolean) {
        val buzzedPlayerIndex = lastBuzzedPlayerIndex ?: return

        lastMarkedCorrect = wasCorrect

        if (wasCorrect) {
            DuelScoreStore.awardPointTo(buzzedPlayerIndex)
        }

        val winnerIndex = DuelScoreStore.getWinnerIndex()
        if (winnerIndex != null) {
            duelState = DuelGameState.GameOver(winnerIndex)
            render()

            winnerLaunchRunnable = Runnable {
                openWinnerScreen(winnerIndex)
            }.also {
                uiHandler.postDelayed(it, WINNER_SCREEN_DELAY_MS)
            }
        } else {
            duelState = DuelGameState.RoundOver
            render()
        }
    }

    private fun openWinnerScreen(winnerIndex: Int) {
        if (isDestroyed || isFinishing) return

        val intent = Intent(this, DuelWinnerActivity::class.java).apply {
            putExtra(DuelWinnerActivity.EXTRA_WINNER_INDEX, winnerIndex)
        }
        startActivity(intent)
        finish()
    }

    private fun render() {
        val snapshot = DuelScoreStore.getSnapshot()

        binding.textPlayerOneName.text = snapshot.playerOneName
        binding.textPlayerTwoName.text = snapshot.playerTwoName
        binding.textPlayerOneScore.text = snapshot.playerOneScore.toString()
        binding.textPlayerTwoScore.text = snapshot.playerTwoScore.toString()
        binding.textRoundCounter.text = getString(
            R.string.duel_round_counter,
            snapshot.roundNumber,
            snapshot.targetScore
        )
        binding.textCategoryLabel.text = PracticeTrackPool.getLabel(snapshot.categoryKey)

        binding.layoutBuzzButtons.visibility = View.GONE

        binding.cardBuzzedPanel.visibility =
            if (duelState is DuelGameState.Buzzed) View.VISIBLE else View.GONE

        binding.cardRevealPanel.visibility =
            if (
                duelState is DuelGameState.Revealing ||
                duelState is DuelGameState.RoundOver ||
                duelState is DuelGameState.GameOver
            ) View.VISIBLE else View.GONE

        binding.layoutJudgementButtons.visibility =
            if (duelState is DuelGameState.Revealing) View.VISIBLE else View.GONE

        binding.buttonNextRound.visibility =
            if (duelState is DuelGameState.RoundOver) View.VISIBLE else View.GONE

        val isListening = duelState is DuelGameState.Listening
        binding.buttonBuzzTop.isEnabled = isListening
        binding.buttonBuzzBottom.isEnabled = isListening
        binding.buttonBuzzTop.alpha = if (isListening) 1f else 0.45f
        binding.buttonBuzzBottom.alpha = if (isListening) 1f else 0.45f

        when (duelState) {
            is DuelGameState.Idle -> {
                binding.textArenaStatus.text = getString(R.string.duel_status_idle)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_default)
                stopBuzzerPulse()
            }

            is DuelGameState.Loading -> {
                binding.textArenaStatus.text = getString(R.string.duel_status_loading)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_loading)
                stopBuzzerPulse()
            }

            is DuelGameState.Listening -> {
                binding.textArenaStatus.text = getString(R.string.duel_status_listening)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_listening)
                startBuzzerPulse()
            }

            is DuelGameState.Buzzed -> {
                val playerName = snapshot.playerNameFor((duelState as DuelGameState.Buzzed).playerIndex)
                binding.textArenaStatus.text = getString(R.string.duel_status_buzzed, playerName)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_buzzed)

                binding.textBuzzedTitle.text = getString(R.string.duel_buzzed_title, playerName)
                binding.textBuzzedBody.text = getString(R.string.duel_buzzed_body, playerName)

                ensureCenterPanelVisible(showRevealPanel = false)
                stopBuzzerPulse()
            }

            is DuelGameState.Revealing -> {
                renderReveal(snapshot)
                binding.textArenaStatus.text = getString(R.string.duel_status_revealing)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_revealing_manual)
                ensureCenterPanelVisible(showRevealPanel = true)
                stopBuzzerPulse()
            }

            is DuelGameState.RoundOver -> {
                renderReveal(snapshot)
                binding.textArenaStatus.text = getString(R.string.duel_status_round_over)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_next_round)
                ensureCenterPanelVisible(showRevealPanel = true)
                stopBuzzerPulse()
            }

            is DuelGameState.GameOver -> {
                renderReveal(snapshot)
                val winnerName = snapshot.playerNameFor((duelState as DuelGameState.GameOver).winnerIndex)
                binding.textArenaStatus.text =
                    getString(R.string.duel_status_game_over, winnerName)
                binding.textArenaHint.text = getString(R.string.duel_status_helper_game_over)
                ensureCenterPanelVisible(showRevealPanel = true)
                stopBuzzerPulse()
            }
        }
    }

    private fun renderReveal(snapshot: DuelSessionSnapshot) {
        val track = currentTrack ?: return
        val buzzedPlayerIndex = lastBuzzedPlayerIndex ?: return
        val buzzedPlayerName = snapshot.playerNameFor(buzzedPlayerIndex)

        binding.textRevealTitle.text =
            getString(R.string.duel_reveal_title, buzzedPlayerName)

        binding.textRevealAnswer.text = getString(
            R.string.duel_reveal_answer_format,
            track.year,
            track.title,
            track.artist
        )

        binding.textRevealResult.text = when (lastMarkedCorrect) {
            true -> getString(R.string.duel_result_correct, buzzedPlayerName)
            false -> getString(R.string.duel_result_wrong)
            null -> getString(R.string.duel_result_pending)
        }
    }

    private fun ensureCenterPanelVisible(showRevealPanel: Boolean) {
        val targetView = if (showRevealPanel) binding.cardRevealPanel else binding.cardBuzzedPanel
        binding.scrollCenterArena.post {
            targetView.requestFocus()
            binding.scrollCenterArena.smoothScrollTo(0, targetView.bottom)
        }
    }

    private fun startBuzzerPulse() {
        if (topBuzzerAnimator?.isRunning == true || bottomBuzzerAnimator?.isRunning == true) {
            return
        }

        binding.buttonBuzzTop.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.buttonBuzzBottom.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        topBuzzerAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.buttonBuzzTop,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = BUZZER_PULSE_DURATION_MS
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }

        bottomBuzzerAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.buttonBuzzBottom,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = BUZZER_PULSE_DURATION_MS
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopBuzzerPulse() {
        topBuzzerAnimator?.cancel()
        bottomBuzzerAnimator?.cancel()
        topBuzzerAnimator = null
        bottomBuzzerAnimator = null

        if (::binding.isInitialized) {
            binding.buttonBuzzTop.scaleX = 1f
            binding.buttonBuzzTop.scaleY = 1f
            binding.buttonBuzzBottom.scaleX = 1f
            binding.buttonBuzzBottom.scaleY = 1f
            binding.buttonBuzzTop.setLayerType(View.LAYER_TYPE_NONE, null)
            binding.buttonBuzzBottom.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun vibrateBuzz() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                VIBRATION_DURATION_MS,
                VIBRATION_AMPLITUDE
            )
        )
    }

    private fun showLeaveDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.duel_leave_title)
            .setMessage(R.string.duel_leave_message)
            .setPositiveButton(R.string.duel_leave_confirm) { _, _ ->
                shouldResetSessionOnDestroy = true
                finish()
            }
            .setNegativeButton(R.string.duel_leave_cancel, null)
            .show()
    }

    private fun showLoadErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.duel_load_error_title)
            .setMessage(getString(R.string.duel_load_error_message, message))
            .setPositiveButton(R.string.duel_retry) { _, _ ->
                startNewRound()
            }
            .setNegativeButton(R.string.duel_leave_confirm) { _, _ ->
                shouldResetSessionOnDestroy = true
                finish()
            }
            .show()
    }

    private fun clearPendingCallbacks() {
        winnerLaunchRunnable?.let(uiHandler::removeCallbacks)
        winnerLaunchRunnable = null
    }

    companion object {
        private const val PLAYER_ONE_INDEX = DuelScoreStore.PLAYER_ONE_INDEX
        private const val PLAYER_TWO_INDEX = DuelScoreStore.PLAYER_TWO_INDEX
        private const val WINNER_SCREEN_DELAY_MS = 900L
        private const val BUZZER_PULSE_DURATION_MS = 800L
        private const val VIBRATION_DURATION_MS = 80L
        private const val VIBRATION_AMPLITUDE = 255
    }
}
