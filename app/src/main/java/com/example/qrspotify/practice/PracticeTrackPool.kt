package com.example.qrspotify.practice

object PracticeTrackPool {

    const val CATEGORY_DUTCH = "dutch"
    const val CATEGORY_ENGLISH = "english"

    private const val MIN_OTHER_TRACKS_BEFORE_REPEAT = 25

    private val recentUrisByCategory = mutableMapOf<String, ArrayDeque<String>>()
    private val shuffledDecksByCategory = mutableMapOf<String, ArrayDeque<PracticeTrack>>()

    private val tracks: List<PracticeTrack>
        get() = (PracticeEnglishCatalog.tracks + PracticeDutchCatalog.tracks)
            .distinctBy { "${it.categoryKey}|${it.spotifyUri}" }

    fun getLabel(categoryKey: String?): String {
        return if (sanitizeCategory(categoryKey) == CATEGORY_DUTCH) {
            "Nederlandstalig"
        } else {
            "Wereldwijd"
        }
    }

    fun getRandomTrack(categoryKey: String?, previousUri: String? = null): PracticeTrack {
        val safeCategory = sanitizeCategory(categoryKey)

        val categoryTracks = tracks
            .filter { it.categoryKey == safeCategory }
            .distinctBy { it.spotifyUri }

        require(categoryTracks.isNotEmpty()) {
            "Geen practice tracks gevonden voor categorie: $safeCategory"
        }

        val recentUris = recentUrisByCategory.getOrPut(safeCategory) { ArrayDeque() }

        val blockedUris = buildSet {
            addAll(recentUris)
            if (!previousUri.isNullOrBlank()) {
                add(previousUri)
            }
        }

        val deck = shuffledDecksByCategory.getOrPut(safeCategory) {
            createDeck(categoryTracks)
        }

        var chosen = takeFirstAllowedFromDeck(deck, blockedUris)

        if (chosen == null) {
            val refillSource = categoryTracks.filter { it.spotifyUri !in blockedUris }
            shuffledDecksByCategory[safeCategory] = createDeck(
                if (refillSource.isNotEmpty()) refillSource else categoryTracks
            )
            chosen = takeFirstAllowedFromDeck(
                shuffledDecksByCategory.getValue(safeCategory),
                blockedUris
            )
        }

        if (chosen == null) {
            val fallback = categoryTracks
                .filter { it.spotifyUri != previousUri }
                .ifEmpty { categoryTracks }

            chosen = fallback.random()
        }

        rememberUri(safeCategory, chosen.spotifyUri)
        return chosen
    }

    fun resetSessionHistory() {
        recentUrisByCategory.clear()
        shuffledDecksByCategory.clear()
    }

    private fun sanitizeCategory(categoryKey: String?): String {
        return if (categoryKey == CATEGORY_DUTCH) {
            CATEGORY_DUTCH
        } else {
            CATEGORY_ENGLISH
        }
    }

    private fun rememberUri(categoryKey: String, uri: String) {
        val recentUris = recentUrisByCategory.getOrPut(categoryKey) { ArrayDeque() }

        recentUris.remove(uri)
        recentUris.addLast(uri)

        while (recentUris.size > MIN_OTHER_TRACKS_BEFORE_REPEAT) {
            recentUris.removeFirst()
        }
    }

    private fun createDeck(source: List<PracticeTrack>): ArrayDeque<PracticeTrack> {
        val deck = ArrayDeque<PracticeTrack>()
        source.shuffled().forEach { deck.addLast(it) }
        return deck
    }

    private fun takeFirstAllowedFromDeck(
        deck: ArrayDeque<PracticeTrack>,
        blockedUris: Set<String>
    ): PracticeTrack? {
        if (deck.isEmpty()) return null

        val attempts = deck.size
        repeat(attempts) {
            val candidate = deck.removeFirst()
            if (candidate.spotifyUri !in blockedUris) {
                return candidate
            }
            deck.addLast(candidate)
        }

        return null
    }
}