package com.algoarch.app

import com.algoarch.app.data.ChoiceChallenge
import com.algoarch.app.data.ContentParser
import com.algoarch.app.data.DesignChallenge
import com.algoarch.app.data.OrderChallenge
import com.algoarch.app.data.Region
import com.algoarch.app.data.TraceChallenge
import com.algoarch.app.data.Track
import com.algoarch.app.game.Architecture
import com.algoarch.app.game.ComponentType
import com.algoarch.app.game.DesignSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentTest {

    private val regions: List<Region> by lazy {
        val dir = File("src/main/assets/content")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.readText() }
        ContentParser.parseRegions(files)
    }

    @Test
    fun `all content packs parse`() {
        assertTrue("expected content packs", regions.size >= 2)
        assertTrue(regions.any { it.track == Track.ALGO })
        assertTrue(regions.any { it.track == Track.ARCH })
        regions.forEach { r -> assertTrue("${r.id} has no levels", r.levels.isNotEmpty()) }
    }

    @Test
    fun `ids are unique`() {
        val ids = regions.flatMap { r -> r.levels.map { it.id } + r.levels.flatMap { l -> l.challenges.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every challenge is internally consistent`() {
        regions.flatMap { it.levels }.flatMap { it.challenges }.forEach { c ->
            when (c) {
                is ChoiceChallenge -> assertTrue(c.id, c.answer in c.options.indices)
                is OrderChallenge -> {
                    val pool = c.lines + c.distractors
                    assertEquals("${c.id} has duplicate lines", pool.size, pool.toSet().size)
                }
                is TraceChallenge -> {
                    val ids = c.nodes.map { it.id }.toSet()
                    assertTrue(c.id, c.answer.all { it in ids })
                    assertTrue(c.id, c.edges.all { it.from in ids && it.to in ids })
                    assertEquals("${c.id} answer repeats a node", c.answer.size, c.answer.toSet().size)
                }
                is DesignChallenge -> Unit
            }
        }
    }

    @Test
    fun `every design scenario is solvable and the start is not`() {
        val designs = regions.flatMap { it.levels }.flatMap { it.challenges }.filterIsInstance<DesignChallenge>()
        assertTrue(designs.isNotEmpty())
        designs.forEach { d ->
            val start = Architecture.fromStart(d.scenario.start)
            assertFalse("${d.id}: start already passes", DesignSimulator.run(d.scenario, start).passed)
            assertTrue("${d.id}: no passing architecture", allArchitectures().any { DesignSimulator.run(d.scenario, it).passed })
        }
    }

    private fun allArchitectures(): Sequence<Architecture> = sequence {
        val types = ComponentType.entries
        fun build(i: Int, acc: Architecture): Sequence<Architecture> = sequence {
            if (i == types.size) {
                yield(acc)
            } else {
                val t = types[i]
                for (n in t.min..t.max) yieldAll(build(i + 1, acc.with(t, n)))
            }
        }
        yieldAll(build(0, Architecture()))
    }
}
