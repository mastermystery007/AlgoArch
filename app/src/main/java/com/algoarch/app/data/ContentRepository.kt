package com.algoarch.app.data

import android.content.Context
import kotlinx.serialization.json.Json

object ContentParser {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun parseRegions(files: List<String>): List<Region> =
        files.map { json.decodeFromString(Region.serializer(), it) }.sortedBy { it.order }
}

/** Loads the JSON content packs bundled in assets/content. */
class ContentRepository(private val context: Context) {

    val regions: List<Region> by lazy {
        val assets = context.assets
        val files = assets.list(CONTENT_DIR).orEmpty()
            .filter { it.endsWith(".json") }
            .map { name -> assets.open("$CONTENT_DIR/$name").bufferedReader().use { it.readText() } }
        ContentParser.parseRegions(files)
    }

    private val levelIndex: Map<String, Pair<Region, Level>> by lazy {
        regions.flatMap { r -> r.levels.map { it.id to (r to it) } }.toMap()
    }

    private val challengeIndex: Map<String, Pair<Region, Challenge>> by lazy {
        regions.flatMap { r -> r.levels.flatMap { l -> l.challenges.map { it.id to (r to it) } } }.toMap()
    }

    fun regionsFor(track: Track): List<Region> = regions.filter { it.track == track }

    fun level(id: String): Pair<Region, Level>? = levelIndex[id]

    fun challenge(id: String): Challenge? = challengeIndex[id]?.second

    fun trackOf(challengeId: String): Track? = challengeIndex[challengeId]?.first?.track

    val allLevels: List<Level> get() = regions.flatMap { it.levels }

    companion object {
        private const val CONTENT_DIR = "content"
    }
}
