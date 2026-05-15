package com.example.qrspotify.duel

sealed class DuelGameState {
    data object Idle : DuelGameState()
    data object Loading : DuelGameState()
    data object Listening : DuelGameState()
    data class Buzzed(val playerIndex: Int) : DuelGameState()
    data class Revealing(val playerIndex: Int) : DuelGameState()
    data object RoundOver : DuelGameState()
    data class GameOver(val winnerIndex: Int) : DuelGameState()
}