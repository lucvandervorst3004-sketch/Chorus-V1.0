package com.example.qrspotify.practice

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

data class PracticeEvaluationResult(
    val isYearCorrect: Boolean,
    val isTitleCorrect: Boolean,
    val isArtistCorrect: Boolean
) {
    val correctAnswers: Int
        get() = listOf(isYearCorrect, isTitleCorrect, isArtistCorrect).count { it }
}

object PracticeAnswerEvaluator {

    fun evaluate(
        track: PracticeTrack,
        yearGuess: String,
        titleGuess: String,
        artistGuess: String
    ): PracticeEvaluationResult {
        val yearCorrect = yearGuess.trim() == track.year
        val titleCorrect = matchesTitle(titleGuess, track)
        val artistCorrect = matchesArtist(artistGuess, track)

        return PracticeEvaluationResult(
            isYearCorrect = yearCorrect,
            isTitleCorrect = titleCorrect,
            isArtistCorrect = artistCorrect
        )
    }

    private fun matchesTitle(guess: String, track: PracticeTrack): Boolean {
        val normalizedGuess = normalize(guess)
        if (normalizedGuess.isBlank()) return false

        val variants = buildTitleVariants(track)

        return variants.any { variant ->
            isCloseEnough(normalizedGuess, variant, 0.72)
        }
    }

    private fun matchesArtist(guess: String, track: PracticeTrack): Boolean {
        val normalizedGuess = normalize(guess)
        if (normalizedGuess.isBlank()) return false

        val expectedVariants = buildArtistVariants(track)
        val guessVariants = splitArtistVariants(guess)

        if (expectedVariants.any { isCloseEnough(normalizedGuess, it, 0.74) }) {
            return true
        }

        if (guessVariants.any { guessVariant ->
                expectedVariants.any { expectedVariant ->
                    isCloseEnough(guessVariant, expectedVariant, 0.74)
                }
            }) {
            return true
        }

        return false
    }

    private fun buildTitleVariants(track: PracticeTrack): Set<String> {
        val rawVariants = buildSet {
            add(track.title)
            addAll(track.titleAliases)
        }

        val cleanVariants = mutableSetOf<String>()

        rawVariants.forEach { value ->
            val normalizedOriginal = normalize(value)
            if (normalizedOriginal.isNotBlank()) {
                cleanVariants.add(normalizedOriginal)
            }

            val withoutBracketMetadata = normalize(
                value.replace(
                    Regex("\\(([^)]*(remaster|version|edit|mix|live|acoustic|mono|stereo)[^)]*)\\)", RegexOption.IGNORE_CASE),
                    " "
                )
            )
            if (withoutBracketMetadata.isNotBlank()) {
                cleanVariants.add(withoutBracketMetadata)
            }

            val withoutDashMetadata = normalize(
                value.replace(
                    Regex("\\s-\\s.*(remaster|version|edit|mix|live|acoustic|mono|stereo).*", RegexOption.IGNORE_CASE),
                    " "
                )
            )
            if (withoutDashMetadata.isNotBlank()) {
                cleanVariants.add(withoutDashMetadata)
            }
        }

        return cleanVariants
    }

    private fun buildArtistVariants(track: PracticeTrack): Set<String> {
        val values = buildSet {
            add(track.artist)
            addAll(track.artistAliases)
        }

        val variants = mutableSetOf<String>()
        values.forEach { value ->
            variants.addAll(splitArtistVariants(value))
        }
        return variants
    }

    private fun splitArtistVariants(text: String): Set<String> {
        val replaced = text
            .replace("&", ",")
            .replace(" feat. ", ",", ignoreCase = true)
            .replace(" feat ", ",", ignoreCase = true)
            .replace(" featuring ", ",", ignoreCase = true)
            .replace(" x ", ",", ignoreCase = true)
            .replace(" and ", ",", ignoreCase = true)
            .replace(" en ", ",", ignoreCase = true)
            .replace("/", ",")

        val full = normalize(replaced)
        val pieces = replaced.split(",")
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        return buildSet {
            if (full.isNotBlank()) add(full)
            addAll(pieces)
        }
    }

    private fun isCloseEnough(
        guess: String,
        expected: String,
        threshold: Double
    ): Boolean {
        if (guess == expected) return true

        val minimumUsefulLength = max(4, (expected.length * 0.6).toInt())

        if (expected.contains(guess) && guess.length >= minimumUsefulLength) {
            return true
        }

        if (guess.contains(expected) && expected.length >= 4) {
            return true
        }

        val guessWords = guess.split(" ").filter { it.isNotBlank() }
        val expectedWords = expected.split(" ").filter { it.isNotBlank() }

        if (guessWords.isNotEmpty() && expectedWords.isNotEmpty()) {
            val overlap = guessWords.intersect(expectedWords.toSet()).size.toDouble() /
                    max(guessWords.size, expectedWords.size)

            if (overlap >= 0.75) {
                return true
            }
        }

        return similarity(guess, expected) >= threshold
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0

        val distance = levenshtein(left, right)
        val maxLength = max(left.length, right.length).coerceAtLeast(1)

        return 1.0 - (distance.toDouble() / maxLength.toDouble())
    }

    private fun levenshtein(left: String, right: String): Int {
        val costs = IntArray(right.length + 1) { it }

        for (i in 1..left.length) {
            var previousDiagonal = costs[0]
            costs[0] = i

            for (j in 1..right.length) {
                val oldCost = costs[j]
                val substitutionCost = if (left[i - 1] == right[j - 1]) 0 else 1

                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    previousDiagonal + substitutionCost
                )

                previousDiagonal = oldCost
            }
        }

        return costs[right.length]
    }

    private fun normalize(value: String): String {
        var safe = value.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(" feat. ", " ", ignoreCase = true)
            .replace(" feat ", " ", ignoreCase = true)
            .replace(" featuring ", " ", ignoreCase = true)
            .replace("ø", "o")

        safe = Normalizer.normalize(safe, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        safe = safe
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return safe
    }
}