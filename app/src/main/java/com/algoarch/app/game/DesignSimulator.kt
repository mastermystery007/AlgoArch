package com.algoarch.app.game

import com.algoarch.app.data.Scenario
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Building blocks the player can drop into an architecture. */
enum class ComponentType(
    val label: String,
    val emoji: String,
    val cost: Int,
    val min: Int,
    val max: Int,
    val blurb: String,
) {
    CDN("CDN", "🌍", 40, 0, 1, "Serves static files from the edge, so they never reach your servers."),
    LOAD_BALANCER("Load Balancer", "⚖️", 30, 0, 1, "Spreads requests across app servers. Without it, extra servers sit idle."),
    APP_SERVER("App Server", "🖥️", 50, 1, 20, "Runs your code. Each one handles ~1,000 req/s."),
    CACHE("Cache", "⚡", 60, 0, 1, "Answers ~85% of reads from memory before they hit the database."),
    QUEUE("Message Queue", "📬", 40, 0, 1, "Buffers writes and processes them at the average rate instead of the peak."),
    DB_SHARD("DB Shard", "🗄️", 100, 1, 8, "A primary database. Each shard adds 2,000 reads/s and 1,000 writes/s."),
    READ_REPLICA("Read Replica", "📚", 70, 0, 6, "A read-only copy of the data. Adds 2,000 reads/s."),
}

/** How many of each component the player has placed. */
data class Architecture(val counts: Map<ComponentType, Int> = emptyMap()) {
    fun count(type: ComponentType): Int = (counts[type] ?: type.min).coerceIn(type.min, type.max)
    fun has(type: ComponentType): Boolean = count(type) > 0
    fun with(type: ComponentType, value: Int): Architecture =
        copy(counts = counts + (type to value.coerceIn(type.min, type.max)))

    val cost: Int get() = ComponentType.entries.sumOf { it.cost * count(it) }

    companion object {
        fun fromStart(start: Map<String, Int>): Architecture = Architecture(
            ComponentType.entries.associateWith { type -> start[type.name] ?: type.min }
        )
    }
}

/** One stage of the request pipeline, as shown in the traffic view. */
data class TierStatus(
    val name: String,
    val emoji: String,
    val load: Double,
    val capacity: Double,
) {
    val utilization: Double get() = if (capacity.isInfinite() || capacity <= 0.0) 0.0 else load / capacity
    val overloaded: Boolean get() = utilization > 1.0
    val unlimited: Boolean get() = capacity.isInfinite()
}

data class SimulationResult(
    val tiers: List<TierStatus>,
    val latencyMs: Int,
    val cost: Int,
    val issues: List<String>,
    val passed: Boolean,
    val stars: Int,
)

/**
 * A deliberately simple capacity model. It is not a real queueing simulation, but every
 * number moves in the direction a real system would, which is what the player needs to learn.
 *
 * Keep the constants in sync with tools/sim.py, which is used to tune scenarios.
 */
object DesignSimulator {
    const val APP_CAPACITY = 1000.0
    const val CACHE_HIT_RATE = 0.85
    const val SHARD_READ_CAPACITY = 2000.0
    const val SHARD_WRITE_CAPACITY = 1000.0
    const val REPLICA_READ_CAPACITY = 2000.0

    private const val LB_LATENCY = 2.0
    private const val APP_LATENCY = 10.0
    private const val CACHE_LATENCY = 2.0
    private const val DB_READ_LATENCY = 15.0
    private const val DB_WRITE_LATENCY = 25.0
    private const val QUEUE_LATENCY = 4.0

    /** Latency grows sharply as a tier approaches saturation. */
    private fun slowdown(utilization: Double): Double {
        val u = min(utilization, 1.0)
        return 1.0 + 3.0 * u * u * u * u
    }

