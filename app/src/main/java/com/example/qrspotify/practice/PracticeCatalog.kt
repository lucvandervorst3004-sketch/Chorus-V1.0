package com.example.qrspotify.practice

data class PracticeTrack(
    val spotifyUri: String,
    val title: String,
    val artist: String,
    val year: String,
    val categoryKey: String,
    val titleAliases: List<String> = emptyList(),
    val artistAliases: List<String> = emptyList()
)