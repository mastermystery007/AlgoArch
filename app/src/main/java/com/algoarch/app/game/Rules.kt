package com.algoarch.app.game

import com.algoarch.app.data.Level
import com.algoarch.app.data.PlayerProgress
import com.algoarch.app.data.Region
import com.algoarch.app.data.ReviewItem

data class PlayerLevel(val level: Int, val xpIntoLevel: Int, val xpForNext: Int) {
    val fraction: Float get() = xpIntoLevel.toFloat() / xpForNext
}

data class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
)

/** All the game-feel rules in one place, as pure functions so they are easy to test and tune. */
object Rules {
    const val MAX_HEARTS = 5
    const val HEART_REGEN_MS = 20L * 60 * 1000
    const val XP_PER_CORRECT = 10
    const val XP_LEVEL_CLEAR = 20
    const val XP_PERFECT_BONUS = 15
    const val XP_REVIEW_CORRECT = 5
    const val REVIEW_SESSION_SIZE = 8
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Days until a remembered challenge comes back, per Leitner box. */
    val REVIEW_INTERVAL_DAYS = listOf(1, 3, 7, 14)

    // ---------- XP and player level ----------

    /** XP needed to go from [level] to [level] + 1: 100, 150, 200, ... */
    fun xpToNext(level: Int): Int = 100 + 50 * (level - 1)

    fun playerLevel(xp: Int): PlayerLevel {
        var level = 1
        var remaining = xp
        while (remaining >= xpToNext(level)) {
            remaining -= xpToNext(level)
            level++
        }
        return PlayerLevel(level, remaining, xpToNext(level))
    }

    // ---------- Hearts ----------

    fun regenHearts(p: PlayerProgress, now: Long): PlayerProgress {
        if (p.hearts >= MAX_HEARTS) return p
        val gained = ((now - p.heartsUpdatedAt) / HEART_REGEN_MS).toInt()
        if (gained <= 0) return p
        val hearts = minOf(MAX_HEARTS, p.hearts + gained)
        val updatedAt = if (hearts == MAX_HEARTS) now else p.heartsUpdatedAt + gained * HEART_REGEN_MS
        return p.copy(hearts = hearts, heartsUpdatedAt = updatedAt)
    }

    fun loseHeart(p: PlayerProgress, now: Long): PlayerProgress {
        val fresh = regenHearts(p, now)
        if (fresh.hearts <= 0) return fresh
        val updatedAt = if (fresh.hearts == MAX_HEARTS) now else fresh.heartsUpdatedAt
        return fresh.copy(hearts = fresh.hearts - 1, heartsUpdatedAt = updatedAt)
    }

    fun gainHeart(p: PlayerProgress, now: Long): PlayerProgress {
        val fresh = regenHearts(p, now)
        if (fresh.hearts >= MAX_HEARTS) return fresh
        val hearts = fresh.hearts + 1
        return fresh.copy(hearts = hearts, heartsUpdatedAt = if (hearts == MAX_HEARTS) now else fresh.heartsUpdatedAt)
    }

    /** Milliseconds until the next heart, or null when full. */
    fun nextHeartIn(p: PlayerProgress, now: Long): Long? {
        if (p.hearts >= MAX_HEARTS) return null
        val elapsed = (now - p.heartsUpdatedAt).coerceAtLeast(0)
        return HEART_REGEN_MS - elapsed % HEART_REGEN_MS
    }

    // ---------- Streaks ----------

    /** Call when a session finishes on [today] (a local day number). */
    fun touchStreak(p: PlayerProgress, today: Long): PlayerProgress {
        val streak = when (p.lastActiveDay) {
            today -> p.streak
            today - 1 -> p.streak + 1
            else -> 1
        }
        return p.copy(streak = streak, bestStreak = maxOf(p.bestStreak, streak), lastActiveDay = today)
    }

    /** The streak as it should be displayed today: it is lost once a whole day is missed. */
    fun displayStreak(p: PlayerProgress, today: Long): Int =
        if (p.lastActiveDay >= today - 1) p.streak else 0

    // ---------- Levels, stars and unlocking ----------

    fun stars(mistakes: Int): Int = when {
        mistakes == 0 -> 3
        mistakes == 1 -> 2
        else -> 1
    }

    fun isLevelUnlocked(regions: List<Region>, region: Region, level: Level, p: PlayerProgress): Boolean {
        if (!isRegionUnlocked(regions, region, p)) return false
        val index = region.levels.indexOf(level)
        return index <= 0 || (p.levelStars[region.levels[index - 1].id] ?: 0) > 0
    }

    fun isRegionUnlocked(regions: List<Region>, region: Region, p: PlayerProgress): Boolean {
        val sameTrack = regions.filter { it.track == region.track }.sortedBy { it.order }
        val index = sameTrack.indexOf(region)
        if (index <= 0) return true
        return isRegionCleared(sameTrack[index - 1], p)
    }

    fun isRegionCleared(region: Region, p: PlayerProgress): Boolean =
        region.levels.all { (p.levelStars[it.id] ?: 0) > 0 }

    // ---------- Spaced repetition ("rematches") ----------

    /** Updates review state after any answer, in a level or in a review session. */
    fun recordAnswer(p: PlayerProgress, challengeId: String, correct: Boolean, now: Long): PlayerProgress {
        val review = p.review.toMutableMap()
        val existing = review[challengeId]
        if (!correct) {
            review[challengeId] = ReviewItem(box = 0, dueAt = now)
        } else if (existing != null) {
            if (existing.box >= REVIEW_INTERVAL_DAYS.size) {
                review.remove(challengeId)
            } else {
                val days = REVIEW_INTERVAL_DAYS[existing.box]
                review[challengeId] = ReviewItem(box = existing.box + 1, dueAt = now + days * DAY_MS)
            }
        }
        return p.copy(
            review = review,
            answered = p.answered + 1,
            correct = p.correct + if (correct) 1 else 0,
        )
    }

    fun dueReviewIds(p: PlayerProgress, now: Long): List<String> =
        p.review.entries.filter { it.value.dueAt <= now }.sortedBy { it.value.dueAt }.map { it.key }

    // ---------- Achievements ----------

    fun achievements(p: PlayerProgress): List<Achievement> {
        val cleared = p.levelStars.count { it.value > 0 }
        val perfect = p.levelStars.count { it.value >= 3 }
        val level = playerLevel(p.xp).level
        return listOf(
            Achievement("🚀", "Hello, World", "Clear your first level", cleared >= 1),
            Achievement("🔥", "On Fire", "Reach a 3-day streak", p.bestStreak >= 3),
            Achievement("📅", "Week Warrior", "Reach a 7-day streak", p.bestStreak >= 7),
            Achievement("🏗️", "Architect", "Ship 3 system designs", p.designsShipped >= 3),
            Achievement("💎", "Perfectionist", "Get 3 stars on 5 levels", perfect >= 5),
            Achievement("🧠", "Comeback Kid", "Clear 3 rematch sessions", p.reviewsCleared >= 3),
            Achievement("🎓", "Scholar", "Reach player level 5", level >= 5),
            Achievement("🏆", "Completionist", "Clear 20 levels", cleared >= 20),
        )
    }
}
