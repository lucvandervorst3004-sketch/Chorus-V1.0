package com.example.qrspotify.duel

import com.example.qrspotify.practice.PracticeTrackPool

data class DuelSessionSnapshot(
    val playerOneName: String,
    val playerTwoName: String,
    val playerOneScore: Int,
    val playerTwoScore: Int,
    val categoryKey: String,
    val roundNumber: Int,
    val lastTrackUri: String,
    val targetScore: Int
) {
    val isInitialized: Boolean
        get() = playerOneName.isNotBlank() && playerTwoName.isNotBlank()

    fun playerNameFor(index: Int): String {
        return if (index == PLAYER_ONE_INDEX) playerOneName else playerTwoName
    }

    fun scoreFor(index: Int): Int {
        return if (index == PLAYER_ONE_INDEX) playerOneScore else playerTwoScore
    }

    companion object {
        const val PLAYER_ONE_INDEX = 0
        const val PLAYER_TWO_INDEX = 1
    }
}

object DuelScoreStore {

    const val PLAYER_ONE_INDEX = 0
    const val PLAYER_TWO_INDEX = 1

    const val DEFAULT_WINNING_SCORE = 7
    const val MIN_WINNING_SCORE = 3
    const val MAX_WINNING_SCORE = 15

    private var playerOneName: String = ""
    private var playerTwoName: String = ""
    private var playerOneScore: Int = 0
    private var playerTwoScore: Int = 0
    private var categoryKey: String = PracticeTrackPool.CATEGORY_ENGLISH
    private var roundNumber: Int = 1
    private var lastTrackUri: String = ""
    private var targetScore: Int = DEFAULT_WINNING_SCORE

    fun initializeSession(
        playerOneName: String,
        playerTwoName: String,
        categoryKey: String,
        targetScore: Int
    ) {
        this.playerOneName = playerOneName
        this.playerTwoName = playerTwoName
        this.categoryKey = categoryKey
        this.playerOneScore = 0
        this.playerTwoScore = 0
        this.roundNumber = 1
        this.lastTrackUri = ""
        this.targetScore = targetScore.coerceIn(MIN_WINNING_SCORE, MAX_WINNING_SCORE)
    }

    fun getSnapshot(): DuelSessionSnapshot {
        return DuelSessionSnapshot(
            playerOneName = playerOneName,
            playerTwoName = playerTwoName,
            playerOneScore = playerOneScore,
            playerTwoScore = playerTwoScore,
            categoryKey = categoryKey,
            roundNumber = roundNumber,
            lastTrackUri = lastTrackUri,
            targetScore = targetScore
        )
    }

    fun awardPointTo(playerIndex: Int) {
        when (playerIndex) {
            PLAYER_ONE_INDEX -> playerOneScore += 1
            PLAYER_TWO_INDEX -> playerTwoScore += 1
        }
    }

    fun setLastTrackUri(uri: String) {
        lastTrackUri = uri
    }

    fun advanceRound() {
        roundNumber += 1
    }

    fun getWinnerIndex(): Int? {
        return when {
            playerOneScore >= targetScore -> PLAYER_ONE_INDEX
            playerTwoScore >= targetScore -> PLAYER_TWO_INDEX
            else -> null
        }
    }

    fun resetSession() {
        playerOneName = ""
        playerTwoName = ""
        playerOneScore = 0
        playerTwoScore = 0
        categoryKey = PracticeTrackPool.CATEGORY_ENGLISH
        roundNumber = 1
        lastTrackUri = ""
        targetScore = DEFAULT_WINNING_SCORE
    }
}