package com.example.qrspotify.practice

import android.content.Context
import android.content.SharedPreferences

data class PracticeSessionSnapshot(
    val totalCorrectAnswers: Int = 0,
    val totalSubmittedAnswers: Int = 0,
    val totalRounds: Int = 0
)

data class PracticeCategorySessionStats(
    val currentStreak: Int = 0,
    val highestSessionStreak: Int = 0,
    val totalCorrectAnswers: Int = 0,
    val totalSubmittedAnswers: Int = 0,
    val totalRounds: Int = 0
)

object PracticeStatsStore {

    private const val PREFS_NAME = "qrspotify_practice_stats"

    private const val KEY_LEGACY_ALL_TIME_HIGHSCORE = "all_time_highscore"
    private const val KEY_ALL_TIME_HIGHSCORE_DUTCH = "all_time_highscore_dutch"
    private const val KEY_ALL_TIME_HIGHSCORE_ENGLISH = "all_time_highscore_english"

    private const val KEY_ALL_TIME_HIGHEST_STREAK_DUTCH = "all_time_highest_streak_dutch"
    private const val KEY_ALL_TIME_HIGHEST_STREAK_ENGLISH = "all_time_highest_streak_english"

    private lateinit var prefs: SharedPreferences

    private var currentSession = PracticeSessionSnapshot()

    private val categorySessionStats = mutableMapOf(
        PracticeTrackPool.CATEGORY_DUTCH to PracticeCategorySessionStats(),
        PracticeTrackPool.CATEGORY_ENGLISH to PracticeCategorySessionStats()
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyHighScoreIfNeeded()
    }

    fun getSessionSnapshot(): PracticeSessionSnapshot {
        return currentSession
    }

    fun getCategorySessionStats(categoryKey: String): PracticeCategorySessionStats {
        return categorySessionStats[categoryKey] ?: PracticeCategorySessionStats()
    }

    fun getAllTimeHighScoreForCategory(categoryKey: String): Int {
        if (!::prefs.isInitialized) return 0

        return when (categoryKey) {
            PracticeTrackPool.CATEGORY_DUTCH -> prefs.getInt(KEY_ALL_TIME_HIGHSCORE_DUTCH, 0)
            else -> prefs.getInt(KEY_ALL_TIME_HIGHSCORE_ENGLISH, 0)
        }
    }

    fun getAllTimeHighestStreakForCategory(categoryKey: String): Int {
        if (!::prefs.isInitialized) return 0

        return when (categoryKey) {
            PracticeTrackPool.CATEGORY_DUTCH -> prefs.getInt(KEY_ALL_TIME_HIGHEST_STREAK_DUTCH, 0)
            else -> prefs.getInt(KEY_ALL_TIME_HIGHEST_STREAK_ENGLISH, 0)
        }
    }

    fun getDutchHighScore(): Int {
        return getAllTimeHighScoreForCategory(PracticeTrackPool.CATEGORY_DUTCH)
    }

    fun getEnglishHighScore(): Int {
        return getAllTimeHighScoreForCategory(PracticeTrackPool.CATEGORY_ENGLISH)
    }

    fun recordRound(categoryKey: String, correctAnswers: Int) {
        val safeCorrectAnswers = correctAnswers.coerceIn(0, 3)

        currentSession = currentSession.copy(
            totalCorrectAnswers = currentSession.totalCorrectAnswers + safeCorrectAnswers,
            totalSubmittedAnswers = currentSession.totalSubmittedAnswers + 3,
            totalRounds = currentSession.totalRounds + 1
        )

        val previousCategoryStats = getCategorySessionStats(categoryKey)
        val newCurrentStreak = if (safeCorrectAnswers >= 2) {
            previousCategoryStats.currentStreak + 1
        } else {
            0
        }

        val newHighestSessionStreak = maxOf(
            previousCategoryStats.highestSessionStreak,
            newCurrentStreak
        )

        categorySessionStats[categoryKey] = previousCategoryStats.copy(
            currentStreak = newCurrentStreak,
            highestSessionStreak = newHighestSessionStreak,
            totalCorrectAnswers = previousCategoryStats.totalCorrectAnswers + safeCorrectAnswers,
            totalSubmittedAnswers = previousCategoryStats.totalSubmittedAnswers + 3,
            totalRounds = previousCategoryStats.totalRounds + 1
        )

        val currentCategoryHighScore = getAllTimeHighScoreForCategory(categoryKey)
        if (safeCorrectAnswers > currentCategoryHighScore && ::prefs.isInitialized) {
            prefs.edit()
                .putInt(getHighScoreKey(categoryKey), safeCorrectAnswers)
                .apply()
        }

        val currentHighestStreak = getAllTimeHighestStreakForCategory(categoryKey)
        if (newCurrentStreak > currentHighestStreak && ::prefs.isInitialized) {
            prefs.edit()
                .putInt(getHighestStreakKey(categoryKey), newCurrentStreak)
                .apply()
        }
    }

    fun resetSession() {
        currentSession = PracticeSessionSnapshot()
        categorySessionStats[PracticeTrackPool.CATEGORY_DUTCH] = PracticeCategorySessionStats()
        categorySessionStats[PracticeTrackPool.CATEGORY_ENGLISH] = PracticeCategorySessionStats()
    }

    fun resetDutchHighScore() {
        if (!::prefs.isInitialized) return

        prefs.edit()
            .putInt(KEY_ALL_TIME_HIGHSCORE_DUTCH, 0)
            .apply()
    }

    fun resetEnglishHighScore() {
        if (!::prefs.isInitialized) return

        prefs.edit()
            .putInt(KEY_ALL_TIME_HIGHSCORE_ENGLISH, 0)
            .apply()
    }

    private fun getHighScoreKey(categoryKey: String): String {
        return when (categoryKey) {
            PracticeTrackPool.CATEGORY_DUTCH -> KEY_ALL_TIME_HIGHSCORE_DUTCH
            else -> KEY_ALL_TIME_HIGHSCORE_ENGLISH
        }
    }

    private fun getHighestStreakKey(categoryKey: String): String {
        return when (categoryKey) {
            PracticeTrackPool.CATEGORY_DUTCH -> KEY_ALL_TIME_HIGHEST_STREAK_DUTCH
            else -> KEY_ALL_TIME_HIGHEST_STREAK_ENGLISH
        }
    }

    private fun migrateLegacyHighScoreIfNeeded() {
        if (!::prefs.isInitialized) return

        val hasLegacy = prefs.contains(KEY_LEGACY_ALL_TIME_HIGHSCORE)
        val hasNewDutch = prefs.contains(KEY_ALL_TIME_HIGHSCORE_DUTCH)
        val hasNewEnglish = prefs.contains(KEY_ALL_TIME_HIGHSCORE_ENGLISH)

        if (!hasLegacy || hasNewDutch || hasNewEnglish) return

        val legacyValue = prefs.getInt(KEY_LEGACY_ALL_TIME_HIGHSCORE, 0)

        prefs.edit()
            .putInt(KEY_ALL_TIME_HIGHSCORE_DUTCH, legacyValue)
            .putInt(KEY_ALL_TIME_HIGHSCORE_ENGLISH, legacyValue)
            .remove(KEY_LEGACY_ALL_TIME_HIGHSCORE)
            .apply()
    }
}