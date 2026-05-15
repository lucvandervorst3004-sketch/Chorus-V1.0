package com.example.qrspotify.ui.renelebak

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
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
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivityReneLeBakBinding
import com.example.qrspotify.model.AppUiState
import com.example.qrspotify.spotify.SpotifyManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ReneLeBakActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReneLeBakBinding

    private var currentState: AppUiState = AppStateStore.state.value
    private var isLaunchingTrack = false
    private var isSequenceRunning = false
    private var queuedTurns = 1
    private var currentOutcome: ReneLeBakOutcome? = null
    private var autoRunJob: Job? = null
    private var turntableAnimator: ObjectAnimator? = null
    private val eqAnimators = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityReneLeBakBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()

        binding.buttonBackReneLeBak.setOnClickListener {
            finish()
        }

        binding.buttonMinusTurns.setOnClickListener {
            if (isLaunchingTrack || isAnyPopupVisible() || isSequenceRunning) return@setOnClickListener
            queuedTurns = (queuedTurns - 1).coerceAtLeast(1)
            renderTurnCounter()
        }

        binding.buttonPlusTurns.setOnClickListener {
            if (isLaunchingTrack || isAnyPopupVisible() || isSequenceRunning) return@setOnClickListener
            queuedTurns = (queuedTurns + 1).coerceAtMost(99)
            renderTurnCounter()
        }

        binding.buttonStartReneLeBak.setOnClickListener {
            if (isLaunchingTrack) return@setOnClickListener
            if (queuedTurns <= 0) return@setOnClickListener
            startSequence()
        }

        binding.buttonContinuePopup.setOnClickListener {
            binding.layoutLulPopup.visibility = View.GONE
            continueAfterHit()
        }

        binding.buttonFinishPopupClose.setOnClickListener {
            binding.layoutFinishPopup.visibility = View.GONE
            resetForNextPlayer()
        }

        observeState()
        renderIdleState()
    }

    override fun onStart() {
        super.onStart()
        SpotifyManager.refreshPlayerState()
    }

    override fun onDestroy() {
        autoRunJob?.cancel()
        stopStageMotion()
        super.onDestroy()

        if (isFinishing) {
            SpotifyManager.stopPlayback()
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootRene) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentRene.setPadding(
                0,
                systemBars.top + dp(12),
                0,
                systemBars.bottom + dp(28)
            )
            insets
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppStateStore.state.collect { state ->
                    currentState = state
                    renderMainButton()
                }
            }
        }
    }

    private fun startSequence() {
        isSequenceRunning = true
        renderMainButton()
        startNextTurn()
    }

    private fun startNextTurn() {
        if (queuedTurns <= 0) {
            finishSequence()
            return
        }

        if (isLaunchingTrack) return

        isLaunchingTrack = true
        currentOutcome = pickRandomOutcome()

        binding.textReneStatus.text = getString(R.string.rene_loading_turn)
        binding.layoutReneResult.visibility = View.GONE
        binding.layoutLulPopup.visibility = View.GONE
        binding.layoutFinishPopup.visibility = View.GONE
        renderTurnCounter()
        renderMainButton()

        val outcome = currentOutcome ?: return

        SpotifyManager.connectAndPlay(this, outcome.spotifyUri) { success, message ->
            runOnUiThread {
                isLaunchingTrack = false

                if (!success) {
                    isSequenceRunning = false
                    binding.textReneStatus.text = message
                    renderMainButton()
                    return@runOnUiThread
                }

                showOutcome(outcome)

                when (outcome.type) {
                    ReneLeBakOutcomeType.SAFE -> {
                        queuedTurns = (queuedTurns - 1).coerceAtLeast(0)
                        renderTurnCounter()
                        binding.textReneStatus.text = getString(R.string.rene_safe_status)
                        scheduleSafeContinue()
                    }

                    ReneLeBakOutcomeType.SINGLE -> {
                        binding.textReneStatus.text = getString(R.string.rene_hit_wait)
                        showNormalHitPopup(
                            title = getString(R.string.rene_popup_single_title),
                            subtitle = getString(R.string.rene_popup_single_subtitle)
                        )
                    }

                    ReneLeBakOutcomeType.PARTY -> {
                        binding.textReneStatus.text = getString(R.string.rene_party_wait)
                        showPartyHitPopup(
                            title = getString(R.string.rene_popup_party_title),
                            subtitle = getString(R.string.rene_popup_party_subtitle)
                        )
                    }
                }

                renderMainButton()
            }
        }
    }

    private fun scheduleSafeContinue() {
        autoRunJob?.cancel()
        autoRunJob = lifecycleScope.launch {
            delay(2200)
            if (queuedTurns > 0 && !isAnyPopupVisible()) {
                startNextTurn()
            } else if (queuedTurns == 0) {
                finishSequence()
            }
        }
    }

    private fun continueAfterHit() {
        if (queuedTurns <= 0) {
            finishSequence()
            return
        }

        queuedTurns = (queuedTurns - 1).coerceAtLeast(0)
        renderTurnCounter()

        if (queuedTurns == 0) {
            finishSequence()
        } else {
            binding.textReneStatus.text = getString(R.string.rene_safe_status)
            startNextTurn()
        }
    }

    private fun finishSequence() {
        autoRunJob?.cancel()
        isSequenceRunning = false
        binding.textReneStatus.text = getString(R.string.rene_round_ready)
        binding.layoutReneResult.visibility = View.GONE
        showFinishPopup()
        renderMainButton()
    }

    private fun resetForNextPlayer() {
        queuedTurns = 1
        currentOutcome = null
        isSequenceRunning = false
        binding.textReneStatus.text = getString(R.string.rene_reset_status)
        binding.layoutReneResult.visibility = View.GONE
        renderTurnCounter()
        renderMainButton()
    }

    private fun showOutcome(outcome: ReneLeBakOutcome) {
        val resultBackground: Int
        val resultColor: Int

        when (outcome.type) {
            ReneLeBakOutcomeType.SAFE -> {
                resultBackground = R.drawable.bg_rene_result_safe_refined
                resultColor = R.color.neon_green
                binding.textResultMain.text = getString(R.string.rene_result_safe_main)
                binding.textResultSub.text = getString(R.string.rene_result_safe_sub)
            }

            ReneLeBakOutcomeType.SINGLE -> {
                resultBackground = R.drawable.bg_rene_result_single_refined
                resultColor = R.color.neon_red
                binding.textResultMain.text = getString(R.string.rene_result_single_main)
                binding.textResultSub.text = getString(R.string.rene_result_single_sub)
            }

            ReneLeBakOutcomeType.PARTY -> {
                resultBackground = R.drawable.bg_rene_result_party_refined
                resultColor = R.color.gold_bright
                binding.textResultMain.text = getString(R.string.rene_result_party_main)
                binding.textResultSub.text = getString(R.string.rene_result_party_sub)
            }
        }

        binding.layoutReneResult.background = ContextCompat.getDrawable(this, resultBackground)
        binding.textResultMain.setTextColor(ContextCompat.getColor(this, resultColor))
        revealPanel(binding.layoutReneResult)
    }

    private fun showNormalHitPopup(title: String, subtitle: String) {
        binding.popupCard.background = ContextCompat.getDrawable(this, R.drawable.bg_rene_popup_single_refined)
        binding.textPopupSymbol.background = ContextCompat.getDrawable(this, R.drawable.bg_rene_popup_symbol_red)
        binding.textPopupSymbol.text = getString(R.string.rene_popup_single_symbol)
        binding.textPopupSymbol.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
        binding.textPopupTitle.text = title
        binding.textPopupSubtitle.text = subtitle
        binding.textPopupTitle.textSize = 26f
        binding.textPopupSubtitle.textSize = 17f
        binding.buttonContinuePopup.text = getString(R.string.rene_popup_continue)
        showOverlay(binding.layoutLulPopup, binding.popupCard)
    }

    private fun showPartyHitPopup(title: String, subtitle: String) {
        binding.popupCard.background = ContextCompat.getDrawable(this, R.drawable.bg_rene_popup_party_refined)
        binding.textPopupSymbol.background = ContextCompat.getDrawable(this, R.drawable.bg_rene_popup_symbol_gold)
        binding.textPopupSymbol.text = getString(R.string.rene_popup_party_symbol)
        binding.textPopupSymbol.setTextColor(ContextCompat.getColor(this, R.color.gold_bright))
        binding.textPopupTitle.text = title
        binding.textPopupSubtitle.text = subtitle
        binding.textPopupTitle.textSize = 27f
        binding.textPopupSubtitle.textSize = 18f
        binding.buttonContinuePopup.text = getString(R.string.rene_popup_party_continue)
        showOverlay(binding.layoutLulPopup, binding.popupCard)
    }

    private fun showFinishPopup() {
        showOverlay(binding.layoutFinishPopup, binding.finishCard)
    }

    private fun revealPanel(view: View) {
        view.alpha = 0f
        view.translationY = dp(8).toFloat()
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()
    }

    private fun showOverlay(overlay: View, card: View) {
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        card.scaleX = 0.96f
        card.scaleY = 0.96f

        overlay.animate()
            .alpha(1f)
            .setDuration(150L)
            .start()

        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .start()
    }

    private fun isAnyPopupVisible(): Boolean {
        return binding.layoutLulPopup.visibility == View.VISIBLE ||
                binding.layoutFinishPopup.visibility == View.VISIBLE
    }

    private fun renderIdleState() {
        binding.textReneStatus.text = getString(R.string.rene_reset_status)
        binding.layoutLulPopup.visibility = View.GONE
        binding.layoutFinishPopup.visibility = View.GONE
        binding.layoutReneResult.visibility = View.GONE
        renderTurnCounter()
        renderMainButton()
    }

    private fun renderTurnCounter() {
        binding.textTurnCount.text = queuedTurns.toString()
        binding.textTurnsLabel.text =
            if (queuedTurns == 1) getString(R.string.rene_turn_count_one)
            else getString(R.string.rene_turn_count_many, queuedTurns)
    }

    private fun renderMainButton() {
        val popupVisible = isAnyPopupVisible()
        val canStart = currentState.isConnected &&
                !isLaunchingTrack &&
                !popupVisible &&
                queuedTurns > 0 &&
                !isSequenceRunning

        binding.buttonStartReneLeBak.visibility =
            if (isSequenceRunning || popupVisible) View.GONE else View.VISIBLE

        binding.buttonStartReneLeBak.isEnabled = canStart
        binding.buttonStartReneLeBak.alpha = if (canStart) 1f else 0.6f

        binding.buttonStartReneLeBak.text = when {
            isLaunchingTrack -> getString(R.string.rene_button_busy)
            queuedTurns <= 0 -> getString(R.string.rene_button_done)
            else -> getString(R.string.rene_start_round)
        }

        val canEditTurns = !isLaunchingTrack && !popupVisible && !isSequenceRunning
        binding.buttonMinusTurns.isEnabled = canEditTurns
        binding.buttonPlusTurns.isEnabled = canEditTurns
        binding.buttonMinusTurns.alpha = if (canEditTurns) 1f else 0.6f
        binding.buttonPlusTurns.alpha = if (canEditTurns) 1f else 0.6f

        updateStageMotion()
    }

    private fun updateStageMotion() {
        val shouldAnimate = isSequenceRunning ||
                isLaunchingTrack ||
                binding.layoutReneResult.visibility == View.VISIBLE ||
                isAnyPopupVisible()

        if (shouldAnimate) {
            startStageMotion()
        } else {
            stopStageMotion()
        }
    }

    private fun startStageMotion() {
        if (turntableAnimator?.isRunning == true) return

        binding.viewReneTurntable.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        turntableAnimator = ObjectAnimator.ofFloat(
            binding.viewReneTurntable,
            View.ROTATION,
            0f,
            360f
        ).apply {
            duration = TURNTABLE_ROTATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        eqAnimators.clear()
        getEqBars().forEachIndexed { index, bar ->
            bar.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            bar.post {
                bar.pivotY = bar.height.toFloat()
            }
            ObjectAnimator.ofFloat(
                bar,
                View.SCALE_Y,
                0.35f,
                1f
            ).apply {
                duration = EQ_PULSE_MS + index * 35L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                startDelay = index * 55L
                start()
                eqAnimators += this
            }
        }
    }

    private fun stopStageMotion() {
        turntableAnimator?.cancel()
        turntableAnimator = null
        binding.viewReneTurntable.rotation = 0f
        binding.viewReneTurntable.setLayerType(View.LAYER_TYPE_NONE, null)

        eqAnimators.forEach { it.cancel() }
        eqAnimators.clear()
        getEqBars().forEach { bar ->
            bar.scaleY = 1f
            bar.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun getEqBars(): List<View> {
        return listOf(
            binding.barReneEq1,
            binding.barReneEq2,
            binding.barReneEq3,
            binding.barReneEq4,
            binding.barReneEq5,
            binding.barReneEq6,
            binding.barReneEq7,
            binding.barReneEq8
        )
    }

    private fun pickRandomOutcome(): ReneLeBakOutcome {
        val roll = Random.nextInt(100)

        return when {
            roll < 5 -> ReneLeBakOutcome(
                spotifyUri = PARTY_TRACK_URI,
                type = ReneLeBakOutcomeType.PARTY
            )

            roll < 25 -> ReneLeBakOutcome(
                spotifyUri = CHALLENGE_TRACK_URI,
                type = ReneLeBakOutcomeType.SINGLE
            )

            else -> ReneLeBakOutcome(
                spotifyUri = SAFE_TRACK_URI,
                type = ReneLeBakOutcomeType.SAFE
            )
        }
    }

    private data class ReneLeBakOutcome(
        val spotifyUri: String,
        val type: ReneLeBakOutcomeType
    )

    private enum class ReneLeBakOutcomeType {
        SAFE,
        SINGLE,
        PARTY
    }

    companion object {
        private const val SAFE_TRACK_URI = "spotify:track:7s110BVIPaRVSvmUCj0JPD"
        private const val CHALLENGE_TRACK_URI = "spotify:track:0GvXLkClNLfrr8K7lBPjK0"
        private const val PARTY_TRACK_URI = "spotify:track:7vaG0LSG8M2J5A24QgSbHW"
        private const val TURNTABLE_ROTATION_MS = 2800L
        private const val EQ_PULSE_MS = 520L
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
