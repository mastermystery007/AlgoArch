package com.algoarch.app

import com.algoarch.app.data.PlayerProgress
import com.algoarch.app.data.Scenario
import com.algoarch.app.game.Architecture
import com.algoarch.app.game.ComponentType
import com.algoarch.app.game.DesignSimulator
import com.algoarch.app.game.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun `player level thresholds`() {
        assertEquals(1, Rules.playerLevel(0).level)
        assertEquals(1, Rules.playerLevel(99).level)
        assertEquals(2, Rules.playerLevel(100).level)
        assertEquals(3, Rules.playerLevel(250).level)
        assertEquals(50, Rules.playerLevel(150).xpIntoLevel)
    }

    @Test
    fun `hearts drain and regenerate`() {
        var p = PlayerProgress()
        p = Rules.loseHeart(p, now = 0)
        p = Rules.loseHeart(p, now = 1_000)
        assertEquals(3, p.hearts)
        assertEquals(4, Rules.regenHearts(p, Rules.HEART_REGEN_MS).hearts)
        assertEquals(Rules.MAX_HEARTS, Rules.regenHearts(p, 10 * Rules.HEART_REGEN_MS).hearts)
        assertNull(Rules.nextHeartIn(PlayerProgress(), 0))
    }

    @Test
    fun `streak grows on consecutive days and resets after a gap`() {
        var p = Rules.touchStreak(PlayerProgress(), today = 100)
        assertEquals(1, p.streak)
        p = Rules.touchStreak(p, 100)
        assertEquals(1, p.streak)
        p = Rules.touchStreak(p, 101)
        assertEquals(2, p.streak)
        assertEquals(2, Rules.displayStreak(p, 102))
        assertEquals(0, Rules.displayStreak(p, 103))
        p = Rules.touchStreak(p, 105)
        assertEquals(1, p.streak)
        assertEquals(2, p.bestStreak)
    }

    @Test
    fun `missed questions come back and graduate`() {
        val day = 24L * 60 * 60 * 1000
        var p = Rules.recordAnswer(PlayerProgress(), "q", correct = false, now = 0)
        assertEquals(listOf("q"), Rules.dueReviewIds(p, 0))
        p = Rules.recordAnswer(p, "q", correct = true, now = 0)
        assertTrue(Rules.dueReviewIds(p, 0).isEmpty())
        assertEquals(listOf("q"), Rules.dueReviewIds(p, day))
        repeat(Rules.REVIEW_INTERVAL_DAYS.size) { p = Rules.recordAnswer(p, "q", correct = true, now = 0) }
        assertFalse(p.review.containsKey("q"))
    }

    @Test
    fun `simulator rewards scaling out`() {
        val scenario = Scenario("t", baseRps = 2000, readRatio = 0.7, budget = 340, maxLatencyMs = 75)
        val single = Architecture.fromStart(emptyMap())
        assertFalse(DesignSimulator.run(scenario, single).passed)

        val noLb = single.with(ComponentType.APP_SERVER, 3)
        val noLbResult = DesignSimulator.run(scenario, noLb)
        assertFalse(noLbResult.passed)
        assertTrue(noLbResult.issues.any { it.contains("load balancer", ignoreCase = true) })

        val good = noLb.with(ComponentType.LOAD_BALANCER, 1)
        val result = DesignSimulator.run(scenario, good)
        assertTrue(result.issues.joinToString(), result.passed)
        assertEquals(3, result.stars)
    }
}
