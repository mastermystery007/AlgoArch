package com.algoarch.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Track {
    @SerialName("algo") ALGO,
    @SerialName("arch") ARCH,
}

@Serializable
data class Region(
    val id: String,
    val order: Int,
    val track: Track,
    val title: String,
    val emoji: String,
    val description: String,
    val levels: List<Level>,
)

@Serializable
data class Level(
    val id: String,
    val title: String,
    val challenges: List<Challenge>,
)

/** Every challenge type the game knows how to play. The JSON "type" field picks the subclass. */
@Serializable
sealed class Challenge {
    abstract val id: String
    abstract val prompt: String
    abstract val explanation: String
}

/** Multiple choice: pattern spotting, complexity duels, concept checks. */
@Serializable
@SerialName("choice")
data class ChoiceChallenge(
    override val id: String,
    override val prompt: String,
    override val explanation: String,
    val options: List<String>,
    val answer: Int,
    val code: String? = null,
    val tag: String = "Quiz",
) : Challenge()

/** Parsons problem: put shuffled lines of code into the right order. */
@Serializable
@SerialName("order")
data class OrderChallenge(
    override val id: String,
    override val prompt: String,
    override val explanation: String,
    /** Lines in the correct order, indentation included. */
    val lines: List<String>,
    /** Extra lines that do not belong in the solution. */
    val distractors: List<String> = emptyList(),
) : Challenge()

/** Tap graph or tree nodes in the order an algorithm visits them. */
@Serializable
@SerialName("trace")
data class TraceChallenge(
    override val id: String,
    override val prompt: String,
    override val explanation: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val answer: List<String>,
    val directed: Boolean = false,
    /** Which algorithm produced [answer]; used by content validation. */
    val algo: String = "",
    val start: String = "",
) : Challenge()

@Serializable
data class GraphNode(
    val id: String,
    /** Position as a fraction (0..1) of the canvas width and height. */
    val x: Float,
    val y: Float,
)

@Serializable
data class GraphEdge(
    val from: String,
    val to: String,
    val weight: Int? = null,
)

/** System design sandbox: build an architecture that survives the traffic. */
@Serializable
@SerialName("design")
data class DesignChallenge(
    override val id: String,
    override val prompt: String,
    override val explanation: String,
    val scenario: Scenario,
) : Challenge()

@Serializable
data class Scenario(
    val name: String,
    val baseRps: Int,
    val readRatio: Double,
    val budget: Int,
    val maxLatencyMs: Int,
    val peakMultiplier: Double = 1.0,
    val staticRatio: Double = 0.0,
    /** Starting component counts, keyed by ComponentType name. */
    val start: Map<String, Int> = emptyMap(),
)