    fun run(scenario: Scenario, arch: Architecture): SimulationResult {
        val peak = scenario.baseRps * scenario.peakMultiplier
        val hasCdn = arch.has(ComponentType.CDN)
        val hasLb = arch.has(ComponentType.LOAD_BALANCER)
        val hasCache = arch.has(ComponentType.CACHE)
        val hasQueue = arch.has(ComponentType.QUEUE)
        val appServers = arch.count(ComponentType.APP_SERVER)
        val shards = arch.count(ComponentType.DB_SHARD)
        val replicas = arch.count(ComponentType.READ_REPLICA)

        val edgeServed = if (hasCdn) peak * scenario.staticRatio else 0.0
        val appLoad = peak - edgeServed
        val reachableServers = if (hasLb || appServers == 1) appServers else 1
        val appCapacity = reachableServers * APP_CAPACITY

        val reads = appLoad * scenario.readRatio
        val writes = appLoad * (1.0 - scenario.readRatio)
        val dbReads = if (hasCache) reads * (1.0 - CACHE_HIT_RATE) else reads
        val dbWrites = if (hasQueue) writes / scenario.peakMultiplier else writes
        val readCapacity = SHARD_READ_CAPACITY * shards + REPLICA_READ_CAPACITY * replicas
        val writeCapacity = SHARD_WRITE_CAPACITY * shards

        val tiers = buildList {
            if (hasCdn) add(TierStatus("CDN edge", ComponentType.CDN.emoji, edgeServed, Double.POSITIVE_INFINITY))
            if (hasLb) add(TierStatus("Load balancer", ComponentType.LOAD_BALANCER.emoji, appLoad, Double.POSITIVE_INFINITY))
            add(TierStatus("App servers", ComponentType.APP_SERVER.emoji, appLoad, appCapacity))
            if (hasCache) add(TierStatus("Cache", ComponentType.CACHE.emoji, reads, Double.POSITIVE_INFINITY))
            if (hasQueue) add(TierStatus("Queue", ComponentType.QUEUE.emoji, writes, Double.POSITIVE_INFINITY))
            add(TierStatus("DB reads", ComponentType.DB_SHARD.emoji, dbReads, readCapacity))
            add(TierStatus("DB writes", ComponentType.DB_SHARD.emoji, dbWrites, writeCapacity))
        }

        val appU = appLoad / appCapacity
        val readU = dbReads / readCapacity
        val writeU = dbWrites / writeCapacity

        var latency = (if (hasLb) LB_LATENCY else 0.0) + APP_LATENCY * slowdown(appU)
        val readLatency = if (hasCache) {
            CACHE_LATENCY + (1.0 - CACHE_HIT_RATE) * DB_READ_LATENCY * slowdown(readU)
        } else {
            DB_READ_LATENCY * slowdown(readU)
        }
        val writeLatency = if (hasQueue) QUEUE_LATENCY else DB_WRITE_LATENCY * slowdown(writeU)
        latency += scenario.readRatio * readLatency + (1.0 - scenario.readRatio) * writeLatency

        val cost = arch.cost
        val issues = mutableListOf<String>()
        if (appServers > 1 && !hasLb) {
            issues += "No load balancer: only 1 of your $appServers app servers receives traffic."
        }
        tiers.filter { it.overloaded }.forEach {
            issues += "${it.name} overloaded: ${it.load.fmt()} req/s vs ${it.capacity.fmt()} capacity."
        }
        val overloaded = tiers.any { it.overloaded }
        if (overloaded) {
            issues += "Requests are being dropped. Users see errors."
        } else if (latency > scenario.maxLatencyMs) {
            issues += "Average latency ${latency.roundToInt()} ms misses the ${scenario.maxLatencyMs} ms target."
        }
        if (cost > scenario.budget) {
            issues += "Over budget by $${cost - scenario.budget}/month."
        }

        val passed = !overloaded && latency <= scenario.maxLatencyMs && cost <= scenario.budget
        val maxUtil = max(appU, max(readU, writeU))
        val stars = if (!passed) 0 else {
            1 + (if (cost <= 0.9 * scenario.budget) 1 else 0) + (if (maxUtil <= 0.8) 1 else 0)
        }
        return SimulationResult(
            tiers = tiers,
            latencyMs = if (overloaded) 999 else latency.roundToInt(),
            cost = cost,
            issues = issues,
            passed = passed,
            stars = stars,
        )
    }

    private fun Double.fmt(): String = "%,d".format(roundToInt())
}
